///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.data.KnowledgeBoxInput;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.IndTestDSep;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.session.SessionModel;
import edu.cmu.tetrad.algcomparison.utils.Parameters;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializableUtils;
import edu.cmu.tetradapp.util.IonInput;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Holds a tetrad-style graph with all of the constructors necessary for it to
 * serve as a model for the tetrad application.
 *
 * @author Joseph Ramsey
 */
public class GraphSelectionWrapper implements SessionModel, GraphSource, KnowledgeBoxInput, IonInput, IndTestProducer {
    static final long serialVersionUID = 23L;
    private final Parameters params;

    public enum Type {
        adjacents, adjacentsOfAdjacents, adjacentsOfAdjacentsOfAdjacents, markovBlankets, treks, trekEdges,
        paths, pathEdges, directedPaths, directedPathEdges, indegree, outdegree, yStructures, degree,
        subgraph, pagYStructures
    }

    public enum nType {equals, atMost, atLeast}

    //=============================CONSTRUCTORS==========================//

    public GraphSelectionWrapper(Graph graph, Parameters params) {
        if (graph == null) {
            throw new NullPointerException("Graph must not be null.");
        }

        this.params = params;

        if ((Graph) params.get("graph", null) != null) {
            graph = GraphUtils.replaceNodes(graph, ((Graph) params.get("graph", null)).getNodes());
        }

        params.set("graph", graph);
        List<Node> highlighted = (List<Node>) params.get("highlightInEditor", new ArrayList<>());
        highlighted.retainAll(graph.getNodes());
        params.set("highlightInEditor", highlighted);
        List<Node> selected = (List<Node>) params.get("selectedVariables", new ArrayList<Node>());
        selected.retainAll(graph.getNodes());
        params.set("selectedVariables", selected);
        params.set("selectionGraph", new EdgeListGraph());
        calculateSelection();
        GraphUtils.fruchtermanReingoldLayout((Graph) params.get("selectionGraph", new EdgeListGraph()));

        log();
    }

    public GraphSelectionWrapper(Graph graph, Parameters params, String message) {
        this(graph, params);
        TetradLogger.getInstance().log("info", message);
    }

    public GraphSelectionWrapper(Parameters params) {
        this(new EdgeListGraphSingleConnections(), params);
    }

