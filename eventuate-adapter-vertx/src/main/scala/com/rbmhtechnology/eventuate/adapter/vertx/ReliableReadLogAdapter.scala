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
import com.rbmhtechnology.eventuate.DurableEvent
import io.vertx.core.eventbus.Message
import io.vertx.core.{ Vertx, Handler => VertxHandler }

import scala.collection.immutable.SortedMap
import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }

private[vertx] object ReliableReadLogAdapter {

  sealed trait ConfirmationStatus
  case object Unconfirmed extends ConfirmationStatus
  case object Confirmed extends ConfirmationStatus
  case class DeliveryAttempt(event: DurableEvent, status: ConfirmationStatus)

  case class Confirm(sequenceNr: Long)
  case object Redeliver

  case class WriteSuccess(sequenceNr: Long)
  case class WriteFailure(failure: Throwable)

  def props(id: String, eventLog: ActorRef, eventbusEndpoint: VertxEventbusSendEndpoint, vertx: Vertx, storageProvider: StorageProvider, deliveryDelay: FiniteDuration): Props =
    Props(new ReliableReadLogAdapter(id, eventLog, eventbusEndpoint, vertx, storageProvider, deliveryDelay))
}

private[vertx] class ReliableReadLogAdapter(val id: String, val eventLog: ActorRef, val eventbusEndpoint: VertxEventbusSendEndpoint, val vertx: Vertx, storageProvider: StorageProvider, deliveryDelay: FiniteDuration)
  extends ReadLogAdapter[Long, Long] with MessageSender with UnboundDelivery {

  import ReliableReadLogAdapter._
  import context.dispatcher

  var deliveryAttempts: SortedMap[Long, DeliveryAttempt] = SortedMap.empty
  var redeliverFuture: Future[Unit] = Future.successful(Unit)

  val messageConsumer = vertx.eventBus().localConsumer[Long](eventbusEndpoint.confirmationEndpoint.address, new VertxHandler[Message[Long]] {
    override def handle(event: Message[Long]): Unit = {
      self ! Confirm(event.body())
    }
  })

  override def onCommand: Receive = {
    case Confirm(sequenceNr) if deliveryAttempts.get(sequenceNr).exists(_.status == Unconfirmed) =>
      val updated = confirm(sequenceNr)

      if (updated.size != deliveryAttempts.size) {
        storageProvider.writeProgress(id, updated.headOption.map(_._1 - 1).getOrElse(deliveryAttempts.last._1))
          .onFailure {
            case err => self ! WriteFailure(err)
          }
      }
      deliveryAttempts = updated

    case WriteFailure(err) =>
      writeFailure(err)

    case Redeliver =>
      redeliverFuture = deliver(deliveryAttempts.values.filter(_.status == Unconfirmed).map(_.event).toVector)
      redeliverFuture.onComplete(_ => scheduleRedelivery())
  }

  override def onDurableEvent(lastHandledEvent: DurableEvent): Unit = {
    deliveryAttempts = deliveryAttempts + (lastHandledEvent.localSequenceNr -> DeliveryAttempt(lastHandledEvent, Unconfirmed))
  }

  override def writeProgress(id: String, snr: Long)(implicit executionContext: ExecutionContext): Future[Long] =
    redeliverFuture.map(_ => snr)

  override def readProgress(id: String)(implicit executionContext: ExecutionContext): Future[Long] =
    storageProvider.readProgress(id)

  override def progress(result: Long): Long =
    result

  override def preStart(): Unit = {
    super.preStart()
    scheduleRedelivery()
  }

  override def postStop(): Unit = {
    super.postStop()
    messageConsumer.unregister()
  }

  private def confirm(sequenceNr: Long): SortedMap[Long, DeliveryAttempt] =
    deliveryAttempts + (sequenceNr -> DeliveryAttempt(null, Confirmed)) dropWhile { case (_, attempt) => attempt.status == Confirmed }

  private def scheduleRedelivery(): Unit = {
    context.system.scheduler.scheduleOnce(deliveryDelay, self, Redeliver)
  }
}
