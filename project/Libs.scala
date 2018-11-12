import sbt._

object Libs {
  val `scopt` = "com.github.scopt" %% "scopt" % "3.7.0" //MIT License
  val `scalaTest` = "org.scalatest" %% "scalatest" % "3.0.5" // ApacheV2
  val `junit-interface` = "com.novocode" % "junit-interface" % "0.11"
  val `slf4j-api` = "org.slf4j" % "slf4j-api" % "1.7.25"
//  val `ea-async` = "com.ea.async" % "ea-async" % "1.2.1" //  BSD 3

}

object CSW {
  private val Org = "com.github.tmtsoftware.csw"
//  private val Version = "0.1-SNAPSHOT"
  private val Version = "002262af0"

  val `csw-logging` = Org %% "csw-logging" % Version
  val `csw-commons` = Org %% "csw-commons" % Version
  val `csw-location-client` = Org %% "csw-location-client" % Version
  val `csw-location-api` = Org %% "csw-location-api" % Version
  val `csw-config-client` = Org %% "csw-config-client" % Version
  val `csw-framework` = Org %% "csw-framework" % Version
  val `csw-event-client` = Org %% "csw-event-client" % Version
}
