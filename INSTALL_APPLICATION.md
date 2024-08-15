# Tetrad Application

This is a user interface tool that divides the analysis of causal problems into modular pieces which can be connected
together to reflect how causal problems should ideally be analyzed. This can helpful as an educational tool or for data
analysis for those who prefer a point and click interface.

Please use a recent Java JDK.
See [Setting up Java for Tetrad](https://github.com/cmu-phil/tetrad/wiki/Setting-up-Java-for-Tetrad).

To download the Tetrad jar, please click the following link (which will always be updated to the latest version):

https://s01.oss.sonatype.org/content/repositories/releases/io/github/cmu-phil/tetrad-gui/7.6.4/tetrad-gui-7.6.4-launch.jar

You may be able to launch this jar by double-clicking the jar file name. However, on a Mac, this presents some security
challenges. On all platforms, the jar may be launched at the command line (with a specification of the amount of RAM you
will allow it to use) using this command:

java -Xmx[g]G -jar *-launch.jar

Here, [g] is the maximum number of Gigabytes you wish to allocate to the process.

See our Documentation for more details about the Tetrad application.
