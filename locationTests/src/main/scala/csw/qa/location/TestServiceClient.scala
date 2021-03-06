package csw.qa.location

import akka.stream.Materializer
import akka.actor.typed.{ActorSystem, Behavior}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors, TimerScheduler}
import csw.location.api.models.Connection.AkkaConnection
import csw.location.api.models.{AkkaLocation, LocationRemoved, LocationUpdated}
import csw.location.api.scaladsl.LocationService
import csw.logging.client.scaladsl.GenericLoggerFactory

object TestServiceClient {
  def behavior(options: TestServiceClientApp.Options, locationService: LocationService)(implicit mat: Materializer): Behavior[ServiceClientMessageType] =
    Behaviors.withTimers(timers => Behaviors.setup[ServiceClientMessageType](ctx => new TestServiceClient(ctx, timers, options, locationService)).narrow)
}

/**
  * A test client actor that uses the location service to resolve services
  */
class TestServiceClient(ctx: ActorContext[ServiceClientMessageType],
                        timers: TimerScheduler[ServiceClientMessageType],
                        options: TestServiceClientApp.Options,
                        locationService: LocationService)(implicit mat: Materializer)
  extends AbstractBehavior[ServiceClientMessageType](ctx) {

  import options._
  implicit def actorSystem: ActorSystem[Nothing] = ctx.system

  private val log = GenericLoggerFactory.getLogger
  private val connections: Set[AkkaConnection] = (firstService until firstService + numServices).
    toList.map(i => TestAkkaService.connection(i)).toSet

  // Subscribes to changes in each connection and forwards location messages to this actor
  connections.foreach{ c =>
    locationService.track(c).runForeach(trackingEvent => ctx.self ! TrackingEventMessage(trackingEvent))
  }

  override def onMessage(msg: ServiceClientMessageType): Behavior[ServiceClientMessageType] = {

    msg match {
      // Receive a location from the location service and if it is an akka location, send it a message
      case TrackingEventMessage(LocationUpdated(loc)) =>
        loc match {
          case loc:
            AkkaLocation => log.info(s"Received Akka Location: $loc")
          case x => log.error(s"Received unexpected location type: $x")
        }

      // A location was removed
      case TrackingEventMessage(LocationRemoved(conn)) =>
        log.debug(s"Location removed ${conn.name}")
    }
    Behaviors.same
  }
}

