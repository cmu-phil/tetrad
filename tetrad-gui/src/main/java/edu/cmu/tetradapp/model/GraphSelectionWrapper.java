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

    public List<Node> getHighlightInEditor() {
        return highlightInEditor;
    }

    public enum Type {
        adjacents, adjacentsOfAdjacents, adjacentsOfAdjacentsOfAdjacents, markovBlankets, treks, trekEdges,
        paths, pathEdges, directedPaths, directedPathEdges, indegree, outdegree, degree
    }

    // For relevant selection methods, the length or degree.
    private int n = 3;

    // Whether the length or degree is equal to n or at most n.
    private nType _nType = nType.atMost;

    public enum nType {equals, atMost, atLeast}

    private String dialogText = "";

    // The name of this wrapper; used by Tetrad.
    private String name;

    // The original graph loaded in; most selections are subsets of this.
    private Graph graph;

    // The selection graph, usually a subset of graph.
    private Graph selectionGraph = new EdgeListGraph();

    // The selected variables in graph.
    private List<Node> selectedVariables = new ArrayList<>();

    // The selection type.
    private Type type = Type.adjacents;

    // The list of nodes that should be highlighted in the editor.
    private List<Node> highlightInEditor = new ArrayList<>();

    //=============================CONSTRUCTORS==========================//

    public GraphSelectionWrapper(Graph graph) {
        if (graph == null) {
            throw new NullPointerException("Graph must not be null.");
        }
        this.graph = graph;
        log();
    }

    public GraphSelectionWrapper(Graph graph, String message) {
        TetradLogger.getInstance().log("info", message);

        if (graph == null) {
            throw new NullPointerException("Graph must not be null.");
        }
        this.graph = graph;
    }

    public GraphSelectionWrapper() {
        graph = new EdgeListGraph();
        log();
    }

    public GraphSelectionWrapper(GraphSource graph) {
        this(graph.getGraph());
    }

    public boolean allowRandomGraph() {
        return true;
    }

    public void setSelectionGraph(Graph selectionGraph) {
        this.selectionGraph = selectionGraph;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see edu.cmu.TestSerialization
     * @see TetradSerializableUtils
     */
    public static GraphSelectionWrapper serializableInstance() {
        return new GraphSelectionWrapper(Dag.serializableInstance());
    }

    //===============================================PUBLIC METHODS================================//

    // Calculates the selection graph based on parameters.
    public void calculateSelection() {
        List<Node> selectedVariables = getSelectedVariables();
        selectedVariables = GraphUtils.replaceNodes(selectedVariables, graph.getNodes());
        Graph selectedGraph;

        if (type == Type.adjacents) {
            Set<Node> adj = new HashSet<>(selectedVariables);

            for (Node node : selectedVariables) {
                adj.addAll(graph.getAdjacentNodes(node));
            }

            selectedGraph = graph.subgraph(new ArrayList<>(adj));
            this.highlightInEditor = selectedVariables;
        } else if (type == Type.adjacentsOfAdjacents) {
            Set<Node> adj = new HashSet<Node>(selectedVariables);

            for (Node node : selectedVariables) {
                adj.addAll(graph.getAdjacentNodes(node));
            }

            for (Node node : new HashSet<Node>(adj)) {
                adj.addAll(graph.getAdjacentNodes(node));
            }

            selectedGraph = graph.subgraph(new ArrayList<Node>(adj));
            this.highlightInEditor = selectedVariables;
        } else if (type == Type.adjacentsOfAdjacentsOfAdjacents) {
            Set<Node> adj = new HashSet<Node>(selectedVariables);

            for (Node node : selectedVariables) {
                adj.addAll(graph.getAdjacentNodes(node));
            }

            for (Node node : new HashSet<Node>(adj)) {
                adj.addAll(graph.getAdjacentNodes(node));
            }

            for (Node node : new HashSet<Node>(adj)) {
                adj.addAll(graph.getAdjacentNodes(node));
            }

            selectedGraph = graph.subgraph(new ArrayList<Node>(adj));
            this.highlightInEditor = selectedVariables;
        } else if (type == Type.markovBlankets) {
            Set<Node> _nodes = new HashSet<>();

            for (Node node : selectedVariables) {
                Set<Node> mb = mb(graph, node);
                mb.add(node);
                _nodes.addAll(mb);
            }

            selectedGraph = graph.subgraph(new ArrayList<Node>(_nodes));
            this.highlightInEditor = selectedVariables;
        }
        else if (type == Type.treks) {
            Graph g = new EdgeListGraph(selectedVariables);

            for (int i = 0; i < selectedVariables.size(); i++) {
                for (int j = i + 1; j < selectedVariables.size(); j++) {
                    Node x = selectedVariables.get(i);
                    Node y = selectedVariables.get(j);
                    List<List<Node>> paths = GraphUtils.treks(graph, x, y, getN() + 1);

                    if (_nType == nType.atMost && !paths.isEmpty()) {
                        for (List<Node> path : paths) {
                            if (path.size() <= getN() + 1) {
                                g.addUndirectedEdge(x, y);
                                break;
                            }
                        }
                    } else if (_nType == nType.atLeast && !paths.isEmpty()) {
                        for (List<Node> path : paths) {
                            if (path.size() >= getN() + 1) {
                                g.addUndirectedEdge(x, y);
                                break;
                            }
                        }
                    } else if (_nType == nType.equals) {
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
            this.highlightInEditor = selectedVariables;
        }
        else if (type == Type.trekEdges) {
            Set<Edge> edges = new HashSet<>();

            for (int i = 0; i < selectedVariables.size(); i++) {
                for (int j = i + 1; j < selectedVariables.size(); j++) {
                    Node x = selectedVariables.get(i);
                    Node y = selectedVariables.get(j);

                    if (_nType == nType.atMost) {
                        List<List<Node>> paths = GraphUtils.treks(graph, x, y, getN() + 1);
                        for (List<Node> path : paths) {
                            if (path.size() <= getN() + 1) {
                                edges.addAll(getEdgesFromPath(path, graph));
                            }
                        }
                    } else if (_nType == nType.atLeast) {
                        List<List<Node>> paths = GraphUtils.treks(graph, x, y, -1);
                        for (List<Node> path : paths) {
                            if (path.size() >= getN() + 1) {
                                edges.addAll(getEdgesFromPath(path, graph));
                            }
                        }
                    } else if (_nType == nType.equals) {
                        List<List<Node>> paths = GraphUtils.treks(graph, x, y, getN() + 1);
                        for (List<Node> path : paths) {
                            if (path.size() == getN() + 1) {
                                edges.addAll(getEdgesFromPath(path, graph));
                            }
                        }
                    }
                }
            }

            selectedGraph = graphFromEdges(edges, new ArrayList<Node>());
            this.highlightInEditor = selectedVariables;
        }
        else if (type == Type.paths) {
            Graph g = new EdgeListGraph(selectedVariables);

            for (int i = 0; i < selectedVariables.size(); i++) {
                for (int j = i + 1; j < selectedVariables.size(); j++) {
                    Node x = selectedVariables.get(i);
                    Node y = selectedVariables.get(j);
                    List<List<Node>> paths = GraphUtils.allPathsFromTo(graph, x, y, getN());

                    if (_nType == nType.atMost && !paths.isEmpty()) {
                        for (List<Node> path : paths) {
                            if (path.size() <= getN() + 1) {
                                g.addUndirectedEdge(x, y);
                                break;
                            }
                        }
                    } else if (_nType == nType.atLeast && !paths.isEmpty()) {
                        for (List<Node> path : paths) {
                            if (path.size() >= getN() + 1) {
                                g.addUndirectedEdge(x, y);
                                break;
                            }
                        }
                    } else if (_nType == nType.equals) {
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
            this.highlightInEditor = selectedVariables;
        } else if (type == Type.pathEdges) {
            Set<Edge> edges = new HashSet<>();

            for (int i = 0; i < selectedVariables.size(); i++) {
                for (int j = i + 1; j < selectedVariables.size(); j++) {
                    Node x = selectedVariables.get(i);
                    Node y = selectedVariables.get(j);

                    if (_nType == nType.atMost) {
                        List<List<Node>> paths = GraphUtils.allPathsFromTo(graph, x, y, getN());
                        for (List<Node> path : paths) {
                            if (path.size() <= getN() + 1) {
                                edges.addAll(getEdgesFromPath(path, graph));
                            }
                        }
                    } else if (_nType == nType.atLeast) {
                        List<List<Node>> paths = GraphUtils.allPathsFromTo(graph, x, y, -1);
                        for (List<Node> path : paths) {
                            if (path.size() >= getN() + 1) {
                                edges.addAll(getEdgesFromPath(path, graph));
                            }
                        }
                    } else if (_nType == nType.equals) {
                        List<List<Node>> paths = GraphUtils.allPathsFromTo(graph, x, y, getN());
                        for (List<Node> path : paths) {
                            if (path.size() == getN() + 1) {
                                edges.addAll(getEdgesFromPath(path, graph));
                            }
                        }
                    }
                }
            }

            selectedGraph = graphFromEdges(edges, new ArrayList<Node>());
            this.highlightInEditor = selectedVariables;
        } else if (type == Type.directedPaths) {
            Graph g = new EdgeListGraph(selectedVariables);

            for (int i = 0; i < selectedVariables.size(); i++) {
                for (int j = 0; j < selectedVariables.size(); j++) {
                    if (i == j) continue;

                    Node x = selectedVariables.get(i);
                    Node y = selectedVariables.get(j);

                    if (_nType == nType.atMost) {
                        List<List<Node>> paths = GraphUtils.allDirectedPathsFromTo(graph, x, y, getN());
                        for (List<Node> path : paths) {
                            if (path.size() <= getN() + 1) {
                                g.addDirectedEdge(x, y);
                                break;
                            }
                        }
                    } else if (_nType == nType.atLeast) {
                        List<List<Node>> paths = GraphUtils.allDirectedPathsFromTo(graph, x, y, -1);
                        for (List<Node> path : paths) {
                            if (path.size() >= getN() + 1) {
                                g.addDirectedEdge(x, y);
                                break;
                            }
                        }
                    } else if (_nType == nType.equals) {
                        List<List<Node>> paths = GraphUtils.allDirectedPathsFromTo(graph, x, y, getN());
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
            this.highlightInEditor = selectedVariables;
        } else if (type == Type.directedPathEdges) {
            Set<Edge> edges = new HashSet<>();

            for (int i = 0; i < selectedVariables.size(); i++) {
                for (int j = 0; j < selectedVariables.size(); j++) {
                    if (i == j) continue;

                    Node x = selectedVariables.get(i);
                    Node y = selectedVariables.get(j);
                    List<List<Node>> paths = GraphUtils.allDirectedPathsFromTo(graph, x, y, getN());

                    if (_nType == nType.atMost && !paths.isEmpty()) {
                        for (List<Node> path : paths) {
                            if (path.size() <= getN() + 1) {
                                edges.addAll(getEdgesFromPath(path, graph));
                            }
                        }
                    } else if (_nType == nType.atLeast && !paths.isEmpty()) {
                        for (List<Node> path : paths) {
                            if (path.size() >= getN() + 1) {
                                edges.addAll(getEdgesFromPath(path, graph));
                            }
                        }
                    } else if (_nType == nType.equals) {
                        for (List<Node> path : paths) {
                            if (path.size() == getN() + 1) {
                                edges.addAll(getEdgesFromPath(path, graph));
                            }
                        }
                    }
                }
            }

            selectedGraph = graphFromEdges(edges, new ArrayList<Node>());
            this.highlightInEditor = selectedVariables;
        } else if (type == Type.indegree) {
            Set<Edge> g = new HashSet<>();
            List<Node> nodes = new ArrayList<>();

            for (Node n : selectedVariables) {
                List<Node> h = graph.getParents(n);

                if (_nType == nType.atMost && h.size() <= getN()) {
                    nodes.add(n);
                    for (Node m : h) {
                        g.add(graph.getEdge(m, n));
                    }
                } else if (_nType == nType.atLeast && h.size() >= getN()) {
                    nodes.add(n);
                    for (Node m : h) {
                        g.add(graph.getEdge(m, n));
                    }
                } else if (_nType == nType.equals && h.size() == getN()) {
                    nodes.add(n);
                    for (Node m : h) {
                        g.add(graph.getEdge(m, n));
                    }
                }
            }

            selectedGraph = graphFromEdges(g, new ArrayList<Node>());
            this.highlightInEditor = nodes;
        } else if (type == Type.outdegree) {
            Set<Edge> g = new HashSet<>();
            List<Node> nodes = new ArrayList<>();

            for (Node n : selectedVariables) {
                List<Node> h = graph.getChildren(n);

                if (_nType == nType.atMost && h.size() <= getN()) {
                    nodes.add(n);
                    for (Node m : h) {
                        g.add(graph.getEdge(m, n));
                    }
                } else if (_nType == nType.atLeast && h.size() >= getN()) {
                    nodes.add(n);
                    for (Node m : h) {
                        g.add(graph.getEdge(m, n));
                    }
                } else if (_nType == nType.equals && h.size() == getN()) {
                    nodes.add(n);
                    for (Node m : h) {
                        g.add(graph.getEdge(m, n));
                    }
                }
            }

            selectedGraph = graphFromEdges(g, nodes);
            this.highlightInEditor = nodes;
        } else if (type == Type.degree) {
            Set<Edge> g = new HashSet<Edge>();
            List<Node> nodes = new ArrayList<>();

            for (Node n : selectedVariables) {
                List<Node> h = graph.getAdjacentNodes(n);

                if (_nType == nType.atMost && h.size() <= getN()) {
                    nodes.add(n);
                    for (Node m : h) {
                        g.add(graph.getEdge(m, n));
                    }
                } else if (_nType == nType.atLeast && h.size() >= getN()) {
                    nodes.add(n);
                    for (Node m : h) {
                        g.add(graph.getEdge(m, n));
                    }
                } else if (_nType == nType.equals && h.size() == getN()) {
                    nodes.add(n);
                    for (Node m : h) {
                        g.add(graph.getEdge(m, n));
                    }
                }
            }

            selectedGraph = graphFromEdges(g, nodes);
            this.highlightInEditor = nodes;
        } else {
            throw new IllegalArgumentException("Unrecognized selection type: " + type);
        }

        this.selectionGraph = selectedGraph;
    }

    // Sorry, this has to return the selection graph since its used downstream in the interface.
    public Graph getGraph() {
        return selectionGraph;
    }

    public void setGraph(Graph graph) {
        this.graph = graph;
        this.selectedVariables = new ArrayList<>();
        this.selectionGraph = new EdgeListGraphSingleConnections();
        log();
    }

    public Graph getSelectionGraph() {
        return selectionGraph;
    }

    public Graph getOriginalGraph() { return graph;}

    public void setDialogText(String dialogText) {
        this.dialogText = dialogText;
    }

    public String getDialogText() {
        return this.dialogText;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Graph getSourceGraph() {
        return graph;
    }

    public Graph getResultGraph() {
        return selectionGraph;
    }

    public List<String> getVariableNames() {

        return graph.getNodeNames();
    }

    public void setSelectedVariables(List<Node> variables) {
        this.selectedVariables = variables;
    }

    public List<Node> getSelectedVariables() {
        return selectedVariables;
    }

    public List<Node> getVariables() {
        return graph.getNodes();
    }

    public void setN(int n) {
        if (n < 0) throw new IllegalArgumentException();
        this.n = n;
    }

    public int getN() {
        return n;
    }

    public void setNType(nType NType) {
        this._nType = NType;
    }

    public nType getNType() {
        return this._nType;
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

    // Calculates the Markov blanket of a node in a graph.
    private Set<Node> mb(Graph graph, Node z) {
        Set<Node> mb = new HashSet<>(graph.getAdjacentNodes(z));

        for (Node c : graph.getChildren(z)) {
            for (Node p : graph.getParents(c)) {
                mb.add(p);
            }
        }

        return mb;
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





