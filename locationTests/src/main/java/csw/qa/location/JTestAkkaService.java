package csw.qa.location;

import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.japi.Creator;
import csw.services.location.javadsl.ILocationService;
import csw.services.location.javadsl.JComponentType;
import csw.services.location.javadsl.JLocationServiceFactory;
import csw.services.location.models.AkkaRegistration;
import csw.services.location.models.ComponentId;
import csw.services.location.scaladsl.ActorSystemFactory;
import csw.services.location.models.Connection.AkkaConnection;
import akka.typed.javadsl.Adapter;
import csw.services.logging.javadsl.ILogger;
import csw.services.logging.javadsl.JComponentLoggerActor;
import csw.services.logging.scaladsl.LoggingSystemFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;

abstract class JTestAkkaServiceLoggerActor extends JComponentLoggerActor {
    @Override
    public String componentName() {
        return "JTestAkkaService";
    }
}

/**
 * Starts one or more akka services in order to test the location service.
 * If a command line arg is given, it should be the number of services to start (default: 1).
 * Each service will have a number appended to its name.
 * You should start the TestServiceClient with the same number, so that it
 * will try to find all the services.
 * The client and service applications can be run on the same or different hosts.
 */
public class JTestAkkaService extends JTestAkkaServiceLoggerActor {

    private ILogger log = getLogger();

    // Component id for the ith service
    static ComponentId componentId(int i) {
        return new ComponentId("TestAkkaService_" + i, JComponentType.Assembly);
    }

    // Connection for the ith service
    private static AkkaConnection connection(int i) {
        return new AkkaConnection(componentId(i));
    }

    // Used to create the ith JTestAkkaService actor
    private static Props props(int i, ILocationService locationService) {
        return Props.create(new Creator<JTestAkkaService>() {
            private static final long serialVersionUID = 1L;

            @Override
            public JTestAkkaService create() throws Exception {
                return new JTestAkkaService(i, locationService);
            }
        });
    }

//    private LoggingAdapter log = Logging.getLogger(getContext().system(), this);

    // Constructor: registers self with the location service
    private JTestAkkaService(int i, ILocationService locationService) {
        locationService.register(new AkkaRegistration(JTestAkkaService.connection(i), Adapter.toTyped(self())));
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

        ILocationService locationService = JLocationServiceFactory.make();
        ActorSystem system = ActorSystemFactory.remote();

        // Start the logging service
        String host = InetAddress.getLocalHost().getHostName();
        LoggingSystemFactory.start("JTestAkkaService", "0.1", host, system);

        for (int i = 1; i <= numServices; i++)
            system.actorOf(JTestAkkaService.props(i, locationService));
    }
}

