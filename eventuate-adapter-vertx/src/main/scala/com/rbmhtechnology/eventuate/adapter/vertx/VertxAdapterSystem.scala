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

import akka.actor.{ ActorSystem, Props }
import com.rbmhtechnology.eventuate.adapter.vertx.japi.rx.{ StorageProvider => RxStorageProvider }
import com.rbmhtechnology.eventuate.adapter.vertx.japi.{ StorageProvider => JStorageProvider }
import io.vertx.core.Vertx
import io.vertx.rxjava.core.{ Vertx => RxVertx }

import scala.collection.immutable.Seq

object VertxAdapterSystem {

  import VertxConverters._

  def apply(config: VertxAdapterSystemConfig, vertx: Vertx, storageProvider: StorageProvider)(implicit system: ActorSystem): VertxAdapterSystem =
    new VertxAdapterSystem(config, vertx, storageProvider)

  def create(config: VertxAdapterSystemConfig,
    vertx: Vertx,
    storageProvider: JStorageProvider,
    system: ActorSystem): VertxAdapterSystem =
    new VertxAdapterSystem(config, vertx, storageProvider.asScala)(system)

  def create(config: VertxAdapterSystemConfig,
    vertx: RxVertx,
    storageProvider: RxStorageProvider,
    system: ActorSystem): VertxAdapterSystem =
    new VertxAdapterSystem(config, vertx, storageProvider.asScala)(system)
}

class VertxAdapterSystem(config: VertxAdapterSystemConfig, vertx: Vertx, storageProvider: StorageProvider)(implicit system: ActorSystem) {

  private def registerCodec(): Unit =
    vertx.eventBus().registerCodec(AkkaSerializationMessageCodec(system))

  def start(): Unit = {
    registerCodec()
    val supervisor = system.actorOf(VertxAdapterSupervisor.props(adapters))
  }

  private def adapters: Seq[Props] = {
    config.adapterConfigs.map {
      case VertxPublishAdapterConfig(id, log, endpointRouter) =>
        VertxPublishAdapter.props(id, log, endpointRouter, vertx, storageProvider)

      case VertxSendAdapterConfig(id, log, endpointRouter, AtMostOnce) =>
        VertxSendAdapter.props(id, log, endpointRouter, vertx, storageProvider)

      case VertxSendAdapterConfig(id, log, endpointRouter, AtLeastOnce(Single, timeout)) =>
        VertxSingleConfirmationSendAdapter.props(id, log, endpointRouter, vertx, timeout)

      case VertxSendAdapterConfig(id, log, endpointRouter, AtLeastOnce(Batch(size), timeout)) =>
        VertxBatchConfirmationSendAdapter.props(id, log, endpointRouter, vertx, storageProvider, size, timeout)

      case VertxWriteAdapterConfig(id, log, endpoints, filter) =>
        VertxWriteAdapter.props(id, log, endpoints.head, vertx)
    }
  }
}
