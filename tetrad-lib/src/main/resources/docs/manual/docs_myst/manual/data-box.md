(data-box)=

# Data Box

The data box stores or manipulates data sets.


## Possible Parent Boxes of the Data Box

- A graph box
- An estimator box
- Another data box
- A simulation box
- A regression box


## Possible Child Boxes of the Data Box

- A graph box
- A parametric model box
- Another data box
- An estimator box
- A simulation box
- A search box
- A classify box
- A regression box
- A knowledge box


## Using the Data Box:

The data box stores the actual data sets from which causal structures are determined. Data can be loaded into the data box from a preexisting source, manually filled in Tetrad, or simulated from an instantiated model.


## Loading Data

Data sets loaded into Tetrad may be categorical, continuous, mixed, or covariance data.


### General Tabular Data

To load data, create a data box with no parent. When you double-click it, an empty data window will appear:

![](/_static/images/data_box_1.png)

Click "File -> Load Data" and select the text file or files that contain your data. The following window will appear:

![](/_static/images/data_box_2.png)

The text of the source file appears in the Data Preview window. Above, there are options to describe your file, so that Tetrad can load it correctly. If you are loading categorical, continuous, or mixed data values, select the “Tabular Data” button. If you are loading a covariance matrix, select “Covariance Data.” Note that if you are loading a covariance matrix, your text file should contain only the lower half of the matrix, as Tetrad will not accept an entire matrix.

Below the file type, you can specify a number of other details about your file, including information about the type of data (categorical/continuous/mixed), metadata JSON file, delimiter between data values, variable names, and more. If your data is mixed (some variables categorical, and some continuous), you must specify the maximum number of categories discrete variables in your data can take on. All columns with more than that number of values will be treated as continuous; the others will be treated as categorical. If you do not list the variable names in the file, you should uncheck “First row variable names.” If you provide case IDs, check the box for the appropriate column in the “Case ID column to ignore” area. If the case ID column is labeled, provide the name of the label; otherwise, the case ID column should be the first column, and you should check “First column.”

Below this, you can specify your comment markers, quote characters, and the character which marks missing data values. Tetrad will use that information to distinguish continuous from discrete variables. You may also choose more files to load (or remove files that you do not wish to load) in the “Files” panel on the lower left.


### Metadata JSON File

Metadata is optional in general data handling. But it can be very helpful if you want to overwrite the data type of given variable column. And the metadata MUST be a JSON file like the following example.

You can specify the name and data type for each variable. Variables that are not in the metadata file will be treated as domain variables and their data type will be the default data type when reading in columns described previously.

When you are satisfied with your description of your data, click “Validate” at the bottom of the window. Tetrad will check that your file is correctly formatted. If it is, you will receive a screen telling you that validation has passed with no error. At this point, you can revisit the settings page, or click “Load” to load the data.

![](/_static/images/data_box_3.png)

![](/_static/images/data_box_4.png)

You can now save this data set to a text file by clicking File: Save Data.

In addition to loading data from a file, you can manually enter data values and variable names by overwriting cells in the data table.


### Covariance Data

Covariance matrices loaded into Tetrad should be ascii text files. The first row contains the sample size, the second row contains the names of the variables. The first two rows are followed by a lower triangular matrix. For example:

Categorical, continuous, or mixed data should also be an ascii text file, with columns representing variables and rows representing cases. Beyond that, there is a great deal of flexibility in the layout: delimiters may be commas, colons, tabs, spaces, semicolons, pipe symbols, or whitespace; comments and missing data may be marked by any symbol you like; there may be a row of variable names or not; and case IDs may be present or not. There should be no sample size row. For example:


### Handling Tabular Data with Interventional Variables

This is an advanced topic for datasets that contain interventional (i.e., experimental) variables. We model a single intervention using two variables: status variable and value variable. Below is a sample dataset, in which `raf`, `mek`, `pip2`, `erk`, `atk` are the 5 domain variables, and `cd3_s` and `cd3_v` are an interventional pair (status and value variable respectively). `icam` in another intervention variable, but it's a combined variable that doesn't have status.

