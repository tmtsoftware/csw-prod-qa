import sbt._

object Libs {
  val `scopt` = "com.github.scopt" %% "scopt" % "4.0.1" //MIT License
  val `scalaTest` = "org.scalatest" %% "scalatest" % "3.2.11" // ApacheV2
  val `junit-interface` = "com.github.sbt" % "junit-interface" % "0.13.2"
  val `slf4j-api` = "org.slf4j" % "slf4j-api" % "1.7.25"

}

object CSW {
  private val Org = "com.github.tmtsoftware.csw"
  private val Version = "5.0.0"
//  private val Version = "0.1.0-SNAPSHOT"

  val `csw-logging-client` = Org %% "csw-logging-client" % Version
  val `csw-commons` = Org %% "csw-commons" % Version
  val `csw-location-client` = Org %% "csw-location-client" % Version
  val `csw-location-api` = Org %% "csw-location-api" % Version
  val `csw-config-client` = Org %% "csw-config-client" % Version
  val `csw-config-cli` = Org %% "csw-config-cli" % Version
  val `csw-aas-installed` = Org %% "csw-aas-installed" % Version
  val `csw-framework` = Org %% "csw-framework" % Version
  val `csw-event-client` = Org %% "csw-event-client" % Version
  val `csw-database` = Org %% "csw-database" % Version
  val `csw-prefix` = Org %% "csw-prefix" % Version
}
