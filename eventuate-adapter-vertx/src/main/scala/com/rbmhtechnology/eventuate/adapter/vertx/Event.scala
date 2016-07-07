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

import com.rbmhtechnology.eventuate.DurableEvent
import io.vertx.core.eventbus.{ Message, MessageConsumer }

case class Event(payload: Any, sequenceNr: Long)

object EventEnvelope {
  def apply(message: Message[DurableEvent]): EventEnvelope =
    new EventEnvelope(message, Event(message.body().payload, message.body().localSequenceNr))

  def apply(message: Message[DurableEvent], eventSubscription: EventSubscription): EventEnvelopeWithSubscription =
    new EventEnvelopeWithSubscription(message, Event(message.body().payload, message.body().localSequenceNr), eventSubscription)
}

class EventEnvelope(private val message: Message[DurableEvent], val event: Event) {
  def confirm(): Unit = {
    message.reply("OK") // TODO
  }
}

class EventEnvelopeWithSubscription(message: Message[DurableEvent], event: Event, val subscription: EventSubscription)
  extends EventEnvelope(message, event) {
}

object EventSubscription {
  def apply(messageConsumer: MessageConsumer[DurableEvent]): EventSubscription =
    new EventSubscription(messageConsumer)
}

class EventSubscription(messageConsumer: MessageConsumer[DurableEvent]) {

  def unsubscribe(): Unit = {
    messageConsumer.unregister()
  }

  def pause(): Unit = {
    messageConsumer.pause()
  }

  def resume(): Unit = {
    messageConsumer.resume()
  }
}
