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

import java.io.File
import java.nio.file.NoSuchFileException
import java.time.Duration
import java.util.UUID

import akka.actor.{ ActorRef, ActorSystem, Props }
import akka.pattern.ask
import akka.util.Timeout
import com.rbmhtechnology.eventuate.adapter.vertx._
import com.rbmhtechnology.eventuate.log.EventLogWriter
import com.rbmhtechnology.eventuate.log.leveldb.LeveldbEventLog
import com.rbmhtechnology.eventuate.{ EventsourcedView, ReplicationConnection, ReplicationEndpoint }
import io.vertx.core._
import io.vertx.core.buffer.Buffer
import io.vertx.core.file.FileSystemException
import io.vertx.core.json.JsonObject

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.util.{ Failure, Random, Success }

object Logs {
  val logA = "log_S_A"
  val logB = "log_S_B"
}

object Consumers {
  val processor = "processor-verticle"
}

object VertxAdapterExample extends App {

  import Consumers._
  import Logs._
  import ExampleVertxExtensions._

  implicit val timeout = Timeout(5.minutes)
  implicit val system = ActorSystem(ReplicationConnection.DefaultRemoteSystemName)
  val vertx = Vertx.vertx()

  import system.dispatcher

  val endpoint = new ReplicationEndpoint(id = "id1", logNames = Set(logA, logB),
    logFactory = logId => LeveldbEventLog.props(logId), connections = Set())

  val adapter = VertxEventbusAdapter(AdapterConfig(
    LogAdapter.readFrom(logA).sendTo(processor).withConfirmedDelivery(Duration.ofSeconds(3)),
    LogAdapter.writeTo(logB),
    LogAdapter.readFrom(logB).publish()),
    endpoint, vertx, new DiskStorageProvider("target/progress/vertx-scala", vertx))

  (for {
    _ <- vertx.deploy[ProcessorVerticle]()
    _ <- vertx.deploy[ReaderVerticle](new DeploymentOptions().setConfig(new JsonObject().put("name", "v_reader-1")))
    i <- vertx.deploy[ReaderVerticle](new DeploymentOptions().setConfig(new JsonObject().put("name", "v_reader-2")))
  } yield i).onComplete {
    case Success(res) => adapter.activate()
    case Failure(err) => println(s"Vert.x startup failed with $err")
  }

  val eventCount = 10
  val writer = new EventLogWriter("writer", endpoint.logs(logA))
  val reader = system.actorOf(Props(new EventLogReader("reader", endpoint.logs(logB), eventCount)))

  val runId = UUID.randomUUID().toString.take(5)
  (1 to eventCount) map (i => s"event[$runId]-$i") foreach { event =>
    writer.write(List(event))
    Thread.sleep(100)
  }

  reader.ask("notifyOnComplete").onComplete { _ =>
    Thread.sleep(500)
    vertx.close()
    system.terminate()
    println("--- finished ---")
  }
}

class ProcessorVerticle extends AbstractVerticle {
  import Consumers._
  import Logs._

  val r = Random

  override def start(): Unit = {
    val readService = LogAdapterService(logA, processor, vertx)
    val writeService = LogAdapterService(logB, vertx)

    readService.onEvent { (ev, sub) =>
      if (r.nextFloat() < 0.4) {
        println(s"[v_processor] dropped   [${ev.payload}]")
      } else {
        println(s"[v_processor] processed [${ev.payload}]")

        writeService.persist(s"*processed*${ev.payload}") {
          case Success(res) => ev.confirm()
          case Failure(err) => println(s"[verticle] persist failed with: ${err.getMessage}")
        }
      }
    }
  }
}

class ReaderVerticle extends AbstractVerticle {
  import Logs._

  override def start(): Unit = {
    val readService = LogAdapterService(logB, vertx)

    readService.onEvent { (ev, sub) =>
      println(s"[${config.getString("name")}]  received  [${ev.payload}]")
    }
  }
}

class EventLogReader(val id: String, val eventLog: ActorRef, val eventCount: Int) extends EventsourcedView {

  var subscribers: List[ActorRef] = List.empty
  var eventsRead = 0

  override def onCommand: Receive = {
    case "notifyOnComplete" =>
      subscribers = sender() +: subscribers
    case "eventRead" =>
      eventsRead = eventsRead + 1
      if (eventsRead == eventCount) {
        subscribers.foreach(_ ! "finished")
      }
  }

  override def onEvent: Receive = {
    case ev =>
      println(s"[e_reader]    received  [$ev]")
      if (!recovering) {
        self ! "eventRead"
      }
  }
}

class DiskStorageProvider(path: String, vertx: Vertx)(implicit system: ActorSystem) extends StorageProvider {
  import ExampleVertxExtensions._

  new File(path).mkdirs()

  override def readProgress(logName: String)(implicit executionContext: ExecutionContext): Future[Long] = {
    val promise = Promise[Buffer]()
    vertx.fileSystem().readFile(path(logName), promise.asVertxHandler)
    promise.future
      .map(_.toString().toLong)
      .recover {
        case err: FileSystemException if err.getCause.isInstanceOf[NoSuchFileException] => 0L
      }
  }

  override def writeProgress(logName: String, sequenceNr: Long)(implicit executionContext: ExecutionContext): Future[Long] = {
    val promise = Promise[Void]()
    vertx.fileSystem().writeFile(path(logName), Buffer.buffer(sequenceNr.toString), promise.asVertxHandler)
    promise.future
      .map(_ => sequenceNr)
  }

  def path(logName: String): String =
    s"$path/progress-$logName.txt"
}
