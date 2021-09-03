package org.tmt.sample.http

import org.tmt.sample.impl.JSampleImpl
import org.tmt.sample.core.models.GreetResponse

import scala.compat.java8.FutureConverters.CompletionStageOps
import scala.concurrent.Future

class JSampleImplWrapper(jSampleImpl: JSampleImpl) {
  def sayBye(): Future[GreetResponse] = jSampleImpl.sayBye().toScala
}
