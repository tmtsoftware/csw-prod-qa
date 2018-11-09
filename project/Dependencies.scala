import sbt._

object Dependencies {

  val `locationTests-deps` = Seq(
    CSW.`csw-location-client`,
    CSW.`csw-location-api`,
    CSW.`csw-framework`
  )

  val `loggingTests-deps` = Seq(
    CSW.`csw-logging`,
    CSW.`csw-commons`,
    Libs.`scopt`,
    Libs.`slf4j-api`
  )

  val `configTests-deps` = Seq(
    CSW.`csw-config-client`,
    CSW.`csw-location-client`,
    Libs.`junit-interface` % Test,
    Libs.scalaTest % Test
  )

  val `frameworkTests-deps` = Seq(
    CSW.`csw-framework`,
//    Libs.`ea-async`,
    Libs.`junit-interface` % Test,
    Libs.`scalaTest` % Test
  )
}

