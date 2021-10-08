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

    // TODO: Add host, port options
    val client1 = new SocketClientStream()
    val client2 = new SocketClientStream()
    val client3 = new SocketClientStream()

    val f1 = client1.send(1, "DELAY 2000").map(s => println(s"resp1: $s"))
    val f2 = client2.send(2, "DELAY 1000").map(s => println(s"resp2: $s"))
    val f3 = client3.send(3, "DELAY 500").map(s => println(s"resp3: $s"))

    val f = for {
      resp1 <- f1
      resp2 <- f2
      resp3 <- f3
    } yield {
      client1.terminate()
    }
    Await.ready(f, 3.seconds)
    System.exit(0)
  }
}
