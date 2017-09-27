import sbt.Keys._
import sbt._

import Dependencies._
import Settings._

lazy val locationTests = project
  .enablePlugins(DeployApp)
  .settings(appSettings: _*)
  .settings(libraryDependencies ++= Seq(
    `track-location-agent`
  ))

lazy val loggingTests = project
  .enablePlugins(DeployApp)
  .settings(appSettings: _*)
  .settings(libraryDependencies ++= Seq(
    `csw-logging`,
    scopt,
    `slf4j-api`
  ))

lazy val configTests = project
  .enablePlugins(DeployApp)
  .settings(appSettings: _*)
  .settings(libraryDependencies ++= Seq(
    `csw-config-client`,
    `junit-interface` % Test,
    scalaTest % Test
  ))

lazy val frameworkTests = project
  .enablePlugins(DeployApp)
  .settings(appSettings: _*)
  .settings(libraryDependencies ++= Seq(
    `csw-framework`,
    `junit-interface` % Test,
    scalaTest % Test
  ))
