package csw.qa.framework

import java.net.InetAddress

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.actor.typed
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, MutableBehavior}
import csw.services.location.scaladsl.LocationServiceFactory
import csw.services.logging.scaladsl.{
  GenericLoggerFactory,
  LoggingSystemFactory
}
import akka.actor.typed.scaladsl.adapter._
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.util.Timeout
import csw.messages.commands.{CommandName, CommandResponse, Setup}
import csw.messages.events._
import csw.messages.location.ComponentType.Assembly
import csw.messages.location.Connection.AkkaConnection
import csw.messages.location._
import csw.messages.params.generics.{Key, KeyType}
import csw.messages.params.models.{ObsId, Prefix}
import csw.services.command.scaladsl.CommandService
import csw.services.event.EventServiceFactory
import csw.services.event.api.scaladsl.EventService
import csw.services.location.commons.ClusterAwareSettings

import scala.async.Async.{async, await}
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

// A client to test locating and communicating with the Test assembly
object TestAssemblyClient extends App {

  implicit val system: ActorSystem = ClusterAwareSettings.system

  import system.dispatcher

//  implicit val scheduler: Scheduler = system.scheduler

  private val locationService = LocationServiceFactory.withSystem(system)
  private val host = InetAddress.getLocalHost.getHostName
  LoggingSystemFactory.start("TestServiceClientApp", "0.1", host, system)
  implicit val mat: ActorMaterializer = ActorMaterializer()
  implicit val timeout: Timeout = Timeout(3.seconds)
  private val log = GenericLoggerFactory.getLogger
  log.info("Starting TestAssemblyClient")

  // Key for events from assembly
  private val assemblyEventValueKey: Key[Int] =
    KeyType.IntKey.make("assemblyEventValue")
  private val assemblyEventName = EventName("myAssemblyEvent")
  private val assemblyPrefix = Prefix("test.assembly")
  // Event that the HCD publishes (must match the names defined by the publisher (TestHcd))
  private val assemblyEventKey = EventKey(assemblyPrefix, assemblyEventName)

  private val obsId = ObsId("2023-Q22-4-33")
  private val encoderKey = KeyType.IntKey.make("encoder")
  private val filterKey = KeyType.StringKey.make("filter")
  private val prefix = Prefix("wfos.blue.filter")
  private val command = CommandName("myCommand")

  lazy val eventService: EventService =
    new EventServiceFactory().make(locationService)

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

  private def makeSetup(encoder: Int, filter: String): Setup = {
    val i1 = encoderKey.set(encoder)
    val i2 = filterKey.set(filter)
    implicit val timeout: Timeout = Timeout(3.seconds)
    Setup(prefix, command, Some(obsId)).add(i1).add(i2)

  }

  private def interact(ctx: ActorContext[TrackingEvent],
                       assembly: CommandService): Unit = {
    val setups = (1 to 10).toList.map(i => makeSetup(i, s"filter$i"))
    submitAll(setups, assembly).onComplete {
      case Success(responses) => println(s"Test Passed: Responses = $responses")
      case Failure(ex)        => println("Test Failed: $ex")
    }
  }

  /**
    * Submits the given setups, one after the other, and returns a future list of command responses.
    * @param setups the setups to submit
    * @param assembly the assembly to submit the setups to
    * @return future list of responses
    */
  private def submitAll(
      setups: List[Setup],
      assembly: CommandService): Future[List[CommandResponse]] = {
    Source(setups)
      .mapAsync(1)(assembly.submitAndSubscribe)
      .toMat(Sink.seq)(Keep.right)
      .run()
      .map(_.toList)
  }
}
