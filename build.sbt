import sbt.Keys._
import sbt._

import Dependencies._
import Settings._

lazy val locationTests = project
  .enablePlugins(DeployApp)
  .settings(defaultSettings: _*)
  .settings(libraryDependencies ++= Seq(
    `track-location-agent`,
    `akka-slf4j`,
    `scala-logging`,
    `logback-classic`,
    `logstash-logback-encoder`,
    janino
  ))

lazy val configTests = project
  .enablePlugins(DeployApp)
  .settings(defaultSettings: _*)
  .settings(libraryDependencies ++= Seq(
    `csw-config-client`,
    `akka-slf4j`,
    `scala-logging`,
    `logback-classic`,
    `logstash-logback-encoder`,
    janino,
    `junit-interface` % Test,
    scalaTest % Test
  ))


