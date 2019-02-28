package csw.qa.framework

import akka.actor.Cancellable
import akka.actor.typed.scaladsl.ActorContext
import com.typesafe.config.ConfigFactory
import csw.command.client.messages.TopLevelActorMessage
import csw.event.api.exceptions.PublishFailure
import csw.framework.deploy.containercmd.ContainerCmd
import csw.framework.models.CswContext
import csw.framework.scaladsl.{ComponentBehaviorFactory, ComponentHandlers}
import csw.location.api.models.TrackingEvent
import csw.params.commands.CommandResponse.{Completed, Error, SubmitResponse, ValidateCommandResponse}
import csw.params.commands.{CommandResponse, ControlCommand, Setup}
import csw.params.core.generics.{Key, KeyType}
import csw.params.core.models.Id
import csw.params.events.{Event, EventName, SystemEvent}
import csw.time.core.models.UTCTime

import scala.concurrent.duration._
import scala.async.Async._
import scala.concurrent.{ExecutionContextExecutor, Future}
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
  private val eventValues = Random
  private val baseEvent = SystemEvent(componentInfo.prefix, eventName)
    .add(eventValueKey.set(eventValues.nextInt(100)))

  override def initialize(): Future[Unit] = async {
    log.debug("Initialize called")
    startPublishingEvents()
  }

  override def validateCommand(
      controlCommand: ControlCommand): ValidateCommandResponse = {
    CommandResponse.Accepted(controlCommand.runId)
  }

  var submitCount = 0

  override def onSubmit(controlCommand: ControlCommand): SubmitResponse = {
    log.debug(s"onSubmit called: $controlCommand")

    controlCommand match {
      case _: Setup =>
        if (submitCount != 3)
          Completed(controlCommand.runId)
        else
          Error(controlCommand.runId, "Command failed")

      case x =>
        Error(controlCommand.runId, s"Unsupported command type: $x")

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

  private def startPublishingEvents(): Cancellable = {
    log.debug("start publishing events (1)")
    val publisher = eventService.defaultPublisher
    log.debug("start publishing events (2)")
    publisher.publish(eventGenerator(), 1.seconds, p => onError(p))
  }

  // this holds the logic for event generation, could be based on some computation or current state of HCD
  private def eventGenerator(): Option[Event] = {
    val event = baseEvent
      .copy(eventId = Id(), eventTime = UTCTime.now())
      .add(eventValueKey.set(eventValues.nextInt(100)))
    log.debug(s"Publishing event: $event")
    Some(event)
  }

  private def onError(publishFailure: PublishFailure): Unit =
    log.error(s"Publish failed for event: [${publishFailure.event}]",
              ex = publishFailure.cause)

}

object TestHcdApp extends App {
  val defaultConfig = ConfigFactory.load("TestHcd.conf")
  ContainerCmd.start("TestHcd", args, Some(defaultConfig))
}
