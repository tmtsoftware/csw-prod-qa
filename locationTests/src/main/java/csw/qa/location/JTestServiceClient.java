package csw.qa.location;

import akka.actor.typed.*;
import akka.actor.typed.javadsl.*;
import akka.stream.ActorMaterializer;
import akka.stream.typed.javadsl.ActorMaterializerFactory;
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
import java.net.URI;
import java.net.UnknownHostException;

import static csw.location.api.javadsl.JConnectionType.AkkaType;

/**
 * A location service test client application that attempts to resolve one or more sets of
 * akka services.
 */
public class JTestServiceClient extends AbstractBehavior<ServiceClientMessageType> {

  private static ActorSystem<SpawnProtocol> typedSystem = ActorSystemFactory.remote(SpawnProtocol.behavior(), "JTestServiceClient");
  private static ActorMaterializer mat = ActorMaterializerFactory.create(typedSystem);
  private static AkkaTypedExtension.UserActorFactory userActorFactory = AkkaTypedExtension.UserActorFactory(typedSystem);
  private final ILogger log;
  private final ActorContext<ServiceClientMessageType> ctx;

  // Connection for the ith service
  private static Connection.AkkaConnection connection(int i) {
    return new Connection.AkkaConnection(JTestAkkaService.componentId(i));
  }

  // Constructor: tracks the given number of akka connections
  private JTestServiceClient(ActorContext<ServiceClientMessageType> ctx, int numServices, ILocationService locationService) {
    log = JGenericLoggerFactory.getLogger(ctx, getClass());
    this.ctx = ctx;
    for (int i = 1; i <= numServices; i++) {
      locationService.track(connection(i)).runForeach(trackingEvent -> ctx.getSelf().tell(
          new TrackingEventMessage(trackingEvent)), mat);
    }
  }

  @Override
  public Receive<ServiceClientMessageType> createReceive() {
    return newReceiveBuilder().onMessage(TrackingEventMessage.class, msg -> {
      TrackingEvent loc = msg.event();
      if (loc instanceof LocationUpdated) {
        LocationUpdated locUpdate = (LocationUpdated) loc;
        log.info("Location updated: " + loc);
        if (loc.connection().connectionType() == AkkaType) {
          URI uri = locUpdate.location().uri();

          // Note: Need to cast the actorRef.
//          ActorRef<?> actorRef = new URIExtension.RichURI(uri).toActorRef(typedSystem);
          // TODO: configure serialization: Search for "serialization-bindings" in the csw config files.
//          actorRef.unsafeUpcast().tell(new ClientMessage(ctx.getSelf()));
        }
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
      numServices = Integer.valueOf(args[0]);
    else
      numServices = 1;

    ILocationService locationService = JHttpLocationServiceFactory.makeLocalClient(typedSystem, mat);

    // Start the logging service
    String host = InetAddress.getLocalHost().getHostName();
    LoggingSystemFactory.start("JTestServiceClient", "0.1", host, typedSystem);

    userActorFactory.spawn(JTestServiceClient.behavior(numServices, locationService), "JTestServiceClient",
        Props.empty());

  }
}
