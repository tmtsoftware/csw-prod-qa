Testing the Config Service
==========================

Before running `sbt test` the location and config services should be running (Run `csw-services.sh start`).
See [../README.md](../README.md) for how to set up the correct environment.

Manually testing the command line app
-------------------------------------

* Create, get, update a file:

```bash
csw-config-cli create x/y/z.txt -c "test 1 comment" -i ~/.tcshrc
csw-config-cli get x/y/z.txt --out xxx
csw-config-cli update x/y/z.txt -c "test 2 comment" -i ~/.emacs
csw-config-cli list
csw-config-cli history x/y/z.txt

csw-config-cli setActive x/y/z.txt --id 3
# csw-config-cli get x/y/z.txt --out xxx
csw-config-cli getActive x/y/z.txt --out xxx
```
