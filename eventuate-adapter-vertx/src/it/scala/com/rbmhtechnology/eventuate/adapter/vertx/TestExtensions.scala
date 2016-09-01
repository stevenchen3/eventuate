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

import akka.testkit.TestProbe
import com.rbmhtechnology.eventuate.DurableEvent

import scala.concurrent.duration.Duration
import scala.reflect.ClassTag
import scala.util.Failure

object TestExtensions {

  implicit class DurableEventConverter(ev: DurableEvent) {
    // TODO
    def toEvent: Event = new Event(null, ev.localSequenceNr, ev.payload)
  }

  implicit class RichTestProbe(probe: TestProbe) {
    def expectEvent(sequenceNr: Long, max: Duration = Duration.Undefined): Event = {
      probe.expectMsgPF[Event](max, hint = s"Event($sequenceNr, _)") {
        case e@Event(ev, id, payload) if id == sequenceNr => e
      }
    }

    def expectConfirmableEvent(sequenceNr: Long, max: Duration = Duration.Undefined): ConfirmableEvent = {
      probe.expectMsgPF[ConfirmableEvent](max, hint = s"Event($sequenceNr, _)") {
        case e@ConfirmableEvent(id, payload) if id == sequenceNr => e
      }
    }

    def expectFailure[T](max: Duration = Duration.Undefined)(implicit t: ClassTag[T]): T = {
      probe.expectMsgPF[T](max, hint = s"Failure($t)") {
        case f@Failure(err:T) => err
      }
    }
  }
}
