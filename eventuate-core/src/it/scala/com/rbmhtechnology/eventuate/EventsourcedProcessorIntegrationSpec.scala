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

package com.rbmhtechnology.eventuate

import akka.actor._
import akka.testkit._
import org.scalatest._

import scala.util._

object EventsourcedProcessorIntegrationSpec {
  class SampleActor(val id: String, val eventLog: ActorRef, probe: ActorRef) extends EventsourcedActor {
    override def onCommand = {
      case s: String => persist(s) {
        case Success(_) =>
        case Failure(_) =>
      }
    }

    override def onEvent = {
      case s: String => probe ! ((s, lastVectorTimestamp))
    }
  }

  class StatelessSampleProcessor(val id: String, val eventLog: ActorRef, val targetEventLog: ActorRef, eventProbe: ActorRef, progressProbe: ActorRef) extends EventsourcedProcessor {
    override def onCommand = {
      case "boom" =>
        eventProbe ! "died"
        throw IntegrationTestException
      case "snap" => save("") {
        case Success(_) => eventProbe ! "snapped"
        case Failure(_) =>
      }
    }

    override val processEvent: Process = {
      case s: String if !s.contains("processed") =>
        eventProbe ! s
        List(s"${s}-processed-1", s"${s}-processed-2")
    }

    override def onSnapshot = {
      case _ =>
    }

    override def writeSuccess(progress: Long): Unit = {
      super.writeSuccess(progress)
      progressProbe ! progress
    }
  }

  class StatefulSampleProcessor(id: String, eventLog: ActorRef, targetEventLog: ActorRef, eventProbe: ActorRef, progressProbe: ActorRef)
    extends StatelessSampleProcessor(id, eventLog, targetEventLog, eventProbe, progressProbe) with StatefulProcessor
}

trait EventsourcedProcessorIntegrationSpec extends TestKitBase with WordSpecLike with Matchers with SingleLocationSpec {
  import EventsourcedProcessorIntegrationSpec._

  def logProps(logId: String): Props

  def sourceLog = log
  def sourceLogId = logId

  var targetLog: ActorRef = _
  var targetLogId: String = _

  var sourceProbe: TestProbe = _
  var targetProbe: TestProbe = _

  var processorEventProbe: TestProbe = _
  var processorProgressProbe: TestProbe = _

  var a1: ActorRef = _
  var a2: ActorRef = _

  def init(): Unit = {
    targetLogId = s"${logId}_target"
    targetLog = system.actorOf(logProps(targetLogId))

    sourceProbe = TestProbe()
    targetProbe = TestProbe()

    processorEventProbe = TestProbe()
    processorProgressProbe = TestProbe()

    a1 = system.actorOf(Props(new SampleActor("a1", sourceLog, sourceProbe.ref)))
    a2 = system.actorOf(Props(new SampleActor("a2", targetLog, targetProbe.ref)))
  }

  def statelessProcessor(): ActorRef =
    system.actorOf(Props(new StatelessSampleProcessor("p", sourceLog, targetLog, processorEventProbe.ref, processorProgressProbe.ref)))

  def statefulProcessor(sourceLog: ActorRef, targetLog: ActorRef): ActorRef =
    system.actorOf(Props(new StatefulSampleProcessor("p", sourceLog, targetLog, processorEventProbe.ref, processorProgressProbe.ref)))

  def waitForProgressWrite(progress: Long): Unit = {
    processorProgressProbe.fishForMessage() {
      case `progress` => true
      case n: Long    => false
    }
  }

