# Welcome to AcmePos

AcmePos is a command-line POS.

Its purpose is to demonstrate to a POS software developer how they might integrate with a PIN pad (card payment terminal) using Assembly Payment's SPI (Simple Payments Integration) as documented at [harness.simplepaymentapi.com](harness.simplepaymentapi.com).

This project is sample code that implements the SPI protocol. It is not complete and is up to you to enhance and change to make it work on your target platform.

# Setup

Basic steps that you need to perform to run this application on your machine.

## Unlimited strength cryptography

Java virtual machine on any machine used to run this application must allow unrestricted cryptographic key sizes.

When running Java 8 Update 161 or higher, you do not need to do anything -- the restriction should already be removed.

For earlier versions, follow these steps:

1. Download 'Java Cryptography Extension (JCE) Unlimited Strength Jurisdiction Policy Files' (available from [downloads](http://www.oracle.com/technetwork/java/javase/downloads/index.html)).
2. Uncompress and extract the downloaded file.
3. Place the uncompressed files into the `<JAVA_HOME>/lib/security` directory for your JDK installation.

For more information and issues with specific versions, follow [this explanation](https://stackoverflow.com/a/3864276).

## Importing and running

The easiest way to run the project is by importing it into [IntelliJ IDEA](https://www.jetbrains.com/idea/). Follow the import wizard, choose "Import project from external model" and select "Gradle". When asked about the Gradle distribution, you are recommended to pick the default wrapper, however any recent version of Gradle should be sufficient.

Once imported, you can run the project by executing the `Pos` class.

Alternatively, you can run it from the command line using the Gradle wrapper as follows:

```bash
./gradlew --console plain run -q -PrunArgs='JAVABAR'
```

The above command will compile the application and run it in plain console mode to make typing input cleaner. 

## Logging

Note that only the information intended for the user interface will be written to the command line. Everything else will be logged using Log4j, so to subscribe to that stream, your project needs to include a `log4j2.xml` configuration file in your resources.

The configuration used in this sample project (see `src/main/resources/log4j2.xml`) outputs the logs to a file called `output.log` in the root of the repository. 

# Disclaimer

This source code is provided “as is“ or “as available“ and Assembly makes no representations or warranties, express or implied, regarding the source code, that the source code will meet your requirements, or that this source code will be error-free. Assembly expressly disclaims any and all express and implied warranties, including, but not limited to, the implied warranties of merchantability, fitness for a particular purpose, and non-infringement. Without limiting the generality of the foregoing, Assembly does not warrant, endorse, guarantee, or assume responsibility for this source code.   

In no event shall Assembly be liable for any direct, indirect, incidental, special, exemplary, or consequential damages (including, but not limited to, procurement of substitute goods or services; loss of use, data, or profits; or business interruption) however caused and on any theory of liability, whether in contract, strict liability, or tort (including negligence or otherwise) arising in any way out of the use of this source code, even if advised of the possibility of such damage.
