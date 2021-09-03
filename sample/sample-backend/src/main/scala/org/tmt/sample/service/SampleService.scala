package org.tmt.sample.service

import org.tmt.sample.core.models.{AdminGreetResponse, GreetResponse, UserInfo}

import scala.concurrent.Future

trait SampleService {
  def greeting(userInfo: UserInfo): Future[GreetResponse]
  def adminGreeting(userInfo: UserInfo): Future[AdminGreetResponse]
}
