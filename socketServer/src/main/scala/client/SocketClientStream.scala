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

import scala.collection.mutable

private[client] object SocketClientActor {
  sealed trait SocketClientActorMessage
  // Queue command to server
  case class Command(s: String) extends SocketClientActorMessage
  // Get next command (replies with command string)
  case class GetNextCommand(replyTo: ActorRef[String]) extends SocketClientActorMessage
  // Response from server
  case class Response(s: String) extends SocketClientActorMessage
}

private[client] class SocketClientActor(ctx: ActorContext[SocketClientActorMessage])
    extends AbstractBehavior[SocketClientActorMessage](ctx) {
  private val commandQueue = mutable.ArrayDeque.empty[String]
  override def onMessage(msg: SocketClientActorMessage): Behavior[SocketClientActorMessage] = {
    msg match {
      case Command(s) =>
        commandQueue += s
      case GetNextCommand(replyTo) =>
        if (commandQueue.nonEmpty)
          replyTo ! commandQueue.removeHead()
      case Response(s) =>
    }
    Behaviors.same
  }
}

class SocketClientStream(host: String = "127.0.0.1", port: Int = 8888)(implicit system: ActorSystem) {
  implicit val ec: ExecutionContext = system.dispatcher
  private val connection            = Tcp().outgoingConnection(host, port)

  private val parser =
    Flow[String].takeWhile(_ != "q").concat(Source.single("BYE")).map(elem => ByteString(s"$elem\n"))

  private val flow = Flow[ByteString]
    .via(Framing.delimiter(ByteString("\n"), maximumFrameLength = 256, allowTruncation = true))
    .map(_.utf8String)
    .map(text => println("XXX Server response: " + text))
    .map(_ => readLine("> "))
    .via(parser)

  private val connectedFlow = connection.join(flow).run()

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
