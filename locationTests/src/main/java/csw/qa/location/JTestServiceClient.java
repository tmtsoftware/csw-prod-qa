package csw.qa.location;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.japi.Creator;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Sink;
import csw.messages.location.AkkaLocation;
import csw.messages.location.Connection;
import csw.messages.location.LocationRemoved;
import csw.messages.location.LocationUpdated;
import csw.services.location.javadsl.ILocationService;
import csw.services.location.javadsl.JLocationServiceFactory;
import csw.services.location.scaladsl.ActorSystemFactory;
import akka.typed.javadsl.Adapter;
import csw.services.logging.javadsl.ILogger;
import csw.services.logging.javadsl.JComponentLoggerActor;
import csw.services.logging.scaladsl.LoggingSystemFactory;

import java.io.Serializable;
import java.net.InetAddress;
import java.net.UnknownHostException;

import static csw.services.location.javadsl.JConnectionType.AkkaType;

abstract class JTestServiceClientLoggerActor extends JComponentLoggerActor {
    @Override
    public String componentName() {
        return "JTestServiceClient";
    }
}

/**
 * A location service test client application that attempts to resolve one or more sets of
 * akka services.
 */
public class JTestServiceClient extends JTestServiceClientLoggerActor {

    private ILogger log = getLogger();

    // Used to create the ith JTestServiceClient actor
    private static Props props(int numServices, ILocationService locationService) {
        return Props.create(new Creator<JTestServiceClient>() {
            private static final long serialVersionUID = 1L;

            @Override
            public JTestServiceClient create() throws Exception {
                return new JTestServiceClient(numServices, locationService);
            }
        });
    }

    // message sent when location stream ends (should not happen?)
    private static class AllDone implements Serializable {
    }

    // Connection for the ith service
    private static Connection.AkkaConnection connection(int i) {
        return new Connection.AkkaConnection(JTestAkkaService.componentId(i));
    }

    // Constructor: tracks the given number of akka connections
    private JTestServiceClient(int numServices, ILocationService locationService) {
        ActorMaterializer mat = ActorMaterializer.create(context());
        for (int i = 1; i <= numServices; i++) {
            locationService.track(connection(i)).to(Sink.actorRef(self(), new AllDone())).run(mat);
        }
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(LocationUpdated.class, loc -> {
                    log.info("Location updated: " + loc);
                    if (loc.connection().connectionType() == AkkaType) {
                        ActorRef actorRef = Adapter.toUntyped(((AkkaLocation)loc.location()).actorRef());
                        actorRef.tell(new ClientMessage(Adapter.toTyped(getSelf())), self());
                    }
                })
                .match(LocationRemoved.class, loc -> log.info("Location removed: " + loc))
                .matchAny(x -> log.warn("Unknown message received: " + x))
                .build();
    }

    // If a command line arg is given, it should be the number of services to resolve (default: 1).
    public static void main(String[] args) throws UnknownHostException {
        int numServices = 1;
        if (args.length != 0)
            numServices = Integer.valueOf(args[0]);

        ILocationService locationService = JLocationServiceFactory.make();
        ActorSystem system = ActorSystemFactory.remote();

        // Start the logging service
        String host = InetAddress.getLocalHost().getHostName();
        LoggingSystemFactory.start("JTestServiceClient", "0.1", host, system);

        system.actorOf(JTestServiceClient.props(numServices, locationService));
    }
}