And the sample metadata JSON file looks like this:

Each intervention consists of a status variable and value variable. There are cases that you may have a combined interventional variable that doesn't have the status variable. In this case, just use `null`. The data type of each variable can either be discrete or continuous. We use a boolean flag to indicate the data type. From the above example, we only specified two domain variables in the metadata JSON, any variables not specified in the metadata will be treated as domain variables.


## Manipulating Data

The data box can also be used to manipulate data sets that have already been loaded or simulated. If you create a data box as the child of another box containing a data set, you will be presented with a list of operations that can be performed on the data. The available data manipulations are:


### Discretize Dataset

This operation allows you to make some or all variables in a data set discrete. If you choose it, a window will open.

![](/_static/images/data_box_5.png)

When the window first opens, no variables are selected, and the right side of the window appears blank; in this case, we have already selected X1 ourselves. In order to discretize a variable, Tetrad assigns all data points within a certain range to a category. You can tell Tetrad to break the range of the dataset into approximately even sections (Evenly Distributed Intervals) or to break the data points themselves into approximately even chunks (Evenly Distributed Values). Use the scrolling menu to increase or decrease the number of categories to create. You can also rename categories by overwriting the text boxes on the left, or change the ranges of the categories by overwriting the text boxes on the right. To discretize another variable, simply select it from the left. If you want your new data set to include the variables you did not discretize, check the box at the bottom of the window.

You may discretize multiple variables at once by selecting multiple variables. In this case, the ranges are not shown, as they will be different from variable to variable.


### Convert Numerical Discrete to Continuous

If you choose this option, any discrete variables with numerical category values will be treated as continuous variables with real values. For example, “1” will be converted to “1.0.”


### Calculator

The Calculator option allows you to add and edit relationships between variables in your data set, and to add new variables to the data set.

![](/_static/images/data_box_6.png)

In many ways, this tool works like the Edit Expression window in a generalized SEM parametric model. To edit the formula that defines a variable (which will change that variable’s values in the table) type that variable name into the text box to the left of the equals sign. To create a new variable, type a name for that variable into the text box to the left of the equals sign. Then, in the box on the right, write the formula by which you wish to define a new variable in place of, or in addition to, the old variable. You can select functions from the scrolling menu below. (For an explanation of the meaning of some the functions, see the section on generalized SEM models in the Parametric Model Box chapter.) To edit or create several formulae at once, click the “Add Expression” button, and another blank formula will appear. To delete a formula, check the box next to it and click the “Remove Selected Expressions” button.

When you click “Save” a table will appear listing the data. Values of variables whose formulae you changed will be changed, and any new variables you created will appear with defined values.


### Merge Deterministic Interventional Variables

This option looks for pairs of interventional variables (currently only discrete variables) that are deterministic and merges them into one combined variable. For domain variables that are fully determined, we'll add an attribute to them. Later in the knowledge box (Edges and Tiers), all the interventional variables (both status and value variables) and the fully-determined domain variables will be automatically put to top tier. And all other domain variables will be placed in the second tier.


### Merge Datasets

This operation takes two or more data boxes as parents and creates a data box containing all data sets in the parent boxes. Individual data sets will be contained in their own tabs in the resulting box.


### Convert to Correlation Matrix

This operation takes a tabular data set and outputs the lower half of the correlation matrix of that data set.


### Convert to Covariance Matrix

This operation takes a tabular data set and outputs the lower half of the covariance matrix of that data set.


### Inverse Matrix

This operation takes a covariance or correlation matrix and outputs its inverse. (Note: The output will not be acceptable in Tetrad as a covariance or correlation matrix, as it is not lower triangular.)


### Simulate Tabular from Covariance

This operation takes a covariance matrix and outputs a tabular data set whose covariances comply with the matrix.


### Difference of Covariance Matrices

