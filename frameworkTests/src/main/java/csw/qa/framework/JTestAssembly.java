package csw.qa.framework;

import akka.typed.ActorRef;
import akka.typed.javadsl.ActorContext;
import akka.typed.javadsl.AskPattern;
import akka.util.Timeout;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import csw.framework.javadsl.JComponentBehaviorFactory;
import csw.framework.javadsl.JComponentHandlers;
import csw.framework.javadsl.JContainerCmd;
import csw.messages.*;
import csw.messages.CommandMessage.Submit;
import csw.messages.ccs.commands.CommandResponse;
import csw.messages.ccs.commands.CommandResponse.Accepted;
import csw.messages.ccs.commands.ControlCommand;
import csw.messages.framework.ComponentInfo;
import csw.messages.location.AkkaLocation;
import csw.messages.location.LocationUpdated;
import csw.messages.location.TrackingEvent;
import csw.messages.models.PubSub;
import csw.messages.params.states.CurrentState;
import csw.services.location.javadsl.ILocationService;
import csw.services.logging.javadsl.ILogger;
import csw.services.logging.javadsl.JLoggerFactory;
import csw.services.logging.scaladsl.LoggerFactory;
import scala.runtime.BoxedUnit;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import csw.messages.CommandResponseManagerMessage.AddOrUpdateCommand;
import csw.messages.ccs.commands.CommandResponse.Completed;

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
        ActorContext<TopLevelActorMessage> ctx,
        ComponentInfo componentInfo,
        ActorRef<CommandResponseManagerMessage> commandResponseManager,
        ActorRef<PubSub.PublisherMessage<CurrentState>> pubSubRef,
        ILocationService locationService,
        JLoggerFactory loggerFactory) {
      return new JTestAssembly.JTestAssemblyHandlers(ctx, componentInfo, commandResponseManager, pubSubRef, locationService,
          loggerFactory, JTestAssemblyDomainMessage.class);
    }
  }

  static class JTestAssemblyHandlers extends JComponentHandlers<JTestAssemblyDomainMessage> {
    private final ILogger log;
    private final ActorContext<TopLevelActorMessage> ctx;
    private final ActorRef<CommandResponseManagerMessage> commandResponseManager;
    // Set when the location is received from the location service (below)
    private Optional<ActorRef<ComponentMessage>> testHcd = Optional.empty();

    JTestAssemblyHandlers(ActorContext<TopLevelActorMessage> ctx,
                          ComponentInfo componentInfo,
                          ActorRef<CommandResponseManagerMessage> commandResponseManager,
                          ActorRef<PubSub.PublisherMessage<CurrentState>> pubSubRef,
                          ILocationService locationService,
                          JLoggerFactory loggerFactory,
                          Class<JTestAssemblyDomainMessage> klass) {
      super(ctx, componentInfo, commandResponseManager, pubSubRef, locationService, loggerFactory, klass);
      this.log = new JLoggerFactory(componentInfo.name()).getLogger(getClass());
      this.ctx = ctx;
      this.commandResponseManager = commandResponseManager;
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
    public CommandResponse validateCommand(ControlCommand controlCommand) {
      return new CommandResponse.Completed(controlCommand.runId());
    }

    @Override
    public void onSubmit(ControlCommand controlCommand) {
      log.debug("onSubmit called: " + controlCommand);
      commandResponseManager.tell(new AddOrUpdateCommand(controlCommand.runId(), new Completed(controlCommand.runId())));
    }

    // For testing, forward command to HCD and complete this command when it completes
    private void forwardCommandToHcd(ControlCommand controlCommand) {
//      testHcd.ifPresent( hcd ->
//          hcd.tell(new Submit(controlCommand, ctx.getSelf()))
//      );

//      if (testHcd.isPresent()) {
//        final CompletionStage<TopLevelActorMessage> reply =
//            AskPattern.ask(testHcd.get(),
//                (ActorRef<CommandResponse> replyTo) -> new Submit(controlCommand, replyTo),
//                new Timeout(3, TimeUnit.SECONDS), ctx.getSystem().scheduler());
//
//        reply.thenAccept(resp -> {
//          log.info("TestHcd responded with " + resp);
//          if (resp instanceof Accepted) {
//            Accepted a = (Accepted) resp;
//            assert (a.runId().equals(controlCommand.runId()));
//            commandResponseManager.tell(new AddOrUpdateCommand(a.runId(), new Completed(a.runId())));
//          }
//        });
//      }
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

    @Override
    public void onLocationTrackingEvent(TrackingEvent trackingEvent) {
      log.debug("onLocationTrackingEvent called: " + trackingEvent);
      if (trackingEvent instanceof LocationUpdated) {
        AkkaLocation location = (AkkaLocation) ((LocationUpdated) trackingEvent).location();
        testHcd = Optional.of(location.componentRef());
      } else testHcd = Optional.empty();
    }
  }

  public static void main(String[] args) {
    Config defaultConfig = ConfigFactory.load("JTestAssembly.conf");
    JContainerCmd.start("TestAssembly", args, Optional.of(defaultConfig));
  }
}
