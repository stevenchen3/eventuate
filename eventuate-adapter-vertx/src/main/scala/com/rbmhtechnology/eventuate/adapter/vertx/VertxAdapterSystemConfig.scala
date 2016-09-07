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

import java.util.{ Collection => JCollection }

import com.rbmhtechnology.eventuate.adapter.vertx.japi.{ VertxAdapterConfig => JVertxAdapterConfig }

import scala.annotation.varargs
import scala.collection.immutable.Seq
import scalaz.Scalaz._
import scalaz._

object VertxAdapterSystemConfig {
  import scala.collection.JavaConverters._

  def apply(adapterConfigurations: Seq[VertxAdapterConfig]): VertxAdapterSystemConfig = {
    validateConfigurations(adapterConfigurations) match {
      case \/-(cs) =>
        new VertxAdapterSystemConfig(cs)
      case -\/(errs) =>
        throw new IllegalArgumentException(s"Invalid configuration given. Cause:\n${errs.mkString("\n")}")
    }
  }

  def apply(adapterConfigurations: VertxAdapterConfig*): VertxAdapterSystemConfig =
    VertxAdapterSystemConfig(adapterConfigurations.toVector)

  @varargs
  def create(adapterConfigurations: JVertxAdapterConfig*): VertxAdapterSystemConfig =
    VertxAdapterSystemConfig(adapterConfigurations.toList.map(_.underlying))

  def create(adapterConfigurations: JCollection[JVertxAdapterConfig]): VertxAdapterSystemConfig =
    VertxAdapterSystemConfig(adapterConfigurations.asScala.toList.map(_.underlying))

  private def validateConfigurations(configs: Seq[VertxAdapterConfig]): \/[Seq[String], Seq[VertxAdapterConfig]] = {
    val invalid = configs.groupBy(_.id).filter(c => c._2.size > 1)

    if (invalid.isEmpty)
      configs.right
    else
      invalid.map(c => s"Ambigious definition for adapter with id '${c._1}' given. An id may only be used once.")
        .toVector
        .left
  }
}

class VertxAdapterSystemConfig(private[vertx] val adapterConfigs: Seq[VertxAdapterConfig]) {

  val readAdapters: Seq[VertxReadAdapterConfig] =
    adapterConfigs.collect({ case c: VertxReadAdapterConfig => c })

  val writeAdapters: Seq[VertxWriteAdapterConfig] =
    adapterConfigs.collect({ case c: VertxWriteAdapterConfig => c })
}
