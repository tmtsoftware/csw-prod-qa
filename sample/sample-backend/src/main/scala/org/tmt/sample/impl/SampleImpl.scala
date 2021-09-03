package org.tmt.sample.impl

import csw.params.core.generics.{KeyType, Parameter}
import csw.params.events.{EventName, SystemEvent}
import csw.prefix.models.Prefix
import csw.prefix.models.Subsystem.CSW
import esw.http.template.wiring.CswServices
import org.tmt.sample.core.models.{AdminGreetResponse, GreetResponse, UserInfo}
import org.tmt.sample.service.SampleService

import scala.Double.NaN
import scala.concurrent.Future

class SampleImpl(cswServices: CswServices) extends SampleService{
  private val publisher = cswServices.eventService.defaultPublisher
  private val prefix = Prefix(CSW, "testComp")
  private val eventName = EventName("testEvent")
  private val testKey = KeyType.DoubleKey.make("testKey")
  private val testKey2 = KeyType.DoubleKey.make("testKey2")

  def greeting(userInfo: UserInfo): Future[GreetResponse] = {
    val paramSet: Set[Parameter[_]] = Set(testKey.set(1.234), testKey2.set(3.456))
    val event = SystemEvent(prefix, eventName, paramSet)
    publisher.publish(event)
    Future.successful(GreetResponse(userInfo))
  }

  def adminGreeting(userInfo: UserInfo): Future[AdminGreetResponse] = {
    val paramSet: Set[Parameter[_]] = Set(testKey.set(9.87), testKey2.set(NaN))
    val event = SystemEvent(prefix, eventName, paramSet)
    publisher.publish(event)
    Future.successful(AdminGreetResponse(userInfo))
  }
}
