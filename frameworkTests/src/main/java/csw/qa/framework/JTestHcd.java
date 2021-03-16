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
import csw.location.api.models.TrackingEvent;
import csw.logging.api.javadsl.ILogger;
import csw.params.commands.CommandResponse;
import csw.params.commands.ControlCommand;
import csw.params.core.generics.Key;
import csw.params.core.models.Id;
import csw.params.events.Event;
import csw.params.events.EventName;
import csw.params.events.SystemEvent;
import csw.params.javadsl.JKeyType;
import csw.prefix.javadsl.JSubsystem;
import csw.time.core.models.UTCTime;

import java.time.Duration;
import java.util.Optional;
import java.util.Random;

public class JTestHcd {

  // Dummy key for publishing events
  private static final Key<Integer> eventValueKey = JKeyType.IntKey().make("hcdEventValue");
  private static final EventName eventName = new EventName("myHcdEvent");
  private static final Random eventValues = new Random();


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
      this.log = cswServices.loggerFactory().getLogger(this.getClass());
      this.eventService = cswServices.eventService();
      this.baseEvent = (new SystemEvent(cswServices.componentInfo().prefix(), eventName)).add(eventValueKey.set(eventValues.nextInt()));
      log.debug("Starting Test HCD");
    }

    @Override
    public void initialize() {
      log.debug("jInitialize called");
      startPublishingEvents();
    }

    @Override
    public void onShutdown() {
      log.debug("onShutdown called");
    }

    @Override
    public void onLocationTrackingEvent(TrackingEvent trackingEvent) {
      log.debug("onLocationTrackingEvent called: " + trackingEvent);
    }

    @Override
    public CommandResponse.ValidateCommandResponse validateCommand(Id runId, ControlCommand controlCommand) {
      return new CommandResponse.Accepted(runId);
    }

    @Override
    public CommandResponse.SubmitResponse onSubmit(Id runId, ControlCommand controlCommand) {
      log.debug("onSubmit called: " + controlCommand);
      return new CommandResponse.Completed(runId);
    }

    @Override
    public void onOneway(Id runId, ControlCommand controlCommand) {
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
    JContainerCmd.start("testhcd", JSubsystem.CSW, args, Optional.of(defaultConfig));
  }
}
