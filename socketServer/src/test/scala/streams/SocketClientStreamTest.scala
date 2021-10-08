package streams

import akka.actor.typed.{ActorSystem, SpawnProtocol}
import org.scalatest.funsuite.AnyFunSuite
import streams.client.SocketClientStream
import streams.server.SocketServerStream
import akka.actor.typed.scaladsl.adapter.TypedActorSystemOps
import akka.util.Timeout

import scala.concurrent.Await
import scala.concurrent.duration.*

class SocketClientStreamTest extends AnyFunSuite {
  implicit val system: ActorSystem[SpawnProtocol.Command] = ActorSystem(SpawnProtocol(), "SocketServerStream")
  import system.*
  implicit val timout: Timeout = Timeout(5.seconds)

  // Start the server
  // XXX TODO FIXME: Use typed system
  new SocketServerStream()(system.toClassic)

  test("Basic test") {

    val client1 = new SocketClientStream("client1")
    val client2 = new SocketClientStream("client2")
    val client3 = new SocketClientStream("client3")

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
}
