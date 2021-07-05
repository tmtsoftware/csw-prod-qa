package csw.qa.framework

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.ActorContext
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import csw.command.client.messages.TopLevelActorMessage
import csw.event.api.exceptions.PublishFailure
import csw.framework.deploy.containercmd.ContainerCmd
import csw.framework.models.CswContext
import csw.framework.scaladsl.{ComponentBehaviorFactory, ComponentHandlers}
import csw.location.api.models.TrackingEvent
import csw.params.commands.CommandResponse.{Completed, SubmitResponse, ValidateCommandResponse}
import csw.params.commands.{CommandName, CommandResponse, ControlCommand, Setup}
import csw.params.core.formats.ParamCodecs
import csw.params.events.{Event, EventName, SystemEvent}
import csw.qa.framework.TestAssemblyWorker.{basePosKey, eventKey1, eventKey1b, eventKey2b, eventKey3, eventKey4}
import csw.time.core.models.UTCTime
import csw.params.core.generics.{Key, KeyType}
import csw.params.core.models.Coords.EqFrame.FK5
import csw.params.core.models.Coords.SolarSystemObject.Venus
import csw.params.core.models.{Angle, Coords, Id, ProperMotion, Struct}
import csw.params.core.states.{CurrentState, StateName}
import csw.prefix.models.Subsystem.CSW

import scala.concurrent.duration._
import scala.concurrent.ExecutionContextExecutor
import scala.util.Random

private class TestHcdBehaviorFactory extends ComponentBehaviorFactory {
  override def handlers(ctx: ActorContext[TopLevelActorMessage], cswServices: CswContext): ComponentHandlers =
    new TestHcdHandlers(ctx, cswServices)
}

//noinspection DuplicatedCode
private class TestHcdHandlers(ctx: ActorContext[TopLevelActorMessage], cswCtx: CswContext)
    extends ComponentHandlers(ctx, cswCtx) with ParamCodecs {

  import cswCtx._

  private val log                           = loggerFactory.getLogger
  implicit val system: ActorSystem[Nothing] = ctx.system
  implicit val ec: ExecutionContextExecutor = ctx.executionContext
  implicit def timeout: Timeout = new Timeout(20.seconds)

  // Dummy key for publishing events
  private val eventValueKey: Key[Int] = KeyType.IntKey.make("hcdEventValue")
  private val eventName               = EventName("myHcdEvent")
  private val eventValues             = Random
  private val baseEvent               = SystemEvent(componentInfo.prefix, eventName)

  // Dummy test command
  private def makeTestCommand(commandName: String): ControlCommand = {
    import Angle._
    import Coords._

    val pm = ProperMotion(0.5, 2.33)
    val eqCoord = EqCoord(
      ra = "12:13:14.15",
      dec = "-30:31:32.3",
      frame = FK5,
      pmx = pm.pmx,
      pmy = pm.pmy
    )
    val solarSystemCoord = SolarSystemCoord(Tag("BASE"), Venus)
    val minorPlanetCoord = MinorPlanetCoord(
      Tag("GUIDER1"),
      2000,
      90.degree,
      2.degree,
      100.degree,
      1.4,
      0.234,
      220.degree
    )
    val cometCoord = CometCoord(
      Tag("BASE"),
      2000.0,
      90.degree,
      2.degree,
      100.degree,
      1.4,
      0.234
    )
    val altAzCoord = AltAzCoord(Tag("BASE"), 301.degree, 42.5.degree)
    val posParam = basePosKey.set(
      eqCoord,
      solarSystemCoord,
      minorPlanetCoord,
      cometCoord,
      altAzCoord
    )

    Setup(componentInfo.prefix, CommandName(commandName), None)
      .add(posParam)
      .add(eventKey1b.set(1.0f, 2.0f, 3.0f))
      .add(
        eventKey2b.set(
          Struct()
            .add(eventKey1.set(1.0f))
            .add(eventKey3.set(1, 2, 3)),
          Struct()
            .add(eventKey1.set(2.0f))
            .add(eventKey3.set(4, 5, 6))
            .add(eventKey4.set(9.toByte, 10.toByte))
        )
      )
  }

  override def initialize(): Unit = {
    log.debug("Initialize called")
    startPublishingEvents()
    startPublishingCurrentState()
  }

  override def validateCommand(runId: Id, controlCommand: ControlCommand): ValidateCommandResponse = {
    CommandResponse.Accepted(runId)
  }

  override def onSubmit(runId: Id, controlCommand: ControlCommand): SubmitResponse = {
    log.debug(s"onSubmit called: $controlCommand")
    Completed(runId)
  }

  override def onOneway(runId: Id, controlCommand: ControlCommand): Unit = {
    log.debug(s"onOneway called: $controlCommand")
  }

  override def onShutdown(): Unit = {
    log.debug("onShutdown called")
  }

  override def onGoOffline(): Unit = log.debug("onGoOffline called")

  override def onGoOnline(): Unit = log.debug("onGoOnline called")

  override def onLocationTrackingEvent(trackingEvent: TrackingEvent): Unit =
    log.debug(s"onLocationTrackingEvent called: $trackingEvent")

  private def startPublishingEvents(): Unit = {
    val publisher = eventService.defaultPublisher
    publisher.publish(eventGenerator(), 60.seconds, p => onError(p))
  }

  private def startPublishingCurrentState(): Unit = {
    system.scheduler.scheduleAtFixedRate(1.second, 60.seconds) { () =>
      val params = makeTestCommand("ignore").paramSet
      val currentState = CurrentState(cswCtx.componentInfo.prefix, StateName("TestHcdState"), params)
      currentStatePublisher.publish(currentState)
    }
  }

  // this holds the logic for event generation, could be based on some computation or current state of HCD
  private def eventGenerator(): Option[Event] = {
    val event = baseEvent
      .copy(eventId = Id(), eventTime = UTCTime.now())
      .add(eventValueKey.set(eventValues.nextInt(100)))
    Some(event)
  }

  private def onError(publishFailure: PublishFailure): Unit =
    log.error(
      s"Publish failed for event: [${publishFailure.event}]",
      ex = publishFailure.cause
    )

  override def onDiagnosticMode(startTime: UTCTime, hint: String): Unit = {}

  override def onOperationsMode(): Unit = {}
}

object TestHcdApp extends App {
  val defaultConfig = ConfigFactory.load("TestHcd.conf")
  ContainerCmd.start("testhcd", CSW, args, Some(defaultConfig))
}
