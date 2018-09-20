package csw.qa.location;

import akka.actor.AbstractActor;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.typed.ActorRef;
import akka.japi.Creator;
import akka.actor.typed.javadsl.Adapter;
import csw.location.api.javadsl.ILocationService;
import csw.location.api.models.ComponentId;
import csw.location.api.models.Connection;
import csw.location.client.ActorSystemFactory;
import csw.location.javadsl.JComponentType;
import csw.location.javadsl.JLocationServiceFactory;
import csw.location.scaladsl.RegistrationFactory;
import csw.logging.commons.LogAdminActorFactory;
import csw.logging.javadsl.ILogger;
import csw.logging.javadsl.JGenericLoggerFactory;
import csw.logging.messages.LogControlMessages;
import csw.logging.scaladsl.LoggingSystemFactory;
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
    private static Props props(int i, ILocationService locationService, ActorRef<LogControlMessages> adminActorRef) {
        return Props.create(new Creator<JTestAkkaService>() {
            private static final long serialVersionUID = 1L;

            @Override
            public JTestAkkaService create() {
                return new JTestAkkaService(i, locationService, adminActorRef);
            }
        });
    }

//    private LoggingAdapter log = Logging.getLogger(getContext().system(), this);

    // Constructor: registers self with the location service
    private JTestAkkaService(int i, ILocationService locationService, ActorRef<LogControlMessages> logAdminActorRef) {
        RegistrationFactory registrationFactory = new RegistrationFactory(logAdminActorRef);
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

        ILocationService locationService = JLocationServiceFactory.make();
        ActorSystem system = ActorSystemFactory.remote();

        // Start the logging service
        String host = InetAddress.getLocalHost().getHostName();
        LoggingSystemFactory.start("JTestAkkaService", "0.1", host, system);
        akka.actor.typed.ActorRef<LogControlMessages> adminActorRef = LogAdminActorFactory.make(system);

        for (int i = 1; i <= numServices; i++)
            system.actorOf(JTestAkkaService.props(i, locationService, adminActorRef));
    }
}

