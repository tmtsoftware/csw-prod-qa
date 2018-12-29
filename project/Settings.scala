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
    crossPaths := true,
    parallelExecution in Test := false,
    fork := true,
    resolvers += "twtmt-maven" at "http://dl.bintray.com/twtmt/maven/",
    resolvers += "jitpack" at "https://jitpack.io",
    updateOptions := updateOptions.value.withLatestSnapshots(false)
  )

  lazy val defaultSettings = buildSettings ++ Seq(
    // compile options ScalaUnidoc, unidoc
    scalacOptions ++= Seq("-target:jvm-1.8", "-encoding", "UTF-8", "-feature", "-deprecation", "-unchecked"),
    javacOptions in Compile ++= Seq("-source", "1.8"),
    javacOptions in (Compile, compile) ++= Seq("-source", "1.8", "-target", "1.8", "-Xlint:unchecked", "-Xlint:deprecation"),
    javaOptions in (Test, run) ++= Seq("-Djava.net.preferIPv4Stack=true")  // For location service use
  )

  lazy val appSettings = defaultSettings ++ Seq(
    bashScriptExtraDefines ++= Seq(s"addJava -DVERSION=$Version")
  )
}
