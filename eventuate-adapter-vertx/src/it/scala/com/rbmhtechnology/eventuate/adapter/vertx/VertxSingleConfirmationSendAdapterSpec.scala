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
import akka.testkit.{TestKit, TestProbe}
import com.rbmhtechnology.eventuate.SingleLocationSpecLeveldb
import org.scalatest.{MustMatchers, WordSpecLike}

import scala.concurrent.duration._

class VertxSingleConfirmationSendAdapterSpec extends TestKit(ActorSystem("test", VertxPublishAdapterSpec.Config))
  with WordSpecLike with MustMatchers with SingleLocationSpecLeveldb with StopSystemAfterAll with EventWriter
  with VertxEventbus {

  val redeliverDelay = 1.seconds
  val inboundLogId = "log_inbound_confirm"
  val endpoint = VertxEndpointResolver("vertx-eb-endpoint")
  var ebProbe: TestProbe = _

  override def beforeEach(): Unit = {
    super.beforeEach()

    ebProbe = eventBusProbe(endpoint.address)
    registerCodec()
    logAdapter()
  }

  def logAdapter(): ActorRef =
    system.actorOf(VertxSingleConfirmationSendAdapter.props(inboundLogId, log, endpoint, vertx, redeliverDelay))

  "A VertxSingleConfirmationSendAdapter" when {
    "reading events from an event log" must {
      "deliver the events to a single consumer" in {
        writeEvents("e", 5)

        ebProbe.expectVertxMsg(body = "e-1")
        ebProbe.expectVertxMsg(body = "e-2")
        ebProbe.expectVertxMsg(body = "e-3")
        ebProbe.expectVertxMsg(body = "e-4")
        ebProbe.expectVertxMsg(body = "e-5")
      }
      "redeliver all unconfirmed events" in {
        writeEvents("e", 2)

        ebProbe.expectVertxMsg(body = "e-1")
        ebProbe.expectVertxMsg(body = "e-2")

        ebProbe.expectVertxMsg(body = "e-1")
        ebProbe.expectVertxMsg(body = "e-2")

        ebProbe.expectVertxMsg(body = "e-1")
        ebProbe.expectVertxMsg(body = "e-2")
      }
      "redeliver only unconfirmed events" in {
        writeEvents("e", 5)

        ebProbe.expectVertxMsg(body = "e-1")
        ebProbe.expectVertxMsg(body = "e-2").confirm()
        ebProbe.expectVertxMsg(body = "e-3").confirm()
        ebProbe.expectVertxMsg(body = "e-4").confirm()
        ebProbe.expectVertxMsg(body = "e-5")

        ebProbe.expectVertxMsg(body = "e-1")
        ebProbe.expectVertxMsg(body = "e-5")
      }
      "redeliver only unconfirmed events while processing new events" in {
        writeEvents("e", 3)

        ebProbe.expectVertxMsg(body = "e-1")
        ebProbe.expectVertxMsg(body = "e-2").confirm()
        ebProbe.expectVertxMsg(body = "e-3")

        writeEvents("e", 2, start = 4)

        ebProbe.expectVertxMsg(body = "e-4").confirm()
        ebProbe.expectVertxMsg(body = "e-5")

        ebProbe.expectVertxMsg(body = "e-1")
        ebProbe.expectVertxMsg(body = "e-3")
        ebProbe.expectVertxMsg(body = "e-5")
      }
    }
  }
}
