(updater-box)=

# Updater Box

The updater box takes an instantiated model as input, and, given information about the values of parameters in that model, updates the information about the values and relationships of other parameters.

The Updater allows the user to specify values of variables as “Evidence.” The default is that the conditional probabilities (Bayes net models; categorical variables) or conditional means (SEM models; continuous variables) are computed. For any variable for which evidence is specified, the user can click on “Manipulated,” in which case the Updater will calculate the conditional probabilities or conditional means for other variables when the evidence variables are forced to have their specified values. In manipulated calculations, all connections into a measured variable are discarded, the manipulated variables are treated as independent of their causes in the graph, and probabilities for variables that are causes of the manipulated variables are unchanged.

There are four available updater algorithms in Tetrad: the approximate updater, the row summing exact updater, and the Junction Tree Updater, and the SEM updater. All except for the SEM updater function only when given Bayes instantiated models as input; the SEM updater functions when given a SEM instantiated model as input. None of the updaters work on cyclic models.


## Possible Parent Boxes of the Updater Box:

- An instantiated model box
- An estimator box


## Possible Child Boxes of the Updater Box:

- An instantiated model box (Note that the instantiated model will have the updated parameters)


## Approximate Updater

The approximated updater is a fast but inexact algorithm. It randomly draws a sample data set from the instantiated model and calculates the conditional frequency of the variable to be estimated.

Take, for example, the following instantiated model:

![](/_static/images/updater_box_1.png)

When it is input into the approximate updater, the following window results:

![](/_static/images/updater_box_2.png)

If we click “Do Update Now” now, without giving the updater any evidence, the right side of the screen changes to show us the marginal probabilities of the variables.

![](/_static/images/updater_box_3.png)

The blue lines, and the values listed across from them, indicate the probability that the variable takes on the given value in the input instantiated model. The red lines indicate the probability that the variable takes on the given value, given the evidence we’ve added to the updater.

Since we have added no evidence to the updater, the red and blue lines are very similar in length. To view the marginal probabilities for a variable, either click on the variable in the graph to the left, or choose it from the scrolling menu at the top of the window. At the moment, they should all be very close to the marginal probabilities taken from the instantiated model.

Now, we’ll return to the original window. We can do so by clicking “Edit Evidence” under the Evidence tab. Suppose we know that X1 takes on the value 1 in our model, or suppose we merely want to see how X1 taking that value affects the values of the other variables. We can click on the box that says “1” next to X1. When we click “Do Update Now,” we again get a list of the marginal probabilities for X1.

![](/_static/images/updater_box_4.png)

Now that we have added evidence, the “red line” marginal probabilities have changed; for X1, the probability that X1=1 is 1, because we’ve told Tetrad that that is the case. Likewise, the probabilities that X1=0 and X1=2 are both 0.

Now, let’s look at the updated marginal probabilities for X2, a parent of X1.

![](/_static/images/updater_box_5.png)

![](/_static/images/updater_box_6.png)

The first image is the marginal probabilities before we added the evidence that X1=1. The second image is the updated marginal probabilities. They have changed; in particular, it has become much more likely that X2=0.

Under the Mode tab, we can change the type of information that the updater box gives us. The mode we have been using so far is “Marginals Only (Multiple Variables).” We can switch the mode to “In-Depth Information (Single Variable).” Under this mode, when we perform the update, we receive more information (such as log odds and joints, when supported; joint probabilities are not supported by the approximate updater), but only about the variable which was selected in the graph when we performed the update. To view information about a different variable, we must re-edit the evidence with that variable selected.

If the variable can take one of several values, or if we know the values of more than one variable, we can select multiple values by pressing and holding the Shift key and then making our selections. For instance, in the model above, suppose that we know that X1 can be 1 or 2, but not 0. We can hold the Shift key and select the boxes for 1 and 2, and when we click “Do Update Now,” the marginal probabilities for X2 look like this:

![](/_static/images/updater_box_7.png)

Since X1 must be 1 or 2, the updated probability that it is 0 is now 0. The marginal probabilities of X2 also change:

![](/_static/images/updater_box_8.png)

The updated marginal probabilities are much closer to their original values than they were when we knew that X1 was 1.

Finally, if we are arbitrarily setting the value of a variable—that is, the values of its parents have no effect on its value—we can check the “Manipulated” box next to it while we are we editing evidence, and the update will reflect this information.

Note that multiple values cannot be selected for evidence for SEM models.


## Row Summing Exact Updater

The row summing exact updater is a slower but more accurate updater than the approximate updater. The complexity of the algorithm depends on the number of variables and the number of categories each variable has. It creates a full exact conditional probability table and updates from that. Its window functions exactly as the approximate updater does, with two exceptions: in “Multiple Variables” mode, you can see conditional as well as marginal probabilities, and in “Single Variable” mode, you can see joint values.


## Junction Tree Exact Updater

The Junction Tree exact updater is another exact learning algorithm. Its window functions exactly as the approximate updater down, with one exception: in “Multiple Variables” mode, you can see conditional as well as marginal probabilities.


## SEM Updater

The SEM updater does not deal with marginal probabilities; instead, it estimates means.

![](/_static/images/updater_box_9.png)

When it is input to the SEM updater, the following window results:

![](/_static/images/updater_box_10.png)

Suppose we know that the mean of X1 is .5. When we enter that value into the text box on the left and click “Do Update Now,” the model on the right updates to reflect that mean, changing the means of both X1 and several other variables. In the new model, the means of X2, X4, and X5 will all have changed. If we click the “Manipulated” check box as well, it means that we have arbitrarily set the mean of X1 to .5, and that the value of its parent variable, X4, has no effect on it. The graph, as well as the updated means, changes to reflect this.

The rest of the window has the same functionality as a SEM instantiated model window, except as noted above.
