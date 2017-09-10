package csw.qa.location

import akka.stream.Materializer
import akka.typed.Behavior
import akka.typed.scaladsl.{Actor, ActorContext, TimerScheduler}
import csw.services.location.models.Connection.AkkaConnection
import csw.services.location.models.{AkkaLocation, LocationRemoved, LocationUpdated}
import csw.services.location.scaladsl.LocationService
import csw.services.logging.scaladsl.ComponentLogger

object TestServiceClientLogger extends ComponentLogger("TestServiceClient")

object TestServiceClient {
  def behavior(options: TestServiceClientApp.Options, locationService: LocationService)(implicit mat: Materializer): Behavior[ServiceClientMessageType] =
    Actor.withTimers(timers => Actor.mutable[ServiceClientMessageType](ctx ⇒ new TestServiceClient(ctx, timers, options, locationService)).narrow)
}

/**
  * A test client actor that uses the location service to resolve services
  */
class TestServiceClient(ctx: ActorContext[ServiceClientMessageType],
                        timers: TimerScheduler[ServiceClientMessageType],
                        options: TestServiceClientApp.Options,
                        locationService: LocationService)(implicit mat: Materializer)
  extends TestServiceClientLogger.TypedActor[ServiceClientMessageType](ctx) {

  import options._

  private val connections: Set[AkkaConnection] = (firstService until firstService + numServices).
    toList.map(i => TestAkkaService.connection(i)).toSet

  // Subscribes to changes in each connection and forwards location messages to this actor
  connections.foreach(locationService.subscribe(_, trackingEvent => ctx.self ! TrackingEventMessage(trackingEvent)))

  override def onMessage(msg: ServiceClientMessageType): Behavior[ServiceClientMessageType] = {
    msg match {
      // Receive a location from the location service and if it is an akka location, send it a message
      case TrackingEventMessage(LocationUpdated(loc)) =>
        log.debug(s"Location updated ${loc.connection.name}")
        loc match {
          case actorRef: AkkaLocation =>
            actorRef.typedRef ! ClientMessage(ctx.self)
          case x => log.error(s"Received unexpected location type: $x")
        }

      // A location was removed
      case TrackingEventMessage(LocationRemoved(conn)) =>
        log.debug(s"Location removed ${conn.name}")
    }
    Behavior.same
  }
}

