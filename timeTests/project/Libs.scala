import sbt._

object Libs {
  val ScalaVersion = "2.12.7"
  val AcyclicVersion = "0.1.8"

  val `scalatest` = "org.scalatest" %% "scalatest" % "3.0.5" //Apache License 2.0
  val `scala-java8-compat` = "org.scala-lang.modules" %% "scala-java8-compat" % "0.9.0" //BSD 3-clause "New" or "Revised" License
  val `scala-async` = "org.scala-lang.modules" %% "scala-async" % "0.9.7" //BSD 3-clause "New" or "Revised" License
  val `scopt` = "com.github.scopt" %% "scopt" % "3.7.0" //MIT License
  val `acyclic` = "com.lihaoyi" %% "acyclic" % AcyclicVersion % Provided //MIT License
  val `junit` = "junit" % "junit" % "4.12" //Eclipse Public License 1.0
  val `junit-interface` = "com.novocode" % "junit-interface" % "0.11" //BSD 2-clause "Simplified" License
  val `mockito-core` = "org.mockito" % "mockito-core" % "2.23.0" //MIT License
  val `logback-classic` = "ch.qos.logback" % "logback-classic" % "1.2.3" //Dual license: Either, Eclipse Public License v1.0 or GNU Lesser General Public License version 2.1
  val `akka-management-cluster-http` = "com.lightbend.akka" %% "akka-management-cluster-http" % "0.6" //N/A at the moment
  val `svnkit` = "org.tmatesoft.svnkit" % "svnkit" % "1.9.3" //TMate Open Source License
  val `commons-codec` = "commons-codec" % "commons-codec" % "1.10" //Apache 2.0
  val `persist-json` = "com.persist" %% "persist-json" % "1.2.1" //Apache 2.0
  val `joda-time` = "joda-time" % "joda-time" % "2.10.1" //Apache 2.0
  val `scala-reflect` = "org.scala-lang" % "scala-reflect" % ScalaVersion //BSD-3
  val `gson` = "com.google.code.gson" % "gson" % "2.8.5" //Apache 2.0

  val `akka-http-play-json` = "de.heikoseeberger" %% "akka-http-play-json" % "1.22.0" //Apache 2.0
  val `jwt-play-json` = "com.pauldijou" %% "jwt-play-json" % "0.19.0"
  val `lettuce` = "io.lettuce" % "lettuce-core" % "5.0.5.RELEASE"
  val `akka-stream-kafka` = "com.typesafe.akka" %% "akka-stream-kafka" % "0.22"
  val `scalatest-embedded-kafka` = "net.manub" %% "scalatest-embedded-kafka" % "1.1.0"
  val `embedded-redis` = "com.github.kstyrc" % "embedded-redis" % "0.6"
  val `scala-compiler` = "org.scala-lang" % "scala-compiler" % ScalaVersion
  val `HdrHistogram` = "org.hdrhistogram" % "HdrHistogram" % "2.1.10"
  val `testng` = "org.testng" % "testng" % "6.14.3"

  val `scala-csv` = "com.github.tototoshi" %% "scala-csv" % "1.3.5"
  val `json-schema-validator` = "com.github.fge" % "json-schema-validator" % "2.2.6" //LGPL/ASL

  val `time4j` = "net.time4j" % "time4j-core" % "4.38"
  val `threeten-extra` = "org.threeten" % "threeten-extra" % "1.4"
  val `jna` = "net.java.dev.jna" % "jna" % "5.1.0"

  val `postgresql` = "org.postgresql" % "postgresql" % "42.2.5"
  val `postgresql-embedded` = "ru.yandex.qatools.embed" % "postgresql-embedded" % "2.10"
  val `otj-pg-embedded` = "com.opentable.components" % "otj-pg-embedded" % "0.12.5"
  val requests = "com.lihaoyi" %% "requests" % "0.1.4"
  val httpclient = "org.apache.httpcomponents" % "httpclient" % "4.5.6"
  val `jboss-logging` = "org.jboss.logging" % "jboss-logging" % "3.3.2.Final"
  val `config` = "com.typesafe" % "config" % "1.3.3"
  val `os-lib` = "com.lihaoyi" %% "os-lib" % "0.2.2"
}

object Akka {
  val Version = "2.5.18" //all akka is Apache License 2.0
  val `akka-stream` = "com.typesafe.akka" %% "akka-stream" % Version
  val `akka-stream-typed` = "com.typesafe.akka" %% "akka-stream-typed" % Version
  val `akka-remote` = "com.typesafe.akka" %% "akka-remote" % Version
  val `akka-stream-testkit` = "com.typesafe.akka" %% "akka-stream-testkit" % Version
  val `akka-actor` = "com.typesafe.akka" %% "akka-actor" % Version
  val `akka-actor-typed` = "com.typesafe.akka" %% "akka-actor-typed" % Version
  val `akka-actor-testkit-typed` = "com.typesafe.akka" %% "akka-actor-testkit-typed" % Version
  val `akka-distributed-data` = "com.typesafe.akka" %% "akka-distributed-data" % Version
  val `akka-multi-node-testkit` = "com.typesafe.akka" %% "akka-multi-node-testkit" % Version
  val `akka-cluster-tools` = "com.typesafe.akka" %% "akka-cluster-tools" % Version
  val `akka-cluster-typed` = "com.typesafe.akka" %% "akka-cluster-typed" % Version
  val `akka-slf4j` = "com.typesafe.akka" %% "akka-slf4j" % Version
}
