Maven Ant Tasks
$Id$

Building the Ant Tasks
----------------------
The Maven Ant Tasks can be build with Maven 2.x (2.2.1 is recommended).  From the root directory run
"mvn install"

The resulting output will be generated to the "target" directory.

Running the tests
-----------------
The test suite is contained in the file "build-tests.xml".  This can be run using Ant 1.6.x or 1.7.x.
"ant -f build-tests.xml"

Note: some of the tests require a running ssh server.  How you start sshd will depend on your system.  
      For example on Fedora Linux, you can start the ssh deamon using "service sshd start"