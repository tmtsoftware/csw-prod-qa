# csw-prod-qa
Contains additional tests and example applications for the csw software

* [Location Service Tests](locationTests)
* [Config Service Tests](configTests)
* [Framework Tests](frameworkTests)

To build, run 

    sbt stage 

## Test Environment

The tests and applications here require that the csw location and config services are
running. The csw-services.sh script does this. 

* Set the `INTERFACE_NAME` environment variable to your host's network interface, as listed by `ifconfig -a`. For example:

```bash
export INTERFACE_NAME=enp0s31f6
```

This ensures that the location service uses the correct network interface (For example, ethernet instead of wireless).

* Start the csw services (location, alarm, event, config services): 

```bash
csw-services.sh start
```

See the READMEs in the subprojects for details on running the test applications.