This operation takes two covariance matrices and outputs their difference. The resulting matrix will be a well-formatted Tetrad covariance matrix data set.


### Sum of Covariance Matrices

This operation takes two covariance matrices and outputs their sum. The resulting matrix will be a well-formatted Tetrad covariance matrix data set.


### Average of Covariance Matrices

This operation takes two or more covariance matrices and outputs their average. The resulting matrix will be a well-formatted Tetrad covariance matrix data set.


### Convert to Time Lag Data

This operation takes a tabular data set and outputs a time lag data set, in which each variable is recorded several times over the course of an experiment. You can specify the number of lags in the data. Each contains the same data, shifted by one “time unit.” For instance, if the original data set had 1000 cases, and you specify that the time lag data set should contain two lags, then the third stage variable values will be those of cases 1 to 998, the second stage variable values will be those of cases 2 to 999, and the first stage variable values will be those of cases 3 to 1000.


### Convert to Time Lag Data with Index

This operation takes a tabular data set and outputs a time lag data set in the same manner as “Convert to Time Lag Data,” then adds an index variable.


### Convert to AR Residuals

This operation is performed on a time lag data set. Tetrad performs a linear regression on each variable in each lag with respect to each of the variables in the previous lag, and derives the error terms. The output data set contains only the error terms.


### Whiten

Takes a continuous tabular data set and converts it to a data set whose covariance matrix is the identity matrix.


### Nonparanormal Transform

Takes a continuous tabular data set and increases its Gaussianity, using a nonparanormal transformation to smooth the variables. (Note: This operation increases only marginal Gaussianity, not the joint, and in linear systems may eliminate information about higher moments that can aid in non-Gaussian orientation procedures.)


### Convert to Residuals

The input for this operation is a directed acyclic graph (DAG) and a data set. Tetrad performs a linear regression on each variable in the data set with respect to all the variables that the graph shows to be its parents, and derives the error terms. The output data set contains only the error terms.


### Standardize Data

This operation manipulates the data in your data set such that each variable has 0 mean and unit variance.


### Remove Cases with Missing Values

If you choose this operation, Tetrad will remove any row in which one or more of the values is missing.


### Replace Missing Values with Column Mode

If you choose this operation, Tetrad will replace any missing value markers with the most commonly used value in the column.


### Replace Missing Values with Column Mean

If you choose this operation, Tetrad will replace any missing value markers with the average of all the values in the column. Replace Missing Values with Regression Predictions: If you choose this operation, Tetrad will perform a linear regression on the data in order to estimate the most likely value of any missing value.


### Replace Missing Values by Extra Category

This operation takes as input a discrete data set. For every variable which has missing values, Tetrad will create an extra category for that variable (named by default “Missing”) and replace any missing data markers with that category.


### Replace Missing with Random

For discrete data, replaces missing values at random from the list of categories the variable takes in other cases. For continuous data, finds the minimum and maximum values of the column (ignoring the missing values) and picks a random number from U(min, max)


### Inject Missing Data Randomly

If you choose this operation, Tetrad will replace randomly selected data values with a missing data marker. You can set the probability with which any particular value will be replaced (that is, approximately the percentage of values for each variable which will be replaced with missing data markers).


### Bootstrap Sample

This operation draws a random subset of the input data set (you specify the size of the subset) with replacement (that is, cases which appear once in the original data set can appear multiple times in the subset). The resulting data set can be used along with similar subsets to achieve more accurate estimates of parameters.


### Split by Cases

This operation allows you to split a data set into several smaller data sets. When you choose it, a window opens.

![](/_static/images/data_box_7.png)

If you would like the subsets to retain the ordering they had in the original set, click “Original Order.” Otherwise, the ordering of the subsets will be assigned at random. You can also increase and decrease the number of subsets created, and specify the range of each subset.


### Permute Rows

This operation randomly reassigns the ordering of a data set’s cases.


### First Differences

This operation takes a tabular data set and outputs the first differences of the data (i.e., if X is a variable in the original data set and X’ is its equivalent in the first differences data set, X’1 = X2 – X1). The resulting data set will have one fewer row than the original.


