package client

import akka.actor.ActorSystem
import akka.stream.OverflowStrategy

import java.util.concurrent.atomic.AtomicReference
import akka.stream.scaladsl.Tcp.*
import akka.stream.scaladsl.*
import akka.util.ByteString
import akka.stream.scaladsl.Framing

import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import scala.io.StdIn.readLine

class SocketClientStream(host: String = "127.0.0.1", port: Int = 8888)(implicit system: ActorSystem) {
  implicit val ec: ExecutionContext = system.dispatcher
  private val connection            = Tcp().outgoingConnection(host, port)

  private val parser =
    Flow[String].takeWhile(_ != "q").concat(Source.single("BYE")).map(elem => ByteString(s"$elem\n"))

  private val sourceDecl = Source.queue[String](bufferSize = 2, OverflowStrategy.backpressure)
  private val (queue, source) = sourceDecl.preMaterialize()

  private val flow = Flow[ByteString]
    .via(Framing.delimiter(ByteString("\n"), maximumFrameLength = 256, allowTruncation = true))
    .map(_.utf8String)
    .map(text => println("XXX Server response: " + text))
    .map(_ => readLine("> "))
    .via(parser)

  private val connectedFlow = connection.join(flow).run()

//  private val queue = Source
//    .queue[ByteString](100)
//    .via(flow)
//    .toMat(Sink.foreach(x => println(s"completed $x")))(Keep.left)
//    .run()

  // Send message to server and return the response
  def send(msg: String): String = {
    queue.offer(ByteString(msg))
    "XXX TODO"
  }
}

object SocketClientStream extends App {
  implicit val system: ActorSystem = ActorSystem("SocketServerStream")
  // TODO: Add host, port options
  val client = new SocketClientStream()
  Thread.sleep(1000)
  println(s"Hello -> ${client.send("Hello")}")
  println(s"World -> ${client.send("World")}")
}
