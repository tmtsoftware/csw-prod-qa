Framework Tests
===============

Contains some simple example aps for testing the CSW command service framework.

To run the Scala HCD and assembly, start the following commands in different terminals (See [../README.md](../README.md) for how to set up the correct environment):

    test-hcd-app --local frameworkTests/src/main/resources/TestHcd.conf

    test-assembly-app --local frameworkTests/src/main/resources/TestAssembly.conf

    test-assembly-client

For the Java version:

    j-test-hcd --local frameworkTests/src/main/resources/JTestHcd.conf
    
    j-test-assembly --local frameworkTests/src/main/resources/JTestAssembly.conf

    test-assembly-client


