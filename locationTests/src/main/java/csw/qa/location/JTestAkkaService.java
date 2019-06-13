package csw.qa.location;

import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.SpawnProtocol;
import akka.actor.typed.javadsl.*;
import akka.stream.Materializer;
import akka.stream.typed.javadsl.ActorMaterializerFactory;
import csw.framework.scaladsl.RegistrationFactory;
import csw.location.api.javadsl.ILocationService;
import csw.location.api.javadsl.JComponentType;
import csw.location.api.models.ComponentId;
import csw.location.api.models.Connection;
import csw.location.client.ActorSystemFactory;
import csw.location.client.javadsl.JHttpLocationServiceFactory;
import csw.logging.api.javadsl.ILogger;
import csw.logging.client.javadsl.JGenericLoggerFactory;
import csw.logging.client.scaladsl.LoggingSystemFactory;
import csw.params.core.models.Prefix;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Starts one or more akka services in order to test the location service.
 * If a command line arg is given, it should be the number of services to start (default: 1).
 * Each service will have a number appended to its name.
 * You should start the TestServiceClient with the same number, so that it
 * will try to find all the services.
 * The client and service applications can be run on the same or different hosts.
 */
public class JTestAkkaService extends AbstractBehavior<ClientMessage> {

  private final ILogger log;

  // Component id for the ith service
  static ComponentId componentId(int i) {
    return new ComponentId("TestAkkaService_" + i, JComponentType.Assembly);
  }

  // Connection for the ith service
  private static Connection.AkkaConnection connection(int i) {
    return new Connection.AkkaConnection(componentId(i));
  }

  private JTestAkkaService(ActorContext<ClientMessage> context, int i, ILocationService locationService) {
    log = JGenericLoggerFactory.getLogger(context, getClass());
    RegistrationFactory registrationFactory = new RegistrationFactory();
    locationService.register(registrationFactory.akkaTyped(JTestAkkaService.connection(i), new Prefix("test.prefix"), context.getSelf()));
  }

  @Override
  public Receive<ClientMessage> createReceive() {
    return newReceiveBuilder().onMessage(
        ClientMessage.class,
        msg -> {
          log.info("Received java client message from: " + msg.replyTo());
          return this;
        }).build();
  }

  private static Behavior<ClientMessage> behavior(int i, ILocationService locationService) {
    return Behaviors.setup(ctx -> new JTestAkkaService(ctx, i, locationService));
  }

  // main: Starts and registers the given number of services (default: 1)
  public static void main(String[] args) throws UnknownHostException {
    final int numServices;
    if (args.length != 0)
      numServices = Integer.valueOf(args[0]);
    else
      numServices = 1;

    ActorSystem<SpawnProtocol> typedSystem = ActorSystemFactory.remote(SpawnProtocol.behavior(), "JTestAkkaService");
    Materializer mat = ActorMaterializerFactory.create(typedSystem);
    ILocationService locationService = JHttpLocationServiceFactory.makeLocalClient(typedSystem, mat);

    // Start the logging service
    String host = InetAddress.getLocalHost().getHostName();
    LoggingSystemFactory.start("JTestAkkaService", "0.1", host, typedSystem);

    Behaviors.setup(
        context -> {
          for (int i = 1; i <= numServices; i++)
            context.spawnAnonymous(JTestAkkaService.behavior(i, locationService));
          return Behaviors.same();
        }
    );
  }
}

