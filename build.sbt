import sbt._
import sbt.Keys._
import sbtunidoc.Plugin.UnidocKeys._

import MultiJvmKeys._

import ProjectSettings._
import ProjectDependencies._

version in ThisBuild := "0.8-SNAPSHOT"

organization in ThisBuild := "com.rbmhtechnology"

scalaVersion in ThisBuild := "2.11.7"

lazy val root = (project in file("."))
  .aggregate(core, crdt, logCassandra, logLeveldb, adapterSpark, examples, exampleSpark)
  .dependsOn(core, logCassandra, logLeveldb)
  .settings(name := "eventuate")
  .settings(commonSettings: _*)
  .settings(documentationSettings: _*)
  .settings(unidocProjectFilter in (ScalaUnidoc, unidoc) := inAnyProject -- inProjects(examples))
  .settings(libraryDependencies ++= Seq(AkkaRemote))
  .enablePlugins(HeaderPlugin, AutomateHeaderPlugin)

lazy val core = (project in file("eventuate-core"))
  .settings(name := "eventuate-core")
  .settings(commonSettings: _*)
  .settings(protocSettings: _*)
  .settings(integrationTestSettings: _*)
  .settings(libraryDependencies ++= Seq(AkkaRemote, CommonsIo, Java8Compat, Scalaz))
  .settings(libraryDependencies ++= Seq(AkkaTestkit % "test,it", AkkaTestkitMultiNode % "test", Javaslang % "test", JunitInterface % "test", Scalatest % "test,it"))
  .configs(IntegrationTest, MultiJvm)
  .enablePlugins(HeaderPlugin, AutomateHeaderPlugin)

lazy val logCassandra = (project in file("eventuate-log-cassandra"))
  .dependsOn(core % "compile->compile;it->it;multi-jvm->multi-jvm")
  .settings(name := "eventuate-log-cassandra")
  .settings(commonSettings: _*)
  .settings(integrationTestSettings: _*)
  .settings(libraryDependencies ++= Seq(AkkaRemote, CassandraDriver))
  .settings(libraryDependencies ++= Seq(AkkaTestkit % "test,it", AkkaTestkitMultiNode % "test", Log4jApi % "test,it", Log4jCore % "test,it", Log4jSlf4j % "test,it", Scalatest % "test,it", Sigar % "test,it"))
  .settings(libraryDependencies ++= Seq(CassandraUnit % "test,it" excludeAll ExclusionRule(organization = "ch.qos.logback")))
  .settings(jvmOptions in MultiJvm += "-Dmultinode.server-port=4712")
  .configs(IntegrationTest, MultiJvm)
  .enablePlugins(HeaderPlugin, AutomateHeaderPlugin)

lazy val logLeveldb = (project in file("eventuate-log-leveldb"))
  .dependsOn(core % "compile->compile;it->it;multi-jvm->multi-jvm")
  .settings(name := "eventuate-log-leveldb")
  .settings(commonSettings: _*)
  .settings(integrationTestSettings: _*)
  .settings(libraryDependencies ++= Seq(AkkaRemote, Leveldb))
  .settings(libraryDependencies ++= Seq(AkkaTestkit % "test,it", AkkaTestkitMultiNode % "test", Scalatest % "test,it"))
  .settings(jvmOptions in MultiJvm += "-Dmultinode.server-port=4713")
  .configs(IntegrationTest, MultiJvm)
  .enablePlugins(HeaderPlugin, AutomateHeaderPlugin)

lazy val adapterSpark  = (project in file("eventuate-adapter-spark"))
  .dependsOn(logCassandra % "compile->compile;it->it;multi-jvm->multi-jvm")
  .dependsOn(logLeveldb % "compile->compile;it->it;multi-jvm->multi-jvm")
  .settings(name := "eventuate-adapter-spark")
  .settings(commonSettings: _*)
  .settings(integrationTestSettings: _*)
  .settings(libraryDependencies ++= Seq(AkkaRemote, CassandraClientUtil, CassandraConnector,
    SparkCore % "provided" exclude("org.slf4j", "slf4j-log4j12"),
    SparkSql % "provided" exclude("org.slf4j", "slf4j-log4j12"),
    SparkStreaming % "provided" exclude("org.slf4j", "slf4j-log4j12")))
  .settings(libraryDependencies ++= Seq(AkkaTestkit % "test,it", AkkaTestkitMultiNode % "test", Scalatest % "test,it", Sigar % "test,it"))
  .settings(libraryDependencies ++= Seq(CassandraUnit % "test,it" excludeAll ExclusionRule(organization = "ch.qos.logback")))
  .settings(jvmOptions in MultiJvm += "-Dmultinode.server-port=4714")
  .configs(IntegrationTest, MultiJvm)
  .enablePlugins(HeaderPlugin, AutomateHeaderPlugin)

lazy val crdt = (project in file("eventuate-crdt"))
  .dependsOn(core % "compile->compile;it->it;multi-jvm->multi-jvm")
  .dependsOn(logLeveldb % "test;it->it;multi-jvm->multi-jvm")
  .settings(name := "eventuate-crdt")
  .settings(commonSettings: _*)
  .settings(protocSettings: _*)
  .settings(integrationTestSettings: _*)
  .settings(libraryDependencies ++= Seq(AkkaRemote))
  .settings(libraryDependencies ++= Seq(AkkaTestkit % "test,it", AkkaTestkitMultiNode % "test", Scalatest % "test,it"))
  .settings(jvmOptions in MultiJvm += "-Dmultinode.server-port=4715")
  .configs(IntegrationTest, MultiJvm)
  .enablePlugins(HeaderPlugin, AutomateHeaderPlugin)

lazy val examples = (project in file("eventuate-examples"))
  .dependsOn(core, logLeveldb)
  .settings(name := "eventuate-examples")
  .settings(commonSettings: _*)
  .settings(exampleSettings: _*)
  .settings(libraryDependencies ++= Seq(AkkaRemote, CassandraDriver, Javaslang, Log4jApi, Log4jCore, Log4jSlf4j))
  .enablePlugins(HeaderPlugin, AutomateHeaderPlugin)

lazy val exampleSpark = (project in file("eventuate-example-spark"))
  .dependsOn(core, logCassandra, adapterSpark)
  .settings(name := "eventuate-example-spark")
  .settings(commonSettings: _*)
  .settings(exampleSettings: _*)
  .settings(libraryDependencies ++= Seq(AkkaRemote, CassandraDriver, Log4jApi, Log4jCore, Log4jSlf4j,
    SparkCore exclude("org.slf4j", "slf4j-log4j12"),
    SparkSql exclude("org.slf4j", "slf4j-log4j12"),
    SparkStreaming exclude("org.slf4j", "slf4j-log4j12")))
  .enablePlugins(HeaderPlugin, AutomateHeaderPlugin)
