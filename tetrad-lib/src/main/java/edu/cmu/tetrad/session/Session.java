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

package edu.cmu.tetrad.session;

import edu.cmu.tetrad.util.TetradSerializable;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * <p>Stores a directed graph over models of specific types, where the possible
 * parent relationships between the models are given in the constructors of the
 * model classes themselves. For instance, if a class Model1 has only this
 * constructor:</p> </p> <code>public Model1(Model2 x, Model3 y)... </code> </p>
 * <p>then if a SessionNode is constructed with Model.class as argument, it will
 * configure itself as a SessionNode requiring two parents, one capable of
 * implementing models of type Model2 and a second capable of implementing
 * models of type Model3. If SessionNodes capable of implementing models of
 * types Model2 and Model3 are available as parents of Model1, a new object of
 * type Model1 can be constructed using them. If Model1 has more than one
 * constructor, then there is more than one possible set of parents that can be
 * used to construct it. SessionNodes can also support more than one possible
 * type of model. A SessionNode, for instance, can support the construction of
 * graphs in general, even if different graphs are implemented using different
 * classes. The SessionNode can keep track of what its parents are and therefore
 * which of its possible models it's capable of constructing. </p> <p>The
 * Session itself keeps track of which nodes are in the session and manages
 * adding and removing nodes. Nodes that are added to the session must be
 * freshly constructed. This constraint eliminates a number of problems that
 * might otherwise exist if interconnected SessionNodes were permitted to
 * participate in more than one Session. If the addNode method is called with a
 * node that is not in the freshly constructed state (either because it was
 * actually just constructed or because the <code>reset</code> method was just
 * called on the node), an IllegalArgumentException is thrown./p> </p> <p>When a
 * node is removed from a session, all of its connections to other objects are
 * eliminated and its models destroyed. This has consequences for other objects,
 * since destroying the model of a session node may result in the destruction of
 * models downstream and the elimination of parent/child relationships between
 * nodes is mutual.</p> </p> <p>The Session organizes events coming from the
 * nodes in the session so that a listener to the Session receives all events
 * from the Session. This is convenience service so that listeners do not need
 * to pay attention to all of the different nodes in the session individually.
 * See <code>SessionEvent</code> for the types of events that are sent.</p> </p>
 * <p>It is intended for the Session to be serializable. For the Session and
 * SessionNode classes, this can be checked directly in unit tests. For the
 * various models that the Session can construct, this has to be tested
 * separately.</p>
 *
 * @author Joseph Ramsey
 * @see SessionNode
 * @see SessionListener
 * @see SessionAdapter
 * @see SessionEvent
 */
public final class Session implements TetradSerializable {
    static final long serialVersionUID = 23L;

    /**
     * The name of the session.
     *
     * @serial Can't be null.
     */
    private String name;

    /**
     * The session nodes, stored as a Set of nodes.
     *
     * @serial Can't be null.
     */
    private List<SessionNode> nodes = new LinkedList<SessionNode>();

    /**
     * Notes when the model has changed. Should be false at time of
     * deserialization.
     */
    private transient boolean sessionChanged = true;

    /**
     * True iff the session is new. Should be false at time of deserialization.
     */
    private transient boolean newSession = true;

    /**
     * Convenience class for firing SessionEvents.
     */
    private transient SessionSupport sessionSupport;

    /**
     * Handles incoming session events, basically by redirecting to any
     * listeners of this session.
     */
    private transient SessionHandler sessionHandler;

    //===========================CONSTRUCTORS=============================//

