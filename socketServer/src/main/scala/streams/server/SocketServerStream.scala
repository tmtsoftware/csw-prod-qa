package streams.server

import akka.actor.ActorSystem
import akka.stream.scaladsl.*
import akka.util.ByteString
import akka.stream.scaladsl.Framing
import streams.shared.SocketMessage
import streams.shared.SocketMessage.{MsgHdr, RSP_TYPE, SourceId}

import java.nio.ByteOrder
import scala.concurrent.duration.*
import scala.concurrent.{ExecutionContext, Future, Promise}

/**
 * A TCL socket server that listens on the given host:port for connections
 * and accepts String messages in the format "id cmd". A reply is sent for
 * each message: "id COMPLETED".
 *
 * Currently any command can be sent and COMPLETED is always returned.
 * If the command is "DELAY ms" the reply is made after the given ms delay.
 */
class SocketServerStream(host: String = "127.0.0.1", port: Int = 8888)(implicit system: ActorSystem) {
  implicit val ec: ExecutionContext = system.dispatcher

  private val connections = Tcp().bind(host, port)

  // Reply to an incoming socket message.
  // The DELAY command is supported here, with one arg: the number of ms. For example: "DELAY 1000",
  // which just sleeps for that amount of time before replying with: "DELAY: Completed".
  // For now, all other commands get an immediate reply.
  private def handleMessage(bs: ByteString): Future[ByteString] = {
    try {
      val msg = SocketMessage.parse(bs)
      val cmd     = msg.cmd.split(' ').head
      val s       = if (cmd.startsWith("ERROR")) "ERROR" else "COMPLETED"
      val respMsg = s"$cmd: $s"
      val resp    = SocketMessage(MsgHdr(RSP_TYPE, SourceId(120), MsgHdr.encodedSize + respMsg.length, msg.hdr.seqNo), respMsg)
      val delayMs = if (cmd == "DELAY") msg.cmd.split(" ")(1).toInt else 0
      if (delayMs == 0) {
        Future.successful(resp.toByteString)
      } else {
        val p = Promise[ByteString]
        system.scheduler.scheduleOnce(delayMs.millis)(p.success(resp.toByteString))
        p.future
      }
    } catch {
      case ex: Exception =>
        ex.printStackTrace()
        throw ex
    }
  }

  private val binding =
    connections
      .to(Sink.foreach { connection =>
        // server logic, parses incoming commands
        val commandParser = Flow[ByteString]
          .takeWhile(_ != ByteString("BYE"))
          .mapAsyncUnordered(100)(handleMessage)

        // XXX Note: Looks like there might be a bug in Framing.lengthField, requiring the function arg!
        val serverLogic = Flow[ByteString]
          .via(Framing.lengthField(2, 4, 264, ByteOrder.LITTLE_ENDIAN, (_, i) => i))
          .via(commandParser)

        connection.handleWith(serverLogic)
      })
      .run()

//  binding.foreach { b =>
//    println(s"XXX local address: ${b.localAddress}")
//
//  }
}

object SocketServerStream extends App {
  implicit val system: ActorSystem = ActorSystem("SocketServerStream")
  // TODO: Add host, port options
  new SocketServerStream()
}
