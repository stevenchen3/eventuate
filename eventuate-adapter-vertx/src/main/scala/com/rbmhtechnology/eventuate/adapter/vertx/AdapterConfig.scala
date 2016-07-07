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

import java.util.{Collection => JCollection}

import scala.annotation.varargs
import scala.collection.immutable.Seq

object LogAdapter {
  def readFrom(logName: String): ReadLogAdapterConfigBuilder =
    new ReadLogAdapterConfigBuilder(logName)

  def writeTo(logName: String): LogAdapterConfig =
    new SimpleLogAdapterConfig(WriteLogAdapterDescriptor(logName))
}

class ReadLogAdapterConfigBuilder(private val logName: String) {
  def publish(): LogAdapterConfig =
    new SimpleLogAdapterConfig(PublishReadLogAdapterDescriptor(logName))

  def sendTo(consumer: String): SendLogAdapterConfig =
    new SendLogAdapterConfig(SendReadLogAdapterDescriptor(logName, consumer, None))
}

trait LogAdapterConfig {
  private[eventuate] def logDescriptor: LogAdapterDescriptor
}

class SimpleLogAdapterConfig(private[eventuate] val logDescriptor: LogAdapterDescriptor) extends LogAdapterConfig

class SendLogAdapterConfig(private[eventuate] val logDescriptor: SendReadLogAdapterDescriptor) extends LogAdapterConfig {
  def withBackPressureSupport(options: BackpressureOptions): SendLogAdapterConfig = {
    new SendLogAdapterConfig(logDescriptor.copy(backPressure = Some(options)))
  }

  def withConfirmedDelivery(): ReliableSendLogAdapterConfig = {
    new ReliableSendLogAdapterConfig(ReliableReadLogAdapterDescriptor(logDescriptor.name, logDescriptor.consumer, logDescriptor.backPressure))
  }
}

class ReliableSendLogAdapterConfig(private[eventuate] val logDescriptor: ReliableReadLogAdapterDescriptor) extends LogAdapterConfig {
  def withBackPressureSupport(options: BackpressureOptions): ReliableSendLogAdapterConfig = {
    new ReliableSendLogAdapterConfig(logDescriptor.copy(backPressure = Some(options)))
  }
}

object AdapterConfig {
  import scala.collection.JavaConverters._

  def apply(logs: Seq[LogAdapterConfig]): AdapterConfig =
    new AdapterConfig(logs)

  def apply(logs: LogAdapterConfig*): AdapterConfig =
    new AdapterConfig(logs.toList)

  @varargs
  def of[T <: LogAdapterConfig](logs: T*): AdapterConfig =
    new AdapterConfig(logs.toList)

  def of[T <: LogAdapterConfig](logs: JCollection[T]): AdapterConfig =
    new AdapterConfig(logs.asScala.toList)
}

class AdapterConfig(val adapters: Seq[LogAdapterConfig]) {
  private[eventuate] def logDescriptors: Seq[LogAdapterDescriptor] = adapters.map(_.logDescriptor)
}