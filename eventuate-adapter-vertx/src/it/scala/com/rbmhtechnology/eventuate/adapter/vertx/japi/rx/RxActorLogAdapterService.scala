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

import akka.actor.ActorRef
import com.rbmhtechnology.eventuate.adapter.vertx.japi.rx.{LogAdapterService => RxLogAdapterService}
import com.rbmhtechnology.eventuate.adapter.vertx.{ActorLogAdapterService, ServiceOptions, VertxEventbus}
import rx.functions.Action1

import scala.util.{Failure, Success}

trait RxActorLogAdapterService extends ActorLogAdapterService {
  this: VertxEventbus =>

  import com.rbmhtechnology.eventuate.adapter.vertx.VertxConverters._

  override def notifyOnPublishEvent(eventNotifier: ActorRef, sourceLog: String, options: ServiceOptions): Unit = {
    val service = RxLogAdapterService.create(sourceLog, vertx, options)
    service.onEvent()
      .subscribe(new Action1[Any] {
        override def call(res: Any): Unit = eventNotifier ! res
      })
  }

  override def notifyOnConfirmableEvent(eventNotifier: ActorRef, sourceLog: String, consumer: String, options: ServiceOptions): Unit = {
    val service = RxLogAdapterService.create(sourceLog, consumer, vertx, options)
    service.onEvent()
      .subscribe(new Action1[Any] {
        override def call(res: Any): Unit = eventNotifier ! res
      })
  }

  override def createPersist(eventNotifier: ActorRef, sourceLog: String, options: ServiceOptions): (Any) => Unit = {
    val service = RxLogAdapterService.create(sourceLog, vertx, options)

    (event: Any) => service.persist(event)
      .subscribe(
        new Action1[Any] {
          override def call(res: Any): Unit = eventNotifier ! Success(res)
        },
        new Action1[Throwable] {
          override def call(err: Throwable): Unit = eventNotifier ! Failure(err)
        }
      )
  }
}
