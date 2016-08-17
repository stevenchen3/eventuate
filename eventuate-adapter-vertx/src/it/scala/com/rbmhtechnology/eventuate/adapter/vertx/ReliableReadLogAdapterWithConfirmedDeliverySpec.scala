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

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.TestKit
import com.rbmhtechnology.eventuate.SingleLocationSpecLeveldb
import org.scalatest.{MustMatchers, WordSpecLike}

import scala.concurrent.duration._

class ReliableReadLogAdapterWithConfirmedDeliverySpec extends TestKit(ActorSystem("test", PublishReadLogAdapterSpec.Config)) with WordSpecLike with MustMatchers
  with SingleLocationSpecLeveldb with VertxEventbusSpec with EventWriter with StopSystemAfterAll {

  val redeliverDelay = 1.seconds
  val inboundLogId = "log_inbound_confirm"

  override def beforeEach(): Unit = {
    super.beforeEach()

    registerCodec()
    confirmableEventLogService(sendAdapterInfo, confirmableEventHandler)
    logAdapter(sendAdapterInfo)
  }

  def logAdapter(logAdapterInfo: SendLogAdapterInfo): ActorRef =
    system.actorOf(ReliableReadLogAdapterWithConfirmedDelivery.props(inboundLogId, log, logAdapterInfo, vertx, redeliverDelay))

  "A ReliableReadLogAdapterWithConfirmedDelivery" when {
    "reading events from an event log" must {
      "deliver the events to a single consumer" in {
        writeEvents("ev", 5)

        ebProbe.expectConfirmableEvent(sequenceNr = 1)
        ebProbe.expectConfirmableEvent(sequenceNr = 2)
        ebProbe.expectConfirmableEvent(sequenceNr = 3)
        ebProbe.expectConfirmableEvent(sequenceNr = 4)
        ebProbe.expectConfirmableEvent(sequenceNr = 5)
      }
      "redeliver all unconfirmed events" in {
        writeEvents("ev", 2)

        ebProbe.expectConfirmableEvent(sequenceNr = 1)
        ebProbe.expectConfirmableEvent(sequenceNr = 2)

        ebProbe.expectConfirmableEvent(sequenceNr = 1)
        ebProbe.expectConfirmableEvent(sequenceNr = 2)

        ebProbe.expectConfirmableEvent(sequenceNr = 1)
        ebProbe.expectConfirmableEvent(sequenceNr = 2)
      }
      "redeliver only unconfirmed events" in {
        writeEvents("ev", 5)

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

        ebProbe.expectConfirmableEvent(sequenceNr = 1)
        ebProbe.expectConfirmableEvent(sequenceNr = 2).confirm()
        ebProbe.expectConfirmableEvent(sequenceNr = 3)

        writeEvents("ev", 2)

        ebProbe.expectConfirmableEvent(sequenceNr = 5).confirm()
        ebProbe.expectConfirmableEvent(sequenceNr = 6)

        ebProbe.expectConfirmableEvent(sequenceNr = 1)
        ebProbe.expectConfirmableEvent(sequenceNr = 3)
        ebProbe.expectConfirmableEvent(sequenceNr = 6)
      }
    }
  }
}
