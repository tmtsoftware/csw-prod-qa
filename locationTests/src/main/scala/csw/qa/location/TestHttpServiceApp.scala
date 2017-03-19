package csw.qa.location

import akka.actor._
import csw.services.location.models.Connection.HttpConnection
import csw.services.location.models.{ComponentId, ComponentType, HttpRegistration}
import csw.services.location.scaladsl.{ActorRuntime, LocationService, LocationServiceFactory}

/**
 * Starts one or more (dummy) http services in order to test the location service.
 * If a command line arg is given, it should be the number of services to start (default: 1).
 * Each service will have a number appended to its name.
 * You should start the TestServiceClient with the same number, so that it
 * will try to find all the services.
 * The client and service applications can be run on the same or different hosts.
 */
object TestHttpServiceApp extends App {
  private val actorRuntime = new ActorRuntime("TestHttpServiceApp")
  val locationService = LocationServiceFactory.make(actorRuntime)
  import actorRuntime.actorSystem

  val numServices = args.headOption.map(_.toInt).getOrElse(1)
  sys.addShutdownHook(actorSystem.terminate())
  for (i <- 1 to numServices) {
    actorSystem.actorOf(TestHttpService.props(i, locationService))
  }
}

object TestHttpService {
  def props(i: Int, locationService: LocationService): Props = Props(new TestHttpService(i, locationService))
  def componentId(i: Int) = ComponentId(s"TestHttpService_$i", ComponentType.Assembly)
  def connection(i: Int): HttpConnection = HttpConnection(componentId(i))
}

/**
  * A dummy akka test service that registers with the location service
  */
class TestHttpService(i: Int, locationService: LocationService) extends Actor with ActorLogging {
  import TestHttpService._

  private val port = 9000 + i // Dummy value for testing: Normally should be the actually port the HTTP server is running on...
  locationService.register(HttpRegistration(connection(i), port, "test.akka.prefix"))

  override def receive: Receive = {
    case x =>
      log.error(s"Received unexpected message $x")
  }
}

