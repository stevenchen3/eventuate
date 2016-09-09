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

import akka.testkit.TestKit
import io.vertx.core.Vertx
import org.scalatest.{BeforeAndAfterEach, Suite}

trait VertxEnvironment extends BeforeAndAfterEach {
  this: TestKit with Suite =>

  val endpoint1 = "vertx-endpoint1"
  val endpoint2 = "vertx-endpoint2"
  val endpoint3 = "vertx-endpoint3"

  var vertx: Vertx = _

  override def beforeEach(): Unit = {
    super.beforeEach()
    vertx = Vertx.vertx()
  }
}
