package csw.qa.location

import akka.actor._
import akka.stream.ActorMaterializer
import csw.services.location.models.Connection.AkkaConnection
import csw.services.location.models.{AkkaRegistration, ComponentId, ComponentType}
import csw.services.location.scaladsl.{ActorSystemFactory, LocationService, LocationServiceFactory}
import csw.services.logging.scaladsl.{GenericLogger, LoggingSystemFactory}

import scala.concurrent.Await
import scala.concurrent.duration._

/**
  * Starts one or more akka services in order to test the location service.
  * If a command line arg is given, it should be the number of services to start (default: 1).
  * An additional command line arg indicates the service number to start with (default: 1)
  * Each service will have a number appended to its name.
  * You should start the TestServiceClient with the same number, so that it
  * will try to find all the services.
  * The client and service applications can be run on the same or different hosts.
  */
object TestAkkaServiceApp extends App {
  private val loggingSystem = LoggingSystemFactory.start()
  private val locationService = LocationServiceFactory.make()
  implicit val system = ActorSystemFactory.remote
  implicit val mat = ActorMaterializer()

  // Starts the given number of akka services (or 1)
  val numServices = args.headOption.map(_.toInt).getOrElse(1)
  val firstService = args.tail.headOption.map(_.toInt).getOrElse(1)
  for (i <- firstService until firstService + numServices) {
    system.actorOf(TestAkkaService.props(i, locationService))
    Thread.sleep(10) // Avoid timeouts?
  }

}

object TestAkkaService {
  // Creates the ith service
  def props(i: Int, locationService: LocationService): Props = Props(new TestAkkaService(i, locationService))

  // Component ID of the ith service
  def componentId(i: Int) = ComponentId(s"TestAkkaService_$i", ComponentType.Assembly)

  // Connection for the ith service
  def connection(i: Int): AkkaConnection = AkkaConnection(componentId(i))

  // Message sent from client once location has been resolved
  case object ClientMessage

}

/**
  * A dummy akka test service that registers with the location service
  */
class TestAkkaService(i: Int, locationService: LocationService) extends Actor with GenericLogger.Simple {

  // Register with the location service
  //  Thread.sleep(3000) // Delaying here to test handling of duplicate registrations
  private val reg = Await.result(locationService.register(AkkaRegistration(TestAkkaService.connection(i), self)), 30.seconds)
  log.info(s"Registered service $i as: ${reg.location.connection.name}")

  override def receive: Receive = {
    // This is the message that TestServiceClient sends when it discovers this service
    case TestAkkaService.ClientMessage =>
      log.info(s"Received scala client message from: ${sender()}")

    // This is the message that JTestServiceClient sends when it discovers this service
    case m: JTestAkkaService.ClientMessage =>
      log.info(s"Received java client message from: ${sender()}")

    case x =>
      log.error(s"Received unexpected message $x")
  }
}
