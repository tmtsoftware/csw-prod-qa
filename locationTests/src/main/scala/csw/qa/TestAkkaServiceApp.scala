package csw.qa

import akka.actor._
import csw.services.location.models.Connection.AkkaConnection
import csw.services.location.models.{AkkaRegistration, ComponentId, ComponentType}
import csw.services.location.scaladsl.{ActorRuntime, LocationService, LocationServiceFactory}

/**
 * Starts one or more akka services in order to test the location service.
 * If a command line arg is given, it should be the number of services to start (default: 1).
 * Each service will have a number appended to its name.
 * You should start the TestServiceClient with the same number, so that it
 * will try to find all the services.
 * The client and service applications can be run on the same or different hosts.
 */
object TestAkkaServiceApp extends App {
  private val actorRuntime = new ActorRuntime("TestAkkaServiceApp")
  val locationService = LocationServiceFactory.make(actorRuntime)
  import actorRuntime.actorSystem

  val numServices = args.headOption.map(_.toInt).getOrElse(1)
  sys.addShutdownHook(actorSystem.terminate())
  for (i <- 1 to numServices) {
    actorSystem.actorOf(TestAkkaService.props(i, locationService))
  }
}

object TestAkkaService {
  def props(i: Int, locationService: LocationService): Props = Props(new TestAkkaService(i, locationService))
  def componentId(i: Int) = ComponentId(s"TestAkkaService_$i", ComponentType.Assembly)
  def connection(i: Int): AkkaConnection = AkkaConnection(componentId(i))
}

/**
 * A dummy akka test service that registers with the location service
 */
class TestAkkaService(i: Int, locationService: LocationService) extends Actor with ActorLogging {
  import TestAkkaService._

  locationService.register(AkkaRegistration(connection(i), self, "test.akka.prefix"))

  override def receive: Receive = {
    case x =>
      log.error(s"Received unexpected message $x")
  }
}
