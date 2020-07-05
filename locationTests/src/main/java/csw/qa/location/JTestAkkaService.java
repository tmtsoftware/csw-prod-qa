package csw.qa.location;

import akka.actor.typed.*;
import akka.actor.typed.javadsl.*;
import csw.location.api.AkkaRegistrationFactory;
import csw.location.api.extensions.ActorExtension;
import csw.location.api.javadsl.ILocationService;
import csw.location.api.javadsl.IRegistrationResult;
import csw.location.api.javadsl.JComponentType;
import csw.location.api.models.AkkaRegistration;
import csw.location.api.models.ComponentId;
import csw.location.api.models.Connection;
import csw.location.client.ActorSystemFactory;
import csw.location.client.javadsl.JHttpLocationServiceFactory;
import csw.logging.api.javadsl.ILogger;
import csw.logging.client.commons.AkkaTypedExtension;
import csw.logging.client.javadsl.JGenericLoggerFactory;
import csw.logging.client.scaladsl.LoggingSystemFactory;
import csw.prefix.javadsl.JSubsystem;
import csw.prefix.models.Prefix;

import java.net.InetAddress;
import java.net.URI;
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

  private static final ActorSystem<SpawnProtocol.Command> typedSystem = ActorSystemFactory.remote(SpawnProtocol.create(), "JTestAkkaService");
  private static final AkkaTypedExtension.UserActorFactory userActorFactory = AkkaTypedExtension.UserActorFactory(typedSystem);
  private final ILogger log;

  // Component id for the ith service
  static ComponentId componentId(int i) {
    return new ComponentId(Prefix.apply(JSubsystem.CSW, "TestAkkaService_" + i), JComponentType.Assembly);
  }

  // Connection for the ith service
  private static Connection.AkkaConnection connection(int i) {
    return new Connection.AkkaConnection(componentId(i));
  }

  private JTestAkkaService(ActorContext<ClientMessage> context, int i, ILocationService locationService) {
    super(context);
    log = JGenericLoggerFactory.getLogger(context, getClass());

    URI actorRefURI = ActorExtension.RichActor(context.getSelf()).toURI();
    AkkaRegistration registration = AkkaRegistrationFactory.make(JTestAkkaService.connection(i), actorRefURI);
    try {
      IRegistrationResult regResult = locationService.register(registration).get();
      log.info("Registered " + registration + " with result: " + regResult);
    } catch (Exception e) {
      e.printStackTrace();
    }
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
      numServices = Integer.parseInt(args[0]);
    else
      numServices = 1;

    ILocationService locationService = JHttpLocationServiceFactory.makeLocalClient(typedSystem);

    // Start the logging service
    String host = InetAddress.getLocalHost().getHostName();
    LoggingSystemFactory.start("JTestAkkaService", "0.1", host, typedSystem);

    for (int i = 1; i <= numServices; i++)
      userActorFactory.spawn(JTestAkkaService.behavior(i, locationService), "JTestAkkaService" + i,
          Props.empty());
  }
}

