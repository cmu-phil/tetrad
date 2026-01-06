(regression-box)=

# Regression Box

The regression box performs regression on variables in a data set, in an attempt to discover causal correlations between them. Both linear and regression are available.


## Possible Parent Boxes of the Regression Box

- A data box
- A simulation box


## Possible Child Boxes of the Instantiated Model Box:

- A graph box
- A compare box
- A parametric model box
- A data box
- A simulation box
- A search box


## Multiple Linear Regression

Linear regression is performed upon continuous data sets. If you have a categorical data set upon which you would like to perform linear regression, you can make it continuous using the data manipulation box.

Take, for example, a data set with the following underlying causal structure:

![](/_static/images/regression_box_1.png)

When used as input to the linear regression box, the following window results:

![](/_static/images/regression_box_2.png)

To select a variable as the response variable, click on it in the leftmost box, and then click on the top right-pointing arrow. If you change your mind about which variable should be the response variable, simply click on another variable and click on the arrow again.

To select a variable as a predictor variable, click on it in the leftmost box, and then click on the second right-pointing arrow. To remove a predictor variable, click on it in the predictor box and then click on the left-pointing arrow.

Clicking “Sort Variables” rearranges the variables in the predictor box so that they follow the same order they did in the leftmost box. The alpha value in the lower left corner is a threshold for independence; the higher it is set, the less discerning Tetrad is when determining the independence of two variables.

When we click “Execute,” the results of the regression appear in the box to the right. For each predictor variable, Tetrad lists the standard error, t value, and p value, and whether its correlation with the response variable is significant.

The Output Graph tab contains a graphical model of the information contained in the Model tab. For the case in which X4 is the response variable and X1, X2, and X3 are the predictors, Tetrad finds that only X1 is significant, and the output graph looks like this:

![](/_static/images/regression_box_3.png)

Comparison to the true causal model shows that this correlation does exist, but that it runs in the opposite direction.


## Logistic Regression

Logistic regression may be run on discrete, continuous, or mixed data sets; however, the response variable must be binary. In all other ways, the logistic regression box functions like the linear regression box.
