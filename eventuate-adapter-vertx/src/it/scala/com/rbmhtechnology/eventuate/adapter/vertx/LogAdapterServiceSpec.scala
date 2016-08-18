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

import akka.actor.{ActorRef, ActorSystem, Status}
import akka.pattern.ask
import akka.testkit.{TestKit, TestProbe}
import com.rbmhtechnology.eventuate.DurableEvent
import io.vertx.core.eventbus.Message
import org.scalatest.{MustMatchers, Suite, WordSpecLike}

import scala.concurrent.duration._
import scala.util.{Failure, Success}

object LogAdapterServiceSpec {
  case class PersistRequest(event: Option[Any], header: Option[HeaderValue])

  implicit class PersistRequestProbe(probe: TestProbe) {

    def expectConnectRequest(): Unit = {
      probe.expectMsg(PersistRequest(None, Some(Headers.Action.Connect)))
    }

    def expectPersistRequest(event: Any, header: HeaderValue = Headers.Action.Persist): Unit = {
      probe.expectMsg(PersistRequest(Some(event), Some(header)))
    }
  }
}

class LogAdapterServiceSpec extends TestKit(ActorSystem("test")) with LogAdapterServiceBaseSpec {
}

trait LogAdapterServiceBaseSpec extends WordSpecLike with MustMatchers with VertxEventbus with ActorLogAdapterService {
  this: TestKit with Suite =>

  import VertxExtensions._
  import VertxHandlerConverters._
  import LogAdapterServiceSpec._
  import TestExtensions._
  import system.dispatcher

  val serviceOptions = ServiceOptions(connectInterval = 500.millis, connectTimeout = 2.seconds)

  var publishProbe: TestProbe = _
  var sendProbe: TestProbe = _
  var persistProbe: TestProbe = _
  var writeAdapterProbe: TestProbe = _

  var persist: (Any) => Unit = _

  override def beforeEach(): Unit = {
    super.beforeEach()

    publishProbe = TestProbe()
    sendProbe = TestProbe()
    persistProbe = TestProbe()
    writeAdapterProbe = TestProbe()

    registerCodec()
    registerWriteAdapter(writeAdapterProbe.ref)
    persist = createPersist(persistProbe.ref, options = serviceOptions)
  }

  def durableEvent(sequenceNr: Long): DurableEvent =
    DurableEvent("emitter-1").copy(localSequenceNr = sequenceNr, payload = "event")

  def publishEvent(sequenceNr: Long): Unit = {
    vertx.eventBus().send(LogAdapterInfo.publishAdapter(logName).readAddress, durableEvent(sequenceNr))
  }

  def sendEvent(sequenceNr: Long, consumer: String): Unit = {
    vertx.eventBus().send(LogAdapterInfo.sendAdapter(logName, consumer).readAddress, durableEvent(sequenceNr))
  }

  def registerWriteAdapter(notifier: ActorRef): Unit = {
    val onMessage: Message[Any] => Unit = message => {
      notifier.ask(PersistRequest(Option(message.body()), message.headers().getHeaderValue(Headers.Action)))(1.second)
        .onComplete {
          case Success(res) => message.reply(res)
          case Failure(err) => message.fail(0, err.getMessage)
        }
    }

    vertx.eventBus().consumer(LogAdapterInfo.writeAdapter(logName).writeAddress, onMessage.asVertxHandler)
  }

  def connectToWriteAdapter(): Unit = {
    persist("initial")

    writeAdapterProbe.expectConnectRequest()
    writeAdapterProbe.reply("ok")

    writeAdapterProbe.expectPersistRequest("initial")
    writeAdapterProbe.reply("initial")

    persistProbe.expectMsg(Success("initial"))
  }

