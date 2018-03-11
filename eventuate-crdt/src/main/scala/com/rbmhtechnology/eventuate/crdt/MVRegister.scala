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

package com.rbmhtechnology.eventuate.crdt

import akka.actor._

import com.rbmhtechnology.eventuate._

import scala.concurrent.Future

/**
 * Operation-based MV-Register CRDT. Has several [[Versioned]] values assigned in case of concurrent assignments,
 * otherwise, a single [[Versioned]] value. Concurrent assignments can be reduced to a single assignment by
 * assigning a [[Versioned]] value with a vector timestamp that is greater than those of the currently assigned
 * [[Versioned]] values.
 *
 * @param versioned Assigned values. Initially empty.
 * @tparam A Assigned value type.
 * @see [[http://hal.upmc.fr/docs/00/55/55/88/PDF/techreport.pdf A comprehensive study of Convergent and Commutative Replicated Data Types]]
 */
case class MVRegister[A](versioned: Set[Versioned[A]] = Set.empty[Versioned[A]]) extends CRDTFormat {
  def value: Set[A] =
    versioned.map(_.value)

  /**
   * Assigns a [[Versioned]] value from `v` and `vectorTimestamp` and returns an updated MV-Register.
   *
   * @param v value to assign.
   * @param vectorTimestamp vector timestamp of the value to assign.
   * @param systemTimestamp system timestamp of the value to assign.
   * @param emitterId id of the value emitter.
   */
  def assign(v: A, vectorTimestamp: VectorTime, systemTimestamp: Long = 0L, emitterId: String = ""): MVRegister[A] = {
    val concurrent = versioned.filter(_.vectorTimestamp <-> vectorTimestamp)
    copy(concurrent + Versioned(v, vectorTimestamp, systemTimestamp, emitterId))
  }
}

object MVRegister {
  def apply[A]: MVRegister[A] =
    new MVRegister[A]()

  implicit def MVRegisterServiceOps[A] = new CRDTServiceOps[MVRegister[A], Set[A]] {
    override def zero: MVRegister[A] =
      MVRegister.apply[A]

    override def value(crdt: MVRegister[A]): Set[A] =
      crdt.value

    override def precondition: Boolean =
      false

    override def effect(crdt: MVRegister[A], operation: Any, event: DurableEvent): MVRegister[A] = operation match {
      case AssignOp(value) => crdt.assign(value.asInstanceOf[A], event.vectorTimestamp, event.systemTimestamp, event.emitterId)
    }
  }
}

/**
 * Replicated [[MVRegister]] CRDT service.
 *
 * @param serviceId Unique id of this service.
 * @param log Event log.
 * @tparam A [[MVRegister]] value type.
 */
class MVRegisterService[A](val serviceId: String, val log: ActorRef)(implicit val system: ActorSystem, val ops: CRDTServiceOps[MVRegister[A], Set[A]])
  extends CRDTService[MVRegister[A], Set[A]] {

  /**
   * Assigns a `value` to the MV-Register identified by `id` and returns the updated MV-Register value.
   */
  def assign(id: String, value: A): Future[Set[A]] =
    op(id, AssignOp(value))

  start()
}

/**
 * Persistent assign operation used for [[MVRegister]] and [[LWWRegister]].
 */
case class AssignOp(value: Any) extends CRDTFormat
