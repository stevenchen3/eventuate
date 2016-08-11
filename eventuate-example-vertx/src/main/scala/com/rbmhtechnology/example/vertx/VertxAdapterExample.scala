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

package com.rbmhtechnology.example.vertx

import java.util.UUID

import akka.actor.ActorSystem
import com.rbmhtechnology.eventuate.adapter.vertx._
import com.rbmhtechnology.eventuate.log.EventLogWriter
import com.rbmhtechnology.eventuate.log.leveldb.LeveldbEventLog
import com.rbmhtechnology.eventuate.{ ReplicationConnection, ReplicationEndpoint }
import io.vertx.core.{ AbstractVerticle, AsyncResult, Handler, Vertx }

import scala.concurrent.{ ExecutionContext, Future }

object VertxAdapterExample extends App {

  implicit val system = ActorSystem(ReplicationConnection.DefaultRemoteSystemName)
  val vertx = Vertx.vertx()

  val runId = UUID.randomUUID()
  val logName = "logA"
  val endpoint = new ReplicationEndpoint(id = "id1", logNames = Set(logName), logFactory = logId => LeveldbEventLog.props(logId), connections = Set())
  val writer = new EventLogWriter("writer", endpoint.logs(logName))
  val adapter = VertxEventbusAdapter(AdapterConfig(LogAdapter.readFrom(logName).publish()), endpoint, vertx, new FakeStorageProvider())

  vertx.deployVerticle(classOf[TestVerticle].getName, new Handler[AsyncResult[String]] {
    override def handle(res: AsyncResult[String]): Unit = {
      if (res.failed()) {
        println(s"Vertx startup failed with ${res.cause()}")
      } else {
        adapter.activate()
      }
    }
  })

  (1 to 100) map (i => s"event[$runId]-$i") foreach { event =>
    writer.write(List(event))
    Thread.sleep(100)
  }

  vertx.close()
  system.terminate()
}

class TestVerticle extends AbstractVerticle {
  override def start(): Unit = {
    val service = LogAdapterService.apply("logA", vertx)

    service.onEvent((ev, sub) => println(s"verticle received event: ${ev.payload}"))
  }
}

class FakeStorageProvider extends StorageProvider {
  override def readProgress(logName: String)(implicit executionContext: ExecutionContext): Future[Long] =
    Future.successful(0L)

  override def writeProgress(logName: String, sequenceNr: Long)(implicit executionContext: ExecutionContext): Future[Long] =
    Future.successful(sequenceNr)
}

