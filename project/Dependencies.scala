import sbt._

//noinspection TypeAnnotation
object Dependencies {

  val Version = "0.1-SNAPSHOT"
  val ScalaVersion = "2.12.3"
  val akkaVersion = "2.5.3"

  val `csw-location-local` = "org.tmt" %% "csw-location" % Version
  val `csw-logging` = "org.tmt" %% "csw-logging" % Version
  val `csw-location-agent` = "org.tmt" %% "csw-location-agent" % Version
  val `csw-config-client` = "org.tmt" %% "csw-config-client" % Version
  val `csw-framework` = "org.tmt" %% "csw-framework" % Version

  val scopt = "com.github.scopt" %% "scopt" % "3.5.0" //MIT License
  val scalaTest = "org.scalatest" %% "scalatest" % "3.0.3" // ApacheV2
  val `junit-interface` = "com.novocode" % "junit-interface" % "0.11"

  val `slf4j-api` = "org.slf4j" % "slf4j-api" % "1.7.25"


}

