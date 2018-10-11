Location Service Tests
======================

This project contains some simple applications using the location service.

See [../README.md](../README.md) for how to set up the correct environment.

Scala Version
-------------

* TestAkkaServiceApp - Starts one or more akka services in order to test the location service.
  If a command line arg is given, it should be the number of services to start (default: 1).
  An additional command line arg indicates the service number to start with (default: 1).
  Each service will have a number appended to its name.
  You should start the TestServiceClient with the same number, so that it
  will try to find all the services.
  The client and service applications can be run on the same or different hosts.
  
  Example command line: This starts and registers ten actors: 
  `test-akka-service-app --numServices 10`

* TestServiceClientApp - A location service test client application that attempts to resolve one or more sets of
  akka services. If a command line arg is given, it should be the number of services to resolve (default: 1).
  
  Example: This looks up the 10 actors with the location service and sends each one a message: 
  `test-service-client-app --numServices 10`

Java Version
------------

* JTestAkkaService - Java version of TestAkkaServiceApp above:

  Example: `j-test-akka-service 10`

* JestServiceClient - Java version of TestServiceClientApp above:

  Example: `j-test-service-client 10`

Once the 3 applications are running, you should see log messages indicating that the connections were located.
If you kill the TestAkkaService application, the TestServiceClient should receive a LocationRemoved message.
