package csw.qa.framework

import java.net.InetAddress

import akka.actor
import akka.actor.typed.{ActorSystem, Behavior, SpawnProtocol}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.scaladsl.adapter._
import akka.util.Timeout
import csw.command.api.scaladsl.CommandService
import csw.command.client.CommandServiceFactory
import csw.event.api.scaladsl.EventService
import csw.event.client.EventServiceFactory
import csw.location.client.scaladsl.HttpLocationServiceFactory
import csw.location.models.{AkkaLocation, ComponentId, LocationRemoved, LocationUpdated, TrackingEvent}
import csw.location.models.ComponentType.Assembly
import csw.location.models.Connection.AkkaConnection
import csw.logging.client.scaladsl.{GenericLoggerFactory, LoggingSystemFactory}
import csw.params.commands.{CommandName, Setup}
import csw.params.core.generics.KeyType
import csw.params.core.models.{ObsId, Prefix}
import csw.params.events.{Event, EventKey, SystemEvent}
import csw.logging.client.commons.AkkaTypedExtension.UserActorFactory
import csw.params.core.models.Subsystem.CSW

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._
import scala.util.{Failure, Success}

//noinspection DuplicatedCode,SameParameterValue
// A client to test locating and communicating with the Test assembly
object TestAssemblyClient extends App {

  private val host = InetAddress.getLocalHost.getHostName
  implicit val typedSystem: ActorSystem[SpawnProtocol.Command] = ActorSystem(SpawnProtocol(), "TestAssemblyClient")
  implicit lazy val untypedSystem: actor.ActorSystem        = typedSystem.toClassic
  implicit lazy val ec: ExecutionContextExecutor            = untypedSystem.dispatcher

  LoggingSystemFactory.start("TestAssemblyClient", "0.1", host, typedSystem)
  private val log = GenericLoggerFactory.getLogger
  log.info("Starting TestAssemblyClient")

  val locationService = HttpLocationServiceFactory.makeLocalClient(typedSystem)

  implicit val timeout: Timeout = Timeout(10.seconds)

  // Key for events from assembly
//  private val assemblyEventValueKey = TestAssemblyWorker.eventKey1
//  private val assemblyEventValueKey2 = TestAssemblyWorker.eventKey2
  private val assemblyEventName = TestAssemblyWorker.eventName
  private val assemblyPrefix = Prefix("csw.assembly")
  // Event that the HCD publishes (must match the names defined by the publisher (TestHcd))
  private val assemblyEventKey = EventKey(assemblyPrefix, assemblyEventName)

  private val obsId = ObsId("2023-Q22-4-33")
  private val encoderKey = KeyType.IntKey.make("encoder")
  private val filterKey = KeyType.StringKey.make("filter")
  private val prefix = Prefix("wfos.blue.filter")
  private val command = CommandName("myCommand")

  val connection = AkkaConnection(ComponentId(Prefix(CSW, "TestAssembly"), Assembly))

  lazy val eventService: EventService =
    new EventServiceFactory().make(locationService)

  // Actor to receive Assembly events
  object EventHandler {
    def make(): Behavior[Event] = {
      log.info("Starting event handler")
      Behaviors.setup(ctx => new EventHandler(ctx))
    }
  }

  private class EventHandler(ctx: ActorContext[Event]) extends AbstractBehavior[Event](ctx) {
    override def onMessage(msg: Event): Behavior[Event] = {
      msg match {
        case e: SystemEvent =>
          log.info(s"Received event: $e")
          e.paramSet.foreach { p =>
            log.info(s"Received parameter: $p")

          }
//          e.get(assemblyEventValueKey)
//            .foreach { p =>
//              val eventValue = p.head
//              log.info(s"Received event with value: $eventValue")
//            }
//          e.get(assemblyEventValueKey2)
//            .foreach { p =>
//              val eventValue = p.head
//              log.info(s"Received event with struct value: ${eventValue.get(assemblyEventValueKey).head.head}")
//            }
          Behaviors.same
        case _ => throw new RuntimeException("Expected SystemEvent")
      }
    }
  }

  def startSubscribingToEvents(ctx: ActorContext[TrackingEvent]): Unit = {
    val subscriber = eventService.defaultSubscriber
    val eventHandler = ctx.spawnAnonymous(EventHandler.make())
    subscriber.subscribeActorRef(Set(assemblyEventKey), eventHandler)
  }

  typedSystem.spawn(initialBehavior, "TestAssemblyClient")

  def initialBehavior: Behavior[TrackingEvent] =
    Behaviors.setup { ctx =>
      locationService.subscribe(connection, { loc =>
        ctx.self ! loc
      })
      startSubscribingToEvents(ctx)
      subscriberBehavior
    }

  def subscriberBehavior: Behavior[TrackingEvent] = {
    Behaviors.receive[TrackingEvent] { (ctx, msg) =>
      msg match {
        case LocationUpdated(loc) =>
          log.info(s"LocationUpdated: $loc")
          interact(ctx, CommandServiceFactory.make(loc.asInstanceOf[AkkaLocation]))
        case LocationRemoved(loc) =>
          log.info(s"LocationRemoved: $loc")
      }
      Behaviors.same
    } receiveSignal {
      case (ctx, x) =>
        log.info(s"${ctx.self} received signal $x")
        Behaviors.stopped
    }
  }

  private def makeSetup(encoder: Int, filter: String): Setup = {
    val i1 = encoderKey.set(encoder)
    val i2 = filterKey.set(filter)
    Setup(prefix, command, Some(obsId)).add(i1).add(i2)
  }

  private def interact(ctx: ActorContext[TrackingEvent], assembly: CommandService): Unit = {
    assembly.submit(makeSetup(0, s"filter0")).onComplete {
      case Success(responses) => log.info(s"Single Submit Test Passed: Responses = $responses")
      case Failure(ex)        => log.info(s"Single Submit Test Failed: $ex")
    }
//    val setups = (1 to 10).toList.map(i => makeSetup(i, s"filter$i"))
//    assembly.submitAll(setups).onComplete {
//      case Success(responses) => log.info(s"Submit All Test Passed: Responses = $responses")
//      case Failure(ex)        => log.info(s"Submit All Test Failed: $ex")
//    }
  }
}
