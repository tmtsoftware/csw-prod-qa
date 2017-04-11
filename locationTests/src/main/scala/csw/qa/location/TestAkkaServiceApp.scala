package csw.qa.location

import akka.actor._
import akka.stream.ActorMaterializer
import csw.services.location.models.Connection.AkkaConnection
import csw.services.location.models.{AkkaRegistration, ComponentId, ComponentType}
import csw.services.location.scaladsl.{ActorSystemFactory, LocationService, LocationServiceFactory}

/**
 * Starts one or more akka services in order to test the location service.
 * If a command line arg is given, it should be the number of services to start (default: 1).
 * Each service will have a number appended to its name.
 * You should start the TestServiceClient with the same number, so that it
 * will try to find all the services.
 * The client and service applications can be run on the same or different hosts.
 */
object TestAkkaServiceApp extends App {
  private val locationService = LocationServiceFactory.make()
  implicit val system = ActorSystemFactory.remote
  implicit val mat = ActorMaterializer()

  val numServices = args.headOption.map(_.toInt).getOrElse(1)
  for (i <- 1 to numServices) {
    system.actorOf(TestAkkaService.props(i, locationService))
  }

}

object TestAkkaService {
  def props(i: Int, locationService: LocationService): Props = Props(new TestAkkaService(i, locationService))
  def componentId(i: Int) = ComponentId(s"TestAkkaService_$i", ComponentType.Assembly)
  def connection(i: Int): AkkaConnection = AkkaConnection(componentId(i))

  // Message sent from client once location has been resolved
  case object ClientMessage
}

/**
 * A dummy akka test service that registers with the location service
 */
class TestAkkaService(i: Int, locationService: LocationService) extends Actor with ActorLogging {

  println(s"In actor $i")
  locationService.register(AkkaRegistration(TestAkkaService.connection(i), self))

  override def receive: Receive = {
    case TestAkkaService.ClientMessage =>
      log.info(s"Received scala client message from: ${sender()}")
    case m: JTestAkkaService.ClientMessage =>
      log.info(s"Received java client message from: ${sender()}")
    case x =>
      log.error(s"Received unexpected message $x")
  }
}
