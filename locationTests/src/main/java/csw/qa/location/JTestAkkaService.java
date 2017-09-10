//package csw.qa.location;
//
//import akka.actor.AbstractActor;
//import akka.actor.ActorSystem;
//import akka.actor.Props;
//import akka.event.Logging;
//import akka.event.LoggingAdapter;
//import akka.japi.Creator;
//import csw.services.location.javadsl.ILocationService;
//import csw.services.location.javadsl.JComponentType;
//import csw.services.location.javadsl.JLocationServiceFactory;
//import csw.services.location.models.AkkaRegistration;
//import csw.services.location.models.ComponentId;
//import csw.services.location.scaladsl.ActorSystemFactory;
//import csw.services.location.models.Connection.AkkaConnection;
//
//import java.io.Serializable;
//
///**
// * Starts one or more akka services in order to test the location service.
// * If a command line arg is given, it should be the number of services to start (default: 1).
// * Each service will have a number appended to its name.
// * You should start the TestServiceClient with the same number, so that it
// * will try to find all the services.
// * The client and service applications can be run on the same or different hosts.
// */
//public class JTestAkkaService extends AbstractActor {
//    // Component id for the ith service
//    static ComponentId componentId(int i) {
//        return new ComponentId("TestAkkaService_" + i, JComponentType.Assembly);
//    }
//
//    // Connection for the ith service
//    private static AkkaConnection connection(int i) {
//        return new AkkaConnection(componentId(i));
//    }
//
//    // Used to create the ith JTestAkkaService actor
//    private static Props props(int i, ILocationService locationService) {
//        return Props.create(new Creator<JTestAkkaService>() {
//            private static final long serialVersionUID = 1L;
//
//            @Override
//            public JTestAkkaService create() throws Exception {
//                return new JTestAkkaService(i, locationService);
//            }
//        });
//    }
//
//    private LoggingAdapter log = Logging.getLogger(getContext().system(), this);
//
//    // Message sent from client once location has been resolved
//    public static class ClientMessage implements Serializable {
//    }
//
//    // Constructor: registers self with the location service
//    private JTestAkkaService(int i, ILocationService locationService) {
//        locationService.register(new AkkaRegistration(JTestAkkaService.connection(i), self()));
//    }
//
//    @Override
//    public Receive createReceive() {
//        return receiveBuilder()
//                .match(ClientMessage.class, loc -> log.info("Received java client message from: " + sender()))
//                .matchEquals(TestAkkaService.ClientMessage$.MODULE$, loc -> log.info("Received scala client message from: " + sender()))
//                .matchAny(t -> log.warning("Unknown message received: " + t))
//                .build();
//    }
//
//    // main: Starts and registers the given number of services (default: 1)
//    public static void main(String[] args) {
//        int numServices = 1;
//        if (args.length != 0)
//            numServices = Integer.valueOf(args[0]);
//
//        ILocationService locationService = JLocationServiceFactory.make();
//        ActorSystem system = ActorSystemFactory.remote();
//        for (int i = 0; i < numServices; i++)
//            system.actorOf(JTestAkkaService.props(i + 1, locationService));
//    }
//}
//
