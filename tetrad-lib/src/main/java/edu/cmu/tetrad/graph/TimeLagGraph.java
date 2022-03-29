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

package edu.cmu.tetrad.graph;

//import edu.cmu.tetrad.data.IKnowledge;
//import edu.cmu.tetrad.data.Knowledge2;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.*;

/**
 * Represents a time series graph--that is, a graph with a fixed number S of lags, with edges into initial lags
 * only--that is, into nodes in the first R lags, for some R. Edge structure repeats every R nodes.
 *
 * @author Joseph Ramsey
 */
public class TimeLagGraph implements Graph {
    static final long serialVersionUID = 23L;

    /**
     * Fires property change events.
     */
    private transient PropertyChangeSupport pcs;


    private EdgeListGraph graph = new EdgeListGraph();
    private int maxLag = 1;
    private int numInitialLags = 1;
    private List<Node> lag0Nodes = new ArrayList<>();

    private boolean pag;
    private boolean CPDAG;

    private final Map<String, Object> attributes = new HashMap<>();

    public TimeLagGraph() {
    }

    public TimeLagGraph(final TimeLagGraph graph) {
        this.graph = new EdgeListGraph(graph.getGraph());
        this.maxLag = graph.getMaxLag();
        this.numInitialLags = graph.getNumInitialLags();
        this.lag0Nodes = graph.getLag0Nodes();

        this.graph.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(final PropertyChangeEvent evt) {
                getPcs().firePropertyChange(evt);
            }
        });
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static TimeLagGraph serializableInstance() {
        return new TimeLagGraph();
    }

    /**
     * Nodes may be added into the getModel time step only. That is, node.getLag() must be 0.
     */
    public boolean addNode(Node node) {

        final NodeId id = getNodeId(node);

        if (id.getLag() != 0) {
            node = node.like(id.getName());
        }

        final boolean added = getGraph().addNode(node);

        if (!this.lag0Nodes.contains(node) && !node.getName().startsWith("E_")) {
            this.lag0Nodes.add(node);
        }

        if (node.getNodeType() == NodeType.ERROR) {
            for (int i = 1; i <= getMaxLag(); i++) {
                final Node node1 = node.like(id.getName() + ":" + i);

                if (i < getNumInitialLags()) {
                    getGraph().addNode(node1);
                }
            }
        } else {
            for (int i = 1; i <= getMaxLag(); i++) {
                final String name = id.getName() + ":" + i;
                final Node node1 = node.like(name);

                if (getGraph().getNode(name) == null) {
                    getGraph().addNode(node1);
                }
            }
        }

        getPcs().firePropertyChange("editingFinished", null, null);

        return added;
    }

    public boolean removeNode(final Node node) {
        if (!containsNode(node)) {
            throw new IllegalArgumentException("That is not a node in this graph: " + node);
        }

        final NodeId id = getNodeId(node);

        for (int lag = 0; lag < this.maxLag; lag++) {
            final Node _node = getNode(id.getName(), lag);
            if (_node != null) {
                getGraph().removeNode(_node);
            }
            if (_node != null && lag == 0) {
                this.lag0Nodes.remove(_node);
            }
        }

        getPcs().firePropertyChange("editingFinished", null, null);

        return getGraph().containsNode(node) && getGraph().removeNode(node);
    }

    public boolean addEdge(final Edge edge) {
        if (!Edges.isDirectedEdge(edge)) {
            throw new IllegalArgumentException("Only directed edges supported: " + edge);
        }

        if (!this.lag0Nodes.contains(edge.getNode2())) {
            throw new IllegalArgumentException("Edges into the current time lag only: " + edge);
        }

        final Node node1 = Edges.getDirectedEdgeTail(edge);
        final Node node2 = Edges.getDirectedEdgeHead(edge);

        final NodeId id1 = getNodeId(node1);
        final NodeId id2 = getNodeId(node2);
        final int lag = id1.getLag() - id2.getLag();

        if (lag < 0) {
            throw new IllegalArgumentException("Backward edges not permitted: " + edge);
        }

        for (int _lag = getNodeId(node2).getLag() % getNumInitialLags(); _lag <= getMaxLag() - lag; _lag += getNumInitialLags()) {
            final Node from = getNode(id1.getName(), _lag + lag);
            final Node to = getNode(id2.getName(), _lag);

            if (from == null || to == null) {
                continue;
            }

            final Edge _edge = Edges.directedEdge(from, to);

            if (!getGraph().containsEdge(_edge)) {
                getGraph().addDirectedEdge(from, to);
            }
        }

        return true;
    }

    public boolean removeEdge(final Edge edge) {
        if (!Edges.isDirectedEdge(edge))
            throw new IllegalArgumentException("Only directed edges are expected in the model.");

        final Node node1 = Edges.getDirectedEdgeTail(edge);
        final Node node2 = Edges.getDirectedEdgeHead(edge);

        final NodeId id1 = getNodeId(node1);
        final NodeId id2 = getNodeId(node2);
        final int lag = id1.getLag() - id2.getLag();

        boolean removed = false;

        for (int _lag = 0; _lag <= getMaxLag(); _lag++) {
            final Node from = getNode(id1.getName(), _lag + lag);
            final Node to = getNode(id2.getName(), _lag);

            if (from != null && to != null) {
                final Edge _edge = getGraph().getEdge(from, to);

                if (_edge != null) {
                    final boolean b = getGraph().removeEdge(_edge);
                    removed = removed || b;
                }
            }
        }

        return removed;
    }

    public boolean setMaxLag(final int maxLag) {
        if (maxLag < 0) {
            throw new IllegalArgumentException("Max lag must be at least 0: " + maxLag);
        }

        final List<Node> lag0Nodes = getLag0Nodes();

        boolean changed = false;

        if (maxLag > this.getMaxLag()) {
            this.maxLag = maxLag;
            for (final Node node : lag0Nodes) {
                addNode(node);
            }

            for (final Node node : lag0Nodes) {
                final List<Edge> edges = getGraph().getEdges(node);

                for (final Edge edge : edges) {
                    final boolean b = addEdge(edge);
                    changed = changed || b;
                }
            }
        } else if (maxLag < this.getMaxLag()) {
            for (final Node node : lag0Nodes) {
                final List<Edge> edges = getGraph().getEdges(node);

                for (final Edge edge : edges) {
                    final Node tail = Edges.getDirectedEdgeTail(edge);

                    if (getNodeId(tail).getLag() > maxLag) {
                        getGraph().removeEdge(edge);
                    }
                }
            }

            for (final Node _node : getNodes()) {
                if (getNodeId(_node).getLag() > maxLag) {
                    final boolean b = getGraph().removeNode(_node);
                    changed = changed || b;
                }
            }

            this.maxLag = maxLag;
        }

        getPcs().firePropertyChange("editingFinished", null, null);

        return changed;
    }

    public boolean removeHighLagEdges(final int maxLag) {
        final List<Node> lag0Nodes = getLag0Nodes();
        boolean changed = false;

        for (final Node node : lag0Nodes) {
            final List<Edge> edges = getGraph().getEdges(node);

            for (final Edge edge : new ArrayList<>(edges)) {
                final Node tail = Edges.getDirectedEdgeTail(edge);

                if (getNodeId(tail).getLag() > maxLag) {
                    final boolean b = getGraph().removeEdge(edge);
                    changed = changed || b;
                }
            }
        }

        return changed;
    }

    public boolean setNumInitialLags(final int numInitialLags) {
        if (numInitialLags < 1) {
            throw new IllegalArgumentException("The number of initial lags must be at least 1: " + numInitialLags);
        }

        if (numInitialLags == this.numInitialLags) return false;

        final List<Node> lag0Nodes = getLag0Nodes();
        boolean changed = false;

        for (final Node node : lag0Nodes) {
            final NodeId id = getNodeId(node);

            for (int lag = 1; lag <= getMaxLag(); lag++) {
                final Node _node = getNode(id.getName(), lag);
                final List<Node> nodesInto = getGraph().getNodesInTo(_node, Endpoint.ARROW);

                for (final Node _node2 : nodesInto) {
                    final Edge edge = Edges.directedEdge(_node2, _node);
                    final boolean b = getGraph().removeEdge(edge);
                    changed = changed || b;
                }
            }
        }

        this.numInitialLags = numInitialLags;

        for (final Node node : lag0Nodes) {
            for (int lag = 0; lag < numInitialLags; lag++) {
                final List<Edge> edges = getGraph().getEdges(node);

                for (final Edge edge : edges) {
                    final boolean b = addEdge(edge);
                    changed = changed || b;
                }
            }
        }

        getPcs().firePropertyChange("editingFinished", null, null);

        return changed;
    }

    public NodeId getNodeId(final Node node) {
        final String _name = node.getName();
        final String[] tokens = _name.split(":");
        if (tokens.length > 2) throw new IllegalArgumentException("Name may contain only one colon: " + _name);
        if (tokens[0].length() == 0) throw new IllegalArgumentException("Part to the left of the colon may " +
                "not be empty; that's the name of the variable: " + _name);
        final String name = tokens[0];
        final int lag;

        if (tokens.length == 1) {
            lag = 0;
        } else {
            lag = Integer.parseInt(tokens[1]);
            if (lag == 0) throw new IllegalArgumentException("Lag 0 edges don't have :0 descriptors");
        }

        if (lag < 0) throw new IllegalArgumentException("Lag is less than 0: " + lag);
        if (lag > getMaxLag()) throw new IllegalArgumentException("Lag is greater than the maximum lag: " + lag);

        return new NodeId(name, lag);
    }

    public Node getNode(final String name, final int lag) {
        if (name.length() == 0) throw new IllegalArgumentException("Empty node name: " + name);
        if (lag < 0) throw new IllegalArgumentException("Negative lag: " + lag);

        final String _name;

        if (lag == 0) {
            _name = name;
        } else {
            _name = name + ":" + lag;
        }

        return getNode(_name);
    }

    public List<Node> getLag0Nodes() {
        return new ArrayList<>(this.lag0Nodes);
    }

    private EdgeListGraph getGraph() {
        return this.graph;
    }

    public int getMaxLag() {
        return this.maxLag;
    }

    public int getNumInitialLags() {
        return this.numInitialLags;
    }

    @Override
    public List<String> getTriplesClassificationTypes() {
        return null;
    }

    @Override
    public List<List<Triple>> getTriplesLists(final Node node) {
        return null;
    }

    @Override
    public boolean isPag() {
        return this.pag;
    }

    @Override
    public void setPag(final boolean pag) {
        this.pag = pag;
    }

    @Override
    public boolean isCPDAG() {
        return this.CPDAG;
    }

    @Override
    public void setCPDAG(final boolean CPDAG) {
        this.CPDAG = CPDAG;
    }

    public static class NodeId {
        private final String name;
        private final int lag;

        public NodeId(final String name, final int lag) {
            this.name = name;
            this.lag = lag;
        }

        public String getName() {
            return this.name;
        }

        public int getLag() {
            return this.lag;
        }
    }

    public String toString() {
        return getGraph().toString() + "\n" + this.lag0Nodes;
    }

    public boolean addDirectedEdge(final Node node1, final Node node2) {
        return this.graph.addDirectedEdge(node1, node2);
    }

    public boolean addUndirectedEdge(final Node node1, final Node node2) {
        throw new UnsupportedOperationException("Undirected edges not currently supported.");
    }

    public boolean addNondirectedEdge(final Node node1, final Node node2) {
        throw new UnsupportedOperationException("Nondireced edges not supported.");
    }

    public boolean addPartiallyOrientedEdge(final Node node1, final Node node2) {
        throw new UnsupportedOperationException("Partially oriented edges not supported.");
    }

    public boolean addBidirectedEdge(final Node node1, final Node node2) {
        throw new UnsupportedOperationException("Bidireced edges not currently supported.");
    }

    public boolean existsDirectedCycle() {
        return getGraph().existsDirectedCycle();
    }

    public boolean isDirectedFromTo(final Node node1, final Node node2) {
        return getGraph().isDirectedFromTo(node1, node2);
    }

    public boolean isUndirectedFromTo(final Node node1, final Node node2) {
        return getGraph().isUndirectedFromTo(node1, node2);
    }

    public boolean defVisible(final Edge edge) {
        return getGraph().defVisible(edge);
    }

    public boolean isDefNoncollider(final Node node1, final Node node2, final Node node3) {
        return getGraph().isDefNoncollider(node1, node2, node3);
    }

    public boolean isDefCollider(final Node node1, final Node node2, final Node node3) {
        return getGraph().isDefCollider(node1, node2, node3);
    }

    public boolean existsDirectedPathFromTo(final Node node1, final Node node2) {
        return getGraph().existsDirectedPathFromTo(node1, node2);
    }

    @Override
    public List<Node> findCycle() {
        return getGraph().findCycle();
    }

    public boolean existsUndirectedPathFromTo(final Node node1, final Node node2) {
        return getGraph().existsUndirectedPathFromTo(node1, node2);
    }

    public boolean existsSemiDirectedPathFromTo(final Node node1, final Set<Node> nodes) {
        return getGraph().existsSemiDirectedPathFromTo(node1, nodes);
    }

    public boolean existsTrek(final Node node1, final Node node2) {
        return getGraph().existsTrek(node1, node2);
    }

    public List<Node> getChildren(final Node node) {
        return getGraph().getChildren(node);
    }

    public int getConnectivity() {
        return getGraph().getConnectivity();
    }

    public List<Node> getDescendants(final List<Node> nodes) {
        return getGraph().getDescendants(nodes);
    }

    public Edge getEdge(final Node node1, final Node node2) {
        return getGraph().getEdge(node1, node2);
    }

    public Edge getDirectedEdge(final Node node1, final Node node2) {
        return getGraph().getDirectedEdge(node1, node2);
    }

    public List<Node> getParents(final Node node) {
        return getGraph().getParents(node);
    }

    public int getIndegree(final Node node) {
        return getGraph().getIndegree(node);
    }

    @Override
    public int getDegree(final Node node) {
        return getGraph().getDegree(node);
    }

    public int getOutdegree(final Node node) {
        return getGraph().getOutdegree(node);
    }

    public boolean isAdjacentTo(final Node node1, final Node node2) {
        return getGraph().isAdjacentTo(node1, node2);
    }

    public boolean isAncestorOf(final Node node1, final Node node2) {
        return getGraph().isAncestorOf(node1, node2);
    }

    public boolean possibleAncestor(final Node node1, final Node node2) {
        return getGraph().possibleAncestor(node1, node2);
    }

    public List<Node> getAncestors(final List<Node> nodes) {
        return getGraph().getAncestors(nodes);
    }

    public boolean isChildOf(final Node node1, final Node node2) {
        return getGraph().isChildOf(node1, node2);
    }

    public boolean isDescendentOf(final Node node1, final Node node2) {
        return getGraph().isDescendentOf(node1, node2);
    }

    public boolean defNonDescendent(final Node node1, final Node node2) {
        return getGraph().defNonDescendent(node1, node2);
    }

    public boolean isDConnectedTo(final Node node1, final Node node2, final List<Node> conditioningNodes) {
        return getGraph().isDConnectedTo(node1, node2, conditioningNodes);
    }

    public boolean isDSeparatedFrom(final Node node1, final Node node2, final List<Node> z) {
        return getGraph().isDSeparatedFrom(node1, node2, z);
    }

    public boolean possDConnectedTo(final Node node1, final Node node2, final List<Node> condNodes) {
        return getGraph().possDConnectedTo(node1, node2, condNodes);
    }

    public boolean existsInducingPath(final Node node1, final Node node2) {
        return getGraph().existsInducingPath(node1, node2);
    }

    public boolean isParentOf(final Node node1, final Node node2) {
        return getGraph().isParentOf(node1, node2);
    }

    public boolean isProperAncestorOf(final Node node1, final Node node2) {
        return getGraph().isProperAncestorOf(node1, node2);
    }

    public boolean isProperDescendentOf(final Node node1, final Node node2) {
        return getGraph().isProperDescendentOf(node1, node2);
    }

    public void transferNodesAndEdges(final Graph graph) throws IllegalArgumentException {
        getGraph().transferNodesAndEdges(graph);
    }

    public void transferAttributes(final Graph graph) throws IllegalArgumentException {
        getGraph().transferAttributes(graph);
    }

    public Set<Triple> getAmbiguousTriples() {
        return getGraph().getAmbiguousTriples();
    }

    public Set<Triple> getUnderLines() {
        return getGraph().getUnderLines();
    }

    public Set<Triple> getDottedUnderlines() {
        return getGraph().getDottedUnderlines();
    }

    public boolean isAmbiguousTriple(final Node x, final Node y, final Node z) {
        return getGraph().isAmbiguousTriple(x, y, z);
    }

    public boolean isUnderlineTriple(final Node x, final Node y, final Node z) {
        return getGraph().isUnderlineTriple(x, y, z);
    }

    public boolean isDottedUnderlineTriple(final Node x, final Node y, final Node z) {
        return getGraph().isDottedUnderlineTriple(x, y, z);
    }

    public void addAmbiguousTriple(final Node x, final Node y, final Node z) {
        getGraph().addAmbiguousTriple(x, y, z);
    }

    public void addUnderlineTriple(final Node x, final Node y, final Node z) {
        getGraph().addUnderlineTriple(x, y, z);
    }

    public void addDottedUnderlineTriple(final Node x, final Node y, final Node z) {
        getGraph().addDottedUnderlineTriple(x, y, z);
    }

    public void removeAmbiguousTriple(final Node x, final Node y, final Node z) {
        getGraph().removeAmbiguousTriple(x, y, z);
    }

    public void removeUnderlineTriple(final Node x, final Node y, final Node z) {
        getGraph().removeUnderlineTriple(x, y, z);
    }

    public void removeDottedUnderlineTriple(final Node x, final Node y, final Node z) {
        getGraph().removeDottedUnderlineTriple(x, y, z);
    }

    public void setAmbiguousTriples(final Set<Triple> triples) {
        getGraph().setAmbiguousTriples(triples);
    }

    public void setUnderLineTriples(final Set<Triple> triples) {
        getGraph().setUnderLineTriples(triples);
    }

    public void setDottedUnderLineTriples(final Set<Triple> triples) {
        getGraph().setDottedUnderLineTriples(triples);
    }

    public List<Node> getCausalOrdering() {
        return getGraph().getCausalOrdering();
    }

    public void setHighlighted(final Edge edge, final boolean highlighted) {
        getGraph().setHighlighted(edge, highlighted);
    }

    public boolean isHighlighted(final Edge edge) {
        return getGraph().isHighlighted(edge);
    }

    public boolean isParameterizable(final Node node) {
        return getNodeId(node).getLag() < getNumInitialLags();
    }

    public boolean isTimeLagModel() {
        return true;
    }

    public TimeLagGraph getTimeLagGraph() {
        return this;
    }

    @Override
    public void removeTriplesNotInGraph() {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Node> getSepset(final Node n1, final Node n2) {
        return this.graph.getSepset(n1, n2);
    }

    @Override
    public void setNodes(final List<Node> nodes) {
        throw new IllegalArgumentException("Sorry, you cannot replace the variables for a time lag graph.");
    }

    public boolean isExogenous(final Node node) {
        return getGraph().isExogenous(node);
    }

    public List<Node> getAdjacentNodes(final Node node) {
        return getGraph().getAdjacentNodes(node);
    }


    public Endpoint getEndpoint(final Node node1, final Node node2) {
        return getGraph().getEndpoint(node1, node2);
    }

    public boolean setEndpoint(final Node from, final Node to, final Endpoint endPoint) throws IllegalArgumentException {
        return getGraph().setEndpoint(from, to, endPoint);
    }

    public List<Node> getNodesInTo(final Node node, final Endpoint endpoint) {
        return getGraph().getNodesInTo(node, endpoint);
    }

    public List<Node> getNodesOutTo(final Node node, final Endpoint endpoint) {
        return getGraph().getNodesOutTo(node, endpoint);
    }

    public Endpoint[][] getEndpointMatrix() {
        return getGraph().getEndpointMatrix();
    }


    public void addPropertyChangeListener(final PropertyChangeListener l) {
        getPcs().addPropertyChangeListener(l);
        getGraph().addPropertyChangeListener(l);
    }

    public Set<Edge> getEdges() {
        return getGraph().getEdges();
    }

    public boolean containsEdge(final Edge edge) {
        return getGraph().containsEdge(edge);
    }

    public boolean containsNode(final Node node) {
        return getGraph().containsNode(node);
    }

    public List<Edge> getEdges(final Node node) {
        if (getGraph().containsNode(node)) {
            return getGraph().getEdges(node);
        } else {
            return null;
        }
    }

    public List<Edge> getEdges(final Node node1, final Node node2) {
        return getGraph().getEdges(node1, node2);
    }

    public int hashCode() {
        return getGraph().hashCode();
    }

    public boolean equals(final Object o) {
        return (o instanceof TimeLagGraph) && getGraph().equals(o);
    }

    public void fullyConnect(final Endpoint endpoint) {
        getGraph().fullyConnect(endpoint);
    }

    public void reorientAllWith(final Endpoint endpoint) {
        getGraph().reorientAllWith(endpoint);
    }

    public Node getNode(final String name) {
        return getGraph().getNode(name);
    }

    public int getNumNodes() {
        return getGraph().getNumNodes();
    }

    public int getNumEdges() {
        return getGraph().getNumEdges();
    }

    public int getNumEdges(final Node node) {
        return getGraph().getNumEdges(node);
    }

    public Graph subgraph(final List<Node> nodes) {
        return getGraph().subgraph(nodes);
    }

    public List<Node> getNodes() {
        return getGraph().getNodes();
    }

    public List<String> getNodeNames() {
        return getGraph().getNodeNames();
    }

    public void clear() {
        getGraph().clear();
    }

    public boolean removeEdge(final Node node1, final Node node2) {
        return removeEdge(getEdge(node1, node2));
    }

    public boolean removeEdges(final Collection<Edge> edges) {
        boolean change = false;

        for (final Edge edge : edges) {
            final boolean _change = removeEdge(edge);
            change = change || _change;
        }

        return change;
    }

    public boolean removeNodes(final List<Node> nodes) {
        return getGraph().removeNodes(nodes);
    }

    public boolean removeEdges(final Node node1, final Node node2) {
        return removeEdges(getEdges(node1, node2));
    }

    /**
     * @return this object.
     */
    private PropertyChangeSupport getPcs() {
        if (this.pcs == null) {
            this.pcs = new PropertyChangeSupport(this);
        }
        return this.pcs;
    }

    @Override
    public Map<String, Object> getAllAttributes() {
        return this.attributes;
    }

    @Override
    public Object getAttribute(final String key) {
        return this.attributes.get(key);
    }

    @Override
    public void removeAttribute(final String key) {
        this.attributes.remove(key);
    }

    @Override
    public void addAttribute(final String key, final Object value) {
        this.attributes.put(key, value);
    }

}



