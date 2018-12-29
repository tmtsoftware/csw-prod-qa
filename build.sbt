import sbt.Keys._
import sbt._

import Dependencies._
import Settings._

lazy val locationTests = project
  .enablePlugins(DeployApp)
  .settings(appSettings: _*)
  .settings(libraryDependencies ++= `locationTests-deps`)

lazy val loggingTests = project
  .enablePlugins(DeployApp)
  .settings(appSettings: _*)
  .settings(libraryDependencies ++= `loggingTests-deps`)

lazy val configTests = project
  .enablePlugins(DeployApp)
  .settings(appSettings: _*)
  .settings(libraryDependencies ++= `configTests-deps`)

lazy val frameworkTests = project
  .enablePlugins(DeployApp)
  .settings(appSettings: _*)
  .settings(libraryDependencies ++= `frameworkTests-deps`)

lazy val databaseTests = project
  .enablePlugins(DeployApp)
  .settings(appSettings: _*)
  .settings(libraryDependencies ++= `databaseTests-deps`)
