(graph-box)=

# Graph Box

The graph box can be used to create a new graph, or to copy or edit a graph from another box.


## Possible Parent Boxes of the Graph Box

- Another graph box
- A parametric model box
- An instantiated model box
- An estimator box
- A data box
- A simulation box
- A search box
- An updater box
- A regression box


## Possible Child Boxes of the Graph Box

- Another graph box
- A compare box
- A parametric model box
- A data box
- A simulation box
- A search box
- A knowledge box


## Creating a New Graph

When you first open a graph box with no parent, you will be presented with several options for which kind of graph you would like to create: a general graph, a directed acyclic graph (DAG), a structural equation model (SEM)graph, or a time lag graph. Once you have selected the type of graph you want to create, an empty graph box will open.

You can add variables to your graph by clicking on the variable button on the left, then clicking inside the graph area. Add edges by clicking on an edge type, then clicking and dragging from one variable to another. Variables may be measured (represented by rectangular icons) or latent (represented by elliptical icons). Edges may be directed, undirected, bidirected, or uncertain (represented by circles at the ends of an edge). Depending on the type of graph you choose to create, your choice of edges may be limited.

DAGs allow only directed edges. If an edge would create a cycle, it will not be accepted. A graph box containing a DAG can be used as input for any parametric model box, and is the only kind of graph box that can be used as input for a Bayes parametric model.

SEM graphs allow only directed and bidirected edges. A graph box containing a SEM graph can be used as input to a SEM parametric model or generalized SEM parametric model, where a bidirected edge between two variables X and Y will be interpreted as X and Y having correlated error terms.

Time lag graphs allow only directed edges. New variables that you add will be initialized with a single lag. (The number of lags in the graph may be changed under “Edit—Configuration…”) Edges from later lags to earlier lags will not be accepted. Edges added within one lag will automatically be replicated in later lags.

The general graph option allows all edge types and configurations.


## Creating a Random Graph

Instead of manually creating a new graph, you can randomly create one. To do so, open up a new empty graph box and click on “Graph—Random Graph.” This will open up a dialog box from which you can choose the type of random graph you would like to create by clicking through the tabs at the top of the window. Tetrad will randomly generate a DAG, a multiple indicator model (MIM) graph, or a scale-free graph. Each type of graph is associated with a number of parameters (including but not limited to the number of nodes and the maximum degree) which you can set.

Once a graph has been randomly generated, you can directly edit it within the same graph box by adding or removing any variables or edges that that type of graph box allows. So, for instance, although you cannot randomly generate a graph with bidirected edges, you can manually add bidirected edges to a randomly generated DAG in a SEM graph box.

Random graph generation is not available for time lag graphs.


## Loading a Saved Graph

If you have previously saved a graph from Tetrad, you can load it into a new graph box by clicking “File—Load…,” and then clicking on the file type of the saved graph. Tetrad can load graphs from XML, from text, and from JSON files.

To save a graph to file, click “File—Save…,” then click on the file type you would like to save your graph as. Tetrad can save graphs to XML, text, JSON, R and dot files. (If you save your graph to R or dot, you will not be able to load that file back into Tetrad.)

You can also save an image of your graph by clicking “File—Save Graph Image…” Tetrad cannot load graphs from saved image files.


## Copying a Graph

There are two ways to copy a graph.

To copy a graph from any box which contains one, first, create a new graph box in the workspace, and draw an arrow from the box whose graph you want to copy to the new graph box. When opened, the new graph box will automatically contain a direct copy of the graph its parent box contains.


## Manipulating a Graph

If you create a graph box as a child of another box, you can also choose to perform a graph manipulation on the parent graph. Your graph box will then contain the manipulated version of the parent graph.

The available graph manipulations are:


### Display Subgraphs

This option allows you to isolate a subgraph from the parent graph. Add variables to the subgraph by highlighting the variable name in the “Unselected” pane and clicking on the right arrow. The highlighted variable will then show up in the “Selected” pane. (You may also define which variables go in the “Selected” pane by clicking on the “Text Input…” button and typing the variable names directly into the window.) Choose the type of subgraph you want to display from the drop-down panel below. Then click “Graph It!” and the resulting subgraph of the selected variables will appear in the pane on the right. (Some types of subgraph, such as “Markov Blanket,” will include unselected variables if they are part of the subgraph as defined on the selected variables. So, for instance, an unselected variable that is in the Markov blanket of a selected variable will appear in the Markov Blanket subgraph. Edges between unselected variables will not be shown.) For large or very dense graphs, it may take a long time to isolate and display subgraphs.

The types of subgraphs that can be displayed are:

