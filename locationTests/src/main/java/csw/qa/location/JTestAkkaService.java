package csw.qa.location;

import akka.actor.AbstractActor;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.typed.javadsl.Adapter;
import akka.stream.ActorMaterializer;
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
public class JTestAkkaService extends AbstractActor {

  private ILogger log = JGenericLoggerFactory.getLogger(context(), getClass());

    // Component id for the ith service
    static ComponentId componentId(int i) {
        return new ComponentId("TestAkkaService_" + i, JComponentType.Assembly);
    }

    // Connection for the ith service
    private static Connection.AkkaConnection connection(int i) {
        return new Connection.AkkaConnection(componentId(i));
    }

    // Used to create the ith JTestAkkaService actor
    private static Props props(int i, ILocationService locationService) {
        return Props.create(JTestAkkaService.class, () -> new JTestAkkaService(i, locationService));
    }

    // Constructor: registers self with the location service
    private JTestAkkaService(int i, ILocationService locationService) {
        RegistrationFactory registrationFactory = new RegistrationFactory();
        locationService.register(registrationFactory.akkaTyped(JTestAkkaService.connection(i), new Prefix("test.prefix"), Adapter.toTyped(self())));
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(ClientMessage.class, loc -> log.info("Received java client message from: " + sender()))
                .matchAny(t -> log.warn("Unknown message received: " + t))
                .build();
    }

    // main: Starts and registers the given number of services (default: 1)
    public static void main(String[] args) throws UnknownHostException {
        int numServices = 1;
        if (args.length != 0)
            numServices = Integer.valueOf(args[0]);

      ActorSystem system = ActorSystemFactory.remote();
      ActorMaterializer mat = ActorMaterializer.create(system);
      ILocationService locationService = JHttpLocationServiceFactory.makeLocalClient(system, mat);

        // Start the logging service
        String host = InetAddress.getLocalHost().getHostName();
        LoggingSystemFactory.start("JTestAkkaService", "0.1", host, system);

        for (int i = 1; i <= numServices; i++)
            system.actorOf(JTestAkkaService.props(i, locationService));
    }
}

