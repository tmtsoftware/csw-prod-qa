package streams.client

import akka.stream.OverflowStrategy
import akka.stream.scaladsl.*
import akka.util.{ByteString, Timeout}
import akka.stream.scaladsl.Framing

import scala.concurrent.{Await, ExecutionContext, Future}
import akka.NotUsed
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, Props, SpawnProtocol}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.scaladsl.adapter.*
import akka.actor.typed.scaladsl.AskPattern.*
import streams.shared.SocketMessage
import SocketClientActor.*
import SocketClientStream.*
import streams.shared.SocketMessage.{CMD_TYPE, MessageId, MsgHdr, SourceId}

import java.nio.ByteOrder

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

  def behavior(name: String): Behavior[SocketClientActorMessage] =
    Behaviors.setup[SocketClientActorMessage](new SocketClientActor(name, _))
}

private[client] class SocketClientActor(name: String, ctx: ActorContext[SocketClientActorMessage])
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
        if (clientMap.contains(resp.hdr.seqNo)) {
          clientMap(resp.hdr.seqNo) ! resp
          clientMap = clientMap - resp.hdr.seqNo
        } else {
          responseMap = responseMap + (resp.hdr.seqNo -> resp)
        }
        Behaviors.same

      case GetResponse(seqNo, replyTo) =>
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

object SocketClientStream {
  /**
   * Should be ActorSystem or ActorContext, needed to create a child actor
   */
  private trait SpawnHelper {
    def spawn[U](behavior: Behavior[U], name: String, props: Props = Props.empty): ActorRef[U]
  }

  def withSystem(name: String, host: String = "127.0.0.1", port: Int = 8888)(implicit system: ActorSystem[SpawnProtocol.Command]): SocketClientStream = {
    val spawnHelper = new SpawnHelper {
      def spawn[U](behavior: Behavior[U], name: String, props: Props = Props.empty): ActorRef[U] = {
        import csw.logging.client.commons.AkkaTypedExtension.UserActorFactory
        system.spawn(behavior, name, props)
      }
    }
    new SocketClientStream(spawnHelper, name, host, port)(system)
  }

  def apply(ctx: ActorContext[?], name: String, host: String = "127.0.0.1", port: Int = 8888): SocketClientStream = {
    val spawnHelper = new SpawnHelper {
      def spawn[U](behavior: Behavior[U], name: String, props: Props = Props.empty): ActorRef[U] = {
        ctx.spawn(behavior, name, props)
      }
    }
    new SocketClientStream(spawnHelper, name, host, port)(ctx.system)
  }

}

class SocketClientStream(spawnHelper: SpawnHelper, name: String, host: String = "127.0.0.1", port: Int = 8888)(
    implicit system: ActorSystem[?]
) {
  implicit val ec: ExecutionContext = system.executionContext
  private val connection            = Tcp()(system.toClassic).outgoingConnection(host, port)

  // Use a queue to feed commands to the stream
  private val (queue, source) = Source.queue[ByteString](bufferSize = 2, OverflowStrategy.backpressure).preMaterialize()

  // An actor to manage the server responses and match them to command ids
  private val clientActor = spawnHelper.spawn(SocketClientActor.behavior(name), s"$name-actor")

  // A sink for responses from the server
  private val sink = Sink.foreach[ByteString] { bs =>
    val resp = SocketMessage.parse(bs)
    clientActor ! SetResponse(resp)
  }


  // Used to feed commands to the stream
  private val clientFlow = Flow.fromSinkAndSource(sink, source)

  private val parser: Flow[ByteString, ByteString, NotUsed] = {
    Flow[ByteString]
      .takeWhile(_ != ByteString("q"))
      .concat(Source.single(SocketMessage(MsgHdr(CMD_TYPE, SourceId(0), MsgHdr.encodedSize + 3, 0), "BYE").toByteString))
  }

  // XXX Note: Looks like there might be a bug in Framing.lengthField, requiring the function arg!
  private val flow = Flow[ByteString]
    .via(Framing.lengthField(2, 4, 264, ByteOrder.LITTLE_ENDIAN, (_,i) => i))
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
//    clientActor.ask(GetSeqNo).flatMap { seqNo =>
//      send(SocketMessage(MsgHdr(msgId, srcId, msgLen = msg.length + MsgHdr.encodedSize, seqNo = seqNo), msg))
//    }
    val seqNo = Await.result(clientActor.ask(GetSeqNo), timeout.duration)
    send(SocketMessage(MsgHdr(msgId, srcId, msgLen = msg.length + MsgHdr.encodedSize, seqNo = seqNo), msg))
  }

  /**
   * Terminates the stream
   */
  def terminate(): Unit = {
    // XXX FIXME
//    queue.offer(ByteString("q"))
    clientActor ! Stop
  }
}

//object SocketClientStreamApp extends App {
//  implicit val system: ActorSystem[SpawnProtocol.Command] = ActorSystem(SpawnProtocol(), "SocketClientStream")
//  implicit val timout: Timeout = Timeout(5.seconds)
//  val client = SocketClientStream.withSystem("socketClientStream")
//  val resp = Await.result(client.send(args.mkString(" ")), timout.duration)
//  println(s"${resp.cmd}")
//}
