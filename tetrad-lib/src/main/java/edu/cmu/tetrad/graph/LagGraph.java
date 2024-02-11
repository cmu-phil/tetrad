///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
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

package edu.cmu.tetrad.graph;

import java.beans.PropertyChangeListener;
import java.io.Serial;
import java.util.*;

/**
 * Implements a graph allowing nodes in the getModel time lag to have parents taken from previous time lags. This is
 * intended to be interpreted as a repeating time series graph for purposes of simulation.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class LagGraph implements Graph {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The list of variables in the getModel time lag.
     */
    private final List<String> variables = new ArrayList<>();

    /**
     * The map from variables to their lagged variables.
     */
    private final Map<String, List<Node>> laggedVariables = new HashMap<>();

    /**
     * The map from variables to their attributes.
     */
    private final Map<String, Object> attributes = new HashMap<>();
    /**
     * The set of underline triples.
     */
    private final Set<Triple> underLineTriples = new HashSet<>();
    /**
     * The set of dotted underline triples.
     */
    private final Set<Triple> dottedUnderLineTriples = new HashSet<>();
    /**
     * The set of ambiguous triples.
     */
    private final Set<Triple> ambiguousTriples = new HashSet<>();
    /**
     * The graph.
     */
    private Dag graph = new Dag();

    /**
     * The paths
     */
    private Paths paths;

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetrad.graph.LagGraph} object
     */
    public static LagGraph serializableInstance() {
        return new LagGraph();
    }

    // New methods.

    /**
     * <p>addVariable.</p>
     *
     * @param variable a {@link java.lang.String} object
     * @return a boolean
     */
    public boolean addVariable(String variable) {
        if (this.variables.contains(variable)) {
            return false;
        }

        for (String _variable : this.variables) {
            if (variable.equals(_variable)) {
                return false;
            }
        }

        this.variables.add(variable);
        this.laggedVariables.put(variable, new ArrayList<>());

        for (String node : this.variables) {
            List<Node> _lags = this.laggedVariables.get(node);
            GraphNode _newNode = new GraphNode(node + "." + _lags.size());
            _lags.add(_newNode);
            _newNode.setCenter(5, 5);
            addNode(_newNode);
        }

        return true;
    }

    // Modified methods from graph.

    /**
     * {@inheritDoc}
     */
    public boolean addDirectedEdge(Node node1, Node node2) {
        return getGraph().addDirectedEdge(node1, node2);
    }

    /**
     * {@inheritDoc}
     */
    public boolean addNode(Node node) {
        throw new UnsupportedOperationException();
    }

    // Wrapped methods from graph.

    /**
     * {@inheritDoc}
     */
    public boolean addBidirectedEdge(Node node1, Node node2) {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    public boolean addUndirectedEdge(Node node1, Node node2) {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    public boolean addNondirectedEdge(Node node1, Node node2) {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    public boolean addPartiallyOrientedEdge(Node node1, Node node2) {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    public boolean addEdge(Edge edge) {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    public void addPropertyChangeListener(PropertyChangeListener e) {
        getGraph().addPropertyChangeListener(e);
    }

    /**
     * <p>clear.</p>
     */
    public void clear() {
        getGraph().clear();
    }

    /**
     * {@inheritDoc}
     */
    public boolean containsEdge(Edge edge) {
        return getGraph().containsEdge(edge);
    }

    /**
     * {@inheritDoc}
     */
    public boolean containsNode(Node node) {
        return getGraph().containsNode(node);
    }

    /**
     * {@inheritDoc}
     */
    public void fullyConnect(Endpoint endpoint) {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    public void reorientAllWith(Endpoint endpoint) {
        getGraph().reorientAllWith(endpoint);
    }

    /**
     * {@inheritDoc}
     */
    public List<Node> getAdjacentNodes(Node node) {
        return getGraph().getAdjacentNodes(node);
    }

    /**
     * {@inheritDoc}
     */
    public List<Node> getChildren(Node node) {
        return getGraph().getChildren(node);
    }

    /**
     * <p>getDegree.</p>
     *
     * @return a int
     */
    public int getDegree() {
        return getGraph().getDegree();
    }

    /**
     * {@inheritDoc}
     */
    public Edge getEdge(Node node1, Node node2) {
        return getGraph().getEdge(node1, node2);
    }

    /**
     * {@inheritDoc}
     */
    public Edge getDirectedEdge(Node node1, Node node2) {
        return getGraph().getDirectedEdge(node1, node2);
    }

    /**
     * {@inheritDoc}
     */
    public List<Edge> getEdges(Node node) {
        return getGraph().getEdges(node);
    }

    /**
     * {@inheritDoc}
     */
    public List<Edge> getEdges(Node node1, Node node2) {
        return getGraph().getEdges(node1, node2);
    }

    /**
     * <p>getEdges.</p>
     *
     * @return a {@link java.util.Set} object
     */
    public Set<Edge> getEdges() {
        return getGraph().getEdges();
    }

    /**
     * {@inheritDoc}
     */
    public Endpoint getEndpoint(Node node1, Node node2) {
        return getGraph().getEndpoint(node1, node2);
    }

    /**
     * {@inheritDoc}
     */
    public int getIndegree(Node node) {
        return getGraph().getIndegree(node);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getDegree(Node node) {
        return getGraph().getDegree(node);
    }

    /**
     * {@inheritDoc}
     */
    public Node getNode(String name) {
        return getGraph().getNode(name);
    }

    /**
     * <p>getNodes.</p>
     *
     * @return a {@link java.util.List} object
     */
    public List<Node> getNodes() {
        return getGraph().getNodes();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setNodes(List<Node> nodes) {
        this.graph.setNodes(nodes);
    }

    /**
     * <p>getNodeNames.</p>
     *
     * @return a {@link java.util.List} object
     */
    public List<String> getNodeNames() {
        return getGraph().getNodeNames();
    }

    /**
     * <p>getNumEdges.</p>
     *
     * @return a int
     */
    public int getNumEdges() {
        return getGraph().getNumEdges();
    }

    /**
     * {@inheritDoc}
     */
    public int getNumEdges(Node node) {
        return getGraph().getNumEdges(node);
    }

    /**
     * <p>getNumNodes.</p>
     *
     * @return a int
     */
    public int getNumNodes() {
        return getGraph().getNumNodes();
    }

    /**
     * {@inheritDoc}
     */
    public int getOutdegree(Node node) {
        return getGraph().getOutdegree(node);
    }

    /**
     * {@inheritDoc}
     */
    public List<Node> getParents(Node node) {
        return getGraph().getParents(node);
    }

    /**
     * {@inheritDoc}
     */
    public boolean isAdjacentTo(Node node1, Node node2) {
        return getGraph().isAdjacentTo(node1, node2);
    }

    /**
     * <p>isAncestorOf.</p>
     *
     * @param node1 a {@link edu.cmu.tetrad.graph.Node} object
     * @param node2 a {@link edu.cmu.tetrad.graph.Node} object
     * @return a boolean
     */
    public boolean isAncestorOf(Node node1, Node node2) {
        return getGraph().paths().isAncestorOf(node1, node2);
    }

    /**
     * {@inheritDoc}
     */
    public boolean isChildOf(Node node1, Node node2) {
        return getGraph().isChildOf(node2, node2);
    }

    /**
     * {@inheritDoc}
     */
    public boolean isParentOf(Node node1, Node node2) {
        return getGraph().isParentOf(node1, node2);
    }

    /**
     * {@inheritDoc}
     */
    public boolean isDefNoncollider(Node node1, Node node2, Node node3) {
        return getGraph().isDefNoncollider(node1, node2, node3);
    }

    /**
     * {@inheritDoc}
     */
    public boolean isDefCollider(Node node1, Node node2, Node node3) {
        return getGraph().isDefCollider(node1, node2, node3);
    }

    /**
     * {@inheritDoc}
     */
    public boolean isExogenous(Node node) {
        return getGraph().isExogenous(node);
    }

    /**
     * {@inheritDoc}
     */
    public List<Node> getNodesInTo(Node node, Endpoint n) {
        return getGraph().getNodesInTo(node, n);
    }

    /**
     * {@inheritDoc}
     */
    public List<Node> getNodesOutTo(Node node, Endpoint n) {
        return getGraph().getNodesOutTo(node, n);
    }

    /**
     * {@inheritDoc}
     */
    public boolean removeEdge(Edge edge) {
        return getGraph().removeEdge(edge);
    }

    /**
     * {@inheritDoc}
     */
    public boolean removeEdge(Node node1, Node node2) {
        return getGraph().removeEdge(node1, node2);
    }

    /**
     * {@inheritDoc}
     */
    public boolean removeEdges(Node node1, Node node2) {
        return getGraph().removeEdges(node1, node2);
    }

    /**
     * {@inheritDoc}
     */
    public boolean removeEdges(Collection<Edge> edges) {
        return getGraph().removeEdges(edges);
    }

    /**
     * {@inheritDoc}
     */
    public boolean removeNode(Node node) {
        return getGraph().removeNode(node);
    }

    /**
     * {@inheritDoc}
     */
    public boolean removeNodes(List<Node> nodes) {
        return getGraph().removeNodes(nodes);
    }

    /**
     * {@inheritDoc}
     */
    public boolean setEndpoint(Node from, Node to, Endpoint endPoint) {
        return getGraph().setEndpoint(from, to, endPoint);
    }

    /**
     * {@inheritDoc}
     */
    public Graph subgraph(List<Node> nodes) {
        return getGraph().subgraph(nodes);
    }

    /**
     * {@inheritDoc}
     */
    public void transferNodesAndEdges(Graph graph) throws IllegalArgumentException {
        this.getGraph().transferNodesAndEdges(graph);
    }

    /**
     * {@inheritDoc}
     */
    public void transferAttributes(Graph graph) throws IllegalArgumentException {
        this.getGraph().transferAttributes(graph);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Paths paths() {
        return this.paths;
    }

    /**
     * {@inheritDoc}
     */
    public boolean isParameterizable(Node node) {
        return getGraph().isParameterizable(node);
    }

    /**
     * <p>isTimeLagModel.</p>
     *
     * @return a boolean
     */
    public boolean isTimeLagModel() {
        return false;
    }

    /**
     * <p>getTimeLagGraph.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.TimeLagGraph} object
     */
    public TimeLagGraph getTimeLagGraph() {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Set<Node> getSepset(Node n1, Node n2) {
        throw new UnsupportedOperationException();
    }

    private Dag getGraph() {
        return this.graph;
    }

    /**
     * <p>Setter for the field <code>graph</code>.</p>
     *
     * @param graph a {@link edu.cmu.tetrad.graph.Dag} object
     */
    public void setGraph(Dag graph) {
        this.graph = graph;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String, Object> getAllAttributes() {
        return this.attributes;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object getAttribute(String key) {
        return this.attributes.get(key);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeAttribute(String key) {
        this.attributes.remove(key);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addAttribute(String key, Object value) {
        this.attributes.put(key, value);
    }

    /**
     * <p>Getter for the field <code>ambiguousTriples</code>.</p>
     *
     * @return a {@link java.util.Set} object
     */
    public Set<Triple> getAmbiguousTriples() {
        return new HashSet<>(this.ambiguousTriples);
    }

    /**
     * {@inheritDoc}
     */
    public void setAmbiguousTriples(Set<Triple> triples) {
        this.ambiguousTriples.clear();

        for (Triple triple : triples) {
            addAmbiguousTriple(triple.getX(), triple.getY(), triple.getZ());
        }
    }

    /**
     * <p>getUnderLines.</p>
     *
     * @return a {@link java.util.Set} object
     */
    public Set<Triple> getUnderLines() {
        return new HashSet<>(this.underLineTriples);
    }

    /**
     * <p>getDottedUnderlines.</p>
     *
     * @return a {@link java.util.Set} object
     */
    public Set<Triple> getDottedUnderlines() {
        return new HashSet<>(this.dottedUnderLineTriples);
    }

    /**
     * {@inheritDoc}
     * <p>
     * States whether r-s-r is an underline triple or not.
     */
    public boolean isAmbiguousTriple(Node x, Node y, Node z) {
        return this.ambiguousTriples.contains(new Triple(x, y, z));
    }

    /**
     * {@inheritDoc}
     * <p>
     * States whether r-s-r is an underline triple or not.
     */
    public boolean isUnderlineTriple(Node x, Node y, Node z) {
        return this.underLineTriples.contains(new Triple(x, y, z));
    }

    /**
     * {@inheritDoc}
     */
    public void addAmbiguousTriple(Node x, Node y, Node z) {
        this.ambiguousTriples.add(new Triple(x, y, z));
    }

    /**
     * {@inheritDoc}
     */
    public void addUnderlineTriple(Node x, Node y, Node z) {
        Triple triple = new Triple(x, y, z);

        if (!triple.alongPathIn(this)) {
            return;
        }

        this.underLineTriples.add(new Triple(x, y, z));
    }

    /**
     * {@inheritDoc}
     */
    public void addDottedUnderlineTriple(Node x, Node y, Node z) {
        Triple triple = new Triple(x, y, z);

        if (!triple.alongPathIn(this)) {
            return;
        }

        this.dottedUnderLineTriples.add(triple);
    }

    /**
     * {@inheritDoc}
     */
    public void removeAmbiguousTriple(Node x, Node y, Node z) {
        this.ambiguousTriples.remove(new Triple(x, y, z));
    }

    /**
     * {@inheritDoc}
     */
    public void removeUnderlineTriple(Node x, Node y, Node z) {
        this.underLineTriples.remove(new Triple(x, y, z));
    }

    /**
     * {@inheritDoc}
     */
    public void removeDottedUnderlineTriple(Node x, Node y, Node z) {
        this.dottedUnderLineTriples.remove(new Triple(x, y, z));
    }

    /**
     * {@inheritDoc}
     */
    public void setUnderLineTriples(Set<Triple> triples) {
        this.underLineTriples.clear();

        for (Triple triple : triples) {
            addUnderlineTriple(triple.getX(), triple.getY(), triple.getZ());
        }
    }

    /**
     * {@inheritDoc}
     */
    public void setDottedUnderLineTriples(Set<Triple> triples) {
        this.dottedUnderLineTriples.clear();

        for (Triple triple : triples) {
            addDottedUnderlineTriple(triple.getX(), triple.getY(), triple.getZ());
        }
    }

    /**
     * <p>removeTriplesNotInGraph.</p>
     */
    public void removeTriplesNotInGraph() {
        for (Triple triple : new HashSet<>(this.ambiguousTriples)) {
            if (!containsNode(triple.getX()) || !containsNode(triple.getY())
                    || !containsNode(triple.getZ())) {
                this.ambiguousTriples.remove(triple);
                continue;
            }

            if (!isAdjacentTo(triple.getX(), triple.getY())
                    || !isAdjacentTo(triple.getY(), triple.getZ())) {
                this.ambiguousTriples.remove(triple);
            }
        }

        for (Triple triple : new HashSet<>(this.underLineTriples)) {
            if (!containsNode(triple.getX()) || !containsNode(triple.getY())
                    || !containsNode(triple.getZ())) {
                this.underLineTriples.remove(triple);
                continue;
            }

            if (!isAdjacentTo(triple.getX(), triple.getY()) || !isAdjacentTo(triple.getY(), triple.getZ())) {
                this.underLineTriples.remove(triple);
            }
        }

        for (Triple triple : new HashSet<>(this.dottedUnderLineTriples)) {
            if (!containsNode(triple.getX()) || !containsNode(triple.getY()) || !containsNode(triple.getZ())) {
                this.dottedUnderLineTriples.remove(triple);
                continue;
            }

            if (!isAdjacentTo(triple.getX(), triple.getY()) || isAdjacentTo(triple.getY(), triple.getZ())) {
                this.dottedUnderLineTriples.remove(triple);
            }
        }
    }
}



