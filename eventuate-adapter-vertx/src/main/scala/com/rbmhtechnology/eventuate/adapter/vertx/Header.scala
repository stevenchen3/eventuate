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

sealed trait Header {
  def name: String
  def valueByName(v: String): Option[HeaderValue]
}

sealed trait HeaderValue {
  def name: String
  def value: String
}

object Headers {

  case object Action extends Header {
    override val name: String = "action"

    override def valueByName(v: String): Option[HeaderValue] = v match {
      case Connect.value => Some(Connect)
      case Persist.value => Some(Persist)
      case _             => None
    }

    case object Connect extends HeaderValue {
      override val name: String = Action.name
      override val value: String = "connect"
    }

    case object Persist extends HeaderValue {
      override val name: String = Action.name
      override val value: String = "persist"
    }
  }
}
