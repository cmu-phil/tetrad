///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataModelList;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.test.MsepTest;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializableUtils;
import edu.cmu.tetradapp.util.IonInput;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.util.*;

/**
 * Holds a tetrad-style graph with all of the constructors necessary for it to serve as a model for the tetrad
 * application.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class GraphSelectionWrapper implements GraphSource, KnowledgeBoxInput, IonInput, IndTestProducer {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The graph.
     */
    private final Parameters params;

    /**
     * The selected nodes.
     */
    private List<Node> selectedNodes;

    /**
     * The graphs.
     */
    private List<Graph> graphs = new ArrayList<>();

    /**
     * <p>Constructor for GraphSelectionWrapper.</p>
     *
     * @param graphWrapper a {@link edu.cmu.tetradapp.model.GraphSource} object
     * @param parameters   a {@link edu.cmu.tetrad.util.Parameters} object
     */
    /**
     * The data the graph is about, if any could be found among the parents; null otherwise. Used only for
     * display (the plot matrix).
     */
    private DataSet dataSet;

    /**
     * All the data sets the parent that supplied the data carries, for choosing among in the plot matrix; holds
     * just the one above if there is only one. Null in sessions saved before this field existed.
     */
    private List<DataSet> dataSets;

    /**
     * If the graph came from a search with several results, the name of the result it is; null otherwise. Used
     * only for display.
     */
    private String sourceResultName;

    public GraphSelectionWrapper(GraphSource graphWrapper, Parameters parameters) {
        this(graphWrapper.getGraph(), parameters);
        this.sourceResultName = resultNameOf(graphWrapper);

        // A graph source that carries its own data (a search, a simulation, a Markov or vertex checker)
        // supplies it.
        this.dataSet = dataOf(graphWrapper);
        this.dataSets = dataSetsOf(graphWrapper);
    }

    /**
     * Constructs the wrapper from a graph source and a separate data box, whose data is made available for
     * display.
     *
     * @param graphWrapper the source of the graph
     * @param dataWrapper  the source of the data
     * @param parameters   the parameters
     */
    public GraphSelectionWrapper(GraphSource graphWrapper, DataWrapper dataWrapper, Parameters parameters) {
        this(graphWrapper.getGraph(), parameters);
        this.sourceResultName = resultNameOf(graphWrapper);
        DataSet fromDataBox = dataOf(dataWrapper);
        this.dataSet = fromDataBox != null ? fromDataBox : dataOf(graphWrapper);
        this.dataSets = dataSetsOf(fromDataBox != null ? dataWrapper : graphWrapper);
    }

    /**
     * The first non-empty tabular data set the given session model carries, or null if it carries none.
     */
    private static DataSet dataOf(Object source) {
        try {
            // A Markov or vertex checker: the data set it is checking, which need not be its first.
            DataModel checked = source instanceof VertexCheckIndTestModel vertexCheck ? vertexCheck.getDataModel()
                    : source instanceof MarkovCheckIndTestModel markovCheck ? markovCheck.getDataModel() : null;
            if (checked instanceof DataSet data && data.getNumRows() > 0) return data;

            List<? extends DataModel> list = dataModelsOf(source);

            if (list == null) return null;

            // A search over several data sets with one result per data set: the data for the selected result.
            if (source instanceof GeneralAlgorithmRunner runner && runner.getGraphs() != null
                && runner.getGraphs().size() == list.size()
                && list.get(runner.getSelectedResultIndex()) instanceof DataSet data && data.getNumRows() > 0) {
                return data;
            }

            for (DataModel model : list) {
                if (model instanceof DataSet data && data.getNumRows() > 0) return data;
            }
        } catch (RuntimeException e) {
            // No data to be had from this source; the plot matrix is simply not offered.
        }

        return null;
    }

    /**
     * The data models the given session model carries, or null if it is not a kind of model that carries any.
     */
    private static List<? extends DataModel> dataModelsOf(Object source) {
        if (source instanceof DataWrapper dataWrapper) return dataWrapper.getDataModelList();
        if (source instanceof GeneralAlgorithmRunner runner) return runner.getDataModelList();
        if (source instanceof VertexCheckIndTestModel vertexCheck) return vertexCheck.getDataModels();
        if (source instanceof MarkovCheckIndTestModel markovCheck) return markovCheck.getDataModels();
        return null;
    }

    private static String resultNameOf(Object source) {
        return source instanceof GeneralAlgorithmRunner runner ? runner.getSelectedResultName() : null;
    }

    /**
     * The name of the search result this graph is, if it came from a search with several results.
     *
     * @return the name, or null
     */
    public String getSourceResultName() {
        return this.sourceResultName;
    }

    private static List<DataSet> dataSetsOf(Object source) {
        List<DataSet> dataSets = new ArrayList<>();

        try {
            List<? extends DataModel> list = dataModelsOf(source);

            if (list != null) {
                for (DataModel model : list) {
                    if (model instanceof DataSet data && data.getNumRows() > 0) dataSets.add(data);
                }
            }
        } catch (RuntimeException e) {
            // As for dataOf: no choice of data is offered.
        }

        return dataSets;
    }

    /**
     * All the data sets available to plot from, of which getDataSet() is the one to show first.
     *
     * @return the data sets; empty if there are none
     */
    public List<DataSet> getDataSets() {
        return this.dataSets == null ? new ArrayList<>() : this.dataSets;
    }

    /**
     * The data the graph is about, if any was available from the parents.
     *
     * @return the data set, or null
     */
    public DataSet getDataSet() {
        return this.dataSet;
    }

    /**
     * <p>Constructor for GraphSelectionWrapper.</p>
     *
     * @param graphs a {@link java.util.List} object
     * @param params a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public GraphSelectionWrapper(List<Graph> graphs, Parameters params) {
        if (graphs == null) {
            throw new NullPointerException("Graph must not be null.");
        }

        this.params = params;


        init(params, graphs);
    }

    //=============================CONSTRUCTORS==========================//

    /**
     * <p>Constructor for GraphSelectionWrapper.</p>
     *
     * @param graph  a {@link edu.cmu.tetrad.graph.Graph} object
     * @param params a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public GraphSelectionWrapper(Graph graph, Parameters params) {
        if (graph == null) {
            throw new NullPointerException("Graph must not be null.");
        }

        this.params = params;

        List<Graph> graphs = new ArrayList<>();
        graphs.add(graph);

        init(params, graphs);
    }

    /**
     * <p>Constructor for GraphSelectionWrapper.</p>
     *
     * @param graphs  a {@link edu.cmu.tetrad.graph.Graph} object
     * @param params  a {@link edu.cmu.tetrad.util.Parameters} object
     * @param message a {@link java.lang.String} object
     */
    public GraphSelectionWrapper(Graph graphs, Parameters params, String message) {
        this(graphs, params);
        TetradLogger.getInstance().log(message);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetradapp.model.GraphSelectionWrapper} object
     * @see TetradSerializableUtils
     */
    public static GraphSelectionWrapper serializableInstance() {
        return new GraphSelectionWrapper(Dag.serializableInstance(), new Parameters());
    }

    private void init(Parameters params, List<Graph> graphs) {
        // Own node objects: the graphs come from another box (usually a search), and the subgraph shown here is
        // laid out on its own, which must not lay out that box's graph too. Node positions live on node objects.
        List<Graph> own = new ArrayList<>();
        for (Graph graph : graphs) own.add(new EdgeListGraph(graph, true));
        setGraphs(own);

        calculateSelection();
        List<Graph> selectionGraphs = getSelectionGraphs(params);

        for (int i = 0; i < graphs.size(); i++) {
            Graph graph = selectionGraphs.get(i);
            LayoutUtil.fruchtermanReingoldLayout(graph);
        }

        // No variable is selected by default - Updated 11/19/2018 by Zhou

        log();
    }

    /**
     * <p>getSelectedVariables.</p>
     *
     * @return a {@link java.util.List} object
     */
    public List<Node> getSelectedVariables() {
        return this.selectedNodes;
    }

    //===============================================METHODS================================//

    /**
     * <p>setSelectedVariables.</p>
     *
     * @param variables a {@link java.util.List} object
     */
    public void setSelectedVariables(List<Node> variables) {
        this.selectedNodes = variables;

        // The names are kept in the parameters, which outlive this wrapper, so that the selection carries over
        // when the box is re-executed on a new graph (see setGraphs).
        List<String> names = new ArrayList<>();
        if (variables != null) for (Node node : variables) names.add(node.getName());
        this.params.set("selectedVariableNames", names);
    }

    private List<Graph> getSelectionGraphs(Parameters params) {
        return (List<Graph>) params.get("selectionGraphs",
                Collections.singletonList(new EdgeListGraph()));
    }

    /**
     * <p>calculateSelection.</p>
     */
    public void calculateSelection() {
        List<Graph> selectedGraphs = new ArrayList<>();

        for (int i = 0; i < getGraphs().size(); i++) {
            selectedGraphs.add(calculateSelectionGraph(i));
        }

        this.params.set("selectionGraphs", selectedGraphs);
    }

    /**
     * <p>Getter for the field <code>graphs</code>.</p>
     *
     * @return a {@link java.util.List} object
     */
    public List<Graph> getGraphs() {

        if (this.graphs == null || this.graphs.isEmpty()) {
            List<Graph> _graphs = Collections.singletonList(new EdgeListGraph());
            this.params.set("graphs", _graphs);
            return _graphs;
        } else {
            return this.graphs;
        }
    }

    /**
     * <p>Setter for the field <code>graphs</code>.</p>
     *
     * @param graphs a {@link java.util.List} object
     */
    public void setGraphs(List<Graph> graphs) {
        this.graphs = graphs;

        List<Graph> selectionGraphs = new ArrayList<>();

        for (int i = 0; i < graphs.size(); i++) {
            selectionGraphs.add(new EdgeListGraph());
        }

        // Start from the selection last made in this box, by name, keeping the variables the new graph has.
        List<Node> restored = new ArrayList<>();

        if (!graphs.isEmpty() && this.params.get("selectedVariableNames", new ArrayList<String>()) instanceof List<?> names) {
            for (Object name : names) {
                Node node = graphs.getFirst().getNode(String.valueOf(name));
                if (node != null && !restored.contains(node)) restored.add(node);
            }
        }

        setSelectedVariables(restored);
        this.params.set("selectionGraphs", selectionGraphs);

        List<Node> highlighted = (List<Node>) this.params.get("highlightInEditor", new ArrayList<>());
        highlighted.retainAll(getSelectedGraph(0).getNodes());
        this.params.set("highlightInEditor", highlighted);
        List<Node> selected = getSelectedVariables();
        selected.retainAll(getSelectedGraph(0).getNodes());
        setSelectedVariables(selected);

        log();
    }

    private Graph calculateSelectionGraph(int k) {
        List<Node> selectedVariables = getSelectedVariables();
        selectedVariables = GraphUtils.replaceNodes(selectedVariables, getSelectedGraph(k).getNodes());
        Graph selectedGraph;

        if (this.params.getString("graphSelectionType", "Subgraph").equals(Type.Subgraph.toString())) {
            selectedGraph = getSelectedGraph(k).subgraph(selectedVariables);
            this.params.set("highlightInEditor", selectedVariables);
        } else if (this.params.getString("graphSelectionType", "subgraph").equals(Type.Adjacents.toString())) {
            Set<Node> adj = new HashSet<>(selectedVariables);

            for (Node node : selectedVariables) {
                adj.addAll((getSelectedGraph(k).getAdjacentNodes(node)));
            }

            selectedGraph = (getSelectedGraph(k).subgraph(new ArrayList<>(adj)));
            this.params.set("highlightInEditor", selectedVariables);
        } else if (this.params.getString("graphSelectionType", "Subgraph").equals(Type.Adjacents_of_Adjacents.toString())) {
            Set<Node> adj = new HashSet<>(selectedVariables);

            for (Node node : selectedVariables) {
                adj.addAll((getSelectedGraph(k).getAdjacentNodes(node)));
            }

            for (Node node : new HashSet<>(adj)) {
                adj.addAll((getSelectedGraph(k).getAdjacentNodes(node)));
            }

            selectedGraph = (getSelectedGraph(k).subgraph(new ArrayList<>(adj)));
            this.params.set("highlightInEditor", selectedVariables);
        } else if (this.params.getString("graphSelectionType", "Subgraph").equals(Type.Adjacents_of_Adjacents_of_Adjacents.toString())) {
            Set<Node> adj = new HashSet<>(selectedVariables);

            for (Node node : selectedVariables) {
                adj.addAll((getSelectedGraph(k).getAdjacentNodes(node)));
            }

            for (Node node : new HashSet<>(adj)) {
                adj.addAll((getSelectedGraph(k).getAdjacentNodes(node)));
            }

            for (Node node : new HashSet<>(adj)) {
                adj.addAll((getSelectedGraph(k).getAdjacentNodes(node)));
            }

            selectedGraph = (getSelectedGraph(k).subgraph(new ArrayList<>(adj)));
            this.params.set("highlightInEditor", selectedVariables);
        } else if (this.params.getString("graphSelectionType", "subgraph").equals(Type.Adjacents.toString())) {
            Set<Node> adj = new HashSet<>(selectedVariables);

            for (Node node : selectedVariables) {
                adj.addAll((getSelectedGraph(k).getAdjacentNodes(node)));
            }

            selectedGraph = (getSelectedGraph(k).subgraph(new ArrayList<>(adj)));
            this.params.set("highlightInEditor", selectedVariables);
        } else if (this.params.getString("graphSelectionType", "parents").equals(Type.Parents.toString())) {
            Set<Node> adj = new HashSet<>(selectedVariables);

            for (Node node : selectedVariables) {
                adj.addAll((getSelectedGraph(k).getParents(node)));
            }

            selectedGraph = (getSelectedGraph(k).subgraph(new ArrayList<>(adj)));
            this.params.set("highlightInEditor", selectedVariables);
        } else if (this.params.getString("graphSelectionType", "children").equals(Type.Children.toString())) {
            Set<Node> adj = new HashSet<>(selectedVariables);

            for (Node node : selectedVariables) {
                adj.addAll((getSelectedGraph(k).getChildren(node)));
            }

            selectedGraph = (getSelectedGraph(k).subgraph(new ArrayList<>(adj)));
            this.params.set("highlightInEditor", selectedVariables);
        } else if (this.params.getString("graphSelectionType", "ancestors").equals(Type.Ancestors.toString())) {
            Set<Node> adj = new HashSet<>(selectedVariables);

            for (Node node : selectedVariables) {
                adj.addAll((getSelectedGraph(k).paths().getAncestors(Collections.singletonList(node))));
            }

            selectedGraph = (getSelectedGraph(k).subgraph(new ArrayList<>(adj)));
            this.params.set("highlightInEditor", selectedVariables);
        } else if (this.params.getString("graphSelectionType", "descendants").equals(Type.Descendants.toString())) {
            Set<Node> adj = new HashSet<>(selectedVariables);

            for (Node node : selectedVariables) {
                adj.addAll((getSelectedGraph(k).paths().getDescendants(Collections.singletonList(node))));
            }

            selectedGraph = (getSelectedGraph(k).subgraph(new ArrayList<>(adj)));
            this.params.set("highlightInEditor", selectedVariables);
        } else if (this.params.getString("graphSelectionType", "Subgraph").equals(Type.Descendants.toString())) {
            Set<Edge> edges = new HashSet<>();

            for (Node node : selectedVariables) {
                Set<Edge> ys = yStructures(getGraphAtIndex(k), node, k);
                edges.addAll(ys);
            }

            Graph subGraph = new EdgeListGraph();

            for (Edge edge : edges) {
                if (!subGraph.containsNode(edge.getNode1())) {
                    subGraph.addNode(edge.getNode1());
                }

                if (!subGraph.containsNode(edge.getNode2())) {
                    subGraph.addNode(edge.getNode2());
                }

                subGraph.addEdge(edge);
            }

            selectedGraph = subGraph;
            this.params.set("highlightInEditor", selectedVariables);
        } else if (this.params.getString("graphSelectionType", "Subgraph").equals(Type.Pag_Y_Structures.toString())) {
            Set<Edge> edges = new HashSet<>();

            for (Node node : selectedVariables) {
                Set<Edge> ys = pagYStructures(getGraphAtIndex(k), node, k);
                edges.addAll(ys);
            }

            Graph subGraph = new EdgeListGraph();

            for (Edge edge : edges) {
                if (!subGraph.containsNode(edge.getNode1())) {
                    subGraph.addNode(edge.getNode1());
                }

                if (!subGraph.containsNode(edge.getNode2())) {
                    subGraph.addNode(edge.getNode2());
                }

                subGraph.addEdge(edge);
            }

            selectedGraph = subGraph;
            this.params.set("highlightInEditor", selectedVariables);
        } else if (this.params.getString("graphSelectionType", "Subgraph").equals(Type.Markov_Blankets.toString())) {
            Set<Node> _nodes = new HashSet<>();

            for (Node node : selectedVariables) {
                Set<Node> mb = mb(getGraphAtIndex(k), node);
                mb.add(node);
                _nodes.addAll(mb);
            }

            selectedGraph = (getSelectedGraph(k).subgraph(new ArrayList<>(_nodes)));
            this.params.set("highlightInEditor", selectedVariables);
        } else {
            String nTypeString = this.params.getString("nType", "atLeast");
            nTypeString = nTypeString.toLowerCase().replace(" ", "");

            String atMostString = nType.atMost.toString().toLowerCase().replace(" ", "");
            String atLeastString = nType.atLeast.toString().toLowerCase().replace(" ", "");
            String equalsString = nType.equals.toString().toLowerCase().replace(" ", "");

            if (this.params.getString("graphSelectionType", "Subgraph").equals(Type.Treks.toString())) {
                Graph g = new EdgeListGraph(selectedVariables);


                for (int i = 0; i < selectedVariables.size(); i++) {
                    for (int j = i + 1; j < selectedVariables.size(); j++) {
                        Node x = selectedVariables.get(i);
                        Node y = selectedVariables.get(j);


                        if (nTypeString.equals(atMostString)) {
                            Set<List<Node>> paths = getGraphAtIndex(k).paths().treks(x, y, getN() + 2);

                            for (List<Node> path : paths) {
                                if (path.size() <= getN() + 2) {
                                    g.addUndirectedEdge(x, y);
                                    break;
                                }
                            }
                        } else if (nTypeString.equals(atLeastString)) {
                            Set<List<Node>> paths = getGraphAtIndex(k).paths().treks(x, y, -1);

                            for (List<Node> path : paths) {
                                if (path.size() >= getN() + 2) {
                                    g.addUndirectedEdge(x, y);
                                    break;
                                }
                            }
                        } else if (nTypeString.equals(equalsString)) {
                            Set<List<Node>> paths = getGraphAtIndex(k).paths().treks(x, y, getN() + 2);

                            for (List<Node> path : paths) {
                                if (path.size() == getN() + 2) {
                                    g.addUndirectedEdge(x, y);
                                    break;
                                }
                            }
                        }
                    }
                }

                selectedGraph = g;
                this.params.set("highlightInEditor", selectedVariables);
            } else if (this.params.getString("graphSelectionType", "Subgraph").equals(Type.Trek_Edges.toString())) {
                Set<Edge> edges = new HashSet<>();

                for (int i = 0; i < selectedVariables.size(); i++) {
                    for (int j = i + 1; j < selectedVariables.size(); j++) {
                        Node x = selectedVariables.get(i);
                        Node y = selectedVariables.get(j);

                        if (nTypeString.equals(atMostString) && !getGraphAtIndex(k).paths().treks(x, y, getN() + 2).isEmpty()) {
                            Set<List<Node>> paths = getGraphAtIndex(k).paths().treks(x, y, getN() + 2);
                            for (List<Node> path : paths) {
                                if (path.size() <= getN() + 2) {
                                    edges.addAll(getEdgesFromPath(path, getGraphAtIndex(k)));
                                }
                            }
                        } else if (nTypeString.equals(atLeastString)) {
                            Set<List<Node>> paths = getGraphAtIndex(k).paths().treks(x, y, -1);
                            for (List<Node> path : paths) {
                                if (path.size() >= getN() + 2) {
                                    edges.addAll(getEdgesFromPath(path, getGraphAtIndex(k)));
                                }
                            }
                        } else if (nTypeString.equals(equalsString)) {
                            Set<List<Node>> paths = getGraphAtIndex(k).paths().treks(x, y, getN() + 2);
                            for (List<Node> path : paths) {
                                if (path.size() == getN() + 2) {
                                    edges.addAll(getEdgesFromPath(path, getGraphAtIndex(k)));
                                }
                            }
                        }
                    }
                }

                selectedGraph = graphFromEdges(edges, new ArrayList<>());
                this.params.set("highlightInEditor", selectedVariables);
            } else if (this.params.getString("graphSelectionType", "Subgraph").equals(Type.Paths.toString())) {
                Graph g = new EdgeListGraph(selectedVariables);

                for (int i = 0; i < selectedVariables.size(); i++) {
                    for (int j = i + 1; j < selectedVariables.size(); j++) {
                        Node x = selectedVariables.get(i);
                        Node y = selectedVariables.get(j);

                        if (nTypeString.equals(atMostString)) {
                            Set<List<Node>> paths = getGraphAtIndex(k).paths().allPaths(x, y, getN() + 2);

                            for (List<Node> path : paths) {
                                if (path.size() <= getN() + 2) {
                                    g.addUndirectedEdge(x, y);
                                    break;
                                }
                            }
                        } else if (nTypeString.equals(atLeastString)) {
                            Set<List<Node>> paths = getGraphAtIndex(k).paths().allPaths(x, y, -1);

                            for (List<Node> path : paths) {
                                if (path.size() >= getN() + 2) {
                                    g.addUndirectedEdge(x, y);
                                    break;
                                }
                            }
                        } else if (nTypeString.equals(equalsString)) {
                            Set<List<Node>> paths = getGraphAtIndex(k).paths().allPaths(x, y, getN() + 2);

                            for (List<Node> path : paths) {
                                if (path.size() == getN() + 2) {
                                    g.addUndirectedEdge(x, y);
                                    break;
                                }
                            }
                        }
                    }
                }

                selectedGraph = g;
                this.params.set("highlightInEditor", selectedVariables);
            } else if (this.params.getString("graphSelectionType", "Subgraph").equals(Type.Path_Edges.toString())) {
                Set<Edge> edges = new HashSet<>();

                for (int i = 0; i < selectedVariables.size(); i++) {
                    for (int j = i + 1; j < selectedVariables.size(); j++) {
                        Node x = selectedVariables.get(i);
                        Node y = selectedVariables.get(j);

                        if (nTypeString.equals(atMostString)) {
                            Set<List<Node>> paths = getGraphAtIndex(k).paths().allPaths(x, y, getN() + 2);
                            for (List<Node> path : paths) {
                                if (path.size() <= getN() + 2) {
                                    edges.addAll(getEdgesFromPath(path, getGraphAtIndex(k)));
                                }
                            }
                        } else if (nTypeString.equals(atLeastString)) {
                            Set<List<Node>> paths = getGraphAtIndex(k).paths().allPaths(x, y, -1);
                            for (List<Node> path : paths) {
                                if (path.size() >= getN() + 1) {
                                    edges.addAll(getEdgesFromPath(path, getGraphAtIndex(k)));
                                }
                            }
                        } else if (nTypeString.equals(equalsString)) {
                            Set<List<Node>> paths = getGraphAtIndex(k).paths().allPaths(x, y, getN() + 2);
                            for (List<Node> path : paths) {
                                if (path.size() == getN() + 2) {
                                    edges.addAll(getEdgesFromPath(path, getGraphAtIndex(k)));
                                }
                            }
                        }
                    }
                }

                selectedGraph = graphFromEdges(edges, new ArrayList<>());
                this.params.set("highlightInEditor", selectedVariables);
            } else if (this.params.getString("graphSelectionType", "Subgraph").equals(Type.Directed_Paths.toString())) {
                Graph g = new EdgeListGraph(selectedVariables);

                for (int i = 0; i < selectedVariables.size(); i++) {
                    for (int j = 0; j < selectedVariables.size(); j++) {
                        if (i == j) continue;

                        Node x = selectedVariables.get(i);
                        Node y = selectedVariables.get(j);

                        if (nTypeString.equals(atMostString)) {
                            List<List<Node>> paths = getGraphAtIndex(k).paths().allDirectedPaths(x, y, getN() + 2);
                            for (List<Node> path : paths) {
                                if (path.size() <= getN() + 2) {
                                    g.addDirectedEdge(x, y);
                                    break;
                                }
                            }
                        } else if (nTypeString.equals(atLeastString)) {
                            List<List<Node>> paths = getGraphAtIndex(k).paths().allDirectedPaths(x, y, -1);
                            for (List<Node> path : paths) {
                                if (path.size() >= getN() + 2) {
                                    g.addDirectedEdge(x, y);
                                    break;
                                }
                            }
                        } else if (nTypeString.equals(equalsString)) {
                            List<List<Node>> paths = getGraphAtIndex(k).paths().allDirectedPaths(x, y, getN() + 2);
                            for (List<Node> path : paths) {
                                if (path.size() == getN() + 2) {
                                    g.addDirectedEdge(x, y);
                                    break;
                                }
                            }
                        }
                    }
                }

                selectedGraph = g;
                this.params.set("highlightInEditor", selectedVariables);
            } else if (this.params.getString("graphSelectionType", "Subgraph").equals(Type.Directed_Path_Edges.toString())) {
                Set<Edge> edges = new HashSet<>();

                for (int i = 0; i < selectedVariables.size(); i++) {
                    for (int j = 0; j < selectedVariables.size(); j++) {
                        if (i == j) continue;

                        Node x = selectedVariables.get(i);
                        Node y = selectedVariables.get(j);


                        if (nTypeString.equals(atMostString)) {
                            List<List<Node>> paths = getGraphAtIndex(k).paths().allDirectedPaths(x, y, getN() + 2);

                            for (List<Node> path : paths) {
                                if (path.size() <= getN() + 2) {
                                    edges.addAll(getEdgesFromPath(path, getGraphAtIndex(k)));
                                }
                            }
                        } else if (nTypeString.equals(atLeastString)) {
                            List<List<Node>> paths = getGraphAtIndex(k).paths().allDirectedPaths(x, y, -1);

                            for (List<Node> path : paths) {
                                if (path.size() >= getN() + 2) {
                                    edges.addAll(getEdgesFromPath(path, getGraphAtIndex(k)));
                                }
                            }
                        } else if (nTypeString.equals(equalsString)) {
                            List<List<Node>> paths = getGraphAtIndex(k).paths().allDirectedPaths(x, y, getN() + 2);

                            for (List<Node> path : paths) {
                                if (path.size() == getN() + 2) {
                                    edges.addAll(getEdgesFromPath(path, getGraphAtIndex(k)));
                                }
                            }
                        }
                    }
                }

                selectedGraph = graphFromEdges(edges, new ArrayList<>());
                this.params.set("highlightInEditor", selectedVariables);
            } else if (this.params.getString("graphSelectionType", "Subgraph").equals(Type.Indegree.toString())) {
                Set<Edge> g = new HashSet<>();
                List<Node> nodes = new ArrayList<>();

                for (Node n : selectedVariables) {
                    List<Node> h = (getSelectedGraph(k).getParents(n));

                    if (nTypeString.equals(atMostString) && h.size() <= getN()) {
                        nodes.add(n);
                        for (Node m : h) {
                            g.add((getSelectedGraph(k).getEdge(m, n)));
                        }
                    } else if (nTypeString.equals(atLeastString) && h.size() >= getN()) {
                        nodes.add(n);
                        for (Node m : h) {
                            g.add((getSelectedGraph(k).getEdge(m, n)));
                        }
                    } else if (nTypeString.equals(equalsString) && h.size() == getN()) {
                        nodes.add(n);
                        for (Node m : h) {
                            g.add((getSelectedGraph(k).getEdge(m, n)));
                        }
                    }
                }

                selectedGraph = graphFromEdges(g, new ArrayList<>());
                this.params.set("highlightInEditor", nodes);
            } else if (this.params.getString("graphSelectionType", "Subgraph").equals(Type.Out_Degree.toString())) {
                Set<Edge> g = new HashSet<>();
                List<Node> nodes = new ArrayList<>();

                for (Node n : selectedVariables) {
                    List<Node> h = (getSelectedGraph(k).getChildren(n));

                    if (nTypeString.equals(atMostString) && h.size() <= getN()) {
                        nodes.add(n);
                        for (Node m : h) {
                            g.add((getSelectedGraph(k).getEdge(m, n)));
                        }
                    } else if (nTypeString.equals(atLeastString) && h.size() >= getN()) {
                        nodes.add(n);
                        for (Node m : h) {
                            g.add((getSelectedGraph(k).getEdge(m, n)));
                        }
                    } else if (nTypeString.equals(equalsString) && h.size() == getN()) {
                        nodes.add(n);
                        for (Node m : h) {
                            g.add((getSelectedGraph(k).getEdge(m, n)));
                        }
                    }
                }

                selectedGraph = graphFromEdges(g, nodes);
                this.params.set("highlightInEditor", nodes);
            } else if (this.params.getString("graphSelectionType", "Subgraph").equals(Type.Degree.toString())) {
                Set<Edge> g = new HashSet<>();
                List<Node> nodes = new ArrayList<>();

                for (Node n : selectedVariables) {
                    List<Node> h = (getSelectedGraph(k).getAdjacentNodes(n));

                    if (nTypeString.equals(atMostString) && h.size() <= getN()) {
                        nodes.add(n);
                        for (Node m : h) {
                            g.add((getSelectedGraph(k).getEdge(m, n)));
                        }
                    } else if (nTypeString.equals(atLeastString) && h.size() >= getN()) {
                        nodes.add(n);
                        for (Node m : h) {
                            g.add((getSelectedGraph(k).getEdge(m, n)));
                        }
                    } else if (nTypeString.equals(equalsString) && h.size() == getN()) {
                        nodes.add(n);
                        for (Node m : h) {
                            g.add((getSelectedGraph(k).getEdge(m, n)));
                        }
                    }
                }

                selectedGraph = graphFromEdges(g, nodes);
                this.params.set("highlightInEditor", nodes);
            } else {
                throw new IllegalArgumentException("Unrecognized selection type: " + this.params.getString("graphSelectionType", "subgraph"));
            }
        }

        return selectedGraph;
    }

    private Graph getGraphAtIndex(int k) {
        return getGraphs().get(k);
    }

    private Graph getSelectedGraph(int i) {
        List<Graph> graphs = getGraphs();

        if (graphs != null && !graphs.isEmpty()) {
            return graphs.get(i);
        } else {
            return new EdgeListGraph();
        }
    }

    // Sorry, this has to return the selection graph since its used downstream in the interface.

    /**
     * <p>getGraph.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public Graph getGraph() {
        return getSelectionGraphs(this.params).getFirst();
    }

    /**
     * <p>getSelectionGraph.</p>
     *
     * @param i a int
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public Graph getSelectionGraph(int i) {
        List<Graph> selectionGraphs = (List<Graph>) this.params.get("selectionGraphs", new ArrayList<>());

        if (selectionGraphs == null || selectionGraphs.isEmpty()) {
            for (int j = 0; j < getGraphs().size(); j++) {
                assert selectionGraphs != null;
                selectionGraphs.add(new EdgeListGraph());
            }

            this.params.set("selectionGraphs", selectionGraphs);
        }

        assert selectionGraphs != null;
        return selectionGraphs.get(i);
    }

    /**
     * <p>getOriginalGraph.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public Graph getOriginalGraph() {
        return getSelectedGraph(0);
    }

    /**
     * <p>getDialogText.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getDialogText() {
        return this.params.getString("dialogText", "");
    }

    /**
     * <p>setDialogText.</p>
     *
     * @param dialogText a {@link java.lang.String} object
     */
    public void setDialogText(String dialogText) {
        this.params.set("dialogText", dialogText);
    }

    /**
     * <p>getType.</p>
     *
     * @return a {@link edu.cmu.tetradapp.model.GraphSelectionWrapper.Type} object
     */
    public Type getType() {
        String graphSelectionType = this.params.getString("graphSelectionType", "subgraph");

        for (Type type : Type.values()) {
            if (type.toString().equals(graphSelectionType)) {
                return type;
            }
        }

        throw new IllegalArgumentException();
    }

    /**
     * <p>setType.</p>
     *
     * @param type a {@link edu.cmu.tetradapp.model.GraphSelectionWrapper.Type} object
     */
    public void setType(Type type) {
        this.params.set("graphSelectionType", type.toString());
    }

    /**
     * <p>getName.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getName() {
        return this.params.getString("name", null);
    }

    /**
     * {@inheritDoc}
     */
    public void setName(String name) {
        this.params.set("name", name);
    }

    /**
     * <p>getSourceGraph.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public Graph getSourceGraph() {
        return getSelectedGraph(0);
    }

    /**
     * <p>getResultGraph.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public Graph getResultGraph() {
        return (getSelectionGraphs(this.params)).getFirst();
    }

    /**
     * <p>getVariableNames.</p>
     *
     * @return a {@link java.util.List} object
     */
    public List<String> getVariableNames() {
        return getSelectedGraph(0).getNodeNames();
    }

    /**
     * <p>getVariables.</p>
     *
     * @return a {@link java.util.List} object
     */
    public List<Node> getVariables() {
        return getSelectedGraph(0).getNodes();
    }

    /**
     * <p>getN.</p>
     *
     * @return a int
     */
    public int getN() {
        return this.params.getInt("n", 0);
    }

    /**
     * <p>setN.</p>
     *
     * @param n a int
     */
    public void setN(int n) {
        if (n < 0) throw new IllegalArgumentException();
        this.params.set("n", n);
    }

    /**
     * <p>getNType.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getNType() {
        return this.params.getString("nType", "atLeast");
    }

    /**
     * <p>setNType.</p>
     *
     * @param NType a {@link edu.cmu.tetradapp.model.GraphSelectionWrapper.nType} object
     */
    public void setNType(nType NType) {
        this.params.set("nType", NType.toString());
    }

    /**
     * <p>getHighlightInEditor.</p>
     *
     * @return a {@link java.util.List} object
     */
    public List<Node> getHighlightInEditor() {
        return (List<Node>) this.params.get("highlightInEditor", new ArrayList<Node>());
    }

    // Calculates a graph from give nodes and edges. The nodes are always included in the graph, plus
    // whatever nodes and edges are in the edges set.
    private Graph graphFromEdges(Set<Edge> edges, List<Node> nodes) {
        Graph selectedGraph = new EdgeListGraph(nodes);

        for (Edge edge : edges) {
            if (!selectedGraph.containsNode(edge.getNode1())) selectedGraph.addNode(edge.getNode1());
            if (!selectedGraph.containsNode(edge.getNode2())) selectedGraph.addNode(edge.getNode2());
            selectedGraph.addEdge(edge);
        }

        return selectedGraph;
    }

    // Calculates the Markov blanket of a node in a params.getGraph().
    private Set<Node> mb(Graph graph, Node z) {
        Set<Node> mb = new HashSet<>(graph.getAdjacentNodes(z));

        for (Node c : graph.getChildren(z)) {
            for (Node p : graph.getParents(c)) {
                if (p != z) {
                    mb.add(p);
                }
            }
        }

        return mb;
    }


    //===========================================PRIVATE METHODS====================================//

    private Set<Edge> yStructures(Graph graph, Node z, int i) {
        Set<Edge> edges = new HashSet<>();

        List<Edge> parents = new ArrayList<>();

        for (Node node : graph.getAdjacentNodes(z)) {
            Edge edge = graph.getEdge(node, z);
            if (Edges.isDirectedEdge(edge) && edge.pointsTowards(z)) {
                parents.add(edge);
            }
        }

        List<Node> children = getSelectedGraph(i).getChildren(z);

        if (parents.size() > 1 && children.size() > 0) {
            edges.addAll(parents);

            for (Node node : children) {
                edges.add(getSelectedGraph(i).getEdge(node, z));
            }
        }

        return edges;
    }

    private Set<Edge> pagYStructures(Graph graph, Node z, int i) {
        Set<Edge> edges = new HashSet<>();

        List<Edge> parents = new ArrayList<>();

        for (Node node : graph.getAdjacentNodes(z)) {
            Edge edge = graph.getEdge(node, z);
            if (Edges.isPartiallyOrientedEdge(edge) && edge.pointsTowards(z)) {
                parents.add(edge);
            }
        }

        List<Node> children = getSelectedGraph(i).getChildren(z);

        if (parents.size() > 1 && children.size() > 0) {
            edges.addAll(parents);

            for (Node node : children) {
                edges.add(getSelectedGraph(i).getEdge(node, z));
            }
        }

        return edges;
    }

    private void log() {
        TetradLogger.getInstance().log("General Graph");
    }

    private Set<Edge> getEdgesFromPath(List<Node> path, Graph graph) {
        Set<Edge> edges = new HashSet<>();

        for (int m = 1; m < path.size(); m++) {
            Node n0 = path.get(m - 1);
            Node n1 = path.get(m);
            Edge edge = graph.getEdge(n0, n1);
            if (edge != null) {
                edges.add(edge);
            }
        }

        return edges;
    }

    /**
     * Writes the object to the specified ObjectOutputStream.
     *
     * @param out The ObjectOutputStream to write the object to.
     * @throws IOException If an I/O error occurs.
     */
    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        try {
            out.defaultWriteObject();
        } catch (IOException e) {
            TetradLogger.getInstance().log("Failed to serialize object: " + getClass().getCanonicalName()
                                           + ", " + e.getMessage());
            throw e;
        }
    }

    /**
     * Reads the object from the specified ObjectInputStream. This method is used during deserialization to restore the
     * state of the object.
     *
     * @param in The ObjectInputStream to read the object from.
     * @throws IOException            If an I/O error occurs.
     * @throws ClassNotFoundException If the class of the serialized object cannot be found.
     */
    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
        } catch (IOException e) {
            TetradLogger.getInstance().log("Failed to deserialize object: " + getClass().getCanonicalName()
                                           + ", " + e.getMessage());
            throw e;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IndependenceTest getIndependenceTest() {
        return new MsepTest(getGraph());
    }

    /**
     * An enum of which type of graph selection to perform.
     */
    public enum Type {

        /**
         * Subgraph.
         */
        Subgraph,

        /**
         * Adjacents.
         */
        Adjacents,

        /**
         * Adjacents of Adjacents.
         */
        Adjacents_of_Adjacents,

        /**
         * Adjacents of Adjacents of Adjacents.
         */
        Adjacents_of_Adjacents_of_Adjacents,

        /**
         * Parents.
         */
        Parents,

        /**
         * Children.
         */
        Children,

        /**
         * Ancestors.
         */
        Ancestors,

        /**
         * Descendants.
         */
        Descendants,

        /**
         * Markov Blankets.
         */
        Markov_Blankets,

        /**
         * Treks.
         */
        Treks,

        /**
         * Trek Edges.
         */
        Trek_Edges,

        /**
         * Paths.
         */
        Paths,

        /**
         * Path Edges.
         */
        Path_Edges,

        /**
         * Directed Paths.
         */
        Directed_Paths,

        /**
         * Directed Path Edges.
         */
        Directed_Path_Edges,

        /**
         * Y Structures.
         */
        Y_Structures,

        /**
         * Pag Y Structures.
         */
        Pag_Y_Structures,

        /**
         * Indegree.
         */
        Indegree,

        /**
         * Out Degree.
         */
        Out_Degree,

        /**
         * Degree.
         */
        Degree
    }

    /**
     * An enum of which type of n to use.
     */
    public enum nType {

        /**
         * equals.
         */
        equals,

        /**
         * atMost.
         */
        atMost,

        /**
         * atLeast.
         */
        atLeast
    }
}






