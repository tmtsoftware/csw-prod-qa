package csw.qa.framework

import java.net.InetAddress

import akka.actor.{ActorRefFactory, ActorSystem, Scheduler}
import akka.stream.ActorMaterializer
import akka.typed.{ActorRef, Behavior}
import akka.typed.scaladsl.{Actor, ActorContext}
import csw.services.location.scaladsl.LocationServiceFactory
import csw.services.logging.scaladsl.{GenericLoggerFactory, LoggingSystemFactory}
import akka.typed.scaladsl.adapter._
import akka.util.Timeout
import csw.messages.ComponentMessage
import csw.messages.ccs.commands.{CommandName, Setup}
import csw.messages.location.ComponentType.Assembly
import csw.messages.location.Connection.AkkaConnection
import csw.messages.location._
import csw.messages.params.generics.KeyType
import csw.messages.params.models.{ObsId, Prefix}
import csw.messages.params.models.Units.degree
import csw.services.location.commons.ClusterAwareSettings

import scala.concurrent.duration._
import scala.util.{Failure, Success}
import csw.messages.ccs.commands.ActorRefExts.RichComponentActor

// A client to test locating and communicating with the Test assembly
object TestAssemblyClient extends App {

  //  private val system = ActorSystemFactory.remote
  private val system: ActorSystem = ClusterAwareSettings.system
  implicit def actorRefFactory: ActorRefFactory = system
  private val locationService = LocationServiceFactory.withSystem(system)
  private val host = InetAddress.getLocalHost.getHostName
  LoggingSystemFactory.start("TestServiceClientApp", "0.1", host, system)
  implicit val mat: ActorMaterializer = ActorMaterializer()
  private val log = GenericLoggerFactory.getLogger
  log.info("Starting TestAssemblyClient")
  system.spawn(initialBehavior, "TestAssemblyClient")

  def initialBehavior: Behavior[TrackingEvent] =
    Actor.deferred { ctx =>
      val connection = AkkaConnection(ComponentId("TestAssembly", Assembly))
      locationService.subscribe(connection, { loc =>
        ctx.self ! loc
      })
      subscriberBehavior
    }

  def subscriberBehavior: Behavior[TrackingEvent] = {
    Actor.immutable[TrackingEvent] { (ctx, msg) =>
      msg match {
        case LocationUpdated(loc) =>
          log.info(s"LocationUpdated: $loc")
          interact(ctx, loc.asInstanceOf[AkkaLocation].componentRef())
        case LocationRemoved(loc) =>
          log.info(s"LocationRemoved: $loc")
      }
      Actor.same
    } onSignal {
      case (ctx, x) =>
        log.info(s"${ctx.self} received signal $x")
        Actor.stopped
    }
  }

  private def interact(ctx: ActorContext[TrackingEvent], assembly: ActorRef[ComponentMessage]): Unit = {
    val k1 = KeyType.IntKey.make("encoder")
    val k2 = KeyType.StringKey.make("filter")
    val i1 = k1.set(22, 33, 44)
    val i2 = k2.set("a", "b", "c").withUnits(degree)
    val setup = Setup(Prefix("wfos.blue.filter"), CommandName("filter"), Some(ObsId("2023-Q22-4-33"))).add(i1).add(i2)
    log.info(s"Sending setup to assembly: $setup")
    implicit val timeout: Timeout = Timeout(3.seconds)
    implicit val scheduler: Scheduler = ctx.system.scheduler
    import ctx.executionContext
    assembly.submit(setup).onComplete {
      case Success(resp) =>
        log.info(s"Assembly responded with $resp")
        // XXX TODO FIXME: This will only get the current response status?
        assembly.getCommandResponse(resp.runId).onComplete(r => log.info(s"Command Response: $r"))
      case Failure(ex) =>
        log.error("Failed to send command to TestAssembly", ex = ex)
    }
  }
}

