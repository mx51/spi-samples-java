# SPI Samples Java

This is a set of SPI client samples for Java.

# Setup

Basic steps that you need to perform to run these applications on your machine.

## Unlimited strength cryptography

Java virtual machine on any machine used to run these applications must allow unrestricted cryptographic key sizes.

When running Java 8 Update 161 or higher, you do not need to do anything -- the restriction should already be removed.

For earlier versions, follow these steps:

1. Download 'Java Cryptography Extension (JCE) Unlimited Strength Jurisdiction Policy Files' (available from [downloads](http://www.oracle.com/technetwork/java/javase/downloads/index.html)).
2. Uncompress and extract the downloaded file.
3. Place the uncompressed files into the `<JAVA_HOME>/lib/security` directory for your JDK installation.

For more information and issues with specific versions, follow [this explanation](https://stackoverflow.com/a/3864276).

# Disclaimer

This source code is provided “as is“ or “as available“ and Assembly makes no representations or warranties, express or implied, regarding the source code, that the source code will meet your requirements, or that this source code will be error-free. Assembly expressly disclaims any and all express and implied warranties, including, but not limited to, the implied warranties of merchantability, fitness for a particular purpose, and non-infringement. Without limiting the generality of the foregoing, Assembly does not warrant, endorse, guarantee, or assume responsibility for this source code.   

In no event shall Assembly be liable for any direct, indirect, incidental, special, exemplary, or consequential damages (including, but not limited to, procurement of substitute goods or services; loss of use, data, or profits; or business interruption) however caused and on any theory of liability, whether in contract, strict liability, or tort (including negligence or otherwise) arising in any way out of the use of this source code, even if advised of the possibility of such damage.
