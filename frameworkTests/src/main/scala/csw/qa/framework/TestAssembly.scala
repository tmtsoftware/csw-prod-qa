package csw.qa.framework

import akka.typed.ActorRef
import akka.typed.scaladsl.ActorContext
import com.typesafe.config.ConfigFactory
import csw.apps.containercmd.ContainerCmd
import csw.framework.scaladsl.{ComponentBehaviorFactory, ComponentHandlers}
import csw.messages._
import csw.messages.PubSub.PublisherMessage
import csw.messages.RunningMessage.DomainMessage
import csw.messages.ccs.commands.{CommandResponse, ControlCommand}
import csw.messages.framework.ComponentInfo
import csw.messages.location.TrackingEvent
import csw.messages.params.states.CurrentState
import csw.services.location.scaladsl.LocationService
import csw.services.logging.scaladsl.CommonComponentLogger

import scala.async.Async._
import scala.concurrent.{ExecutionContextExecutor, Future}

// Base trait for Test Assembly domain messages
sealed trait TestAssemblyDomainMessage extends DomainMessage

// Add messages here...

private class TestAssemblyBehaviorFactory extends ComponentBehaviorFactory[TestAssemblyDomainMessage] {
  override def handlers(ctx: ActorContext[ComponentMessage],
                        componentInfo: ComponentInfo,
                        commandResponseManager: ActorRef[CommandResponseManagerMessage],
                        pubSubRef: ActorRef[PublisherMessage[CurrentState]],
                        locationService: LocationService
                       ): ComponentHandlers[TestAssemblyDomainMessage] =
    new TestAssemblyHandlers(ctx, componentInfo, commandResponseManager, pubSubRef, locationService)
}

object TestAssemblyLogger extends CommonComponentLogger("TestAssembly")

private class TestAssemblyHandlers(ctx: ActorContext[ComponentMessage],
                                   componentInfo: ComponentInfo,
                                   commandResponseManager: ActorRef[CommandResponseManagerMessage],
                                   pubSubRef: ActorRef[PubSub.PublisherMessage[CurrentState]],
                                   locationService: LocationService)
  extends ComponentHandlers[TestAssemblyDomainMessage](ctx, componentInfo, commandResponseManager, pubSubRef, locationService)
    with TestAssemblyLogger.Simple {

  implicit val ec: ExecutionContextExecutor = ctx.executionContext

  override def initialize(): Future[Unit] = async {
    log.debug("Initialize called")
  }

  override def validateSubmit(controlCommand: ControlCommand): CommandResponse = {
    CommandResponse.Accepted(controlCommand.runId)
  }

  override def validateOneway(controlCommand: ControlCommand): CommandResponse = {
    CommandResponse.Accepted(controlCommand.runId)
  }

  override def onSubmit(controlCommand: ControlCommand, replyTo: ActorRef[CommandResponse]): Unit = {
    log.debug("onSubmit called")
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

  override def onLocationTrackingEvent(trackingEvent: TrackingEvent): Unit =
    log.debug(s"onLocationTrackingEvent called: $trackingEvent")

  override protected def maybeComponentName(): Option[String] = Some("TestAssembly")

}

// Start assembly from the command line using TestAssembly.conf resource file
object TestAssemblyApp extends App {
  val defaultConfig = ConfigFactory.load("TestAssembly.conf")
  ContainerCmd.start("TestAssembly", args, Some(defaultConfig))
}

