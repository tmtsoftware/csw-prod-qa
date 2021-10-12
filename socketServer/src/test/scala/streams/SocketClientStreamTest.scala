package streams

import akka.actor.typed.{ActorSystem, SpawnProtocol}
import org.scalatest.funsuite.AnyFunSuite
import streams.client.SocketClientStream
import streams.server.SocketServerStream
import akka.actor.typed.scaladsl.adapter.TypedActorSystemOps
import akka.util.Timeout
import streams.shared.SocketMessage

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.*

class SocketClientStreamTest extends AnyFunSuite {
  implicit val system: ActorSystem[SpawnProtocol.Command] = ActorSystem(SpawnProtocol(), "SocketServerStream")
  import system.*
  implicit val timout: Timeout = Timeout(5.seconds)

  // Start the server
  // XXX TODO FIXME: Use typed system
  new SocketServerStream()(system.toClassic)

  test("Basic test") {

    val client1 = new SocketClientStream()
    val client2 = new SocketClientStream()
    val client3 = new SocketClientStream()

    def showResult(msg: SocketMessage): SocketMessage = {
      println(msg)
      msg
    }
                                "1234567890"
    val f1 = client1.send("DELAY 2000").map(showResult)
    val f2 = client2.send("DELAY 1000").map(showResult)
    val f3 = client3.send("DELAY  500").map(showResult)
    val f4 = client1.send("DELAY  200").map(showResult)
    val f5 = client2.send("IMMEDIATE ").map(showResult)

    val f = for {
      resp1 <- f1
      resp2 <- f2
      resp3 <- f3
      resp4 <- f4
      resp5 <- f5
    } yield {
//      client1.terminate()
      List(resp1, resp2, resp3, resp4, resp5)
    }
    val list = Await.result(f, 6.seconds)
//    assert(list == List("1: COMPLETED", "2: COMPLETED", "3: COMPLETED", "4: COMPLETED", "5: COMPLETED"))
  }

//  test("Test with 492 clients") {
//    val segments = (1 to 492).toList
//    val clientPairs = segments.map(i => (i, new SocketClientStream()))
//    val fList = clientPairs.map(p => p._2.send(p._1, "IMMEDIATE"))
//    assert(Await.result(Future.sequence(fList).map(_.forall(_ == "COMPLETED")), timout.duration))
//  }
}
