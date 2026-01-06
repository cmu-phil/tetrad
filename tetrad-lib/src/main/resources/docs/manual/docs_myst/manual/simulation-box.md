(simulation-box)=

# Simulation Box

The simulation box takes a graph, parametric model, or instantiated model and uses it to simulate a data set.


## Possible Parent Boxes of the Simulation Box

- A graph box
- A parametric model box
- An instantiated model box
- An estimator box
- A data box
- Another simulation box
- A search box
- An updater box
- A regression box


## Possible Child Boxes of the Simulation Box

- A graph box
- A compare box
- A parametric model box
- An instantiated model box
- An estimator box
- A data box
- Another simulation box
- A search box
- A classify box
- A regression box
- A knowledge box


## Using the Simulator Box

When you first open the simulation box, you will see some variation on this window:

![](/_static/images/simulation_box_1.png)

The “True Graph” tab contains the graph from which data is simulated.


### The Simulation Box with no Input

Because it has no input box to create constraints, a parentless simulation box offers the greatest freedom for setting the graph type, model type, and parameters of your simulated data. In particular, it is the only way that the simulation box will allow you to create a random graph or graphs within the box. (If you are simulating multiple data sets, and want to use a different random graph for each one, you can select “Yes” under “Yes if a different graph should be used for each run.”) You can choose the type of graph you want Tetrad to create from the “Type of Graph” drop-down list.

This option creates a DAG by randomly adding forward edges (edges that do not point to a variable’s ancestors) one at a time. You can specify graph parameters such as number of variables, maximum and minimum degrees, and connectedness.

This option creates a DAG by randomly adding edges with a given edge probability. The graph is then oriented as a DAG by choosing a causal order.

This option creates a DAG whose variable’s degrees obey a power law. You can specify graph parameters such as number of variables, alpha, beta, and delta values.

This option creates a cyclic graph. You can specify graph parameters such as number of variables, maximum and average degrees, and the probability of the graph containing at least one cycle.

It is very important when dealing with cyclic models to realize that the potential exists always to instantiate these models with coefficients that are too large. Always, to keep simulations from "exploding" ("diverging"--i.e., having simulation values that tend to infinity over time), it is necessary to make sure that coefficient values are relatively small, usually less than 1. One can tell whether a model will produce simulations that diverge in value by testing the eigenvalues of the covariance matrix of the data. If any of these eigenvalues are greater than 1, the potential exists for the simulation to "explode" toward infinity over time.

This option creates a one-factor multiple indicator model. You can specify graph parameters such as number of latent nodes, number of measurements per latent, and number of impure edges.

This option creates a two-factor multiple indicator model. You can specify graph parameters such as number of latent nodes, number of measurements per latent, and number of impure edges.

In addition to the graph type, you can also specify the type of model you would like Tetrad to simulate.

Simulates a Bayes instantiated model. You can specify model parameters including maximum and minimum number of categories for each variable.

Simulates a SEM instantiated model. You can specify model parameters including coefficient, variance, and covariance ranges.

Simulates data using a linear Markov 1 DBN without concurrent edges. The Fisher model suggests that shocks should be applied at intervals and the time series be allowed to move to convergence between shocks. This simulation has many parameters that can be adjusted, as indicated in the interface. The ones that require some explanation are as follows.

- Low end of coefficient range, high end of coefficient range, low end of variance range, high end of variance range. Each variable is a linear function of the parents of the variable (in the previous time lag) plus Gaussian noise. The coefficients are drawn randomly from U(a, b) where a is the low end of the coefficient range and b is the high end of the coefficient range. Here, a < b. The Gaussian noise is drawn uniformly from U(c, d), where c is the low end of the variance range and d is the high end of the variance range. Here, c < d.
- Yes, if negative values should be considered. If no, only positive values will be recorded. This should not be used for large numbers of variables, since it is more difficult to find cases with all positive values when the number of variables is large.
- Percentage of discrete variables. The model generates continuous data, but some or all of the variables may be discretized at random. The user needs to indicate the percentage of variables (randomly chosen that one wishes to have discretized. The default is zero—i.e., all continuous variables.
- Number of categories of discrete variables. For the variables that are discretized, the number of categories to use to discretize each of these variables.
- Sample size. The number of records to be simulated.
- Interval between shocks. The number of time steps between shocks in the model.
- Interval between data recordings. The data are recorded every so many steps. If one wishes to allow to completely converge between steps (i.e., produce equilibrium data), set this interval to some large number like 20 and set the interval between shocks likewise to 20 Other values can be used, however.
- Epsilon for convergence. Even if you set the interval between data recordings to a large number, you can specify an epsilon such that if all values of variables differ from their values one time step back by less than epsilon, the series will be taken to have converged, and the remaining steps between data recordings will be skipped, the data point being recorded at convergence.

This is a model for simulating mixed data (data with both continuous and discrete variables. The model is given in Lee J, Hastie T. 2013, Structure Learning of Mixed Graphical Models, Journal of Machine Learning Research 31: 388-396. Here, mixtures of continuous and discrete variables are treated as log-linear.

- Percentage of discrete variables. The model generates continuous data, but some or all of the variables may be discretized at random. The user needs to indicate the percentage of variables (randomly chosen that one wishes to have discretized. The default is zero—i.e., all continuous variables.
- Number of categories of discrete variables. For the variables that are discretized, the number of categories to use to discretize each of these variables.
- Sample size. The number of records to be simulated.

This is a special simulation for representing time series. Concurrent edges are allowed. This can take a Time Series Graph as input, in which variables in the current lag are written as functions of the parents in the current and previous lags.

- Sample size. The number of records to be simulated.


### Functional–Causal Simulator (Zhang 2015)

Generates data from an additive, potentially nonlinear functional-causal model (Zhang 2015). Each variable Xi is produced as Xi := fi(PAi) + Ui, where Ui are mutually independent noise terms.

- GUI: Simulate ▸ Functional-Causal
- CLI: -sim-func -n {samples} -dag {graphSpec}
- Outputs: continuous data set; ground-truth DAG.


### Additive-Noise Simulator (Peters 2014)

Implements the linear/non-linear additive-noise model used by Peters et al., 2014. Noise variance is user-selectable; functions are sampled from Gaussian processes.


### Post-Non-Linear Simulator (Zhang & Hyvärinen 2009)

Samples data from the PNL causal model X := g(f(PA) + U), covering non-monotone g when required.


### Causal-Perceptron Network (DJI)

Experimental simulator for deep causal generative networks (Dji internal research). Currently marked “beta”.


### The Simulation Box with a Graph Input

If you input a graph, you will be able to simulate any kind of model, with any parameters. But the model will be constrained by the graph you have input (or the subgraph you choose in the “True Graph” tab.) Because of this, if you create a simulation box with a graph as a parent, you will not see the “Type of Graph” option.


### The Simulation Box with a Parametric Model Input

At the time of writing, a simulation box with a parametric model input acts as though the PM’s underlying graph had been input into the box.


### The Simulation Box with an Instantiated Model Input

If you input an instantiated model, your only options will be the sample size of your simulation and the number of data sets you want to simulate; Tetrad will simulate every one of them based on the parameters of the IM. The model will not be re-parameterized for each run of the simulation.
