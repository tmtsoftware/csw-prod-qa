package csw.qa.framework

import java.net.InetAddress

import akka.actor.{ActorRefFactory, ActorSystem, Scheduler}
import akka.stream.ActorMaterializer
import akka.actor.typed
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, MutableBehavior}
import csw.services.location.scaladsl.LocationServiceFactory
import csw.services.logging.scaladsl.{GenericLoggerFactory, Logger, LoggingSystemFactory}
import akka.actor.typed.scaladsl.adapter._
import akka.util.Timeout
import csw.messages.commands.{CommandName, Setup}
import csw.messages.events._
import csw.messages.location.ComponentType.Assembly
import csw.messages.location.Connection.AkkaConnection
import csw.messages.location._
import csw.messages.params.generics.{Key, KeyType}
import csw.messages.params.models.{Id, ObsId, Prefix}
import csw.messages.params.models.Units.degree
import csw.qa.framework.TestAssemblyHandlers.{EventHandler, eventKey, eventName}
import csw.services.command.scaladsl.CommandService
import csw.services.event.api.scaladsl.{EventPublisher, EventService}
import csw.services.event.internal.redis.RedisEventServiceFactory
import csw.services.location.commons.ClusterAwareSettings

import scala.async.Async.{async, await}
import scala.concurrent.duration._
import scala.util.{Failure, Success}

// A client to test locating and communicating with the Test assembly
object TestAssemblyClient extends App {

  //  private val system = ActorSystemFactory.remote
  implicit val system: ActorSystem = ClusterAwareSettings.system
  import system.dispatcher

//  implicit def actorRefFactory: ActorRefFactory = system

  private val locationService = LocationServiceFactory.withSystem(system)
  private val host = InetAddress.getLocalHost.getHostName
  LoggingSystemFactory.start("TestServiceClientApp", "0.1", host, system)
  implicit val mat: ActorMaterializer = ActorMaterializer()
  private val log = GenericLoggerFactory.getLogger
  log.info("Starting TestAssemblyClient")

  // Key for events from assembly
  private val assemblyEventValueKey: Key[Int]    = KeyType.IntKey.make("assemblyEventValue")
  private val assemblyEventName = EventName("myAssemblyEvent")
  private val assemblyPrefix = Prefix("test.assembly")
  // Event that the HCD publishes (must match the names defined by the publisher (TestHcd))
  private val assemblyEventKey = EventKey(assemblyPrefix, assemblyEventName)

  // XXX TODO FIXME: Should not need to access internal API!
  lazy val eventService: EventService =
    new RedisEventServiceFactory().make(locationService)

  // Actor to receive Assembly events
  object EventHandler {
    def make(): Behavior[Event] = {
      log.info("Starting event handler")
      Behaviors.setup(ctx ⇒ new EventHandler(ctx))
    }
  }

  class EventHandler(ctx: ActorContext[Event]) extends MutableBehavior[Event] {
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


  def startSubscribingToEvents(ctx: ActorContext[TrackingEvent]) = async {
    val subscriber = await(eventService.defaultSubscriber)
    val eventHandler = ctx.spawnAnonymous(EventHandler.make())
    subscriber.subscribeActorRef(Set(assemblyEventKey), eventHandler)
  }

  system.spawn(initialBehavior, "TestAssemblyClient")

  def initialBehavior: Behavior[TrackingEvent] =
    Behaviors.setup { ctx =>
      val connection = AkkaConnection(ComponentId("TestAssembly", Assembly))
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
          interact(ctx, new CommandService(loc.asInstanceOf[AkkaLocation]))
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

  private def interact(ctx: ActorContext[TrackingEvent],
                       assembly: CommandService): Unit = {
    val k1 = KeyType.IntKey.make("encoder")
    val k2 = KeyType.StringKey.make("filter")
    val i1 = k1.set(22, 33, 44)
    val i2 = k2.set("a", "b", "c").withUnits(degree)
    val setup = Setup(Prefix("wfos.blue.filter"),
                      CommandName("filter"),
                      Some(ObsId("2023-Q22-4-33"))).add(i1).add(i2)
    log.info(s"Sending setup to assembly: $setup")
    implicit val timeout: Timeout = Timeout(3.seconds)
    implicit val scheduler: Scheduler = ctx.system.scheduler
    assembly.submitAndSubscribe(setup).onComplete {
      case Success(resp) =>
        log.info(s"Assembly responded with $resp")
      case Failure(ex) =>
        log.error("Failed to send command to TestAssembly", ex = ex)
    }
  }
}
