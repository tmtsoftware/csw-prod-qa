package server

import akka.actor.ActorSystem

import akka.stream.scaladsl.*
import akka.util.ByteString
import akka.stream.scaladsl.Framing

import scala.concurrent.ExecutionContext

class SocketServerStream(host: String = "127.0.0.1", port: Int = 8888)(implicit system: ActorSystem) {
  implicit val ec: ExecutionContext = system.dispatcher

  private val connections = Tcp().bind(host, port)

  private val binding =
    //#welcome-banner-chat-server
    connections
      .to(Sink.foreach { connection =>
        // server logic, parses incoming commands
        val commandParser = Flow[String]
          .takeWhile(_ != "BYE")
          .map { msg =>
            println(s"XXX got $msg")
            s"OK $msg"
          }

        val serverLogic = Flow[ByteString]
          .via(Framing.delimiter(ByteString("\n"), maximumFrameLength = 256, allowTruncation = true))
          .map(_.utf8String)
          .via(commandParser)
          .map(s => s"Server got $s")
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
