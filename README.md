# Tetrad

Please visit our [Tetrad web page](https://sites.google.com/view/tetradcausal) for current links for downloadables, a list of contributors, some history, documentation, descriptions, links for our various projects, Javadocs, and more!

## Cloning This Project

So glad you want to clone our project! Here's the command:

```
git clone https://github.com/cmu-phil/tetrad
```

Or, you can use GitHub's Code button.

## Install

Here are some [instructions on how to set this project up in IntelliJ IDEA](https://github.com/cmu-phil/tetrad/wiki/Setting-up-Tetrad-in-IntelliJ-IDEA). You can run the Tetrad lifecycle package target and launch the "-launch" jar built in the target directory.

The project contains well-developed code in these packages:

* tetrad
* pitt
* tetradapp

The tetrad-lib package contains the model code; the tetrad-gui package contains the view (GUI) code.

A similar method can be followed for installing in some other IDE.

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

## Problems? Comments?

Please submit an issue in our [Issue Tracker](https://github.com/cmu-phil/tetrad/issues), which we assiduously read.
