package csw.qa.location

import akka.typed.Behavior
import akka.typed.scaladsl.{Actor, ActorContext, TimerScheduler}
import csw.services.location.models.Connection.AkkaConnection
import csw.services.location.models.{AkkaRegistration, ComponentId, ComponentType}
import csw.services.location.scaladsl.LocationService
import csw.services.logging.scaladsl.ComponentLogger

import scala.concurrent.duration._
import scala.concurrent.Await

object TestAkkaService {
  // Behaviour of the ith service
  def behavior(i: Int, options: TestAkkaServiceApp.Options, locationService: LocationService): Behavior[ServiceMessageType] =
    Actor.withTimers(timers => Actor.mutable[ServiceMessageType](ctx ⇒ new TestAkkaService(ctx, timers, i, options, locationService)))

  // Component ID of the ith service
  def componentId(i: Int) = ComponentId(s"TestAkkaService_$i", ComponentType.Assembly)

  // Connection for the ith service
  def connection(i: Int): AkkaConnection = AkkaConnection(componentId(i))

  private case object TimerKey

}

object TestAkkaServiceLogger extends ComponentLogger("TestAkkaService")

/**
  * A dummy akka test service that registers with the location service
  */
class TestAkkaService(ctx: ActorContext[ServiceMessageType],
                      timers: TimerScheduler[ServiceMessageType],
                      i: Int, options: TestAkkaServiceApp.Options,
                      locationService: LocationService)
  extends TestAkkaServiceLogger.TypedActor[ServiceMessageType](ctx) {

  import options._

  // Register with the location service
  private val reg = Await.result(
    locationService.register(AkkaRegistration(TestAkkaService.connection(i), ctx.self)),
    30.seconds)

  log.debug(s"Registered service $i as: ${reg.location.connection.name} with URI = ${reg.location.uri}")

  if (autostop != 0)
    timers.startSingleTimer(TestAkkaService.TimerKey, Quit, autostop.seconds)

  override def onMessage(msg: ServiceMessageType): Behavior[ServiceMessageType] = {
    msg match {
      // This is the message that TestServiceClient sends when it discovers this service
      case ClientMessage(replyTo) =>
        if (logMessages)
          log.debug(s"Received scala client message from: $replyTo")
        Behavior.same

      case Quit =>
        log.info(s"Actor $i is shutting down after $autostop seconds")
        Await.result(reg.unregister(), 10.seconds)
        Behavior.stopped
    }
  }
}


// ---- test second component -----

object TestAkkaService2 {
  // Behaviour of the ith service
  def behavior(i: Int, options: TestAkkaServiceApp.Options, locationService: LocationService): Behavior[ServiceMessageType] =
    Actor.withTimers(timers => Actor.mutable[ServiceMessageType](ctx ⇒ new TestAkkaService2(ctx, timers, i, options, locationService)))

  // Component ID of the ith service
  def componentId(i: Int) = ComponentId(s"TestAkkaService2_$i", ComponentType.Assembly)

  // Connection for the ith service
  def connection(i: Int): AkkaConnection = AkkaConnection(componentId(i))

  private case object TimerKey
}


object TestAkkaServiceLogger2 extends ComponentLogger("TestAkkaService2")

/**
  * A dummy akka test service that registers with the location service
  */
class TestAkkaService2(ctx: ActorContext[ServiceMessageType],
                       timers: TimerScheduler[ServiceMessageType],
                       i: Int, options: TestAkkaServiceApp.Options, locationService: LocationService)
  extends TestAkkaServiceLogger2.TypedActor[ServiceMessageType](ctx) {

  import options._

  // Register with the location service
  private val reg = Await.result(
    locationService.register(AkkaRegistration(TestAkkaService2.connection(i), ctx.self)),
    30.seconds)

  log.debug(s"Registered service $i as: ${reg.location.connection.name} with URI = ${reg.location.uri}")

  if (autostop != 0)
    timers.startSingleTimer(TestAkkaService2.TimerKey, Quit, autostop.seconds)

  override def onMessage(msg: ServiceMessageType): Behavior[ServiceMessageType] = {
    msg match {
      // This is the message that TestServiceClient sends when it discovers this service
      case ClientMessage(replyTo) =>
        if (logMessages)
          log.debug(s"Received scala client message from: $replyTo")
        this

      case Quit =>
        log.info(s"Actor $i is shutting down after $autostop seconds")
        Await.result(reg.unregister(), 10.seconds)
        Behavior.stopped
    }
  }
}

