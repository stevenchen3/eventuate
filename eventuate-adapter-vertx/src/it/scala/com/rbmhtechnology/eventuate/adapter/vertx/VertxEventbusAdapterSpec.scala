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
import akka.testkit.{TestKit, TestProbe}
import com.rbmhtechnology.eventuate.log.EventLogWriter
import com.rbmhtechnology.eventuate.log.leveldb.LeveldbEventLog
import com.rbmhtechnology.eventuate.utilities._
import com.rbmhtechnology.eventuate.{LocationCleanupLeveldb, ReplicationEndpoint}
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.{BeforeAndAfterAll, MustMatchers, WordSpecLike}

import scala.collection.immutable.Seq

object VertxEventbusAdapterSpec {
  val Config = ConfigFactory.defaultApplication()
    .withFallback(ConfigFactory.parseString(
      """
        |eventuate.log.replay-batch-size = 10
      """.stripMargin))
}

class VertxEventbusAdapterSpec extends TestKit(ActorSystem("test", VertxEventbusAdapterSpec.Config))
  with WordSpecLike with MustMatchers with BeforeAndAfterAll with VertxEventbus with ActorStorage with StopSystemAfterAll with LocationCleanupLeveldb {

  import TestExtensions._

  val logName = "logA"
  var endpoint: ReplicationEndpoint = _
  var eventBusProbe: TestProbe = _

  override def config: Config = VertxEventbusAdapterSpec.Config

  override def beforeAll(): Unit = {
    super.beforeAll()
    eventBusProbe = TestProbe()
    endpoint = new ReplicationEndpoint(id = "1", logNames = Set(logName), logFactory = logId => LeveldbEventLog.props(logId), connections = Set())
  }

  "A VertxEventbusAdapter" must {
    "read events from an inbound log and deliver them to the Vert.x service" in {
      val vertxAdapter = VertxEventbusAdapter(AdapterConfig(LogAdapter.readFrom(logName).publish()), endpoint, vertx, actorStorageProvider())
      val service = LogAdapterService(logName, vertx)
      val logWriter = new EventLogWriter("w1", endpoint.logs(logName))
      val logStorageName = VertxEventbusAdapter.logId(logName, ReadLog)

      service.onEvent((ev, sub) => eventBusProbe.ref.tell(ev, ActorRef.noSender))
      vertxAdapter.activate()

      val write1 = logWriter.write(Seq("event1")).await.head

      storageProbe.expectMsg(read(logStorageName))
      storageProbe.reply(0L)

      storageProbe.expectMsg(write(logStorageName)(1))
      storageProbe.reply(1L)

      eventBusProbe.expectMsgType[Event].id must be(write1.localSequenceNr)

      val write2 = logWriter.write(Seq("event2", "event3", "event4")).await

      storageProbe.expectMsg(write(logStorageName)(2))
      storageProbe.reply(2L)

      eventBusProbe.receiveN(3).asInstanceOf[Seq[Event]].map(_.id) must be(write2.map(_.localSequenceNr))

      storageProbe.expectMsg(write(logStorageName)(4))
      storageProbe.reply(4L)
    }
  }
}
