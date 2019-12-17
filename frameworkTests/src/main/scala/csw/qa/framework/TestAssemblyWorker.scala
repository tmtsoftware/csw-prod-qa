package csw.qa.framework

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import TestAssemblyWorker._
import akka.actor.typed
import akka.util.Timeout
import csw.alarm.models.Key.AlarmKey
import csw.command.api.scaladsl.CommandService
import csw.command.client.CommandServiceFactory
import csw.event.api.scaladsl.EventPublisher
import csw.framework.models.CswContext
import csw.location.models.{AkkaLocation, LocationRemoved, LocationUpdated, TrackingEvent}
import csw.logging.api.scaladsl.Logger
import csw.params.commands.CommandResponse.Error
import csw.params.commands.{CommandName, ControlCommand, Setup}
import csw.params.core.generics.KeyType.CoordKey
import csw.params.core.generics.{Key, KeyType}
import csw.params.core.models.Coords.EqFrame.FK5
import csw.params.core.models.Coords.SolarSystemObject.Venus
import csw.params.core.models.{Angle, Coords, Id, ObsId, ProperMotion, Struct}
import csw.params.core.states.StateName
import csw.params.events.{Event, EventKey, EventName, SystemEvent}
import csw.prefix.models.{Prefix, Subsystem}
import csw.prefix.models.Subsystem.{CSW, WFOS}
import csw.time.core.models.UTCTime

import scala.async.Async.async
import scala.concurrent.ExecutionContextExecutor
import scala.util.{Failure, Success}

//noinspection DuplicatedCode
object TestAssemblyWorker {

  sealed trait TestAssemblyWorkerMsg

  case class Initialize(replyTo: ActorRef[Unit]) extends TestAssemblyWorkerMsg

  case class Submit(runId: Id, controlCommand: ControlCommand)
      extends TestAssemblyWorkerMsg

  case class Location(trackingEvent: TrackingEvent)
      extends TestAssemblyWorkerMsg

  case object RefreshAlarms extends TestAssemblyWorkerMsg

//  case class SetDatabase(dsl: DSLContext) extends TestAssemblyWorkerMsg

  def make(cswCtx: CswContext): Behavior[TestAssemblyWorkerMsg] = {
    Behaviors.setup(ctx => new TestAssemblyWorker(ctx, cswCtx))
  }

//  private val dbName = "postgres"

  // --- Events ---

  // Key for HCD events
  private val hcdEventValueKey: Key[Int] = KeyType.IntKey.make("hcdEventValue")
  private val hcdEventName = EventName("myHcdEvent")
  private val hcdPrefix = Prefix(CSW, "TestHcd")

  // Keys for publishing events from assembly
  private[framework] val eventKey1: Key[Float] =
    KeyType.FloatKey.make("assemblyEventValue")
  private[framework] val eventKey1b: Key[Float] =
    KeyType.FloatKey.make("assemblyEventValue")
  private[framework] val eventKey2: Key[Struct] =
    KeyType.StructKey.make("assemblyEventStructValue")
  private[framework] val eventKey2b: Key[Struct] =
    KeyType.StructKey.make("assemblyEventStructValueB")
  private[framework] val eventKey3: Key[Int] =
    KeyType.IntKey.make("assemblyEventStructValue3")
  private[framework] val eventKey4: Key[Byte] =
    KeyType.ByteKey.make("assemblyEventStructValue4")
  private[framework] val eventName = EventName("myAssemblyEvent")
  private[framework] val basePosKey = CoordKey.make("BasePosition")

  // Actor to receive HCD events
  private def eventHandler(log: Logger,
                           publisher: EventPublisher,
                           baseEvent: SystemEvent): Behavior[Event] = {
    import Angle._
    import Coords._
    Behaviors.receive { (_, msg) =>
      msg match {
        case event: SystemEvent =>
          log.debug(s"received event: $event")
          event.get(hcdEventValueKey)
            .foreach { p =>
              val eventValue = p.head
              log.debug(s"Received event with event time: ${event.eventTime} with value: $eventValue")
              // fire a new event from the assembly based on the one from the HCD

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

              val e = baseEvent
                .copy(eventId = Id(), eventTime = UTCTime.now())
                .add(posParam)
                .add(eventKey1b.set(1.0f / eventValue, 2.0f, 3.0f))
                .add(
                  eventKey2b.set(
                    Struct()
                      .add(eventKey1.set(1.0f / eventValue))
                      .add(eventKey3.set(eventValue, 1, 2, 3)),
                    Struct()
                      .add(eventKey1.set(2.0f / eventValue))
                      .add(eventKey3.set(eventValue, 4, 5, 6))
                      .add(eventKey4.set(9.toByte, 10.toByte))
                  )
                )
              publisher.publish(e)
            }
          Behaviors.same
        case x =>
          log.error(s"Unexpected message: $x")
          Behaviors.same
      }
    }
  }

