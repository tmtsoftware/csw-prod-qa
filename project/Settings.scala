import sbt.Keys._
import sbt._
import com.typesafe.sbt.packager.Keys._

//noinspection TypeAnnotation
// Defines the global build settings so they don't need to be edited everywhere
object Settings {
  val Version = "0.1-SNAPSHOT"
  val ScalaVersion = "2.12.8"

  val buildSettings = Seq(
    organization := "org.tmt",
    organizationName := "TMT",
    organizationHomepage := Some(url("http://www.tmt.org")),
    version := Version,
    scalaVersion := ScalaVersion,
//    crossPaths := true,
    parallelExecution in Test := false,
    fork := true,
    resolvers += "jitpack" at "https://jitpack.io",
    resolvers += "bintray" at "http://jcenter.bintray.com",
    updateOptions := updateOptions.value.withLatestSnapshots(false)
  )

  lazy val appSettings = buildSettings ++ Seq(
    bashScriptExtraDefines ++= Seq(s"addJava -DVERSION=$Version")
  )
}
