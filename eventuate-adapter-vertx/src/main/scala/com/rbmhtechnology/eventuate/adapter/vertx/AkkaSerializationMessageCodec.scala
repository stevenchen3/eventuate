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
import io.vertx.core.buffer.Buffer
import io.vertx.core.eventbus.MessageCodec

object AkkaSerializationMessageCodec {
  def apply(implicit system: ActorSystem): MessageCodec[AnyRef, AnyRef] =
    new AkkaSerializationMessageCodec()

  val Name = "akka-serialization-message-codec"
}

class AkkaSerializationMessageCodec(implicit system: ActorSystem) extends MessageCodec[AnyRef, AnyRef] {
  import AkkaSerializationMessageCodec._

  lazy val serialization = SerializationExtension(system)

  override def transform(o: AnyRef): AnyRef = o

  override def encodeToWire(buffer: Buffer, o: AnyRef): Unit = {
    val clazz = o.getClass.getName.getBytes
    val payload = serialization.serialize(o).get
    buffer.appendInt(clazz.length)
    buffer.appendBytes(clazz)
    buffer.appendInt(payload.length)
    buffer.appendBytes(payload)
  }

  override def decodeFromWire(pos: Int, buffer: Buffer): AnyRef = {
    val classNameLength = buffer.getInt(pos)
    val classNameStart = pos + Integer.BYTES

    val className = new String(buffer.getBytes(classNameStart, classNameStart + classNameLength))

    val payloadLengthStart = classNameStart + classNameLength
    val payloadLength = buffer.getInt(payloadLengthStart)

    val payloadStart = payloadLengthStart + Integer.BYTES
    val payload = buffer.getBytes(payloadStart, payloadStart + payloadLength)

    serialization.deserialize(payload, Class.forName(className)).get.asInstanceOf[AnyRef]
  }

  override def name(): String = Name

  override def systemCodecID(): Byte = -1
}
