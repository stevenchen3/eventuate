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
import com.rbmhtechnology.eventuate.{ ConfirmedDelivery, DurableEvent, EventsourcedActor }
import io.vertx.core.eventbus.Message
import io.vertx.core.{ Vertx, Handler => VertxHandler }

import scala.concurrent.duration.FiniteDuration
import scala.util.{ Failure, Success }

private[vertx] object ReliableReadLogAdapterWithConfirmedDelivery {
  case class DeliverEvent(durableEvent: DurableEvent)
  case class Confirm(deliveryId: String)
  case object Redeliver

  case class DeliveryConfirmed()

  def props(id: String, eventLog: ActorRef, eventbusEndpoint: VertxEventbusSendEndpoint, vertx: Vertx, deliveryDelay: FiniteDuration): Props =
    Props(new ReliableReadLogAdapterWithConfirmedDelivery(id, eventLog, eventbusEndpoint, vertx, deliveryDelay))
}

private[vertx] class ReliableReadLogAdapterWithConfirmedDelivery(val id: String, val eventLog: ActorRef, val eventbusEndpoint: VertxEventbusSendEndpoint, val vertx: Vertx, deliveryDelay: FiniteDuration)
  extends EventsourcedActor with MessageSender with ConfirmedDelivery {

  import ReliableReadLogAdapterWithConfirmedDelivery._
  import context.dispatcher

  val messageConsumer = vertx.eventBus().localConsumer[Long](eventbusEndpoint.confirmationEndpoint.address, new VertxHandler[Message[Long]] {
    override def handle(event: Message[Long]): Unit = {
      self ! Confirm(event.body().toString)
    }
  })

  context.system.scheduler.schedule(deliveryDelay, deliveryDelay, self, Redeliver)

  override def onCommand: Receive = {
    case DeliverEvent(event) =>
      producer.send(event)

    case Confirm(deliveryId) if unconfirmed.contains(deliveryId) =>
      persistConfirmation(DeliveryConfirmed(), deliveryId) {
        case Success(evt) =>
        case Failure(err) => throw new RuntimeException(err)
      }

    case Redeliver =>
      redeliverUnconfirmed()
  }

  override def onEvent: Receive = {
    case DeliveryConfirmed() =>
    // do nothing
    case ev =>
      val deliveryId = lastSequenceNr.toString
      val event = lastHandledEvent
      deliver(deliveryId, DeliverEvent(event), self.path)
  }

  override def postStop(): Unit = {
    super.postStop()
    messageConsumer.unregister()
  }
}
