package csw.qa.location


import java.net.InetAddress

import akka.actor.ActorSystem
import akka.actor.CoordinatedShutdown.UnknownReason
import akka.stream.ActorMaterializer
import csw.services.location.scaladsl.LocationServiceFactory
import csw.services.logging.scaladsl._
import akka.typed.scaladsl.adapter._
import csw.services.location.commons.ActorSystemFactory

import scala.concurrent.duration._

/**
  * A location service test client application that attempts to resolve one or more
  * akka services.
  *
  * Type test-akka-service-app --help (or see below) for a description of the command line options.
  *
  * The client and service applications can be run on the same or different hosts.
  */
object TestServiceClientApp extends App {
  private val locationService = LocationServiceFactory.make()
  private val host = InetAddress.getLocalHost.getHostName
  implicit val system: ActorSystem = ActorSystemFactory.remote
  LoggingSystemFactory.start("TestServiceClientApp", "0.1", host, system)
  implicit val mat: ActorMaterializer = ActorMaterializer()
  private val log = GenericLoggerFactory.getLogger
  log.info(s"TestServiceClientApp is running on $host")

  case class Options(numServices: Int = 1, firstService: Int = 1, autoshutdown: Int = 0)

  // Parses the command line options
  private val parser = new scopt.OptionParser[Options]("test-akka-service-app") {
    head("test-akka-service-app", System.getProperty("CSW_VERSION"))

    opt[Int]("numServices") valueName "<count>" action { (x, c) =>
      c.copy(numServices = x)
    } text "the number of services to resolve (default: 1)"

    opt[Int]("firstService") valueName "<n>" action { (x, c) =>
      c.copy(firstService = x)
    } text "the service number to start with (default: 1)"

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
    // Note: Need to start with the untyped system in order to have mixed typed/untyped actors!
    system.spawn(TestServiceClient.behavior(options, locationService), "TestServiceClientApp")
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

