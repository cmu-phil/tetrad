(instantiated-model-box)=

# Instantiated Model Box

The instantiated model (IM) box takes a parametric model and assigns values to the parameters.


## Possible Parent Boxes of the Instantiated Model Box:

- A parametric model box
- Another instantiated model box
- An estimator box
- A simulation box
- An updater box


## Possible Child Boxes of the Instantiated Model Box:

- A graph box
- A compare box
- A parametric model box
- Another instantiated model box
- An estimator box
- A simulation box
- A search box
- An updater box
- A classify box
- A knowledge box


## Bayes Instantiated Models

A Bayes IM consists of a Bayes parametric model with defined probability values for all variables. This means that, conditional on the values of each of its parent variables, there is a defined probability that a variable will take on each of its possible values. For each assignment of a value to each of the parents of a variable X, the probabilities of the several values of X must sum to 1.

You can manually set the probability values for each variable, or have Tetrad assign them randomly. If you choose to have Tetrad assign probability values, you can manually edit them later.

Here is an example of a Bayes PM and its randomly created instantiated model:

![](/_static/images/instantiated_model_box_1.png)

![](/_static/images/instantiated_model_box_2.png)

In the model above, when X4 and X5 are both 0, the probability that X5 is 0 is 0.0346, that X5 is 1 is 0.4425, and that X5 is 2 is 0.5229. Since X5 must be 0, 1, or 2, those three values must add up to one, as must the values in every row.

To view the probability values of a variable, either double click on the variable in the graph or choose it from the drop-down menu on the right. You can manually set a given probability value by overwriting the text box. Be warned that changing the value in one cell will delete the values in all the other cells in the row. Since the values in any row must sum to one, if all the cells in a row but one are set, Tetrad will automatically change the value in the last cell to make the sum correct. For instance, in the above model, if you change the first row such that the probability that X5 = 0 is 0.5000 and the probability that X5 = 1 is 0.4000, the probability that X5 = 2 will automatically be set to 0.1000.

If you right-click on a cell in the table (or two-finger click on Macs), you can choose to randomize the probabilities in the row containing that cell, randomize the values in all incomplete rows in the table, randomize the entire table, or randomize the table of every variable in the model. You can also choose to clear the row or table.


## Dirichlet Instantiated Models

A Dirichlet instantiated model is a specialized form of a Bayes instantiated model. Like a Bayes IM, a Dirichlet IM consists of a Bayes parametric model with defined probability values. Unlike a Bayes IM, these probability values are not manually set or assigned randomly. Instead, the pseudocount is manually set or assigned uniformly, and the probability values are derived from it. The pseudocount of a given value of a variable is the number of data points for which the variable takes on that value, conditional on the values of the variable’s parents, where these numbers are permitted to take on non-negative real values. Since we are creating models without data, we can set the pseudocount to be any number we want. If you choose to create a Dirichlet IM, a window will open allowing you to either manually set the pseudocounts, or have Tetrad set all the pseudocounts in the model to one number, which you specify.

Here is an example of a Bayes PM and the Dirichlet IM which Tetrad creates from it when all pseudocounts are set to one:

![](/_static/images/instantiated_model_box_3.png)

![](/_static/images/instantiated_model_box_4.png)

In the above model, when X2=0 and X6=0, there is one (pseudo) data point at which X4=0, one at which X4=1, and one at which X4=2. There are three total (pseudo) data points in which X2=0 and X6=0. You can view the pseudocounts of any variable by clicking on it in the graph or choosing it from the drop-down menu at the top of the window. To edit the value of a pseudocount, double-click on it and overwrite it. The total count of a row cannot be directly edited.

From the pseudocounts, Tetrad determines the conditional probability of a category. This estimation is done by taking the pseudocount of a category and dividing it by the total count for its row. For instance, the total count of X4 when X2=0 and X6=0 is 3. So the conditional probability of X4=0 given that X2=0 and X6=0 is 1/3. The reasoning behind this is clear: in a third of the data points in which X2 and X6 are both 0, X4 is also 0, so the probability that X4=0 given that X2 and X6 also equal 0 is probably one third. This also guarantees that the conditional probabilities for any configuration of parent variables add up to one, which is necessary.

To view the table of conditional probabilities for a variable, click the Probabilities tab. In the above model, the Probabilities tab looks like this:

![](/_static/images/instantiated_model_box_5.png)


## SEM Instantiated Models

