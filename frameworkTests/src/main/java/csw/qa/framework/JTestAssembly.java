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

public class JTestAssembly {

  // Base trait for Test Assembly domain messages
  interface JTestAssemblyDomainMessage extends RunningMessage.DomainMessage {
  }
  // Add messages here...

  @SuppressWarnings("unused")
  public static class JTestAssemblyBehaviorFactory extends JComponentBehaviorFactory<JTestAssemblyDomainMessage> {

    public JTestAssemblyBehaviorFactory() {
      super(JTestAssembly.JTestAssemblyDomainMessage.class);
    }

    @Override
    public JComponentHandlers<JTestAssemblyDomainMessage> jHandlers(
        ActorContext<ComponentMessage> ctx,
        ComponentInfo componentInfo,
        ActorRef<PubSub.PublisherMessage<CurrentState>> pubSubRef,
        ILocationService locationService) {
      return new JTestAssembly.JTestAssemblyHandlers(ctx, componentInfo, pubSubRef, locationService,
          JTestAssemblyDomainMessage.class);
    }
  }

  static class JTestAssemblyHandlers extends JComponentHandlers<JTestAssemblyDomainMessage>
      implements JComponentLogger {
    private ILogger log = getLogger();

    JTestAssemblyHandlers(ActorContext<ComponentMessage> ctx,
                           ComponentInfo componentInfo,
                           ActorRef<PubSub.PublisherMessage<CurrentState>> pubSubRef,
                           ILocationService locationService,
                           Class<JTestAssemblyDomainMessage> klass) {
      super(ctx, componentInfo, pubSubRef, locationService, klass);
      log.debug("Starting Test Assembly");
    }

    private BoxedUnit doNothing() {
      return null;
    }

    @Override
    public CompletableFuture<BoxedUnit> jInitialize() {
      log.debug("jInitialize called");
      return CompletableFuture.supplyAsync(this::doNothing);
    }

    @Override
    public void onDomainMsg(JTestAssemblyDomainMessage testAssemblyDomainMessage) {
      log.debug("onDomainMessage called: " + testAssemblyDomainMessage);
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
    public void onLocationTrackingEvent(TrackingEvent trackingEvent) {
      log.debug("onLocationTrackingEvent called: " + trackingEvent);
    }

    @Override
    public String componentName() {
      return "TestAssembly";
    }
  }

  public static void main(String[] args) throws UnknownHostException {
    Config defaultConfig = ConfigFactory.load("JTestAssembly.conf");
    JContainerCmd.start("TestAssembly", args, Optional.of(defaultConfig));
  }
}