### Concatenate Datasets

This operation takes two or more datasets and concatenates. The parent datasets must have the same number of variables.


### Copy Continuous Variables

This operation takes as input a data set and creates a new data set containing only the continuous variables present in the original.


### Copy Discrete Variables

This operation takes as input a data set and creates a new data set containing only the discrete variables present in the original.


### Remove Selected Variables


### Copy Selected Variables

As explained above, you can select an entire column in a data set by clicking on the C1, C2, C3, etc… cell above the column. To select multiple columns, press and hold the “control” key while clicking on the cells. Once you have done so, you can use the Copy Selected Variables tool to create a data set in which only those columns appear.


### Remove Constant Columns

This operation takes a data set as input, and creates a data set which contains all columns in the original data set except for those with constant values (such as, for example, a column containing nothing but 2’s).


### Randomly Reorder Columns

This operation randomly reassigns the ordering of a data set’s variables.


## Manually Editing Data

Under the Edit tab, there are several options to manipulate data. If you select a number of cells and click “Clear Cells,” Tetrad will replace the data values in the selected cells with a missing data marker. If you select an entire row or column and click “Delete selected rows or columns,” Tetrad will delete all data values in the row or column, and the name of the row or column. (To select an entire column, click on the category number above it, labeled C1, C2, C3, and so on. To select an entire row, click on the row number to the left of it, labeled 1, 2, 3, and so on.) You can also copy, cut, and paste data values to and from selected cells. You can choose to show or hide category names, and if you click on “Set Constants Col to Missing,” then in any column in which the variable takes on only one value (for example, a column in which every cell contains the number 2) Tetrad will set every cell to the missing data marker.

Under the Tools tab, the Calculator tool allows you to add or edit relationships between variables in the graph. For more information on how the Calculator tool works, see “Manipulating Data” section above.


## Data Information

Under the Tools tab, there are options to view information about your data in several formats.

The Plot Matrix tool shows a grid of scatter plots and histograms for selected variables. This may be used for continuous, discrete, or mixtures of continuous and discrete data. To select which variables to include in the rows and columns of the grid, click the variable lists to the right of the tool. To select multiple variables in these lists, use the shift or control keys when clicking; shift-click select ranges, whereas control-click will select additional single variables.

Histograms show the data distribution for a variable, with the width of each bar representing a range of values and the height of each bar representing how many data points fall into that range.

Scatter plots show a plot of variables taken two at a time. They plot values of one variable's values against another variable's values, point by point, and allow one to see the distribution of points for the pair of variables.

If viewing a grid of plots, one wishes to view a single plot in this grid, double-click on the desired plot, and it will be magnified so that it is in the only plot viewed. Double-click on the magnified plot to return to the grid.

The “Settings” menu contains some tools to control the output. One may add regression lines to the scatter plots or select the number of bins to include in the histograms.

Finally, one may condition on ranges of variables or particular discrete values by selecting “Edit Conditioning Variables and Ranges.” This brings up a dialog that lets one add conditioning variables with particular ranges for continuous variables or values for discrete values. For continuous ranges, one may pick “Above Average,” “Below Average,” or “In n-tile” (where n is specified) or give a particular range manually. One may add as many conditions as one prefers; when one clicks “OK,” all plots will be updated to reflect these conditioning choices.

![](/_static/images/data_box_8b.png)

![](/_static/images/data_box_8a.png)

The Q-Q Plot tool is a test for normality of distribution.

![](/_static/images/data_box_10.png)

If a variable has a distribution which is approximately Normal, its Q-Q plot should appear as a straight line with a positive slope. You can select the variable whose Q-Q plot you wish to view from the drop-down menu on the right.

The Normality Tests tool gives a text box with the results of the Kolmogorov and Anderson Darling Tests for normality for each variable. The Descriptive Statistics tool gives a text box with statistical information such as the mean, median, and variance of each variable.
