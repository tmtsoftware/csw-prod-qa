package csw.qa.location

import akka.actor.{Actor, ActorLogging, Props}
import akka.stream.{ActorMaterializer, Materializer}
import akka.stream.scaladsl.Sink
import csw.services.location.models.Connection.AkkaConnection
import csw.services.location.models.{AkkaLocation, Location, LocationRemoved, LocationUpdated}
import csw.services.location.scaladsl.{ActorSystemFactory, LocationService, LocationServiceFactory}

/**
  * A location service test client application that attempts to resolve one or more
  * akka services.
  * If a command line arg is given, it should be the number of services to resolve (default: 1).
  * The client and service applications can be run on the same or different hosts.
  */
object TestServiceClientApp extends App {
  private val locationService = LocationServiceFactory.make()
  implicit val system = ActorSystemFactory.remote
  implicit val mat = ActorMaterializer()

  val numServices = args.headOption.map(_.toInt).getOrElse(1)
  system.actorOf(TestServiceClient.props(numServices, locationService))

}

object TestServiceClient {

  // message sent when location stream ends (should not happen?)
  case object AllDone

  def props(numServices: Int, locationService: LocationService)(implicit mat: Materializer): Props =
    Props(new TestServiceClient(numServices, locationService))
}

/**
  * A test client actor that uses the location service to resolve services
  */
class TestServiceClient(numServices: Int, locationService: LocationService)(implicit mat: Materializer) extends Actor with ActorLogging {

  import TestServiceClient._

  private val connections: Set[AkkaConnection] = (1 to numServices).toList.map(i => TestAkkaService.connection(i)).toSet
  log.info(s"TestServiceClient: looking up connections = $connections")

  // TODO: add a reusable class that does the below:

  // Calls track method for each connection and forwards location messages to this actor
  connections.foreach(locationService.track(_).to(Sink.actorRef(self, AllDone)).run())

  override def receive: Receive = {

    case LocationUpdated(loc) =>
      log.info(s"Location updated $loc")
      loc match {
        case AkkaLocation(_, _, actorRef) =>
          actorRef ! TestAkkaService.ClientMessage
        case x => log.error(s"Received unexpected location type: $x")
      }

    case LocationRemoved(conn) =>
      log.info(s"Location removed $conn")

    case x =>
      log.error(s"Received unexpected message $x")
  }

}

