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

import com.rbmhtechnology.eventuate.adapter.vertx.japi.{ LogAdapterService => JLogAdapterService }
import io.vertx.core.{ Handler, Vertx }

object LogAdapterService {
  import VertxEventbusInfo._

  type EventHandler = EventEnvelopeWithSubscription => Unit

  def apply(logName: String, vertx: Vertx): LogAdapterService =
    new LogAdapterService(vertx, eventbusAddress(logName, Inbound))

  def apply(logName: String, consumer: String, vertx: Vertx): LogAdapterService =
    new LogAdapterService(vertx, eventbusAddress(logName, Inbound, Some(consumer)))
}

class LogAdapterService private[eventuate] (vertx: Vertx, ebAddress: String) {
  import LogAdapterService._

  private val delegate = new JLogAdapterService(vertx, ebAddress)

  def onEvent(handler: EventHandler): EventSubscription = {
    delegate.onEvent(new Handler[EventEnvelopeWithSubscription] {
      override def handle(event: EventEnvelopeWithSubscription): Unit = handler.apply(event)
    })
  }
}
