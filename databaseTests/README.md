Database Service Tests
=======================

This project contains some code using the database service.

The code depends on a running postgres database, which is started by csw-services.sh by default.
Make sure to set the necessary environment variables for the user name and password.
For example:

    setenv dbReadUsername myName

    setenv dbReadPassword myPassword

You can use a command like this to set the password the first time. 
(Note: Temporarily replace `password` with `trust` in the installed version of `pg_hba.conf` under csw/target/universal/stage/conf):

```
> psql postgres -h localhost
psql (10.6)
Type "help" for help.

postgres=# ALTER USER myName WITH PASSWORD 'myPassword';
```
