package csw.qa.location;

import akka.actor.typed.*;
import akka.actor.typed.javadsl.*;
import csw.location.api.javadsl.ILocationService;
import csw.location.client.ActorSystemFactory;
import csw.location.client.javadsl.JHttpLocationServiceFactory;
import csw.location.models.Connection;
import csw.location.models.LocationUpdated;
import csw.location.models.TrackingEvent;
import csw.logging.api.javadsl.ILogger;
import csw.logging.client.commons.AkkaTypedExtension;
import csw.logging.client.javadsl.JGenericLoggerFactory;
import csw.logging.client.scaladsl.LoggingSystemFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * A location service test client application that attempts to resolve one or more sets of
 * akka services.
 */
@SuppressWarnings("FieldCanBeLocal")
public class JTestServiceClient extends AbstractBehavior<ServiceClientMessageType> {

  private static ActorSystem<SpawnProtocol.Command> typedSystem = ActorSystemFactory.remote(SpawnProtocol.create(), "JTestServiceClient");
  private static AkkaTypedExtension.UserActorFactory userActorFactory = AkkaTypedExtension.UserActorFactory(typedSystem);
  private final ILogger log;

  // Connection for the ith service
  private static Connection.AkkaConnection connection(int i) {
    return new Connection.AkkaConnection(JTestAkkaService.componentId(i));
  }

  // Constructor: tracks the given number of akka connections
  private JTestServiceClient(ActorContext<ServiceClientMessageType> ctx, int numServices, ILocationService locationService) {
    super(ctx);
    log = JGenericLoggerFactory.getLogger(ctx, getClass());
    for (int i = 1; i <= numServices; i++) {
      locationService.track(connection(i)).runForeach(trackingEvent -> ctx.getSelf().tell(
          new TrackingEventMessage(trackingEvent)), ctx.getSystem());
    }
  }

  @Override
  public Receive<ServiceClientMessageType> createReceive() {
    return newReceiveBuilder().onMessage(TrackingEventMessage.class, msg -> {
      TrackingEvent loc = msg.event();
      if (loc instanceof LocationUpdated) {
//        LocationUpdated locUpdate = (LocationUpdated) loc;
        log.info("Location updated: " + loc);
//        if (loc.connection().connectionType() == JConnectionType.AkkaType()) {
////          URI uri = locUpdate.location().uri();
//
//          // Note: Need to cast the actorRef.
////          ActorRef<?> actorRef = new URIExtension.RichURI(uri).toActorRef(typedSystem);
//          // TODO: configure serialization: Search for "serialization-bindings" in the csw config files.
////          actorRef.unsafeUpcast().tell(new ClientMessage(ctx.getSelf()));
//        }
      } else {
        log.info("Location removed: " + loc);
      }
      return this;
    }).build();
  }


  private static Behavior<ServiceClientMessageType> behavior(int numServices, ILocationService locationService) {
    return Behaviors.setup(ctx -> new JTestServiceClient(ctx, numServices, locationService));
  }


  // If a command line arg is given, it should be the number of services to resolve (default: 1).
  public static void main(String[] args) throws UnknownHostException {
    final int numServices;
    if (args.length != 0)
      numServices = Integer.parseInt(args[0]);
    else
      numServices = 1;

    ILocationService locationService = JHttpLocationServiceFactory.makeLocalClient(typedSystem);

    // Start the logging service
    String host = InetAddress.getLocalHost().getHostName();
    LoggingSystemFactory.start("JTestServiceClient", "0.1", host, typedSystem);

    userActorFactory.spawn(JTestServiceClient.behavior(numServices, locationService), "JTestServiceClient",
        Props.empty());

  }
}
