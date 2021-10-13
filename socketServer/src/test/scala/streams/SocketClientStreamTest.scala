package streams

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, Scheduler, SpawnProtocol}
import org.scalatest.funsuite.AnyFunSuite
import streams.client.SocketClientStream
import streams.server.SocketServerStream
import akka.actor.typed.scaladsl.adapter.TypedActorSystemOps
import akka.util.Timeout
import csw.logging.client.commons.AkkaTypedExtension.UserActorFactory
import akka.actor.typed.scaladsl.AskPattern.*

import scala.concurrent.{Await, ExecutionContextExecutor, Future}
import scala.concurrent.duration.*
import TestActor.*
import streams.shared.SocketMessage

private object TestActor {
  sealed trait TestMessages
  case class Start(actorRef: ActorRef[Boolean]) extends TestMessages
  case object Stop                              extends TestMessages

  def behavior(): Behavior[TestMessages] =
    Behaviors.setup[TestMessages](new TestActor(_))
}

private class TestActor(ctx: ActorContext[TestMessages]) extends AbstractBehavior[TestMessages](ctx) {
  override def onMessage(msg: TestMessages): Behavior[TestMessages] = {
    implicit val timeout: Timeout              = Timeout(30.seconds)
    implicit val system: ActorSystem[Nothing]  = ctx.system
    implicit val exc: ExecutionContextExecutor = system.executionContext
    implicit val sched: Scheduler              = ctx.system.scheduler

    msg match {
      case Start(replyTo) =>
        val segments    = (1 to 492).toList
        val clientPairs = segments.map(i => (i, SocketClientStream(ctx, s"client_$i")))
        val fList       = clientPairs.map(p => p._2.send(s"DELAY ${p._1 * 10}"))
        assert(Await.result(Future.sequence(fList).map(_.forall(_.cmd == "COMPLETED")), timeout.duration))
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
  implicit val timout: Timeout                            = Timeout(10.seconds)

  // Start the server
  // XXX TODO FIXME: Use typed system
  new SocketServerStream()(system.toClassic)

  test("Basic test") {
    val client1 = SocketClientStream.withSystem("client1")
    val client2 = SocketClientStream.withSystem("client2")
    val client3 = SocketClientStream.withSystem("client3")

    def showResult(msg: SocketMessage): SocketMessage = {
      println(s"XXX showResult: $msg")
      msg
    }
    val f0 = client1.send("IMMEDIATE").map(showResult)
    val f1 = client1.send("DELAY 2000").map(showResult)
    val f2 = client2.send("DELAY 1000").map(showResult)
    val f3 = client3.send("DELAY 500").map(showResult)
    val f4 = client1.send("DELAY 200").map(showResult)
    val f5 = client2.send("IMMEDIATE").map(showResult)

    val f = for {
      resp0 <- f0
      resp1 <- f1
      resp2 <- f2
      resp3 <- f3
      resp4 <- f4
      resp5 <- f5
    } yield {
      client1.terminate()
      client2.terminate()
      client3.terminate()
      List(resp0, resp1, resp2, resp3, resp4, resp5)
    }
    val list = Await.result(f, 30.seconds)
    println(s"XXX test1 result = $list")
    assert(list.forall(_.cmd.endsWith(" COMPLETED")))
  }

  test("Test with actor") {
    val actorRef = system.spawn(TestActor.behavior(), "TestActor")
    assert(Await.result(actorRef.ask(Start), 30.seconds))
    actorRef ! Stop
  }
}
