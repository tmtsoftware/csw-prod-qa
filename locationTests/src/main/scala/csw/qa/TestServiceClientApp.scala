package csw.qa

import akka.actor.{Actor, ActorLogging, Props}
import akka.stream.scaladsl.Sink
import csw.qa.TestServiceClient.AllResolved
import csw.services.location.models.{Connection, Resolved, ResolvedAkkaLocation}
import csw.services.location.models.Connection.AkkaConnection
import csw.services.location.scaladsl.{ActorRuntime, LocationService, LocationServiceFactory}

import scala.concurrent.Future
import scala.util.{Failure, Success}

/**
  * A location service test client application that attempts to resolve one or more sets of
  * akka and http services.
  * If a command line arg is given, it should be the number of (akka, http) pairs of services to start (default: 1 of each).
  * The client and service applications can be run on the same or different hosts.
  */
object TestServiceClientApp extends App {
  private val actorRuntime = new ActorRuntime("TestAkkaServiceApp")
  val locationService = LocationServiceFactory.make(actorRuntime)

  import actorRuntime.actorSystem

  val numServices = args.headOption.map(_.toInt).getOrElse(1)
  sys.addShutdownHook(actorSystem.terminate())
  actorSystem.actorOf(TestServiceClient.props(actorRuntime, numServices, locationService))
}

object TestServiceClient {
  case class AllResolved(set: Set[Resolved])
  def props(actorRuntime: ActorRuntime, numServices: Int, locationService: LocationService): Props =
    Props(new TestServiceClient(actorRuntime, numServices, locationService))
}

/**
  * A test client actor that uses the location service to resolve services
  */
class TestServiceClient(actorRuntime: ActorRuntime, numServices: Int, locationService: LocationService) extends Actor with ActorLogging {
  import context.dispatcher
  import actorRuntime.mat
//  private val connections: Set[Connection] = (1 to numServices).toList.flatMap(i => List(TestAkkaService.connection(i), TestHttpService.connection(i))).toSet
  private val connections: Set[AkkaConnection] = (1 to numServices).toList.map(i => TestAkkaService.connection(i)).toSet
  log.info(s"TestServiceClient: connections = $connections")

  Future.sequence(connections.map(locationService.resolve)).onComplete {
    case Success(resolved) =>
      self ! AllResolved(resolved)
    case Failure(ex) =>
      log.error(s"Failed to resolve connections:", ex)
  }

  connections.foreach{ c =>
    locationService.track(c).to(Sink.actorRef(self, s"Completed connection: $c")).run()
  }

  override def receive: Receive = {
    case AllResolved(resolved) =>
      log.info(s"All resolved: Received services: ${resolved.map(_.connection.componentId.name).mkString(", ")}")
    case c: AkkaConnection =>
    case x =>
      log.error(s"Received unexpected message $x")
  }

}

