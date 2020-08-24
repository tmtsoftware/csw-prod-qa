import sbt._

object Dependencies {

  val `locationTests-deps` = Seq(
    CSW.`csw-location-client`,
    CSW.`csw-location-api`,
    CSW.`csw-framework`,
    CSW.`csw-prefix`
  )

  val `loggingTests-deps` = Seq(
    CSW.`csw-logging-client`,
    CSW.`csw-framework`,
    CSW.`csw-commons`,
    CSW.`csw-prefix`,
    Libs.`scopt`,
    Libs.`slf4j-api`
  )

  val `configTests-deps` = Seq(
    CSW.`csw-config-client`,
    CSW.`csw-config-cli`,
    CSW.`csw-aas-installed`,
    CSW.`csw-location-client`,
    Libs.`junit-interface` % Test,
    Libs.scalaTest         % Test
  )

  val `frameworkTests-deps` = Seq(
    CSW.`csw-framework`,
//    CSW.`csw-logging-client`,
    CSW.`csw-database`,
    CSW.`csw-prefix`,
    Libs.`junit-interface` % Test,
    Libs.`scalaTest`       % Test
  )

  val `databaseTests-deps` = Seq(
    CSW.`csw-framework`,
    CSW.`csw-database`,
    CSW.`csw-prefix`,
    Libs.`junit-interface` % Test,
    Libs.`scalaTest`       % Test
  )
}
