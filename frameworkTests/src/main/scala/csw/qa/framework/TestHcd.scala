package csw.qa.framework

import akka.actor.Cancellable
import akka.actor.typed.scaladsl.ActorContext
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import csw.command.client.HttpCommandService
import csw.command.client.messages.TopLevelActorMessage
import csw.event.api.exceptions.PublishFailure
import csw.framework.deploy.containercmd.ContainerCmd
import csw.framework.models.CswContext
import csw.framework.scaladsl.{ComponentBehaviorFactory, ComponentHandlers}
import csw.location.models.{ComponentId, ComponentType, TrackingEvent}
import csw.location.models.Connection.HttpConnection
import csw.params.commands.CommandResponse.{Accepted, Error, SubmitResponse, ValidateCommandResponse}
import csw.params.commands.{CommandName, CommandResponse, ControlCommand, Setup}
import csw.params.events.{Event, EventName, SystemEvent}
import csw.qa.framework.TestAssemblyWorker.{basePosKey, eventKey1, eventKey1b, eventKey2b, eventKey3, eventKey4}
import csw.time.core.models.UTCTime
import csw.params.core.generics.{Key, KeyType}
import csw.params.core.models.Coords.EqFrame.FK5
import csw.params.core.models.Coords.SolarSystemObject.Venus
import csw.params.core.models.{Angle, Coords, Id, ProperMotion, Struct}

import scala.concurrent.duration._
import scala.async.Async._
import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import scala.util.Random

private class TestHcdBehaviorFactory extends ComponentBehaviorFactory {
  override def handlers(ctx: ActorContext[TopLevelActorMessage],
                        cswServices: CswContext): ComponentHandlers =
    new TestHcdHandlers(ctx, cswServices)
}

private class TestHcdHandlers(ctx: ActorContext[TopLevelActorMessage],
                              cswCtx: CswContext)
    extends ComponentHandlers(ctx, cswCtx) {

  import cswCtx._

  private val log = loggerFactory.getLogger
  implicit val ec: ExecutionContextExecutor = ctx.executionContext

  // Dummy key for publishing events
  private val eventValueKey: Key[Int] = KeyType.IntKey.make("hcdEventValue")
  private val eventName = EventName("myHcdEvent")
  private val eventName2 = EventName("myHcdEvent2")
  private val eventName3 = EventName("myHcdEvent3")
  private val eventValues = Random
  private val baseEvent = SystemEvent(componentInfo.prefix, eventName)
  private val baseEvent2 = SystemEvent(componentInfo.prefix, eventName2)
  private val baseEvent3 = SystemEvent(componentInfo.prefix, eventName3)

  private val connection = HttpConnection(
    ComponentId("pycswTest", ComponentType.Service)
  )
  private val service =
    HttpCommandService(ctx.system, cswCtx.locationService, connection)

  // Dummy test command
  private def makeTestCommand(): ControlCommand = {
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

    Setup(componentInfo.prefix, CommandName("testCommand"), None)
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

  override def initialize(): Future[Unit] = async {
    log.debug("Initialize called")
    startPublishingEvents()
  }

  override def validateCommand(
    controlCommand: ControlCommand
  ): ValidateCommandResponse = {
    CommandResponse.Accepted(controlCommand.runId)
  }

  override def onSubmit(controlCommand: ControlCommand): SubmitResponse = {
    log.debug(s"onSubmit called: $controlCommand")
    implicit def timeout: Timeout = new Timeout(20.seconds)

    try {
      // Test sending a command to a python based HTTP service
      // (see pycsw project: Assumes pycsw's "TestCommandServer" is running)
      val validateResponse = Await.result(service.validate(controlCommand), 5.seconds)
      log.info(s"Response from validate command to ${connection.componentId.name}: $validateResponse")
      val onewayResponse = Await.result(service.oneway(controlCommand), 5.seconds)
      log.info(s"Response from oneway command to ${connection.componentId.name}: $onewayResponse")

      val testCommand = makeTestCommand()
      val firstCommandResponse = Await.result(service.submit(testCommand), 5.seconds)
      log.info(s"Response from submit command to ${connection.componentId.name}: $firstCommandResponse")
      val commandResponse = Await.result(service.queryFinal(testCommand.runId), 20.seconds)
      log.info(s"Response from submit command to ${connection.componentId.name}: $commandResponse")

      commandResponse
    } catch {
      case e: Exception =>
        log.error(
          s"Error sending command to ${service.connection.componentId.name}: $e",
          ex = e
        )
        Error(
          controlCommand.runId,
          s"Error sending command to ${service.connection.componentId.name}: $e"
        )
    }
  }

  override def onOneway(controlCommand: ControlCommand): Unit = {
    log.debug(s"onOneway called: $controlCommand")
    try {
      val onewayResponse =
        Await.result(service.oneway(controlCommand), 5.seconds)
      log.info(
        s"Response from oneway command to ${connection.componentId.name}: $onewayResponse"
      )
    } catch {
      case e: Exception =>
        log.error(
          s"Error sending oneway command to ${service.connection.componentId.name}: $e",
          ex = e
        )
    }
  }

  override def onShutdown(): Future[Unit] = async {
    log.debug("onShutdown called")
  }

  override def onGoOffline(): Unit = log.debug("onGoOffline called")

  override def onGoOnline(): Unit = log.debug("onGoOnline called")

  override def onLocationTrackingEvent(trackingEvent: TrackingEvent): Unit =
    log.debug(s"onLocationTrackingEvent called: $trackingEvent")

  private def startPublishingEvents(): Cancellable = {
    val publisher = eventService.defaultPublisher
    publisher.publish(eventGenerator(), 1.seconds, p => onError(p))
    publisher.publish(eventGenerator2(), 50.millis, p => onError(p))
    publisher.publish(eventGenerator3(), 5.seconds, p => onError(p))
  }

  // this holds the logic for event generation, could be based on some computation or current state of HCD
  private def eventGenerator(): Option[Event] = {
    val event = baseEvent
      .copy(eventId = Id(), eventTime = UTCTime.now())
      .add(eventValueKey.set(eventValues.nextInt(100)))
    Some(event)
  }
  private def eventGenerator2(): Option[Event] = {
    val event = baseEvent2
      .copy(eventId = Id(), eventTime = UTCTime.now())
      .add(eventValueKey.set(eventValues.nextInt(1000)))
    Some(event)
  }

  private def eventGenerator3(): Option[Event] = {
    val event = baseEvent3
      .copy(eventId = Id(), eventTime = UTCTime.now())
      .add(eventValueKey.set(eventValues.nextInt(10000)))
    Some(event)
  }

  private def onError(publishFailure: PublishFailure): Unit =
    log.error(
      s"Publish failed for event: [${publishFailure.event}]",
      ex = publishFailure.cause
    )

}

object TestHcdApp extends App {
  val defaultConfig = ConfigFactory.load("TestHcd.conf")
  ContainerCmd.start("TestHcd", args, Some(defaultConfig))
}