A SEM instantiated model is a SEM parametric model in which the parameters and error terms have defined values. It assumes that relationships between variables are linear, and that error terms have Gaussian distributions. If you choose to create a SEM IM, the following window will open:

![](/_static/images/instantiated_model_box_6.png)

Using this box, you can specify the ranges of values from which you want coefficients, covariances, and variances to be drawn for the parameters in the model. In the above box, for example, all linear coefficients will be between -1.0 and 1.0. If you uncheck “symmetric about zero,” they will only be between 0.0 and 1.0.

Here is an example of a SEM PM and a SEM IM generated from it using the default settings:

![](/_static/images/instantiated_model_box_7.png)

![](/_static/images/instantiated_model_box_8.png)

You can now manually edit the values of parameters in one of two ways. Double-clicking on the parameter in the graph will open up a small text box for you to overwrite. Or you can click on the Tabular Editor tab, which will show all the parameters in a table which you can edit. The Tabular Editor tab of our SEM IM looks like this:

![](/_static/images/instantiated_model_box_9.png)

In the Tabular Editor tab of a SEM estimator box (which functions similarly to the SEM IM box), the SE, T, and P columns provide statistics showing how robust the estimation of each parameter is. Our SEM IM, however, is in an instantiated model box, so these columns are empty.

The Implied Matrices tab shows matrices of relationships between variables in the model. In the Implied Matrices tab, you can view the covariance or correlation matrix for all variables (including latents) or just measured variables. In our SEM IM, the Implied Matrices tab looks like this:

![](/_static/images/instantiated_model_box_10.png)

You can choose the matrix you wish to view from the drop-down menu at the top of the window. Only half of any matrix is shown, because in a well-formed acyclic model, the matrices should be symmetric. The cells in the Implied Matrices tab cannot be edited.

In an estimator box, the Model Statistics tab provides goodness of fit statistics for the SEM IM which has been estimated. Our SEM IM, however, is in an instantiated model box, so no estimation has occurred, and the Model Statistics tab is empty.


## Standardized SEM Instantiated Models

A standardized SEM instantiated model consists of a SEM parametric model with defined values for its parameters. In a standardized SEM IM, each variable (not error terms) has a Normal distribution with 0 mean and unit variance. The input PM to a standardized SEM IM must be acyclic.

Here is an example of an acyclic SEM PM and the standardized SEM IM which Tetrad creates from it

![](/_static/images/instantiated_model_box_11.png)

![](/_static/images/instantiated_model_box_12.png)

To edit a parameter, double-click on it. A slider will open at the bottom of the window (shown above for the edge parameter between X1 and X2). Click and drag the slider to change the value of the parameter, or enter the specific value you wish into the box. The value must stay within a certain range in order for the variables in the model to remain standard Normal (N(0, 1)), so if you attempt to overwrite the text box on the bottom right with a value outside the listed range, Tetrad will not allow it. That is, given that the variables are all distributed as N(0, 1), there is a limited range in which each parameter may be adjusted; these ranges vary parameter by parameter, given the values of the other parameters. In a standardized SEM IM, error terms are not considered parameters and cannot be edited, but you can view them by clicking Parameters: Show Error Terms.

It is possible to make a SEM IM with a time lag graph, even with latent variables. This does not work for other types of models, such as Bayes IMs or for mixed data (for which no IM is currently available-- though mixed data can be simulated in the Simulate box with an appropriate choice of simulation model). Standardization for time lag model is not currently available.

The Implied Matrices tab works in the same way that it does in a normal SEM IM.


## Generalized SEM Instantiated Models

A generalized SEM instantiated model consists of a generalized SEM parametric model with defined values for its parameters. Since the distributions of the parameters were specified in the SEM PM, Tetrad does not give you the option of specifying these before it creates the instantiated model.

Here is an example of a generalized SEM PM and its generalized SEM IM:

![](/_static/images/instantiated_model_box_13.png)

![](/_static/images/instantiated_model_box_14.png)

Note that the expressions for X6 and X2 are not shown, having been replaced with the words “long formula.” Formulae over a certain length—the default setting is 25 characters—are hidden to improve visibility. Long formulae can be viewed in the Variables tab, which lists all variables and their formulae. You can change the cutoff point for long formulae by clicking Tools: Formula Cutoff.

If you double-click on a formula in either the graph or the Variables tab, you can change the value of the parameters in that formula.
