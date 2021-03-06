package csw.qa.location

import java.net.InetAddress

import akka.actor.typed.{ActorSystem, SpawnProtocol}

import csw.location.client.scaladsl.HttpLocationServiceFactory
import csw.logging.client.scaladsl.{GenericLoggerFactory, LoggingSystemFactory}
import csw.logging.client.commons.AkkaTypedExtension.UserActorFactory

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._

/**
  * Starts one or more akka services in order to test the location service.
  *
  * Type test-akka-service-app --help (or see below) for a description of the command line options.
  *
  * Each service will have a number appended to its name.
  * You should start the TestServiceClient with the same number, so that it
  * will try to find all the services.
  *
  * The client and service applications can be run on the same or different hosts.
  */
//noinspection DuplicatedCode
object TestAkkaServiceApp extends App {

  private val host = InetAddress.getLocalHost.getHostName
//  implicit val typedSystem: ActorSystem[Nothing] = ActorSystem(Behaviors.empty, "event-server")
  implicit val typedSystem: ActorSystem[SpawnProtocol.Command] = ActorSystem(SpawnProtocol(), "TestAkkaServiceApp")
  implicit lazy val ec: ExecutionContextExecutor            = typedSystem.executionContext

  LoggingSystemFactory.start("TestAkkaServiceApp", "0.1", host, typedSystem)
  private val log = GenericLoggerFactory.getLogger

  log.debug("Started TestAkkaServiceApp")


  val locationService = HttpLocationServiceFactory.makeLocalClient(typedSystem)

  case class Options(numServices: Int = 1, firstService: Int = 1,
                     autostop: Int = 0, autoshutdown: Int = 0, delay: Int = 100,
                     logMessages: Boolean = false, startSecond: Boolean = false)

  // Parses the command line options
  private val parser = new scopt.OptionParser[Options]("test-akka-service-app") {
    head("test-akka-service-app", System.getProperty("CSW_VERSION"))

    opt[Int]("numServices") valueName "<count>" action { (x, c) =>
      c.copy(numServices = x)
    } text "the number of services to start (default: 1)"

    opt[Int]("firstService") valueName "<n>" action { (x, c) =>
      c.copy(firstService = x)
    } text "the service number to start with (default: 1)"

    opt[Int]("autostop") valueName "<count>" action { (x, c) =>
      c.copy(autostop = x)
    } text "the number of seconds before unregistering and stopping each actor (default: 0 = no stopping)"

    opt[Int]("autoshutdown") valueName "<count>" action { (x, c) =>
      c.copy(autoshutdown = x)
    } text "the number of seconds before shutting down the app (default: 0, no shutting down)"

    opt[Int]("delay") valueName "<ms>" action { (x, c) =>
      c.copy(delay = x)
    } text "the number of ms to wait before starting each service (default: 100)"

    opt[Unit]("logMessages") action { (_, c) =>
      c.copy(logMessages = true)
    } text "If given, log messages received from the client app (default: not logged)"

    opt[Unit]("startSecond") action { (_, c) =>
      c.copy(startSecond = true)
    } text "If given, start a second service for each service (with 2 appended to name) (default: not started)"

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

    for (i <- firstService until firstService + numServices) {
      typedSystem.spawn(TestAkkaService.behavior(i, options, locationService), s"TestAkkaService$i")
      if (startSecond)
        typedSystem.spawn(TestAkkaService2.behavior(i, options, locationService), s"TestAkkaService2$i")
    }
    autoShutdown(options)

  }

  // If the autoshutdown option was specified, shutdown the app after the given number of seconds
  private def autoShutdown(options: Options): Unit = {
    import options._
    if (options.autoshutdown != 0) {
      typedSystem.scheduler.scheduleOnce(autoshutdown.seconds, () => {
        log.info(s"Auto-shutdown starting after $autoshutdown seconds")
        typedSystem.terminate()
      })
    }
  }
}

