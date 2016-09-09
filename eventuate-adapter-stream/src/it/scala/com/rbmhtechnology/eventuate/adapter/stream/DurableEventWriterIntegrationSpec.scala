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
import akka.stream._
import akka.stream.scaladsl._
import akka.stream.testkit.scaladsl._
import akka.testkit.TestKit

import com.rbmhtechnology.eventuate._

import org.scalatest._

import scala.collection.immutable.Seq

class DurableEventWriterIntegrationSpec extends TestKit(ActorSystem("test")) with WordSpecLike with Matchers with SingleLocationSpecLeveldb {
  implicit val materializer: Materializer = ActorMaterializer()

  val SrcEmitterId = "src-emitter"
  val SrcLogId = "src-log"

  def durableEvent(payload: String, sequenceNr: Long): DurableEvent =
    DurableEvent(payload, SrcEmitterId, processId = SrcLogId, localLogId = SrcLogId, localSequenceNr = sequenceNr, vectorTimestamp = VectorTime(SrcLogId -> sequenceNr))

  "A DurableEventWriter" must {
    "write events to a log and emit the logged events" in {
      val (src, snk) = TestSource.probe[DurableEvent]
        .via(new DurableEventWriter(log))
        .toMat(TestSink.probe[DurableEvent])(Keep.both)
        .run()

      snk.request(3)

      src.sendNext(durableEvent("a", 11))
      src.sendNext(durableEvent("b", 12))
      src.sendNext(durableEvent("c", 13))

      snk.expectNextN(3).map(e => (e.payload, e.processId, e.localLogId, e.localSequenceNr, e.vectorTimestamp)) should be(Seq(
        ("a", logId, logId, 1L, VectorTime(SrcLogId -> 11L, logId -> 1L)),
        ("b", logId, logId, 2L, VectorTime(SrcLogId -> 12L, logId -> 2L)),
        ("c", logId, logId, 3L, VectorTime(SrcLogId -> 13L, logId -> 3L))))

      snk.cancel()
    }
  }
}
