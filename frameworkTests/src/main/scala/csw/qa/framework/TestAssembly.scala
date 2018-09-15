package csw.qa.framework

import akka.actor.typed.Behavior
import akka.actor.Scheduler
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, MutableBehavior}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import csw.command.messages.TopLevelActorMessage
import csw.command.scaladsl.CommandService
import csw.event.api.scaladsl.EventPublisher
import csw.framework.deploy.containercmd.ContainerCmd
import csw.framework.models.CswContext
import csw.framework.scaladsl.{ComponentBehaviorFactory, ComponentHandlers}
import csw.location.api.models.{AkkaLocation, LocationRemoved, LocationUpdated, TrackingEvent}
import csw.logging.scaladsl.Logger
import csw.params.commands.CommandResponse.Error
import csw.params.commands.{CommandResponse, ControlCommand, Setup}
import csw.params.core.generics.{Key, KeyType}
import csw.params.core.models.{Id, Prefix}
import csw.params.events._

import scala.concurrent.duration._
import scala.async.Async._
import scala.concurrent.{ExecutionContextExecutor, Future}

private class TestAssemblyBehaviorFactory extends ComponentBehaviorFactory {
  override def handlers(ctx: ActorContext[TopLevelActorMessage],
                        cswCtx: CswContext): ComponentHandlers =
    new TestAssemblyHandlers(ctx, cswCtx)
}

object TestAssemblyHandlers {
  // Key for HCD events
  private val hcdEventValueKey: Key[Int] = KeyType.IntKey.make("hcdEventValue")
  private val hcdEventName = EventName("myHcdEvent")
  private val hcdPrefix = Prefix("test.hcd")

  // Dummy key for publishing events from assembly
  private val eventKey: Key[Int] = KeyType.IntKey.make("assemblyEventValue")
  private val eventName = EventName("myAssemblyEvent")

  // Actor to receive HCD events
  object EventHandler {
    def make(log: Logger,
             publisher: EventPublisher,
             baseEvent: SystemEvent): Behavior[Event] = {
      log.info("Starting event handler")
      Behaviors.setup(ctx ⇒ new EventHandler(ctx, log, publisher, baseEvent))
    }
  }

  class EventHandler(ctx: ActorContext[Event],
                     log: Logger,
                     publisher: EventPublisher,
                     baseEvent: SystemEvent)
      extends MutableBehavior[Event] {
    override def onMessage(msg: Event): Behavior[Event] = {
      msg match {
        case e: SystemEvent =>
          e.get(hcdEventValueKey)
            .foreach { p =>
              val eventValue = p.head
              log.info(s"Received event with value: $eventValue")
              // fire a new event from the assembly based on the one from the HCD
              val e = baseEvent
                .copy(eventId = Id(), eventTime = EventTime())
                .add(eventKey.set(eventValue))
              publisher.publish(e)
            }
          Behaviors.same
        case _ => throw new RuntimeException("Expected SystemEvent")
      }
    }
  }

}

private class TestAssemblyHandlers(ctx: ActorContext[TopLevelActorMessage],
                                   cswServices: CswContext)
    extends ComponentHandlers(ctx, cswServices) {

  import TestAssemblyHandlers._
  import cswServices._

  implicit val ec: ExecutionContextExecutor = ctx.executionContext

  private val log = loggerFactory.getLogger
  // Set when the location is received from the location service (below)
  private var testHcd: Option[CommandService] = None

  // Event that the HCD publishes (must match the names defined by the publisher (TestHcd))
  private val hcdEventKey = EventKey(hcdPrefix, hcdEventName)

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
    log.debug(s"onSubmit called: $controlCommand")
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
      cswServices.commandResponseManager.addSubCommand(controlCommand.runId, setup.runId)

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
    val subscriber = eventService.defaultSubscriber
    val publisher = eventService.defaultPublisher
    val baseEvent =
      SystemEvent(componentInfo.prefix, eventName).add(eventKey.set(0))
    val eventHandler =
      ctx.spawnAnonymous(EventHandler.make(log, publisher, baseEvent))
    subscriber.subscribeActorRef(Set(hcdEventKey), eventHandler)
  }

}

// Start assembly from the command line using TestAssembly.conf resource file
object TestAssemblyApp extends App {
  val defaultConfig = ConfigFactory.load("TestAssembly.conf")
  ContainerCmd.start("TestAssembly", args, Some(defaultConfig))
}
