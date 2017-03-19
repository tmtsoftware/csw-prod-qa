import sbt._

object Dependencies {

  val Version = "10000" // XXX FIXME
  val ScalaVersion = "2.12.1"
  val akkaVersion = "2.4.17"


  val `csw-location-local` = "org.tmt" %% "csw-location" % Version
  val `track-location-local` = "org.tmt" %% "tracklocation" % Version

  val `akka-slf4j` = "com.typesafe.akka" %% "akka-slf4j" % akkaVersion
  val `scala-logging` = "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0" // ApacheV2
  val `logback-classic` = "ch.qos.logback" % "logback-classic" % "1.1.10" // EPL v1.0 and the LGPL 2.1
  val `logstash-logback-encoder` = "net.logstash.logback"   % "logstash-logback-encoder" % "4.8" // ApacheV2
  // Required by logback (runtime dependency)
  val janino = "org.codehaus.janino" % "janino" % "3.0.6" // BSD
}

