package csw.qa.framework;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.util.Timeout;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import csw.command.api.javadsl.ICommandService;
import csw.command.client.CommandResponseManager;
import csw.command.client.CommandServiceFactory;
import csw.command.client.messages.TopLevelActorMessage;
import csw.event.api.javadsl.IEventPublisher;
import csw.event.api.javadsl.IEventSubscriber;
import csw.event.api.javadsl.IEventSubscription;
import csw.framework.javadsl.JComponentBehaviorFactory;
import csw.framework.javadsl.JComponentHandlers;
import csw.framework.javadsl.JContainerCmd;
import csw.framework.models.JCswContext;
import csw.location.api.models.AkkaLocation;
import csw.location.api.models.LocationUpdated;
import csw.location.api.models.TrackingEvent;
import csw.logging.api.javadsl.ILogger;
import csw.params.commands.CommandResponse;
import csw.params.commands.ControlCommand;
import csw.params.commands.Setup;
import csw.params.core.generics.Key;
import csw.params.core.models.Id;
import csw.params.events.*;
import csw.params.javadsl.JKeyType;
import csw.prefix.javadsl.JSubsystem;
import csw.prefix.models.Prefix;
import csw.time.core.models.UTCTime;

import static java.util.concurrent.CompletableFuture.completedFuture;

import java.util.Collections;
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


  // Key for HCD events
  private static final Key<Integer> hcdEventValueKey = JKeyType.IntKey().make("hcdEventValue");
  private static final EventName hcdEventName = new EventName("myHcdEvent");
  private static final Prefix hcdPrefix = Prefix.apply(JSubsystem.CSW, "hcd");

  // Dummy key for publishing events from assembly
  private static final Key<Integer> eventKey = JKeyType.IntKey().make("assemblyEventValue");
  private static final EventName eventName = new EventName("myAssemblyEvent");

  // Actor to receive HCD events
  private static Behavior<Event> eventHandler(ILogger log, IEventPublisher publisher, SystemEvent baseEvent) {
    return Behaviors.receive(Event.class)
        .onMessage(SystemEvent.class, e -> {
          e.jGet(hcdEventValueKey)
              .ifPresent(p -> {
                Integer eventValue = p.head();
                log.info("Received event with value: " + eventValue);
                // fire a new event from the assembly based on the one from the HCD
                SystemEvent se = new SystemEvent(baseEvent.source(), baseEvent.eventName())
                    .add(eventKey.set(eventValue));
                publisher.publish(se);
              });
          return Behaviors.same();
        }).onMessage(Event.class, e -> {
          throw new RuntimeException("Expected SystemEvent");
        }).build();
  }

  @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
  static class JTestAssemblyHandlers extends JComponentHandlers {
    private final ILogger log;
    private final ActorContext<TopLevelActorMessage> ctx;
    private final JCswContext cswServices;
    // Set when the location is received from the location service (below)
    private Optional<ICommandService> testHcd = Optional.empty();

    // Event that the HCD publishes (must match the names defined by the publisher (TestHcd))
    private final EventKey hcdEventKey = EventKey.apply(hcdPrefix, hcdEventName);


    JTestAssemblyHandlers(ActorContext<TopLevelActorMessage> ctx,
                          JCswContext cswServices) {
      super(ctx, cswServices);

      this.log = cswServices.loggerFactory().getLogger(this.getClass());
      this.ctx = ctx;
      this.cswServices = cswServices;
      log.debug("Starting Test Assembly");
    }

    @Override
    public void jInitialize() {
      log.debug("jInitialize called");
      startSubscribingToEvents();
    }

    @Override
    public void jOnShutdown() {
      log.debug("onShutdown called");
    }

    @Override
    public CommandResponse.ValidateCommandResponse validateCommand(Id runId, ControlCommand controlCommand) {
      return new CommandResponse.Accepted(runId);
    }

    @Override
    public CommandResponse.SubmitResponse onSubmit(Id runId, ControlCommand controlCommand) {
      log.debug("onSubmit called: " + controlCommand);
      forwardCommandToHcd(runId, controlCommand);
      return new CommandResponse.Started(runId);
    }

    // For testing, forward command to HCD and complete this command when it completes
    private void forwardCommandToHcd(Id runId, ControlCommand controlCommand) {
      CommandResponseManager commandResponseManager = cswServices.commandResponseManager();
      testHcd.ifPresent(hcd -> {
        Timeout timeout = new Timeout(3, TimeUnit.SECONDS);
        Setup setup = new Setup(controlCommand.source(), controlCommand.commandName(), controlCommand.jMaybeObsId());
//        commandResponseManager.addSubCommand(controlCommand.runId(), setup.runId());
        try {
          CommandResponse.SubmitResponse response = hcd.submitAndWait(setup, timeout).get();
          log.info("response = " + response);
//          commandResponseManager.updateSubCommand(response);
        } catch (Exception ex) {
//          commandResponseManager.updateSubCommand(new CommandResponse.Error(setup.runId(), ex.toString()));
        }
      });
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
    public void onLocationTrackingEvent(TrackingEvent trackingEvent) {
      log.debug("onLocationTrackingEvent called: " + trackingEvent);
      if (trackingEvent instanceof LocationUpdated) {
        AkkaLocation location = (AkkaLocation) ((LocationUpdated) trackingEvent).location();
        testHcd = Optional.of(CommandServiceFactory.jMake(location, ctx.getSystem()));
      } else testHcd = Optional.empty();
    }

    @Override
    public void onDiagnosticMode(UTCTime startTime, String hint) {
    }

    @Override
    public void onOperationsMode() {
    }

    private CompletableFuture<IEventSubscription> startSubscribingToEvents() {
      IEventSubscriber subscriber = cswServices.eventService().defaultSubscriber();
      IEventPublisher publisher = cswServices.eventService().defaultPublisher();
      SystemEvent baseEvent = new SystemEvent(cswServices.componentInfo().prefix(), eventName).add(eventKey.set(0));
      ActorRef<Event> eventHandlerActor = ctx.spawn(eventHandler(log, publisher, baseEvent), "eventHandlerActor");
      IEventSubscription subscription = subscriber.subscribeActorRef(Collections.singleton(hcdEventKey), eventHandlerActor);
      return completedFuture(subscription);
    }
  }

  public static void main(String[] args) {
//    Async.init(); // required for Java ea-async: See https://github.com/electronicarts/ea-async
    Config defaultConfig = ConfigFactory.load("JTestAssembly.conf");
    JContainerCmd.start("testassembly", JSubsystem.CSW, args, Optional.of(defaultConfig));
  }
}
