package server

import java.net.InetSocketAddress

import akka.actor.{ActorSystem, Props, Actor}
import akka.io.{Tcp, IO}
import akka.io.Tcp.*
import akka.util.ByteString

class TCPConnectionManager(address: String, port: Int) extends Actor {
  import context.system
  IO(Tcp) ! Bind(self, new InetSocketAddress(address, port))

  override def receive: Receive = {
    case Bound(local) =>
      println(s"Server started on $local")
    case Connected(remote, local) =>
      val handler = context.actorOf(Props(new TCPConnectionHandler))
      println(s"New connnection: $local -> $remote")
      sender() ! Register(handler)
  }
}

class TCPConnectionHandler extends Actor {
  override def receive: Actor.Receive = {
    case Received(data) =>
      val decoded = data.utf8String
      Thread.sleep(1000)
      sender() ! Write(ByteString(s"You told us: $decoded"))
    case x: ConnectionClosed =>
      println("Connection has been closed")
      context.stop(self)
  }
}

object SocketServer extends App {
  val system    = ActorSystem("SocketServer")
  val tcpserver = system.actorOf(Props(classOf[TCPConnectionManager], "localhost", 8080), "socketServerActor")
}
