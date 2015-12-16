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

import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.session.Session;
import edu.cmu.tetrad.session.SessionAdapter;
import edu.cmu.tetrad.session.SessionEvent;
import edu.cmu.tetrad.session.SessionNode;
import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetrad.util.TetradSerializableUtils;
import edu.cmu.tetradapp.util.SessionWrapperIndirectRef;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.*;
import java.util.List;

/**
 * Wraps a Session as a Graph so that an AbstractWorkbench can be used to edit
 * it.
 *
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 * @see edu.cmu.tetrad.session.Session
 * @see edu.cmu.tetrad.graph.Graph
 */
public class SessionWrapper implements Graph, SessionWrapperIndirectRef {
    static final long serialVersionUID = 23L;

    /**
     * The session being wrapped.
     *
     * @serial Cannot be null.
     */
    private Session session;

    /**
     * The set of SessionNodeWrappers.
     *
     * @serial Cannot be null.
     */
    private Set<Node> sessionNodeWrappers = new HashSet<Node>();

    /**
     * The set of SessionEdges.
     *
     * @serial Cannot be null.
     */
    private Set<Edge> sessionEdges = new HashSet<Edge>();

    /**
     * The property change support.
     */
    private transient PropertyChangeSupport propertyChangeSupport;

    /**
     * Handles incoming session events, basically by redirecting to any
     * listeners of this session.
     */
    private transient SessionHandler sessionHandler;
    private boolean highlighted = false;

    //==========================CONSTRUCTORS=======================//

