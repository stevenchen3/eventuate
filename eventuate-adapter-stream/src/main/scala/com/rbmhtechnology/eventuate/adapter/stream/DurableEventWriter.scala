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

package com.rbmhtechnology.eventuate.adapter.stream

import akka.NotUsed
import akka.actor.ActorRef
import akka.pattern.ask
import akka.stream._
import akka.stream.scaladsl.Flow
import akka.stream.stage._
import akka.util.Timeout

import com.rbmhtechnology.eventuate._
import com.rbmhtechnology.eventuate.DurableEvent.UndefinedLogId
import com.rbmhtechnology.eventuate.ReplicationProtocol._

import scala.collection.immutable.Seq
import scala.concurrent._
import scala.concurrent.duration._
import scala.util._

object DurableEventWriter {
  def apply(eventLog: ActorRef, max: Int): Flow[DurableEvent, DurableEvent, NotUsed] = {
    Flow[DurableEvent]
      .batch(max, Seq(_)) { case (s, e) => s :+ e }
      .via(new DurableEventWriter(eventLog))
      .mapConcat(identity)
  }
}

class DurableEventWriter(eventLog: ActorRef) extends GraphStage[FlowShape[Seq[DurableEvent], Seq[DurableEvent]]] {
  val in = Inlet[Seq[DurableEvent]]("DurableEventWriter.in")
  val out = Outlet[Seq[DurableEvent]]("DurableEventWriter.out")

  override val shape = FlowShape.of(in, out)

  override def createLogic(attr: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    setHandler(in, new InHandler {
      implicit val writeTimeout = Timeout(10.seconds) // TODO: make configurable

      override def onPush(): Unit = {
        val events = grab(in)
        val callback = getAsyncCallback[Seq[DurableEvent]](push(out, _))
        write(events).onComplete {
          case Success(r) => callback.invoke(r)
          case Failure(e) => failStage(e)
        }(materializer.executionContext)
      }

      private def write(events: Seq[DurableEvent]): Future[Seq[DurableEvent]] = {
        eventLog.ask(ReplicationWrite(events.map(_.copy(processId = UndefinedLogId)), 0L, UndefinedLogId, VectorTime.Zero)).flatMap {
          case s: ReplicationWriteSuccess => Future.successful(s.events)
          case f: ReplicationWriteFailure => Future.failed(f.cause)
        }(materializer.executionContext)
      }
    })

    setHandler(out, new OutHandler {
      override def onPull(): Unit = pull(in)
    })
  }
}
