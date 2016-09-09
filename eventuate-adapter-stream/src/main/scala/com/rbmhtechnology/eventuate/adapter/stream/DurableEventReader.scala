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

import akka.actor._
import akka.pattern.{ ask, pipe }
import akka.stream.actor.ActorPublisher
import akka.stream.actor.ActorPublisherMessage._
import akka.util.Timeout

import com.rbmhtechnology.eventuate.DurableEvent
import com.rbmhtechnology.eventuate.EventsourcingProtocol._

import scala.collection.immutable.Seq
import scala.concurrent.duration._

object DurableEventReader {
  case object Paused

  def props(eventLog: ActorRef, fromSequenceNr: Long = 1L, aggregateId: Option[String] = None): Props =
    Props(new DurableEventReader(eventLog, fromSequenceNr, aggregateId))
}

class DurableEventReader(eventLog: ActorRef, fromSequenceNr: Long, aggregateId: Option[String]) extends ActorPublisher[DurableEvent] with ActorLogging {
  import DurableEventReader._

  private val bufSize = 16
  private var buf: Vector[DurableEvent] = Vector.empty

  private var progress: Long = fromSequenceNr - 1L
  private var schedule: Option[Cancellable] = None

  import context.dispatcher

  def reading: Receive = {
    case ReplaySuccess(Seq(), _, _) =>
      schedule = Some(schedulePaused())
      context.become(pausing)
    case ReplaySuccess(events, to, _) =>
      buffer(events, to)
      if (emit()) {
        read()
      } else {
        context.become(waiting)
      }
    case ReplayFailure(cause, _) =>
      schedule = Some(schedulePaused())
      context.become(pausing)
      log.warning(s"reading from log failed: $cause")
  }

  def waiting: Receive = {
    case Request(_) if buf.isEmpty =>
      read()
      context.become(reading)
    case Request(_) =>
      if (emit()) {
        read()
        context.become(reading)
      }
  }

  def pausing: Receive = {
    case Paused =>
      schedule = None
      if (totalDemand > 0) {
        read()
        context.become(reading)
      } else {
        context.become(waiting)
      }
    case Written(_) =>
      schedule.foreach(_.cancel())
      schedule = None
      if (totalDemand > 0) {
        read()
        context.become(reading)
      } else {
        context.become(waiting)
      }
  }

  override def unhandled(message: Any): Unit = message match {
    case Cancel | SubscriptionTimeoutExceeded =>
      context.stop(self)
    case Terminated(`eventLog`) =>
      onCompleteThenStop()
    case other =>
      super.unhandled(other)
  }

  def receive = reading

  override def preStart(): Unit = {
    super.preStart()
    context.watch(eventLog)
    read(subscribe = true)
  }

  private def read(subscribe: Boolean = false): Unit = {
    implicit val timeout = Timeout(10.seconds)
    val subscriber = if (subscribe) Some(self) else None

    eventLog ? Replay(progress + 1L, bufSize, subscriber, aggregateId, 1) recover {
      case t => ReplayFailure(t, 1)
    } pipeTo self
  }

  private def emit(): Boolean = {
    val splitPos = if (totalDemand > Int.MaxValue) Int.MaxValue else totalDemand.toInt
    val (use, keep) = buf.splitAt(splitPos)

    use.foreach(onNext)
    buf = keep
    buf.isEmpty && totalDemand > 0L
  }

  private def buffer(events: Seq[DurableEvent], to: Long): Unit = {
    buf = events.foldLeft(buf)(_ :+ _)
    progress = to
  }

  private def schedulePaused(): Cancellable =
    context.system.scheduler.scheduleOnce(5.seconds, self, Paused)
}
