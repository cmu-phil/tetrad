(search-box)=

# Search Box

The search box takes as input a data set (in either a data or simulation box) and optionally a knowledge box, and searches for causal explanations represented by directed graphs. The result of a search is not necessarily—and not usually—a unique graph, but an object such as a CPDAG that represents a set of graphs, usually a Markov Equivalence class. More alternatives can be found by varying the parameters of search algorithms.


## Possible Parent Boxes of the Search Box

- A graph box
- A parametric model box
- An instantiated model box
- An estimator box
- A data box
- A simulation box
- Another search box
- A regression box
- A knowledge box


## Possible Child Boxes of the Simulation Box

- A graph box
- A compare box
- A parametric model box
- A simulation box
- Another search box
- A knowledge box


## Using the Search Box

For more information that in included about the search algorithms than is included in the text below, please see our Javadocs for the Search package.

Using the search box requires you to select an algorithm (optionally select a test/score), confirm/change search parameters and finally run the search.

![](/_static/images/search_box_1.png)

The search box first asks what algorithm, statistical tests and/or scoring functions you would like to use in the search. The upper left panel allows you to filter for different types of search algorithms with the results of filtering appearing in the middle panel. Selecting a particular algorithm will update the algorithm description on the right panel.

Choosing the correct algorithm for your needs is an important consideration. Tetrad provides over 30 search algorithms (and more are added all the time) each of which makes different assumptions about the input data, uses different parameters, and produces different kinds of output. For instance, some algorithms produce Markov blankets or CPDAGs, and some produce full graphs; some algorithms work best with Gaussian or non-Gaussian data; some algorithms require an alpha value, some require a penalty discount, and some require both or neither. You can narrow down the list using the “Algorithm filter" panel, which allows you to limit the provided algorithms according to whichever factor is important to you.

Depending on the datatype used as input for the search (i.e., continuous, discrete, or mixed data) and algorithm selected, the lower left panel will display available statistical tests (i.e., tests of independence) and Bayesian scoring functions.

After selecting the algorithm and desired test/score, click on "Set parameters" which will allow you to confirm/change the parameters of the search.

After optionally changing any search parameters, click on "Run Search and Generate Graph" which will execute the search.

Notably there are some experimental algorithms available in this box. To see these, select File->Settings->Enable Experimental.
