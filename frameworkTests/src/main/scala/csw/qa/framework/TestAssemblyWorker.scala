package csw.qa.framework

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import TestAssemblyWorker._
import akka.actor.Scheduler
import akka.util.Timeout
import csw.alarm.api.models.Key.AlarmKey
import csw.command.api.scaladsl.CommandService
import csw.command.client.CommandServiceFactory
import csw.database.DatabaseServiceFactory
import csw.event.api.scaladsl.EventPublisher
import csw.framework.models.CswContext
import csw.location.api.models.{
  AkkaLocation,
  LocationRemoved,
  LocationUpdated,
  TrackingEvent
}
import csw.logging.api.scaladsl.Logger
import csw.params.commands.CommandResponse.Error
import csw.params.commands.{ControlCommand, Setup}
import csw.params.core.generics.{Key, KeyType}
import csw.params.core.models.{Id, Prefix, Struct, Subsystem}
import csw.params.events._
import csw.time.core.models.UTCTime
import org.jooq.DSLContext

import scala.async.Async.{async, await}
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object TestAssemblyWorker {

  sealed trait TestAssemblyWorkerMsg

  case class Initialize(replyTo: ActorRef[Unit]) extends TestAssemblyWorkerMsg

  case class Submit(controlCommand: ControlCommand)
    extends TestAssemblyWorkerMsg

  case class Location(trackingEvent: TrackingEvent)
    extends TestAssemblyWorkerMsg

  case object RefreshAlarms extends TestAssemblyWorkerMsg

  case class SetDatabase(dsl: DSLContext) extends TestAssemblyWorkerMsg

  def make(cswCtx: CswContext): Behavior[TestAssemblyWorkerMsg] = {
    Behaviors.setup(ctx ⇒ new TestAssemblyWorker(ctx, cswCtx))
  }

  private val dbName = "postgres"

  // --- Events ---

  // Key for HCD events
  private val hcdEventValueKey: Key[Int] = KeyType.IntKey.make("hcdEventValue")
  private val hcdEventName = EventName("myHcdEvent")
  private val hcdPrefix = Prefix("test.hcd")

  // Dummy key for publishing events from assembly
  private[framework] val eventKey: Key[Float] =
    KeyType.FloatKey.make("assemblyEventValue")
  private[framework] val eventKey2: Key[Struct] =
    KeyType.StructKey.make("assemblyEventStructValue")
  private[framework] val eventKey3: Key[Int] =
    KeyType.IntKey.make("assemblyEventStructValue3")
  private[framework] val eventKey4: Key[Byte] =
    KeyType.ByteKey.make("assemblyEventStructValue4")
  private[framework] val eventName = EventName("myAssemblyEvent")

  // Actor to receive HCD events
  private def eventHandler(log: Logger,
                           publisher: EventPublisher,
                           baseEvent: SystemEvent): Behavior[Event] =
    Behaviors.receive { (_, msg) =>
      msg match {
        case e: SystemEvent =>
          e.get(hcdEventValueKey)
            .foreach { p =>
              val eventValue = p.head
              log.info(s"Received event with value: $eventValue")
              // fire a new event from the assembly based on the one from the HCD
              val e = baseEvent
                .copy(eventId = Id(), eventTime = UTCTime.now())
                .add(eventKey.set(1.0f / eventValue, 2.0f, 3.0f))
                .add(eventKey2.set(
                  Struct()
                  .add(eventKey.set(1.0f / eventValue))
                  .add(eventKey3.set(eventValue, 1, 2, 3)),
                  Struct()
                  .add(eventKey.set(2.0f / eventValue))
                  .add(eventKey3.set(eventValue, 4, 5, 6))
                  .add(eventKey4.set(9.toByte, 10.toByte),
                )))
              publisher.publish(e)
            }
          Behaviors.same
        case _ => throw new RuntimeException("Expected SystemEvent")
      }
    }

  // --- Alarms ---
  val alarmKey = AlarmKey(Subsystem.TEST, "testComponent", "testAlarm")
}

class TestAssemblyWorker(ctx: ActorContext[TestAssemblyWorkerMsg],
                         cswCtx: CswContext)
  extends AbstractBehavior[TestAssemblyWorkerMsg] {

  import cswCtx._
  import TestAssemblyWorker._

  implicit val ec: ExecutionContextExecutor = ctx.executionContext
  implicit val timeout: Timeout = Timeout(3.seconds)
  implicit val sched: Scheduler = ctx.system.scheduler

  private val log = loggerFactory.getLogger

  // Set when the location is received from the location service (below)
  private var testHcd: Option[CommandService] = None

  // Set when the database has been located
  private var database: Option[DSLContext] = None

  // Event that the HCD publishes (must match the names defined by the publisher (TestHcd))
  private val hcdEventKey = EventKey(hcdPrefix, hcdEventName)

  override def onMessage(
                          msg: TestAssemblyWorkerMsg): Behavior[TestAssemblyWorkerMsg] = {
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
          case Success(_) => log.info("Initialized")
          case Failure(ex) => log.error("Initialize failed", ex = ex)
        }
      case SetDatabase(dsl) =>
        database = Some(dsl)
      case Submit(controlCommand) =>
        forwardCommandToHcd(controlCommand)
      case Location(trackingEvent) =>
        trackingEvent match {
          case LocationUpdated(location) =>
            testHcd = Some(
              CommandServiceFactory.make(location.asInstanceOf[AkkaLocation])(
                ctx.system))
          case LocationRemoved(_) =>
            testHcd = None
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
      SystemEvent(componentInfo.prefix, eventName)
        .add(eventKey.set(0))
        .add(eventKey2.set(Struct().add(eventKey.set(0)).add(eventKey3.set(0))))

    val eventHandlerActor =
      ctx.spawn(eventHandler(log, publisher, baseEvent), "eventHandlerActor")
    subscriber.subscribeActorRef(Set(hcdEventKey), eventHandlerActor)
  }

  // For testing, forward command to HCD and complete this command when it completes
  private def forwardCommandToHcd(controlCommand: ControlCommand): Unit = {
    implicit val scheduler: Scheduler = ctx.system.scheduler
    implicit val timeout: Timeout = Timeout(3.seconds)
    testHcd.map { hcd =>
      val setup = Setup(controlCommand.source,
        controlCommand.commandName,
        controlCommand.maybeObsId,
        controlCommand.paramSet)
      cswCtx.commandResponseManager.addSubCommand(controlCommand.runId,
        setup.runId)

      val f = for {
        response <- hcd.submit(setup)
      } yield {
        log.info(s"response = $response")
        commandResponseManager.updateSubCommand(response)
      }
      f.recover {
        case ex =>
          val cmdStatus = Error(setup.runId, ex.toString)
          commandResponseManager.updateSubCommand(cmdStatus)
      }
    }
  }

  private def refreshAlarms(): Unit = {
    //    alarmService.setSeverity(alarmKey, Okay)
    ctx.scheduleOnce(1.seconds, ctx.self, RefreshAlarms)
  }

  private def initDatabaseTable(): Future[DSLContext] = async {
    val dbFactory = new DatabaseServiceFactory(ctx.system)
    val dsl = await(dbFactory.makeDsl(locationService, dbName))
    dsl
  }

}
