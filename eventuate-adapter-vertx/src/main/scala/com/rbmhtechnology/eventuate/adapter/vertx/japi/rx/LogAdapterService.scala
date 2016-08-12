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
  import scala.language.implicitConversions

  def create(logName: String, vertx: Vertx): LogAdapterService[Event] =
    new LogAdapterService[Event](vertx, LogAdapterInfo.publishAdapter(logName), Event(_))

  def create(logName: String, consumer: String, vertx: Vertx): LogAdapterService[ConfirmableEvent] = {
    val logAdapterInfo = LogAdapterInfo.sendAdapter(logName, consumer)
    new LogAdapterService[ConfirmableEvent](vertx, logAdapterInfo, m => Event.withConfirmation(m, logAdapterInfo, vertx.getDelegate.asInstanceOf[CoreVertx]))
  }

  implicit def function1ToRxFunc1[A, B](fn: A => B): Func1[A, B] = new Func1[A, B] {
    override def call(a: A): B = fn(a)
  }
}

class LogAdapterService[A <: Event] private[eventuate] (vertx: Vertx, logAdapterInfo: LogAdapterInfo, event: Message[DurableEvent] => A) {
  import LogAdapterService._

  def onEvent(): Observable[A] = {
    vertx.eventBus().consumer[DurableEvent](logAdapterInfo.readAddress)
      .toObservable
      .map((m: RxMessage[DurableEvent]) => event(m.getDelegate.asInstanceOf[Message[DurableEvent]]))
  }

  def persist[E](event: E): Observable[E] = {
    vertx.eventBus().sendObservable[E](logAdapterInfo.writeAddress, event)
      .map((m: RxMessage[E]) => m.body())
  }
}
