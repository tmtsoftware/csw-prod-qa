import sbt._

object Dependencies {

  val TimeClient = Def.setting(
    Seq(
      Libs.`time4j`,
      Libs.`threeten-extra`,
      Libs.`jna`,
      Akka.`akka-actor`,
      Libs.`junit-interface`,
      Libs.`scalatest`,
      Akka.`akka-actor-testkit-typed`,
      Libs.`mockito-core`
    )
  )
}

