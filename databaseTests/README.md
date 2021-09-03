Database Service Tests
=======================

This project contains some code using the database service.

The code depends on a running postgres database, which is started by csw-services by default.
Make sure to set the necessary environment variables for the user name and password.
For example:

    setenv DB_READ_USERNAME myName

    setenv DB_READ_PASSWORD myPassword

You can use a command like this to set the password the first time. 
(Note: Temporarily replace `password` with `trust` in csw/csw-services/src/main/resources/database/pg_hba.conf and run `sbt stage`):

```
> > psql postgres -h myHost -p 5432
psql (12.4 (Ubuntu 12.4-0ubuntu0.20.04.1))

Type "help" for help.

postgres=# ALTER USER myName WITH PASSWORD 'myPassword';
```
