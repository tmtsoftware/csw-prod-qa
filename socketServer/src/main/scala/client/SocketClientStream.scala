package client

import akka.actor.ActorSystem
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.stream.OverflowStrategy

import java.util.concurrent.atomic.AtomicReference
import akka.stream.scaladsl.Tcp.*
import akka.stream.scaladsl.*
import akka.util.ByteString
import akka.stream.scaladsl.Framing

import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import scala.io.StdIn.readLine
import SocketClientActor.*
import akka.NotUsed

import scala.collection.mutable

class SocketClientStream(host: String = "127.0.0.1", port: Int = 8888)(implicit system: ActorSystem) {
  implicit val ec: ExecutionContext = system.dispatcher
  private val connection            = Tcp().outgoingConnection(host, port)

  private val sourceDecl = Source.queue[String](bufferSize = 2, OverflowStrategy.backpressure)
  private val (queue, source) = sourceDecl.preMaterialize()

  private val sink = Sink.foreach[String]{ s =>
    println(s"XXX sink: response from server: $s")
  }
  private val clientFlow = Flow.fromSinkAndSource(sink, source)

  private val parser: Flow[String, ByteString, NotUsed] = {
    Flow[String]
      .takeWhile(_ != "q")
      .concat(Source.single("BYE"))
      .map(elem => ByteString(s"$elem\n"))
  }

  private val flow = Flow[ByteString]
    .via(Framing.delimiter(ByteString("\n"), maximumFrameLength = 256, allowTruncation = true))
    .map(_.utf8String)
//    .map { bs =>
//      println(s"XXX Server response: ${bs.utf8String}")
//      bs.utf8String
//    }
//    .map(_ => readLine("> "))
//    .map(x => "IGNORE")
    .via(clientFlow)
    .via(parser)


//  val (connected, done) = clientFlow.viaMat(Tcp(system)
//    .outgoingConnection(host, port))(Keep.right)
//    .toMat(Sink.ignore)(Keep.both).run()


    private val connectedFlow = connection.join(flow).run()

  // Send message to server and return the response
  def send(msg: String): String = {
    queue.offer(msg)
    "XXX TODO"
  }
}

object SocketClientStream extends App {
  implicit val system: ActorSystem = ActorSystem("SocketServerStream")
  // TODO: Add host, port options
  val client = new SocketClientStream()
  Thread.sleep(100)
  println(s"Hello -> ${client.send("Hello")}")
  Thread.sleep(100)
  println(s"World -> ${client.send("World")}")
  Thread.sleep(100)
  println(s"All -> ${client.send("All")}")
//  println(s"q -> ${client.send("q")}")
//  println(s"Last -> ${client.send("Last")}")
}
