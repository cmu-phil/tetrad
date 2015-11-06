$Revision: 6858 $ $Date: 2008-06-10 15:12:42 -0400 (Tue, 10 Jun 2008) $

To build the Tetrad 4.0 application, assuming you've gotten it out of
CVS correctly, do the following.

1. Install Java SDK 1.3.1 on your machine. Add the /bin directory of
Java SDK 1.3.1 to you path. (Make sure that when you type "java
-version" it responds with the correct version. Java SDK 1.3.1 may be
obtained online at http://java.sun.com/j2se/.)

2. Install Ant 1.4.1 on your machine. Add the /bin directory of Ant
1.4.1 to your path. (Make sure that when you type "ant -version" it
responds with the correct version. You may need to make the "ant"
script executable--chmod +x ant--in unix/linux. Ant 1.4.1 may be
obtained online at http://jakarta.apache.org/ant/index.html.)

3. Set environment variables as follows:

   JAVA_HOME = path to your Java SDK 1.3.1 installation.
   ANT_HOME = path to your Ant 1.4.1 installation.

4. Change directory to where the compile.xml script is for this project;
in this directory, type "ant".

5. For information on available targets in the compile.xml script, type
"ant -projecthelp" or just read the compile.xml file. (Typing "ant"
without any target specified is the same as typing "ant ejar" for this
project.)

6. We have recently begun to incorporate JUnit testing into the Tetrad
project. To run the JUnit tests, you will need to add two jars to the
/lib directory of the Ant 1.4.1 installation:

     junit-3.7.jar
     jakarta-ant-1.4-optional.jar

The first jar can be downloaded from http://junit.org; the second jar
can be downloaded from http://jakarta.apache.org/ant/index.html. Once
these jars have been added, JUnit tests can be run through Ant by
typing:
     
     ant test
