package csw.qa.framework;

import akka.actor.Scheduler;
import akka.typed.ActorRef;
import akka.typed.javadsl.ActorContext;
import akka.util.Timeout;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import csw.framework.javadsl.JComponentBehaviorFactory;
import csw.framework.javadsl.JComponentHandlers;
import csw.framework.javadsl.JContainerCmd;
import csw.messages.*;
import csw.messages.ccs.commands.CommandExecutionService;
import csw.messages.ccs.commands.CommandResponse;
import csw.messages.ccs.commands.ControlCommand;
import csw.messages.framework.ComponentInfo;
import csw.messages.location.AkkaLocation;
import csw.messages.location.LocationUpdated;
import csw.messages.location.TrackingEvent;
import csw.messages.models.PubSub;
import csw.messages.params.models.RunId;
import csw.messages.params.states.CurrentState;
import csw.services.location.javadsl.ILocationService;
import csw.services.logging.javadsl.ILogger;
import csw.services.logging.javadsl.JLoggerFactory;
import scala.runtime.BoxedUnit;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import csw.messages.CommandResponseManagerMessage.AddOrUpdateCommand;

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
      forwardCommandToHcd(controlCommand);
    }


    // For testing, forward command to HCD and complete this command when it completes
    private void forwardCommandToHcd(ControlCommand controlCommand) {
      testHcd.ifPresent(hcd -> {
        Scheduler scheduler = ctx.getSystem().scheduler();
        Timeout timeout = new Timeout(3, TimeUnit.SECONDS);
        CommandExecutionService.submit(hcd, controlCommand, timeout, scheduler)
            .thenAccept(commandResponse -> {
              RunId runId = commandResponse.runId();
              assert (runId.equals(controlCommand.runId()));
              if (commandResponse instanceof CommandResponse.Accepted) {
                CommandExecutionService.getCommandResponse(hcd, runId, timeout, scheduler)
                    .thenAccept(finalResponse -> commandResponseManager.tell(new AddOrUpdateCommand(runId, finalResponse)))
                    .exceptionally(ex -> {
                      log.error("Failed to get command response from TestHcd", ex);
                      commandResponseManager.tell(new AddOrUpdateCommand(runId, new CommandResponse.Error(runId, ex.toString())));
                      return null;
                    });
              } else {
                log.error("Unexpected response from TestHcd: " + commandResponse);
              }
            })
            .exceptionally(ex -> {
              log.error("Failed to get validation response from TestHcd", ex);
              commandResponseManager.tell(new AddOrUpdateCommand(controlCommand.runId(),
                  new CommandResponse.Error(controlCommand.runId(), ex.toString())));
              return null;
            });
      });
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
