package streams.server

import akka.actor.ActorSystem
import akka.stream.scaladsl.*
import akka.util.ByteString
import akka.stream.scaladsl.Framing
import streams.shared.Command

import scala.concurrent.{ExecutionContext, Future}

class SocketServerStream(host: String = "127.0.0.1", port: Int = 8888)(implicit system: ActorSystem) {
  implicit val ec: ExecutionContext = system.dispatcher

  private val connections = Tcp().bind(host, port)

  // Reply to an incoming socket message.
  // The message is assumed to be in the command format: "123 commandStr"
  // For the the DELAY command is supported, with a number of ms: "DELAY 1000".
  // which just sleeps for that amount of time before replying with: "123 COMPLETED",
  // where 123 is the command id number.
  private def handleMessage(msg: String): Future[String] = {
    println(s"XXX got $msg")
    val (id, command) = Command.parse(msg)
    val delayMs       = if (command.startsWith("DELAY ")) command.split(" ")(1).toInt else 0
    if (delayMs == 0)
      Future.successful(s"$id COMPLETED\n")
    else
      Future {
        Thread.sleep(delayMs)
        s"$id COMPLETED\n"
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
