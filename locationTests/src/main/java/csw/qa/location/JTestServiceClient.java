//package csw.qa.location;
//
//import akka.actor.AbstractActor;
//import akka.actor.ActorRef;
//import akka.actor.ActorSystem;
//import akka.actor.Props;
//import akka.event.Logging;
//import akka.event.LoggingAdapter;
//import akka.japi.Creator;
//import akka.stream.ActorMaterializer;
//import akka.stream.javadsl.Sink;
//import csw.services.location.javadsl.ILocationService;
//import csw.services.location.javadsl.JLocationServiceFactory;
//import csw.services.location.models.*;
//import csw.services.location.scaladsl.ActorSystemFactory;
//import csw.services.location.models.Connection.AkkaConnection;
//import akka.typed.javadsl.Adapter;
//
//import java.io.Serializable;
//
//import static csw.services.location.javadsl.JConnectionType.AkkaType;
//
///**
// * A location service test client application that attempts to resolve one or more sets of
// * akka services.
// */
//public class JTestServiceClient extends AbstractActor {
//
//    // Used to create the ith JTestServiceClient actor
//    private static Props props(int numServices, ILocationService locationService) {
//        return Props.create(new Creator<JTestServiceClient>() {
//            private static final long serialVersionUID = 1L;
//
//            @Override
//            public JTestServiceClient create() throws Exception {
//                return new JTestServiceClient(numServices, locationService);
//            }
//        });
//    }
//
//    // message sent when location stream ends (should not happen?)
//    private static class AllDone implements Serializable {
//    }
//
//    // Connection for the ith service
//    private static AkkaConnection connection(int i) {
//        return new AkkaConnection(JTestAkkaService.componentId(i));
//    }
//
//    private LoggingAdapter log = Logging.getLogger(getContext().system(), this);
//
//    // Constructor: tracks the given number of akka connections
//    private JTestServiceClient(int numServices, ILocationService locationService) {
//        ActorMaterializer mat = ActorMaterializer.create(context());
//        for (int i = 0; i < numServices; i++) {
//            locationService.track(connection(i)).to(Sink.actorRef(self(), new AllDone())).run(mat);
//        }
//    }
//
//    @Override
//    public Receive createReceive() {
//        return receiveBuilder()
//                .match(LocationUpdated.class, loc -> {
//                    log.info("Location updated: " + loc);
//                    if (loc.connection().connectionType() == AkkaType) {
//                        ActorRef actorRef = Adapter.toUntyped(((AkkaLocation)loc.location()).actorRef());
//                        actorRef.tell(new JTestAkkaService.ClientMessage(), self());
//                    }
//                })
//                .match(LocationRemoved.class, loc -> log.info("Location removed: " + loc))
//                .matchAny(x -> log.warning("Unknown message received: " + x))
//                .build();
//    }
//
//    // If a command line arg is given, it should be the number of services to resolve (default: 1).
//    public static void main(String[] args) {
//        int numServices = 1;
//        if (args.length != 0)
//            numServices = Integer.valueOf(args[0]);
//
//        ILocationService locationService = JLocationServiceFactory.make();
//        ActorSystem system = ActorSystemFactory.remote();
//        system.actorOf(JTestServiceClient.props(numServices, locationService));
//    }
//}
