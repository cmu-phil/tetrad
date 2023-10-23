Our Tetrad Application is a graphical user interface tool that divides the analysis of causal problems into modular pieces that can be connected to reflect how causal problems should ideally be analyzed. This is helpful as an educational tool or for data analysis for those who prefer a visual interface. 

This requires a Java JDK. See [Setting up Java for Tetrad](https://github.com/cmu-phil/tetrad/wiki/Setting-up-Java-for-Tetrad). 

Currently, the Tetrad Application is a downloadable Java jar that can be launched on your specific platform in the usual ways for jars. First, determine which version of Java you are using by typing in a terminal window: 

java -version

All jar files, checksums, documentation, and so on are downloadable from Maven Central. The current version has been compiled alternatively under JDK 1.8 and JDK 17. If you're using JDK 1.8 (and are unable to install a more recent JDK), please download Java using this link:

https://s01.oss.sonatype.org/content/repositories/releases/io/github/cmu-phil/tetrad-gui/7.5.0-jdk1.8/tetrad-gui-7.5.0-jdk1.8-launch.jar

If your version of the Java JDK is version 9 or higher, please download the Java launch jar using this link, compiled under JDK 17:

https://s01.oss.sonatype.org/content/repositories/releases/io/github/cmu-phil/tetrad-gui/7.5.0/tetrad-gui-7.5.0-launch.jar

You may be able to launch this jar by double-clicking the jar file name. However, on a Mac, this presents some security challenges. On all platforms, the jar may be launched at the command line (with a specification of the amount of RAM you will allow it to use) using this command: 

java -Xmx[g]G -jar *-launch.jar

Here, [g] is the maximum number of Gigabytes you wish to allocate to the process. 

We plan to publish the Tetrad application soon as an application that will not require a separate download of the Java JDK, so stay tuned! 

See our Documentation for more details about the Tetrad application.