  "A StatefulProcessor" must {
    "write processed events to a target log and recover from scratch" in {
      val p = statefulProcessor(sourceLog, targetLog)

      a1 ! "a"
      a1 ! "b"
      a1 ! "c"

      processorEventProbe.expectMsg("a")
      processorEventProbe.expectMsg("b")
      processorEventProbe.expectMsg("c")

      targetProbe.expectMsg(("a-processed-1", VectorTime(sourceLogId -> 1L, targetLogId -> 1L)))
      targetProbe.expectMsg(("a-processed-2", VectorTime(sourceLogId -> 1L, targetLogId -> 2L)))
      targetProbe.expectMsg(("b-processed-1", VectorTime(sourceLogId -> 2L, targetLogId -> 3L)))
      targetProbe.expectMsg(("b-processed-2", VectorTime(sourceLogId -> 2L, targetLogId -> 4L)))
      targetProbe.expectMsg(("c-processed-1", VectorTime(sourceLogId -> 3L, targetLogId -> 5L)))
      targetProbe.expectMsg(("c-processed-2", VectorTime(sourceLogId -> 3L, targetLogId -> 6L)))

      waitForProgressWrite(3L)

      p ! "boom"

      processorEventProbe.expectMsg("died") // Fix #306: Make sure "boom" is processed before "d"

      a1 ! "d"

      processorEventProbe.expectMsg("a")
      processorEventProbe.expectMsg("b")
      processorEventProbe.expectMsg("c")
      processorEventProbe.expectMsg("d")

      targetProbe.expectMsg(("d-processed-1", VectorTime(sourceLogId -> 4L, targetLogId -> 7L)))
      targetProbe.expectMsg(("d-processed-2", VectorTime(sourceLogId -> 4L, targetLogId -> 8L)))
    }
    "write processed events to a target log and recover from snapshot" in {
      val p = statefulProcessor(sourceLog, targetLog)

      a1 ! "a"
      a1 ! "b"

      processorEventProbe.expectMsg("a")
      processorEventProbe.expectMsg("b")

      p ! "snap"

      processorEventProbe.expectMsg("snapped")

      a1 ! "c"

      processorEventProbe.expectMsg("c")

      targetProbe.expectMsg(("a-processed-1", VectorTime(sourceLogId -> 1L, targetLogId -> 1L)))
      targetProbe.expectMsg(("a-processed-2", VectorTime(sourceLogId -> 1L, targetLogId -> 2L)))
      targetProbe.expectMsg(("b-processed-1", VectorTime(sourceLogId -> 2L, targetLogId -> 3L)))
      targetProbe.expectMsg(("b-processed-2", VectorTime(sourceLogId -> 2L, targetLogId -> 4L)))
      targetProbe.expectMsg(("c-processed-1", VectorTime(sourceLogId -> 3L, targetLogId -> 5L)))
      targetProbe.expectMsg(("c-processed-2", VectorTime(sourceLogId -> 3L, targetLogId -> 6L)))

      waitForProgressWrite(3L)

      p ! "boom"

      processorEventProbe.expectMsg("died")

      a1 ! "d"

      processorEventProbe.expectMsg("c")
      processorEventProbe.expectMsg("d")

      targetProbe.expectMsg(("d-processed-1", VectorTime(sourceLogId -> 4L, targetLogId -> 7L)))
      targetProbe.expectMsg(("d-processed-2", VectorTime(sourceLogId -> 4L, targetLogId -> 8L)))
    }
  }

  "A stateless EventsourcedProcessor" must {
    "write processed events to a target log and resume from stored position" in {
      val p = statelessProcessor()

      a1 ! "a"
      a1 ! "b"
      a1 ! "c"

      processorEventProbe.expectMsg("a")
      processorEventProbe.expectMsg("b")
      processorEventProbe.expectMsg("c")

      targetProbe.expectMsg(("a-processed-1", VectorTime(sourceLogId -> 1L, targetLogId -> 1L)))
      targetProbe.expectMsg(("a-processed-2", VectorTime(sourceLogId -> 1L, targetLogId -> 2L)))
      targetProbe.expectMsg(("b-processed-1", VectorTime(sourceLogId -> 2L, targetLogId -> 3L)))
      targetProbe.expectMsg(("b-processed-2", VectorTime(sourceLogId -> 2L, targetLogId -> 4L)))
      targetProbe.expectMsg(("c-processed-1", VectorTime(sourceLogId -> 3L, targetLogId -> 5L)))
      targetProbe.expectMsg(("c-processed-2", VectorTime(sourceLogId -> 3L, targetLogId -> 6L)))

      waitForProgressWrite(3L)

      p ! "boom"

      processorEventProbe.expectMsg("died")

      a1 ! "d"

      processorEventProbe.expectMsg("d")

      targetProbe.expectMsg(("d-processed-1", VectorTime(sourceLogId -> 4L, targetLogId -> 7L)))
      targetProbe.expectMsg(("d-processed-2", VectorTime(sourceLogId -> 4L, targetLogId -> 8L)))
    }
  }
}
