package csw.qa.location

import java.net.InetAddress

import akka.actor.{Actor, Props}
import akka.stream.{ActorMaterializer, Materializer}
import akka.stream.scaladsl.Sink
import csw.services.location.models.Connection.AkkaConnection
import csw.services.location.models.{AkkaLocation, LocationRemoved, LocationUpdated}
import csw.services.location.scaladsl.{ActorSystemFactory, LocationService, LocationServiceFactory}
import csw.services.logging.internal.LoggingSystem
import csw.services.logging.scaladsl.{ComponentLogger, GenericLogger}
import scala.concurrent.duration._

/**
  * A location service test client application that attempts to resolve one or more
  * akka services.
  *
  * Type test-akka-service-app --help (or see below) for a description of the command line options.
  *
  * The client and service applications can be run on the same or different hosts.
  */
object TestServiceClientApp extends App with GenericLogger.Simple {
  private val locationService = LocationServiceFactory.make()
  private val host = InetAddress.getLocalHost.getHostName
  implicit val system = ActorSystemFactory.remote
  private val loggingSystem = new LoggingSystem(
    name = "TestServiceClientApp",
    version = "0.1",
    host = host,
    system = system)
  implicit val mat = ActorMaterializer()
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
    import options._
    import system.dispatcher

    system.actorOf(TestServiceClient.props(options, locationService))

    if (options.autoshutdown != 0)
      system.scheduler.scheduleOnce(autoshutdown.seconds) {
        log.info(s"Auto-shutdown starting after $autoshutdown seconds")
        for {
          _ <- locationService.shutdown()
//          _ <- loggingSystem.stop
          _ <- system.terminate()
        } {
          println("Shutdown complete")
        }
      }
  }
}

object TestServiceClient {

  // message sent when location stream ends (should not happen?)
  case object AllDone

  def props(options: TestServiceClientApp.Options, locationService: LocationService)(implicit mat: Materializer): Props =
    Props(new TestServiceClient(options, locationService))
}

object TestServiceClientLogger extends ComponentLogger("TestServiceClient")

/**
  * A test client actor that uses the location service to resolve services
  */
class TestServiceClient(options: TestServiceClientApp.Options, locationService: LocationService)(implicit mat: Materializer)
  extends Actor with TestServiceClientLogger.Actor {

  import TestServiceClient._
  import options._

  private val connections: Set[AkkaConnection] = (firstService until firstService + numServices).
    toList.map(i => TestAkkaService.connection(i)).toSet

  // TODO: add a reusable class that does the below:

  // Calls track method for each connection and forwards location messages to this actor
  connections.foreach(locationService.track(_).to(Sink.actorRef(self, AllDone)).run())

  override def receive: Receive = {

    // Receive a location from the location service and if it is an akka location, send it a message
    case LocationUpdated(loc) =>
      log.debug(s"Location updated ${loc.connection.name}")
      loc match {
        case AkkaLocation(_, _, actorRef) =>
          actorRef ! TestAkkaService.ClientMessage
        case x => log.error(s"Received unexpected location type: $x")
      }

    // A location was removed
    case LocationRemoved(conn) =>
      log.debug(s"Location removed ${conn.name}")

    case x =>
      log.error(s"Received unexpected message $x")
  }

}

