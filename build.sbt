import sbt.Keys._
import sbt._

import Dependencies._
import Settings._

lazy val locationTests = project
  .enablePlugins(DeployApp)
  .settings(defaultSettings: _*)
  .settings(libraryDependencies ++= Seq(
    `track-location-agent`
  ))

lazy val loggingTests = project
  .enablePlugins(DeployApp)
  .settings(defaultSettings: _*)
  .settings(libraryDependencies ++= Seq(
    `csw-logging`,
    scopt
  ))

lazy val configTests = project
  .enablePlugins(DeployApp)
  .settings(defaultSettings: _*)
  .settings(libraryDependencies ++= Seq(
    `csw-config-client`,
    `junit-interface` % Test,
    scalaTest % Test
  ))


