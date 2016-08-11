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

import java.util.function.BiConsumer

import com.rbmhtechnology.eventuate.DurableEvent
import com.rbmhtechnology.eventuate.adapter.vertx._
import io.vertx.core.eventbus.Message
import io.vertx.core.{Handler, Vertx}

object LogAdapterService {

  def create(logName: String, vertx: Vertx): LogAdapterService[Event] =
    create(VertxEventbusEndpoint.publish(logName, Inbound), vertx)

  def create(logName: String, consumer: String, vertx: Vertx): LogAdapterService[ConfirmableEvent] =
    create(VertxEventbusEndpoint.send(logName, Inbound, consumer), vertx)

  private[vertx] def create(endpoint: VertxEventbusEndpoint, vertx: Vertx): LogAdapterService[Event] =
    new LogAdapterService[Event](vertx, endpoint, Event(_))

  private[vertx] def create(endpoint: VertxEventbusSendEndpoint, vertx: Vertx) : LogAdapterService[ConfirmableEvent] =
    new LogAdapterService[ConfirmableEvent](vertx, endpoint, m => Event.withConfirmation(m, endpoint, vertx))
}

class LogAdapterService[A <: Event] private[eventuate] (vertx: Vertx, eventbusEndpoint: VertxEventbusEndpoint, event: Message[DurableEvent] => A) {

  def onEvent(handler: BiConsumer[A, EventSubscription]): EventSubscription = {
    val messageConsumer = vertx.eventBus().consumer[DurableEvent](eventbusEndpoint.address)
    val sub = EventSubscription(messageConsumer)

    messageConsumer.handler(new Handler[Message[DurableEvent]] {
      override def handle(m: Message[DurableEvent]): Unit =
        handler.accept(event(m), sub)
    })
    sub
  }
}
