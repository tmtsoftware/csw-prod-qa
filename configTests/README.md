Testing the Config Service
==========================

Manually testing the command line app
-------------------------------------

Assuming your host IP address is 192.168.178.66 and you want to start the location service cluster on port 7777:
(Replace IP address, port and interface name as needed):

* Start the location service cluster:

```bash
cd csw-cluster-seed/target/universal/stage/bin/
csw-cluster-seed -DclusterSeeds=192.168.178.66:7777 --clusterPort 7777 -DinterfaceName=enp0s31f6
```

Note: The system properties specified with -D can instead be set as environment variables:

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


The following examples assume the two environment variables above have been set.

* Start the config service server (specify --initRepo only the first time):

```bash
cd csw-config-server/target/universal/stage/bin/
csw-config-server --initRepo
```
 
* Create, get, update a file:

```bash
csw-config-client-cli create x/y/z.txt -c "test this xxx" -i ~/.tcshrc
csw-config-client-cli get x/y/z.txt --out xxx
csw-config-client-cli update x/y/z.txt -c "test this xxx" -i ~/.emacs
csw-config-client-cli list
csw-config-client-cli history x/y/z.txt

# These are likely to change...
csw-config-client-cli setDefault x/y/z.txt --id 1
# csw-config-client-cli get x/y/z.txt --out xxx
csw-config-client-cli getDefault x/y/z.txt --out xxx
```