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
import akka.pattern.pipe
import com.rbmhtechnology.eventuate.{ ConfirmedDelivery, DurableEvent, EventsourcedActor }
import io.vertx.core.Vertx

import scala.concurrent.duration.FiniteDuration
import scala.util.{ Failure, Success }

private[vertx] object ReliableSingleConfirmationReadLogAdapter {
  case class DeliverEvent(durableEvent: DurableEvent, deliveryId: String)
  case class Confirm(deliveryId: String)
  case class DeliverFailed(durableEvent: DurableEvent, deliveryId: String, err: Throwable)
  case object Redeliver

  case class DeliveryConfirmed()

  def props(id: String, eventLog: ActorRef, logAdapterInfo: SendLogAdapterInfo, vertx: Vertx, deliveryDelay: FiniteDuration): Props =
    Props(new ReliableSingleConfirmationReadLogAdapter(id, eventLog, logAdapterInfo, vertx, deliveryDelay))
}

private[vertx] class ReliableSingleConfirmationReadLogAdapter(val id: String, val eventLog: ActorRef, val logAdapterInfo: SendLogAdapterInfo, val vertx: Vertx, deliveryDelay: FiniteDuration)
  extends EventsourcedActor with MessageSender with ConfirmedDelivery {

  import ReliableSingleConfirmationReadLogAdapter._
  import VertxExtensions._
  import context.dispatcher

  context.system.scheduler.schedule(deliveryDelay, deliveryDelay, self, Redeliver)

  override def onCommand: Receive = {
    case DeliverEvent(event, deliveryId) =>
      producer.sendFt[Unit](event)
        .map(_ => Confirm(deliveryId))
        .recover {
          case err => DeliverFailed(event, deliveryId, err)
        }
        .pipeTo(self)

    case Confirm(deliveryId) if unconfirmed.contains(deliveryId) =>
      persistConfirmation(DeliveryConfirmed(), deliveryId) {
        case Success(evt) =>
        case Failure(err) => throw new RuntimeException(err)
      }

    case DeliverFailed(event, deliveryId, err) =>
    // ignore

    case Redeliver =>
      redeliverUnconfirmed()
  }

  override def onEvent: Receive = {
    case DeliveryConfirmed() =>
    // confirmations should not be published
    case ev =>
      val deliveryId = lastSequenceNr.toString
      val event = lastHandledEvent
      deliver(deliveryId, DeliverEvent(event, deliveryId), self.path)
  }
}
