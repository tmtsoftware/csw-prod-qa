package csw.qa.framework

import akka.typed.ActorRef
import akka.typed.scaladsl.ActorContext
import com.typesafe.config.ConfigFactory
import csw.apps.containercmd.ContainerCmd
import csw.framework.scaladsl.{ComponentBehaviorFactory, ComponentHandlers}
import csw.messages.CommandResponseManagerMessage.AddOrUpdateCommand
import csw.messages._
import csw.messages.RunningMessage.DomainMessage
import csw.messages.ccs.commands.CommandResponse.Completed
import csw.messages.ccs.commands.{CommandResponse, ControlCommand}
import csw.messages.framework.ComponentInfo
import csw.messages.location.TrackingEvent
import csw.messages.models.PubSub.PublisherMessage
import csw.messages.params.states.CurrentState
import csw.services.location.scaladsl.LocationService
import csw.services.logging.scaladsl.LoggerFactory

import scala.async.Async._
import scala.concurrent.{ExecutionContextExecutor, Future}

// Base trait for Test HCD domain messages
sealed trait TestHcdDomainMessage extends DomainMessage

// Add messages here...

private class TestHcdBehaviorFactory extends ComponentBehaviorFactory[TestHcdDomainMessage] {
  override def handlers(ctx: ActorContext[TopLevelActorMessage],
                        componentInfo: ComponentInfo,
                        commandResponseManager: ActorRef[CommandResponseManagerMessage],
                        pubSubRef: ActorRef[PublisherMessage[CurrentState]],
                        locationService: LocationService,
                        loggerFactory: LoggerFactory
                       ): ComponentHandlers[TestHcdDomainMessage] =
    new TestHcdHandlers(ctx, componentInfo, commandResponseManager, pubSubRef, locationService, loggerFactory)
}

private class TestHcdHandlers(ctx: ActorContext[TopLevelActorMessage],
                              componentInfo: ComponentInfo,
                              commandResponseManager: ActorRef[CommandResponseManagerMessage],
                              pubSubRef: ActorRef[PublisherMessage[CurrentState]],
                              locationService: LocationService,
                              loggerFactory: LoggerFactory)
  extends ComponentHandlers[TestHcdDomainMessage](ctx, componentInfo, commandResponseManager, pubSubRef,
    locationService, loggerFactory) {

  private val log = loggerFactory.getLogger
  implicit val ec: ExecutionContextExecutor = ctx.executionContext

  override def initialize(): Future[Unit] = async {
    log.debug("Initialize called")
  }

  override def validateCommand(controlCommand: ControlCommand): CommandResponse = {
    CommandResponse.Accepted(controlCommand.runId)
//    CommandResponse.Invalid(controlCommand.runId, MissingKeyIssue("XXX"))
  }

  override def onSubmit(controlCommand: ControlCommand): Unit = {
    log.debug("onSubmit called")
    commandResponseManager ! AddOrUpdateCommand(controlCommand.runId, Completed(controlCommand.runId))
//    commandResponseManager ! AddOrUpdateCommand(controlCommand.runId, Error(controlCommand.runId, "XXX"))
  }

  override def onOneway(controlCommand: ControlCommand): Unit = {
    log.debug("onOneway called")
  }

  override def onShutdown(): Future[Unit] = async {
    log.debug("onShutdown called")
  }

  override def onGoOffline(): Unit = log.debug("onGoOffline called")

  override def onGoOnline(): Unit = log.debug("onGoOnline called")

  override def onDomainMsg(testMsg: TestHcdDomainMessage): Unit = testMsg match {
    case x => log.debug(s"onDomainMessage called: $x")
  }

  override def onLocationTrackingEvent(trackingEvent: TrackingEvent): Unit =
    log.debug(s"onLocationTrackingEvent called: $trackingEvent")
}

object TestHcdApp extends App {
  val defaultConfig = ConfigFactory.load("TestHcd.conf")
  ContainerCmd.start("TestHcd", args, Some(defaultConfig))
}