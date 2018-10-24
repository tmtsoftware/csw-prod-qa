package csw.qa.framework;

import akka.actor.typed.javadsl.ActorContext;
import akka.util.Timeout;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import csw.command.api.javadsl.ICommandService;
import csw.command.client.CommandResponseManager;
import csw.command.client.CommandServiceFactory;
import csw.command.client.messages.TopLevelActorMessage;
import csw.framework.javadsl.JComponentBehaviorFactory;
import csw.framework.javadsl.JComponentHandlers;
import csw.framework.javadsl.JContainerCmd;
import csw.framework.models.JCswContext;
import csw.location.api.models.AkkaLocation;
import csw.location.api.models.LocationUpdated;
import csw.location.api.models.TrackingEvent;
import csw.logging.javadsl.ILogger;
import csw.logging.javadsl.JLoggerFactory;
import csw.params.commands.CommandResponse;
import csw.params.commands.ControlCommand;
import csw.params.commands.Setup;

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
        JCswContext cswCtx) {
      return new JTestAssembly.JTestAssemblyHandlers(ctx, cswCtx);
    }
  }

  @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
  static class JTestAssemblyHandlers extends JComponentHandlers {
    private final ILogger log;
    private final ActorContext<TopLevelActorMessage> ctx;
    private final CommandResponseManager commandResponseManager;
    // Set when the location is received from the location service (below)
    private Optional<ICommandService> testHcd = Optional.empty();


    JTestAssemblyHandlers(ActorContext<TopLevelActorMessage> ctx,
                          JCswContext cswServices) {
      super(ctx, cswServices);

      this.log = new JLoggerFactory(cswServices.componentInfo().name()).getLogger(getClass());
      this.ctx = ctx;
      this.commandResponseManager = cswServices.commandResponseManager();
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
    public CommandResponse.ValidateCommandResponse validateCommand(ControlCommand controlCommand) {
      return new CommandResponse.Accepted(controlCommand.runId());
    }

    @Override
    public CommandResponse.SubmitResponse onSubmit(ControlCommand controlCommand) {
      log.debug("onSubmit called: " + controlCommand);
      forwardCommandToHcd(controlCommand);
      return new CommandResponse.Started(controlCommand.runId());
    }

    // For testing, forward command to HCD and complete this command when it completes
    private void forwardCommandToHcd(ControlCommand controlCommand) {
      testHcd.ifPresent(hcd -> {
        Timeout timeout = new Timeout(3, TimeUnit.SECONDS);
        Setup setup = new Setup(controlCommand.source(), controlCommand.commandName(), controlCommand.jMaybeObsId());
        commandResponseManager.addSubCommand(controlCommand.runId(), setup.runId());
        try {
          CommandResponse.SubmitResponse response = hcd.submit(setup, timeout).get();
          log.info("response = " + response);
          commandResponseManager.updateSubCommand(setup.runId(), response);
        } catch (Exception ex) {
          commandResponseManager.updateSubCommand(setup.runId(), new CommandResponse.Error(setup.runId(), ex.toString()));
        }
      });
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
        testHcd = Optional.of(CommandServiceFactory.jMake(location, ctx.getSystem()));
      } else testHcd = Optional.empty();
    }
  }

  public static void main(String[] args) {
    Config defaultConfig = ConfigFactory.load("JTestAssembly.conf");
    JContainerCmd.start("TestAssembly", args, Optional.of(defaultConfig));
  }
}
