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

import com.rbmhtechnology.eventuate.{ DurableEvent, EventsourcedWriter }
import io.vertx.core.Vertx
import io.vertx.core.eventbus.{ MessageProducer => VertxMessageProducer }

import scala.concurrent.{ ExecutionContext, Future }

trait MessageProducer {
  def vertx: Vertx
  def logAdapterInfo: LogAdapterInfo
  def producer: VertxMessageProducer[DurableEvent]
}

trait MessagePublisher extends MessageProducer {
  override lazy val producer = vertx.eventBus().publisher[DurableEvent](logAdapterInfo.readAddress)
}

trait MessageSender extends MessageProducer {
  override lazy val producer = vertx.eventBus().sender[DurableEvent](logAdapterInfo.readAddress)
}

trait MessageDelivery extends MessageProducer {
  def deliver(events: Vector[DurableEvent])(implicit ec: ExecutionContext): Future[Unit]
}

trait UnboundDelivery extends MessageDelivery {
  override def deliver(events: Vector[DurableEvent])(implicit ec: ExecutionContext): Future[Unit] =
    Future(events.foreach(producer.write))
}

trait ReactiveDelivery extends MessageDelivery {
  override def deliver(events: Vector[DurableEvent])(implicit ec: ExecutionContext): Future[Unit] = ??? // TODO

  def backpressureOptions: BackpressureOptions

  producer.setWriteQueueMaxSize(backpressureOptions.maxItems)
}

trait ProgressStore[R, W] {
  def writeProgress(id: String, snr: Long)(implicit executionContext: ExecutionContext): Future[W]

  def readProgress(id: String)(implicit executionContext: ExecutionContext): Future[R]

  def progress(result: R): Long
}

trait SequenceNumberProgressStore extends ProgressStore[Long, Long] {
  def storageProvider: StorageProvider

  override def writeProgress(id: String, snr: Long)(implicit executionContext: ExecutionContext): Future[Long] =
    storageProvider.writeProgress(id, snr)

  override def readProgress(id: String)(implicit executionContext: ExecutionContext): Future[Long] =
    storageProvider.readProgress(id)

  override def progress(result: Long): Long =
    result
}

trait ReadLogAdapter[R, W] extends EventsourcedWriter[R, W] with MessageDelivery with ProgressStore[R, W] {
  import context.dispatcher

  var events: Vector[DurableEvent] = Vector.empty

  override def onCommand: Receive = {
    case _ =>
  }

  override def onEvent: Receive = {
    case event => events = events :+ lastHandledEvent
  }

  override def write(): Future[W] = {
    val snr = lastSequenceNr
    val ft = deliver(events).flatMap(x => writeProgress(id, snr))

    events = Vector.empty
    ft
  }

  override def read(): Future[R] =
    readProgress(id)

  override def readSuccess(result: R): Option[Long] =
    Some(progress(result) + 1L)
}
