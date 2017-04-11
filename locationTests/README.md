Location Service Tests
======================

This project contains some simple applications using the location service.

Before running the applications here, the location service needs to be started. For example:

    cd csw-prod/apps/csw-cluster-seed/target/universal/stage/bin
    csw-cluster-seed -DclusterPort=3552 -DinterfaceName=enp0s31f6

Replace the value of `interfaceName` with the name of the network interface you want the location service to use.
(Use `ifconfig -a` to list the network interfaces.)

These properties can also be specified as environment variables. Note that if interfaceName is not specified,
the location service might choose the wrong one by default (For example, a virtual vmware network or the wireless network).

The commands described below can be found under `csw-prod-qa/locationTests/target/universal/stage/bin`.
You can run them in different terminals, on the same or different hosts.

Make sure to use the same `interfaceName` as above, if running on the same host. 
In addition, the `clusterSeeds` property must indicate the IP address and port number of the location 
service `csw-cluster-seed` application. For redundancy, you can run multiple instances of the location service
on different hosts. In that case, separate the values by a comma. For example:

    test-akka-service-app 10 -DinterfaceName=enp0s31f6 -DclusterSeeds='192.168.178.66:3552,192.168.178.23:3552'

Scala Version
-------------

* TestAkkaServiceApp - Starts one or more akka services in order to test the location service.
  If a command line arg is given, it should be the number of services to start (default: 1).
  Each service will have a number appended to its name.
  You should start the TestServiceClient with the same number, so that it
  will try to find all the services.
  The client and service applications can be run on the same or different hosts.
  
  Example command line: This starts and registers tem actors:
 `test-akka-service-app 10 -DinterfaceName=enp0s31f6 -DclusterSeeds='192.168.178.66:3552'`

* TestServiceClientApp - A location service test client application that attempts to resolve one or more sets of
  akka services. If a command line arg is given, it should be the number of services to resolve (default: 1).
  
  Example: This looks up the 10 actors with the location service and sends each one a message:
   `test-service-client-app 10 -DinterfaceName=enp0s31f6 -DclusterSeeds='192.168.178.66:3552'`

Java Version
------------

* JTestAkkaService - Java version of TestAkkaServiceApp above:

  Example: `j-test-akka-service 10 -DinterfaceName=enp0s31f6 -DclusterSeeds='192.168.178.66:3552'`

* JestServiceClient - Java version of TestServiceClientApp above:

  Example: `j-test-service-client 10 -DinterfaceName=enp0s31f6 -DclusterSeeds='192.168.178.66:3552'`

One the 3 applications are running, you should see log messages indicating that the connections were located.
If you kill the TestAkkaService application, the TestServiceClient should receive a LocationRemoved message.
