# csw-prod-qa
Contains additional tests and example applications for the csw-prod software

* [Location Service Tests](locationTests)
* [Config Service Tests](configTests)

To build, run sbt stage. 

Note that the tests and applications here require that the csw location service cluster and config service are
running and that the required environment variables are set. For example:

* Start the location service:

```bash
export interfaceName=enp0s31f6
export clusterSeeds=192.168.178.66:7777
csw-cluster-seed --clusterPort 7777
```
or 

```csh
setenv interfaceName enp0s31f6
setenv clusterSeeds 192.168.178.66:7777
csw-cluster-seed --clusterPort 7777
```

* and start the config service:

```bash
cd ../csw-prod/csw-config-server/target/universal/stage/bin/
csw-config-server --initRepo
```
