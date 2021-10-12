package streams

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, Props, Scheduler, SpawnProtocol}
import org.scalatest.funsuite.AnyFunSuite
import streams.client.{SocketClientStream, SpawnHelper}
import streams.server.SocketServerStream
import akka.actor.typed.scaladsl.adapter.TypedActorSystemOps
import akka.util.Timeout
import csw.logging.client.commons.AkkaTypedExtension.UserActorFactory
import akka.actor.typed.scaladsl.AskPattern.*

import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import scala.concurrent.duration.*
import TestActor.*

private object TestActor {
  sealed trait TestMessages
  case class Start(actorRef: ActorRef[Boolean]) extends TestMessages
  case object Stop                              extends TestMessages

  def behavior(name: String): Behavior[TestMessages] =
    Behaviors.setup[TestMessages](new TestActor(name, _))
}

private class TestActor(name: String, ctx: ActorContext[TestMessages]) extends AbstractBehavior[TestMessages](ctx) {
  private val spawnHelper2 = new SpawnHelper {
    def spawn[U](behavior: Behavior[U], name: String, props: Props = Props.empty): ActorRef[U] = {
      ctx.spawn(behavior, name, props)
    }
  }

  override def onMessage(msg: TestMessages): Behavior[TestMessages] = {
    implicit val timeout: Timeout              = Timeout(10.seconds)
    implicit val system: ActorSystem[Nothing]  = ctx.system
    implicit val exc: ExecutionContextExecutor = system.executionContext
    implicit val sched: Scheduler              = ctx.system.scheduler

    msg match {
      case Start(replyTo) =>
        val segments    = (1 to 492).toList
        val clientPairs = segments.map(i => (i, new SocketClientStream(spawnHelper2, s"client_$i")))
        val fList       = clientPairs.map(p => p._2.send(p._1, "IMMEDIATE"))
        assert(Await.result(Future.sequence(fList).map(_.forall(_ == "COMPLETED")), timeout.duration))
        replyTo ! true
        Behaviors.same

      case Stop =>
        Behaviors.stopped
    }
  }
}

class SocketClientStreamTest extends AnyFunSuite {
  implicit val system: ActorSystem[SpawnProtocol.Command] = ActorSystem(SpawnProtocol(), "SocketServerStream")
  implicit val ece: ExecutionContextExecutor              = system.executionContext
  implicit val timout: Timeout                            = Timeout(5.seconds)

  // Start the server
  // XXX TODO FIXME: Use typed system
  new SocketServerStream()(system.toClassic)

  private val spawnHelper = new SpawnHelper {
    def spawn[U](behavior: Behavior[U], name: String, props: Props = Props.empty): ActorRef[U] =
      system.spawn(behavior, name, props)
  }

  test("Basic test") {

    val client1 = new SocketClientStream(spawnHelper, "client1")
    val client2 = new SocketClientStream(spawnHelper, "client2")
    val client3 = new SocketClientStream(spawnHelper, "client3")

    def showResult(id: Int, s: String): String = {
      val result = s"$id: $s"
      println(result)
      result
    }

    val f1 = client1.send(1, "DELAY 2000").map(showResult(1, _))
    val f2 = client2.send(2, "DELAY 1000").map(showResult(2, _))
    val f3 = client3.send(3, "DELAY 500").map(showResult(3, _))
    val f4 = client1.send(4, "DELAY 200").map(showResult(4, _))
    val f5 = client2.send(5, "IMMEDIATE").map(showResult(5, _))

    val f = for {
      resp1 <- f1
      resp2 <- f2
      resp3 <- f3
      resp4 <- f4
      resp5 <- f5
    } yield {
      client1.terminate()
      List(resp1, resp2, resp3, resp4, resp5)
    }
    val list = Await.result(f, 3.seconds)
    assert(list == List("1: COMPLETED", "2: COMPLETED", "3: COMPLETED", "4: COMPLETED", "5: COMPLETED"))
  }

  test("Test with actor") {
    val actorRef = system.spawn(TestActor.behavior("test"), "TestActor")
    assert(Await.result(actorRef.ask(Start), 10.seconds))
    actorRef ! Stop
  }

//  test("Test with 492 clients") {
//    val segments    = (1 to 492).toList
//    val clientPairs = segments.map(i => (i, new SocketClientStream(spawnHelper, s"client$i")))
//    val fList       = clientPairs.map(p => p._2.send(p._1, "IMMEDIATE"))
//    assert(Await.result(Future.sequence(fList).map(_.forall(_ == "COMPLETED")), timout.duration))
//  }
}
