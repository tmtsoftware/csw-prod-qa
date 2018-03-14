package csw.qa.location

import akka.actor.typed.ActorRef
import csw.messages.location.TrackingEvent



// Message type received by TestServiceClient
sealed trait ServiceClientMessageType extends Serializable

case class TrackingEventMessage(event: TrackingEvent) extends ServiceClientMessageType


// Message type received by TestAkkaService
sealed trait ServiceMessageType extends Serializable

// Message sent from client once location has been resolved
case class ClientMessage(replyTo: ActorRef[ServiceClientMessageType]) extends ServiceMessageType

// Message to unregister and quit
case object Quit extends ServiceMessageType


