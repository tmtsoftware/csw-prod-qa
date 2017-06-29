package csw.qa.location

import java.net.InetAddress

import akka.actor._
import akka.stream.ActorMaterializer
import csw.services.location.models.Connection.AkkaConnection
import csw.services.location.models.{AkkaRegistration, ComponentId, ComponentType}
import csw.services.location.scaladsl.{ActorSystemFactory, LocationService, LocationServiceFactory}
import csw.services.logging.appenders.{FileAppender, StdOutAppender}
import csw.services.logging.internal.LoggingSystem
import csw.services.logging.scaladsl.{ComponentLogger, GenericLogger}

import scala.concurrent.Await
import scala.concurrent.duration._

/**
  * Starts one or more akka services in order to test the location service.
  *
  * Type test-akka-service-app --help (or see below) for a description of the command line options.
  *
  * Each service will have a number appended to its name.
  * You should start the TestServiceClient with the same number, so that it
  * will try to find all the services.
  *
  * The client and service applications can be run on the same or different hosts.
  */
object TestAkkaServiceApp extends App {
  implicit val system = ActorSystemFactory.remote
  private val locationService = LocationServiceFactory.make()
  private val host = InetAddress.getLocalHost.getHostName
  private val loggingSystem = new LoggingSystem(
    name = "TestAkkaServiceApp",
    version = "0.1",
    host = host,
    system = system,
    appenderBuilders = Seq(StdOutAppender, FileAppender))

  implicit val mat = ActorMaterializer()

  case class Options(numServices: Int = 1, firstService: Int = 1,
                     autostop: Int = 0, delay: Int = 100,
                     logMessages: Boolean = false)

  // Parses the command line options
  private val parser = new scopt.OptionParser[Options]("test-akka-service-app") {
    head("test-akka-service-app", System.getProperty("CSW_VERSION"))

    opt[Int]("numServices") valueName "<count>" action { (x, c) =>
      c.copy(numServices = x)
    } text "the number of services to start (default: 1)"

    opt[Int]("firstService") valueName "<n>" action { (x, c) =>
      c.copy(firstService = x)
    } text "the service number to start with (default: 1)"

    opt[Int]("autostop") valueName "<count>" action { (x, c) =>
      c.copy(autostop = x)
    } text "the number of seconds before unregistering and shutdown (default: 0)"

    opt[Int]("delay") valueName "<ms>" action { (x, c) =>
      c.copy(delay = x)
    } text "the number of ms to wait before starting each service (default: 100)"

    opt[Unit]("logMessages") action { (_, c) =>
      c.copy(logMessages = true)
    } text "If given, log messages received from the client app (default: not logged)"

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
    for (i <- firstService until firstService + numServices) {
      Thread.sleep(delay) // Avoid timeouts?
      system.actorOf(TestAkkaService.props(i, options, locationService))
    }
  }
}

object TestAkkaService {
  // Creates the ith service
  def props(i: Int, options: TestAkkaServiceApp.Options, locationService: LocationService): Props =
    Props(new TestAkkaService(i, options, locationService))

  // Component ID of the ith service
  def componentId(i: Int) = ComponentId(s"TestAkkaService_$i", ComponentType.Assembly)

  // Connection for the ith service
  def connection(i: Int): AkkaConnection = AkkaConnection(componentId(i))

  // Message sent from client once location has been resolved
  case object ClientMessage

  // Message to unregister and quit
  case object Quit

}

object TestAkkaServiceLogger extends ComponentLogger("TestAkkaService")

/**
  * A dummy akka test service that registers with the location service
  */
class TestAkkaService(i: Int, options: TestAkkaServiceApp.Options, locationService: LocationService)
  extends Actor with TestAkkaServiceLogger.Actor {

  import context.dispatcher
  import options._

  // Register with the location service
  private val reg = Await.result(locationService.register(AkkaRegistration(TestAkkaService.connection(i), self)), 30.seconds)
  log.debug(s"Registered service $i as: ${reg.location.connection.name} with URI = ${reg.location.uri}")

  if (autostop != 0)
    context.system.scheduler.scheduleOnce(autostop.seconds, self, TestAkkaService.Quit)

  override def receive: Receive = {
    // This is the message that TestServiceClient sends when it discovers this service
    case TestAkkaService.ClientMessage =>
      if (logMessages)
        log.debug(s"Received scala client message from: ${sender()}")

    // This is the message that JTestServiceClient sends when it discovers this service
    case m: JTestAkkaService.ClientMessage =>
      if (logMessages)
        log.debug(s"Received java client message from: ${sender()}")

    case TestAkkaService.Quit =>
      log.info(s"Actor $i is shutting down after $autostop seconds")
      Await.result(reg.unregister(), 10.seconds)
      context.stop(self)

    case x =>
      log.error(s"Received unexpected message $x")
  }
}
