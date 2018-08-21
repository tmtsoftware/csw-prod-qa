package csw.qa.framework

import akka.actor.Cancellable
import akka.actor.typed.scaladsl.ActorContext
import com.typesafe.config.ConfigFactory
import csw.framework.CurrentStatePublisher
import csw.framework.deploy.containercmd.ContainerCmd
import csw.framework.scaladsl.{ComponentBehaviorFactory, ComponentHandlers}
import csw.messages.TopLevelActorMessage
import csw.messages.commands.CommandResponse.{Completed, Error}
import csw.messages.commands.{CommandResponse, ControlCommand, Setup}
import csw.messages.events._
import csw.messages.framework.ComponentInfo
import csw.messages.location.TrackingEvent
import csw.messages.params.generics.{Key, KeyType}
import csw.messages.params.models.Id
import csw.services.alarm.api.scaladsl.AlarmService
import csw.services.command.CommandResponseManager
import csw.services.event.api.exceptions.PublishFailure
import csw.services.event.api.scaladsl.EventService
import csw.services.location.scaladsl.LocationService
import csw.services.logging.scaladsl.LoggerFactory

import scala.concurrent.duration._
import scala.async.Async._
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.Random

private class TestHcdBehaviorFactory extends ComponentBehaviorFactory {
  override def handlers(ctx: ActorContext[TopLevelActorMessage],
                        componentInfo: ComponentInfo,
                        commandResponseManager: CommandResponseManager,
                        currentStatePublisher: CurrentStatePublisher,
                        locationService: LocationService,
                        eventService: EventService,
                        alarmService: AlarmService,
                        loggerFactory: LoggerFactory): ComponentHandlers =
    new TestHcdHandlers(ctx,
                        componentInfo,
                        commandResponseManager,
                        currentStatePublisher,
                        locationService,
                        eventService,
                        alarmService,
                        loggerFactory)
}

private class TestHcdHandlers(ctx: ActorContext[TopLevelActorMessage],
                              componentInfo: ComponentInfo,
                              commandResponseManager: CommandResponseManager,
                              currentStatePublisher: CurrentStatePublisher,
                              locationService: LocationService,
                              eventService: EventService,
                              alarmService: AlarmService,
                              loggerFactory: LoggerFactory)
    extends ComponentHandlers(ctx,
                              componentInfo,
                              commandResponseManager,
                              currentStatePublisher,
                              locationService,
                              eventService,
                              alarmService,
                              loggerFactory) {

  private val log = loggerFactory.getLogger
  implicit val ec: ExecutionContextExecutor = ctx.executionContext

  // Dummy key for publishing events
  private val eventValueKey: Key[Int] = KeyType.IntKey.make("hcdEventValue")
  private val eventName = EventName("myHcdEvent")
  private val eventValues = Random
  private val baseEvent = SystemEvent(componentInfo.prefix, eventName)
    .add(eventValueKey.set(eventValues.nextInt))

  override def initialize(): Future[Unit] = {
    log.debug("Initialize called")
    startPublishingEvents().map(_ => ())
  }

  override def validateCommand(
      controlCommand: ControlCommand): CommandResponse = {
    CommandResponse.Accepted(controlCommand.runId)
  }

  var submitCount = 0

  override def onSubmit(controlCommand: ControlCommand): Unit = {
    log.debug(s"onSubmit called: $controlCommand")
    Thread.sleep(1000) // simulate some work

    // Temp: Used to test what happens when a submit fails
    submitCount = submitCount + 1

    controlCommand match {
      case setup: Setup =>
        if (submitCount != 3)
          commandResponseManager.addOrUpdateCommand(
            controlCommand.runId,
            Completed(controlCommand.runId))
        else
          commandResponseManager.addOrUpdateCommand(
            controlCommand.runId,
            Error(controlCommand.runId, "Command failed"))

      case x =>
        commandResponseManager.addOrUpdateCommand(
          controlCommand.runId,
          Error(controlCommand.runId, s"Unsupported command type: $x"))

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

  override def onLocationTrackingEvent(trackingEvent: TrackingEvent): Unit =
    log.debug(s"onLocationTrackingEvent called: $trackingEvent")

  private def startPublishingEvents(): Future[Cancellable] = async {
    log.debug("start publishing events (1)")
    val publisher = await(eventService.defaultPublisher)
    log.debug("start publishing events (2)")
    publisher.publish(eventGenerator(), 5.seconds, onError)
  }

  // this holds the logic for event generation, could be based on some computation or current state of HCD
  private def eventGenerator(): Event = {
    val event = baseEvent
      .copy(eventId = Id(), eventTime = EventTime())
      .add(eventValueKey.set(eventValues.nextInt))
    log.debug(s"Publishing event: $event")
    event
  }

  private def onError(publishFailure: PublishFailure): Unit =
    log.error(s"Publish failed for event: [${publishFailure.event}]",
              ex = publishFailure.cause)

}

object TestHcdApp extends App {
  val defaultConfig = ConfigFactory.load("TestHcd.conf")
  ContainerCmd.start("TestHcd", args, Some(defaultConfig))
}
