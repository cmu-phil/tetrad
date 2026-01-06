(introduction)=

# Introduction

Tetrad is a suite of software for the discovery, estimation, and simulation of causal models. Some of the functions that you can perform with Tetrad include, but are not limited to:

- Loading an existing data set, restricting potential models using your a-priori causal knowledge, and searching for a model that explains it using one of Tetrad’s causal search algorithms
- Loading an existing causal graph and existing data set, and estimating a parameterized model from them
- Creating a new causal graph, parameterizing a model from it, and simulating data from that model

Tetrad allows for numerous types of data, graph, and model to be input and output, and some functions may be restricted based on what types of data or graph the user inputs. Other functions may simply not perform as well on certain types of data.

All analysis in Tetrad is performed graphically using a box paradigm, found in a sidebar to the left of the workspace. A box either houses an object such as a graph or a dataset, or performs an operation such as a search or an estimation. Some boxes require input from other boxes in order to work. Complex operations are performed by stringing chains of boxes together in the workspace. For instance, to simulate data, you would input a graph box into a parametric model box, the PM box into an instantiated model box, and finally the IM box into a simulation box.

In order to use a box, click on it in the sidebar, then click inside the workspace. This creates an empty box, which you can be instantiated by double-clicking. Most boxes have multiple options available on instantiation, which will be explained in further detail in this manual.

In order to use one box as input to another, draw an arrow between them by clicking on the arrow tool in the sidebar, and clicking and dragging from the first box to the second in the workspace.

Starting 1/14/2024, we will compile Tetrad under JDK 17 and use language level 17.

Tetrad may be cited using the following reference: Ramsey, J. D., Zhang, K., Glymour, M., Romero, R. S., Huang, B., Ebert-Uphoff, I., ... & Glymour, C. (2018). TETRAD—A toolbox for causal discovery. In 8th International Workshop on Climate Informatics.
