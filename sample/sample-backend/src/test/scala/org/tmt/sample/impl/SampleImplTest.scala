package org.tmt.sample.impl

import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.tmt.sample.core.models.{AdminGreetResponse, GreetResponse, UserInfo}

class SampleImplTest extends AnyWordSpec with Matchers {

  "SampleImpl" must {
    "greeting should return greeting response of 'Hello user'" in {
      val sampleImpl = new SampleImpl()
      sampleImpl.greeting(UserInfo("John", "Smith")).futureValue should ===(GreetResponse("Hello user: John Smith!!!"))
    }

    "adminGreeting should return greeting response of 'Hello admin user'" in {
      val sampleImpl = new SampleImpl()
      sampleImpl.adminGreeting(UserInfo("John", "Smith")).futureValue should ===(AdminGreetResponse("Hello admin user: John Smith!!!"))
    }
  }
}
