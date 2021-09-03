package org.tmt.sample.http

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import csw.aas.http.AuthorizationPolicy.RealmRolePolicy
import csw.aas.http.SecurityDirectives
import org.tmt.sample.service.SampleService
import org.tmt.sample.core.models.UserInfo

import scala.concurrent.ExecutionContext

class SampleRoute(service1: SampleService, service2: JSampleImplWrapper, securityDirectives: SecurityDirectives) (implicit  ec: ExecutionContext) extends HttpCodecs {

 val route: Route = post {
    path("greeting") {
      entity(as[UserInfo]) { userInfo =>
        complete(service1.greeting(userInfo))
      }
    } ~
    path("adminGreeting") {
      securityDirectives.sPost(RealmRolePolicy("Esw-user")) { token =>
        entity(as[UserInfo]) { userInfo => complete(service1.adminGreeting(userInfo)) }
      }
    }
  } ~
    path("sayBye") {
      complete(service2.sayBye())
    }
}