    /**
     * Constructs a new session with the given name.
     */
    public SessionWrapper(Session session) {
        if (session == null) {
            throw new NullPointerException("Session must not be null.");
        }
        this.session = session;
        this.session.addSessionListener(getSessionHandler());
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static SessionWrapper serializableInstance() {
        return new SessionWrapper(Session.serializableInstance());
    }

    //==================PUBLIC METHODS IMPLEMENTING Graph===========//

    /**
     * Adds an edge to the workbench (cast as indicated) and fires a
     * PropertyChangeEvent, property "edgeAdded," with the new edge as the
     * newValue. The nodes connected by the edge must both be
     * SessionNodeWrappers that already lie in the workbench.
     *
     * @param edge the edge to be added.
     * @return true if the edge was added, false if not.
     */
    public boolean addEdge(Edge edge) {
        SessionNodeWrapper from =
                (SessionNodeWrapper) Edges.getDirectedEdgeTail(edge);
        SessionNodeWrapper to =
                (SessionNodeWrapper) Edges.getDirectedEdgeHead(edge);
        SessionNode parent = from.getSessionNode();
        SessionNode child = to.getSessionNode();

        boolean added = child.addParent2(parent);
//        boolean added = child.addParent(parent);

        if (added) {
            this.sessionEdges.add(edge);
            getPropertyChangeSupport().firePropertyChange("edgeAdded", null,
                    edge);
            return true;
        }
        else {
            return false;
        }
    }

    /**
     * Adds a PropertyChangeListener to the workbench.
     *
     * @param e the property change listener.
     */
    public void addPropertyChangeListener(PropertyChangeListener e) {
        getPropertyChangeSupport().addPropertyChangeListener(e);
    }

    /**
     * Adds a node to the workbench and fires a PropertyChangeEvent for property
     * "nodeAdded" with the new node as the new value.
     *
     * @param node the node to be added.
     * @return true if nodes were added, false if not.
     */
    public boolean addNode(Node node) {
        SessionNodeWrapper wrapper = (SessionNodeWrapper) node;
        SessionNode sessionNode = wrapper.getSessionNode();

        try {
            this.session.addNode(sessionNode);
            this.sessionNodeWrappers.add(node);
            getPropertyChangeSupport().firePropertyChange("nodeAdded", null,
                    node);
            return true;
        }
        catch (IllegalArgumentException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Pastes a list of session elements (SessionNodeWrappers and SessionEdges)
     * into the workbench.
     */
    public void pasteSubsession(List sessionElements, Point upperLeft) {

        // Extract the SessionNodes from the SessionNodeWrappers
        // and pass the list of them to the Session.  Choose a unique
        // name for each of the session wrappers.
        List<SessionNode> sessionNodes = new ArrayList<SessionNode>();
        List<SessionNodeWrapper> sessionNodeWrappers =
                new ArrayList<SessionNodeWrapper>();
        List<Edge> sessionEdges = new ArrayList<Edge>();

        Point oldUpperLeft = EditorUtils.getTopLeftPoint(sessionElements);
        int deltaX = upperLeft.x - oldUpperLeft.x;
        int deltaY = upperLeft.y - oldUpperLeft.y;

        for (Object sessionElement : sessionElements) {
            if (sessionElement instanceof SessionNodeWrapper) {
                SessionNodeWrapper wrapper =
                        (SessionNodeWrapper) sessionElement;
                sessionNodeWrappers.add(wrapper);
                adjustNameAndPosition(wrapper, sessionNodeWrappers, deltaX,
                        deltaY);
                SessionNode sessionNode = wrapper.getSessionNode();
                sessionNodes.add(sessionNode);
            }
            else if (sessionElement instanceof Edge) {
                sessionEdges.add((Edge) sessionElement);
            }
            else {
                throw new IllegalArgumentException("The list of session " +
                        "elements should contain only SessionNodeWrappers " +
                        "and SessionEdges: " + sessionElement);
            }
        }

        // KEY STEP: Add the session nodes to the session.
        try {
            this.session.addNodeList(sessionNodes);
        }
        catch (Exception e) {
            throw new RuntimeException("There was an error when trying to " +
                    "add session nodes to the session.", e);
        }

        // If that worked, go ahead and put the session node wrappers and
        // session edges into the main data structures and throw events
        // for everything.
        this.sessionNodeWrappers.addAll(sessionNodeWrappers);
        this.sessionEdges.addAll(sessionEdges);

        for (Object sessionNodeWrapper : sessionNodeWrappers) {
            Node node = (Node) sessionNodeWrapper;
            getPropertyChangeSupport().firePropertyChange("nodeAdded", null,
                    node);
        }

        for (Object sessionEdge : sessionEdges) {
            Edge edge = (Edge) sessionEdge;
            getPropertyChangeSupport().firePropertyChange("edgeAdded", null,
                    edge);
        }
    }

    /**
     * Indirect reference to session handler to avoid saving out listeners
     * during serialization.
     */
    SessionHandler getSessionHandler() {
        if (this.sessionHandler == null) {
            this.sessionHandler = new SessionHandler();
        }

        return this.sessionHandler;
    }

    /**
     * Adjusts the name to avoid name conflicts in the new session and, if the
     * name is adjusted, adjusts the position so the user can see the two
     * nodes.
     *
     * @param wrapper             The wrapper which is being adjusted
     * @param sessionNodeWrappers a list of wrappers that the name might
     * @param deltaX              the shift in x
     * @param deltaY              the shift in y.
     */
    private void adjustNameAndPosition(SessionNodeWrapper wrapper,
            List sessionNodeWrappers, int deltaX, int deltaY) {
        String originalName = wrapper.getSessionName();
        String base = extractBase(originalName);
        String uniqueName = nextUniqueName(base, sessionNodeWrappers);

        if (!uniqueName.equals(originalName)) {
            wrapper.setSessionName(uniqueName);
            wrapper.setCenterX(wrapper.getCenterX() + deltaX);
            wrapper.setCenterY(wrapper.getCenterY() + deltaY);
        }
    }

    /**
     * @return the substring of <code>name</code> up to but not including a
     * contiguous string of digits at the end. For example, given "Graph123"
     */
    private String extractBase(String name) {

        if (name == null) {
            throw new NullPointerException("Name must not be null.");
        }

        StringBuilder buffer = new StringBuilder(name);

        for (int i = buffer.length() - 1; i >= 0; i--) {
            if (!Character.isDigit(buffer.charAt(i))) {
                return buffer.substring(0, i + 1);
            }
        }

        return "Node";
    }

    /**
     * @param base                the string base of the name--for example,
     *                            "Graph".
     * @param sessionNodeWrappers list of wrappers with names that cannot be the
     *                            next string in the sequence.
     * @return the next string in the sequence--for example, "Graph1".
     */
    private String nextUniqueName(String base, List sessionNodeWrappers) {

        if (base == null) {
            throw new NullPointerException("Base name must be non-null.");
        }

        int i = 0;    // Sequence 1, 2, 3, ...
        String name;

        loop:
        while (true) {
            i++;

            name = base + i;

            Iterator iterator = this.sessionNodeWrappers.iterator();
            for (Iterator j = iterator; j.hasNext();) {
                SessionNodeWrapper wrapper = (SessionNodeWrapper) j.next();

                if (wrapper.getSessionName().equals(name)) {
                    continue loop;
                }
            }

            iterator = sessionNodeWrappers.iterator();
            for (Iterator j = iterator; j.hasNext();) {
                SessionNodeWrapper wrapper = (SessionNodeWrapper) j.next();

                if (wrapper.getSessionName().equals(name)) {
                    continue loop;
                }
            }

            break;
        }
        return base + i;
    }

    /**
     * @return true just in case the given object is this object.
     */
    public boolean equals(Object o) {
        if (o == null) {
            return false;
        }

        return o == this;
    }

    /**
     * @return the list of edges in the workbench.  No particular ordering of
     * the edges in the list is guaranteed.
     */
    public Set<Edge> getEdges() {
        return new HashSet<Edge>(sessionEdges);
    }

    public Edge getEdge(Node node1, Node node2) {
        return null;
    }

    public Edge getDirectedEdge(Node node1, Node node2) {
        return null;                
    }

    /**
     * Determines whether this workbench contains the given edge.
     *
     * @param edge the edge to check.
     * @return true iff the workbench contain 'edge'.
     */
    public boolean containsEdge(Edge edge) {
        return sessionEdges.contains(edge);
    }

    /**
     * Determines whether this workbench contains the given node.
     */
    public boolean containsNode(Node node) {
        return sessionNodeWrappers.contains(node);
    }

    /**
     * @return the list of edges connected to a particular node. No particular
     * ordering of the edges in the list is guaranteed.
     */
    public List<Edge> getEdges(Node node) {
        List<Edge> edgeList = new LinkedList<Edge>();

        for (Edge edge : sessionEdges) {
            if ((edge.getNode1() == node) || (edge.getNode2() == node)) {
                edgeList.add(edge);
            }
        }

        return edgeList;
    }

    /**
     * @return the node with the given string name.  In case of accidental
     * duplicates, the first node encountered with the given name is returned.
     * In case no node exists with the given name, null is returned.
     */
    public Node getNode(String name) {
        for (Node sessionNodeWrapper : sessionNodeWrappers) {
            SessionNodeWrapper wrapper =
                    (SessionNodeWrapper) sessionNodeWrapper;

            if (wrapper.getSessionName().equals(name)) {
                return wrapper;
            }
        }

        return null;
    }

    /**
     * @return the number of nodes in the workbench.
     */
    public int getNumNodes() {
        return sessionNodeWrappers.size();
    }

    /**
     * @return the number of edges in the (entire) workbench.
     */
    public int getNumEdges() {
        return sessionEdges.size();
    }

    /**
     * @return the number of edges in the workbench which are connected to a
     * particular node.
     *
     * @param node the node in question
     */
    public int getNumEdges(Node node) {

        Set<Edge> edgeSet = new HashSet<Edge>();

        for (Edge edge : sessionEdges) {
            if ((edge.getNode1() == node) || (edge.getNode2() == node)) {
                edgeSet.add(edge);
            }
        }

        return edgeSet.size();
    }

    public List<Node> getNodes() {
        return new ArrayList<Node>(sessionNodeWrappers);
    }

    /**
     * @return the list of graph constraints for this graph.
     */
    public List<GraphConstraint> getGraphConstraints() {
        return new LinkedList<GraphConstraint>();
    }

    /**
     * @return true iff graph constraints will be checked for future graph
     * modifications.
     */
    public boolean isGraphConstraintsChecked() {
        return false;
    }

    /**
     * Set whether graph constraints will be checked for future graph
     * modifications.
     */
    public void setGraphConstraintsChecked(boolean checked) {
        throw new UnsupportedOperationException();
    }

    /**
     * Removes an edge from the workbench.
     */
    public boolean removeEdge(Edge edge) {
        if (sessionEdges.contains(edge)) {
            SessionNodeWrapper nodeAWrapper =
                    (SessionNodeWrapper) edge.getNode1();
            SessionNodeWrapper nodeBWrapper =
                    (SessionNodeWrapper) edge.getNode2();
            SessionNode nodeA = nodeAWrapper.getSessionNode();
            SessionNode nodeB = nodeBWrapper.getSessionNode();
            boolean removed = nodeB.removeParent(nodeA);

            if (removed) {
                sessionEdges.remove(edge);
                getPropertyChangeSupport().firePropertyChange("edgeRemoved",
                        edge, null);

                return true;
            }
        }

        return false;
    }

    /**
     * Removes the edge connecting the two given nodes, provided there is
     * exactly one such edge.
     */
    public boolean removeEdge(Node node1, Node node2) {
        return false;
    }

    /**
     * Removes all nodes (and therefore all edges) from the workbench.
     */
    public void clear() {
        throw new UnsupportedOperationException();
    }

    /**
     * Removes a property change listener from the workbench.
     *
     * @param e the property change listener.
     */
    public void removePropertyChangeListener(PropertyChangeListener e) {
        getPropertyChangeSupport().removePropertyChangeListener(e);
    }

    /**
     * Removes a node from the workbench.
     *
     * @param node the node to be removed.
     * @return true if the node was removed, false if not.
     */
    public boolean removeNode(Node node) {
        if (sessionNodeWrappers.contains(node)) {
            for (Edge edge : getEdges(node)) {
                removeEdge(edge);
            }

            SessionNodeWrapper wrapper = (SessionNodeWrapper) node;
            SessionNode sessionNode = wrapper.getSessionNode();

            try {
                this.session.removeNode(sessionNode);
                sessionNodeWrappers.remove(wrapper);
                getPropertyChangeSupport().firePropertyChange("nodeRemoved",
                        node, null);

                return true;
            }
            catch (IllegalArgumentException e) {
                return false;
            }
        }

        return false;
    }

    /**
     * Iterates through the collection and removes any permissible nodes found.
     * The order in which nodes are removed is the order in which they are
     * presented in the iterator.
     *
     * @param newNodes the Collection of nodes.
     * @return true if nodes were added, false if not.
     */
    public boolean removeNodes(List newNodes) {
        throw new UnsupportedOperationException();
    }

    /**
     * Iterates through the collection and removes any permissible edges found.
     * The order in which edges are added is the order in which they are
     * presented in the iterator.
     *
     * @param edges the Collection of edges.
     * @return true if edges were added, false if not.
     */
    public boolean removeEdges(Collection<Edge> edges) {

        boolean removed = false;

        for (Edge edge : edges) {
            removed = removed || removeEdge(edge);
        }

        return removed;
    }

    /**
     * Removes all edges connecting node A to node B.  In most cases, this will
     * remove at most one edge, but since multiple edges are permitted in some
     * workbench implementations, the number will in some cases be greater than
     * one.
     */
    public boolean removeEdges(Node nodeA, Node nodeB) {
        boolean removed = false;

        for (Edge edge : getEdges()) {
            if ((edge.getNode1() == nodeA) && (edge.getNode2() == nodeB)) {
                removed = removed || removeEdge(edge);
            }
        }

        return true;
    }

    /**
     * @return the endpoint along the edge from node to node2 at the node2 end.
     */
    public Endpoint getEndpoint(Node node1, Node node2) {
        return getEdge(node1, node2).getProximalEndpoint(node2);
    }

    /**
     * Sets the endpoint type at the 'to' end of the edge from 'from' to 'to' to
     * the given endpoint.
     *
     * @throws UnsupportedOperationException since this graph may contains only
     *                                       directed edges.
     */
    public boolean setEndpoint(Node from, Node to, Endpoint endPoint) {
        throw new UnsupportedOperationException();
    }

    /**
     * @return a string representation of the workbench.
     */
    public String toString() {
        return "Wrapper for " + this.session.toString();
    }

    public void transferNodesAndEdges(Graph graph)
            throws IllegalArgumentException {
        throw new UnsupportedOperationException();
    }

    public Set<Triple> getAmbiguousTriples() {
        throw new UnsupportedOperationException();
    }

    public Set<Triple> getUnderLines() {
        throw new UnsupportedOperationException();
    }

    public Set<Triple> getDottedUnderlines() {
        throw new UnsupportedOperationException();
    }

    /**
     * States whether x-y-x is an underline triple or not.
     */
    public boolean isAmbiguousTriple(Node x, Node y, Node z) {
        throw new UnsupportedOperationException();
    }

    /**
     * States whether x-y-x is an underline triple or not.
     */
    public boolean isUnderlineTriple(Node x, Node y, Node z) {
        throw new UnsupportedOperationException();
    }

    /**
     * States whether x-y-x is an underline triple or not.
     */
    public boolean isDottedUnderlineTriple(Node x, Node y, Node z) {
        throw new UnsupportedOperationException();
    }

    public void addAmbiguousTriple(Node x, Node y, Node z) {
        throw new UnsupportedOperationException();
    }

    public void addUnderlineTriple(Node x, Node y, Node z) {
        throw new UnsupportedOperationException();
    }

    public void addDottedUnderlineTriple(Node x, Node y, Node z) {
        throw new UnsupportedOperationException();
    }

    public void removeAmbiguousTriple(Node x, Node y, Node z) {
        throw new UnsupportedOperationException();
    }

    public void removeUnderlineTriple(Node x, Node y, Node z) {
        throw new UnsupportedOperationException();
    }

    public void removeDottedUnderlineTriple(Node x, Node y, Node z) {
        throw new UnsupportedOperationException();
    }


    public void setAmbiguousTriples(Set<Triple> triples) {
        throw new UnsupportedOperationException();
    }

    public void setUnderLineTriples(Set<Triple> triples) {
        throw new UnsupportedOperationException();
    }


    public void setDottedUnderLineTriples(Set<Triple> triples) {
        throw new UnsupportedOperationException();                
    }

    public List<Node> getCausalOrdering() {
        throw new UnsupportedOperationException();
    }

    public List<String> getNodeNames() {
        throw new UnsupportedOperationException();
    }

    public boolean existsInducingPath(Node node1, Node node2) {
        throw new UnsupportedOperationException();
    }

    public boolean addGraphConstraint(GraphConstraint gc) {
        throw new UnsupportedOperationException();
    }

    public Graph subgraph(List nodes) {
        throw new UnsupportedOperationException();
    }

    //** ***************OTHER PUBLIC METHODS ******************** */

    /**
     * @return a reference to the session being edited.
     */
    public Session getSession() {
        return this.session;
    }

    /**
     * @return the name of the session. The name cannot be null.
     */
    public String getName() {
        return this.session.getName();
    }

    /**
     * Sets the name of the session. The name cannot be null.
     */
    public void setName(String name) {
        String oldName = this.session.getName();
        this.session.setName(name);
        propertyChangeSupport.firePropertyChange("name", oldName,
                this.session.getName());
    }

    //======================PRIVATE METHODS=========================//

    /**
     * @return the property change support.
     */
    private PropertyChangeSupport getPropertyChangeSupport() {
        if (this.propertyChangeSupport == null) {
            this.propertyChangeSupport = new PropertyChangeSupport(this);
        }

        return this.propertyChangeSupport;
    }

    /**
     * Handles <code>SessionEvent</code>s. Hides the handling of these from the
     * API.
     */
    private class SessionHandler extends SessionAdapter {

        /**
         * Allows the user to verify that an edge added to a node that already
         * has a model in it is OK.
         */
        public void addingEdge(SessionEvent event) {
            String message =
                    "Child node already created. If you add this edge,\n" +
                            "the content of the child node will be made\n" +
                            "consistent with the parent.";

            int ret = JOptionPane.showConfirmDialog(
                    JOptionUtils.centeringComp(), message, "Warning",
                    JOptionPane.OK_CANCEL_OPTION);

            if (ret == JOptionPane.CANCEL_OPTION) {
                SessionNode sessionNode = (SessionNode) event.getSource();
                sessionNode.setNextEdgeAddAllowed(false);
            }
        }
    }

    /**
     * @return the edges connecting node1 and node2.
     */
    public List<Edge> getEdges(Node node1, Node node2) {
        throw new UnsupportedOperationException();
    }

    // Unused methods from Graph

    /**
     * Adds a directed edge --&gt; to the graph.
     */
    public boolean addDirectedEdge(Node node1, Node node2) {
        throw new UnsupportedOperationException();
    }

    /**
     * Adds an undirected edge --- to the graph.
     */
    public boolean addUndirectedEdge(Node node1, Node node2) {
        throw new UnsupportedOperationException();
    }

    /**
     * Adds an nondirected edges o-o to the graph.
     */
    public boolean addNondirectedEdge(Node node1, Node node2) {
        throw new UnsupportedOperationException();
    }

    /**
     * Adds a bidirected edges &lt;-&gt; to the graph.
     */
    public boolean addBidirectedEdge(Node node1, Node node2) {
        throw new UnsupportedOperationException();
    }

    /**
     * Adds a partially oriented edge o-&gt; to the graph.
     */
    public boolean addPartiallyOrientedEdge(Node node1, Node node2) {
        throw new UnsupportedOperationException();
    }

    /**
     * @return true iff there is a directed cycle in the graph.
     */
    public boolean existsDirectedCycle() {
        throw new UnsupportedOperationException();
    }

    public boolean isDirectedFromTo(Node node1, Node node2) {
        throw new UnsupportedOperationException();
    }

    public boolean isUndirectedFromTo(Node node1, Node node2) {
        throw new UnsupportedOperationException();
    }

    public boolean defVisible(Edge edge) {
        throw new UnsupportedOperationException();
    }

    /**
     * @return true iff there is a directed path from node1 to node2 in the
     * graph.
     */
    public boolean existsDirectedPathFromTo(Node node1, Node node2) {
        throw new UnsupportedOperationException();
    }

    public boolean existsUndirectedPathFromTo(Node node1, Node node2) {
        throw new UnsupportedOperationException();
    }

    public boolean existsSemiDirectedPathFromTo(Node node1, Set nodes2) {
        throw new UnsupportedOperationException();
    }

    public boolean existsSemiDirectedPathFromTo(Node node1, Node node2) {
        throw new UnsupportedOperationException();
    }

    /**
     * @return true iff a trek exists between two nodes in the graph.  A trek
     * exists if there is a directed path between the two nodes or else, for
     * some third node in the graph, there is a path to each of the two nodes in
     * question.
     */
    public boolean existsTrek(Node node1, Node node2) {
        throw new UnsupportedOperationException();
    }

    /**
     * @return the list of ancestors for the given nodes.
     */
    public List<Node> getAncestors(List nodes) {
        throw new UnsupportedOperationException();
    }

    /**
     * @return the Collection of children for a node.
     */
    public List<Node> getChildren(Node node) {
        throw new UnsupportedOperationException();
    }

    public int getConnectivity() {
        throw new UnsupportedOperationException();
    }

    public List<Node> getDescendants(List nodes) {
        throw new UnsupportedOperationException();
    }

    /**
     * @return a matrix of endpoints for the nodes in this graph, with nodes in
     * the same order as getNodes().
     */
    public Endpoint[][] getEndpointMatrix() {
        throw new UnsupportedOperationException();
    }

    /**
     * @return the list of nodes adjacent to the given node.
     */
    public List<Node> getAdjacentNodes(Node node) {
        throw new UnsupportedOperationException();
    }

    /**
     * @return the number of arrow endpoint adjacent to an edge.
     */
    public int getIndegree(Node node) {
        throw new UnsupportedOperationException();
    }

    /**
     * @return the number of null endpoints adjacent to an edge.
     */
    public int getOutdegree(Node node) {
        throw new UnsupportedOperationException();
    }

    /**
     * @return the list of parents for a node.
     */
    public List<Node> getParents(Node node) {
        throw new UnsupportedOperationException();
    }

    /**
     * Determines whether one node is an ancestor of another.
     */
    public boolean isAncestorOf(Node node1, Node node2) {
        throw new UnsupportedOperationException();
    }

    public boolean possibleAncestor(Node node1, Node node2) {
        throw new UnsupportedOperationException();
    }

    /**
     * @return true iff node1 is adjacent to node2 in the graph.
     */
    public boolean isAdjacentTo(Node node1, Node node2) {
        throw new UnsupportedOperationException();
    }

    /**
     * @return true iff node1 is a child of node2 in the graph.
     */
    public boolean isChildOf(Node node1, Node node2) {
        throw new UnsupportedOperationException();
    }

    /**
     * @return true iff node1 is a (non-proper) descendant of node2.
     */
    public boolean isDescendentOf(Node node1, Node node2) {
        throw new UnsupportedOperationException();
    }

    public boolean defNonDescendent(Node node1, Node node2) {
        throw new UnsupportedOperationException();
    }

    public boolean isDefNoncollider(Node node1, Node node2, Node node3) {
        throw new UnsupportedOperationException();
    }

    public boolean isDefCollider(Node node1, Node node2, Node node3) {
        throw new UnsupportedOperationException();
    }

    /**
     * Determines whether one node is d-separated from another. According to
     * Spirtes, Richardson &amp; Meek, two nodes are d- connected given some
     * conditioning set Z if there is an acyclic undirected path U between them,
     * such that every collider on U is an ancestor of some element in Z and
     * every non-collider on U is not in Z.  Two elements are d-separated just
     * in case they are not d-separated.  A collider is a node which two edges
     * hold in common for which the endpoints leading into the node are both
     * arrow endpoints.
     */
    public boolean isDConnectedTo(Node node1, Node node2, List z) {
        throw new UnsupportedOperationException();
    }

    /**
     * Determines whether one node is d-separated from another. According to
     * Spirtes, Richardson &amp; Meek, two nodes are d- connected given some
     * conditioning set Z if there is an acyclic undirected path U between them,
     * such that every collider on U is an ancestor of some element in Z and
     * every non-collider on U is not in Z.  Two elements are d-separated just
     * in case they are not d-separated.  A collider is a node which two edges
     * hold in common for which the endpoints leading into the node are both
     * arrow endpoints.
     */
    public boolean isDSeparatedFrom(Node node1, Node node2, List z) {
        throw new UnsupportedOperationException();
    }

    public boolean possDConnectedTo(Node node1, Node node2, List z) {
        throw new UnsupportedOperationException();
    }

    /**
     * @return true iff the given node is exogenous in the graph.
     */
    public boolean isExogenous(Node node) {
        throw new UnsupportedOperationException();
    }

    /**
     * Determines whether one node is a parent of another.
     */
    public boolean isParentOf(Node node1, Node node2) {
        throw new UnsupportedOperationException();
    }

    /**
     * Determines whether one node is a proper ancestor of another.
     */
    public boolean isProperAncestorOf(Node node1, Node node2) {
        throw new UnsupportedOperationException();
    }

    /**
     * Determines whether one node is a proper decendent of another.
     */
    public boolean isProperDescendentOf(Node node1, Node node2) {
        throw new UnsupportedOperationException();
    }

    /**
     * Nodes adjacent to the given node with the given proximal endpoint.
     */
    public List<Node> getNodesInTo(Node node, Endpoint n) {
        throw new UnsupportedOperationException();
    }

    /**
     * Nodes adjacent to the given node with the given distal endpoint.
     */
    public List<Node> getNodesOutTo(Node node, Endpoint n) {
        throw new UnsupportedOperationException();
    }

    /**
     * Removes all edges from the graph and fully connects it using #-# edges,
     * where # is the given endpoint.
     */
    public void fullyConnect(Endpoint endpoint) {
        throw new UnsupportedOperationException();
    }

    public void reorientAllWith(Endpoint endpoint) {
        throw new UnsupportedOperationException();
    }

    public void setHighlighted(Edge edge, boolean highlighted) {
        this.highlighted = highlighted;
    }

    public boolean isHighlighted(Edge edge) {
        return highlighted;
    }

    public boolean isParameterizable(Node node) {
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
    public List<Node> getSepset(Node n1, Node n2) {
        throw new UnsupportedOperationException();
    }

    public boolean isSessionChanged() {
        return this.session.isSessionChanged();
    }

    public void setSessionChanged(boolean sessionChanged) {
        this.session.setSessionChanged(sessionChanged);
    }

    public boolean isNewSession() {
        return this.session.isNewSession();
    }

    public void setNewSession(boolean newSession) {
        this.session.setNewSession(newSession);
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
     * @throws java.io.IOException
     * @throws ClassNotFoundException
     */
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        if (session == null) {
            throw new NullPointerException();
        }

        if (sessionNodeWrappers == null) {
            throw new NullPointerException();
        }

        if (sessionEdges == null) {
            throw new NullPointerException();
        }
    }
}





