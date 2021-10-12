package streams.server

import akka.actor.ActorSystem
import akka.stream.scaladsl.*
import akka.util.ByteString
import akka.stream.scaladsl.Framing
import streams.shared.SocketMessage
import streams.shared.SocketMessage.{MsgHdr, RSP_TYPE, SourceId}

import scala.concurrent.{ExecutionContext, Future}

/**
 * A TCL socket server that listens on the given host:port for connections
 * and accepts String messages in the format "id cmd". A reply is sent for
 * each message: "id COMPLETED".
 *
 * Currently any command can be sent and COMPLETED is always returned.
 * If the command is "DELAY ms" the reply is made after the giiven ms delay.
 */
class SocketServerStream(host: String = "127.0.0.1", port: Int = 8888)(implicit system: ActorSystem) {
  implicit val ec: ExecutionContext = system.dispatcher

  private val connections = Tcp().bind(host, port)

  // Reply to an incoming socket message.
  // The DELAY command is supported here, with one arg: the number of ms. For example: "DELAY 1000",
  // which just sleeps for that amount of time before replying with: "DELAY: Completed".
  // For now, all other commands get an immediate reply.
  private def handleMessage(bs: ByteString): Future[ByteString] = {
    println(s"XXX handleMessage")
    val msg     = SocketMessage.parse(bs)
    println(s"XXX handleMessage: $msg")
    val cmd     = msg.cmd.split(' ').head
    val respMsg = s"$cmd: Completed"
    val resp    = SocketMessage(MsgHdr(RSP_TYPE, SourceId(120), MsgHdr.encodedSize + respMsg.length, msg.hdr.seqNo), respMsg)
    val delayMs = if (cmd == "DELAY ") msg.cmd.split(" ")(1).toInt else 0
    if (delayMs == 0) {
      println(s"immed: $resp")
      Future.successful(resp.toByteString)
    } else {
      Future {
        // TODO: Use system.scheduler...
        Thread.sleep(delayMs)
        println(s"de;ayed: $resp")
        resp.toByteString
      }
    }
  }

  private val binding =
    connections
      .to(Sink.foreach { connection =>
        println(s"XXX got connection: $connection")
        // server logic, parses incoming commands
        val commandParser = Flow[ByteString]
          .map{bs => println(s"XXX commandParser got ${bs.size}"); bs}
//          .takeWhile(_ != ByteString("BYE"))
          .mapAsyncUnordered(100)(handleMessage)

        val serverLogic = Flow[ByteString]
          .map{bs => println(s"XXX serverLogic got ${bs.length}"); bs}
//          .via(Framing.delimiter(ByteString("\n"), maximumFrameLength = 256, allowTruncation = true))
          .via(Framing.lengthField(2, 4, 264))
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
