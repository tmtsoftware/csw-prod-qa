package csw.qa.framework;

import akka.actor.typed.javadsl.ActorContext;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import csw.framework.javadsl.JComponentBehaviorFactory;
import csw.framework.javadsl.JComponentHandlers;
import csw.framework.javadsl.JContainerCmd;
import csw.framework.scaladsl.CurrentStatePublisher;
import csw.messages.commands.CommandResponse;
import csw.messages.commands.ControlCommand;
import csw.messages.framework.ComponentInfo;
import csw.messages.location.TrackingEvent;
import csw.messages.scaladsl.TopLevelActorMessage;
import csw.services.command.scaladsl.CommandResponseManager;
import csw.services.location.javadsl.ILocationService;
import csw.services.logging.javadsl.ILogger;
import csw.services.logging.javadsl.JLoggerFactory;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class JTestHcd {

  @SuppressWarnings("unused")
  public static class JTestHcdBehaviorFactory extends JComponentBehaviorFactory {

    public JTestHcdBehaviorFactory() {
    }

    @Override
    public JComponentHandlers jHandlers(
        ActorContext<TopLevelActorMessage> ctx,
        ComponentInfo componentInfo,
        CommandResponseManager commandResponseManager,
        CurrentStatePublisher currentStatePublisher,
        ILocationService locationService,
        JLoggerFactory loggerFactory) {
      return new JTestHcd.JTestHcdHandlers(ctx, componentInfo, commandResponseManager, currentStatePublisher, locationService,
          loggerFactory);
    }
  }

  static class JTestHcdHandlers extends JComponentHandlers {
    private ILogger log;
    private final CommandResponseManager commandResponseManager;

    JTestHcdHandlers(ActorContext<TopLevelActorMessage> ctx,
                     ComponentInfo componentInfo,
                     CommandResponseManager commandResponseManager,
                     CurrentStatePublisher currentStatePublisher,
                     ILocationService locationService,
                     JLoggerFactory loggerFactory) {
      super(ctx, componentInfo, commandResponseManager, currentStatePublisher, locationService,loggerFactory);
      this.log = new JLoggerFactory(componentInfo.name()).getLogger(getClass());
      this.commandResponseManager = commandResponseManager;
      log.debug("Starting Test HCD");
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
    public void onLocationTrackingEvent(TrackingEvent trackingEvent) {
      log.debug("onLocationTrackingEvent called: " + trackingEvent);
    }

    @Override
    public CommandResponse validateCommand(ControlCommand controlCommand) {
      return new CommandResponse.Accepted(controlCommand.runId());
    }

    @Override
    public void onSubmit(ControlCommand controlCommand) {
      log.debug("onSubmit called: " + controlCommand);
      commandResponseManager.addOrUpdateCommand(controlCommand.runId(), new CommandResponse.Completed(controlCommand.runId()));
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
  }

  public static void main(String[] args) {
    Config defaultConfig = ConfigFactory.load("JTestHcd.conf");
    JContainerCmd.start("TestHcd", args, Optional.of(defaultConfig));
  }
}
