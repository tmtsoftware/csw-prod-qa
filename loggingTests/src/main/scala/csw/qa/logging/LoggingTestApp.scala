package csw.qa.logging

import java.net.InetAddress

import akka.actor
import akka.actor._
import akka.actor.typed.{ActorSystem, SpawnProtocol}
import akka.stream.Materializer
import akka.actor.typed.scaladsl.adapter.TypedActorSystemOps

import csw.location.client.scaladsl.HttpLocationServiceFactory
import csw.logging.client.scaladsl.{GenericLoggerFactory, LoggingSystemFactory}

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._

/**
 * An test application that uses the logging service
 */
object LoggingTestApp extends App {
  private val host                                   = InetAddress.getLocalHost.getHostName
  val typedSystem                                    = ActorSystem(SpawnProtocol(), "DatabaseTest")
  implicit lazy val untypedSystem: actor.ActorSystem = typedSystem.toClassic
  implicit val mat: Materializer                     = Materializer(typedSystem)
  implicit lazy val ec: ExecutionContextExecutor     = untypedSystem.dispatcher

  LoggingSystemFactory.start("LoggingTestApp", "0.1", host, typedSystem)
  private val log = GenericLoggerFactory.getLogger

  val locationService = HttpLocationServiceFactory.makeLocalClient(typedSystem)

  log.debug("Started LoggingTestApp")

  case class Options(numActors: Int = 1, autostop: Int = 0, autoshutdown: Int = 0, delay: Int = 1000)

  // Parses the command line options
  private val parser = new scopt.OptionParser[Options]("logging-test-app") {
    head("logging-test-app", System.getProperty("CSW_VERSION"))

    opt[Int]("numActors") valueName "<count>" action { (x, c) =>
      c.copy(numActors = x)
    } text "the number of actors to start (default: 1)"

    opt[Int]("delay") valueName "<ms>" action { (x, c) =>
      c.copy(delay = x)
    } text "the number of ms between log messages (default: 1000)"

    opt[Int]("autostop") valueName "<count>" action { (x, c) =>
      c.copy(autostop = x)
    } text "the number of seconds before unregistering and stopping each actor (default: 0 = no stopping)"

    opt[Int]("autoshutdown") valueName "<count>" action { (x, c) =>
      c.copy(autoshutdown = x)
    } text "the number of seconds before shutting down the app (default: 0, no shutting down)"

    help("help")
    version("version")
  }

  // Parse the command line options
  parser.parse(args, Options()) match {
    case Some(options) =>
      try {
        run(options)
      } catch {
        case e: Throwable =>
          e.printStackTrace()
          System.exit(1)
      }
    case None => System.exit(1)
  }

  private def run(options: Options): Unit = {
    import options._
    if (options.autoshutdown != 0)
      typedSystem.scheduler.scheduleOnce(
        autoshutdown.seconds,
        () => {
          log.info(s"Auto-shutdown starting after $autoshutdown seconds")
          for {
            _ <- untypedSystem.terminate()
          } {
            println("Shutdown complete")
          }
        }
      )

    for (i <- 0 until numActors) {
      untypedSystem.actorOf(LoggingTest.props(i, options))
    }
  }
}

object LoggingTest {
  // Creates the ith actor
  def props(i: Int, options: LoggingTestApp.Options): Props =
    Props(new LoggingTest(i, options))

  // Message sent from client once location has been resolved
  case object ClientMessage

  // Message to log some messages
  case object LogMessages

  // Message to unregister and quit
  case object Quit

}

/**
 * A dummy akka test actor
 */
class LoggingTest(i: Int, options: LoggingTestApp.Options) extends Actor {

  import context.dispatcher
  import options._

  private val log = GenericLoggerFactory.getLogger
  log.debug(s"In test actor $i")

  if (autostop != 0)
    context.system.scheduler.scheduleOnce(autostop.seconds, self, LoggingTest.Quit)

  private val logMsgTimer = context.system.scheduler.scheduleWithFixedDelay(delay.millis, delay.millis, self, LoggingTest.LogMessages)
  private val log4j2Test  = new Slf4jTest()

  override def receive: Receive = {
    case LoggingTest.LogMessages =>
      log.debug(s"Actor $i debug message")
      log.info(s"Actor $i info message")
      log.warn(s"Actor $i warn message")
      log4j2Test.foo();

    case LoggingTest.Quit =>
      log.info(s"Actor $i is shutting down after $autostop seconds")
      logMsgTimer.cancel()
      context.stop(self)

    case x =>
      log.error(s"Received unexpected message $x")
  }
}
