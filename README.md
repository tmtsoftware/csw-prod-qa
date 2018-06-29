# csw-prod-qa
Contains additional tests and example applications for the csw-prod software

* [Location Service Tests](locationTests)
* [Config Service Tests](configTests)
* [Framework Tests](frameworkTests)

To build, run sbt stage. 

Note that the tests and applications here require that the csw location service cluster and config service are
running, `csw-prod/target/universal/stage/bin` is in your shell path,
and the required environment variables are set. For example:

* Set the environment variables (Replace interface name, IP address and port with your own values):

```bash
export interfaceName=enp0s31f6
export clusterSeeds=192.168.178.77:7777
```
or 

```csh
setenv interfaceName enp0s31f6
setenv clusterSeeds 192.168.178.77:7777
```

* Start the location service: 

```bash
csw-cluster-seed --clusterPort 7777
```

* Start the config service:

```bash
csw-config-server --initRepo
```
