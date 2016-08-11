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

package com.rbmhtechnology.eventuate.adapter.vertx.japi.rx

import com.rbmhtechnology.eventuate.DurableEvent
import com.rbmhtechnology.eventuate.adapter.vertx._
import io.vertx.core.eventbus.Message
import io.vertx.core.{Vertx => CoreVertx}
import io.vertx.rxjava.core.Vertx
import io.vertx.rxjava.core.eventbus.{Message => RxMessage}
import rx.Observable
import rx.functions.Func1

object LogAdapterService {

  def create(logName: String, vertx: Vertx): LogAdapterService[Event] =
    new LogAdapterService[Event](vertx, VertxEventbusEndpoint.publish(logName, Inbound), Event(_))

  def create(logName: String, consumer: String, vertx: Vertx): LogAdapterService[ConfirmableEvent] = {
    val endpoint = VertxEventbusEndpoint.send(logName, Inbound, consumer)
    new LogAdapterService[ConfirmableEvent](vertx, endpoint, m => Event.withConfirmation(m, endpoint, vertx.getDelegate.asInstanceOf[CoreVertx]))
  }
}

class LogAdapterService[A <: Event] private[eventuate] (vertx: Vertx, eventbusEndpoint: VertxEventbusEndpoint, event: Message[DurableEvent] => A) {

  def onEvent(): Observable[A] = {
    vertx.eventBus().consumer[DurableEvent](eventbusEndpoint.address)
      .toObservable
      .map(new Func1[RxMessage[DurableEvent], A] {
        override def call(m: RxMessage[DurableEvent]): A = event(m.getDelegate.asInstanceOf[Message[DurableEvent]])
      })
  }
}
