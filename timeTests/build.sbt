import sbt.Keys._
import sbt._

import Dependencies._
import Settings._

lazy val timeTests = (project in file("."))
  .enablePlugins(DeployApp)
  .settings(appSettings: _*)
  .settings(libraryDependencies ++= TimeClient.value)
