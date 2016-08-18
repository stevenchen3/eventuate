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

import scala.util.{Failure, Success}

trait ActorLogAdapterService {
  this: VertxEventbus =>

  val logName = "log-A"
  val consumer = "consumer1"

  def notifyOnPublishEvent(eventNotifier: ActorRef, sourceLog: String = logName, options: ServiceOptions = ServiceOptions()): Unit = {
    val service = LogAdapterService.apply(sourceLog, vertx, options)
    service.onEvent((ev, sub) => eventNotifier ! ev)
  }

  def notifyOnConfirmableEvent(eventNotifier: ActorRef, sourceLog: String = logName, consumer: String = consumer, options: ServiceOptions = ServiceOptions()): Unit = {
    val service = LogAdapterService.apply(sourceLog, consumer, vertx, options)
    service.onEvent((ev, sub) => eventNotifier ! ev)
  }

  def createPersist(eventNotifier: ActorRef, sourceLog: String = logName, options: ServiceOptions = ServiceOptions()): (Any) => Unit = {
    val service = LogAdapterService.apply(sourceLog, vertx, options)

    (event: Any) => service.persist(event) {
      case s@Success(res) => eventNotifier ! s
      case f@Failure(err) => eventNotifier ! f
    }
  }
}
