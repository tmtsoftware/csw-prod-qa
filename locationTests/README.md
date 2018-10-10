Location Service Tests
======================

This project contains some simple applications using the location service.

Before running the applications here, the location service needs to be started. For example:

    # Assuming csw/target/universal/stage/bin is in your shell path:
    csw-cluster-seed --clusterPort=7777 -DinterfaceName=enp0s31f6 -DclusterSeeds=192.168.178.77:7777

Replace the IP address and port with the IP address and port where the location service cluster is started.
Replace the value for `interfaceName` with the name of the network interface you want the location service to use.
(Use `ifconfig -a` to list the network interfaces.)

Note: The system properties specified with -D can instead be set as environment variables. For example:

```bash
export interfaceName=enp0s31f6
export clusterSeeds=192.168.178.77:7777
csw-cluster-seed --clusterPort 7777
```
or 

```csh
setenv interfaceName enp0s31f6
setenv clusterSeeds 192.168.178.77:7777
csw-cluster-seed --clusterPort 7777
```

Alternatively, start all of the csw services with:
```
csw-services.sh start
```

The commands described below can be found under `csw/target/universal/stage/bin`.
After setting the above environment variables, you can run them in different terminals, on the same or different hosts.

The `clusterSeeds` property must indicate the IP address and port number of the location 
service `csw-cluster-seed` application. For redundancy, you can run multiple instances of the location service
on different hosts. In that case, separate the `clusterSeeds` values by a comma. For example:

    export clusterSeeds='192.168.178.77:7777,192.168.178.68:7777'

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
