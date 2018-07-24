** These are instructions for running performance simulations using ComparisonScript
** author: dmalinsky 05.20.2016
** contact: malinsky@cmu.edu

Required files:

1) ComparisonScript.java
2) Comparison2.java
3) ComparisonParameters.java (not the same as the one currently publicly available)

Optional: directory of input data files and graph files if you want to run from file
(must be in the right format, described below)

These files are in the branch “dan” on github. An example data directory is called “danexamples”.

*******

The point is to compare the performance of different algorithms in terms of various statistics like adjacency precision/recall, arrowhead precision/recall, structural distance from the true graph, etc.
 
There are currently 3 “modes” you can try, with different settings required.

1) Simulate random graphs and generate data from them at varying sample sizes.
2) Use graphs and data from an input file directory.
3) Simulate random graphs but no data; just run the algorithms directly on the graphs. 

For each of these you have to change settings in ComparisonScript.java

For mode 1):

You have to specify the type of data (Continuous or Discrete), number of variables, number of edges, sample sizes to try, and number of data sets to run at each sample size (trials). At each sample size the results over the trials will be averaged together to produce the final table. By default, you will generate DAGs with 20 variables and 40 edges, at sample sizes starting from n=100 up to n=2000 by increments of 100. At each sample size you will generate 100 different graphs and (continuous) data sets, search with your specified algorithms, and average the results over those 100 trials.

All these settings are just the first 8 lines of the code in ComparisonScript.java.

For mode 2):

In ComparisonScript.java set parameters.setDataFromFile(true) (it is false by default). maxGraphs is the number of graphs in your data directory. dataSetsPerGraph is the number of data sets you have per graph in your directory. This might be equal to one, or you might have multiple data sets for the same graph which you want to average.

You must set the path for your data directory in Comparison2.java, near the top where it says “String path = <some directory>”.

Your directory should have two kinds of files with the following naming scheme:

Graphs:
graph1.g.txt
graph2.g.txt
graph3.g.txt

Data:
graph1-1.dat.txt
graph1-2.dat.txt
graph2-1.dat.txt
graph2-2.dat.txt
graph3-1.dat.txt

The default delimiter for the data is tab (“\t”). You can change this in Comparison2.java. Also note that Tetrad might save a data file with a column of 1’s in the first column, under the heading “MULT”. You have to remove this (or don’t save it that way) because the data reader will think this is a variable.

For mode 3):

In ComparisonScript.java set parameters.isNoData(true) (it is false by default). You can change numTrials to change the number of random graphs to try, the default is 100.

In this mode you want to see perfect performance everywhere: perfect precision and recall, etc. If you see any deviation from this, there must be a mistake somewhere. Note that this procedure compares, for example, the output of PC or FGES with a true graph specified by DagToPattern. FCI or GFCI would be compared with the output of DagToPag. If you want to examine an algorithm which searches for something other than a standard Pattern or PAG, you have to add the appropriate method in Comparison2.java. Just search for “DagToPag” and you’ll see where it goes.

Note also that some algorithms are quite slow running directly on graphs. Start with small variable sets.

*****

For any of the different modes, you can add or remove algorithms to compare by modifying algList in ComparisonParameters.java. You can also add or remove performance statistics by  modifying tableColumns.

This should be enough to get you started. Email malinsky@cmu.edu with any questions.

 