    public GraphSelectionWrapper(GraphSource graph, Parameters params) {
        this(graph.getGraph(), params);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static GraphSelectionWrapper serializableInstance() {
        return new GraphSelectionWrapper(Dag.serializableInstance(), new Parameters());
    }

    //===============================================PUBLIC METHODS================================//

    // Calculates the selection graph based on parameters.
    public void calculateSelection() {
        List<Node> selectedVariables = getSelectedVariables();
        selectedVariables = GraphUtils.replaceNodes(selectedVariables, ((Graph) params.get("graph", null)).getNodes());
        Graph selectedGraph;

        if (params.getString("graphSelectionType", "subgraph").equals(Type.subgraph.toString())) {
            selectedGraph = ((Graph) params.get("graph", null)).subgraph(selectedVariables);
            params.set("highlightInEditor", selectedVariables);
        } else if (params.getString("graphSelectionType", "subgraph").equals(Type.adjacents.toString())) {
            Set<Node> adj = new HashSet<>(selectedVariables);

            for (Node node : selectedVariables) {
                adj.addAll(((Graph) params.get("graph", null)).getAdjacentNodes(node));
            }

            selectedGraph = ((Graph) params.get("graph", null)).subgraph(new ArrayList<>(adj));
            params.set("highlightInEditor", selectedVariables);
        } else if (params.getString("graphSelectionType", "subgraph").equals(Type.adjacentsOfAdjacents.toString())) {
            Set<Node> adj = new HashSet<>(selectedVariables);

            for (Node node : selectedVariables) {
                adj.addAll(((Graph) params.get("graph", null)).getAdjacentNodes(node));
            }

            for (Node node : new HashSet<>(adj)) {
                adj.addAll(((Graph) params.get("graph", null)).getAdjacentNodes(node));
            }

            selectedGraph = ((Graph) params.get("graph", null)).subgraph(new ArrayList<>(adj));
            params.set("highlightInEditor", selectedVariables);
        } else if (params.getString("graphSelectionType", "subgraph").equals(Type.adjacentsOfAdjacentsOfAdjacents)) {
            Set<Node> adj = new HashSet<>(selectedVariables);

            for (Node node : selectedVariables) {
                adj.addAll(((Graph) params.get("graph", null)).getAdjacentNodes(node));
            }

            for (Node node : new HashSet<>(adj)) {
                adj.addAll(((Graph) params.get("graph", null)).getAdjacentNodes(node));
            }

            for (Node node : new HashSet<>(adj)) {
                adj.addAll(((Graph) params.get("graph", null)).getAdjacentNodes(node));
            }

            selectedGraph = ((Graph) params.get("graph", null)).subgraph(new ArrayList<>(adj));
            params.set("highlightInEditor", selectedVariables);
        } else if (params.getString("graphSelectionType", "subgraph").equals(Type.yStructures.toString())) {
            Set<Edge> edges = new HashSet<>();

            for (Node node : selectedVariables) {
                Set<Edge> ys = yStructures((Graph) params.get("graph", null), node);
                edges.addAll(ys);
            }

            Graph subGraph = new EdgeListGraphSingleConnections();

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
            params.set("highlightInEditor", selectedVariables);
        } else if (params.getString("graphSelectionType", "subgraph").equals(Type.pagYStructures.toString())) {
            Set<Edge> edges = new HashSet<>();

            for (Node node : selectedVariables) {
                Set<Edge> ys = pagYStructures((Graph) params.get("graph", null), node);
                edges.addAll(ys);
            }

            Graph subGraph = new EdgeListGraphSingleConnections();

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
            params.set("highlightInEditor", selectedVariables);
        } else if (params.getString("graphSelectionType", "subgraph").equals(Type.markovBlankets.toString())) {
            Set<Node> _nodes = new HashSet<>();

            for (Node node : selectedVariables) {
                Set<Node> mb = mb((Graph) params.get("graph", null), node);
                mb.add(node);
                _nodes.addAll(mb);
            }

            selectedGraph = ((Graph) params.get("graph", null)).subgraph(new ArrayList<>(_nodes));
            params.set("highlightInEditor", selectedVariables);
        } else if (params.getString("graphSelectionType", "subgraph").equals(Type.treks.toString())) {
            Graph g = new EdgeListGraph(selectedVariables);

            for (int i = 0; i < selectedVariables.size(); i++) {
                for (int j = i + 1; j < selectedVariables.size(); j++) {
                    Node x = selectedVariables.get(i);
                    Node y = selectedVariables.get(j);
                    List<List<Node>> paths = GraphUtils.treks((Graph) params.get("graph", null), x, y, getN() + 1);

                    if (params.getString("nType", "atLeast").equals(nType.atMost.toString()) && !paths.isEmpty()) {
                        for (List<Node> path : paths) {
                            if (path.size() <= getN() + 1) {
                                g.addUndirectedEdge(x, y);
                                break;
                            }
                        }
                    } else if (params.getString("nType", "atLeast").equals(nType.atLeast.toString()) && !paths.isEmpty()) {
                        for (List<Node> path : paths) {
                            if (path.size() >= getN() + 1) {
                                g.addUndirectedEdge(x, y);
                                break;
                            }
                        }
                    } else if (params.getString("nType", "atLeast").equals(nType.equals.toString())) {
                        for (List<Node> path : paths) {
                            if (path.size() == getN() + 1) {
                                g.addUndirectedEdge(x, y);
                                break;
                            }
                        }
                    }
                }
            }

            selectedGraph = g;
            params.set("highlightInEditor", selectedVariables);
        } else if (params.getString("graphSelectionType", "subgraph").equals(Type.trekEdges.toString())) {
            Set<Edge> edges = new HashSet<>();

            for (int i = 0; i < selectedVariables.size(); i++) {
                for (int j = i + 1; j < selectedVariables.size(); j++) {
                    Node x = selectedVariables.get(i);
                    Node y = selectedVariables.get(j);

                    if (params.getString("nType", "atLeast").equals(nType.atMost.toString())) {
                        List<List<Node>> paths = GraphUtils.treks((Graph) params.get("graph", null), x, y, getN() + 1);
                        for (List<Node> path : paths) {
                            if (path.size() <= getN() + 1) {
                                edges.addAll(getEdgesFromPath(path, (Graph) params.get("graph", null)));
                            }
                        }
                    } else if (params.getString("nType", "atLeast").equals(nType.atLeast.toString())) {
                        List<List<Node>> paths = GraphUtils.treks((Graph) params.get("graph", null), x, y, -1);
                        for (List<Node> path : paths) {
                            if (path.size() >= getN() + 1) {
                                edges.addAll(getEdgesFromPath(path, (Graph) params.get("graph", null)));
                            }
                        }
                    } else if (params.getString("nType", "atLeast").equals(nType.equals.toString())) {
                        List<List<Node>> paths = GraphUtils.treks((Graph) params.get("graph", null), x, y, getN() + 1);
                        for (List<Node> path : paths) {
                            if (path.size() == getN() + 1) {
                                edges.addAll(getEdgesFromPath(path, (Graph) params.get("graph", null)));
                            }
                        }
                    }
                }
            }

            selectedGraph = graphFromEdges(edges, new ArrayList<Node>());
            params.set("highlightInEditor", selectedVariables);
        } else if (params.getString("graphSelectionType", "subgraph").equals(Type.paths.toString())) {
            Graph g = new EdgeListGraph(selectedVariables);

            for (int i = 0; i < selectedVariables.size(); i++) {
                for (int j = i + 1; j < selectedVariables.size(); j++) {
                    Node x = selectedVariables.get(i);
                    Node y = selectedVariables.get(j);
                    List<List<Node>> paths = GraphUtils.allPathsFromTo((Graph) params.get("graph", null), x, y, getN());

                    if (params.getString("nType", "atLeast").equals(nType.atMost.toString()) && !paths.isEmpty()) {
                        for (List<Node> path : paths) {
                            if (path.size() <= getN() + 1) {
                                g.addUndirectedEdge(x, y);
                                break;
                            }
                        }
                    } else if (params.getString("nType", "atLeast").equals(nType.atLeast.toString()) && !paths.isEmpty()) {
                        for (List<Node> path : paths) {
                            if (path.size() >= getN() + 1) {
                                g.addUndirectedEdge(x, y);
                                break;
                            }
                        }
                    } else if (params.getString("nType", "atLeast").equals(nType.equals.toString())) {
                        for (List<Node> path : paths) {
                            if (path.size() == getN() + 1) {
                                g.addUndirectedEdge(x, y);
                                break;
                            }
                        }
                    }
                }
            }

            selectedGraph = g;
            params.set("highlightInEditor", selectedVariables);
        } else if (params.getString("graphSelectionType", "subgraph").equals(Type.pathEdges.toString())) {
            Set<Edge> edges = new HashSet<>();

            for (int i = 0; i < selectedVariables.size(); i++) {
                for (int j = i + 1; j < selectedVariables.size(); j++) {
                    Node x = selectedVariables.get(i);
                    Node y = selectedVariables.get(j);

                    if (params.getString("nType", "atLeast").equals(nType.atMost)) {
                        List<List<Node>> paths = GraphUtils.allPathsFromTo((Graph) params.get("graph", null), x, y, getN());
                        for (List<Node> path : paths) {
                            if (path.size() <= getN() + 1) {
                                edges.addAll(getEdgesFromPath(path, (Graph) params.get("graph", null)));
                            }
                        }
                    } else if (params.getString("nType", "atLeast").equals(nType.atLeast)) {
                        List<List<Node>> paths = GraphUtils.allPathsFromTo((Graph) params.get("graph", null), x, y, -1);
                        for (List<Node> path : paths) {
                            if (path.size() >= getN() + 1) {
                                edges.addAll(getEdgesFromPath(path, (Graph) params.get("graph", null)));
                            }
                        }
                    } else if (params.getString("nType", "atLeast").equals(nType.equals.toString())) {
                        List<List<Node>> paths = GraphUtils.allPathsFromTo((Graph) params.get("graph", null), x, y, getN());
                        for (List<Node> path : paths) {
                            if (path.size() == getN() + 1) {
                                edges.addAll(getEdgesFromPath(path, (Graph) params.get("graph", null)));
                            }
                        }
                    }
                }
            }

            selectedGraph = graphFromEdges(edges, new ArrayList<Node>());
            params.set("highlightInEditor", selectedVariables);
        } else if (params.getString("graphSelectionType", "subgraph").equals(Type.directedPaths)) {
            Graph g = new EdgeListGraph(selectedVariables);

            for (int i = 0; i < selectedVariables.size(); i++) {
                for (int j = 0; j < selectedVariables.size(); j++) {
                    if (i == j) continue;

                    Node x = selectedVariables.get(i);
                    Node y = selectedVariables.get(j);

                    if (params.getString("nType", "atLeast").equals(nType.atMost.toString())) {
                        List<List<Node>> paths = GraphUtils.allDirectedPathsFromTo((Graph) params.get("graph", null), x, y, getN());
                        for (List<Node> path : paths) {
                            if (path.size() <= getN() + 1) {
                                g.addDirectedEdge(x, y);
                                break;
                            }
                        }
                    } else if (params.getString("nType", "atLeast").equals(nType.atLeast.toString())) {
                        List<List<Node>> paths = GraphUtils.allDirectedPathsFromTo((Graph) params.get("graph", null), x, y, -1);
                        for (List<Node> path : paths) {
                            if (path.size() >= getN() + 1) {
                                g.addDirectedEdge(x, y);
                                break;
                            }
                        }
                    } else if (params.getString("nType", "atLeast").equals(nType.equals.toString())) {
                        List<List<Node>> paths = GraphUtils.allDirectedPathsFromTo((Graph) params.get("graph", null), x, y, getN());
                        for (List<Node> path : paths) {
                            if (path.size() == getN() + 1) {
                                g.addDirectedEdge(x, y);
                                break;
                            }
                        }
                    }
                }
            }

            selectedGraph = g;
            params.set("highlightInEditor", selectedVariables);
        } else if (params.getString("graphSelectionType", "subgraph").equals(Type.directedPathEdges)) {
            Set<Edge> edges = new HashSet<>();

            for (int i = 0; i < selectedVariables.size(); i++) {
                for (int j = 0; j < selectedVariables.size(); j++) {
                    if (i == j) continue;

                    Node x = selectedVariables.get(i);
                    Node y = selectedVariables.get(j);
                    List<List<Node>> paths = GraphUtils.allDirectedPathsFromTo((Graph) params.get("graph", null), x, y, getN());

                    if (params.getString("nType", "atLeast").equals(nType.atMost.toString()) && !paths.isEmpty()) {
                        for (List<Node> path : paths) {
                            if (path.size() <= getN() + 1) {
                                edges.addAll(getEdgesFromPath(path, (Graph) params.get("graph", null)));
                            }
                        }
                    } else if (params.getString("nType", "atLeast").equals(nType.atLeast.toString()) && !paths.isEmpty()) {
                        for (List<Node> path : paths) {
                            if (path.size() >= getN() + 1) {
                                edges.addAll(getEdgesFromPath(path, (Graph) params.get("graph", null)));
                            }
                        }
                    } else if (params.getString("nType", "atLeast").equals(nType.equals.toString())) {
                        for (List<Node> path : paths) {
                            if (path.size() == getN() + 1) {
                                edges.addAll(getEdgesFromPath(path, (Graph) params.get("graph", null)));
                            }
                        }
                    }
                }
            }

            selectedGraph = graphFromEdges(edges, new ArrayList<Node>());
            params.set("highlightInEditor", selectedVariables);
        } else if (params.getString("graphSelectionType", "subgraph").equals(Type.indegree)) {
            Set<Edge> g = new HashSet<>();
            List<Node> nodes = new ArrayList<>();

            for (Node n : selectedVariables) {
                List<Node> h = ((Graph) params.get("graph", null)).getParents(n);

                if (params.getString("nType", "atLeast").equals(nType.atMost.toString()) && h.size() <= getN()) {
                    nodes.add(n);
                    for (Node m : h) {
                        g.add(((Graph) params.get("graph", null)).getEdge(m, n));
                    }
                } else if (params.getString("nType", "atLeast").equals(nType.atLeast.toString()) && h.size() >= getN()) {
                    nodes.add(n);
                    for (Node m : h) {
                        g.add(((Graph) params.get("graph", null)).getEdge(m, n));
                    }
                } else if (params.getString("nType", "atLeast").equals(nType.equals.toString()) && h.size() == getN()) {
                    nodes.add(n);
                    for (Node m : h) {
                        g.add(((Graph) params.get("graph", null)).getEdge(m, n));
                    }
                }
            }

            selectedGraph = graphFromEdges(g, new ArrayList<Node>());
            params.set("highlightInEditor", nodes);
        } else if (params.getString("graphSelectionType", "subgraph").equals(Type.outdegree.toString())) {
            Set<Edge> g = new HashSet<>();
            List<Node> nodes = new ArrayList<>();

            for (Node n : selectedVariables) {
                List<Node> h = ((Graph) params.get("graph", null)).getChildren(n);

                if (params.getString("nType", "atLeast").equals(nType.atMost.toString()) && h.size() <= getN()) {
                    nodes.add(n);
                    for (Node m : h) {
                        g.add(((Graph) params.get("graph", null)).getEdge(m, n));
                    }
                } else if (params.getString("nType", "atLeast").equals(nType.atLeast.toString()) && h.size() >= getN()) {
                    nodes.add(n);
                    for (Node m : h) {
                        g.add(((Graph) params.get("graph", null)).getEdge(m, n));
                    }
                } else if (params.getString("nType", "atLeast").equals(nType.equals.toString()) && h.size() == getN()) {
                    nodes.add(n);
                    for (Node m : h) {
                        g.add(((Graph) params.get("graph", null)).getEdge(m, n));
                    }
                }
            }

            selectedGraph = graphFromEdges(g, nodes);
            params.set("highlightInEditor", nodes);
        } else if (params.getString("graphSelectionType", "subgraph").equals(Type.degree.toString())) {
            Set<Edge> g = new HashSet<>();
            List<Node> nodes = new ArrayList<>();

            for (Node n : selectedVariables) {
                List<Node> h = ((Graph) params.get("graph", null)).getAdjacentNodes(n);

                if (params.getString("nType", "atLeast").equals(nType.atMost.toString()) && h.size() <= getN()) {
                    nodes.add(n);
                    for (Node m : h) {
                        g.add(((Graph) params.get("graph", null)).getEdge(m, n));
                    }
                } else if (params.getString("nType", "atLeast").equals(nType.atLeast.toString()) && h.size() >= getN()) {
                    nodes.add(n);
                    for (Node m : h) {
                        g.add(((Graph) params.get("graph", null)).getEdge(m, n));
                    }
                } else if (params.getString("nType", "atLeast").equals(nType.equals.toString()) && h.size() == getN()) {
                    nodes.add(n);
                    for (Node m : h) {
                        g.add(((Graph) params.get("graph", null)).getEdge(m, n));
                    }
                }
            }

            selectedGraph = graphFromEdges(g, nodes);
            params.set("highlightInEditor", nodes);
        } else {
            throw new IllegalArgumentException("Unrecognized selection type: " + params.getString("graphSelectionType", "subgraph"));
        }

        params.set("selectionGraph", selectedGraph);
    }

    // Sorry, this has to return the selection graph since its used downstream in the interface.
    public Graph getGraph() {
        return (Graph) params.get("selectionGraph", new EdgeListGraph());
    }

    public void setGraph(Graph graph) {
        params.set("graph", graph);
        params.set("selectedVariables", new ArrayList<Node>());
        params.set("selectionGraph", new EdgeListGraphSingleConnections());
        log();
    }

    public Graph getSelectionGraph() {
        return (Graph) params.get("selectionGraph", new EdgeListGraph());
    }

    public Graph getOriginalGraph() {
        return (Graph) params.get("graph", null);
    }

    public void setDialogText(String dialogText) {
        params.set("dialogText", dialogText);
    }

    public String getDialogText() {
        return params.getString("dialogText", "");
    }

    public Type getType() {
        String graphSelectionType = params.getString("graphSelectionType", "subgraph");

        for (Type type : Type.values()) {
            if (type.toString().equals(graphSelectionType)) {
                return type;
            }
        }

        throw new IllegalArgumentException();
    }

    public void setType(Type type) {
        params.set("graphSelectionType", type.toString());
    }

    public String getName() {
        return params.getString("name", null);
    }

    public void setName(String name) {
        params.set("name", name);
    }

    public Graph getSourceGraph() {
        return (Graph) params.get("graph", null);
    }

    public Graph getResultGraph() {
        return (Graph) params.get("selectionGraph", new EdgeListGraph());
    }

    public List<String> getVariableNames() {
        return ((Graph) params.get("graph", null)).getNodeNames();
    }

    public void setSelectedVariables(List<Node> variables) {
        params.set("selectedVariables", variables);
    }

    public List<Node> getSelectedVariables() {
        return (List<Node>) params.get("selectedVariables", new ArrayList<Node>());
    }

    public List<Node> getVariables() {
        return ((Graph) params.get("graph", null)).getNodes();
    }

    public void setN(int n) {
        if (n < 0) throw new IllegalArgumentException();
        params.set("n", n);
    }

    public int getN() {
        return params.getInt("n", 0);
    }

    public void setNType(nType NType) {
        params.set("nType", NType.toString());
    }

    public String getNType() {
        return params.getString("nType", "atLeast");
    }

    public List<Node> getHighlightInEditor() {
        return (List<Node>) params.get("highlightInEditor", new ArrayList<Node>());
    }


    //===========================================PRIVATE METHODS====================================//

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

    private Set<Edge> yStructures(Graph graph, Node z) {
        Set<Edge> edges = new HashSet<>();

        List<Edge> parents = new ArrayList<>();

        for (Node node : graph.getAdjacentNodes(z)) {
            Edge edge = graph.getEdge(node, z);
            if (Edges.isDirectedEdge(edge) && edge.pointsTowards(z)) {
                parents.add(edge);
            }
        }

        List<Node> children = ((Graph) params.get("graph", null)).getChildren(z);

        if (parents.size() > 1 && children.size() > 0) {
            edges.addAll(parents);

            for (Node node : children) {
                edges.add(((Graph) params.get("graph", null)).getEdge(node, z));
            }
        }

        return edges;
    }

    private Set<Edge> pagYStructures(Graph graph, Node z) {
        Set<Edge> edges = new HashSet<>();

        List<Edge> parents = new ArrayList<>();

        for (Node node : graph.getAdjacentNodes(z)) {
            Edge edge = graph.getEdge(node, z);
            if (Edges.isPartiallyOrientedEdge(edge) && edge.pointsTowards(z)) {
                parents.add(edge);
            }
        }

        List<Node> children = ((Graph) params.get("graph", null)).getChildren(z);

        if (parents.size() > 1 && children.size() > 0) {
            edges.addAll(parents);

            for (Node node : children) {
                edges.add(((Graph) params.get("graph", null)).getEdge(node, z));
            }
        }

        return edges;
    }

    private void log() {
        TetradLogger.getInstance().log("info", "General Graph");
        TetradLogger.getInstance().log("graph", "" + getSelectionGraph());
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
     * Adds semantic checks to the default deserialization method. This method
     * must have the standard signature for a readObject method, and the body of
     * the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from
     * version to version. A readObject method of this form may be added to any
     * class, even if Tetrad sessions were previously saved out using a version
     * of the class that didn't include it. (That's what the
     * "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for help.
     *
     * @throws IOException
     * @throws ClassNotFoundException
     */
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();
    }

    @Override
    public IndependenceTest getIndependenceTest() {
        return new IndTestDSep(getGraph());
    }
}





