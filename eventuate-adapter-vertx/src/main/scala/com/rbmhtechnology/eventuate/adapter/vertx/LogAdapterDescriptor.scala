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

import scala.concurrent.duration.FiniteDuration

sealed trait LogAdapterType {
  def name: String
}

case object Inbound extends LogAdapterType { val name: String = "inbound" }
case object Outbound extends LogAdapterType { val name: String = "outbound" }

sealed trait LogAdapterDescriptor {
  def name: String
  def logType: LogAdapterType
}

sealed trait InboundLogAdapterDescriptor extends LogAdapterDescriptor {
  override def logType: LogAdapterType = Inbound
}

sealed trait OutboundLogAdapterDescriptor extends LogAdapterDescriptor {
  override def logType: LogAdapterType = Outbound
}

case class PublishReadLogAdapterDescriptor(name: String) extends InboundLogAdapterDescriptor
case class SendReadLogAdapterDescriptor(name: String, consumer: String, backPressure: Option[BackpressureOptions]) extends InboundLogAdapterDescriptor
case class ReliableReadLogAdapterDescriptor(name: String, consumer: String, delay: FiniteDuration, backPressure: Option[BackpressureOptions]) extends InboundLogAdapterDescriptor
case class WriteLogAdapterDescriptor(name: String) extends OutboundLogAdapterDescriptor
