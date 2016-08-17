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

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.testkit.{TestKit, TestProbe}
import com.rbmhtechnology.eventuate.EventsourcingProtocol._
import com.rbmhtechnology.eventuate.adapter.vertx.japi.rx.{LogAdapterService => RxLogAdapterService}
import com.rbmhtechnology.eventuate.adapter.vertx.japi.{LogAdapterService => JLogAdapterService}
import com.rbmhtechnology.eventuate.{EventsourcedView, SingleLocationSpecLeveldb}
import io.vertx.core.{AsyncResult, Handler}
import io.vertx.rxjava.core.{Vertx => RxVertx}
import org.scalatest.{MustMatchers, Suite, WordSpecLike}
import rx.functions.Action1

import scala.collection.immutable.Seq
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object WriteLogAdapterSpec {

  class LogReader(val id: String, val eventLog: ActorRef, receiver: ActorRef) extends EventsourcedView {
    override def onCommand: Receive = {
      case _ =>
    }

    override def onEvent: Receive = {
      case event => receiver ! event
    }
  }

  class FailingWriteLog(eventLog: ActorRef, failureEvents: Seq[Any]) extends Actor {
    override def receive: Receive = {
      case Write(events, _, replyTo, correlationId, instanceId) if failureEvents.intersect(events.map(_.payload)).nonEmpty =>
        replyTo ! WriteFailure(events, new RuntimeException("error"), correlationId, instanceId)

      case cmd =>
        eventLog forward cmd
    }
  }
}

class WriteLogAdapterSpec extends TestKit(ActorSystem("test", PublishReadLogAdapterSpec.Config)) with WriteLogAdapterBaseSpec {
  override def doPersist(event: Any)(handler: EventsourcedView.Handler[Any]): Unit = {
    logService.persist(event)(handler)
  }
}

class JavaApiWriteLogAdapterSpec extends TestKit(ActorSystem("test", PublishReadLogAdapterSpec.Config)) with WriteLogAdapterBaseSpec {

  var jLogService: JLogAdapterService[Event] = _

  override def beforeEach(): Unit = {
    super.beforeEach()

    jLogService = new JLogAdapterService(logService)
  }

  override def doPersist(event: Any)(handler: EventsourcedView.Handler[Any]): Unit = {
    jLogService.persist(event, new Handler[AsyncResult[Any]] {
      override def handle(res: AsyncResult[Any]): Unit = {
        if (res.succeeded()) {
          handler(Success(res.result()))
        } else {
          handler(Failure(res.cause()))
        }
      }
    })
  }
}

class JavaRxApiWriteLogAdapterSpec extends TestKit(ActorSystem("test", PublishReadLogAdapterSpec.Config)) with WriteLogAdapterBaseSpec {

  var rxLogService: RxLogAdapterService[Event] = _

  override def beforeEach(): Unit = {
    super.beforeEach()

    rxLogService = new RxLogAdapterService(RxVertx.newInstance(vertx), logService)
  }

  override def doPersist(event: Any)(handler: EventsourcedView.Handler[Any]): Unit = {
    rxLogService.persist(event)
      .subscribe(
        new Action1[Any] {
          override def call(res: Any): Unit = handler(Success(res))
        },
        new Action1[Throwable] {
          override def call(err: Throwable): Unit = handler(Failure(err))
        }
      )
  }
}

trait WriteLogAdapterBaseSpec extends WordSpecLike with MustMatchers with SingleLocationSpecLeveldb with VertxEventbusSpec with StopSystemAfterAll {
  this: TestKit with Suite =>

  import WriteLogAdapterSpec._

  var resultProbe: TestProbe = _
  var logProbe: TestProbe = _
  var logService: LogAdapterService[Event] = _

  val serviceOptions = ServiceOptions(connectInterval = 500.millis, connectTimeout = 3.seconds)

  def doPersist(event: Any)(h: EventsourcedView.Handler[Any])

  override def beforeEach(): Unit = {
    super.beforeEach()

    registerCodec()

    resultProbe = TestProbe()
    logProbe = TestProbe()

    logReader(logProbe.ref)
    logService = eventLogService(writeAdapterInfo, eventHandler, serviceOptions)
  }

  def logReader(receiver: ActorRef): ActorRef =
    system.actorOf(Props(new LogReader("r", log, receiver)))

  def writeLogAdapter(eventLog: ActorRef = log): ActorRef = {
    system.actorOf(WriteLogAdapter.props("write-log-adapter", eventLog, writeAdapterInfo, vertx))
  }

  def failingWriteLog(failureEvents: Seq[Any] = Seq()): ActorRef = {
    system.actorOf(Props(new FailingWriteLog(log, failureEvents)))
  }

  def persist(event: Any): Unit = {
    doPersist(event) {
      case s@Success(res) => resultProbe.ref ! s
      case f@Failure(err) => resultProbe.ref ! f
    }
  }

  "A WriteLogAdapter" when {
    "receiving events" must {
      "persist events in the event log" in {
        val actor = writeLogAdapter()

        persist("ev-1")

        logProbe.expectMsg("ev-1")

        resultProbe.expectMsg(Success("ev-1"))
      }
      "persist events in the same order they were received" in {
        val actor = writeLogAdapter()

        persist("ev-1")
        persist("ev-2")
        persist("ev-3")

        resultProbe.expectMsg(Success("ev-1"))
        resultProbe.expectMsg(Success("ev-2"))
        resultProbe.expectMsg(Success("ev-3"))

        logProbe.expectMsg("ev-1")
        logProbe.expectMsg("ev-2")
        logProbe.expectMsg("ev-3")
      }
    }
    "started with a delay" must {
      "persist events from before the adapter was started" in {
        persist("ev-1")
        persist("ev-2")
        persist("ev-3")

        Thread.sleep((serviceOptions.connectTimeout / 2).toMillis)
        writeLogAdapter()

        resultProbe.expectMsg(Success("ev-1"))
        resultProbe.expectMsg(Success("ev-2"))
        resultProbe.expectMsg(Success("ev-3"))

        logProbe.expectMsg("ev-1")
        logProbe.expectMsg("ev-2")
        logProbe.expectMsg("ev-3")
      }
      "persist events from before and after the adapter was started" in {
        persist("ev-1")
        persist("ev-2")

        Thread.sleep((serviceOptions.connectTimeout / 2).toMillis)
        writeLogAdapter()

        persist("ev-3")

        resultProbe.expectMsg(Success("ev-1"))
        resultProbe.expectMsg(Success("ev-2"))
        resultProbe.expectMsg(Success("ev-3"))

        logProbe.expectMsg("ev-1")
        logProbe.expectMsg("ev-2")
        logProbe.expectMsg("ev-3")
      }
      "resolve persist request with an error if adapter was not started" in {
        persist("ev-1")
        persist("ev-2")

        resultProbe.expectFailure[ConnectionException]()
        resultProbe.expectFailure[ConnectionException]()
      }
    }
    "encountering an error while persisting events" must {
      "return a failure for the failed event" in {
        val actor = writeLogAdapter(failingWriteLog(Seq("ev-1")))

        persist("ev-1")

        resultProbe.expectFailure[PersistenceException]()

        logProbe.expectNoMsg(1.second)
      }
      "return a failure for the failed event only" in {
        val actor = writeLogAdapter(failingWriteLog(Seq("ev-1")))

        persist("ev-1")
        resultProbe.expectFailure[PersistenceException]()
        logProbe.expectNoMsg(1.second)

        persist("ev-2")
        resultProbe.expectMsg(Success("ev-2"))
        logProbe.expectMsg("ev-2")
      }
    }
  }
}
