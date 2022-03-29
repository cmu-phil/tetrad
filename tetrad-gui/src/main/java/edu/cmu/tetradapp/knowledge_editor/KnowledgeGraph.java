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

package edu.cmu.tetradapp.knowledge_editor;

import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.TetradSerializableExcluded;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.beans.PropertyChangeListener;
import java.util.*;

/**
 * This class represents a directed acyclic graph.  In addition to the
 * constraints imposed by Graph, the following (mostly redundant)
 * basicConstraints are in place: (a) The graph may contain only measured and
 * latent variables (no error variables). (b) The graph may contain only
 * directed edges (c) The graph may contain no directed cycles.
 *
 * @author Joseph Ramsey
 */
public class KnowledgeGraph implements Graph, TetradSerializableExcluded {
    static final long serialVersionUID = 23L;

    /**
     * @serial
     */
    private final Graph graph = new EdgeListGraph();

    /**
     * @serial
     */
    private final IKnowledge knowledge;
    private boolean pag;
    private boolean CPDAG;

    private final Map<String, Object> attributes = new HashMap<>();

    //============================CONSTRUCTORS=============================//

    /**
     * Constructs a new directed acyclic graph (DAG).
     */
    public KnowledgeGraph(final IKnowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException();
        }

        this.knowledge = knowledge;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static KnowledgeGraph serializableInstance() {
        return new KnowledgeGraph(Knowledge2.serializableInstance());
    }

    //=============================PUBLIC METHODS==========================//

    public final void transferNodesAndEdges(final Graph graph)
            throws IllegalArgumentException {
        this.getGraph().transferNodesAndEdges(graph);
        for (final Node node : this.getGraph().getNodes()) {
            node.getAllAttributes().clear();
        }
    }

    public final void transferAttributes(final Graph graph)
            throws IllegalArgumentException {
        this.getGraph().transferAttributes(graph);
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


    /**
     * States whether x-y-x is an underline triple or not.
     */
    public boolean isAmbiguousTriple(final Node x, final Node y, final Node z) {
        return getGraph().isAmbiguousTriple(x, y, z);
    }

    /**
     * States whether x-y-x is an underline triple or not.
     */
    public boolean isUnderlineTriple(final Node x, final Node y, final Node z) {
        return getGraph().isUnderlineTriple(x, y, z);
    }

    /**
     * States whether x-y-x is an underline triple or not.
     */
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
        return false;
    }

    public boolean isTimeLagModel() {
        return false;
    }

    public TimeLagGraph getTimeLagGraph() {
        return null;
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
        this.graph.setNodes(nodes);
    }

    public List<String> getNodeNames() {
        return getGraph().getNodeNames();
    }

    public void fullyConnect(final Endpoint endpoint) {
        getGraph().fullyConnect(endpoint);
    }

    public void reorientAllWith(final Endpoint endpoint) {
        getGraph().reorientAllWith(endpoint);
    }

    public Endpoint[][] getEndpointMatrix() {
        return getGraph().getEndpointMatrix();
    }

    public List<Node> getAdjacentNodes(final Node node) {
        return getGraph().getAdjacentNodes(node);
    }

    public List<Node> getNodesInTo(final Node node, final Endpoint endpoint) {
        return getGraph().getNodesInTo(node, endpoint);
    }

    public List<Node> getNodesOutTo(final Node node, final Endpoint n) {
        return getGraph().getNodesOutTo(node, n);
    }

    public List<Node> getNodes() {
        return getGraph().getNodes();
    }

    public boolean removeEdge(final Node node1, final Node node2) {
        return removeEdge(getEdge(node1, node2));
    }

    public boolean removeEdges(final Node node1, final Node node2) {
        return getGraph().removeEdges(node1, node2);
    }

    public boolean isAdjacentTo(final Node nodeX, final Node nodeY) {
        return getGraph().isAdjacentTo(nodeX, nodeY);
    }

    public boolean setEndpoint(final Node node1, final Node node2, final Endpoint endpoint) {
        return getGraph().setEndpoint(node1, node2, endpoint);
    }

    public Endpoint getEndpoint(final Node node1, final Node node2) {
        return getGraph().getEndpoint(node1, node2);
    }

    public boolean equals(final Object o) {
        return getGraph().equals(o);
    }

