package org.tmt.sample.impl

import csw.params.core.generics.{KeyType, Parameter}
import csw.params.events.{EventName, SystemEvent}
import csw.prefix.models.Prefix
import csw.prefix.models.Subsystem.CSW
import esw.http.template.wiring.CswServices
import org.tmt.sample.core.models.{AdminGreetResponse, GreetResponse, UserInfo}
import org.tmt.sample.service.SampleService

import scala.Double.NaN
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class SampleImpl(cswServices: CswServices)(implicit ec: ExecutionContext) extends SampleService {
  private val publisher = cswServices.eventService.defaultPublisher
  private val prefix = Prefix(CSW, "testComp")
  private val eventName = EventName("testEvent")
  private val testKey = KeyType.DoubleKey.make("testKey")
  private val testKey2 = KeyType.DoubleKey.make("testKey2")

  private val log = cswServices.loggerFactory.getLogger

  def greeting(userInfo: UserInfo): Future[GreetResponse] = {
    val paramSet: Set[Parameter[_]] = Set(testKey.set(1.234), testKey2.set(3.456))
    val event = SystemEvent(prefix, eventName, paramSet)
    publisher.publish(event).onComplete {
      case Success(_) =>
        log.info(s"Published event: $event")
      case Failure(ex) =>
        log.error(s"Error publishing event: $event", ex = ex)
        ex.printStackTrace()
    }
    Future.successful(GreetResponse(userInfo))
  }

  def adminGreeting(userInfo: UserInfo): Future[AdminGreetResponse] = {
    val paramSet: Set[Parameter[_]] = Set(testKey.set(9.87), testKey2.set(NaN))
    val event = SystemEvent(prefix, eventName, paramSet)
    publisher.publish(event).onComplete {
      case Success(_) =>
        log.info(s"Published event: $event")
      case Failure(ex) =>
        log.error(s"Error publishing event: $event", ex = ex)
        ex.printStackTrace()
    }
    Future.successful(AdminGreetResponse(userInfo))
  }
}
