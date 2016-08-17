/*
 * Copyright 2015 - 2016 Red Bull Media House GmbH <http://www.redbullmediahouse.com> - all rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.rbmhtechnology.eventuate.adapter.vertx

import akka.actor.{ActorRef, ActorSystem, Status}
import akka.testkit.TestKit
import com.rbmhtechnology.eventuate.SingleLocationSpecLeveldb
import org.scalatest.{MustMatchers, WordSpecLike}

import scala.concurrent.duration._

class ReliableReadLogAdapterSpec extends TestKit(ActorSystem("test", PublishReadLogAdapterSpec.Config)) with WordSpecLike with MustMatchers
  with SingleLocationSpecLeveldb with VertxEventbusSpec with ActorStorage with EventWriter with StopSystemAfterAll {

  val redeliverDelay = 1.seconds
  val storageTimeout = 500.millis
  val inboundLogId = "log_inbound_confirm"

  override def beforeEach(): Unit = {
    super.beforeEach()

    registerCodec()
    confirmableEventLogService(sendAdapterInfo, confirmableEventHandler)
    logAdapter(sendAdapterInfo)
  }

  def logAdapter(logAdapterInfo: SendLogAdapterInfo): ActorRef =
    system.actorOf(ReliableReadLogAdapter.props(inboundLogId, log, logAdapterInfo, vertx, actorStorageProvider(), redeliverDelay))

  def read: String = read(inboundLogId)

  def write: (Long) => String = write(inboundLogId)

  "A ReliableReadLogAdapter" when {
    "reading events from an event log" must {
      "deliver events to a single consumer" in {
        writeEvents("ev", 5)

        storageProbe.expectMsg(read)
        storageProbe.reply(0L)

        ebProbe.expectConfirmableEvent(sequenceNr = 1)
        ebProbe.expectConfirmableEvent(sequenceNr = 2)
        ebProbe.expectConfirmableEvent(sequenceNr = 3)
        ebProbe.expectConfirmableEvent(sequenceNr = 4)
        ebProbe.expectConfirmableEvent(sequenceNr = 5)
      }
      "deliver events based on the replication progress" in {
        writeEvents("ev", 5)

        storageProbe.expectMsg(read)
        storageProbe.reply(2L)

        ebProbe.expectConfirmableEvent(sequenceNr = 3)
        ebProbe.expectConfirmableEvent(sequenceNr = 4)
        ebProbe.expectConfirmableEvent(sequenceNr = 5)
      }
      "persist event confirmations if no gaps exist" in {
        writeEvents("ev", 3)

        storageProbe.expectMsg(read)
        storageProbe.reply(0L)
        storageProbe.expectNoMsg(storageTimeout)

        ebProbe.expectConfirmableEvent(sequenceNr = 1).confirm()
        ebProbe.expectConfirmableEvent(sequenceNr = 2)
        ebProbe.expectConfirmableEvent(sequenceNr = 3)

        storageProbe.expectMsg(write(1))
      }
      "persist event confirmations only after gaps have been resolved" in {
        writeEvents("ev", 4)

        storageProbe.expectMsg(read)
        storageProbe.reply(0L)
        storageProbe.expectNoMsg(storageTimeout)

        ebProbe.expectConfirmableEvent(sequenceNr = 1)
        ebProbe.expectConfirmableEvent(sequenceNr = 2).confirm()
        ebProbe.expectConfirmableEvent(sequenceNr = 3).confirm()
        ebProbe.expectConfirmableEvent(sequenceNr = 4)

        storageProbe.expectNoMsg(storageTimeout)

        ebProbe.expectConfirmableEvent(sequenceNr = 1).confirm()
        ebProbe.expectConfirmableEvent(sequenceNr = 4)

        storageProbe.expectMsg(write(3))
        storageProbe.reply(3L)
      }
      "persist event confirmations once all events have been confirmed" in {
        writeEvents("ev", 3)

        storageProbe.expectMsg(read)
        storageProbe.reply(0L)
        storageProbe.expectNoMsg(storageTimeout)

        val ev1 = ebProbe.expectConfirmableEvent(sequenceNr = 1)
        val ev2 = ebProbe.expectConfirmableEvent(sequenceNr = 2)
        val ev3 = ebProbe.expectConfirmableEvent(sequenceNr = 3)

        ev3.confirm()
        ev2.confirm()
        ev1.confirm()

        storageProbe.expectMsg(write(3))
        storageProbe.reply(3L)
      }
      "persist event confirmations sequentially" in {
        writeEvents("ev", 3)

        storageProbe.expectMsg(read)
        storageProbe.reply(0L)
        storageProbe.expectNoMsg(storageTimeout)

        ebProbe.expectConfirmableEvent(sequenceNr = 1).confirm()
        ebProbe.expectConfirmableEvent(sequenceNr = 2).confirm()
        ebProbe.expectConfirmableEvent(sequenceNr = 3).confirm()

        storageProbe.expectMsg(write(1))
        storageProbe.reply(1L)

        storageProbe.expectMsg(write(2))
        storageProbe.reply(2L)

        storageProbe.expectMsg(write(3))
        storageProbe.reply(3L)
      }
      "redeliver all unconfirmed events" in {
        writeEvents("ev", 2)

        storageProbe.expectMsg(read)
        storageProbe.reply(0L)

        storageProbe.expectNoMsg(storageTimeout)

        ebProbe.expectConfirmableEvent(sequenceNr = 1)
        ebProbe.expectConfirmableEvent(sequenceNr = 2)

        ebProbe.expectConfirmableEvent(sequenceNr = 1)
        ebProbe.expectConfirmableEvent(sequenceNr = 2)

        ebProbe.expectConfirmableEvent(sequenceNr = 1)
        ebProbe.expectConfirmableEvent(sequenceNr = 2)
      }
      "redeliver only unconfirmed events" in {
        writeEvents("ev", 5)

        storageProbe.expectMsg(read)
        storageProbe.reply(0L)

        ebProbe.expectConfirmableEvent(sequenceNr = 1)
        ebProbe.expectConfirmableEvent(sequenceNr = 2).confirm()
        ebProbe.expectConfirmableEvent(sequenceNr = 3).confirm()
        ebProbe.expectConfirmableEvent(sequenceNr = 4).confirm()
        ebProbe.expectConfirmableEvent(sequenceNr = 5)

        ebProbe.expectConfirmableEvent(sequenceNr = 1)
        ebProbe.expectConfirmableEvent(sequenceNr = 5)
      }
      "redeliver only unconfirmed events while processing new events" in {
        writeEvents("ev", 3)

        storageProbe.expectMsg(read)
        storageProbe.reply(0L)

        ebProbe.expectConfirmableEvent(sequenceNr = 1)
        ebProbe.expectConfirmableEvent(sequenceNr = 2).confirm()
        ebProbe.expectConfirmableEvent(sequenceNr = 3)

        writeEvents("ev", 2)

        ebProbe.expectConfirmableEvent(sequenceNr = 4).confirm()
        ebProbe.expectConfirmableEvent(sequenceNr = 5)

        ebProbe.expectConfirmableEvent(sequenceNr = 1)
        ebProbe.expectConfirmableEvent(sequenceNr = 3)
        ebProbe.expectConfirmableEvent(sequenceNr = 5)
      }
    }
    "encountering a write failure" must {
      "restart and start at the last position" in {
        writeEvents("ev", 3)

        storageProbe.expectMsg(read)
        storageProbe.reply(0L)

        ebProbe.expectConfirmableEvent(sequenceNr = 1).confirm()
        ebProbe.expectConfirmableEvent(sequenceNr = 2)
        ebProbe.expectConfirmableEvent(sequenceNr = 3)

        storageProbe.expectMsg(write(1))
        storageProbe.reply(Status.Failure(new RuntimeException("err")))

        storageProbe.expectMsg(read)
        storageProbe.reply(1L)

        ebProbe.expectConfirmableEvent(sequenceNr = 2)
        ebProbe.expectConfirmableEvent(sequenceNr = 3)
      }
    }
  }
}
