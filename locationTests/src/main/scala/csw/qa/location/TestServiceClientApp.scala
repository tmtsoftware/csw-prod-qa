package csw.qa.location

import akka.actor.{Actor, Props}
import akka.stream.{ActorMaterializer, Materializer}
import akka.stream.scaladsl.Sink
import csw.services.location.models.Connection.AkkaConnection
import csw.services.location.models.{AkkaLocation, LocationRemoved, LocationUpdated}
import csw.services.location.scaladsl.{ActorSystemFactory, LocationService, LocationServiceFactory}
import csw.services.logging.scaladsl.{GenericLogger, LoggingSystemFactory}

/**
  * A location service test client application that attempts to resolve one or more
  * akka services.
  * If a command line arg is given, it should be the number of services to resolve (default: 1).
  * An additional command line arg indicates the service number to start with (default: 1)
  * The client and service applications can be run on the same or different hosts.
  */
object TestServiceClientApp extends App {
  private val loggingSystem = LoggingSystemFactory.start()
  private val locationService = LocationServiceFactory.make()
  implicit val system = ActorSystemFactory.remote
  implicit val mat = ActorMaterializer()

  // Starts the application actor with the given number arg (or 1)
  val numServices = args.headOption.map(_.toInt).getOrElse(1)
  val firstService = args.tail.headOption.map(_.toInt).getOrElse(1)
  system.actorOf(TestServiceClient.props(numServices, firstService, locationService))

}

object TestServiceClient {

  // message sent when location stream ends (should not happen?)
  case object AllDone

  def props(numServices: Int, firstService: Int, locationService: LocationService)(implicit mat: Materializer): Props =
    Props(new TestServiceClient(numServices, firstService, locationService))
}

/**
  * A test client actor that uses the location service to resolve services
  */
class TestServiceClient(numServices: Int, firstService: Int, locationService: LocationService)(implicit mat: Materializer) extends Actor with GenericLogger.Simple {

  import TestServiceClient._

  private val connections: Set[AkkaConnection] = (firstService until firstService+numServices).toList.map(i => TestAkkaService.connection(i)).toSet

  // TODO: add a reusable class that does the below:

  // Calls track method for each connection and forwards location messages to this actor
  connections.foreach(locationService.track(_).to(Sink.actorRef(self, AllDone)).run())

  override def receive: Receive = {

    // Receive a location from the location service and if it is an akka location, send it a message
    case LocationUpdated(loc) =>
      log.info(s"Location updated ${loc.connection.name}")
      loc match {
        case AkkaLocation(_, _, actorRef) =>
          actorRef ! TestAkkaService.ClientMessage
        case x => log.error(s"Received unexpected location type: $x")
      }

    // A location was removed
    case LocationRemoved(conn) =>
      log.info(s"Location removed ${conn.name}")

    case x =>
      log.error(s"Received unexpected message $x")
  }

}

