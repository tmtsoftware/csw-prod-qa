package csw.qa.framework;

import akka.actor.typed.javadsl.ActorContext;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import csw.framework.javadsl.JComponentBehaviorFactory;
import csw.framework.javadsl.JComponentHandlers;
import csw.framework.javadsl.JContainerCmd;
import csw.framework.models.JCswServices;
import csw.messages.TopLevelActorMessage;
import csw.messages.commands.CommandResponse;
import csw.messages.commands.ControlCommand;
import csw.messages.events.Event;
import csw.messages.events.EventName;
import csw.messages.events.SystemEvent;
import csw.messages.location.TrackingEvent;
import csw.messages.params.generics.JKeyType;
import csw.messages.params.generics.Key;
import csw.services.command.CommandResponseManager;
import csw.services.event.api.javadsl.IEventPublisher;
import csw.services.event.api.javadsl.IEventService;
import csw.services.logging.javadsl.ILogger;
import csw.services.logging.javadsl.JLoggerFactory;

import java.time.Duration;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

public class JTestHcd {

  // Dummy key for publishing events
  private static Key<Integer> eventValueKey = JKeyType.IntKey().make("hcdEventValue");
  private static EventName eventName = new EventName("myHcdEvent");
  private static Random eventValues = new Random();


  @SuppressWarnings("unused")
  public static class JTestHcdBehaviorFactory extends JComponentBehaviorFactory {

    public JTestHcdBehaviorFactory() {
    }

    @Override
    public JComponentHandlers jHandlers(
        ActorContext<TopLevelActorMessage> ctx,
        JCswServices cswServices) {
      return new JTestHcd.JTestHcdHandlers(ctx, cswServices);
    }
  }

  static class JTestHcdHandlers extends JComponentHandlers {
    private final ILogger log;
    private final CommandResponseManager commandResponseManager;
    private final IEventService eventService;
    private final SystemEvent baseEvent;


    JTestHcdHandlers(ActorContext<TopLevelActorMessage> ctx,
                     JCswServices cswServices) {
      super(ctx, cswServices);
      this.log = new JLoggerFactory(cswServices.componentInfo().name()).getLogger(getClass());
      this.commandResponseManager = cswServices.commandResponseManager();
      this.eventService = cswServices.eventService();
      this.baseEvent = (new SystemEvent(cswServices.componentInfo().prefix(), eventName)).add(eventValueKey.set(eventValues.nextInt()));
      log.debug("Starting Test HCD");
    }

    @Override
    public CompletableFuture<Void> jInitialize() {
      log.debug("jInitialize called");
      startPublishingEvents();
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

    private void startPublishingEvents() {
      log.debug("start publishing events");
      IEventPublisher publisher = eventService.defaultPublisher();
      publisher.publish(this::eventGenerator, Duration.ofMillis(5000));
    }

    // this holds the logic for event generation, could be based on some computation or current state of HCD
    private Event eventGenerator() {
      SystemEvent newEvent = new SystemEvent(baseEvent.source(), baseEvent.eventName())
          .add(eventValueKey.set(eventValues.nextInt()));
      log.debug("Publishing event: " + newEvent);
      return newEvent;
    }
  }

  public static void main(String[] args) {
    Config defaultConfig = ConfigFactory.load("JTestHcd.conf");
    JContainerCmd.start("TestHcd", args, Optional.of(defaultConfig));
  }
}
