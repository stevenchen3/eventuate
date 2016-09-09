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

import akka.actor.ActorRef
import akka.pattern.ask
import akka.stream._
import akka.stream.stage._
import akka.util.Timeout

import com.rbmhtechnology.eventuate._
import com.rbmhtechnology.eventuate.DurableEvent.UndefinedLogId
import com.rbmhtechnology.eventuate.ReplicationProtocol._

import scala.collection.immutable.Seq
import scala.concurrent._
import scala.concurrent.duration._
import scala.util._

class DurableEventWriter(eventLog: ActorRef) extends GraphStage[FlowShape[DurableEvent, DurableEvent]] {
  val in = Inlet[DurableEvent]("DurableEventWriter.in")
  val out = Outlet[DurableEvent]("DurableEventWriter.out")

  override val shape = FlowShape.of(in, out)

  override def createLogic(attr: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    implicit val writeTimeout = Timeout(10.seconds)

    setHandler(in, new InHandler {
      override def onPush(): Unit = {
        val inEvent = grab(in).copy(processId = UndefinedLogId)
        val callback = getAsyncCallback[Option[DurableEvent]] {
          case Some(outEvent) => push(out, outEvent)
          case None           => pull(in)
        }
        write(inEvent).onComplete {
          case Success(r) => callback.invoke(r)
          case Failure(e) => e.printStackTrace() // FIXME
        }(materializer.executionContext)
      }
      private def write(event: DurableEvent): Future[Option[DurableEvent]] = {
        eventLog.ask(ReplicationWrite(Seq(event), event.localSequenceNr, event.localLogId, VectorTime.Zero)).flatMap {
          case s: ReplicationWriteSuccess => Future.successful(s.events.headOption)
          case f: ReplicationWriteFailure => Future.failed(f.cause)
        }(materializer.executionContext)
      }
    })
    setHandler(out, new OutHandler {
      override def onPull(): Unit = {
        pull(in)
      }
    })
  }
}
