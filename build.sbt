import sbt.Keys._
import sbt._

import Dependencies._
import Settings._

lazy val locationTests = project
  .settings(defaultSettings: _*)
  .settings(libraryDependencies ++= Seq(
    `track-location-agent`,
    `akka-slf4j`,
    `scala-logging`,
    `logback-classic`,
    `logstash-logback-encoder`,
    janino
  ))

