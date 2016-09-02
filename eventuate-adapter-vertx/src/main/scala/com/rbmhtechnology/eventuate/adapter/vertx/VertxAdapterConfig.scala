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

import akka.actor.ActorRef

import scala.collection.immutable.Seq
import scala.concurrent.duration.FiniteDuration

sealed trait VertxAdapterConfig {
  def id: String
  def log: ActorRef
}

case class VertxPublishAdapterConfig(id: String, log: ActorRef, endpoints: VertxEndpointResolver) extends VertxAdapterConfig
case class VertxSendAdapterConfig(id: String, log: ActorRef, endpoints: VertxEndpointResolver, deliveryMode: DeliveryMode) extends VertxAdapterConfig
case class VertxWriteAdapterConfig(id: String, log: ActorRef, endpoints: Seq[String], filter: PartialFunction[Any, Boolean]) extends VertxAdapterConfig

sealed trait ConfirmationType {}
case object Single extends ConfirmationType
case class Batch(size: Int) extends ConfirmationType

sealed trait DeliveryMode {}
case object AtMostOnce extends DeliveryMode
case class AtLeastOnce(confirmationType: ConfirmationType, confirmationTimeout: FiniteDuration) extends DeliveryMode

object VertxEndpointResolver {

  def apply(f: PartialFunction[Any, String]): VertxEndpointResolver =
    new VertxEndpointResolver(f)

  def apply(s: String): VertxEndpointResolver =
    new VertxEndpointResolver({ case _ => s })
}

class VertxEndpointResolver(f: PartialFunction[Any, String]) {
  def hasEndpoint(v: Any): Boolean =
    f.isDefinedAt(v)

  def endpoint(v: Any): String =
    f(v)

  // TODO: remove
  def address: String =
    f(Unit)
}

object VertxAdapterConfig {

  def fromLog(log: ActorRef): VertxReadAdapterConfigFactory =
    new VertxReadAdapterConfigFactory(log)

  def fromEndpoints(endpoints: String*): VertxWriteAdapterConfigFactory =
    new VertxWriteAdapterConfigFactory(endpoints.toVector)
}

trait CompletableVertxAdapterConfigFactory {
  def as(id: String): VertxAdapterConfig
}

class VertxReadAdapterConfigFactory(log: ActorRef) {

  def publishTo(endpoints: PartialFunction[Any, String]): VertxPublishAdapterConfigFactory =
    new VertxPublishAdapterConfigFactory(log, VertxEndpointResolver(endpoints))

  def sendTo(endpoints: PartialFunction[Any, String]): VertxSendAdapterConfigFactory =
    new VertxSendAdapterConfigFactory(log, VertxEndpointResolver(endpoints))
}

class VertxPublishAdapterConfigFactory(log: ActorRef, endpoints: VertxEndpointResolver)
  extends CompletableVertxAdapterConfigFactory {

  override def as(id: String): VertxAdapterConfig =
    VertxPublishAdapterConfig(id, log, endpoints)
}

class VertxSendAdapterConfigFactory(log: ActorRef, endpoints: VertxEndpointResolver, deliveryMode: DeliveryMode = AtMostOnce)
  extends CompletableVertxAdapterConfigFactory {

  def atLeastOnce(confirmationType: ConfirmationType, confirmationTimeout: FiniteDuration): VertxSendAdapterConfigFactory =
    new VertxSendAdapterConfigFactory(log, endpoints, AtLeastOnce(confirmationType, confirmationTimeout))

  override def as(id: String): VertxAdapterConfig =
    VertxSendAdapterConfig(id, log, endpoints, deliveryMode)
}

class VertxWriteAdapterConfigFactory(endpoints: Seq[String]) {

  def writeTo(log: ActorRef, filter: PartialFunction[Any, Boolean] = { case _ => true }) = new CompletableVertxAdapterConfigFactory {
    override def as(id: String): VertxAdapterConfig =
      VertxWriteAdapterConfig(id, log, endpoints, filter)
  }
}