    public Graph subgraph(final List<Node> nodes) {
        return getGraph().subgraph(nodes);
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

    public boolean existsSemiDirectedPathFromTo(final Node node1, final Set node2) {
        return getGraph().existsSemiDirectedPathFromTo(node1, node2);
    }

    public boolean addDirectedEdge(final Node nodeA, final Node nodeB) {
        throw new UnsupportedOperationException();
    }

    public boolean addUndirectedEdge(final Node nodeA, final Node nodeB) {
        throw new UnsupportedOperationException();
    }

    public boolean addNondirectedEdge(final Node nodeA, final Node nodeB) {
        throw new UnsupportedOperationException();
    }

    public boolean addPartiallyOrientedEdge(final Node nodeA, final Node nodeB) {
        throw new UnsupportedOperationException();
    }

    public boolean addBidirectedEdge(final Node nodeA, final Node nodeB) {
        throw new UnsupportedOperationException();
    }

    public boolean addEdge(final Edge edge) {
        if (!(edge instanceof KnowledgeModelEdge)) {
            return false;
        }
        final KnowledgeModelEdge _edge = (KnowledgeModelEdge) edge;
        final KnowledgeModelNode _node1 = (KnowledgeModelNode) _edge.getNode1();
        final KnowledgeModelNode _node2 = (KnowledgeModelNode) _edge.getNode2();
        final String from = _node1.getName();
        final String to = _node2.getName();

        if (_edge.getType() == KnowledgeModelEdge.FORBIDDEN_EXPLICITLY) {
            this.knowledge.setForbidden(from, to);
        } else if (_edge.getType() == KnowledgeModelEdge.REQUIRED) {
            this.knowledge.setRequired(from, to);
        } else if (_edge.getType() == KnowledgeModelEdge.FORBIDDEN_BY_TIERS) {
            if (!this.knowledge.isForbiddenByTiers(from, to)) {
                throw new IllegalArgumentException("Edge " + from + "-->" + to +
                        " is not forbidden by tiers.");
            }
        } else if (_edge.getType() == KnowledgeModelEdge.FORBIDDEN_BY_GROUPS) {
            if (!this.knowledge.isForbiddenByGroups(from, to)) {
                throw new IllegalArgumentException("Edge " + from + "-->" + to +
                        " is not forbidden by groups.");
            }
        } else if (_edge.getType() == KnowledgeModelEdge.REQUIRED_BY_GROUPS) {
            if (!this.knowledge.isRequiredByGroups(from, to)) {
                throw new IllegalArgumentException("Edge " + from + "-->" + to +
                        " is not required by groups.");
            }
        }

        if (!getGraph().containsEdge(edge)) {
            return getGraph().addEdge(edge);
        }

        return false;
    }

    public boolean addNode(final Node node) {
        return getGraph().addNode(node);
    }

    public void addPropertyChangeListener(final PropertyChangeListener l) {
        getGraph().addPropertyChangeListener(l);
    }

    public boolean containsEdge(final Edge edge) {
        return getGraph().containsEdge(edge);
    }

    public boolean containsNode(final Node node) {
        return getGraph().containsNode(node);
    }

    public Set<Edge> getEdges() {
        return getGraph().getEdges();
    }

    public List<Edge> getEdges(final Node node) {
        return getGraph().getEdges(node);
    }

    public List<Edge> getEdges(final Node node1, final Node node2) {
        return getGraph().getEdges(node1, node2);
    }

    public Node getNode(final String name) {
        return getGraph().getNode(name);
    }

    public int getNumEdges() {
        return getGraph().getNumEdges();
    }

    public int getNumNodes() {
        return getGraph().getNumNodes();
    }

    public int getNumEdges(final Node node) {
        return getGraph().getNumEdges(node);
    }

    public boolean removeEdge(final Edge edge) {
        final KnowledgeModelEdge _edge = (KnowledgeModelEdge) edge;
        final KnowledgeModelNode _node1 = (KnowledgeModelNode) _edge.getNode1();
        final KnowledgeModelNode _node2 = (KnowledgeModelNode) _edge.getNode2();
        final String from = _node1.getName();
        final String to = _node2.getName();

        if (_edge.getType() == KnowledgeModelEdge.FORBIDDEN_EXPLICITLY) {
            getKnowledge().removeForbidden(from, to);
        } else if (_edge.getType() == KnowledgeModelEdge.REQUIRED) {
            getKnowledge().removeRequired(from, to);
        } else if (_edge.getType() == KnowledgeModelEdge.FORBIDDEN_BY_TIERS) {
            throw new IllegalArgumentException(
                    "Please use the tiers interface " +
                            "to remove edges forbidden by tiers.");
        } else if (_edge.getType() == KnowledgeModelEdge.FORBIDDEN_BY_GROUPS) {
            throw new IllegalArgumentException("Please use the Other Groups interface to " +
                    "remove edges forbidden by groups.");
        } else if (_edge.getType() == KnowledgeModelEdge.REQUIRED_BY_GROUPS) {
            throw new IllegalArgumentException("Please use the Other Groups interface to " +
                    "remove edges required by groups.");
        }

        return getGraph().removeEdge(edge);
    }

    public boolean removeEdges(final Collection edges) {
        boolean removed = false;

        for (final Object edge1 : edges) {
            final Edge edge = (Edge) edge1;
            removed = removed || removeEdge(edge);
        }

        return removed;
    }

    public boolean removeNode(final Node node) {
        return getGraph().removeNode(node);
    }

    public void clear() {
        getGraph().clear();
    }

    public boolean removeNodes(final List<Node> nodes) {
        return getGraph().removeNodes(nodes);
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

    public boolean isDConnectedTo(final Node node1, final Node node2,
                                  final List<Node> conditioningNodes) {
        return getGraph().isDConnectedTo(node1, node2, conditioningNodes);
    }

    public boolean isDSeparatedFrom(final Node node1, final Node node2, final List<Node> z) {
        return getGraph().isDSeparatedFrom(node1, node2, z);
    }

    public boolean possDConnectedTo(final Node node1, final Node node2, final List<Node> z) {
        return getGraph().possDConnectedTo(node1, node2, z);
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

    public boolean isExogenous(final Node node) {
        return getGraph().isExogenous(node);
    }

    public String toString() {
        return getGraph().toString();
    }

    public IKnowledge getKnowledge() {
        return this.knowledge;
    }

    private Graph getGraph() {
        return this.graph;
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





