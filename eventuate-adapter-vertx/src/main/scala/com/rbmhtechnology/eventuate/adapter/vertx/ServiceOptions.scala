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

import java.time.{ Duration => JDuration }
import scala.concurrent.duration._

object ServiceOptions {

  def apply(): ServiceOptions =
    ServiceOptions(1.second, 30.seconds)

  def create(): ServiceOptions =
    apply()

  def create(connectInterval: JDuration, connectTimeout: JDuration): ServiceOptions =
    ServiceOptions(
      Duration.fromNanos(connectInterval.toNanos),
      Duration.fromNanos(connectTimeout.toNanos))
}

case class ServiceOptions(connectInterval: FiniteDuration, connectTimeout: FiniteDuration)
