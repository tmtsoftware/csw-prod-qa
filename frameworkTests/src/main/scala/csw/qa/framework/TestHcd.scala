package csw.qa.framework

import akka.actor.{ActorSystem, Cancellable}
import akka.actor.typed.scaladsl.ActorContext
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpMethods, HttpRequest, StatusCodes}
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.typed.scaladsl.ActorMaterializer
import akka.util.ByteString
import com.typesafe.config.ConfigFactory
import csw.command.client.messages.TopLevelActorMessage
import csw.event.api.exceptions.PublishFailure
import csw.framework.deploy.containercmd.ContainerCmd
import csw.framework.models.CswContext
import csw.framework.scaladsl.{ComponentBehaviorFactory, ComponentHandlers}
import csw.location.api.models.Connection.HttpConnection
import csw.location.api.models.{ComponentId, ComponentType, TrackingEvent}
import csw.params.commands.CommandResponse.{Completed, Error, SubmitResponse, ValidateCommandResponse}
import csw.params.commands.{CommandResponse, ControlCommand, Setup}
import csw.params.core.generics.{Key, KeyType}
import csw.params.core.models.Id
import csw.params.events.{Event, EventName, SystemEvent}
import csw.time.core.models.UTCTime
import io.bullet.borer.Cbor

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
    .add(eventValueKey.set(eventValues.nextInt(100)))
  private val baseEvent2 = SystemEvent(componentInfo.prefix, eventName2)
    .add(eventValueKey.set(eventValues.nextInt(1000)))
  private val baseEvent3 = SystemEvent(componentInfo.prefix, eventName3)
    .add(eventValueKey.set(eventValues.nextInt(10000)))

  override def initialize(): Future[Unit] = async {
    log.debug("Initialize called")
    startPublishingEvents()
  }

  override def validateCommand(
      controlCommand: ControlCommand): ValidateCommandResponse = {
    CommandResponse.Accepted(controlCommand.runId)
  }

  var submitCount = 0


  // Test connection to pycsw based service
  def pycswTest(setup: Setup): Unit = {
    import akka.actor.typed.scaladsl.adapter.TypedActorSystemOps
    import csw.params.core.formats.ParamCodecs._
    implicit val sys: ActorSystem = ctx.system.toUntyped
    implicit val mat: Materializer = ActorMaterializer()(ctx.system)

    def concatByteStrings(source: Source[ByteString, _]): Future[ByteString] = {
      val sink = Sink.fold[ByteString, ByteString](ByteString()) { case (acc, bs) =>
        acc ++ bs
      }
      source.runWith(sink)
    }

    val maybeLocation = Await.result(cswCtx.locationService.find(HttpConnection(ComponentId("pycswTest", ComponentType.Service))), 2.seconds)
    maybeLocation.foreach {loc =>
      val host = loc.uri.getHost
      val port = loc.uri.getPort
      val uri = s"http://$host:$port/submit"
      val byteArray = Cbor.encode(setup).toByteArray

      val response = Await.result(
        Http(sys).singleRequest(
          HttpRequest(HttpMethods.POST, uri,
            entity = HttpEntity(ContentTypes.`application/octet-stream`, byteArray)
          )
        ), 3.seconds)
      if (response.status == StatusCodes.OK) {
        log.info(s"pycsw response: $response")
        val bs = Await.result(concatByteStrings(response.entity.dataBytes), 1.second)
        val commandResponse = Cbor.decode(bs.toArray).to[CommandResponse.RemoteMsg].value
        log.info(s"pycsw commandResponse = $commandResponse")
      } else {
        log.error(s"pycsw error response: $response")
      }
    }
  }

  override def onSubmit(controlCommand: ControlCommand): SubmitResponse = {
    log.debug(s"onSubmit called: $controlCommand")

    controlCommand match {
      case setup: Setup =>
        pycswTest(setup)

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
    log.error(s"Publish failed for event: [${publishFailure.event}]",
              ex = publishFailure.cause)

}

object TestHcdApp extends App {
  val defaultConfig = ConfigFactory.load("TestHcd.conf")
  ContainerCmd.start("TestHcd", args, Some(defaultConfig))
}
