package org.tmt.sample.impl

import akka.http.scaladsl.server.Route
import esw.http.template.wiring.ServerWiring
import org.tmt.sample.http.{JSampleImplWrapper, SampleRoute}

class SampleWiring(val port: Option[Int]) extends ServerWiring {
  override val actorSystemName: String = "sample-actor-system"

  lazy val jSampleImpl: JSampleImpl = new JSampleImpl(jCswServices)
  lazy val sampleImpl               = new SampleImpl(cswServices)
  lazy val sampleImplWrapper        = new JSampleImplWrapper(jSampleImpl)

  import actorRuntime.ec
  override lazy val routes: Route = new SampleRoute(sampleImpl, sampleImplWrapper, securityDirectives).route
}
