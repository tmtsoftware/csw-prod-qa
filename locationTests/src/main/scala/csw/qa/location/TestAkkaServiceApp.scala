package csw.qa.location

import java.net.InetAddress

import akka.actor.ActorSystem
import akka.actor.CoordinatedShutdown.UnknownReason
import akka.stream.ActorMaterializer
import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.adapter._
import csw.location.client.ActorSystemFactory
import csw.location.scaladsl.LocationServiceFactory
import csw.logging.commons.LogAdminActorFactory
import csw.logging.messages.LogControlMessages
import csw.logging.scaladsl.{GenericLoggerFactory, LoggingSystemFactory}

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
object TestAkkaServiceApp extends App {

  implicit val system: ActorSystem = ActorSystemFactory.remote
  private val locationService = LocationServiceFactory.make()
  private val host = InetAddress.getLocalHost.getHostName
  LoggingSystemFactory.start("TestAkkaServiceApp", "0.1", host, system)
  private val log = GenericLoggerFactory.getLogger

  private val adminActorRef: ActorRef[LogControlMessages] = LogAdminActorFactory.make(system)

  log.debug("Started TestAkkaServiceApp")

  implicit val mat: ActorMaterializer = ActorMaterializer()

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
      Thread.sleep(delay) // Avoid timeouts?
      // Note: Need to start with the untyped system in order to have mixed typed/untyped actors!
      system.spawn(TestAkkaService.behavior(i, options, locationService, adminActorRef), s"TestAkkaService$i")
      if (startSecond)
        system.spawn(TestAkkaService2.behavior(i, options, locationService, adminActorRef), s"TestAkkaService2$i")
    }
    autoShutdown(options)

  }

  // If the autoshutdown option was specified, shutdown the app after the given number of seconds
  private def autoShutdown(options: Options): Unit = {
    import options._
    import system.dispatcher
    if (options.autoshutdown != 0) {
      system.scheduler.scheduleOnce(autoshutdown.seconds) {
        log.info(s"Auto-shutdown starting after $autoshutdown seconds")
        for {
          _ <- locationService.shutdown(UnknownReason)
          _ <- system.terminate()
        } {
          println("Shutdown complete")
        }
      }
    }
  }
}

