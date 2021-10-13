package streams.server

import akka.actor.ActorSystem
import akka.stream.scaladsl.*
import akka.util.ByteString
import akka.stream.scaladsl.Framing
import streams.shared.Command

import scala.concurrent.duration.*
import scala.concurrent.{ExecutionContext, Future, Promise}

/**
 * A TCL socket server that listens on the given host:port for connections
 * and accepts String messages in the format "id cmd". A reply is sent for
 * each message: "id COMPLETED".
 *
 * Currently any command can be sent and COMPLETED is always returned.
 * If the command is "DELAY ms" the reply is made after the giiven ms delay.
 */
//noinspection DuplicatedCode
class SocketServerStream(host: String = "127.0.0.1", port: Int = 8888)(implicit system: ActorSystem) {
  implicit val ec: ExecutionContext = system.dispatcher

  private val connections = Tcp().bind(host, port)

  // Reply to an incoming socket message.
  // The message is assumed to be in the command format: "123 commandStr"
  // The DELAY command is supported, with a number of ms: "DELAY 1000".
  // which just sleeps for that amount of time before replying with: "123 COMPLETED",
  // where 123 is the command id number.
  // A command starting with ERROR returns ERROR
  private def handleMessage(msg: String): Future[String] = {
    val (id, command) = Command.parse(msg)
    val resp          = if (command.startsWith("ERROR")) "ERROR" else "COMPLETED"
    val delayMs       = if (command.startsWith("DELAY ")) command.split(" ")(1).toInt else 0
    if (delayMs == 0)
      Future.successful(s"$id $resp\n")
    else {
      val p = Promise[String]
      system.scheduler.scheduleOnce(delayMs.millis)(p.success(s"$id $resp\n"))
      p.future
    }
  }

  private val binding =
    //#welcome-banner-chat-server
    connections
      .to(Sink.foreach { connection =>
        // server logic, parses incoming commands
        val commandParser = Flow[String]
          .takeWhile(_ != "BYE")
          .mapAsyncUnordered(100)(handleMessage)

        val serverLogic = Flow[ByteString]
          .via(Framing.delimiter(ByteString("\n"), maximumFrameLength = 256, allowTruncation = true))
          .map(_.utf8String)
          .via(commandParser)
          .map(ByteString(_))

        connection.handleWith(serverLogic)
      })
      .run()
}

object SocketServerStream extends App {
  implicit val system: ActorSystem = ActorSystem("SocketServerStream")
  // TODO: Add host, port options
  new SocketServerStream()
}
