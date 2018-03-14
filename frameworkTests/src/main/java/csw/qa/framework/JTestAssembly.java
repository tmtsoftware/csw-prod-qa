package csw.qa.framework;

import akka.actor.typed.javadsl.ActorContext;
import akka.util.Timeout;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import csw.framework.javadsl.JComponentBehaviorFactory;
import csw.framework.javadsl.JComponentHandlers;
import csw.framework.javadsl.JContainerCmd;
import csw.framework.scaladsl.CurrentStatePublisher;
import csw.messages.commands.CommandResponse;
import csw.messages.commands.ControlCommand;
import csw.messages.commands.Setup;
import csw.messages.framework.ComponentInfo;
import csw.messages.location.AkkaLocation;
import csw.messages.location.LocationUpdated;
import csw.messages.location.TrackingEvent;
import csw.messages.scaladsl.TopLevelActorMessage;
import csw.services.command.javadsl.JCommandService;
import csw.services.command.scaladsl.CommandResponseManager;
import csw.services.location.javadsl.ILocationService;
import csw.services.logging.javadsl.ILogger;
import csw.services.logging.javadsl.JLoggerFactory;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class JTestAssembly {

  @SuppressWarnings("unused")
  public static class JTestAssemblyBehaviorFactory extends JComponentBehaviorFactory {

    public JTestAssemblyBehaviorFactory() {
    }

    @Override
    public JComponentHandlers jHandlers(
        ActorContext<TopLevelActorMessage> ctx,
        ComponentInfo componentInfo,
        CommandResponseManager commandResponseManager,
        CurrentStatePublisher currentStatePublisher,
        ILocationService locationService,
        JLoggerFactory loggerFactory) {
      return new JTestAssembly.JTestAssemblyHandlers(ctx, componentInfo, commandResponseManager, currentStatePublisher, locationService,
          loggerFactory);
    }
  }

  static class JTestAssemblyHandlers extends JComponentHandlers {
    private final ILogger log;
    private final ActorContext<TopLevelActorMessage> ctx;
    private final CommandResponseManager commandResponseManager;
    // Set when the location is received from the location service (below)
    private Optional<JCommandService> testHcd = Optional.empty();

    JTestAssemblyHandlers(ActorContext<TopLevelActorMessage> ctx,
                          ComponentInfo componentInfo,
                          CommandResponseManager commandResponseManager,
                          CurrentStatePublisher currentStatePublisher,
                          ILocationService locationService,
                          JLoggerFactory loggerFactory) {
      super(ctx, componentInfo, commandResponseManager, currentStatePublisher, locationService, loggerFactory);
      this.log = new JLoggerFactory(componentInfo.name()).getLogger(getClass());
      this.ctx = ctx;
      this.commandResponseManager = commandResponseManager;
      log.debug("Starting Test Assembly");
    }

    @Override
    public CompletableFuture<Void> jInitialize() {
      log.debug("jInitialize called");
      return CompletableFuture.runAsync(() -> {
      });
    }

    @Override
    public CompletableFuture<Void> jOnShutdown() {
      log.debug("onShutdown called");
      return CompletableFuture.runAsync(() -> {
      });
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
        Timeout timeout = new Timeout(3, TimeUnit.SECONDS);
        Setup setup = new Setup(controlCommand.source(), controlCommand.commandName(), controlCommand.jMaybeObsId());
        commandResponseManager.addSubCommand(controlCommand.runId(), setup.runId());
        try {
          CommandResponse response = hcd.submitAndSubscribe(setup, timeout).get();
          log.info("response = " + response);
          commandResponseManager.updateSubCommand(setup.runId(), response);
        } catch (Exception ex) {
          commandResponseManager.updateSubCommand(setup.runId(), new CommandResponse.Error(setup.runId(), ex.toString()));
        }
      });


//        hcd.submit(controlCommand, timeout, scheduler)
//            .thenAccept(commandResponse -> {
//              RunId runId = commandResponse.runId();
//              assert (runId.equals(controlCommand.runId()));
//              if (commandResponse instanceof CommandResponse.Accepted) {
//                hcd.getCommandResponse(runId, timeout, scheduler)
//                    .thenAccept(finalResponse -> commandResponseManager.tell(new AddOrUpdateCommand(runId, finalResponse)))
//                    .exceptionally(ex -> {
//                      log.error("Failed to get command response from TestHcd", ex);
//                      commandResponseManager.tell(new AddOrUpdateCommand(runId, new CommandResponse.Error(runId, ex.toString())));
//                      return null;
//                    });
//              } else {
//                log.error("Unexpected response from TestHcd: " + commandResponse);
//              }
//            })
//            .exceptionally(ex -> {
//              log.error("Failed to get validation response from TestHcd", ex);
//              commandResponseManager.tell(new AddOrUpdateCommand(controlCommand.runId(),
//                  new CommandResponse.Error(controlCommand.runId(), ex.toString())));
//              return null;
//            });
//      });
    }


    @Override
    public void onOneway(ControlCommand controlCommand) {
      log.debug("onOneway called: " + controlCommand);
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
        testHcd = Optional.of(new JCommandService(location, ctx.getSystem()));
      } else testHcd = Optional.empty();
    }
  }

  public static void main(String[] args) {
    Config defaultConfig = ConfigFactory.load("JTestAssembly.conf");
    JContainerCmd.start("TestAssembly", args, Optional.of(defaultConfig));
  }
}
