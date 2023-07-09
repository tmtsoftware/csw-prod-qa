package csw.qa.framework

import akka.actor.typed
import akka.actor.typed.scaladsl.ActorContext
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import csw.command.client.messages.TopLevelActorMessage
import csw.framework.deploy.containercmd.ContainerCmd
import csw.framework.models.CswContext
import csw.framework.scaladsl.ComponentHandlers
import csw.params.commands.CommandResponse.{SubmitResponse, ValidateCommandResponse}
import csw.params.commands.{CommandResponse, ControlCommand}
import akka.actor.typed.scaladsl.AskPattern._
import csw.location.api.models.TrackingEvent
import csw.params.core.models.Id
import csw.prefix.models.Subsystem.CSW
import csw.time.core.models.UTCTime

import scala.concurrent.duration._
import scala.concurrent.ExecutionContextExecutor

private class TestAssemblyHandlers(ctx: ActorContext[TopLevelActorMessage],
                                   cswCtx: CswContext)
  extends ComponentHandlers(ctx, cswCtx) {

  import cswCtx._

  implicit val ec: ExecutionContextExecutor = ctx.executionContext
  implicit val timeout: Timeout = Timeout(3.seconds)
  implicit val sched: typed.Scheduler = ctx.system.scheduler

  private val log = loggerFactory.getLogger

  // A worker actor that holds the state and implements the assembly (Optional if the assembly has no mutable state)
  private val worker = ctx.spawn(TestAssemblyWorker.make(cswCtx), "testWorker")

  override def initialize(): Unit = {
    worker ? (ref => TestAssemblyWorker.Initialize(ref))
  }

  override def validateCommand(runId: Id,
                               controlCommand: ControlCommand): ValidateCommandResponse = {
    CommandResponse.Accepted(runId)
  }

  override def onSubmit(runId: Id, controlCommand: ControlCommand): SubmitResponse = {
    log.info(s"Received submit: $controlCommand")
    worker ! TestAssemblyWorker.Submit(runId, controlCommand)
    CommandResponse.Started(runId)
  }

  override def onOneway(runId: Id, controlCommand: ControlCommand): Unit = {
    log.debug("onOneway called")
  }

  override def onShutdown(): Unit = {
    log.debug("onShutdown called")
  }

  override def onGoOffline(): Unit = log.debug("onGoOffline called")

  override def onGoOnline(): Unit = log.debug("onGoOnline called")

  override def onLocationTrackingEvent(trackingEvent: TrackingEvent): Unit = {
    log.info(s"XXX onLocationTrackingEvent $trackingEvent")
    worker ! TestAssemblyWorker.Location(trackingEvent)
  }

  override def onDiagnosticMode(startTime: UTCTime, hint: String): Unit = {}

  override def onOperationsMode(): Unit = {}
}

// Start assembly from the command line using TestAssembly.conf resource file
object TestAssemblyApp extends App {
  val defaultConfig = ConfigFactory.load("TestAssembly.conf")
  ContainerCmd.start("testassembly", CSW, args, Some(defaultConfig))
}