  // --- Alarms ---
  val alarmKey: AlarmKey = AlarmKey(Subsystem.CSW, "testComponent", "testAlarm")
}

//noinspection DuplicatedCode,SameParameterValue
class TestAssemblyWorker(ctx: ActorContext[TestAssemblyWorkerMsg],
                         cswCtx: CswContext)
    extends AbstractBehavior[TestAssemblyWorkerMsg](ctx) {

  import cswCtx._
  import TestAssemblyWorker._
  import scala.concurrent.duration._

  implicit val ec: ExecutionContextExecutor = ctx.executionContext
  implicit val timeout: Timeout = Timeout(5.seconds)
  implicit val sched: typed.Scheduler = ctx.system.scheduler

  private val log = loggerFactory.getLogger

  // Set when the location is received from the location service (below)
  private var testHcd: Option[CommandService] = None

  // Set when the database has been located
//  private var database: Option[DSLContext] = None

  // Event that the HCD publishes (must match the names defined by the publisher (TestHcd))
  private val hcdEventKey = EventKey(hcdPrefix, hcdEventName)

  private val obsId = ObsId("2023-Q22-4-33")
  private val encoderKey = KeyType.IntKey.make("encoder")
  private val filterKey = KeyType.StringKey.make("filter")
  private val prefix = Prefix(WFOS, "blue.filter")
  private val command = CommandName("myCommand")

  private def makeSetup(encoder: Int, filter: String): Setup = {
    val i1 = encoderKey.set(encoder)
    val i2 = filterKey.set(filter)
    Setup(prefix, command, Some(obsId)).add(i1).add(i2)
  }

  override def onMessage(
      msg: TestAssemblyWorkerMsg
  ): Behavior[TestAssemblyWorkerMsg] = {
    msg match {
      case Initialize(replyTo) =>
        async {
          startSubscribingToEvents()
          refreshAlarms()
          //          log.info(s"getting database")
          //          val dsl = await(initDatabaseTable())
          //          log.info(s"database = $dsl")
          //          ctx.self ! SetDatabase(dsl)
          replyTo.tell(())
        }.onComplete {
          case Success(_)  => log.info("Initialized")
          case Failure(ex) => log.error("Initialize failed", ex = ex)
        }
//      case SetDatabase(dsl) =>
//        database = Some(dsl)
      case Submit(runId, controlCommand) =>
        forwardCommandToHcd(runId, controlCommand)
      case Location(trackingEvent) =>
        log.info(s"Location updated: $trackingEvent")
        trackingEvent match {
          case LocationUpdated(location) =>
            testHcd = Some(CommandServiceFactory.make(location.asInstanceOf[AkkaLocation])(ctx.system))
            testHcd.get.subscribeCurrentState({ cs =>
              log.debug(s"Received current state from TestHcd: $cs")
            })
            val setup = makeSetup(0, "None")
            testHcd.get.submit(setup).onComplete {
              case Success(responses) => log.info(s"Initial Submit Test Passed: Responses = $responses")
              case Failure(ex)        => log.info(s"Initial Submit Test Failed: $ex")
            }
          case LocationRemoved(location) =>
              log.info(s"Location removed: $location")
//            testHcd = None
        }
      case RefreshAlarms =>
        refreshAlarms()
    }
    Behaviors.same
  }

  private def startSubscribingToEvents(): Unit = {
    val subscriber = eventService.defaultSubscriber
    val publisher = eventService.defaultPublisher
    val baseEvent =
      SystemEvent(hcdPrefix, eventName)
        .add(eventKey1.set(0))
        .add(eventKey2.set(Struct().add(eventKey1.set(0)).add(eventKey3.set(0))))

    val eventHandlerActor =
      ctx.spawn(eventHandler(log, publisher, baseEvent), "eventHandlerActor")
    subscriber.subscribeActorRef(Set(hcdEventKey), eventHandlerActor)
  }

  // For testing, forward command to HCD and complete this command when it completes
  private def forwardCommandToHcd(runId: Id, controlCommand: ControlCommand): Unit = {
    log.info(s"Forward command to hcd: $testHcd")
    testHcd.foreach { hcd =>
      val f = for {
        onewayResponse <-hcd.oneway(controlCommand)
        response <- hcd.submitAndWait(controlCommand)
      } yield {
        log.info(s"oneway response = $onewayResponse, submit response = $response")
        commandResponseManager.updateCommand(response)
      }
      f.recover {
        case ex =>
          val cmdStatus = Error(runId, ex.toString)
          commandResponseManager.updateCommand(cmdStatus)
      }
    }
  }

  private def refreshAlarms(): Unit = {
    //    alarmService.setSeverity(alarmKey, Okay)
    ctx.scheduleOnce(1.seconds, ctx.self, RefreshAlarms)
  }

//  private def initDatabaseTable(): Future[DSLContext] = async {
//    val dbFactory = new DatabaseServiceFactory(ctx.system)
//    val dsl = await(dbFactory.makeDsl(locationService, dbName))
//    dsl
//  }

}
