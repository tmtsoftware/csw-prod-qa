package csw.qa.framework

import akka.actor.typed.Behavior
import akka.actor.Scheduler
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, MutableBehavior}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import csw.framework.CurrentStatePublisher
import csw.framework.deploy.containercmd.ContainerCmd
import csw.framework.scaladsl.{ComponentBehaviorFactory, ComponentHandlers}
import csw.messages.TopLevelActorMessage
import csw.messages.commands.CommandResponse.Error
import csw.messages.commands.{CommandResponse, ControlCommand, Setup}
import csw.messages.events.{Event, EventKey, EventName, SystemEvent}
import csw.messages.framework.ComponentInfo
import csw.messages.location._
import csw.messages.params.generics.{Key, KeyType}
import csw.messages.params.models.Prefix
import csw.services.command.CommandResponseManager
import csw.services.command.scaladsl.CommandService
import csw.services.event.api.scaladsl.EventService

import scala.concurrent.duration._
import csw.services.location.scaladsl.LocationService

import scala.async.Async._
import scala.concurrent.{ExecutionContextExecutor, Future}
import csw.services.logging.scaladsl.{Logger, LoggerFactory}

private class TestAssemblyBehaviorFactory extends ComponentBehaviorFactory {
  override def handlers(ctx: ActorContext[TopLevelActorMessage],
                        componentInfo: ComponentInfo,
                        commandResponseManager: CommandResponseManager,
                        currentStatePublisher: CurrentStatePublisher,
                        locationService: LocationService,
                        eventService: EventService,
                        loggerFactory: LoggerFactory): ComponentHandlers =
    new TestAssemblyHandlers(ctx,
                             componentInfo,
                             commandResponseManager,
                             currentStatePublisher,
                             locationService,
                             eventService,
                             loggerFactory)
}

object TestAssemblyHandlers {
  // Key for HCD event value
  private val hcdEventKey: Key[Int]    = KeyType.IntKey.make("eventValue")
  private val hcdEventName = EventName("myEvent")
  private val hcdPrefix = Prefix("test.hcd")

  // Actor to receive HCD events
  object EventHandler {
    def make(log: Logger): Behavior[Event] = {
      log.info("Starting event handler")
      Behaviors.setup(ctx ⇒ new EventHandler(ctx, log))
    }
  }

  class EventHandler(ctx: ActorContext[Event], log: Logger) extends MutableBehavior[Event] {
    override def onMessage(msg: Event): Behavior[Event] = {
      msg match {
        case e: SystemEvent =>
          e.get(hcdEventKey)
            .foreach(p => log.info(s"Received event with value: ${p.head}"))
          Behaviors.same
        case _ => throw new RuntimeException("Expected SystemEvent")
      }
    }
  }

}

private class TestAssemblyHandlers(
    ctx: ActorContext[TopLevelActorMessage],
    componentInfo: ComponentInfo,
    commandResponseManager: CommandResponseManager,
    currentStatePublisher: CurrentStatePublisher,
    locationService: LocationService,
    eventService: EventService,
    loggerFactory: LoggerFactory)
    extends ComponentHandlers(ctx,
                              componentInfo,
                              commandResponseManager,
                              currentStatePublisher,
                              locationService,
                              eventService,
                              loggerFactory) {

  import TestAssemblyHandlers._

  implicit val ec: ExecutionContextExecutor = ctx.executionContext

  private val log = loggerFactory.getLogger
  // Set when the location is received from the location service (below)
  private var testHcd: Option[CommandService] = None

  // Event that the HCD publishes (must match the names defined by the publisher (TestHcd))
  private val hcdEventKey = EventKey(hcdPrefix, hcdEventName)
  private val eventHandler = ctx.spawnAnonymous(EventHandler.make(log))

  override def initialize(): Future[Unit] = async {
    log.debug("Initialize called")
    startSubscribingToEvents()
  }

  override def validateCommand(
      controlCommand: ControlCommand): CommandResponse = {
    CommandResponse.Accepted(controlCommand.runId)
  }

  override def onSubmit(controlCommand: ControlCommand): Unit = {
    implicit val timeout: Timeout = Timeout(3.seconds)
    log.debug("onSubmit called")
    forwardCommandToHcd(controlCommand)
  }

  // For testing, forward command to HCD and complete this command when it completes
  private def forwardCommandToHcd(controlCommand: ControlCommand): Unit = {
    implicit val scheduler: Scheduler = ctx.system.scheduler
    implicit val timeout: Timeout = Timeout(3.seconds)
    testHcd.foreach { hcd =>
      val setup = Setup(controlCommand.source,
                        controlCommand.commandName,
                        controlCommand.maybeObsId,
                        controlCommand.paramSet)
      commandResponseManager.addSubCommand(controlCommand.runId, setup.runId)

      val f = for {
        response <- hcd.submitAndSubscribe(setup)
      } yield {
        log.info(s"response = $response")
        commandResponseManager.updateSubCommand(setup.runId, response)
      }
      f.recover {
        case ex =>
          val cmdStatus = Error(setup.runId, ex.toString)
          commandResponseManager.updateSubCommand(setup.runId, cmdStatus)
      }
    }
  }

  override def onOneway(controlCommand: ControlCommand): Unit = {
    log.debug("onOneway called")
  }

  override def onShutdown(): Future[Unit] = async {
    log.debug("onShutdown called")
  }

  override def onGoOffline(): Unit = log.debug("onGoOffline called")

  override def onGoOnline(): Unit = log.debug("onGoOnline called")

  override def onLocationTrackingEvent(trackingEvent: TrackingEvent): Unit = {
    log.debug(s"onLocationTrackingEvent called: $trackingEvent")
    trackingEvent match {
      case LocationUpdated(location) =>
        testHcd = Some(
          new CommandService(location.asInstanceOf[AkkaLocation])(ctx.system))
      case LocationRemoved(_) =>
        testHcd = None
    }
  }

  private def startSubscribingToEvents() = async {
    val subscriber = await(eventService.defaultSubscriber)
    subscriber.subscribeActorRef(Set(hcdEventKey), eventHandler)
  }

}

// Start assembly from the command line using TestAssembly.conf resource file
object TestAssemblyApp extends App {
  val defaultConfig = ConfigFactory.load("TestAssembly.conf")
  ContainerCmd.start("TestAssembly", args, Some(defaultConfig))
}
