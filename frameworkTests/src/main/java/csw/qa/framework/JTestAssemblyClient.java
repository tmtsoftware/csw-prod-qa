package csw.qa.framework;

import akka.actor.ActorSystem;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Adapter;
import akka.actor.typed.javadsl.Behaviors;
import akka.stream.ActorMaterializer;
import akka.util.Timeout;
import csw.command.api.javadsl.ICommandService;
import csw.command.client.CommandServiceFactory;
import csw.event.api.javadsl.IEventService;
import csw.event.api.javadsl.IEventSubscriber;
import csw.event.client.EventServiceFactory;
import csw.location.api.javadsl.ILocationService;
import csw.location.api.javadsl.JComponentType;
import csw.location.api.models.*;
import csw.location.client.ActorSystemFactory;
import csw.location.client.javadsl.JHttpLocationServiceFactory;
import csw.logging.api.javadsl.ILogger;
import csw.logging.client.javadsl.JGenericLoggerFactory;
import csw.logging.client.scaladsl.LoggingSystemFactory;
import csw.params.commands.CommandName;
import csw.params.commands.CommandResponse;
import csw.params.commands.ControlCommand;
import csw.params.commands.Setup;
import csw.params.core.generics.Key;
import csw.params.core.generics.Parameter;
import csw.params.core.models.ObsId;
import csw.params.core.models.Prefix;
import csw.params.events.Event;
import csw.params.events.EventKey;
import csw.params.events.EventName;
import csw.params.events.SystemEvent;
import csw.params.javadsl.JKeyType;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;


// A client to test locating and communicating with the Test assembly
public class JTestAssemblyClient {


  private static final Timeout timeout = new Timeout(3, TimeUnit.SECONDS);

  // Key for events from assembly
  private static final Key<Integer> assemblyEventValueKey = JKeyType.IntKey().make("assemblyEventValue");
  private static final EventName assemblyEventName = new EventName("myAssemblyEvent");
  private static final Prefix assemblyPrefix = new Prefix("test.assembly");
  // Event that the HCD publishes (must match the names defined by the publisher (TestHcd))
  private static final EventKey assemblyEventKey = new EventKey(assemblyPrefix, assemblyEventName);

  private static final ObsId obsId = new ObsId("2023-Q22-4-33");
  private static final Key<Integer> encoderKey = JKeyType.IntKey().make("encoder");
  private static final Key<String> filterKey = JKeyType.StringKey().make("filter");
  private static final Prefix prefix = new Prefix("wfos.blue.filter");
  private static final CommandName command = new CommandName("myCommand");


  private static final ComponentId componentId = new ComponentId("TestAssembly", JComponentType.Assembly);

  private static final Connection.AkkaConnection connection = new Connection.AkkaConnection(componentId);


  private final ActorSystem system = ActorSystemFactory.remote();
  private final akka.actor.typed.ActorSystem<Void> typedSystem = Adapter.toTyped(system);

  private final ActorMaterializer mat = ActorMaterializer.create(system);
  private final ILocationService locationService = JHttpLocationServiceFactory.makeLocalClient(system, mat);
  private final ILogger log = JGenericLoggerFactory.getLogger(JTestAssemblyClient.class);

  private final IEventService eventService = (new EventServiceFactory()).jMake(locationService, system);


  // Actor to receive HCD events
  private Behavior<Event> eventHandler() {
    return Behaviors.receive(Event.class)
        .onMessage(SystemEvent.class, (ctx, e) -> {
          e.jGet(assemblyEventValueKey)
              .ifPresent(p -> {
                Integer eventValue = p.head();
                log.info("Received event with value: " + eventValue);
              });
          return Behaviors.same();
        }).onMessage(Event.class, (ctx, o) -> {
          throw new RuntimeException("Expected SystemEvent");
        }).build();
  }


  private JTestAssemblyClient() {
  }

  private void start() throws UnknownHostException {
    // Start the logging service
    String host = InetAddress.getLocalHost().getHostName();
    LoggingSystemFactory.start("JTestAssemblyClient", "0.1", host, system);

    Adapter.spawn(system, initialBehavior(), "TestAssemblyClient");
  }

  private void startSubscribingToEvents(ActorContext<TrackingEvent> ctx) {
    IEventSubscriber subscriber = eventService.defaultSubscriber();
    ActorRef<Event> eventHandler = ctx.spawn(eventHandler(), "EventHandler");
    subscriber.subscribeActorRef(Collections.singleton(assemblyEventKey), eventHandler);
  }


  private Behavior<TrackingEvent> initialBehavior() {
    return Behaviors.setup((ActorContext<TrackingEvent> ctx) -> {
      locationService.subscribe(connection, loc -> ctx.getSelf().tell(loc));
      startSubscribingToEvents(ctx);
      return subscriberBehavior();
    });
  }


  private Behavior<TrackingEvent> subscriberBehavior() {
    return Behaviors.receive(TrackingEvent.class)
        .onMessage(LocationUpdated.class, (ctx, msg) -> {
          Location loc = msg.location();
          log.info("LocationUpdated: " + loc);
          interact(ctx, CommandServiceFactory.jMake((AkkaLocation)loc, typedSystem));
          return Behaviors.same();
        }).build();
  }


  private Setup makeSetup(int encoder, String filter) {
    Parameter<Integer> i1 = encoderKey.set(encoder);
    Parameter<String> i2 = filterKey.set(filter);
    return new Setup(prefix, command, Optional.of(obsId)).add(i1).add(i2);
  }

  private void interact(ActorContext<TrackingEvent> ctx, ICommandService assembly) {
    List<ControlCommand> setups = new ArrayList<>();
    for (int i = 1; i <= 10; i++) {
      setups.add(makeSetup(i, "filter" + i));
      try {
        List<CommandResponse.SubmitResponse> responses = assembly.submitAll(setups, timeout).get();
        System.out.println("Test Passed: Responses = " + responses);
      } catch(Exception ex) {
        System.out.println("Test Failed: " + ex);
      }
    }
  }


  // If a command line arg is given, it should be the number of services to resolve (default: 1).
  public static void main(String[] args) throws UnknownHostException {
    JTestAssemblyClient client = new JTestAssemblyClient();
    client.start();
  }
}

