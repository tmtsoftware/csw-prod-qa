package streams.client

import akka.stream.OverflowStrategy
import akka.stream.scaladsl.*
import akka.util.{ByteString, Timeout}
import akka.stream.scaladsl.Framing

import scala.concurrent.{Await, ExecutionContext, Future}
import akka.NotUsed
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, SpawnProtocol}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.scaladsl.adapter.*
import akka.actor.typed.scaladsl.AskPattern.*
import csw.logging.client.commons.AkkaTypedExtension.UserActorFactory
import streams.shared.SocketMessage
import SocketClientActor.*
import streams.shared.SocketMessage.{CMD_TYPE, MessageId, MsgHdr, SourceId}

import scala.concurrent.duration.*

// Actor used to keep track of the server responses and match them with ids
private[client] object SocketClientActor {
  sealed trait SocketClientActorMessage
  // Sets the response to the command with the given seqNo
  case class SetResponse(resp: SocketMessage) extends SocketClientActorMessage
  // Gets the response for the command with the given seqNo
  case class GetResponse(seqNo: Int, replyTo: ActorRef[SocketMessage]) extends SocketClientActorMessage
  // Gets the next sequence number for sending a command
  case class GetSeqNo(replyTo: ActorRef[Int]) extends SocketClientActorMessage
  // Stop the actor
  case object Stop extends SocketClientActorMessage

  def behavior(): Behavior[SocketClientActorMessage] =
    Behaviors.setup[SocketClientActorMessage](new SocketClientActor(_))
}

private[client] class SocketClientActor(ctx: ActorContext[SocketClientActorMessage])
    extends AbstractBehavior[SocketClientActorMessage](ctx) {
  // Maps command seqNo to server response
  private var responseMap = Map.empty[Int, SocketMessage]

  // Maps command seqNo to the actor waiting to get the response
  private var clientMap = Map.empty[Int, ActorRef[SocketMessage]]

  // Used to generate sequence numbers
  private var seqNo = 0

  override def onMessage(msg: SocketClientActorMessage): Behavior[SocketClientActorMessage] = {
    msg match {
      case SetResponse(resp) =>
        println(s"XXX SetResponse($resp)")
        if (clientMap.contains(resp.hdr.seqNo)) {
          clientMap(resp.hdr.seqNo) ! resp
          clientMap = clientMap - resp.hdr.seqNo
        } else {
          responseMap = responseMap + (resp.hdr.seqNo -> resp)
        }
        Behaviors.same

      case GetResponse(seqNo, replyTo) =>
        println(s"XXX GetResponse($seqNo)")
        if (responseMap.contains(seqNo)) {
          replyTo ! responseMap(seqNo)
          responseMap = responseMap - seqNo
        } else {
          clientMap = clientMap + (seqNo -> replyTo)
        }
        Behaviors.same

      case GetSeqNo(replyTo) =>
        seqNo = seqNo + 1
        replyTo ! seqNo
        Behaviors.same

      case Stop =>
        Behaviors.stopped
    }
  }
}

class SocketClientStream(host: String = "127.0.0.1", port: Int = 8888)(
    implicit system: ActorSystem[SpawnProtocol.Command]
) {
  implicit val ec: ExecutionContext = system.executionContext
  private val connection            = Tcp()(system.toClassic).outgoingConnection(host, port)

  // Use a queue to feed commands to the stream
  private val (queue, source) = Source.queue[ByteString](bufferSize = 2, OverflowStrategy.backpressure).preMaterialize()

  // An actor to manage the server responses and match them to command ids
  private val clientActor = system.spawn(SocketClientActor.behavior(), "SocketClientActor")

  // A sink for responses from the server
  private val sink = Sink.foreach[ByteString] { bs =>
    println(s"XXX sink got msg")
    val resp = SocketMessage.parse(bs)
    println(s"XXX sink got $resp")
    clientActor ! SetResponse(resp)
  }

  // Used to feed commands to the stream
  private val clientFlow = Flow.fromSinkAndSource(sink, source)

  // Quits if "q" received (sending "BYE" to server)
  private val parser: Flow[ByteString, ByteString, NotUsed] = {
    Flow[ByteString]
      .takeWhile(_ != ByteString("q"))
      .concat(Source.single(ByteString("BYE\n")))
  }

  // Commands are assumed to be terminated with "\n" (XXX TODO: Check this)
  private val flow = Flow[ByteString]
    .via(Framing.delimiter(ByteString("\n"), maximumFrameLength = 256, allowTruncation = true))
    .via(clientFlow)
    .via(parser)

  private val connectedFlow = connection.join(flow).run()
  connectedFlow.foreach { c =>
    println(s"local addr: ${c.localAddress}, remote addr: ${c.remoteAddress}")
  }

  /**
   * Sends a command to the server and returns the response
   * @return the future response from the server
   */
  private def send(cmd: SocketMessage)(implicit timeout: Timeout): Future[SocketMessage] = {
    queue.offer(cmd.toByteString)
    clientActor.ask(GetResponse(cmd.hdr.seqNo, _))
  }

  /**
   * Sends a command to the server and returns the response
   * @param msg the command text
   * @return the future response from the server
   */
  def send(msg: String, msgId: MessageId = CMD_TYPE, srcId: SourceId = SourceId(0))(
      implicit timeout: Timeout
  ): Future[SocketMessage] = {
    clientActor.ask(GetSeqNo).flatMap { seqNo =>
      send(SocketMessage(MsgHdr(msgId, srcId, msgLen = msg.length, seqNo = seqNo), msg + "\n"))
    }
  }

  /**
   * Terminates the stream
   */
  def terminate(): Unit = {
    queue.offer(ByteString("q"))
    clientActor ! Stop
  }
}

object SocketClientStream extends App {
  implicit val system: ActorSystem[SpawnProtocol.Command] = ActorSystem(SpawnProtocol(), "SocketServerStream")
  import system.*
  implicit val timout: Timeout = Timeout(5.seconds)
  val client = new SocketClientStream()
  val resp = Await.result(client.send(args.mkString(" ")), timout.duration)
  println(s"${resp.cmd}")
}
