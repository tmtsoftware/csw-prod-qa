import sbt._

//noinspection TypeAnnotation
object Dependencies {

  val Version = "0.1-SNAPSHOT"
  val ScalaVersion = "2.12.2"
  val akkaVersion = "2.5.1"

  val `csw-location-local` = "org.tmt" %% "csw-location" % Version
  val `track-location-agent` = "org.tmt" %% "track-location-agent" % Version
  val `csw-config-client` = "org.tmt" %% "csw-config-client" % Version

  val scalaTest = "org.scalatest" %% "scalatest" % "3.0.1" // ApacheV2
  val `junit-interface` = "com.novocode" % "junit-interface" % "0.11"
}

