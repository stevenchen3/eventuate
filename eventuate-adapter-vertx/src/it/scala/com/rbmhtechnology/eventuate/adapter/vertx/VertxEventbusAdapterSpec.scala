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
import akka.testkit.TestKit
import com.rbmhtechnology.eventuate.log.EventLogWriter
import com.rbmhtechnology.eventuate.log.leveldb.LeveldbEventLog
import com.rbmhtechnology.eventuate.{LocationCleanupLeveldb, ReplicationEndpoint}
import org.scalatest.{BeforeAndAfterAll, MustMatchers, WordSpecLike}
import com.rbmhtechnology.eventuate.utilities._
import com.typesafe.config.{Config, ConfigFactory}

import scala.collection.immutable.Seq

object VertxEventbusAdapterSpec {
  val Config = ConfigFactory.defaultApplication()
    .withFallback(ConfigFactory.parseString(
      """
        |eventuate.log.replay-batch-size = 10
      """.stripMargin))
}

class VertxEventbusAdapterSpec extends TestKit(ActorSystem("test", VertxEventbusAdapterSpec.Config))
  with WordSpecLike with MustMatchers with BeforeAndAfterAll with VertxEventbusSpec with ActorStorage with StopSystemAfterAll with LocationCleanupLeveldb {

  import VertxEventbusInfo._

  val logName = "logA"
  var endpoint: ReplicationEndpoint = _

  override def config: Config = VertxEventbusAdapterSpec.Config

  override def beforeAll(): Unit = {
    super.beforeAll()
    endpoint = new ReplicationEndpoint(id = "1", logNames = Set(logName), logFactory = logId => LeveldbEventLog.props(logId), connections = Set())
  }

  "A VertxEventbusAdapter" must {
    "read events from an inbound log and deliver them to the Vert.x service" in {
      val vertxAdapter = VertxEventbusAdapter(AdapterConfig(LogAdapter.readFrom(logName).publish()), endpoint, vertx, actorStorageProvider())
      val service = LogAdapterService.apply(logName, vertx)
      val logWriter = new EventLogWriter("w1", endpoint.logs(logName))
      val logStorageName = logId(logName, Inbound)

      service.onEvent(ebHandler)
      vertxAdapter.activate()

      logWriter.write(Seq("event1")).await

      storageProbe.expectMsg(read(logStorageName))
      storageProbe.reply(0L)

      storageProbe.expectMsg(write(logStorageName)(1))
      storageProbe.reply(1L)

      ebProbe.expectMsgType[Event] must be(Event("event1", 1L))

      logWriter.write(Seq("event2", "event3", "event4")).await

      storageProbe.expectMsg(write(logStorageName)(2))
      storageProbe.reply(2L)

      ebProbe.receiveN(3).asInstanceOf[Seq[Event]] must contain inOrderOnly(Event("event2", 2L), Event("event3", 3L), Event("event4", 4L))

      storageProbe.expectMsg(write(logStorageName)(4))
      storageProbe.reply(4L)
    }
  }
}
