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
import com.rbmhtechnology.eventuate.adapter.vertx.{LogAdapterService => BaseLogAdapterService, _}
import io.vertx.core.eventbus.Message
import io.vertx.rx.java.RxHelper
import io.vertx.rxjava.core.eventbus.{Message => RxMessage}
import io.vertx.rxjava.core.{Vertx => RxVertx}
import rx.Observable

object LogAdapterService {

  import VertxConverters._

  def create(logName: String, vertx: RxVertx): LogAdapterService[Event] =
    new LogAdapterService[Event](vertx, BaseLogAdapterService(logName, vertx))

  def create(logName: String, vertx: RxVertx, options: ServiceOptions): LogAdapterService[Event] =
    new LogAdapterService[Event](vertx, BaseLogAdapterService(logName, vertx, options))

  def create(logName: String, consumer: String, vertx: RxVertx): LogAdapterService[ConfirmableEvent] = {
    new LogAdapterService[ConfirmableEvent](vertx, BaseLogAdapterService(logName, consumer, vertx))
  }

  def create(logName: String, consumer: String, vertx: RxVertx, options: ServiceOptions): LogAdapterService[ConfirmableEvent] = {
    new LogAdapterService[ConfirmableEvent](vertx, BaseLogAdapterService(logName, consumer, vertx, options))
  }
}

class LogAdapterService[A <: Event] private[vertx](vertx: RxVertx, delegate: BaseLogAdapterService[A]) {

  import RxConverters._
  import VertxHandlerConverters._

  def onEvent(): Observable[A] = {
    vertx.eventBus().consumer[DurableEvent](delegate.logAdapterInfo.readAddress)
      .toObservable
      .map({ (m: RxMessage[DurableEvent]) =>
        delegate.toEvent(m.getDelegate.asInstanceOf[Message[DurableEvent]])
      }.asRx)
  }

  def persist[E](event: E): Observable[E] = {
    val observable = RxHelper.observableFuture[E]()
    delegate.persist(event)(observable.toHandler.asEventuateHandler)
    observable
  }
}
