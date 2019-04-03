Framework Tests
===============

Contains some simple example aps for testing the CSW command service framework.

To run the Scala HCD and assembly, start the following commands in different terminals (See [../README.md](../README.md) for how to set up the correct environment):

Note that the assembly accesses the postgres database, which is started by csw-services.sh by default.
Make sure to set the necessary environment variables for the user name and password.
For example:

    setenv DB_READ_USERNAME myName

    setenv DB_READ_PASSWORD myPassword

You can use a command like this to set the password the first time. 
(Note: Temporarily replace `password` with `trust` in the installed version of `pg_hba.conf` under csw/target/universal/stage/conf):

```
> psql postgres -h localhost
psql (10.6)
Type "help" for help.

postgres=# ALTER USER myName WITH PASSWORD 'myPassword';
```

Then, to run the applications:

    test-hcd-app --local frameworkTests/src/main/resources/TestHcd.conf

    test-assembly-app --local frameworkTests/src/main/resources/TestAssembly.conf

    test-assembly-client

For the Java version:

    j-test-hcd --local frameworkTests/src/main/resources/JTestHcd.conf
    
    j-test-assembly --local frameworkTests/src/main/resources/JTestAssembly.conf

    test-assembly-client