- Subgraph (displays the selected nodes and all edges between them)
- Adjacents (displays the selected nodes and all edges between them, as well as nodes adjacent to the selected nodes)
- Adjacents of adjacents (displays the selected nodes and all edges between them, as well as nodes adjacent to the selected nodes and nodes adjacent to adjacencies of the selected nodes)
- Adjacents of adjacents of adjacents (displays the selected nodes and all edges between them, as well as nodes adjacent to the selected nodes, nodes adjacent to adjacencies of the selected nodes, and nodes adjacent to adjacencies of adjacencies of the selected nodes)
- Markov Blankets (displays the selected nodes and all edges between them, as well as the Markov blankets of each selected node)
- Treks (displays the selected nodes, with an edge between each pair if and only if a trek exists between them in the full graph)
- Trek Edges (displays the selected nodes, and any treks between them, including nodes not in the selected set if they are part of a trek)
- Paths (displays the selected nodes, with an edge between each pair if and only if a path exists between them in the full graph)
- Path Edges (displays the selected nodes, and any paths between them, including nodes not in the selected set if they are part of a path)
- Directed Paths (displays the selected nodes, with a directed edge between each pair if and only if a directed path exists between them in the full graph)
- Directed Path Edges (displays the selected nodes, and any directed paths between them, including nodes not in the selected set if they are part of a path)
- Y Structures (displays any Y structures involving at least two of the selected nodes)
- Pag_Y Structures (displays any Y PAGs involving at least two of the selected nodes)
- Indegree (displays the selected nodes and their parents)
- Outdegree (displays the selected nodes and their children)
- Degree (displays the selected nodes and their parents and children)


### Choose Random DAG in CPDAG

If given a CPDAG as input, this chooses a random DAG from the Markov equivalence class of the CPDAG to display. The resulting DAG functions as a normal graph box.


### Choose Zhang MAG in PAG

If given a partial ancestral graph (PAG) as input, this chooses a mixed ancestral graph (MAG) from the equivalence class of the PAG to display using Zhang's method. The resulting MAG functions as a normal graph box.


### Show DAGs in CPDAG

If given a CPDAG as input, this displays all DAGs in the CPDAG’s Markov equivalence class. Each DAG is displayed in its own tab. Most graph box functionality is not available in this type of graph box, but the DAG currently on display can be copied by clicking “Copy Selected Graph.”


### Generate CPDAG from DAG

If given a DAG as input, this displays the CPDAG of the Markov equivalence class to which the parent graph belongs. The resulting CPDAG functions as a normal graph box.


### Generate PAG from DAG

Converts an input graph from partial ancestral to directed acyclic format. The resulting DAG functions as a normal graph box.


### Generate PAG from tsDAG

Converts an input graph from partial ancestral to time series DAG format. The resulting DAG functions as a normal graph box.


### Make Bidirected Edges Undirected

Replaces all bidirected edges in the input graph with undirected edges.


### Make Undirected Edges Bidirected

Replaces all undirected edges in the input graph with bidirected edges.


### Make All Edges Undirected

Replaces all edges in the input graph with undirected edges.


### Generate Complete Graph

Creates a completely connected, undirected graph from the variables in the input graph.


### Extract Structure Model

Isolates the subgraph of the input graph involving all and only latent variables.


### Discrete-to-Indicator Expansion

In Data ▸ Transform, the new Indicator button converts a discrete column into k−1 {0,1} dummies preserving rank.


### Bootstrap “Sample w/out Replacement”

The bootstrap dialog now offers Without replacement. Useful for exact subsampling when the sample size is small.


## Other Graph Box Functions


### Edges and Edge Type Frequencies

At the bottom of the graph box, the Edges and Edge Type Frequencies section provides an accounting of every edge in the graph, and how certain Tetrad is of its type. The first three columns contain a list, in text form, of all the edges in the graph. The columns to the right are all blank in manually constructed graphs, user-loaded graphs, and graphs output by searches with default settings. They are only filled in for graphs that are output by searches performed with bootstrapping. In those cases, the fourth column will contain the percentage of bootstrap outputs in which the edge type between these two variables matches the edge type in the final graph. All the columns to the right contain the percentages of the bootstrap outputs that output each possible edge type.

For more information on bootstrap searches, see the Search Box section of the manual.


### Layout

You can change the layout of your graph by clicking on the “Layout” tab and choosing between several common layouts. You can also rearrange the layout of one graph box to match the layout of another graph box (so long as the two graphs have identical variables) by clicking “Layout—Copy Layout” and “Layout—Paste Layout.” You do not need to a highlight the graph in order to copy the layout.


### Graph Properties

Clicking on “Graph—Graph Properties” will give you a text box containing the following properties of your graph:

- Number of nodes
- Number of latent nodes
- Number of adjacencies
- Number of directed edges (not in 2-cycles)
- Number of bidirected edges
- Number of undirected edges
- Max degree
- Max indegree
- Max outdegree
- Average degree
- Density
- Number of latents
- Cyclic/Acyclic


### Paths

Clicking on “Graph—Paths” opens a dialog box that allows you to see all the paths between any two variables. You can specify whether you want to see only adjacencies, only directed paths, only potentially directed paths, or all treks between the two variables of interest, and the maximum length of the paths you are interested in using drop boxes at the top of the pane. To apply those settings, click “update.”


### Correlation

You can automatically correlate or uncorrelated exogenous variables under the Graph tab.


### Highlighting

You can highlight bidirected edges, undirected edges, and latent nodes under the Graph tab.
