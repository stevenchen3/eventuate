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

import akka.testkit.{TestKit, TestProbe}
import io.vertx.core.Vertx
import io.vertx.core.eventbus.Message
import org.scalatest.{BeforeAndAfterEach, Suite}

import scala.concurrent.duration.Duration
import scala.reflect.ClassTag
import scala.util.Failure

trait VertxEventbus extends BeforeAndAfterEach {
  this: TestKit with Suite =>

  import VertxHandlerConverters._

  case class VertxEventBusMessage[T](body: T, message: Message[T]) {
    def confirm(): Unit = {
      message.reply(null)
    }
  }

  val registerEventBusCodec = true

  val endpoint1 = "vertx-endpoint1"
  val endpoint2 = "vertx-endpoint2"
  val endpoint3 = "vertx-endpoint3"

  var vertx: Vertx = _

  override def beforeEach(): Unit = {
    super.beforeEach()
    vertx = Vertx.vertx()

    if (registerEventBusCodec) {
      registerCodec()
    }
  }

  def registerCodec(): Unit = {
    vertx.eventBus().registerCodec(AkkaSerializationMessageCodec(system))
  }

  def eventBusProbe(endpoint: String): TestProbe = {
    val probe = TestProbe()
    val handler = (m: Message[String]) => probe.ref ! VertxEventBusMessage(m.body(), m)
    vertx.eventBus().consumer[String](endpoint, handler.asVertxHandler)
    probe
  }

  implicit class VertxTestProbe(probe: TestProbe) {
    def expectVertxMsg[T](body: T, max: Duration = Duration.Undefined)(implicit t: ClassTag[T]): VertxEventBusMessage[T] = {
      probe.expectMsgPF[VertxEventBusMessage[T]](max, hint = s"VertxEventBusMessage($body, _)") {
        case m: VertxEventBusMessage[T] if m.body == body => m
      }
    }

    def receiveNVertxMsg[T](n: Int): Seq[VertxEventBusMessage[T]] =
      probe.receiveN(n).asInstanceOf[Seq[VertxEventBusMessage[T]]]

    def expectFailure[T](max: Duration = Duration.Undefined)(implicit t: ClassTag[T]): T = {
      probe.expectMsgPF[T](max, hint = s"Failure($t)") {
        case f@Failure(err:T) => err
      }
    }
  }
}

trait VertxEventbusProbes extends BeforeAndAfterEach {
  this: TestKit with Suite with VertxEventbus =>

  var endpoint1Probe: TestProbe = _
  var endpoint2Probe: TestProbe = _
  var endpoint3Probe: TestProbe = _

  override def beforeEach(): Unit = {
    super.beforeEach()

    endpoint1Probe = eventBusProbe(endpoint1)
    endpoint2Probe = eventBusProbe(endpoint2)
    endpoint3Probe = eventBusProbe(endpoint3)
  }
}
