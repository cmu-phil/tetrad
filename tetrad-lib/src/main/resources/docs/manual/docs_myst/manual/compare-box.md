(compare-box)=

# Compare Box

The compare box compares two or more graphs.


## Possible Parent Boxes of the Compare box:

- A graph box
- An instantiated model box
- An estimator box
- A simulation box
- A search box
- A regression box


## Possible Child Boxes of the Compare box:

- None


## Edgewise Comparisons

An edgewise comparison compares two graphs, and gives a textual list of the edges which must be added to or taken away from one to make it identical to the other.

Take, for example, the following two graphs. The first is the reference graph, the second is the graph to be compared to it. When the Edgewise Comparison box is opened, a comparison like this appears:

![](/_static/images/compare_box_12.png)

You may choose (by a menu in the upper left part of the box) whether the graph being compared is the original DAG, or the CPDAG of the original DAG, of the PAG of the original DAG

When the listed changes have been made to the second graph, it will be identical to the first graph.


## Stats List Graph Comparisons

A stats list graph comparison tallies up and presents statistics for the differences and similarities between a true graph and a reference graph. Consider the example used in the above section; once again, we’ll let graph one be the true graph. Just as above, when the graphs are input to the tabular graph compare box, we must specify which of the graphs is the reference graph, and whether it contains latent variables. When the comparison is complete, the following window results:

![](/_static/images/compare_box_1.png)

You may choose (by a menu in the upper left part of the box) whether the graph being compared is the original DAG, or the CPDAG of the original DAG, of the PAG of the original DAG

The first columns gives an abbreviation for the statistic; the second columns gives a definition of the statistic. The third columns gives the statistic value.


## Misclassifications

A misclassification procedure organizes a graph comparison by edge type. The edge types (undirected, directed, uncertain, partially uncertain, bidirected, and null) are listed as the rows and columns of a matrix, with the true graph edges as the row headers and the target graph edges as the column headers. If, for example, there are three pairs of variables that are connected by undirected edges in the reference graph, but are connected by directed edges in the estimated graph, then there will be a 3 in the (undirected, directed) cell of the matrix. An analogous method is used to represent endpoint errors. For example:

![](/_static/images/compare_box_13.png)


## Graph Intersections

A graph intersection compares two or more graphs in the same comparison. It does so by ranking adjacencies (edges without regard to direction) and orientations based on how many of the graphs they appear in. In an n-graph comparison, it first lists any adjacencies found in all n graphs. Then it lists all adjacencies found in n – 1 graphs, then adjacencies found in n – 2 graphs, and so on.

After it has listed all adjacencies, it lists any orientations that are not contradicted among the graphs, again in descending order of how many graphs the orientation appears in. An uncontradicted orientation is one on which all graphs either agree or have no opinion. So if the edge X  Y appears in all n graphs, it will be listed first. If the edge X  Z appears in n – 1 graphs, it will be listed next, but only if the nth graph doesn’t contradict it—that is, only if the edge Z  X does not appear in the final graph. If the undirected edge Z – X appears in the final graph, the orientation X  Z is still considered to be uncontradicted.

Finally, any contradicted orientations (orientations that the graphs disagree on) are listed.


## Independence Facts Comparison

Rather than comparing edges or orientation, this option directly compares the implied dependencies in two graphs. When you initially open the box, you will see the following window:

![](/_static/images/compare_box_6.png)

The drop-down menu allows you to choose which variables you want to check the dependence of. If you select more than two variables, any subsequent variables will be considered members of the conditioning set. So, if you select variables X1, X2, and X3, in that order, the box will determine whether X1 is independent of X2, conditional on X3, in each of the graphs being compared. When you click “List,” in the bottom right of the window, the results will be displayed in the center of the window:

![](/_static/images/compare_box_7.png)


## Edge Weight Similarity Comparisons

Edge weight (linear coefficient) similarity comparisons compare two linear SEM instantiated models. The output is a score equal to the sum of the squares of the differences between each corresponding edge weight in each model. Therefore, the lower the score, the more similar the two graphs are. The score has peculiarities: it does not take account of the variances of the variables, and may therefore best be used with standardized models; the complete absence of an edge is scored as 0—so a negative coefficient compares less well with a positive coefficient than does no edge at all.

Consider, for example, an edge weight similarity comparison between the following two SEM IMs:

![](/_static/images/compare_box_8.png)

![](/_static/images/compare_box_9.png)

When they are input into an edge weight similarity comparison, the following window results:

![](/_static/images/compare_box_10.png)

This is, unsurprisingly, a high score; the input models have few adjacencies in common, let alone similar parameters.


## Model Fit

A model fit comparison takes a simulation box and a search box (ideally, a search that has been run on the simulated data in the simulation box), and provides goodness-of-fit statistics, including a Student’s t statistic and p value for each edge, for the output graph and the data, as well as estimating the values of any parameters. It looks and functions identically to the estimator box, but unlike the estimator box, it takes the search box directly as a parent, without needing to isolate and parameterize the graph output by the search.


## Markov Check

The Markov Checker checks to see whether the Markov Condition is satisfied for a given graph. A simple version of the Markov Condition states that for any variable X, X is independent of all non-descendants of X given X’s parents. The Markov Checker will output all such implied independences and their p-values; these p-values should be distributed as U(0, 1) if the Markov Condition is satisfied, so violations can often be detected by plotting a histogram of these p-values or doing an Anderson-Darling test or a Kolmogorov-Smirnov test to see if the hypothesis that they are drawn from a U(0, 1) distribution can be rejected. This sort of check is actually more general, since graphical implicaitons of separation are not limited to directect acyclic graphs (DAGs) but can be inferred from many types of graphs, including CPDAGs, MAGs, ADMGs, and PAGs.

Instructions for using the Markov Checker are included in the box itself, in the "Help" tab.


## IDA Check

The IDA Checker check loops through all pairs of variable (X, Y) and calculates the IDA minimum effect for each X on Y, for a linear CPDAG model. The IDA minimum effect is the minimum effect of X on Y, regressing Y on S U {X} for all possible parent sets of X in the CPDAG. This gives a range of effects, and one then see whether the true effect of X on Y (as calculated from the true SEM IM) falls within this range. If it does not, then the IDA minimum effect is not consistent with the true effect.

The IDA check table gives information to help the user assess these results along with several summary statistics. Further instructions for using the IDA Checker are included in the box itself, in the "Help" tab.


## Algcomparison

The Algcomparison (Algorithm comparison) tool allows the user to compare the results of multiple searches. The user can select one or more simulations, one or more algorithm (with selected test and/or score), and one or more table columns representing columns of parameter values or else statistics that are calculated based on the comparisons done. For most of these values, the use can specify multiple options for values, and the tool will iterate over all sensible combinations of these values and output a table of results. Full results, including all simulated dataset, all true graphs, all estimated graphs, and all timing results, are saved to the hard drive.

Further instructions for using the Algcomparison tool are included in the box itself, in the "Help" tab.
