package csw.qa.framework

import java.net.InetAddress

import akka.actor.ActorSystem
import akka.typed.ActorRef
import akka.typed.scaladsl.ActorContext
import com.typesafe.config.ConfigFactory
import csw.apps.containercmd.ContainerCmd
import csw.framework.internal.wiring.{FrameworkWiring, Standalone}
import csw.framework.scaladsl.{ComponentBehaviorFactory, ComponentHandlers}
import csw.messages._
import csw.messages.PubSub.PublisherMessage
import csw.messages.RunningMessage.DomainMessage
import csw.messages.ccs.{Validation, ValidationIssue, Validations}
import csw.messages.framework.ComponentInfo
import csw.messages.location.TrackingEvent
import csw.messages.params.states.CurrentState
import csw.services.location.commons.ClusterAwareSettings
import csw.services.location.scaladsl.LocationService
import csw.services.logging.scaladsl.{ComponentLogger, LoggingSystemFactory}

import scala.async.Async._
import scala.concurrent.{ExecutionContextExecutor, Future}

// Base trait for Test Assembly domain messages
sealed trait TestAssemblyDomainMessage extends DomainMessage

// Add messages here...

private class TestAssemblyBehaviorFactory extends ComponentBehaviorFactory[TestAssemblyDomainMessage] {
  override def handlers(
                ctx: ActorContext[ComponentMessage],
                componentInfo: ComponentInfo,
                pubSubRef: ActorRef[PublisherMessage[CurrentState]],
                locationService: LocationService
              ): ComponentHandlers[TestAssemblyDomainMessage] =
    new TestAssemblyHandlers(ctx, componentInfo, pubSubRef, locationService)
}

private class TestAssemblyHandlers(ctx: ActorContext[ComponentMessage],
                                    componentInfo: ComponentInfo,
                                    pubSubRef: ActorRef[PubSub.PublisherMessage[CurrentState]],
                                    locationService: LocationService)
  extends ComponentHandlers[TestAssemblyDomainMessage](ctx, componentInfo, pubSubRef, locationService)
    with ComponentLogger.Simple {

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

  override def onDomainMsg(testMessage: TestAssemblyDomainMessage): Unit = testMessage match {
    case x => log.debug(s"onDomainMsg called: $x")
  }

  override def onLocationTrackingEvent(trackingEvent: TrackingEvent): Unit =
    log.debug(s"onLocationTrackingEvent called: $trackingEvent")

  override protected def maybeComponentName(): Option[String] = Some("TestAssembly")

}

// Start assembly from the command line using TestAssembly.conf resource file
object TestAssemblyApp extends App {
  // See See DEOPSCSW-171: Starting component from command line.
  val path = TempUtil.createStandaloneTmpFile("TestAssembly.conf")
  val defaultArgs = if (args.isEmpty) Array("--local",  "--standalone", path.toString) else args
  ContainerCmd.start("TestAssembly", defaultArgs)
}

