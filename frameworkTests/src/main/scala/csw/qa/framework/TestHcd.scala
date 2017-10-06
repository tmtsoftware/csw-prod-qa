package csw.qa.framework

import akka.typed.ActorRef
import akka.typed.scaladsl.ActorContext
import csw.apps.containercmd.ContainerCmd
import csw.framework.scaladsl.{ComponentBehaviorFactory, ComponentHandlers}
import csw.messages._
import csw.messages.RunningMessage.DomainMessage
import csw.messages.ccs.{Validation, ValidationIssue, Validations}
import csw.messages.framework.ComponentInfo
import csw.messages.location.TrackingEvent
import csw.messages.params.states.CurrentState
import csw.services.location.scaladsl.LocationService
import csw.services.logging.scaladsl.ComponentLogger

import scala.async.Async._
import scala.concurrent.{ExecutionContextExecutor, Future}

// Base trait for Test HCD domain messages
sealed trait TestHcdDomainMessage extends DomainMessage

// Add messages here...

private class TestHcdBehaviorFactory extends ComponentBehaviorFactory[TestHcdDomainMessage] {
  override def handlers(ctx: ActorContext[ComponentMessage],
                        componentInfo: ComponentInfo,
                        pubSubRef: ActorRef[PubSub.PublisherMessage[CurrentState]],
                        locationService: LocationService
                       ): ComponentHandlers[TestHcdDomainMessage] =
    new TestHcdHandlers(ctx, componentInfo, pubSubRef, locationService)
}

private class TestHcdHandlers(ctx: ActorContext[ComponentMessage],
                               componentInfo: ComponentInfo,
                               pubSubRef: ActorRef[PubSub.PublisherMessage[CurrentState]],
                               locationService: LocationService)
  extends ComponentHandlers[TestHcdDomainMessage](ctx, componentInfo, pubSubRef, locationService)
    with ComponentLogger.Simple{

  implicit val ec: ExecutionContextExecutor = ctx.executionContext

  override def initialize(): Future[Unit] = async {
    log.debug("Initialize called")
  }

  override def onSetup(commandMessage: CommandMessage): Validation = {
    log.debug(s"onSetup called: $commandMessage")
    Validations.Valid
  }

  override def onObserve(commandMessage: CommandMessage): Validation =  {
    log.debug(s"onObserve called: $commandMessage")
    Validations.Invalid(ValidationIssue.UnsupportedCommandIssue("Observe  not supported"))
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

  override protected def maybeComponentName(): Option[String] = Some("TestHcd")
}

object TestHcdApp extends App {
  // See See DEOPSCSW-171: Starting component from command line.
  val path = TempUtil.createStandaloneTmpFile("TestHcd.conf")
  val defaultArgs = if (args.isEmpty) Array("--local",  "--standalone", path.toString) else args
  ContainerCmd.start("TestHcd", defaultArgs)
}