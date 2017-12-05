package csw.qa.framework

import akka.actor.Scheduler
import akka.typed.ActorRef
import akka.typed.scaladsl.ActorContext
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import csw.apps.containercmd.ContainerCmd
import csw.framework.scaladsl.{ComponentBehaviorFactory, ComponentHandlers}
import csw.messages.CommandResponseManagerMessage.AddOrUpdateCommand
import csw.messages._
import csw.messages.RunningMessage.DomainMessage
import csw.messages.ccs.commands.CommandResponse.{Accepted, Completed}
import csw.messages.ccs.commands.{CommandResponse, ControlCommand}
import csw.messages.framework.ComponentInfo
import csw.messages.location._
import csw.messages.models.PubSub.PublisherMessage
import csw.messages.params.states.CurrentState

import scala.concurrent.duration._
import csw.services.location.scaladsl.LocationService

import scala.async.Async._
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Success}
import csw.services.ccs.common.ActorRefExts.RichComponentActor
import csw.services.logging.scaladsl.LoggerFactory

// Base trait for Test Assembly domain messages
sealed trait TestAssemblyDomainMessage extends DomainMessage

// Add messages here...

private class TestAssemblyBehaviorFactory extends ComponentBehaviorFactory[TestAssemblyDomainMessage] {
  override def handlers(ctx: ActorContext[TopLevelActorMessage],
                        componentInfo: ComponentInfo,
                        commandResponseManager: ActorRef[CommandResponseManagerMessage],
                        pubSubRef: ActorRef[PublisherMessage[CurrentState]],
                        locationService: LocationService,
                        loggerFactory: LoggerFactory
                       ): ComponentHandlers[TestAssemblyDomainMessage] =
    new TestAssemblyHandlers(ctx, componentInfo, commandResponseManager, pubSubRef, locationService, loggerFactory)
}

private class TestAssemblyHandlers(ctx: ActorContext[TopLevelActorMessage],
                                   componentInfo: ComponentInfo,
                                   commandResponseManager: ActorRef[CommandResponseManagerMessage],
                                   pubSubRef: ActorRef[PublisherMessage[CurrentState]],
                                   locationService: LocationService,
                                   loggerFactory: LoggerFactory)
  extends ComponentHandlers[TestAssemblyDomainMessage](ctx, componentInfo, commandResponseManager,
    pubSubRef, locationService, loggerFactory) {

  private val log = loggerFactory.getLogger
  // Set when the location is received from the location service (below)
  private var testHcd: Option[ActorRef[ComponentMessage]] = None
  implicit val ec: ExecutionContextExecutor = ctx.executionContext

  override def initialize(): Future[Unit] = async {
    log.debug("Initialize called")
  }

  override def validateCommand(controlCommand: ControlCommand): CommandResponse = {
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
      hcd.submit(controlCommand).onComplete {
        case Success(resp) =>
          log.info(s"TestHcd responded with $resp")
          resp match {
            case Accepted(runId) =>
              assert(runId == controlCommand.runId)
              commandResponseManager ! AddOrUpdateCommand(runId, Completed(runId))
            case x =>
              log.error(s"Unexpected response from TestHcd: $x")
          }
        case Failure(ex) =>
          log.error("Failed to send command to TestHcd", ex = ex)
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

  override def onDomainMsg(testMessage: TestAssemblyDomainMessage): Unit = testMessage match {
    case x => log.debug(s"onDomainMsg called: $x")
  }

  override def onLocationTrackingEvent(trackingEvent: TrackingEvent): Unit = {
    log.debug(s"onLocationTrackingEvent called: $trackingEvent")
    trackingEvent match {
      case LocationUpdated(location) =>
        testHcd = Some(location.asInstanceOf[AkkaLocation].componentRef())
      case LocationRemoved(_) =>
        testHcd = None
    }
  }
}

// Start assembly from the command line using TestAssembly.conf resource file
object TestAssemblyApp extends App {
  val defaultConfig = ConfigFactory.load("TestAssembly.conf")
  ContainerCmd.start("TestAssembly", args, Some(defaultConfig))
}

