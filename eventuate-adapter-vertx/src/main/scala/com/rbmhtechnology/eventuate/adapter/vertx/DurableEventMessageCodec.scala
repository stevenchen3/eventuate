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

import akka.actor.ActorSystem
import akka.serialization.SerializationExtension
import com.rbmhtechnology.eventuate.DurableEvent
import io.vertx.core.buffer.Buffer
import io.vertx.core.eventbus.MessageCodec

object DurableEventMessageCodec {
  def apply(implicit system: ActorSystem): DurableEventMessageCodec =
    new DurableEventMessageCodec()
}

class DurableEventMessageCodec(implicit system: ActorSystem) extends MessageCodec[DurableEvent, DurableEvent] {

  lazy val serialization = SerializationExtension(system)

  override def transform(event: DurableEvent): DurableEvent =
    event

  override def decodeFromWire(pos: Int, buffer: Buffer): DurableEvent = {
    val payloadSize = buffer.getInt(pos)
    val payloadStart = pos + Integer.BYTES
    serialization.deserialize(buffer.getBytes(payloadStart, payloadStart + payloadSize), classOf[DurableEvent]).get
  }

  override def encodeToWire(buffer: Buffer, s: DurableEvent): Unit = {
    val payload = serialization.serialize(s).get
    buffer.appendInt(payload.length)
    buffer.appendBytes(payload)
  }

  override def name(): String = DurableEvent.getClass.getSimpleName

  override def systemCodecID(): Byte = -1
}
