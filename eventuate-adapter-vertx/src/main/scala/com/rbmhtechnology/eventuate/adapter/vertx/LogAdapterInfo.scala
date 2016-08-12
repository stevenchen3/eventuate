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

object LogAdapterInfo {
  def publishAdapter(logName: String): PublishLogAdapterInfo =
    new PublishLogAdapterInfo(logName)

  def sendAdapter(logName: String, consumer: String): SendLogAdapterInfo =
    new SendLogAdapterInfo(logName, consumer)

  def writeAdapter(logName: String): WriteLogAdapterInfo =
    new WriteLogAdapterInfo(logName)
}

trait LogAdapterInfo {

  def logName: String

  lazy val readAddress: String =
    vertxEventBusAddress(logName, ReadLog)

  lazy val writeAddress: String =
    vertxEventBusAddress(logName, WriteLog)

  protected def vertxEventBusAddress(logName: String, logType: LogAdapterType, consumer: Option[String] = None): String =
    s"com.rbmhtechnology.eventuate.adapter.vertx:$logName${consumerAddress(consumer, ":")}:${logType.name}"

  private def consumerAddress(consumer: Option[String], delimiter: String): String =
    consumer.map(c => s"$delimiter$c").getOrElse("")
}

class PublishLogAdapterInfo(val logName: String) extends LogAdapterInfo {}

class SendLogAdapterInfo(val logName: String, consumer: String) extends LogAdapterInfo {

  override lazy val readAddress: String =
    vertxEventBusAddress(logName, ReadLog, Some(consumer))

  lazy val readConfirmationAddress: String = s"$readAddress:confirmation"
}

class WriteLogAdapterInfo(val logName: String) extends LogAdapterInfo {}
