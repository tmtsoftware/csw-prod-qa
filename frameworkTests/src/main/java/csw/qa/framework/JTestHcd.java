package csw.qa.framework;

import akka.typed.ActorRef;
import akka.typed.javadsl.ActorContext;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import csw.apps.containercmd.ContainerCmd$;
import csw.framework.javadsl.JComponentBehaviorFactory;
import csw.framework.javadsl.JComponentHandlers;
import csw.framework.javadsl.JContainerCmd;
import csw.messages.*;
import csw.messages.ccs.Validation;
import csw.messages.ccs.ValidationIssue;
import csw.messages.ccs.Validations;
import csw.messages.framework.ComponentInfo;
import csw.messages.location.TrackingEvent;
import csw.messages.params.states.CurrentState;
import csw.services.location.javadsl.ILocationService;
import csw.services.logging.javadsl.ILogger;
import csw.services.logging.javadsl.JComponentLogger;
import scala.runtime.BoxedUnit;

import java.net.UnknownHostException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class JTestHcd {

  // Base trait for Test HCD domain messages
  interface JTestHcdDomainMessage extends RunningMessage.DomainMessage {
  }
  // Add messages here...


  @SuppressWarnings("unused")
  public static class JTestHcdBehaviorFactory extends JComponentBehaviorFactory<JTestHcdDomainMessage> {

    public JTestHcdBehaviorFactory() {
      super(JTestHcd.JTestHcdDomainMessage.class);
    }

    @Override
    public JComponentHandlers<JTestHcdDomainMessage> jHandlers(
        ActorContext<ComponentMessage> ctx,
        ComponentInfo componentInfo,
        ActorRef<PubSub.PublisherMessage<CurrentState>> pubSubRef,
        ILocationService locationService) {
      return new JTestHcd.JTestHcdHandlers(ctx, componentInfo, pubSubRef, locationService, JTestHcd.JTestHcdDomainMessage.class);
    }
  }

  static class JTestHcdHandlers extends JComponentHandlers<JTestHcdDomainMessage> implements JComponentLogger {
    // XXX Can't this be done in the interface?
    private ILogger log = getLogger();

    JTestHcdHandlers(ActorContext<ComponentMessage> ctx,
                      ComponentInfo componentInfo,
                      ActorRef<PubSub.PublisherMessage<CurrentState>> pubSubRef,
                      ILocationService locationService,
                      Class<JTestHcdDomainMessage> klass) {
      super(ctx, componentInfo, pubSubRef, locationService, klass);
      log.debug("Starting Test HCD");
    }

    private BoxedUnit doNothing() {
      return BoxedUnit.UNIT;
    }

    @Override
    public CompletableFuture<BoxedUnit> jInitialize() {
      log.debug("jInitialize called");
      return CompletableFuture.supplyAsync(this::doNothing);
    }

    @Override
    public void onLocationTrackingEvent(TrackingEvent trackingEvent) {
      log.debug("onLocationTrackingEvent called: " + trackingEvent);
    }

    @Override
    public void onDomainMsg(JTestHcdDomainMessage testHcdDomainMessage) {
      log.debug("onDomainMessage called: " + testHcdDomainMessage);
    }

    @Override
    public Validation onSetup(CommandMessage commandMessage) {
      log.debug("onSetup called: " + commandMessage);
      return Validations.JValid();
    }

    @Override
    public Validation onObserve(CommandMessage commandMessage) {
      log.debug("onObserve called: " + commandMessage);
      return new Validations.Invalid(new ValidationIssue.UnsupportedCommandIssue("Observe not supported"));
    }

    @Override
    public CompletableFuture<BoxedUnit> jOnShutdown() {
      log.debug("onShutdown called");
      return CompletableFuture.supplyAsync(this::doNothing);
    }

    @Override
    public void onGoOffline() {
      log.debug("onGoOffline called");
    }

    @Override
    public void onGoOnline() {
      log.debug("onGoOnline called");
    }

    @Override
    public String componentName() {
      return "TestAssembly";
    }
  }

  public static void main(String[] args) throws UnknownHostException {
    Config defaultConfig = ConfigFactory.load("JTestHcd.conf");
    JContainerCmd.start("TestHcd", args, Optional.of(defaultConfig));
  }
}
