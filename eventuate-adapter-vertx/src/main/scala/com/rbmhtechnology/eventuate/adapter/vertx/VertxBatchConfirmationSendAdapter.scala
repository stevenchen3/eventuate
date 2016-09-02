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

import java.util.concurrent.TimeoutException

import akka.actor.{ ActorRef, Props }
import akka.pattern.after
import io.vertx.core.Vertx

import scala.collection.immutable.Seq
import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }

private[vertx] object VertxBatchConfirmationSendAdapter {

  def props(id: String, eventLog: ActorRef, endpointResolver: VertxEndpointResolver, vertx: Vertx, storageProvider: StorageProvider, batchSize: Int, confirmationTimeout: FiniteDuration): Props =
    Props(new VertxBatchConfirmationSendAdapter(id, eventLog, endpointResolver, vertx, storageProvider, batchSize, confirmationTimeout))
}

private[vertx] class VertxBatchConfirmationSendAdapter(val id: String, val eventLog: ActorRef, val endpointResolver: VertxEndpointResolver, val vertx: Vertx, val storageProvider: StorageProvider, batchSize: Int, confirmationTimeout: FiniteDuration)
  extends VertxReadAdapter[Long, Long] with MessageSender with SequenceNumberProgressStore {

  import VertxExtensions._

  override def replayBatchSize: Int = batchSize

  override def deliver(events: Vector[Any])(implicit ec: ExecutionContext): Future[Unit] = {
    Future.firstCompletedOf(Seq(
      deliverEventsWithConfirmation(events),
      timeoutFt(confirmationTimeout)))
  }

  def deliverEventsWithConfirmation(events: Vector[Any])(implicit ec: ExecutionContext): Future[Unit] = {
    Future.sequence(events.map(producer.sendFt[Unit](_))).map(_ => Unit)
  }

  private def timeoutFt(delay: FiniteDuration)(implicit ec: ExecutionContext): Future[Unit] =
    after(delay, context.system.scheduler)(Future.failed(new TimeoutException(s"Delivery timeout of $delay reached.")))
}
