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
case object ReadLog extends LogAdapterType { val name: String = "read-log" }
case object WriteLog extends LogAdapterType { val name: String = "write-log" }

sealed trait LogAdapterDescriptor {
  def name: String
}

case class PublishReadLogAdapterDescriptor(name: String) extends LogAdapterDescriptor
case class SendReadLogAdapterDescriptor(name: String, consumer: String, backPressure: Option[BackpressureOptions]) extends LogAdapterDescriptor
case class ReliableReadLogAdapterDescriptor(name: String, consumer: String, redeliverDelay: FiniteDuration, backPressure: Option[BackpressureOptions]) extends LogAdapterDescriptor
case class WriteLogAdapterDescriptor(name: String) extends LogAdapterDescriptor
