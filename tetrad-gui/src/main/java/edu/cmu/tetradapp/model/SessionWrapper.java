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

package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetrad.util.TetradSerializableUtils;
import edu.cmu.tetradapp.session.Session;
import edu.cmu.tetradapp.session.SessionAdapter;
import edu.cmu.tetradapp.session.SessionEvent;
import edu.cmu.tetradapp.session.SessionNode;
import edu.cmu.tetradapp.util.SessionWrapperIndirectRef;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.util.List;
import java.util.*;

/**
 * Wraps a Session as a Graph so that an AbstractWorkbench can be used to edit it.
 *
 * @author josephramsey
 * @version $Id: $Id
 * @see Session
 * @see edu.cmu.tetrad.graph.Graph
 */
public class SessionWrapper extends EdgeListGraph implements SessionWrapperIndirectRef {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The session being wrapped.
     */
    private final Session session;

    /**
     * The set of SessionNodeWrappers.
     */
    private final Set<Node> sessionNodeWrappers = new HashSet<>();

    /**
     * The set of SessionEdges.
     */
    private final Set<Edge> sessionEdges = new HashSet<>();

    /**
     * True just in case the session is highlighted.
     */
    private final boolean highlighted = false;
    /**
     * The property change support.
     */
    private transient PropertyChangeSupport propertyChangeSupport;

    /**
     * Handles incoming session events, basically by redirecting to any listeners of this session.
     */
    private transient SessionHandler sessionHandler;

    //==========================CONSTRUCTORS=======================//

    /**
     * Constructs a new session with the given name.
     *
     * @param session a {@link Session} object
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
     * @return a {@link edu.cmu.tetradapp.model.SessionWrapper} object
     * @see TetradSerializableUtils
     */
    public static SessionWrapper serializableInstance() {
        return new SessionWrapper(Session.serializableInstance());
    }

    //==================PUBLIC METHODS IMPLEMENTING Graph===========//

