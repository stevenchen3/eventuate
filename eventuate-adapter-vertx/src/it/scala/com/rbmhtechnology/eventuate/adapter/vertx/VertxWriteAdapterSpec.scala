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
import com.rbmhtechnology.eventuate.{EventsourcedView, SingleLocationSpecLeveldb}
import io.vertx.core.AsyncResult
import io.vertx.core.eventbus.{Message, ReplyException}
import org.scalatest.{MustMatchers, WordSpecLike}

import scala.collection.immutable.Seq
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object VertxWriteAdapterSpec {

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

class VertxWriteAdapterSpec extends TestKit(ActorSystem("test", VertxPublishAdapterSpec.Config))
  with WordSpecLike with MustMatchers with SingleLocationSpecLeveldb with StopSystemAfterAll with VertxEventbus {

  import VertxHandlerConverters._
  import VertxWriteAdapterSpec._

  val endpoint = "vertx-eb-endpoint"
  var resultProbe: TestProbe = _
  var logProbe: TestProbe = _
  var persist: (Any) => Unit = _

  override def beforeEach(): Unit = {
    super.beforeEach()

    resultProbe = TestProbe()
    logProbe = TestProbe()

    logReader(logProbe.ref)
    persist = createPersist(resultProbe.ref, endpoint)
  }

  def waitForStartup(): Unit = {
    Thread.sleep(500)
  }

  def logReader(receiver: ActorRef): ActorRef =
    system.actorOf(Props(new LogReader("r", log, receiver)))

  def writeLogAdapter(eventLog: ActorRef = log): ActorRef = {
    val actor = system.actorOf(VertxWriteAdapter.props("write-log-adapter", eventLog, endpoint, vertx))
    waitForStartup()
    actor
  }

  def failingWriteLog(failureEvents: Seq[Any] = Seq()): ActorRef = {
    system.actorOf(Props(new FailingWriteLog(log, failureEvents)))
  }

  def createPersist(eventNotifier: ActorRef, endpoint: String): (Any) => Unit = {
    (event: Any) => vertx.eventBus().send[Any](endpoint, event, {(ar: AsyncResult[Message[Any]]) =>
      if (ar.succeeded()) {
        eventNotifier ! Success(ar.result().body)
      } else {
        eventNotifier ! Failure(ar.cause())
      }
    }.asVertxHandler)
  }

  "A VertxWriteAdapter" when {
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
    // TODO determine if these tests should be deleted or a vertx eventbus interceptor can handle theses cases
//    "started with a delay" must {
//      "persist events from before the adapter was started" in {
//        persist("ev-1")
//        persist("ev-2")
//        persist("ev-3")
//
//        Thread.sleep((serviceOptions.connectTimeout / 2).toMillis)
//        writeLogAdapter()
//
//        resultProbe.expectMsg(Success("ev-1"))
//        resultProbe.expectMsg(Success("ev-2"))
//        resultProbe.expectMsg(Success("ev-3"))
//
//        logProbe.expectMsg("ev-1")
//        logProbe.expectMsg("ev-2")
//        logProbe.expectMsg("ev-3")
//      }
//      "persist events from before and after the adapter was started" in {
//        persist("ev-1")
//        persist("ev-2")
//
//        Thread.sleep((serviceOptions.connectTimeout / 2).toMillis)
//        writeLogAdapter()
//
//        persist("ev-3")
//
//        resultProbe.expectMsg(Success("ev-1"))
//        resultProbe.expectMsg(Success("ev-2"))
//        resultProbe.expectMsg(Success("ev-3"))
//
//        logProbe.expectMsg("ev-1")
//        logProbe.expectMsg("ev-2")
//        logProbe.expectMsg("ev-3")
//      }
//      "resolve persist request with an error if adapter was not started" in {
//        persist("ev-1")
//        persist("ev-2")
//
//        resultProbe.expectFailure[ConnectionException]()
//        resultProbe.expectFailure[ConnectionException]()
//      }
//    }
    "encountering an error while persisting events" must {
      "return a failure for the failed event" in {
        val actor = writeLogAdapter(failingWriteLog(Seq("ev-1")))

        persist("ev-1")

        resultProbe.expectFailure[ReplyException]()

        logProbe.expectNoMsg(1.second)
      }
      "return a failure for the failed event only" in {
        val actor = writeLogAdapter(failingWriteLog(Seq("ev-1")))

        persist("ev-1")
        resultProbe.expectFailure[ReplyException]()
        logProbe.expectNoMsg(1.second)

        persist("ev-2")
        resultProbe.expectMsg(Success("ev-2"))
        logProbe.expectMsg("ev-2")
      }
    }
  }
}
