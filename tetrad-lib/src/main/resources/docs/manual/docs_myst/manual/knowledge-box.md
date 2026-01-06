(knowledge-box)=

# Knowledge Box

The knowledge box takes as input a graph or a data set and imposes additional constraints onto it, to aid with search.


## Possible Parent Boxes of the Knowledge Box:

- A graph box
- A parametric model box
- An instantiated model box
- A data box
- A simulation box
- A search box
- Another knowledge box


## Possible Child Boxes of the Knowledge Box:

- A search box
- Another knowledge box


## Tiers and Edges

The tiers and edges option allows you to sort variables into groupings that can or cannot affect each other. It also allows you to manually add forbidden and required edges one at a time.


### Tiers

The tiers tab for a graph with ten variables looks like this:

![](/_static/images/knowledge_box_1.png)

Tiers separate your variables into a timeline. Variables in higher-numbered tiers occur later than variables in lower-numbered tiers, which gives Tetrad information about causation. For example, a variable in Tier 3 could not possibly be a cause of a variable in Tier 1.

To place a variable in a tier, click on the variable in the “Not in tier” box, and then click on the box of the tier. If you check the “Forbid Within Tier” box for a tier, variables in that tier will not be allowed to be causes of each other. To increase or decrease the number of tiers, use the scrolling box in the upper right corner of the window.

You can quickly search, select and place variables in a tier using the Find button associated with each tier. Enter a search string into the Find dialogue box using asterisks as wildcard indicators. E.g., "X1*" would find and select variables X1 and X10.

You can also limit the search such that edges from one tier only are added to the next immediate tier e.g., if Tier 1 "Can cause only next tier" is checked then edges from variables in Tier 1 to variables in Tier 3 are forbidden.


### Handling of Interventional Variables in Tiers

If you have annotated your variables with interventional status and interventional value tags using a metadata JSON file (see Data Box section) the Tiers and Edges panel will automatically place these variables in Tier 1. If you have information about the effects of the intervention variables you can use the groups tab to indicate this.


### Groups

The groups tab for a graph with four variables looks like this:

![](/_static/images/knowledge_box_2.png)

In the groups tab, you can specify certain groups of variables which are forbidden or required to cause other groups of variables. To add a variable to the “cause” section of a group, click on the variable in the box at the top, and then click on the box to the left of the group’s arrow. To add a variable to the “effect” section of a group, click on the variable in the box at the top, and then click on the box to the right of the group’s arrow. You can add a group by clicking on one of the buttons at the top of the window, and remove one by clicking the “remove” button above the group’s boxes.


### Edges

The edges tab for a graph with four variables looks like this:

![](/_static/images/knowledge_box_3.png)

In the edges tab, you can require or forbid individual causal edges between variables. To add an edge, click the type of edge you’d like to create, and then click and drag from the “cause” variable to the “effect” variable.

You can also use this tab to see the effects of the knowledge you created in the other tabs by checking and unchecking the boxes at the bottom of the window. You can adjust the layout to mimic the layout of the source (by clicking “source layout”) or to see the variables in their timeline tiers (by clicking “knowledge layout”).


## Forbidden Graph

If you use a graph as input to a knowledge box with the “Forbidden Graph” operation, the box will immediately add all edges in the parent graph as forbidden edges. It will otherwise work like a Tiers and Edges box.


## Required Graph

If you use a graph as input to a knowledge box with the “Required Graph” operation, the box will immediately add all edges in the parent graph as required edges. It will otherwise work like a Tiers and Edges box.


## Measurement Model

This option allows you to build clusters for a measurement model. When first opened, the window looks like this:

![](/_static/images/knowledge_box_4.png)

You can change the number of clusters using the text box in the upper right hand corner. To place a variable in a cluster, click and drag the box with its name into the cluster pane. To move multiple variables at once, shift- or command-click on the variables, and (without releasing the shift/command button or the mouse after the final click) drag. In the search boxes, these variables will be assumed to be children of a common latent cause.
