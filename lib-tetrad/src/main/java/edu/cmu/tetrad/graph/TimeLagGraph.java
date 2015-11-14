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

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

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
    private List<Node> lag0Nodes = new ArrayList<Node>();

    public TimeLagGraph() {
    }

    public TimeLagGraph(TimeLagGraph graph) {
        this.graph = new EdgeListGraph(graph.getGraph());
        this.maxLag = graph.getMaxLag();
        this.numInitialLags = graph.getNumInitialLags();
        this.lag0Nodes = graph.getLag0Nodes();

        this.graph.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
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
        
        NodeId id = getNodeId(node);

        if (id.getLag() != 0) {
            node = node.like(id.getName());
//            throw new IllegalArgumentException("Nodes may be added into the getModel time step only.");
        }

        boolean added = getGraph().addNode(node);

        if (!lag0Nodes.contains(node)) {
            lag0Nodes.add(node);
        }

        if (node.getNodeType() == NodeType.ERROR) {
            for (int i = 1; i <= getMaxLag(); i++) {
                Node node1 = node.like(id.getName() + ":" + i);

                if (i < getNumInitialLags()) {
                    getGraph().addNode(node1);
                }
            }
        }
        else {
            for (int i = 1; i <= getMaxLag(); i++) {
                Node node1 = node.like(id.getName() + ":" + i);
                getGraph().addNode(node1);
            }
        }

        getPcs().firePropertyChange("editingFinished", null, null);

        return added;
    }

    public boolean removeNode(Node node) {
        if (!containsNode(node)) {
            throw new IllegalArgumentException("That is not a node in this graph: " + node);
        }

        NodeId id = getNodeId(node);

        for (int lag = 0; lag < maxLag; lag++) {
            Node _node = getNode(id.getName(), lag);
            if (_node != null) {
                getGraph().removeNode(_node);
            }
            if (_node != null && lag == 0) {
                lag0Nodes.remove(_node);
            }
        }

        getPcs().firePropertyChange("editingFinished", null, null);

        if (getGraph().containsNode(node)) {
            return getGraph().removeNode(node);
        }
        else {
            return false;
        }
    }

    public boolean addEdge(Edge edge) {
        if (!Edges.isDirectedEdge(edge)) {
            throw new IllegalArgumentException("Only directed edges supported: " + edge);
        }

        if (!lag0Nodes.contains(edge.getNode2())) {
            throw new IllegalArgumentException("Edges into the current time lag only: " + edge);
        }

        Node node1 = Edges.getDirectedEdgeTail(edge);
        Node node2 = Edges.getDirectedEdgeHead(edge);

        NodeId id1 = getNodeId(node1);
        NodeId id2 = getNodeId(node2);
        int lag = id1.getLag() - id2.getLag();

        if (lag < 0) {
            throw new IllegalArgumentException("Backward edges not permitted: " + edge);
        }

        for (int _lag = getNodeId(node2).getLag() % getNumInitialLags(); _lag <= getMaxLag() - lag; _lag += getNumInitialLags()) {
            Node from = getNode(id1.getName(), _lag + lag);
            Node to = getNode(id2.getName(), _lag);

            if (from == null || to == null) {
                continue;
            }

            Edge _edge = Edges.directedEdge(from, to);

            if (!getGraph().containsEdge(_edge)) {
                getGraph().addDirectedEdge(from, to);
            }
        }

        return true;
    }

    public boolean removeEdge(Edge edge) {
        if (!Edges.isDirectedEdge(edge)) throw new IllegalArgumentException("Only directed edges are expected in the model.");

        Node node1 = Edges.getDirectedEdgeTail(edge);
        Node node2 = Edges.getDirectedEdgeHead(edge);

        NodeId id1 = getNodeId(node1);
        NodeId id2 = getNodeId(node2);
        int lag = id1.getLag() - id2.getLag();

        boolean removed = false;

        for (int _lag = 0; _lag <= getMaxLag(); _lag++) {
            Node from = getNode(id1.getName(), _lag + lag);
            Node to = getNode(id2.getName(), _lag);

            if (from != null && to != null) {
                Edge _edge = getGraph().getEdge(from, to);

                if (_edge != null) {
                    boolean b = getGraph().removeEdge(_edge);
                    removed = removed || b;
                }
            }
        }

        return removed;
    }

    public boolean setMaxLag(int maxLag) {
        if (maxLag < 0) {
            throw new IllegalArgumentException("Max lag must be at least 0: " + maxLag);
        }

        List<Node> lag0Nodes = getLag0Nodes();

        boolean changed = false;

        if (maxLag > this.getMaxLag()) {
            this.maxLag = maxLag;
            for (Node node : lag0Nodes) {
                addNode(node);
            }

            for (Node node : lag0Nodes) {
                List<Edge> edges = getGraph().getEdges(node);

                for (Edge edge : edges) {
                    boolean b = addEdge(edge);
                    changed = changed || b;
                }
            }
        } else if (maxLag < this.getMaxLag()) {
            for (Node node : lag0Nodes) {
                List<Edge> edges = getGraph().getEdges(node);

                for (Edge edge : edges) {
                    Node tail = Edges.getDirectedEdgeTail(edge);

                    if (getNodeId(tail).getLag() > maxLag) {
                        getGraph().removeEdge(edge);
//                        throw new IllegalArgumentException("This edge has lag greater than the new maxLag: " + edge +
//                                " Please remove first.");
                    }
                }
            }

            for (Node _node : getNodes()) {
                if (getNodeId(_node).getLag() > maxLag) {
                    boolean b = getGraph().removeNode(_node);
                    changed = changed || b;
                }
            }

            this.maxLag = maxLag;
        }

        getPcs().firePropertyChange("editingFinished", null, null);

        return changed;
    }

    public boolean removeHighLagEdges(int maxLag) {
        List<Node> lag0Nodes = getLag0Nodes();
        boolean changed = false;

        for (Node node : lag0Nodes) {
            List<Edge> edges = getGraph().getEdges(node);

            for (Edge edge : new ArrayList<Edge>(edges)) {
                Node tail = Edges.getDirectedEdgeTail(edge);

                if (getNodeId(tail).getLag() > maxLag) {
                    boolean b = getGraph().removeEdge(edge);
                    changed = changed || b;
                }
            }
        }

        return changed;
    }

    public boolean setNumInitialLags(int numInitialLags) {
        if (numInitialLags < 1) {
            throw new IllegalArgumentException("The number of initial lags must be at least 1: " + numInitialLags);
        }

        if (numInitialLags == this.numInitialLags) return false;

        List<Node> lag0Nodes = getLag0Nodes();
        boolean changed = false;

        for (Node node : lag0Nodes) {
            NodeId id = getNodeId(node);

            for (int lag = 1; lag <= getMaxLag(); lag++) {
                Node _node = getNode(id.getName(), lag);
                List<Node> nodesInto = getGraph().getNodesInTo(_node, Endpoint.ARROW);

                for (Node _node2 : nodesInto) {
                    Edge edge = Edges.directedEdge(_node2, _node);
                    boolean b = getGraph().removeEdge(edge);
                    changed = changed || b;
                }
            }
        }

        this.numInitialLags = numInitialLags;

        for (Node node : lag0Nodes) {
            for (int lag = 0; lag < numInitialLags; lag++) {
                List<Edge> edges = getGraph().getEdges(node);

                for (Edge edge : edges) {
                    boolean b = addEdge(edge);
                    changed = changed || b;
                }
            }
        }

        getPcs().firePropertyChange("editingFinished", null, null);

        return changed;
    }

    public NodeId getNodeId(Node node) {
        String _name = node.getName();
        String[] tokens = _name.split(":");
        if (tokens.length > 2) throw new IllegalArgumentException("Name may contain only one colon: " + _name);
        if (tokens[0].length() == 0) throw new IllegalArgumentException("Part to the left of the colon may " +
                "not be empty; that's the name of the variable: " + _name);
        String name = tokens[0];
        int lag;

        if (tokens.length == 1) {
            lag = 0;
        }
        else {
            lag = Integer.parseInt(tokens[1]);
            if (lag == 0) throw new IllegalArgumentException("Lag 0 edges don't have :0 descriptors");
        }

        if (lag < 0) throw new IllegalArgumentException("Lag is less than 0: " + lag);
        if (lag > getMaxLag()) throw new IllegalArgumentException("Lag is greater than the maximum lag: " + lag);

        return new NodeId(name, lag);
    }

    public Node getNode(String name, int lag) {
        if (name.length() == 0) throw new IllegalArgumentException("Empty node name: " + name);
        if (lag < 0) throw new IllegalArgumentException("Negative lag: " + lag);

        String _name;

        if (lag == 0) {
            _name = name;
        }
        else {
            _name = name + ":" + lag;
        }

        return getNode(_name);
    }

    public List<Node> getLag0Nodes() {
        return new ArrayList<>(lag0Nodes);
    }

    public EdgeListGraph getGraph() {
        return graph;
    }

    public int getMaxLag() {
        return maxLag;
    }

    public int getNumInitialLags() {
        return numInitialLags;
    }

    public static class NodeId {
        private String name;
        private int lag;

        public NodeId(String name, int lag) {
            this.name = name;
            this.lag = lag;
        }

        public String getName() {
            return name;
        }

        public int getLag() {
            return lag;
        }
    }

    public String toString() {
        return getGraph().toString() + "\n" + lag0Nodes;
    }

    public boolean addGraphConstraint(GraphConstraint gc) {
        return getGraph().addGraphConstraint(gc);
    }

    public boolean addDirectedEdge(Node node1, Node node2) {
        return graph.addDirectedEdge(node1, node2);
    }

    public boolean addUndirectedEdge(Node node1, Node node2) {
        throw new UnsupportedOperationException("Undirected edges not currently supported.");
    }

    public boolean addNondirectedEdge(Node node1, Node node2) {
        throw new UnsupportedOperationException("Nondireced edges not supported.");
    }

    public boolean addPartiallyOrientedEdge(Node node1, Node node2) {
        throw new UnsupportedOperationException("Partially oriented edges not supported.");
    }

    public boolean addBidirectedEdge(Node node1, Node node2) {
        throw new UnsupportedOperationException("Bidireced edges not currently supported.");
    }

    public boolean existsDirectedCycle() {
        return getGraph().existsDirectedCycle();
    }

    public boolean isDirectedFromTo(Node node1, Node node2) {
        return getGraph().isDirectedFromTo(node1, node2);
    }

    public boolean isUndirectedFromTo(Node node1, Node node2) {
        return getGraph().isUndirectedFromTo(node1, node2);
    }

    public boolean defVisible(Edge edge) {
        return getGraph().defVisible(edge);
    }

    public boolean isDefNoncollider(Node node1, Node node2, Node node3) {
        return getGraph().isDefNoncollider(node1, node2, node3);
    }

    public boolean isDefCollider(Node node1, Node node2, Node node3) {
        return getGraph().isDefCollider(node1, node2, node3);
    }

    public boolean existsDirectedPathFromTo(Node node1, Node node2) {
        return getGraph().existsDirectedPathFromTo(node1, node2);
    }

    public boolean existsUndirectedPathFromTo(Node node1, Node node2) {
        return getGraph().existsUndirectedPathFromTo(node1, node2);
    }

    public boolean existsSemiDirectedPathFromTo(Node node1, Set<Node> nodes) {
        return getGraph().existsSemiDirectedPathFromTo(node1, nodes);
    }

    public boolean existsTrek(Node node1, Node node2) {
        return getGraph().existsTrek(node1, node2);
    }

    public List<Node> getChildren(Node node) {
        return getGraph().getChildren(node);
    }

    public int getConnectivity() {
        return getGraph().getConnectivity();
    }

    public List<Node> getDescendants(List<Node> nodes) {
        return getGraph().getDescendants(nodes);
    }

    public Edge getEdge(Node node1, Node node2) {
        return getGraph().getEdge(node1, node2);
    }

    public Edge getDirectedEdge(Node node1, Node node2) {
        return getGraph().getDirectedEdge(node1, node2);
    }

    public List<Node> getParents(Node node) {
        return getGraph().getParents(node);
    }

    public int getIndegree(Node node) {
        return getGraph().getIndegree(node);
    }

    public int getOutdegree(Node node) {
        return getGraph().getOutdegree(node);
    }

    public boolean isAdjacentTo(Node node1, Node node2) {
        return getGraph().isAdjacentTo(node1, node2);
    }

    public boolean isAncestorOf(Node node1, Node node2) {
        return getGraph().isAncestorOf(node1, node2);
    }

    public boolean possibleAncestor(Node node1, Node node2) {
        return getGraph().possibleAncestor(node1, node2);
    }

    public List<Node> getAncestors(List<Node> nodes) {
        return getGraph().getAncestors(nodes);
    }

    public boolean isChildOf(Node node1, Node node2) {
        return getGraph().isChildOf(node1, node2);
    }

    public boolean isDescendentOf(Node node1, Node node2) {
        return getGraph().isDescendentOf(node1, node2);
    }

    public boolean defNonDescendent(Node node1, Node node2) {
        return getGraph().defNonDescendent(node1, node2);
    }

    public boolean isDConnectedTo(Node node1, Node node2, List<Node> conditioningNodes) {
        return getGraph().isDConnectedTo(node1, node2, conditioningNodes);
    }

    public boolean isDSeparatedFrom(Node node1, Node node2, List<Node> z) {
        return getGraph().isDSeparatedFrom(node1, node2, z);
    }

    public boolean possDConnectedTo(Node node1, Node node2, List<Node> condNodes) {
        return getGraph().possDConnectedTo(node1, node2, condNodes);
    }

    public boolean existsInducingPath(Node node1, Node node2) {
        return getGraph().existsInducingPath(node1, node2);
    }

    public boolean isParentOf(Node node1, Node node2) {
        return getGraph().isParentOf(node1, node2);
    }

    public boolean isProperAncestorOf(Node node1, Node node2) {
        return getGraph().isProperAncestorOf(node1, node2);
    }

    public boolean isProperDescendentOf(Node node1, Node node2) {
        return getGraph().isProperDescendentOf(node1, node2);
    }

    public void transferNodesAndEdges(Graph graph) throws IllegalArgumentException {
        getGraph().transferNodesAndEdges(graph);
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

    public boolean isAmbiguousTriple(Node x, Node y, Node z) {
        return getGraph().isAmbiguousTriple(x, y, z);
    }

    public boolean isUnderlineTriple(Node x, Node y, Node z) {
        return getGraph().isUnderlineTriple(x, y, z);
    }

    public boolean isDottedUnderlineTriple(Node x, Node y, Node z) {
        return getGraph().isDottedUnderlineTriple(x, y, z);
    }

    public void addAmbiguousTriple(Node x, Node y, Node z) {
        getGraph().addAmbiguousTriple(x, y, z);
    }

    public void addUnderlineTriple(Node x, Node y, Node z) {
        getGraph().addUnderlineTriple(x, y, z);
    }

    public void addDottedUnderlineTriple(Node x, Node y, Node z) {
        getGraph().addDottedUnderlineTriple(x, y, z);
    }

    public void removeAmbiguousTriple(Node x, Node y, Node z) {
        getGraph().removeAmbiguousTriple(x, y, z);
    }

    public void removeUnderlineTriple(Node x, Node y, Node z) {
        getGraph().removeUnderlineTriple(x, y, z);
    }

    public void removeDottedUnderlineTriple(Node x, Node y, Node z) {
        getGraph().removeDottedUnderlineTriple(x, y, z);
    }

    public void setAmbiguousTriples(Set<Triple> triples) {
        getGraph().setAmbiguousTriples(triples);
    }

    public void setUnderLineTriples(Set<Triple> triples) {
        getGraph().setUnderLineTriples(triples);
    }

    public void setDottedUnderLineTriples(Set<Triple> triples) {
        getGraph().setDottedUnderLineTriples(triples);
    }

    public List<Node> getCausalOrdering() {
        return getGraph().getCausalOrdering();
    }

    public void setHighlighted(Edge edge, boolean highlighted) {
        getGraph().setHighlighted(edge, highlighted);
    }

    public boolean isHighlighted(Edge edge) {
        return getGraph().isHighlighted(edge);
    }

    public boolean isParameterizable(Node node) {
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
    public List<Node> getSepset(Node n1, Node n2) {
        return graph.getSepset(n1, n2);
    }

    public boolean isExogenous(Node node) {
        return getGraph().isExogenous(node);
    }

    public List<Node> getAdjacentNodes(Node node) {
        return getGraph().getAdjacentNodes(node);
    }


    public Endpoint getEndpoint(Node node1, Node node2) {
        return getGraph().getEndpoint(node1, node2);
    }

    public boolean setEndpoint(Node from, Node to, Endpoint endPoint) throws IllegalArgumentException {
        return getGraph().setEndpoint(from, to, endPoint);
    }

    public List<Node> getNodesInTo(Node node, Endpoint endpoint) {
        return getGraph().getNodesInTo(node, endpoint);
    }

    public List<Node> getNodesOutTo(Node node, Endpoint endpoint) {
        return getGraph().getNodesOutTo(node, endpoint);
    }

    public Endpoint[][] getEndpointMatrix() {
        return getGraph().getEndpointMatrix();
    }


    public void addPropertyChangeListener(PropertyChangeListener l) {
        getPcs().addPropertyChangeListener(l);
        getGraph().addPropertyChangeListener(l);
    }

    public Set<Edge> getEdges() {
        return getGraph().getEdges();
    }

    public boolean containsEdge(Edge edge) {
        return getGraph().containsEdge(edge);
    }

    public boolean containsNode(Node node) {
        return getGraph().containsNode(node);
    }

    public List<Edge> getEdges(Node node) {
        if (getGraph().containsNode(node)) {
            return getGraph().getEdges(node);
        }
        else {
            return null;
        }
    }

    public List<Edge> getEdges(Node node1, Node node2) {
        return getGraph().getEdges(node1, node2);
    }

    public int hashCode() {
        return getGraph().hashCode();
    }

    public boolean equals(Object o) {
        return getGraph().equals(o);
    }

    public void fullyConnect(Endpoint endpoint) {
        getGraph().fullyConnect(endpoint);
    }

    public void reorientAllWith(Endpoint endpoint) {
        getGraph().reorientAllWith(endpoint);
    }

    public Node getNode(String name) {
        return getGraph().getNode(name);
    }

    public int getNumNodes() {
        return getGraph().getNumNodes();
    }

    public int getNumEdges() {
        return getGraph().getNumEdges();
    }

    public int getNumEdges(Node node) {
        return getGraph().getNumEdges(node);
    }

    public List<GraphConstraint> getGraphConstraints() {
        return getGraph().getGraphConstraints();
    }

    public boolean isGraphConstraintsChecked() {
        return getGraph().isGraphConstraintsChecked();
    }

    public void setGraphConstraintsChecked(boolean checked) {
        getGraph().setGraphConstraintsChecked(checked);
    }

    public Graph subgraph(List<Node> nodes) {
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

    public boolean removeEdge(Node node1, Node node2) {
        return removeEdge(getEdge(node1, node2));
    }

    public boolean removeEdges(Collection<Edge> edges) {
        boolean change = false;

        for (Edge edge : edges) {
            boolean _change = removeEdge(edge);
            change = change || _change;
        }

        return change;
    }

    public boolean removeNodes(List<Node> nodes) {
        return getGraph().removeNodes(nodes);
    }

    public boolean removeEdges(Node node1, Node node2) {
        return removeEdges(getEdges(node1, node2));
    }

    /**
     * @return the existing property change support object for this class, if
     * there is one, or else creates a new one and returns that.
     *
     * @return this object.
     */
    private PropertyChangeSupport getPcs() {
        if (pcs == null) {
            pcs = new PropertyChangeSupport(this);
        }
        return pcs;
    }


}



