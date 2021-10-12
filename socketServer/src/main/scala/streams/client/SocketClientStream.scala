package streams.client

import akka.stream.OverflowStrategy
import akka.stream.scaladsl.*
import akka.util.{ByteString, Timeout}
import akka.stream.scaladsl.Framing

import scala.concurrent.{ExecutionContext, Future}
import akka.NotUsed
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, Props, SpawnProtocol}
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.scaladsl.adapter.*
import akka.actor.typed.scaladsl.AskPattern.*
import streams.shared.Command
import SocketClientActor.*
import SocketClientStream.*

// Actor used to keep track of the server responses and match them with ids
private[client] object SocketClientActor {
  sealed trait SocketClientActorMessage
  // Sets the response to the command with the given id
  case class SetResponse(id: Int, resp: String) extends SocketClientActorMessage
  // Gets the response for the command with the given id
  case class GetResponse(id: Int, replyTo: ActorRef[String]) extends SocketClientActorMessage
  // Stop the actor
  case object Stop extends SocketClientActorMessage

  def behavior(name: String): Behavior[SocketClientActorMessage] =
    Behaviors.setup[SocketClientActorMessage](new SocketClientActor(_))
}

private[client] class SocketClientActor(ctx: ActorContext[SocketClientActorMessage])
    extends AbstractBehavior[SocketClientActorMessage](ctx) {
  // Maps command id to server response
  private var responseMap = Map.empty[Int, String]
  // Maps command id to the actor waiting to get the response
  private var clientMap = Map.empty[Int, ActorRef[String]]

  override def onMessage(msg: SocketClientActorMessage): Behavior[SocketClientActorMessage] = {
    msg match {
      case SetResponse(id, resp) =>
        if (clientMap.contains(id)) {
          clientMap(id) ! resp
          clientMap = clientMap - id
        } else {
          responseMap = responseMap + (id -> resp)
        }
        Behaviors.same

      case GetResponse(id, replyTo) =>
        if (responseMap.contains(id)) {
          replyTo ! responseMap(id)
          responseMap = responseMap - id
        } else {
          clientMap = clientMap + (id -> replyTo)
        }
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

//noinspection DuplicatedCode
class SocketClientStream private(spawnHelper: SpawnHelper, name: String, host: String = "127.0.0.1", port: Int = 8888)(
    implicit system: ActorSystem[?]
) {
  implicit val ec: ExecutionContext = system.executionContext
  private val connection            = Tcp()(system.toClassic).outgoingConnection(host, port)

  // Use a queue to feed commands to the stream
  private val (queue, source) = Source.queue[String](bufferSize = 2, OverflowStrategy.backpressure).preMaterialize()

  // An actor to manage the server responses and match them to command ids
  private val clientActor = spawnHelper.spawn(SocketClientActor.behavior(name), s"$name-actor")

  // A sink for responses from the server
  private val sink = Sink.foreach[String] { s =>
    val (id, resp) = Command.parse(s)
    clientActor ! SetResponse(id, resp)
  }

  // Used to feed commands to the stream
  private val clientFlow = Flow.fromSinkAndSource(sink, source)

  // Converts the strings to ByteString, quits if "q" received (sending "BYE" to server)
  private val parser: Flow[String, ByteString, NotUsed] = {
    Flow[String]
      .takeWhile(_ != "q")
      .concat(Source.single("BYE"))
      .map(elem => ByteString(s"$elem\n"))
  }

  // Commands are assumed to be terminated with "\n" for now
  private val flow = Flow[ByteString]
    .via(Framing.delimiter(ByteString("\n"), maximumFrameLength = 256, allowTruncation = true))
    .map(_.utf8String)
    .via(clientFlow)
    .via(parser)

  private val connectedFlow = connection.join(flow).run()
  connectedFlow.foreach { c =>
    println(s"XXX local addr: ${c.localAddress}, remote addr: ${c.remoteAddress}")
  }

  /**
   * Sends a message to the server and returns the response
   * @param id a unique id for the command
   * @param msg the command text
   * @return the future response from the server
   */
  def send(id: Int, msg: String)(implicit timeout: Timeout): Future[String] = {
    val cmd = Command.make(id, msg)
    queue.offer(cmd)
    clientActor.ask(GetResponse(id, _))
  }

  /**
   * Terminates the stream
   */
  def terminate(): Unit = {
    queue.offer("q")
    clientActor ! Stop
  }
}
