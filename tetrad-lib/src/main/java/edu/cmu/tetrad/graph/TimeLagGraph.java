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

    public TimeLagGraph(TimeLagGraph graph) {
        this.graph = new EdgeListGraph(graph.getGraph());
        maxLag = graph.getMaxLag();
        numInitialLags = graph.getNumInitialLags();
        lag0Nodes = graph.getLag0Nodes();

        this.graph.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                TimeLagGraph.this.getPcs().firePropertyChange(evt);
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

        NodeId id = this.getNodeId(node);

        if (id.getLag() != 0) {
            node = node.like(id.getName());
        }

        boolean added = this.getGraph().addNode(node);

        if (!lag0Nodes.contains(node) && !node.getName().startsWith("E_")) {
            lag0Nodes.add(node);
        }

        if (node.getNodeType() == NodeType.ERROR) {
            for (int i = 1; i <= this.getMaxLag(); i++) {
                Node node1 = node.like(id.getName() + ":" + i);

                if (i < this.getNumInitialLags()) {
                    this.getGraph().addNode(node1);
                }
            }
        } else {
            for (int i = 1; i <= this.getMaxLag(); i++) {
                String name = id.getName() + ":" + i;
                Node node1 = node.like(name);

                if (this.getGraph().getNode(name) == null) {
                    this.getGraph().addNode(node1);
                }
            }
        }

        this.getPcs().firePropertyChange("editingFinished", null, null);

        return added;
    }

    public boolean removeNode(Node node) {
        if (!this.containsNode(node)) {
            throw new IllegalArgumentException("That is not a node in this graph: " + node);
        }

        NodeId id = this.getNodeId(node);

        for (int lag = 0; lag < maxLag; lag++) {
            Node _node = this.getNode(id.getName(), lag);
            if (_node != null) {
                this.getGraph().removeNode(_node);
            }
            if (_node != null && lag == 0) {
                lag0Nodes.remove(_node);
            }
        }

        this.getPcs().firePropertyChange("editingFinished", null, null);

        return this.getGraph().containsNode(node) && this.getGraph().removeNode(node);
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

        NodeId id1 = this.getNodeId(node1);
        NodeId id2 = this.getNodeId(node2);
        int lag = id1.getLag() - id2.getLag();

        if (lag < 0) {
            throw new IllegalArgumentException("Backward edges not permitted: " + edge);
        }

        for (int _lag = this.getNodeId(node2).getLag() % this.getNumInitialLags(); _lag <= this.getMaxLag() - lag; _lag += this.getNumInitialLags()) {
            Node from = this.getNode(id1.getName(), _lag + lag);
            Node to = this.getNode(id2.getName(), _lag);

            if (from == null || to == null) {
                continue;
            }

            Edge _edge = Edges.directedEdge(from, to);

            if (!this.getGraph().containsEdge(_edge)) {
                this.getGraph().addDirectedEdge(from, to);
            }
        }

        return true;
    }

    public boolean removeEdge(Edge edge) {
        if (!Edges.isDirectedEdge(edge))
            throw new IllegalArgumentException("Only directed edges are expected in the model.");

        Node node1 = Edges.getDirectedEdgeTail(edge);
        Node node2 = Edges.getDirectedEdgeHead(edge);

        NodeId id1 = this.getNodeId(node1);
        NodeId id2 = this.getNodeId(node2);
        int lag = id1.getLag() - id2.getLag();

        boolean removed = false;

        for (int _lag = 0; _lag <= this.getMaxLag(); _lag++) {
            Node from = this.getNode(id1.getName(), _lag + lag);
            Node to = this.getNode(id2.getName(), _lag);

            if (from != null && to != null) {
                Edge _edge = this.getGraph().getEdge(from, to);

                if (_edge != null) {
                    boolean b = this.getGraph().removeEdge(_edge);
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

        List<Node> lag0Nodes = this.getLag0Nodes();

        boolean changed = false;

        if (maxLag > getMaxLag()) {
            this.maxLag = maxLag;
            for (Node node : lag0Nodes) {
                this.addNode(node);
            }

            for (Node node : lag0Nodes) {
                List<Edge> edges = this.getGraph().getEdges(node);

                for (Edge edge : edges) {
                    boolean b = this.addEdge(edge);
                    changed = changed || b;
                }
            }
        } else if (maxLag < getMaxLag()) {
            for (Node node : lag0Nodes) {
                List<Edge> edges = this.getGraph().getEdges(node);

                for (Edge edge : edges) {
                    Node tail = Edges.getDirectedEdgeTail(edge);

                    if (this.getNodeId(tail).getLag() > maxLag) {
                        this.getGraph().removeEdge(edge);
                    }
                }
            }

            for (Node _node : this.getNodes()) {
                if (this.getNodeId(_node).getLag() > maxLag) {
                    boolean b = this.getGraph().removeNode(_node);
                    changed = changed || b;
                }
            }

            this.maxLag = maxLag;
        }

        this.getPcs().firePropertyChange("editingFinished", null, null);

        return changed;
    }

    public boolean removeHighLagEdges(int maxLag) {
        List<Node> lag0Nodes = this.getLag0Nodes();
        boolean changed = false;

        for (Node node : lag0Nodes) {
            List<Edge> edges = this.getGraph().getEdges(node);

            for (Edge edge : new ArrayList<>(edges)) {
                Node tail = Edges.getDirectedEdgeTail(edge);

                if (this.getNodeId(tail).getLag() > maxLag) {
                    boolean b = this.getGraph().removeEdge(edge);
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

        List<Node> lag0Nodes = this.getLag0Nodes();
        boolean changed = false;

        for (Node node : lag0Nodes) {
            NodeId id = this.getNodeId(node);

            for (int lag = 1; lag <= this.getMaxLag(); lag++) {
                Node _node = this.getNode(id.getName(), lag);
                List<Node> nodesInto = this.getGraph().getNodesInTo(_node, Endpoint.ARROW);

                for (Node _node2 : nodesInto) {
                    Edge edge = Edges.directedEdge(_node2, _node);
                    boolean b = this.getGraph().removeEdge(edge);
                    changed = changed || b;
                }
            }
        }

        this.numInitialLags = numInitialLags;

        for (Node node : lag0Nodes) {
            for (int lag = 0; lag < numInitialLags; lag++) {
                List<Edge> edges = this.getGraph().getEdges(node);

                for (Edge edge : edges) {
                    boolean b = this.addEdge(edge);
                    changed = changed || b;
                }
            }
        }

        this.getPcs().firePropertyChange("editingFinished", null, null);

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
        } else {
            lag = Integer.parseInt(tokens[1]);
            if (lag == 0) throw new IllegalArgumentException("Lag 0 edges don't have :0 descriptors");
        }

        if (lag < 0) throw new IllegalArgumentException("Lag is less than 0: " + lag);
        if (lag > this.getMaxLag()) throw new IllegalArgumentException("Lag is greater than the maximum lag: " + lag);

        return new NodeId(name, lag);
    }

    public Node getNode(String name, int lag) {
        if (name.length() == 0) throw new IllegalArgumentException("Empty node name: " + name);
        if (lag < 0) throw new IllegalArgumentException("Negative lag: " + lag);

        String _name;

        if (lag == 0) {
            _name = name;
        } else {
            _name = name + ":" + lag;
        }

        return this.getNode(_name);
    }

    public List<Node> getLag0Nodes() {
        return new ArrayList<>(lag0Nodes);
    }

    private EdgeListGraph getGraph() {
        return graph;
    }

    public int getMaxLag() {
        return maxLag;
    }

    public int getNumInitialLags() {
        return numInitialLags;
    }

    @Override
    public List<String> getTriplesClassificationTypes() {
        return null;
    }

    @Override
    public List<List<Triple>> getTriplesLists(Node node) {
        return null;
    }

    @Override
    public boolean isPag() {
        return pag;
    }

    @Override
    public void setPag(boolean pag) {
        this.pag = pag;
    }

    @Override
    public boolean isCPDAG() {
        return CPDAG;
    }

    @Override
    public void setCPDAG(boolean CPDAG) {
        this.CPDAG = CPDAG;
    }

    public static class NodeId {
        private final String name;
        private final int lag;

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
        return this.getGraph().toString() + "\n" + lag0Nodes;
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
        return this.getGraph().existsDirectedCycle();
    }

    public boolean isDirectedFromTo(Node node1, Node node2) {
        return this.getGraph().isDirectedFromTo(node1, node2);
    }

    public boolean isUndirectedFromTo(Node node1, Node node2) {
        return this.getGraph().isUndirectedFromTo(node1, node2);
    }

    public boolean defVisible(Edge edge) {
        return this.getGraph().defVisible(edge);
    }

    public boolean isDefNoncollider(Node node1, Node node2, Node node3) {
        return this.getGraph().isDefNoncollider(node1, node2, node3);
    }

    public boolean isDefCollider(Node node1, Node node2, Node node3) {
        return this.getGraph().isDefCollider(node1, node2, node3);
    }

    public boolean existsDirectedPathFromTo(Node node1, Node node2) {
        return this.getGraph().existsDirectedPathFromTo(node1, node2);
    }

    @Override
    public List<Node> findCycle() {
        return this.getGraph().findCycle();
    }

    public boolean existsUndirectedPathFromTo(Node node1, Node node2) {
        return this.getGraph().existsUndirectedPathFromTo(node1, node2);
    }

    public boolean existsSemiDirectedPathFromTo(Node node1, Set<Node> nodes) {
        return this.getGraph().existsSemiDirectedPathFromTo(node1, nodes);
    }

    public boolean existsTrek(Node node1, Node node2) {
        return this.getGraph().existsTrek(node1, node2);
    }

    public List<Node> getChildren(Node node) {
        return this.getGraph().getChildren(node);
    }

    public int getConnectivity() {
        return this.getGraph().getConnectivity();
    }

    public List<Node> getDescendants(List<Node> nodes) {
        return this.getGraph().getDescendants(nodes);
    }

    public Edge getEdge(Node node1, Node node2) {
        return this.getGraph().getEdge(node1, node2);
    }

    public Edge getDirectedEdge(Node node1, Node node2) {
        return this.getGraph().getDirectedEdge(node1, node2);
    }

    public List<Node> getParents(Node node) {
        return this.getGraph().getParents(node);
    }

    public int getIndegree(Node node) {
        return this.getGraph().getIndegree(node);
    }

    @Override
    public int getDegree(Node node) {
        return this.getGraph().getDegree(node);
    }

    public int getOutdegree(Node node) {
        return this.getGraph().getOutdegree(node);
    }

    public boolean isAdjacentTo(Node node1, Node node2) {
        return this.getGraph().isAdjacentTo(node1, node2);
    }

    public boolean isAncestorOf(Node node1, Node node2) {
        return this.getGraph().isAncestorOf(node1, node2);
    }

    public boolean possibleAncestor(Node node1, Node node2) {
        return this.getGraph().possibleAncestor(node1, node2);
    }

    public List<Node> getAncestors(List<Node> nodes) {
        return this.getGraph().getAncestors(nodes);
    }

    public boolean isChildOf(Node node1, Node node2) {
        return this.getGraph().isChildOf(node1, node2);
    }

    public boolean isDescendentOf(Node node1, Node node2) {
        return this.getGraph().isDescendentOf(node1, node2);
    }

    public boolean defNonDescendent(Node node1, Node node2) {
        return this.getGraph().defNonDescendent(node1, node2);
    }

    public boolean isDConnectedTo(Node node1, Node node2, List<Node> conditioningNodes) {
        return this.getGraph().isDConnectedTo(node1, node2, conditioningNodes);
    }

    public boolean isDSeparatedFrom(Node node1, Node node2, List<Node> z) {
        return this.getGraph().isDSeparatedFrom(node1, node2, z);
    }

    public boolean possDConnectedTo(Node node1, Node node2, List<Node> condNodes) {
        return this.getGraph().possDConnectedTo(node1, node2, condNodes);
    }

    public boolean existsInducingPath(Node node1, Node node2) {
        return this.getGraph().existsInducingPath(node1, node2);
    }

    public boolean isParentOf(Node node1, Node node2) {
        return this.getGraph().isParentOf(node1, node2);
    }

    public boolean isProperAncestorOf(Node node1, Node node2) {
        return this.getGraph().isProperAncestorOf(node1, node2);
    }

    public boolean isProperDescendentOf(Node node1, Node node2) {
        return this.getGraph().isProperDescendentOf(node1, node2);
    }

    public void transferNodesAndEdges(Graph graph) throws IllegalArgumentException {
        this.getGraph().transferNodesAndEdges(graph);
    }

    public void transferAttributes(Graph graph) throws IllegalArgumentException {
        this.getGraph().transferAttributes(graph);
    }

    public Set<Triple> getAmbiguousTriples() {
        return this.getGraph().getAmbiguousTriples();
    }

    public Set<Triple> getUnderLines() {
        return this.getGraph().getUnderLines();
    }

    public Set<Triple> getDottedUnderlines() {
        return this.getGraph().getDottedUnderlines();
    }

    public boolean isAmbiguousTriple(Node x, Node y, Node z) {
        return this.getGraph().isAmbiguousTriple(x, y, z);
    }

    public boolean isUnderlineTriple(Node x, Node y, Node z) {
        return this.getGraph().isUnderlineTriple(x, y, z);
    }

    public boolean isDottedUnderlineTriple(Node x, Node y, Node z) {
        return this.getGraph().isDottedUnderlineTriple(x, y, z);
    }

    public void addAmbiguousTriple(Node x, Node y, Node z) {
        this.getGraph().addAmbiguousTriple(x, y, z);
    }

    public void addUnderlineTriple(Node x, Node y, Node z) {
        this.getGraph().addUnderlineTriple(x, y, z);
    }

    public void addDottedUnderlineTriple(Node x, Node y, Node z) {
        this.getGraph().addDottedUnderlineTriple(x, y, z);
    }

    public void removeAmbiguousTriple(Node x, Node y, Node z) {
        this.getGraph().removeAmbiguousTriple(x, y, z);
    }

    public void removeUnderlineTriple(Node x, Node y, Node z) {
        this.getGraph().removeUnderlineTriple(x, y, z);
    }

    public void removeDottedUnderlineTriple(Node x, Node y, Node z) {
        this.getGraph().removeDottedUnderlineTriple(x, y, z);
    }

    public void setAmbiguousTriples(Set<Triple> triples) {
        this.getGraph().setAmbiguousTriples(triples);
    }

    public void setUnderLineTriples(Set<Triple> triples) {
        this.getGraph().setUnderLineTriples(triples);
    }

    public void setDottedUnderLineTriples(Set<Triple> triples) {
        this.getGraph().setDottedUnderLineTriples(triples);
    }

    public List<Node> getCausalOrdering() {
        return this.getGraph().getCausalOrdering();
    }

    public void setHighlighted(Edge edge, boolean highlighted) {
        this.getGraph().setHighlighted(edge, highlighted);
    }

    public boolean isHighlighted(Edge edge) {
        return this.getGraph().isHighlighted(edge);
    }

    public boolean isParameterizable(Node node) {
        return this.getNodeId(node).getLag() < this.getNumInitialLags();
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

    @Override
    public void setNodes(List<Node> nodes) {
        throw new IllegalArgumentException("Sorry, you cannot replace the variables for a time lag graph.");
    }

    public boolean isExogenous(Node node) {
        return this.getGraph().isExogenous(node);
    }

    public List<Node> getAdjacentNodes(Node node) {
        return this.getGraph().getAdjacentNodes(node);
    }


    public Endpoint getEndpoint(Node node1, Node node2) {
        return this.getGraph().getEndpoint(node1, node2);
    }

    public boolean setEndpoint(Node from, Node to, Endpoint endPoint) throws IllegalArgumentException {
        return this.getGraph().setEndpoint(from, to, endPoint);
    }

    public List<Node> getNodesInTo(Node node, Endpoint endpoint) {
        return this.getGraph().getNodesInTo(node, endpoint);
    }

    public List<Node> getNodesOutTo(Node node, Endpoint endpoint) {
        return this.getGraph().getNodesOutTo(node, endpoint);
    }

    public Endpoint[][] getEndpointMatrix() {
        return this.getGraph().getEndpointMatrix();
    }


    public void addPropertyChangeListener(PropertyChangeListener l) {
        this.getPcs().addPropertyChangeListener(l);
        this.getGraph().addPropertyChangeListener(l);
    }

    public Set<Edge> getEdges() {
        return this.getGraph().getEdges();
    }

    public boolean containsEdge(Edge edge) {
        return this.getGraph().containsEdge(edge);
    }

    public boolean containsNode(Node node) {
        return this.getGraph().containsNode(node);
    }

    public List<Edge> getEdges(Node node) {
        if (this.getGraph().containsNode(node)) {
            return this.getGraph().getEdges(node);
        } else {
            return null;
        }
    }

    public List<Edge> getEdges(Node node1, Node node2) {
        return this.getGraph().getEdges(node1, node2);
    }

    public int hashCode() {
        return this.getGraph().hashCode();
    }

    public boolean equals(Object o) {
        return (o instanceof TimeLagGraph) && this.getGraph().equals(o);
    }

    public void fullyConnect(Endpoint endpoint) {
        this.getGraph().fullyConnect(endpoint);
    }

    public void reorientAllWith(Endpoint endpoint) {
        this.getGraph().reorientAllWith(endpoint);
    }

    public Node getNode(String name) {
        return this.getGraph().getNode(name);
    }

    public int getNumNodes() {
        return this.getGraph().getNumNodes();
    }

    public int getNumEdges() {
        return this.getGraph().getNumEdges();
    }

    public int getNumEdges(Node node) {
        return this.getGraph().getNumEdges(node);
    }

    public Graph subgraph(List<Node> nodes) {
        return this.getGraph().subgraph(nodes);
    }

    public List<Node> getNodes() {
        return this.getGraph().getNodes();
    }

    public List<String> getNodeNames() {
        return this.getGraph().getNodeNames();
    }

    public void clear() {
        this.getGraph().clear();
    }

    public boolean removeEdge(Node node1, Node node2) {
        return this.removeEdge(this.getEdge(node1, node2));
    }

    public boolean removeEdges(Collection<Edge> edges) {
        boolean change = false;

        for (Edge edge : edges) {
            boolean _change = this.removeEdge(edge);
            change = change || _change;
        }

        return change;
    }

    public boolean removeNodes(List<Node> nodes) {
        return this.getGraph().removeNodes(nodes);
    }

    public boolean removeEdges(Node node1, Node node2) {
        return this.removeEdges(this.getEdges(node1, node2));
    }

    /**
     * @return this object.
     */
    private PropertyChangeSupport getPcs() {
        if (pcs == null) {
            pcs = new PropertyChangeSupport(this);
        }
        return pcs;
    }

    @Override
    public Map<String, Object> getAllAttributes() {
        return attributes;
    }

    @Override
    public Object getAttribute(String key) {
        return attributes.get(key);
    }

    @Override
    public void removeAttribute(String key) {
        attributes.remove(key);
    }

    @Override
    public void addAttribute(String key, Object value) {
        attributes.put(key, value);
    }

}



