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

object VertxAdapterSystemConfig {
  import scala.collection.JavaConverters._

  def apply(adapterConfigurations: VertxAdapterConfig*): VertxAdapterSystemConfig =
    new VertxAdapterSystemConfig(adapterConfigurations.toVector)

  def apply(adapterConfigurations: Seq[VertxAdapterConfig]): VertxAdapterSystemConfig =
    new VertxAdapterSystemConfig(adapterConfigurations)

  @varargs
  def create(adapterConfigurations: JVertxAdapterConfig*): VertxAdapterSystemConfig =
    new VertxAdapterSystemConfig(adapterConfigurations.toList.map(_.underlying))

  def create(adapterConfigurations: JCollection[JVertxAdapterConfig]): VertxAdapterSystemConfig =
    new VertxAdapterSystemConfig(adapterConfigurations.asScala.toList.map(_.underlying))
}

class VertxAdapterSystemConfig(private[vertx] val adapterConfigs: Seq[VertxAdapterConfig]) {
}
