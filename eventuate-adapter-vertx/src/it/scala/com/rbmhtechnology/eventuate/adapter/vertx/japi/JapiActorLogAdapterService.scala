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

import akka.actor.ActorRef
import com.rbmhtechnology.eventuate.adapter.vertx.japi.{LogAdapterService => JLogAdapterService}
import com.rbmhtechnology.eventuate.adapter.vertx.{ActorLogAdapterService, ConfirmableEvent, Event, EventSubscription, ServiceOptions, VertxEventbus}
import io.vertx.core.{AsyncResult, Handler}

import scala.util.{Failure, Success}

trait JapiActorLogAdapterService extends ActorLogAdapterService {
  this: VertxEventbus =>

  override def notifyOnPublishEvent(eventNotifier: ActorRef, sourceLog: String, options: ServiceOptions): Unit = {
    val service = JLogAdapterService.create(sourceLog, vertx, options)
    service.onEvent(new BiConsumer[Event, EventSubscription] {
      override def accept(ev: Event, s: EventSubscription): Unit = eventNotifier ! ev
    })
  }

  override def notifyOnConfirmableEvent(eventNotifier: ActorRef, sourceLog: String, consumer: String, options: ServiceOptions): Unit = {
    val service = JLogAdapterService.create(sourceLog, consumer, vertx, options)
    service.onEvent(new BiConsumer[ConfirmableEvent, EventSubscription] {
      override def accept(ev: ConfirmableEvent, s: EventSubscription): Unit = eventNotifier ! ev
    })
  }

  override def createPersist(eventNotifier: ActorRef, sourceLog: String, options: ServiceOptions): (Any) => Unit = {
    val service = JLogAdapterService.create(sourceLog, vertx, options)

    (event: Any) => service.persist(event, new Handler[AsyncResult[Any]] {
      override def handle(res: AsyncResult[Any]): Unit = {
        if (res.succeeded()) {
          eventNotifier ! Success(res.result())
        } else {
          eventNotifier ! Failure(res.cause())
        }
      }
    })
  }
}
