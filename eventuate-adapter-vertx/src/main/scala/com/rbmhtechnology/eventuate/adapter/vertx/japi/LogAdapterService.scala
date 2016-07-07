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

package com.rbmhtechnology.eventuate.adapter.vertx.japi

import com.rbmhtechnology.eventuate.DurableEvent
import com.rbmhtechnology.eventuate.adapter.vertx._
import io.vertx.core.eventbus.Message
import io.vertx.core.{ Handler, Vertx }

object LogAdapterService {

  import VertxEventbusInfo._

  def create(logName: String, vertx: Vertx): LogAdapterService =
    new LogAdapterService(vertx, eventbusAddress(logName, Inbound))

  def create(logName: String, consumer: String, vertx: Vertx): LogAdapterService =
    new LogAdapterService(vertx, eventbusAddress(logName, Inbound, Some(consumer)))
}

class LogAdapterService private[eventuate] (vertx: Vertx, ebAddress: String) {

  def onEvent(handler: Handler[EventEnvelopeWithSubscription]): EventSubscription = {
    val messageConsumer = vertx.eventBus().consumer[DurableEvent](ebAddress)
    val sub = EventSubscription(messageConsumer)

    messageConsumer.handler(new Handler[Message[DurableEvent]] {
      override def handle(m: Message[DurableEvent]): Unit =
        handler.handle(EventEnvelope(m, sub))
    })
    sub
  }
}
