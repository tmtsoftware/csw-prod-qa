package csw.qa.location

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.stream.{ActorMaterializer, Materializer}
import akka.stream.scaladsl.Sink
import csw.services.location.models.Connection.AkkaConnection
import csw.services.location.models.{AkkaLocation, Location, LocationRemoved, LocationUpdated}
import csw.services.location.scaladsl.{LocationService, LocationServiceFactory}

import scala.concurrent.Await
import scala.concurrent.duration._

/**
  * A location service test client application that attempts to resolve one or more sets of
  * akka services.
  * If a command line arg is given, it should be the number of services to start (default: 1 of each).
  * The client and service applications can be run on the same or different hosts.
  */
object TestServiceClientApp extends App {
  private val locationService = LocationServiceFactory.make()
  implicit val system = ActorSystem()
  implicit val mat = ActorMaterializer()

  val numServices = args.headOption.map(_.toInt).getOrElse(1)
  system.actorOf(TestServiceClient.props(numServices, locationService))

//  sys.addShutdownHook(shutdown())
//
//  def shutdown(): Unit = {
//    val timeout = 5.seconds
//    Await.ready(locationService.shutdown(), timeout)
//    Await.ready(system.terminate(), timeout)
//  }

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
//  import context.dispatcher

  private val connections: Set[AkkaConnection] = (1 to numServices).toList.map(i => TestAkkaService.connection(i)).toSet
  log.info(s"TestServiceClient: looking up connections = $connections")

  // XXX add class for this, suggest reuse!
  // Test calling track method for each connection and forward location messages to actor
  connections.foreach(locationService.track(_).to(Sink.actorRef(self, AllDone)).run())

  override def receive: Receive = {
    case loc: AkkaLocation =>
      log.info(s"Received $loc")
      loc.actorRef ! TestAkkaService.ClientMessage

    case loc: Location =>
      log.info(s"Received $loc")

    case LocationUpdated(loc) =>
      log.info(s"Location updated $loc")

    case LocationRemoved(conn) =>
      log.info(s"Location removed $conn")

    case x =>
      log.error(s"Received unexpected message $x")
  }

}

