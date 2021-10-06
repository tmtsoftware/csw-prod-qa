package csw.qa.framework

import java.net.InetAddress
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.util.Timeout
import csw.command.api.scaladsl.CommandService
import csw.command.client.CommandServiceFactory
import csw.location.api.models.{AkkaLocation, ComponentId, LocationRemoved, LocationUpdated, TrackingEvent}
import csw.location.api.models.ComponentType.Assembly
import csw.location.api.models.Connection.AkkaConnection
import csw.location.client.ActorSystemFactory
import csw.location.client.scaladsl.HttpLocationServiceFactory
import csw.logging.client.scaladsl.{GenericLoggerFactory, LoggingSystemFactory}
import csw.params.commands.{CommandName, Setup}
import csw.params.core.generics.KeyType
import csw.params.core.models.ObsId
import csw.logging.client.commons.AkkaTypedExtension.UserActorFactory
import csw.prefix.models.Prefix
import csw.prefix.models.Subsystem.CSW

import scala.concurrent.duration._
import scala.util.{Failure, Success}

//noinspection DuplicatedCode,SameParameterValue
// A client to test locating and communicating with the Test assembly
object TestAssemblyClient2 extends App {

  private val host = InetAddress.getLocalHost.getHostName
  val typedSystem: ActorSystem[SpawnProtocol.Command] = ActorSystemFactory.remote(SpawnProtocol(), "TestAssemblyClientSystem")
  import typedSystem.executionContext

  LoggingSystemFactory.start("TestAssemblyClient", "0.1", host, typedSystem)
  private val log = GenericLoggerFactory.getLogger
  log.info("Starting TestAssemblyClient")

  val locationService = HttpLocationServiceFactory.makeLocalClient(typedSystem)

  implicit val timeout: Timeout = Timeout(10.seconds)

  private val obsId = ObsId("2020A-001-123")
  private val encoderKey = KeyType.IntKey.make("encoder")
  private val filterKey = KeyType.StringKey.make("filter")
  private val prefix = Prefix("WFOS.blue.filter")
  private val command = CommandName("myCommand")

  val connection = AkkaConnection(ComponentId(Prefix(CSW, "testassembly"), Assembly))

  typedSystem.spawn(initialBehavior, "TestAssemblyClient")

  def initialBehavior: Behavior[TrackingEvent] =
    Behaviors.setup { ctx =>
      locationService.subscribe(connection, { loc =>
        ctx.self ! loc
      })
      subscriberBehavior()
    }

  def subscriberBehavior(): Behavior[TrackingEvent] = {
    Behaviors.receive[TrackingEvent] { (ctx, msg) =>
      log.debug(s"subscriberBehavior received message :[$msg]")
      msg match {
        case LocationUpdated(loc) if loc.connection == connection =>
          log.info(s"LocationUpdated: $loc")
          interact(ctx, CommandServiceFactory.make(loc.asInstanceOf[AkkaLocation])(typedSystem))
        case LocationRemoved(loc) =>
          log.info(s"LocationRemoved: $loc")
        case x =>
      }
      Behaviors.same
    }
  }

  private def makeSetup(encoder: Int, filter: String): Setup = {
    val i1 = encoderKey.set(encoder)
    val i2 = filterKey.set(filter)
    Setup(prefix, command, Some(obsId)).add(i1).add(i2)
  }

  private def interact(ctx: ActorContext[TrackingEvent], assembly: CommandService): Unit = {
    log.info(s"Sending filter0 setup to assembly")
    assembly.submitAndWait(makeSetup(0, s"filter0")).onComplete {
      case Success(response) => log.info(s"Single Submit Test Passed: Responses = $response")
      case Failure(ex)        => log.info(s"Single Submit Test Failed: $ex")
    }
  }
}