  "A LogAdapterService" when {
    "receiving events published to all" must {
      "notify the caller with the received event" in {
        notifyOnPublishEvent(publishProbe.ref)

        publishEvent(sequenceNr = 1)

        publishProbe.expectEvent(sequenceNr = 1)
      }
      "notify the caller with all received events in the correct order" in {
        notifyOnPublishEvent(publishProbe.ref)

        publishEvent(sequenceNr = 1)
        publishEvent(sequenceNr = 2)
        publishEvent(sequenceNr = 3)

        publishProbe.expectEvent(sequenceNr = 1)
        publishProbe.expectEvent(sequenceNr = 2)
        publishProbe.expectEvent(sequenceNr = 3)
      }
    }
    "receiving events sent to a single consumer" must {
      "notify the consumer with a received event" in {
        val consumer1 = "consumer-1"

        notifyOnConfirmableEvent(sendProbe.ref, consumer = consumer1)

        sendEvent(sequenceNr = 1, consumer1)

        sendProbe.expectConfirmableEvent(sequenceNr = 1)
      }
      "notify the consumer with all received events in the correct order" in {
        val consumer1 = "consumer-1"

        notifyOnConfirmableEvent(sendProbe.ref, consumer = consumer1)

        sendEvent(sequenceNr = 1, consumer1)
        sendEvent(sequenceNr = 2, consumer1)
        sendEvent(sequenceNr = 3, consumer1)

        sendProbe.expectConfirmableEvent(sequenceNr = 1)
        sendProbe.expectConfirmableEvent(sequenceNr = 2)
        sendProbe.expectConfirmableEvent(sequenceNr = 3)
      }
      "notify the single consumer only with events addressed to it" in {
        val consumer1 = "consumer-1"
        val consumer2 = "consumer-2"

        notifyOnConfirmableEvent(sendProbe.ref, consumer = consumer1)

        sendEvent(sequenceNr = 1, consumer1)
        sendEvent(sequenceNr = 2, consumer2)
        sendEvent(sequenceNr = 3, consumer2)
        sendEvent(sequenceNr = 4, consumer1)

        sendProbe.expectConfirmableEvent(sequenceNr = 1)
        sendProbe.expectConfirmableEvent(sequenceNr = 4)

        sendProbe.expectNoMsg(1.second)
      }
    }
    "persisting events" must {
      "connect to the event write adapter before sending the first event" in {
        persist("ev-1")

        writeAdapterProbe.expectConnectRequest()
        writeAdapterProbe.reply("ok")
      }
      "send an event to the write adapter and notify the caller with response" in {
        connectToWriteAdapter()

        persist("ev-1")

        writeAdapterProbe.expectPersistRequest("ev-1")
        writeAdapterProbe.reply("ev-1")

        persistProbe.expectMsg(Success("ev-1"))
      }
      "send multiple events to the write adapter and notify with the caller for each response" in {
        connectToWriteAdapter()

        persist("ev-1")
        persist("ev-2")
        persist("ev-3")

        writeAdapterProbe.expectPersistRequest("ev-1")
        writeAdapterProbe.reply("ev-1")

        writeAdapterProbe.expectPersistRequest("ev-2")
        writeAdapterProbe.reply("ev-2")

        writeAdapterProbe.expectPersistRequest("ev-3")
        writeAdapterProbe.reply("ev-3")

        persistProbe.expectMsg(Success("ev-1"))
        persistProbe.expectMsg(Success("ev-2"))
        persistProbe.expectMsg(Success("ev-3"))
      }
      "notify the caller with failures for failed events" in {
        connectToWriteAdapter()

        persist("ev-1")
        persist("ev-2")
        persist("ev-3")

        writeAdapterProbe.expectPersistRequest("ev-1")
        writeAdapterProbe.reply("ev-1")
        persistProbe.expectMsg(Success("ev-1"))

        writeAdapterProbe.expectPersistRequest("ev-2")
        writeAdapterProbe.reply(Status.Failure(new RuntimeException("persist-error")))
        persistProbe.expectFailure[PersistenceException]()

        writeAdapterProbe.expectPersistRequest("ev-3")
        writeAdapterProbe.reply("ev-3")
        persistProbe.expectMsg(Success("ev-3"))
      }
      "notify the caller with a failure if no connection to the writer could be established" in {
        persist("ev-1")

        writeAdapterProbe.expectConnectRequest()
        writeAdapterProbe.reply(Status.Failure(new RuntimeException("connection-error")))

        persistProbe.expectFailure[ConnectionException]()
      }
      "notify the caller with a failure if no write adapter is present within the given timeout" in {
        persist("ev-1")

        writeAdapterProbe.expectConnectRequest()

        persistProbe.expectFailure[ConnectionException]()
      }
      "notify the caller with failures for all events if write adapter is not available" in {
        persist("ev-1")
        persist("ev-2")
        persist("ev-3")

        writeAdapterProbe.expectConnectRequest()

        persistProbe.expectFailure[ConnectionException]()
        persistProbe.expectFailure[ConnectionException]()
        persistProbe.expectFailure[ConnectionException]()
      }
    }
  }
}
