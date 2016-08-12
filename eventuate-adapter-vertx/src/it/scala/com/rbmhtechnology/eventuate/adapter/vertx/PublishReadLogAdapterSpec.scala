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

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit._
import com.rbmhtechnology.eventuate.SingleLocationSpecLeveldb
import com.typesafe.config.ConfigFactory
import org.scalatest._

import scala.collection.immutable.Seq
import scala.concurrent.duration._

object PublishReadLogAdapterSpec {
  val Config = ConfigFactory.defaultApplication()
    .withFallback(ConfigFactory.parseString(
      """
        |eventuate.log.replay-batch-size = 50
      """.stripMargin))
}

class PublishReadLogAdapterSpec extends TestKit(ActorSystem("test", PublishReadLogAdapterSpec.Config)) with WordSpecLike with MustMatchers
  with SingleLocationSpecLeveldb with VertxEventbusSpec with ActorStorage with EventWriter {

  val inboundLogId = "log_inbound"

  override def beforeEach(): Unit = {
    super.beforeEach()

    registerCodec()
    eventLogService(publishAdapterInfo, eventHandler)
    logAdapter(publishAdapterInfo)
  }

  def logAdapter(logAdapterInfo: LogAdapterInfo): ActorRef =
    system.actorOf(PublishReadLogAdapter.props(inboundLogId, log, logAdapterInfo, vertx, actorStorageProvider()))

  def read: String = read(inboundLogId)

  def write: (Long) => String = write(inboundLogId)

  "A PublishReadLogAdapter" must {
    "read events from the beginning and emit them to the Vert.x eventbus" in {
      val writtenEvents = writeEvents("ev", 50)

      storageProbe.expectMsg(read)
      storageProbe.reply(0L)

      storageProbe.expectMsg(write(50))
      storageProbe.reply(50L)

      storageProbe.expectNoMsg(1.second)

      ebProbe.receiveN(50).asInstanceOf[Seq[Event]] must be(writtenEvents.map(_.toEvent))
    }
    "read events from a stored sequence number and emit them to the Vert.x eventbus" in {
      val writtenEvents = writeEvents("ev", 50)

      storageProbe.expectMsg(read)
      storageProbe.reply(10L)

      storageProbe.expectMsg(write(50))
      storageProbe.reply(50L)

      storageProbe.expectNoMsg(1.second)

      ebProbe.receiveN(40).asInstanceOf[Seq[Event]] must be(writtenEvents.drop(10).map(_.toEvent))
    }
    "read events in batches and emit them Vert.x eventbus" in {
      val writtenEvents = writeEvents("ev", 100)

      storageProbe.expectMsg(read)
      storageProbe.reply(0L)

      storageProbe.expectMsg(write(50))
      storageProbe.reply(50L)

      storageProbe.expectMsg(write(100))
      storageProbe.reply(100L)

      storageProbe.expectNoMsg(1.second)

      ebProbe.receiveN(100).asInstanceOf[Seq[Event]] must be(writtenEvents.map(_.toEvent))
    }
  }
}
