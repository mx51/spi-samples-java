# Welcome to TablePos

TablePos is a command-line POS.

Its purpose is to demonstrate to a POS software developer how they might integrate with a PIN pad (card payment terminal) using Assembly Payment's SPI (Simple Payments Integration) as documented at [harness.simplepaymentapi.com](harness.simplepaymentapi.com).

This project is sample code that implements the SPI protocol. It is not complete and is up to you to enhance and change to make it work on your target platform.

# Setup

Basic steps that you need to perform to run this application on your machine.

## Importing and running

The easiest way to run the project is by importing it into [IntelliJ IDEA](https://www.jetbrains.com/idea/). Follow the import wizard, choose "Import project from external model" and select "Gradle". When asked about the Gradle distribution, you are recommended to pick the default wrapper, however any recent version of Gradle should be sufficient.

Once imported, you can run the project by executing the `Pos` class.

Alternatively, you can run it from the command line using the Gradle wrapper as follows:

```bash
./gradlew --console plain tablepos:run -q -PrunArgs='JAVABAR'
```

The above command will compile the application and run it in plain console mode to make typing input cleaner. 

## Logging

Note that only the information intended for the user interface will be written to the command line. Everything else will be logged using Log4j, so to subscribe to that stream, your project needs to include a `log4j2.xml` configuration file in your resources.

The configuration used in this sample project (see `src/main/resources/log4j2.xml`) outputs the logs to a file called `output.log` in the root of the repository. 
