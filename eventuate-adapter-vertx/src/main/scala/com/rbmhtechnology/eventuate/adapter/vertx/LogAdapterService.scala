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

import java.lang.{Long => JLong}

import com.rbmhtechnology.eventuate.{DurableEvent, EventsourcedView}
import io.vertx.core.eventbus.{DeliveryOptions, Message}
import io.vertx.core.{AsyncResult, Vertx}

import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success}

class ConnectionException(message: String, cause: Throwable) extends RuntimeException(message, cause) {}

class PersistenceException(message: String, cause: Throwable) extends RuntimeException(message, cause) {}

object LogAdapterService {
  type EventHandler[A <: Event] = (A, EventSubscription) => Unit

  def apply(logName: String, vertx: Vertx): LogAdapterService[Event] =
    apply(logName, vertx, ServiceOptions())

  def apply(logName: String, vertx: Vertx, options: ServiceOptions): LogAdapterService[Event] =
    apply(LogAdapterInfo.publishAdapter(logName), vertx, options)

  def apply(logName: String, consumer: String, vertx: Vertx): LogAdapterService[ConfirmableEvent] =
    apply(logName, consumer, vertx, ServiceOptions())

  def apply(logName: String, consumer: String, vertx: Vertx, options: ServiceOptions): LogAdapterService[ConfirmableEvent] =
    apply(LogAdapterInfo.sendAdapter(logName, consumer), vertx, options)

  private[vertx] def apply(logAdapterInfo: LogAdapterInfo, vertx: Vertx, options: ServiceOptions): LogAdapterService[Event] =
    new LogAdapterService[Event](vertx, options, logAdapterInfo, Event(_))

  private[vertx] def apply(logAdapterInfo: SendLogAdapterInfo, vertx: Vertx, options: ServiceOptions): LogAdapterService[ConfirmableEvent] =
    new LogAdapterService[ConfirmableEvent](vertx, options, logAdapterInfo, m => Event.withConfirmation(m, logAdapterInfo, vertx))
}

class LogAdapterService[A <: Event] private(vertx: Vertx, options: ServiceOptions,
                                            private[vertx] val logAdapterInfo: LogAdapterInfo,
                                            private[vertx] val toEvent: Message[DurableEvent] => A) {

  import LogAdapterService._
  import VertxExtensions._
  import VertxHandlerConverters._

  private type ResultHandler[B] = EventsourcedView.Handler[B]
  private type MessageStash = List[(Any, ResultHandler[Any])]
  private type Persist = (Any, ResultHandler[Any]) => Unit

  private val context = vertx.getOrCreateContext()

  private var messageStash = List.empty[(Any, ResultHandler[Any])]
  private var persistBehaviour: Persist = connecting

  def onEvent(handler: EventHandler[A]): EventSubscription = {
    val messageConsumer = vertx.eventBus().consumer[DurableEvent](logAdapterInfo.readAddress)
    val sub = EventSubscription(messageConsumer)
    val onMessage = (m: Message[DurableEvent]) => handler(toEvent(m), sub)

    messageConsumer.handler(onMessage.asVertxHandler)
    sub
  }

  def persist[E](event: E)(handler: EventsourcedView.Handler[E]): Unit = {
    context.runOnContext(
      persistBehaviour(event, handler.asInstanceOf[ResultHandler[Any]]).asVertxHandler
    )
  }

  private def become(behaviour: Persist): Unit = {
    persistBehaviour = behaviour
  }

  private def connecting: Persist = { (event, handler) =>
    stashing(event, handler)
    become(stashing)

    tryConnect(now())
  }

  private def stashing: Persist = { (event, handler) =>
    messageStash = (event, handler) +: messageStash
  }

  private def tryConnect(timestamp: Long, delay: Option[FiniteDuration] = None): Unit = {
    def deliverMessages(messages: MessageStash): Unit = {
      messages.reverse.foreach { case (m, h) => sending(m, h) }
      messageStash = List.empty
    }

    def failMessages(messages: MessageStash, failure: Throwable): Unit = {
      messages.reverse.foreach { case (m, h) => failing(failure)(m, h) }
      messageStash = List.empty
    }

    def requestConnect(f: AsyncResult[Message[Unit]] => Unit): Unit = {
      vertx.eventBus().send[Unit](logAdapterInfo.writeAddress, null, new DeliveryOptions().addHeader(Headers.Action.Connect), f.asVertxHandler)
    }

    def doConnect(timestamp: Long): JLong => Unit = { _ =>
      requestConnect { result =>
        if (result.succeeded()) {
          deliverMessages(messageStash)
          become(sending)
        } else if (now() - timestamp < options.connectTimeout.toNanos) {
          tryConnect(timestamp, Some(options.connectInterval))
        } else {
          val failure = new ConnectionException(
            s"""Connection to writer for log '${logAdapterInfo.logName}' could not be established within ${options.connectTimeout}.
                |The log configuration may be incorrect or the VertxEventbusAdapter has not been started with 'activate()'."""
              .stripMargin.replaceAll("\n", " "), result.cause())

          failMessages(messageStash, failure)
          become(failing(failure))
        }
      }
    }

    delay match {
      case Some(d) =>
        vertx.setTimer(d.toMillis, doConnect(timestamp).asVertxHandler)
      case None =>
        doConnect(timestamp)(0L)
    }
  }

  private def sending: Persist = { (event, handler) =>
    val onMessage = (reply: AsyncResult[Message[Any]]) => {
      if (reply.succeeded()) {
        handler(Success(reply.result().body()))
      } else {
        handler(Failure(new PersistenceException(s"Event [$event] could not be persisted.", reply.cause())))
      }
    }
    vertx.eventBus().send[Any](logAdapterInfo.writeAddress, event, new DeliveryOptions().addHeader(Headers.Action.Persist), onMessage.asVertxHandler)
  }

  private def failing(failure: Throwable): Persist = { (event, handler) =>
    handler(Failure(failure))
  }

  private def now(): Long =
    System.nanoTime()
}
