package csw.qa.framework

import akka.actor.Scheduler
import akka.actor.typed.scaladsl.ActorContext
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import csw.framework.deploy.containercmd.ContainerCmd
import csw.framework.scaladsl.{ComponentBehaviorFactory, ComponentHandlers, CurrentStatePublisher}
import csw.messages.commands.CommandResponse.Error
import csw.messages.commands.{CommandResponse, ControlCommand, Setup}
import csw.messages.framework.ComponentInfo
import csw.messages.location._
import csw.messages.scaladsl.TopLevelActorMessage
import csw.services.command.scaladsl.{CommandResponseManager, CommandService}

import scala.concurrent.duration._
import csw.services.location.scaladsl.LocationService

import scala.async.Async._
import scala.concurrent.{ExecutionContextExecutor, Future}
import csw.services.logging.scaladsl.LoggerFactory

// Add messages here...

private class TestAssemblyBehaviorFactory extends ComponentBehaviorFactory {
  override def handlers(ctx: ActorContext[TopLevelActorMessage],
                        componentInfo: ComponentInfo,
                        commandResponseManager: CommandResponseManager,
                        currentStatePublisher: CurrentStatePublisher,
                        locationService: LocationService,
                        loggerFactory: LoggerFactory
                       ): ComponentHandlers =
    new TestAssemblyHandlers(ctx, componentInfo, commandResponseManager, currentStatePublisher, locationService, loggerFactory)
}

private class TestAssemblyHandlers(ctx: ActorContext[TopLevelActorMessage],
                                   componentInfo: ComponentInfo,
                                   commandResponseManager: CommandResponseManager,
                                   currentStatePublisher: CurrentStatePublisher,
                                   locationService: LocationService,
                                   loggerFactory: LoggerFactory)
  extends ComponentHandlers(ctx, componentInfo, commandResponseManager,
    currentStatePublisher, locationService, loggerFactory) {

  private val log = loggerFactory.getLogger
  // Set when the location is received from the location service (below)
  private var testHcd: Option[CommandService] = None
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
      val setup = Setup(controlCommand.source, controlCommand.commandName, controlCommand.maybeObsId, controlCommand.paramSet)
      commandResponseManager.addSubCommand(controlCommand.runId, setup.runId)

      val f = for {
        response <- hcd.submitAndSubscribe(setup)
      } yield {
        log.info(s"response = $response")
        commandResponseManager.updateSubCommand(setup.runId, response)
      }
      f.recover {
        case ex =>
          commandResponseManager.updateSubCommand(setup.runId, Error(setup.runId, ex.toString))
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
        testHcd = Some(new CommandService(location.asInstanceOf[AkkaLocation])(ctx.system))
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

