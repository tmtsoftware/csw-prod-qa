package csw.qa.framework;

import akka.actor.typed.javadsl.ActorContext;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import csw.command.client.messages.TopLevelActorMessage;
import csw.event.api.javadsl.IEventPublisher;
import csw.event.api.javadsl.IEventService;
import csw.framework.javadsl.JComponentBehaviorFactory;
import csw.framework.javadsl.JComponentHandlers;
import csw.framework.javadsl.JContainerCmd;
import csw.framework.models.JCswContext;
import csw.location.models.TrackingEvent;
import csw.logging.api.javadsl.ILogger;
import csw.logging.client.javadsl.JLoggerFactory;
import csw.params.commands.CommandResponse;
import csw.params.commands.ControlCommand;
import csw.params.core.generics.Key;
import csw.params.events.Event;
import csw.params.events.EventName;
import csw.params.events.SystemEvent;
import csw.params.javadsl.JKeyType;
import csw.time.core.models.UTCTime;

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
        JCswContext cswServices) {
      return new JTestHcd.JTestHcdHandlers(ctx, cswServices);
    }
  }

  static class JTestHcdHandlers extends JComponentHandlers {
    private final ILogger log;
    private final IEventService eventService;
    private final SystemEvent baseEvent;


    JTestHcdHandlers(ActorContext<TopLevelActorMessage> ctx,
                     JCswContext cswServices) {
      super(ctx, cswServices);
      this.log = new JLoggerFactory(cswServices.componentInfo().name()).getLogger(getClass());
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
    public CommandResponse.ValidateCommandResponse validateCommand(ControlCommand controlCommand) {
      return new CommandResponse.Accepted(controlCommand.runId());
    }

    @Override
    public CommandResponse.SubmitResponse onSubmit(ControlCommand controlCommand) {
      log.debug("onSubmit called: " + controlCommand);
      return new CommandResponse.Completed(controlCommand.runId());
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
    public void onDiagnosticMode(UTCTime startTime, String hint) {
    }

    @Override
    public void onOperationsMode() {
    }

    private void startPublishingEvents() {
      log.debug("start publishing events");
      IEventPublisher publisher = eventService.defaultPublisher();
      publisher.publish(this::eventGenerator, Duration.ofMillis(5000));
    }

    // this holds the logic for event generation, could be based on some computation or current state of HCD
    private Optional<Event> eventGenerator() {
      SystemEvent newEvent = new SystemEvent(baseEvent.source(), baseEvent.eventName())
          .add(eventValueKey.set(eventValues.nextInt()));
      log.debug("Publishing event: " + newEvent);
      return Optional.of(newEvent);
    }
  }

  public static void main(String[] args) {
    Config defaultConfig = ConfigFactory.load("JTestHcd.conf");
    JContainerCmd.start("TestHcd", args, Optional.of(defaultConfig));
  }
}
