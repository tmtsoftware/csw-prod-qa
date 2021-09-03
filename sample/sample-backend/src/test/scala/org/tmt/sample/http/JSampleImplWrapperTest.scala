package org.tmt.sample.http

import java.util.concurrent.CompletableFuture

import org.mockito.MockitoSugar.{mock, verify, when}
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.tmt.sample.impl.JSampleImpl
import org.tmt.sample.core.models.GreetResponse

class JSampleImplWrapperTest extends AnyWordSpec with Matchers {

  "SampleImplWrapper" must {
    "delegate sayBye to JSampleImpl.sayBye" in {
      val jSampleImpl       = mock[JSampleImpl]
      val sampleImplWrapper = new JSampleImplWrapper(jSampleImpl)

      val sampleResponse = mock[GreetResponse]
      when(jSampleImpl.sayBye()).thenReturn(CompletableFuture.completedFuture(sampleResponse))

      sampleImplWrapper.sayBye().futureValue should ===(sampleResponse)
      verify(jSampleImpl).sayBye()
    }
  }
}
