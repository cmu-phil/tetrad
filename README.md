# Tetrad

The purpose of Tetrad is to make algorithms available to do causal inference. Here is the [Dietrich College Carnegie Mellon University web page for Tetrad](https://www.cmu.edu/dietrich/news/news-stories/2020/august/tetrad-sail.html). Here is the [Simon Initiative page for Tetrad](https://www.cmu.edu/simon/open-simon/toolkit/tools/learning-tools/tetrad.html).

Here is our [project web page](https://sites.google.com/view/tetradcausal) here with current links for artifacts, a list of contributors, and a bit of history.

All artifacts are published in the [Maven Central Repository](https://s01.oss.sonatype.org/content/repositories/releases/io/github/cmu-phil/).

To download the current jar you can use to launch the Tetrad GUI, click [here](https://s01.oss.sonatype.org/content/repositories/releases/io/github/cmu-phil/tetrad-gui/7.2.2/tetrad-gui-7.2.2-launch.jar). Please delete any old ones you're not using.

The application will work on all major platforms once a recent version of the Java JRE/JCK is installed, certainly _greater than version 1.8 (version 8)_. We find that the most recent [Corretto JRE/JDK](https://aws.amazon.com/corretto/?filtered-posts.sort-by=item.additionalFields.createdDate&filtered-posts.sort-order=desc) with long term support (LTS) works well cross-platform. 

You may be able to launch this jar by double clicking the jar file name, though on a Mac, this presents some [security challenges](https://github.com/cmu-phil/tetrad/wiki/Dealing-with-Tetrad-on-a-Mac:--Security-Issues). In any case, on all platforms, the jar may be launched at the command line (with a specification of the amount of RAM you will allow it to use) using this command:

```
java -Xmx[g]G -jar *-launch.jar
```

where g is the maximum number of Gigabytes you wish to allocate to the process.

We have a tool, [Causal Command](https://github.com/bd2kccd/causal-cmd), that lets you run Tetrad algorithms at the command line.

For Python integration, please see our (still new) [py-tetrad project](https://github.com/cmu-phil/py-tetrad), which shows how to integrate arbitrary Java code in this project into a Python workflow using the [JPype Python project](https://jpype.readthedocs.io/en/latest/). The JPype project is already quite awesome; it is not necessary to use it in the way suggested in py-tetrad, though perhaps py-tetrad can provide a way to get started with it. Have fun exploring!

Also, please see the [causal-learn Python package](https://causal-learn.readthedocs.io/en/latest/), translating some Tetrad algorithms into Python and adding some algorithms not in Tetrad, now part of the awesome [py-why space](https://github.com/py-why).

All artifacts for Tetrad and causal-cmd are published in [Maven Central](https://s01.oss.sonatype.org/content/repositories/releases/io/github/cmu-phil/). This includes all downloadable jars and Javadocs.

Please submit issue requests for Tetrad using our [Issue Tracker](https://github.com/cmu-phil/tetrad/issues).

Here is our [GitHub URL](https://github.com/cmu-phil/tetrad). Actually you're already here. All of our code is public and we welcome suggestions.

Here is the web page for the [Center for Causal Discovery](https://www.ccd.pitt.edu/), which also supports the latest version of Tetrad and Causal Command.

If you're new to Tetrad, here is a [Tutorial](https://rawgit.com/cmu-phil/tetrad/development/tetrad-gui/src/main/resources/resources/javahelp/manual/tetrad_tutorial.html). Also, here is our [Manual](https://htmlpreview.github.io/?https:///github.com/cmu-phil/tetrad/blob/development/docs/manual/index.html).

Here are some [instructions on how to set this project up in IntelliJ IDEA](https://github.com/cmu-phil/tetrad/wiki/Setting-up-Tetrad-in-IntelliJ-IDEA). You can run the Tetrad lifecycle package target and launch the "-launch" jar that is  built in the target directory.

The project contains fairly well-developed code in these packages:
* tetrad
* pitt
* tetradapp

The  tetrad-lib package contains the model code; the tetrad-gui package contains the view (GUI) code.
