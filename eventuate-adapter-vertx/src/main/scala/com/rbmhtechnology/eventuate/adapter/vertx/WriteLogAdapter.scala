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
import com.rbmhtechnology.eventuate.EventsourcedActor
import io.vertx.core.eventbus.Message
import io.vertx.core.{ Vertx, Handler => VertxHandler }

import scala.util.{ Failure, Success }

private[vertx] object WriteLogAdapter {

  case class PersistEvent(event: Any, message: Message[Any])

  def props(id: String, eventLog: ActorRef, logAdapterInfo: LogAdapterInfo, vertx: Vertx): Props =
    Props(new WriteLogAdapter(id, eventLog, logAdapterInfo, vertx))
}

private[vertx] class WriteLogAdapter(val id: String, val eventLog: ActorRef, logAdapterInfo: LogAdapterInfo, vertx: Vertx) extends EventsourcedActor {

  import WriteLogAdapter._
  import VertxExtensions._

  var messageConsumer = vertx.eventBus().localConsumer[Any](logAdapterInfo.writeAddress, new VertxHandler[Message[Any]] {
    override def handle(message: Message[Any]): Unit = {
      message.headers().getHeaderValue(Headers.Action) match {
        case Some(Headers.Action.Persist) =>
          self ! PersistEvent(message.body(), message)
        case Some(Headers.Action.Connect) =>
          message.reply("ok")
        case Some(action) =>
          message.fail(0, s"Action '$action' is not supported.")
        case _ =>
          message.fail(0, "Header 'action' is missing.")
      }
    }
  })

  override def stateSync: Boolean = false

  override def onCommand: Receive = {
    case PersistEvent(evt, msg) =>
      persist(evt) {
        case Success(res) => msg.reply(res)
        case Failure(err) => msg.fail(0, err.getMessage)
      }
  }

  override def onEvent: Receive = {
    case _ =>
  }

  override def postStop(): Unit = {
    super.postStop()
    messageConsumer.unregister()
  }
}