    /**
     * {@inheritDoc}
     * <p>
     * Adds an edge to the workbench (cast as indicated) and fires a PropertyChangeEvent, property "edgeAdded," with the
     * new edge as the newValue. The nodes connected by the edge must both be SessionNodeWrappers that already lie in
     * the workbench.
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
        } else {
            return false;
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Adds a PropertyChangeListener to the workbench.
     */
    public void addPropertyChangeListener(PropertyChangeListener e) {
        getPropertyChangeSupport().addPropertyChangeListener(e);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Adds a node to the workbench and fires a PropertyChangeEvent for property "nodeAdded" with the new node as the
     * new value.
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
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Pastes a list of session elements (SessionNodeWrappers and SessionEdges) into the workbench.
     *
     * @param sessionElements a {@link java.util.List} object
     * @param upperLeft       a {@link java.awt.Point} object
     */
    public void pasteSubsession(List sessionElements, Point upperLeft) {

        // Extract the SessionNodes from the SessionNodeWrappers
        // and pass the list of them to the Session.  Choose a unique
        // name for each of the session wrappers.
        List<SessionNode> sessionNodes = new ArrayList<>();
        List<SessionNodeWrapper> sessionNodeWrappers =
                new ArrayList<>();
        List<Edge> sessionEdges = new ArrayList<>();

        Point oldUpperLeft = EditorUtils.getTopLeftPoint(sessionElements);
        int deltaX = upperLeft.x - oldUpperLeft.x;
        int deltaY = upperLeft.y - oldUpperLeft.y;

        for (Object sessionElement : sessionElements) {
            if (sessionElement instanceof SessionNodeWrapper wrapper) {
                sessionNodeWrappers.add(wrapper);
                adjustNameAndPosition(wrapper, sessionNodeWrappers, deltaX,
                        deltaY);
                SessionNode sessionNode = wrapper.getSessionNode();
                sessionNodes.add(sessionNode);
            } else if (sessionElement instanceof Edge) {
                sessionEdges.add((Edge) sessionElement);
            } else {
                throw new IllegalArgumentException("The list of session " +
                                                   "elements should contain only SessionNodeWrappers " +
                                                   "and SessionEdges: " + sessionElement);
            }
        }

        // KEY STEP: Add the session nodes to the session.
        try {
            this.session.addNodeList(sessionNodes);
        } catch (Exception e) {
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
     * Indirect reference to session handler to avoid saving out listeners during serialization.
     */
    private SessionHandler getSessionHandler() {
        if (this.sessionHandler == null) {
            this.sessionHandler = new SessionHandler();
        }

        return this.sessionHandler;
    }

    /**
     * Adjusts the name to avoid name conflicts in the new session and, if the name is adjusted, adjusts the position so
     * the user can see the two nodes.
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
     * @return the substring of <code>name</code> up to but not including a contiguous string of digits at the end. For
     * example, given "Graph123"
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
     * @param base                the string base of the name--for example, "Graph".
     * @param sessionNodeWrappers list of wrappers with names that cannot be the next string in the sequence.
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
            for (Iterator j = iterator; j.hasNext(); ) {
                SessionNodeWrapper wrapper = (SessionNodeWrapper) j.next();

                if (wrapper.getSessionName().equals(name)) {
                    continue loop;
                }
            }

            iterator = sessionNodeWrappers.iterator();
            for (Iterator j = iterator; j.hasNext(); ) {
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
     * {@inheritDoc}
     */
    public boolean equals(Object o) {
        if (o == null) {
            return false;
        }

        return o == this;
    }

    /**
     * <p>getEdges.</p>
     *
     * @return the list of edges in the workbench.  No particular ordering of the edges in the list is guaranteed.
     */
    public Set<Edge> getEdges() {
        return new HashSet<>(this.sessionEdges);
    }

    /**
     * {@inheritDoc}
     */
    public Edge getEdge(Node node1, Node node2) {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    public Edge getDirectedEdge(Node node1, Node node2) {
        return null;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Determines whether this workbench contains the given edge.
     */
    public boolean containsEdge(Edge edge) {
        return this.sessionEdges.contains(edge);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Determines whether this workbench contains the given node.
     */
    public boolean containsNode(Node node) {
        return this.sessionNodeWrappers.contains(node);
    }

    /**
     * {@inheritDoc}
     */
    public List<Edge> getEdges(Node node) {
        List<Edge> edgeList = new ArrayList<>();

        for (Edge edge : this.sessionEdges) {
            if ((edge.getNode1() == node) || (edge.getNode2() == node)) {
                edgeList.add(edge);
            }
        }

        return edgeList;
    }

    /**
     * {@inheritDoc}
     */
    public Node getNode(String name) {
        for (Node sessionNodeWrapper : this.sessionNodeWrappers) {
            SessionNodeWrapper wrapper =
                    (SessionNodeWrapper) sessionNodeWrapper;

            if (wrapper.getSessionName().equals(name)) {
                return wrapper;
            }
        }

        return null;
    }

    /**
     * <p>getNumNodes.</p>
     *
     * @return the number of nodes in the workbench.
     */
    public int getNumNodes() {
        return this.sessionNodeWrappers.size();
    }

    /**
     * <p>getNumEdges.</p>
     *
     * @return the number of edges in the (entire) workbench.
     */
    public int getNumEdges() {
        return this.sessionEdges.size();
    }

    /**
     * {@inheritDoc}
     */
    public int getNumEdges(Node node) {

        Set<Edge> edgeSet = new HashSet<>();

        for (Edge edge : this.sessionEdges) {
            if ((edge.getNode1() == node) || (edge.getNode2() == node)) {
                edgeSet.add(edge);
            }
        }

        return edgeSet.size();
    }

    /**
     * <p>getNodes.</p>
     *
     * @return a {@link java.util.List} object
     */
    public List<Node> getNodes() {
        return new ArrayList<>(this.sessionNodeWrappers);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Removes an edge from the workbench.
     */
    public boolean removeEdge(Edge edge) {
        if (this.sessionEdges.contains(edge)) {
            SessionNodeWrapper nodeAWrapper =
                    (SessionNodeWrapper) edge.getNode1();
            SessionNodeWrapper nodeBWrapper =
                    (SessionNodeWrapper) edge.getNode2();
            SessionNode nodeA = nodeAWrapper.getSessionNode();
            SessionNode nodeB = nodeBWrapper.getSessionNode();
            boolean removed = nodeB.removeParent(nodeA);

            if (removed) {
                this.sessionEdges.remove(edge);
                getPropertyChangeSupport().firePropertyChange("edgeRemoved",
                        edge, null);

                return true;
            }
        }

        return false;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Removes the edge connecting the two given nodes, provided there is exactly one such edge.
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
     * {@inheritDoc}
     * <p>
     * Removes a node from the workbench.
     */
    public boolean removeNode(Node node) {
        if (this.sessionNodeWrappers.contains(node)) {
            for (Edge edge : getEdges(node)) {
                removeEdge(edge);
            }

            SessionNodeWrapper wrapper = (SessionNodeWrapper) node;
            SessionNode sessionNode = wrapper.getSessionNode();

            try {
                this.session.removeNode(sessionNode);
                this.sessionNodeWrappers.remove(wrapper);
                getPropertyChangeSupport().firePropertyChange("nodeRemoved",
                        node, null);

                return true;
            } catch (IllegalArgumentException e) {
                return false;
            }
        }

        return false;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Iterates through the collection and removes any permissible nodes found. The order in which nodes are removed is
     * the order in which they are presented in the iterator.
     */
    public boolean removeNodes(List newNodes) {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Iterates through the collection and removes any permissible edges found. The order in which edges are added is
     * the order in which they are presented in the iterator.
     */
    public boolean removeEdges(Collection<Edge> edges) {

        boolean removed = false;

        for (Edge edge : edges) {
            removed = removed || removeEdge(edge);
        }

        return removed;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Removes all edges connecting node A to node B.  In most cases, this will remove at most one edge, but since
     * multiple edges are permitted in some workbench implementations, the number will in some cases be greater than
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
     * {@inheritDoc}
     */
    public Endpoint getEndpoint(Node node1, Node node2) {
        return getEdge(node1, node2).getProximalEndpoint(node2);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Sets the endpoint type at the 'to' end of the edge from 'from' to 'to' to the given endpoint.
     */
    public boolean setEndpoint(Node from, Node to, Endpoint endPoint) {
        throw new UnsupportedOperationException();
    }

    /**
     * <p>toString.</p>
     *
     * @return a string representation of the workbench.
     */
    public String toString() {
        return "Wrapper for " + this.session.toString();
    }

    /**
     * {@inheritDoc}
     */
    public void transferNodesAndEdges(Graph graph)
            throws IllegalArgumentException {
        throw new UnsupportedOperationException();
    }

    /**
     * <p>getNodeNames.</p>
     *
     * @return a {@link java.util.List} object
     */
    public List<String> getNodeNames() {
        throw new UnsupportedOperationException();
    }

    /**
     * <p>existsInducingPath.</p>
     *
     * @param node1 a {@link edu.cmu.tetrad.graph.Node} object
     * @param node2 a {@link edu.cmu.tetrad.graph.Node} object
     * @return a boolean
     */
    public boolean existsInducingPath(Node node1, Node node2) {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    public Graph subgraph(List nodes) {
        throw new UnsupportedOperationException();
    }

    //** ***************OTHER PUBLIC METHODS ******************** */

    /**
     * <p>Getter for the field <code>session</code>.</p>
     *
     * @return a reference to the session being edited.
     */
    public Session getSession() {
        return this.session;
    }

    /**
     * <p>getName.</p>
     *
     * @return the name of the session. The name cannot be null.
     */
    public String getName() {
        return this.session.getName();
    }

    /**
     * Sets the name of the session. The name cannot be null.
     *
     * @param name a {@link java.lang.String} object
     */
    public void setName(String name) {
        String oldName = this.session.getName();
        this.session.setName(name);
        this.propertyChangeSupport.firePropertyChange("name", oldName,
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
     * {@inheritDoc}
     */
    public List<Edge> getEdges(Node node1, Node node2) {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    public List<Node> getParents(Node node) {
        return new ArrayList<>(((SessionNode) node).getParents());
    }

    /**
     * <p>isSessionChanged.</p>
     *
     * @return a boolean
     */
    public boolean isSessionChanged() {
        return this.session.isSessionChanged();
    }

    /**
     * <p>setSessionChanged.</p>
     *
     * @param sessionChanged a boolean
     */
    public void setSessionChanged(boolean sessionChanged) {
        this.session.setSessionChanged(sessionChanged);
    }

    /**
     * <p>isNewSession.</p>
     *
     * @return a boolean
     */
    public boolean isNewSession() {
        return this.session.isNewSession();
    }

    /**
     * <p>setNewSession.</p>
     *
     * @param newSession a boolean
     */
    public void setNewSession(boolean newSession) {
        this.session.setNewSession(newSession);
    }

    /**
     * Adds semantic checks to the default deserialization method. This method must have the standard signature for a
     * readObject method, and the body of the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from version to version. A readObject method of
     * this form may be added to any class, even if Tetrad sessions were previously saved out using a version of the
     * class that didn't include it. (That's what the "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for
     * help.
     *
     * @param s The object input stream.
     * @throws IOException            If any.
     * @throws ClassNotFoundException If any.
     */
    @Serial
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();
    }

    /**
     * Handles <code>SessionEvent</code>s. Hides the handling of these from the API.
     */
    private static class SessionHandler extends SessionAdapter {

        /**
         * Allows the user to verify that an edge added to a node that already has a model in it is OK.
         */
        public void addingEdge(SessionEvent event) {
            final String message =
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
}





