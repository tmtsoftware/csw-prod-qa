package org.tmt.sample.impl;

import esw.http.template.wiring.JCswServices;
import org.hamcrest.CoreMatchers;
import org.junit.Test;
import org.mockito.Mockito;
import org.scalatestplus.junit.JUnitSuite;
import org.tmt.sample.core.models.GreetResponse;

import java.util.concurrent.ExecutionException;

import static org.hamcrest.MatcherAssert.assertThat;

public class JSampleImplTest extends JUnitSuite {

  @Test
  public void shouldCallBye() throws ExecutionException, InterruptedException {
    JCswServices mock = Mockito.mock(JCswServices.class);
    JSampleImpl jSample = new JSampleImpl(mock);
    GreetResponse greetResponse = new GreetResponse("Bye!!!");
    assertThat(jSample.sayBye().get(), CoreMatchers.is(greetResponse));
  }
}
