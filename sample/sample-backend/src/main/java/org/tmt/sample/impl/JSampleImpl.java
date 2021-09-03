package org.tmt.sample.impl;

import esw.http.template.wiring.JCswServices;
import org.tmt.sample.core.models.GreetResponse;

import java.util.concurrent.CompletableFuture;

public class JSampleImpl {
  JCswServices jCswServices;

  public JSampleImpl(JCswServices jCswServices) {
    this.jCswServices = jCswServices;
  }

  public CompletableFuture<GreetResponse> sayBye() {
    return CompletableFuture.completedFuture(new GreetResponse("Bye!!!"));
  }

}
