package csw.qa.location

import akka.actor.typed.{ActorSystem, Behavior}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors, TimerScheduler}
import csw.framework.scaladsl.RegistrationFactory
import csw.location.api.scaladsl.LocationService
import csw.location.client.HttpCodecs
import csw.location.models.Connection.{AkkaConnection, HttpConnection}
import csw.location.models.codecs.LocationCodecs
import csw.location.models.{ComponentId, ComponentType}
import csw.logging.client.scaladsl.GenericLoggerFactory
import csw.params.core.models.Prefix

import scala.concurrent.duration._
import scala.concurrent.Await

object TestAkkaService {
  // Behavior of the ith service
  def behavior(i: Int,
               options: TestAkkaServiceApp.Options,
               locationService: LocationService): Behavior[ServiceMessageType] =
    Behaviors.withTimers(
      timers =>
        Behaviors.setup[ServiceMessageType](
          ctx => new TestAkkaService(ctx, timers, i, options, locationService)
      )
    )

  // Component ID of the ith service
  def componentId(i: Int) =
    ComponentId(s"TestAkkaService_$i", ComponentType.Service)

  // Connection for the ith service
  def connection(i: Int): AkkaConnection = AkkaConnection(componentId(i))

  private case object TimerKey

}

/**
  * A dummy akka test service that registers with the location service
  */
class TestAkkaService(ctx: ActorContext[ServiceMessageType],
                      timers: TimerScheduler[ServiceMessageType],
                      i: Int,
                      options: TestAkkaServiceApp.Options,
                      locationService: LocationService)
    extends AbstractBehavior[ServiceMessageType]
    with HttpCodecs
    with LocationCodecs {

  import options._

  implicit def actorSystem: ActorSystem[Nothing] = ctx.system
  private val log = GenericLoggerFactory.getLogger(ctx)
  private val registrationFactory = new RegistrationFactory()

  // Register with the location service
  private val reg = Await.result(
    locationService.register(
      registrationFactory.akkaTyped(
        TestAkkaService.connection(i),
        Prefix("test.prefix"),
        ctx.self
      )
    ),
    30.seconds
  )

  log.debug(
    s"Registered service $i as: ${reg.location.connection.name} with URI = ${reg.location.uri}"
  )

  if (autostop != 0)
    timers.startSingleTimer(TestAkkaService.TimerKey, Quit, autostop.seconds)

  val componentId = ComponentId("rmyservice", ComponentType.Service)
  val connection = HttpConnection(componentId)

  override def onMessage(
    msg: ServiceMessageType
  ): Behavior[ServiceMessageType] = {
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
  // Behavior of the ith service
  def behavior(i: Int,
               options: TestAkkaServiceApp.Options,
               locationService: LocationService): Behavior[ServiceMessageType] =
    Behaviors.withTimers(
      timers =>
        Behaviors.setup[ServiceMessageType](
          ctx => new TestAkkaService2(ctx, timers, i, options, locationService)
      )
    )

  // Component ID of the ith service
  def componentId(i: Int) =
    ComponentId(s"TestAkkaService2_$i", ComponentType.Service)

  // Connection for the ith service
  def connection(i: Int): AkkaConnection = AkkaConnection(componentId(i))

  private case object TimerKey
}

/**
  * A dummy akka test service that registers with the location service
  */
class TestAkkaService2(ctx: ActorContext[ServiceMessageType],
                       timers: TimerScheduler[ServiceMessageType],
                       i: Int,
                       options: TestAkkaServiceApp.Options,
                       locationService: LocationService)
    extends AbstractBehavior[ServiceMessageType] {

  import options._

  implicit def actorSystem: ActorSystem[Nothing] = ctx.system
  private val log = GenericLoggerFactory.getLogger(ctx)
  private val registrationFactory = new RegistrationFactory()

  // Register with the location service
  private val reg = Await.result(
    locationService.register(
      registrationFactory.akkaTyped(
        TestAkkaService2.connection(i),
        Prefix("test.prefix"),
        ctx.self
      )
    ),
    30.seconds
  )

  log.debug(
    s"Registered service $i as: ${reg.location.connection.name} with URI = ${reg.location.uri}"
  )

  if (autostop != 0)
    timers.startSingleTimer(TestAkkaService2.TimerKey, Quit, autostop.seconds)

  override def onMessage(
    msg: ServiceMessageType
  ): Behavior[ServiceMessageType] = {
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
