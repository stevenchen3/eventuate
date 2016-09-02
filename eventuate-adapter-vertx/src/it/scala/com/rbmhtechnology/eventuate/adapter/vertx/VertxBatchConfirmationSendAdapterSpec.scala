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
import akka.testkit.{TestKit, TestProbe}
import com.rbmhtechnology.eventuate.SingleLocationSpecLeveldb
import org.scalatest.{MustMatchers, WordSpecLike}

import scala.concurrent.duration._

class VertxBatchConfirmationSendAdapterSpec extends TestKit(ActorSystem("test", VertxPublishAdapterSpec.Config))
  with WordSpecLike with MustMatchers with SingleLocationSpecLeveldb with StopSystemAfterAll
  with ActorStorage with EventWriter with VertxEventbus {

  val redeliverTimeout = 1.seconds
  val storageTimeout = 500.millis
  val inboundLogId = "log_inbound_confirm"
  val endpoint = VertxEndpointResolver("vertx-eb-endpoint")
  var ebProbe: TestProbe = _

  override def beforeEach(): Unit = {
    super.beforeEach()

    registerCodec()
    ebProbe = eventBusProbe(endpoint.address)
  }

  def logAdapter(batchSize: Int = 10): ActorRef =
    system.actorOf(VertxBatchConfirmationSendAdapter.props(inboundLogId, log, endpoint, vertx, actorStorageProvider(), batchSize, redeliverTimeout))

  def read: String = read(inboundLogId)

  def write: (Long) => String = write(inboundLogId)

  "A VertxBatchConfirmationSendAdapter" when {
    "reading events from an event log" must {
      "deliver events to a single consumer" in {
        logAdapter()
        writeEvents("e", 5)

        storageProbe.expectMsg(read)
        storageProbe.reply(0L)

        ebProbe.expectVertxMsg(body = "e-1")
        ebProbe.expectVertxMsg(body = "e-2")
        ebProbe.expectVertxMsg(body = "e-3")
        ebProbe.expectVertxMsg(body = "e-4")
        ebProbe.expectVertxMsg(body = "e-5")
      }
      "deliver events based on the replication progress" in {
        logAdapter()
        writeEvents("e", 5)

        storageProbe.expectMsg(read)
        storageProbe.reply(2L)

        ebProbe.expectVertxMsg(body = "e-3")
        ebProbe.expectVertxMsg(body = "e-4")
        ebProbe.expectVertxMsg(body = "e-5")
      }
      "persist event confirmations" in {
        logAdapter()
        writeEvents("e", 3)

        storageProbe.expectMsg(read)
        storageProbe.reply(0L)
        storageProbe.expectNoMsg(storageTimeout)

        ebProbe.expectVertxMsg(body = "e-1").confirm()
        ebProbe.expectVertxMsg(body = "e-2").confirm()
        ebProbe.expectVertxMsg(body = "e-3").confirm()

        storageProbe.expectMsg(write(3))
      }
      "persist event confirmations in batches" in {
        logAdapter(batchSize = 2)
        writeEvents("e", 4)

        storageProbe.expectMsg(read)
        storageProbe.reply(0L)
        storageProbe.expectNoMsg(storageTimeout)

        ebProbe.expectVertxMsg(body = "e-1").confirm()
        ebProbe.expectVertxMsg(body = "e-2").confirm()

        storageProbe.expectMsg(write(2))
        storageProbe.reply(2L)

        ebProbe.expectVertxMsg(body = "e-3").confirm()
        ebProbe.expectVertxMsg(body = "e-4").confirm()

        storageProbe.expectMsg(write(4))
        storageProbe.reply(2L)
      }
      "persist event confirmations in batches of smaller size if no further events present" in {
        logAdapter(batchSize = 2)
        writeEvents("e", 3)

        storageProbe.expectMsg(read)
        storageProbe.reply(0L)
        storageProbe.expectNoMsg(storageTimeout)

        ebProbe.expectVertxMsg(body = "e-1").confirm()
        ebProbe.expectVertxMsg(body = "e-2").confirm()

        storageProbe.expectMsg(write(2))
        storageProbe.reply(2L)

        ebProbe.expectVertxMsg(body = "e-3").confirm()

        storageProbe.expectMsg(write(3))
        storageProbe.reply(3L)
      }
      "redeliver whole batch if events are unconfirmed" in {
        logAdapter()
        writeEvents("e", 5)

        storageProbe.expectMsg(read)
        storageProbe.reply(0L)

        ebProbe.expectVertxMsg(body = "e-1")
        ebProbe.expectVertxMsg(body = "e-2").confirm()
        ebProbe.expectVertxMsg(body = "e-3").confirm()
        ebProbe.expectVertxMsg(body = "e-4").confirm()
        ebProbe.expectVertxMsg(body = "e-5").confirm()

        storageProbe.expectMsg(read)
        storageProbe.reply(0L)

        ebProbe.expectVertxMsg(body = "e-1")
        ebProbe.expectVertxMsg(body = "e-2")
        ebProbe.expectVertxMsg(body = "e-3")
        ebProbe.expectVertxMsg(body = "e-4")
        ebProbe.expectVertxMsg(body = "e-5")
      }
      "redeliver unconfirmed event batches while replaying events" in {
        logAdapter(batchSize = 2)
        writeEvents("e", 4)

        storageProbe.expectMsg(read)
        storageProbe.reply(0L)

        ebProbe.expectVertxMsg(body = "e-1").confirm()
        ebProbe.expectVertxMsg(body = "e-2").confirm()

        storageProbe.expectMsg(write(2))
        storageProbe.reply(2L)

        ebProbe.expectVertxMsg(body = "e-3").confirm()
        ebProbe.expectVertxMsg(body = "e-4")

        storageProbe.expectMsg(read)
        storageProbe.reply(2L)

        ebProbe.expectVertxMsg(body = "e-3").confirm()
        ebProbe.expectVertxMsg(body = "e-4").confirm()
      }
      "redeliver unconfirmed event batches while processing new events" in {
        logAdapter(batchSize = 2)
        writeEvents("e", 2)

        storageProbe.expectMsg(read)
        storageProbe.reply(0L)

        ebProbe.expectVertxMsg(body = "e-1").confirm()
        ebProbe.expectVertxMsg(body = "e-2").confirm()

        storageProbe.expectMsg(write(2))
        storageProbe.reply(2L)

        writeEvents("e", 2, start = 3)

        ebProbe.expectVertxMsg(body = "e-3").confirm()

        storageProbe.expectMsg(write(3))
        storageProbe.reply(3L)

        ebProbe.expectVertxMsg(body = "e-4")

        storageProbe.expectMsg(read)
        storageProbe.reply(3L)

        ebProbe.expectVertxMsg(body = "e-4").confirm()
      }
    }
    "encountering a write failure" must {
      "restart and start at the last position" in {
        logAdapter()
        writeEvents("e", 3)

        storageProbe.expectMsg(read)
        storageProbe.reply(0L)

        ebProbe.expectVertxMsg(body = "e-1").confirm()
        ebProbe.expectVertxMsg(body = "e-2").confirm()
        ebProbe.expectVertxMsg(body = "e-3").confirm()

        storageProbe.expectMsg(write(3))
        storageProbe.reply(Status.Failure(new RuntimeException("err")))

        storageProbe.expectMsg(read)
        storageProbe.reply(0L)

        ebProbe.expectVertxMsg(body = "e-1")
        ebProbe.expectVertxMsg(body = "e-2")
        ebProbe.expectVertxMsg(body = "e-3")
      }
    }
  }
}
