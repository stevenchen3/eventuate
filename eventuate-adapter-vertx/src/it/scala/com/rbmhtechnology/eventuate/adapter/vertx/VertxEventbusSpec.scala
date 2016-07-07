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

import akka.actor.ActorRef
import akka.testkit.{TestKit, TestProbe}
import com.rbmhtechnology.eventuate.DurableEvent
import com.rbmhtechnology.eventuate.adapter.vertx.LogAdapterService.EventHandler
import io.vertx.core.Vertx
import org.scalatest.{BeforeAndAfterEach, Suite}

trait VertxEventbusSpec extends BeforeAndAfterEach {
  this: TestKit with Suite =>

  val eventBusAddress = "eb-address"

  var vertx: Vertx = _
  var ebProbe: TestProbe = _

  override protected def beforeEach(): Unit = {
    super.beforeEach()

    vertx = Vertx.vertx()
    ebProbe = TestProbe()
  }

  def registerCodec(): Unit =
    vertx.eventBus().registerDefaultCodec(classOf[DurableEvent], DurableEventMessageCodec(system))

  def ebHandler: EventHandler =
    em => ebProbe.ref.tell(em.event, ActorRef.noSender)

  implicit class DurableEventConverter(durableEvent: DurableEvent) {
    def toEvent: Event = Event(durableEvent.payload, durableEvent.localSequenceNr)
  }
}
