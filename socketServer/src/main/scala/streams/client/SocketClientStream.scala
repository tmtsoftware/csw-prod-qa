package streams.client

import akka.stream.OverflowStrategy
import akka.stream.scaladsl.*
import akka.util.{ByteString, Timeout}
import akka.stream.scaladsl.Framing

import scala.concurrent.{ExecutionContext, Future}
import akka.NotUsed
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, SpawnProtocol}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.scaladsl.adapter.*
import akka.actor.typed.scaladsl.AskPattern.*
import csw.logging.client.commons.AkkaTypedExtension.UserActorFactory
import streams.shared.Command
import SocketClientActor.*

import scala.concurrent.duration.DurationInt

private[client] object SocketClientActor {
  sealed trait SocketClientActorMessage
  // Queue command to server
  case class SetResponse(id: Int, resp: String)              extends SocketClientActorMessage
  case class GetResponse(id: Int, replyTo: ActorRef[String]) extends SocketClientActorMessage

  def behavior(): Behavior[SocketClientActorMessage] =
    Behaviors.setup[SocketClientActorMessage](new SocketClientActor(_))
}

private[client] class SocketClientActor(ctx: ActorContext[SocketClientActorMessage])
    extends AbstractBehavior[SocketClientActorMessage](ctx) {
  private var responseMap = Map.empty[Int, String]
  private var clientMap   = Map.empty[Int, ActorRef[String]]
  override def onMessage(msg: SocketClientActorMessage): Behavior[SocketClientActorMessage] = {
    msg match {
      case SetResponse(id, resp) =>
        if (clientMap.contains(id)) {
          clientMap(id) ! resp
          clientMap = clientMap - id
        } else {
          responseMap = responseMap + (id -> resp)
        }
      case GetResponse(id, replyTo) =>
        if (responseMap.contains(id)) {
          replyTo ! responseMap(id)
          responseMap = responseMap - id
        } else {
          clientMap = clientMap + (id -> replyTo)
        }
    }
    Behaviors.same
  }
}

class SocketClientStream(host: String = "127.0.0.1", port: Int = 8888)(implicit system: ActorSystem[SpawnProtocol.Command]) {
  implicit val ec: ExecutionContext = system.executionContext
  private val connection            = Tcp()(system.toClassic).outgoingConnection(host, port)

  private val sourceDecl      = Source.queue[String](bufferSize = 2, OverflowStrategy.backpressure)
  private val (queue, source) = sourceDecl.preMaterialize()
  private val clientActor     = system.spawn(SocketClientActor.behavior(), "SocketClientActor")

  private val sink = Sink.foreach[String] { s =>
    val (id, resp) = Command.parse(s)
    clientActor ! SetResponse(id, resp)
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
    .via(clientFlow)
    .via(parser)

  private val connectedFlow = connection.join(flow).run()

  // Send message to server and return the response
  def send(msg: String): Future[String] = {
    implicit val timeout: Timeout = Timeout(5.seconds)
    queue.offer(msg)
    val (id, _) = Command.parse(msg)
    clientActor.ask(GetResponse(id, _))
  }

  def terminate(): Unit = {
    queue.offer("q")
  }
}

object SocketClientStream extends App {
  implicit val system: ActorSystem[SpawnProtocol.Command] = ActorSystem(SpawnProtocol(), "SocketServerStream")
  import system.*

  // TODO: Add host, port options
  val client = new SocketClientStream()

  val f1 = client.send("1 DELAY 2000").map(s => println(s"resp1: $s"))
  val f2 = client.send("2 DELAY 1000").map(s => println(s"resp2: $s"))
  val f3 = client.send("3 DELAY 500").map(s => println(s"resp3: $s"))

  for {
    resp1 <- f1
    resp2 <- f2
    resp3 <- f3
  } {
    client.terminate()
    system.terminate()
  }
}
