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

package com.rbmhtechnology.eventuate

import java.io.File

import akka.actor.Props
import akka.remote.testconductor.RoleName
import akka.remote.testkit.MultiNodeSpec

import com.rbmhtechnology.eventuate.log.cassandra._

import org.apache.commons.io.FileUtils
import org.scalatest.BeforeAndAfterAll

trait MultiNodeSupportCassandra extends BeforeAndAfterAll { this: MultiNodeSpec with MultiNodeWordSpec =>
  val coordinator = RoleName("nodeA")

  def cassandraDir: String =
    MultiNodeEmbeddedCassandra.DefaultCassandraDir

  def logProps(logId: String): Props =
    CassandraEventLog.props(logId)

  override def atStartup(): Unit = {
    if (isNode(coordinator)) {
      MultiNodeEmbeddedCassandra.start(cassandraDir)
      Cassandra(system)
    }
    enterBarrier("startup")
  }

  override def afterAll(): Unit = {
    // get all config data before shutting down node
    val snapshotRootDir = new File(system.settings.config.getString("eventuate.snapshot.filesystem.dir"))

    // shut down node
    super.afterAll()

    // clean database and delete snapshot files
    if (isNode(coordinator)) {
      FileUtils.deleteDirectory(snapshotRootDir)
      MultiNodeEmbeddedCassandra.clean()
    }
  }
}
