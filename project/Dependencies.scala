import sbt._

//noinspection TypeAnnotation
object Dependencies {

  val Version = "0.1-SNAPSHOT"
  val ScalaVersion = "2.12.2"
  val akkaVersion = "2.5.1"

  val `csw-location-local` = "org.tmt" %% "csw-location" % Version
  val `csw-logging` = "org.tmt" %% "csw-logging" % Version
  val `track-location-agent` = "org.tmt" %% "track-location-agent" % Version
  val `csw-config-client` = "org.tmt" %% "csw-config-client" % Version


  val scopt = "com.github.scopt" %% "scopt" % "3.5.0" //MIT License
  val scalaTest = "org.scalatest" %% "scalatest" % "3.0.1" // ApacheV2
  val `junit-interface` = "com.novocode" % "junit-interface" % "0.11"

//  val `log4j-api` = "org.apache.logging.log4j" % "log4j-api" % "2.8.2"
//  val `log4j-core` = "org.apache.logging.log4j" % "log4j-core" % "2.8.2"
//  val `akka-slf4j` = "com.typesafe.akka" %% "akka-slf4j" % Version
  val `slf4j-api` = "org.slf4j" % "slf4j-api" % "1.7.25"


}

