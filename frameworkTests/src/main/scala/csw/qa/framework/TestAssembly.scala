package csw.qa.framework

import akka.actor.Scheduler
import akka.actor.typed.scaladsl.ActorContext
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import csw.command.client.messages.TopLevelActorMessage
import csw.framework.deploy.containercmd.ContainerCmd
import csw.framework.models.CswContext
import csw.framework.scaladsl.{ComponentBehaviorFactory, ComponentHandlers}
import csw.params.commands.CommandResponse.{SubmitResponse, ValidateCommandResponse}
import csw.params.commands.{CommandResponse, ControlCommand}
import akka.actor.typed.scaladsl.AskPattern._
import csw.location.model.scaladsl.TrackingEvent

import scala.concurrent.duration._
import scala.async.Async._
import scala.concurrent.{ExecutionContextExecutor, Future}

private class TestAssemblyBehaviorFactory extends ComponentBehaviorFactory {
  override def handlers(ctx: ActorContext[TopLevelActorMessage],
                        cswCtx: CswContext): ComponentHandlers =
    new TestAssemblyHandlers(ctx, cswCtx)
}


private class TestAssemblyHandlers(ctx: ActorContext[TopLevelActorMessage],
                                   cswCtx: CswContext)
  extends ComponentHandlers(ctx, cswCtx) {

  import cswCtx._

  implicit val ec: ExecutionContextExecutor = ctx.executionContext
  implicit val timeout: Timeout = Timeout(3.seconds)
  implicit val sched: Scheduler = ctx.system.scheduler

  private val log = loggerFactory.getLogger

  // A worker actor that holds the state and implements the assembly (Optional if the assembly has no mutable state)
  private val worker = ctx.spawn(TestAssemblyWorker.make(cswCtx), "testWorker")

  override def initialize(): Future[Unit] = {
    worker ? (ref => TestAssemblyWorker.Initialize(ref))
  }

  override def validateCommand(
                                controlCommand: ControlCommand): ValidateCommandResponse = {
    CommandResponse.Accepted(controlCommand.runId)
  }

  override def onSubmit(controlCommand: ControlCommand): SubmitResponse = {
    log.info(s"Received submit: $controlCommand")
    worker ! TestAssemblyWorker.Submit(controlCommand)
    CommandResponse.Started(controlCommand.runId)
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
    worker ! TestAssemblyWorker.Location(trackingEvent)
  }

}

// Start assembly from the command line using TestAssembly.conf resource file
object TestAssemblyApp extends App {
  val defaultConfig = ConfigFactory.load("TestAssembly.conf")
  ContainerCmd.start("TestAssembly", args, Some(defaultConfig))
}
