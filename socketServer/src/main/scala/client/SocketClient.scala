package client

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.io.Tcp.Connected
import akka.io.{IO, Tcp}
import akka.pattern.ask
import akka.util.{ByteString, Timeout}

import java.net.InetSocketAddress
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class SocketClient(remote: InetSocketAddress, listener: ActorRef) extends Actor {
  import Tcp.*
  import context.system

  IO(Tcp) ! Connect(remote)

  def receive: Receive = {
    case CommandFailed(_: Connect) =>
      listener ! "connect failed"
      context.stop(self)

    case c @ Connected(_, _) =>
      listener ! c
      val connection = sender()
      connection ! Register(self)
      context.become(connectedState(connection))

    case x => println(s"SocketClient received unexpected message: $x")
  }

  def connectedState(connection: ActorRef): Receive = {
    case data: ByteString =>
      connection ! Write(data)
    case CommandFailed(_: Write) =>
      // O/S buffer was full
      listener ! "write failed"
    case Received(data) =>
      listener ! data
    case "close" =>
      connection ! Close
    case _: ConnectionClosed =>
      listener ! "connection closed"
      context.stop(self)
  }
}

object ResponseHandler {
  sealed trait ResponseHandlerMessages
  // Notify the sender when the socket is connected
  object WhenConnected extends ResponseHandlerMessages
  // Send the given message to the given clientActor and then notify the sender when there is a response on the socket
  case class GetResponse(clientActor: ActorRef, message: String) extends ResponseHandlerMessages
}

class ResponseHandler extends Actor {
  import ResponseHandler.*
  def receive: Receive = {
    case Connected(_, _) =>
      context.become(connectedState())
    case WhenConnected =>
      context.become(waitingForConnectionState(sender()))
    case x => println(s"XXX client received unexpected message $x")
  }

  def waitingForConnectionState(listener: ActorRef): Receive = {
    case Connected(_, _) =>
      listener ! WhenConnected
      context.become(connectedState())
    case x => println(s"XXX client received unexpected message while waiting for connection: $x")
  }

  def connectedState(): Receive = {
    case WhenConnected =>
      sender() ! WhenConnected
    case data: ByteString =>
      println(s"XXX connectedState: client received ${data.utf8String}")
    case GetResponse(clientActor, message) =>
      clientActor ! ByteString(message)
      context.become(waitingForResponse(sender()))
    case "connection closed" => context.stop(self)
    case x                   => println(s"XXX client received unexpected message in connected state: $x")
  }

  def waitingForResponse(actorRef: ActorRef): Receive = {
    case data: ByteString =>
      println(s"XXX waitingForResponse: client received ${data.utf8String}")
      actorRef ! data.utf8String
      context.become(connectedState())
    case x => println(s"XXX client received unexpected message in connected state: $x")
  }
}

object SocketClient extends App {
  import ResponseHandler.*
  val system                    = ActorSystem("SocketClient")
  implicit val timeout: Timeout = Timeout(5.seconds)
  val server                    = new InetSocketAddress("localhost", 8080)
  val responseHandler           = system.actorOf(Props(new ResponseHandler), "responseHandlerActor")
  val clientActor               = system.actorOf(Props(new SocketClient(server, responseHandler)), "clientActor")

  // Wait for connection before sending a message
  Await.result(responseHandler ? ResponseHandler.WhenConnected, timeout.duration)

  // Send
  val resp1 = Await.result(responseHandler ? GetResponse(clientActor, "Hello"), timeout.duration)
  val resp2 = Await.result(responseHandler ? GetResponse(clientActor, "World"), timeout.duration)
  println(s"XXX Main responses: $resp1, $resp2")
  clientActor ! "close"
}