    /**
     * Constructs a new session with the given name. (The name cannot be null.)
     */
    public Session(String name) {
        setName(name);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static Session serializableInstance() {
        return new Session("X");
    }

    //===========================PUBLIC METHODS===========================//

    /**
     * Sets the name.
     */
    public final void setName(String name) {
        if (name == null) {
            throw new NullPointerException("Name must not be null.");
        }

        this.name = name;
    }

    /**
     * Gets the name.
     */
    public String getName() {
        return this.name;
    }

    /**
     * <p>Adds the given node to the session, provided the node is in a freshly
     * created state.</p>
     *
     * @throws NullPointerException     if the node is null.
     * @throws IllegalArgumentException if the node is not in a freshly created
     *                                  state. There are two ways to put a
     *                                  SessionNode into a freshly created
     *                                  state. One is to freshly create it,
     *                                  using one of the constructors. The other
     *                                  was is to call the <code>reset</code>
     *                                  method on the SessionNode.
     * @see edu.cmu.tetrad.session.SessionNode#isFreshlyCreated
     * @see edu.cmu.tetrad.session.SessionNode#resetToFreshlyCreated
     */
    public void addNode(SessionNode node) {
        if (node == null) {
            throw new NullPointerException("Node cannot be null.");
        }

        if (!node.isFreshlyCreated()) {
            throw new IllegalArgumentException("Node must be freshly " +
                    "created in order to be added to a session: " + node);
        }

        // Causing templates not to work sometimes. Unnecessary. jdramsey 6/5/2015
//        if (existsNodeByName(node.getDisplayName())) {
//            throw new IllegalArgumentException(
//                    "Attempt to add node to the session with duplicate name: " +
//                            node.getDisplayName());
//        }

        this.nodes.add(node);
        node.addSessionListener(getSessionHandler());
        getSessionSupport().fireNodeAdded(node);
    }

    /**
     * Adds a list of nodes to the session. Each item in the list must be a
     * SessionNode, and none of them may have a name that already exists in the
     * session. Upon being added to the session, if any node has a parent that
     * is not in the list, the parent is removed, the node's model is destroyed,
     * and any models downstream are destroyed as well. Any children not in the
     * list are removed. Also, any listeners that are not SessionNodes are
     * removed from each node.
     */
    public void addNodeList(List<SessionNode> nodes) {

        if (nodes == null) {
            throw new NullPointerException(
                    "The list of nodes must not be " + "null.");
        }

        // Verify that all of the nodes are SessionNodes.
        for (int i = 0; i < nodes.size(); i++) {
            if (nodes.get(i) == null) {
                throw new IllegalArgumentException(
                        "The object at index " + i + " is null.");
            }
        }

        // Check to make sure none of the nodes has a name already in the
        // session.
        for (SessionNode node : nodes) {
            if (existsNodeByName(node.getDisplayName())) {
                throw new IllegalArgumentException(
                        "Attempt to add node to the " +
                                "session with duplicate " + "name: " +
                                node.getDisplayName());
            }
        }

        // Add the nodes, removing any parents or children not in the list.
        for (int i = 0; i < nodes.size(); i++) {
            SessionNode node = nodes.get(i);
            node.restrictConnectionsToList(nodes);
            node.restrictListenersToSessionNodes();
            this.nodes.add(node);
            node.addSessionListener(getSessionHandler());
            getSessionSupport().fireNodeAdded(node);
        }
    }

    /**
     * <p>Removes the given node from the session, removing any connectivity the
     * node might have to other objects.</p>
     *
     * @param node the SessionNode to be removed.
     * @throws IllegalArgumentException if the specified node is not in the
     *                                  session.
     * @see edu.cmu.tetrad.session.SessionNode#resetToFreshlyCreated
     */
    public void removeNode(SessionNode node) {
        if (nodes.contains(node)) {
            node.resetToFreshlyCreated();
            nodes.remove(node);
            node.removeSessionListener(getSessionHandler());
            getSessionSupport().fireNodeRemoved(node);
        }
        else {
            throw new IllegalArgumentException(
                    "Node doesn't exist in" + "graph: " + node);
        }
    }

    /**
     * @return the getModel set of session nodes.
     */
    public Set<SessionNode> getNodes() {
        return new HashSet<SessionNode>(nodes);
    }

    /**
     * Removes all nodes.
     */
    public void clearNodes() {

        // Use the removeNode method to make sure events are fired.
        Set<SessionNode> _nodes = new HashSet<SessionNode>(this.nodes);

        for (SessionNode node : _nodes) {
            removeNode(node);
        }
    }

    public boolean contains(SessionNode node) {
        return this.nodes.contains(node);
    }

    /**
     * Adds a session listener.
     */
    public void addSessionListener(SessionListener l) {
        getSessionSupport().addSessionListener(l);
    }

    /**
     * Removes a session listener.
     */
    public void removeSessionListener(SessionListener l) {
        getSessionSupport().removeSessionListener(l);
    }

    //=====================PACKAGE PROTECTED METHODS=====================//

    /**
     * Indirect reference to session support to avoid saving out any listeners
     * during serialization.
     */
    SessionSupport getSessionSupport() {

        if (this.sessionSupport == null) {
            this.sessionSupport = new SessionSupport(this);

            for (SessionNode node : this.nodes) {
                node.addSessionListener(getSessionHandler());
            }
        }

        return this.sessionSupport;
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

    //==========================PRIVATE METHODS===========================//

    /**
     * @return true iff a node exists in the session with the given name.
     */
    private boolean existsNodeByName(String name) {
        if (name == null) {
            return false;
        }

        for (SessionNode node : getNodes()) {
            if (name.equals(node.getDisplayName())) {
                return true;
            }
        }
        return false;
    }

    public boolean isSessionChanged() {
        return sessionChanged;
    }

    public void setSessionChanged(boolean sessionChanged) {
        this.sessionChanged = sessionChanged;
    }

    public boolean isNewSession() {
        return newSession;
    }

    public void setNewSession(boolean newSession) {
        this.newSession = newSession;
    }

    //=========================== MEMBER CLASSES =========================//

    /**
     * Handles <code>SessionEvent</code>s. Hides the handling of these from the
     * API.
     */
    private class SessionHandler extends SessionAdapter {

        /**
         * This method is called when a node is added.
         */
        public void nodeAdded(SessionEvent event) {
            getSessionSupport().fireSessionEvent(event);
            setSessionChanged(true);
        }

        /**
         * This method is called when a node is removed.
         */
        public void nodeRemoved(SessionEvent event) {
            getSessionSupport().fireSessionEvent(event);
            setSessionChanged(true);
        }

        /**
         * This method is called when a parent is added.
         */
        public void parentAdded(SessionEvent event) {
            getSessionSupport().fireSessionEvent(event);
            setSessionChanged(true);
        }

        /**
         * This method is called when a parent is removed.
         */
        public void parentRemoved(SessionEvent event) {
            getSessionSupport().fireSessionEvent(event);
            setSessionChanged(true);
        }

        /**
         * This method is called when a model is created for a node.
         */
        public void modelCreated(SessionEvent event) {
            getSessionSupport().fireSessionEvent(event);
            setSessionChanged(true);
        }

        /**
         * This method is called when a model is destroyed for a node.
         */
        public void modelDestroyed(SessionEvent event) {
            getSessionSupport().fireSessionEvent(event);
            setSessionChanged(true);
        }

        /**
         * Relays addingEdge events up the chain, without changing their
         * source.
         */
        public void addingEdge(SessionEvent event) {
            getSessionSupport().fireSessionEvent(event, false);
        }
    }

    public boolean isEmpty() {
        return nodes.isEmpty();
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

        if (name == null) {
            throw new NullPointerException();
        }

        if (nodes == null) {
            throw new NullPointerException();
        }

        sessionChanged = false;
        newSession = false;
    }
}





