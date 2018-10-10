# csw-qa
Contains additional tests and example applications for the csw software

* [Location Service Tests](locationTests)
* [Config Service Tests](configTests)
* [Framework Tests](frameworkTests)

To build, run sbt stage. 

Note that the tests and applications here require that the csw location service cluster and config service are
running, `csw/target/universal/stage/bin` is in your shell path,
and the required environment variables are set. For example:

* Set the `interfaceName` environment variable to your host's network interface, as listed by `ifconfig -a`:

```bash
export interfaceName=enp0s31f6
```

This ensures that the location service uses the correct network interface (For example, ethernet instead of wireless).

* Start the csw services (location, alarm, event, config services): 

```bash
csw-services.sh start
```

* Set the `clusterSeeds` environment variable (The correct value is printed in the output of the above command):

```bash
export clusterSeeds=192.168.178.77:5552
```

* Start the location service

```bash
csw-location-server
```
