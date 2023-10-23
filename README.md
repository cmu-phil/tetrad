# Tetrad

Please visit our [Tetrad web page](https://sites.google.com/view/tetradcausal) for current links, a list of contributors, some history, documentation, descriptions, links for our various projects, Javadocs, and more.

## Install

Here's the git command to clone our project:

```
git clone https://github.com/cmu-phil/tetrad
```

Or, you can use GitHub's Code button.

If you have Maven installed, you can type the following to compile:

```
mvn clean compile
```

To run the unit tests:

```
mvn clean test
```

To generate an executable jar:

```
mvn clean package
```

The (launch) jar for the Tetrad Application will appear in the tetrad-gui/target directory. For links to our Python and R projects or our command line tool, please see our [Tetrad web page](https://sites.google.com/view/tetradcausal).

Here are some [instructions on how to set this project up in IntelliJ IDEA](https://github.com/cmu-phil/tetrad/wiki/Setting-up-Tetrad-in-IntelliJ-IDEA). You can run the Tetrad lifecycle package target and launch the "-launch" jar built in the target directory.

The project contains well-developed code in these packages:

* tetrad
* pitt
* tetradapp

The tetrad-lib package contains the model code; the tetrad-gui package contains the view (GUI) code.

A similar method can be followed for installing in some other IDE.

## Tetrad Application

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

## Tetrad in Python

We have a project, [py-tetrad](https://github.com/cmu-phil/py-tetrad), that allows you to incorporate arbitrary Tetrad code into a Python workflow. It's new, and the installation is still nonstandard, but it had a good response. This requires Python 3.5+. and Java JDK 9+.

Please see our [description](https://sites.google.com/view/tetradcausal/tetrad-in-python

## Tetrad in R

We also have a project, [rpy-tetrad](https://github.com/cmu-phil/py-tetrad/tree/main/pytetrad/R), that allows you to incorporate _some_ Tetrad functionality in R. It's also new, and the isntallation for it is also still nonstandard, but has gotten good feedgack so. This requires Python 3.5+ and Java JDK 9+.

Please see our [description](https://sites.google.com/view/tetradcausal/tetrad-in-r?authuser=0).

## Tetrad at the Command Line

In addition, we have a fully-developed tool, [Causal Coimmand](https://github.com/bd2kccd/causal-cmd), that lets you run arbitrary Tetrad searches at the command likne.

## Problems? Comments?

Please submit an issue in our [Issue Tracker](https://github.com/cmu-phil/tetrad/issues), which we assiduously read.
