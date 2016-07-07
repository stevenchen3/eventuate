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

import akka.actor.{ ActorRef, Props }
import com.rbmhtechnology.eventuate.EventsourcedWriter
import io.vertx.core.Vertx

import scala.concurrent.Future

private[vertx] object ReliableReadLogAdapter {
  def props(id: String, eventLog: ActorRef, consumer: String, vertx: Vertx, storageProvider: StorageProvider): Props =
    Props(new ReliableReadLogAdapter(id, eventLog, consumer, vertx, storageProvider))
}

private[vertx] class ReliableReadLogAdapter(val id: String, val eventLog: ActorRef, consumer: String, vertx: Vertx, storageProvider: StorageProvider)
  extends EventsourcedWriter[Long, Long] {

  /**
   * Event handler.
   */
  override def onEvent: Receive = {
    case event => lastHandledEvent
  }

  /**
   * Command handler.
   */
  override def onCommand: Receive = ???

  /**
   * Asynchronously writes an incremental update to the target database. Incremental updates are prepared
   * during event processing by a concrete `onEvent` handler.
   *
   * During event replay, this method is called latest after having replayed `eventuate.log.replay-batch-size`
   * events and immediately after replay completes. During live processing, `write` is called immediately if
   * no write operation is in progress and an event has been handled by `onEvent`. If a write operation is in
   * progress, further event handling may run concurrently to that operation. If events are handled while a
   * write operation is in progress, another write will follow immediately after the previous write operation
   * completes.
   */
  override def write(): Future[Long] = ???

  /**
   * Asynchronously reads an initial value from the target database, usually to obtain information about
   * event processing progress. This method is called during initialization.
   */
  override def read(): Future[Long] = ???
}
