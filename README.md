# Tetrad

Please visit our [Tetrad web page](https://www.cmu.edu/dietrich/philosophy/tetrad/) for current links, a list of
contributors, some history, documentation, descriptions, links for our various projects, Javadocs, and more.

## Tetrad Application

See our instructions
for [Installing the Tetrad Application](https://github.com/cmu-phil/tetrad/blob/development/INSTALL_APPLICATION.md).

## Tetrad in Python

We have a project, [py-tetrad](https://github.com/cmu-phil/py-tetrad), that allows you to incorporate arbitrary Tetrad
code into a Python workflow. It's new, and the installation is still nonstandard, but it had a good response. This
requires Python 3.5+. and Java JDK 21+.

## Tetrad in R

We also have a project, [rpy-tetrad](https://github.com/cmu-phil/py-tetrad/tree/main/pytetrad/R), that allows you to
incorporate _some_ Tetrad functionality in R. It's also new, and the installation for it is also still nonstandard, but
has gotten good feedback. This requires Python 3.5+ and Java JDK 21+.

Please see our [description](https://sites.google.com/view/tetradcausal/tetrad-in-r?authuser=0).

## Tetrad at the Command Line

In addition, we have a fully developed tool, [Causal Command](https://github.com/bd2kccd/causal-cmd), that lets you run
arbitrary Tetrad searches at the command line.

## Installallation for Programmers

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

The (launch) jar for the Tetrad Application will appear in the tetrad-gui/target directory. For links to our Python and
R projects or our command line tool, please see our [Tetrad web page](https://sites.google.com/view/tetradcausal).

Here are
some [instructions on how to set this project up in IntelliJ IDEA](https://github.com/cmu-phil/tetrad/wiki/Setting-up-Tetrad-in-IntelliJ-IDEA).
You can run the Tetrad lifecycle package target and launch the "-launch" jar built in the target directory.

The project contains well-developed code in these packages:

* tetrad
* pitt
* tetradapp

The tetrad-lib package contains the model code; the tetrad-gui package contains the view (GUI) code.

A similar method can be followed for installing in some other IDE.

## Problems? Comments?

Please submit an issue in our [Issue Tracker](https://github.com/cmu-phil/tetrad/issues), which we assiduously read.
