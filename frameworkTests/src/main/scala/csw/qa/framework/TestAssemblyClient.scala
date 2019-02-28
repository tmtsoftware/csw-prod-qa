package csw.qa.framework

import java.net.InetAddress

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.actor.typed
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.scaladsl.adapter._
import akka.util.Timeout
import csw.command.api.scaladsl.CommandService
import csw.command.client.CommandServiceFactory
import csw.event.api.scaladsl.EventService
import csw.event.client.EventServiceFactory
import csw.location.api.models.ComponentType.Assembly
import csw.location.api.models.Connection.AkkaConnection
import csw.location.api.models._
import csw.location.client.ActorSystemFactory
import csw.location.client.scaladsl.HttpLocationServiceFactory
import csw.logging.client.scaladsl.{GenericLoggerFactory, LoggingSystemFactory}
import csw.params.commands.{CommandName, Setup}
import csw.params.core.generics.{Key, KeyType}
import csw.params.core.models.{ObsId, Prefix}
import csw.params.events.{Event, EventKey, EventName, SystemEvent}

import scala.concurrent.duration._
import scala.util.{Failure, Success}

// A client to test locating and communicating with the Test assembly
object TestAssemblyClient extends App {

  implicit val system: ActorSystem = ActorSystemFactory.remote("TestAssemblyClient")
  import system.dispatcher

  implicit val mat: ActorMaterializer = ActorMaterializer()
  private val locationService = HttpLocationServiceFactory.makeLocalClient(system, mat)
  private val host = InetAddress.getLocalHost.getHostName
  LoggingSystemFactory.start("TestServiceClientApp", "0.1", host, system)
  implicit val timeout: Timeout = Timeout(3.seconds)
  private val log = GenericLoggerFactory.getLogger
  log.info("Starting TestAssemblyClient")

  // Key for events from assembly
  private val assemblyEventValueKey = TestAssemblyWorker.eventKey
  private val assemblyEventName = TestAssemblyWorker.eventName
  private val assemblyPrefix = Prefix("test.assembly")
  // Event that the HCD publishes (must match the names defined by the publisher (TestHcd))
  private val assemblyEventKey = EventKey(assemblyPrefix, assemblyEventName)

  private val obsId = ObsId("2023-Q22-4-33")
  private val encoderKey = KeyType.IntKey.make("encoder")
  private val filterKey = KeyType.StringKey.make("filter")
  private val prefix = Prefix("wfos.blue.filter")
  private val command = CommandName("myCommand")

  val connection = AkkaConnection(ComponentId("TestAssembly", Assembly))

  lazy val eventService: EventService =
    new EventServiceFactory().make(locationService)

  // Actor to receive Assembly events
  object EventHandler {
    def make(): Behavior[Event] = {
      log.info("Starting event handler")
      Behaviors.setup(ctx ⇒ new EventHandler(ctx))
    }
  }

  private class EventHandler(ctx: ActorContext[Event]) extends AbstractBehavior[Event] {
    override def onMessage(msg: Event): Behavior[Event] = {
      msg match {
        case e: SystemEvent =>
          e.get(assemblyEventValueKey)
            .foreach { p =>
              val eventValue = p.head
              log.info(s"Received event with value: $eventValue")
            }
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

  system.spawn(initialBehavior, "TestAssemblyClient")

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
          implicit val sys: typed.ActorSystem[Nothing] = ctx.system
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
    implicit val timeout: Timeout = Timeout(3.seconds)
    Setup(prefix, command, Some(obsId)).add(i1).add(i2)

  }

  private def interact(ctx: ActorContext[TrackingEvent], assembly: CommandService): Unit = {
    val setups = (1 to 10).toList.map(i => makeSetup(i, s"filter$i"))
    assembly.submitAll(setups).onComplete {
      case Success(responses) => println(s"Test Passed: Responses = $responses")
      case Failure(ex)        => println(s"Test Failed: $ex")
    }
  }
}
