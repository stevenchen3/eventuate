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

import com.rbmhtechnology.eventuate.adapter.vertx.{LogAdapterService => BaseLogAdapterService, _}
import io.vertx.core._

object LogAdapterService {

  def create(logName: String, vertx: Vertx): LogAdapterService[Event] =
    new LogAdapterService[Event](BaseLogAdapterService(logName, vertx))

  def create(logName: String, vertx: Vertx, options: ServiceOptions): LogAdapterService[Event] =
    new LogAdapterService[Event](BaseLogAdapterService(logName, vertx, options))

  def create(logName: String, consumer: String, vertx: Vertx): LogAdapterService[ConfirmableEvent] =
    new LogAdapterService[ConfirmableEvent](BaseLogAdapterService(logName, consumer, vertx))

  def create(logName: String, consumer: String, vertx: Vertx, options: ServiceOptions): LogAdapterService[ConfirmableEvent] =
    new LogAdapterService[ConfirmableEvent](BaseLogAdapterService(logName, consumer, vertx, options))
}

class LogAdapterService[A <: Event] private[vertx](delegate: BaseLogAdapterService[A]) {

  import VertxHandlerConverters._

  def onEvent(handler: BiConsumer[A, EventSubscription]): EventSubscription = {
    delegate.onEvent(handler.accept)
  }

  def persist[E](event: E, handler: Handler[AsyncResult[E]]): Unit = {
    delegate.persist(event)(handler.asEventuateHandler)
  }
}
