import sbt._

object Libs {
  val `scopt` = "com.github.scopt" %% "scopt" % "3.7.1" //MIT License
  val `scalaTest` = "org.scalatest" %% "scalatest" % "3.0.8" // ApacheV2
  val `junit-interface` = "com.novocode" % "junit-interface" % "0.11"
  val `slf4j-api` = "org.slf4j" % "slf4j-api" % "1.7.25"

}

object CSW {
  private val Org = "com.github.tmtsoftware.csw"
//  private val Version = "1.0.0"
  private val Version = "0.1.0-SNAPSHOT"

  val `csw-logging-client` = Org %% "csw-logging-client" % Version
  val `csw-commons` = Org %% "csw-commons" % Version
  val `csw-location-client` = Org %% "csw-location-client" % Version
  val `csw-location-api` = Org %% "csw-location-api" % Version
  val `csw-config-client` = Org %% "csw-config-client" % Version
  val `csw-aas-installed` = Org %% "csw-aas-installed" % Version
  val `csw-framework` = Org %% "csw-framework" % Version
  val `csw-event-client` = Org %% "csw-event-client" % Version
  val `csw-database` = Org %% "csw-database" % Version
  val `csw-prefix` = Org %% "csw-prefix" % Version
}
