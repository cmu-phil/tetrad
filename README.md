# Tetrad

The purpose of Tetrad is to make algorithms available for causal inference. Here is the [Carnegie Mellon University, Dietrich College, web page for Tetrad](https://www.cmu.edu/dietrich/news/news-stories/2020/august/tetrad-sail.html). Here is the [Simon Initiative page for Tetrad](https://www.cmu.edu/simon/open-simon/toolkit/tools/learning-tools/tetrad.html).

Here is our [project web page](https://sites.google.com/view/tetradcausal) here with current links for artifacts, a list of contributors, and a bit of history.

Here is the web page for the [Center for Causal Discovery](https://www.ccd.pitt.edu/), which also supports the latest version of Tetrad and Causal Command.

Our tools are freeware, and all of our code is public. We welcome suggestions, expecially if you have awesome algorithms that outperform ours or know how to improve performance of our algorithms.

## Install

This software will work on all major platforms once a recent version of the Java JRE/JCK is installed, certainly **_greater than version 1.8 (version 8)_**. We find that the most recent [Corretto JRE/JDK](https://aws.amazon.com/corretto/?filtered-posts.sort-by=item.additionalFields.createdDate&filtered-posts.sort-order=desc) with long term support (LTS) works well and consistently on all platforms. 

All artifacts are published in the [Maven Central Repository](https://s01.oss.sonatype.org/content/repositories/releases/io/github/cmu-phil/). This includes all downloadable jars and Javadocs.

Here is our [GitHub URL](https://github.com/cmu-phil/tetrad). Also, here are some [instructions on how to set this project up in IntelliJ IDEA](https://github.com/cmu-phil/tetrad/wiki/Setting-up-Tetrad-in-IntelliJ-IDEA), if you are interested in Java coding.

## Documentation

If you're new to Tetrad, here is a [Tutorial](https://rawgit.com/cmu-phil/tetrad/development/tetrad-gui/src/main/resources/resources/javahelp/manual/tetrad_tutorial.html). Also, here is our [Manual](https://htmlpreview.github.io/?https:///github.com/cmu-phil/tetrad/blob/development/docs/manual/index.html). If you like to watch thought-provoking lectures, here are some [lectures on the Center for Causal Discovery site](https://www.ccd.pitt.edu/video-tutorials/).

## Tetrad GUI Application

To download the current jar for launching the Tetrad GUI, click [here](https://s01.oss.sonatype.org/content/repositories/releases/io/github/cmu-phil/tetrad-gui/7.2.2/tetrad-gui-7.2.2-launch.jar). Please delete any old ones you're not using.

You may be able to launch this jar by double clicking the jar file name, though on a Mac, this presents some [security challenges](https://github.com/cmu-phil/tetrad/wiki/Dealing-with-Tetrad-on-a-Mac:--Security-Issues). In any case, on all platforms, the jar may be launched at the command line (with a specification of the amount of RAM you will allow it to use) using this command:

```
java -Xmx[g]G -jar *-launch.jar
```

where [g] is the maximum number of Gigabytes you wish to allocate to the process.

## Command Line

We have a tool, [Causal Command](https://github.com/bd2kccd/causal-cmd), that lets you run Tetrad algorithms at the command line.

## Python Integration

For Python integration, please see our [py-tetrad project](https://github.com/cmu-phil/py-tetrad), which shows how to integrate arbitrary Tetrad code into a Python workflow using [JPype](https://jpype.readthedocs.io/en/latest/). This allows for integration with [causal-learn](https://github.com/py-why/causal-learn) in the [py-why space](https://github.com/py-why).

You can also integrate Tetrad code into Python by making os.system(..) calls to [Causal Command](https://github.com/bd2kccd/causal-cmd) and parsing the results using tools in [causal-learn](https://github.com/py-why/causal-learn). Here are some [now-outdated examples](https://github.com/cmu-phil/algocompy/blob/main/causalcmd/tetrad_cmd_algs.py) of how to do this, which we will soon update.

Also, please see the [causal-learn package](https://causal-learn.readthedocs.io/en/latest/), which translates some Tetrad algorithms into Python and adds several algorithms not in Tetrad, now part of the [py-why space](https://github.com/py-why).

## R Integration

We have an old project, [r-causal](https://github.com/bd2kccd/r-causal), which integrates R with an old version of Tetrad. We will make an effort to provide updated advice for doing this with a more recent version of Tetrad, as we've done with Python.

## Example Data

Tetrad has robust facilities for simulating data, but if you'd like to try your hand at real data, or realistically simulated data, we maintain a [repository of example datasets](https://github.com/cmu-phil/example-causal-datasets), both real and simulated, with ground truth where known or strongly suggested. This has been formatted in a uniform way to help avoid problems with preprocessing.

## Bug Reports/Issue Requests

Please submit issue requests for Tetrad using our [Issue Tracker](https://github.com/cmu-phil/tetrad/issues). We will try to the extent possible to resolve all reported issues before [releasing new versions of Tetrad](https://github.com/cmu-phil/tetrad/releases). This may involve moving items to our [Wish List
](https://github.com/cmu-phil/tetrad/wiki/Current-Wish-List), which we revisit periodically.
