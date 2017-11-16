package csw.qa.framework;

import akka.typed.ActorRef;
import akka.typed.javadsl.ActorContext;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import csw.framework.javadsl.JComponentBehaviorFactory;
import csw.framework.javadsl.JComponentHandlers;
import csw.framework.javadsl.JContainerCmd;
import csw.messages.*;
import csw.messages.ccs.commands.CommandResponse;
import csw.messages.ccs.commands.ControlCommand;
import csw.messages.framework.ComponentInfo;
import csw.messages.location.TrackingEvent;
import csw.messages.params.states.CurrentState;
import csw.services.location.javadsl.ILocationService;
import csw.services.logging.javadsl.ILogger;
import csw.services.logging.javadsl.JCommonComponentLogger;
import scala.runtime.BoxedUnit;

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
        ActorRef<CommandResponseManagerMessage> commandResponseManager,
        ActorRef<PubSub.PublisherMessage<CurrentState>> pubSubRef,
        ILocationService locationService) {
      return new JTestHcd.JTestHcdHandlers(ctx, componentInfo, commandResponseManager, pubSubRef, locationService, JTestHcd.JTestHcdDomainMessage.class);
    }
  }

  static class JTestHcdHandlers extends JComponentHandlers<JTestHcdDomainMessage> implements JCommonComponentLogger {
    private ILogger log = getLogger();
    private final ComponentInfo componentInfo;

    JTestHcdHandlers(ActorContext<ComponentMessage> ctx,
                     ComponentInfo componentInfo,
                     ActorRef<CommandResponseManagerMessage> commandResponseManager,
                     ActorRef<PubSub.PublisherMessage<CurrentState>> pubSubRef,
                     ILocationService locationService,
                     Class<JTestHcdDomainMessage> klass) {
      super(ctx, componentInfo, commandResponseManager, pubSubRef, locationService, klass);
      this.componentInfo = componentInfo;
      log.debug("Starting Test HCD");
    }

    @Override
    public String componentName() {
      return componentInfo.name();
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
    public CommandResponse validateSubmit(ControlCommand controlCommand) {
      return new CommandResponse.Accepted(controlCommand.runId());
    }

    @Override
    public void onSubmit(ControlCommand controlCommand, ActorRef<CommandResponse> replyTo) {
      log.debug("onSubmit called: " + controlCommand);
    }

    @Override
    public CommandResponse validateOneway(ControlCommand controlCommand) {
      return new CommandResponse.Accepted(controlCommand.runId());
    }

    @Override
    public void onOneway(ControlCommand controlCommand) {
      log.debug("onOneway called: " + controlCommand);
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
  }

  public static void main(String[] args) {
    Config defaultConfig = ConfigFactory.load("JTestHcd.conf");
    JContainerCmd.start("TestHcd", args, Optional.of(defaultConfig));
  }
}
