import sbt._

//noinspection TypeAnnotation
object Dependencies {

  val Version = "0.1-SNAPSHOT"
  val ScalaVersion = "2.12.6"

  val `csw-logging` = "org.tmt" %% "csw-logging" % Version
  val `csw-commons` = "org.tmt" %% "csw-commons" % Version
  val `csw-command` = "org.tmt" %% "csw-command" % Version
  val `csw-location-agent` = "org.tmt" %% "csw-location-agent" % Version
  val `csw-config-client` = "org.tmt" %% "csw-config-client" % Version
  val `csw-framework` = "org.tmt" %% "csw-framework" % Version
  val `csw-event-client` = "org.tmt" %% "csw-event-client" % Version

  val scopt = "com.github.scopt" %% "scopt" % "3.7.0" //MIT License
  val scalaTest = "org.scalatest" %% "scalatest" % "3.0.5" // ApacheV2
  val `junit-interface` = "com.novocode" % "junit-interface" % "0.11"

  val `slf4j-api` = "org.slf4j" % "slf4j-api" % "1.7.25"


}

