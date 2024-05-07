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

package edu.cmu.tetradapp.session;

import edu.cmu.tetrad.util.MillisecondTimes;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.*;
import java.util.prefs.Preferences;

/**
 * Runs a simulation edu.cmu.tetrad.study for a session which traverses a subtree of the session graph in depth-first
 * order and executes each node encountered a specified number of times.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public final class SimulationStudy {

    /**
     * The session, nodes of which this simulation edu.cmu.tetrad.study is executing.
     */
    private final Session session;

    /**
     * Support for firing SessionEvent's.
     */
    private transient SessionSupport sessionSupport;

    /**
     * The set of nodes with models; only these should be executed.
     */
    private Set<SessionNode> nodesToExecute;

    //===========================CONSTRUCTORS==============================//

    /**
     * Constructs a new simulation edu.cmu.tetrad.study for the given session.
     *
     * @param session a {@link Session} object
     */
    public SimulationStudy(Session session) {
        if (session == null) {
            throw new NullPointerException();
        }

        this.session = session;

        // Cleanup: when session nodes are removed from the session,
        // remove their repetition numbers from the repetitions map.
        session.addSessionListener(new SessionAdapter() {
            public void nodeRemoved(SessionEvent e) {
                SessionNode node = e.getNode();
                SimulationStudy.removeRepetition(node);
            }
        });
    }

    //===========================PUBLIC METHODS============================//

    /**
     * Gets the repeition of the given node. If the repetition of a node has not been set, it is assumed to be 1.
     *
     * @param node a {@link SessionNode} object
     * @return a int
     * @see #setRepetition
     */
    public static int getRepetition(SessionNode node) {
        if (node.getRepetition() < 1) {
            node.setRepetition(1);
        }

        return node.getRepetition();
    }

    /**
     * Removes the repetition number for the given node. If it's still in the graph, its repetition will be 1.
     */
    private static void removeRepetition(SessionNode sessionNode) {
        sessionNode.setRepetition(1);
    }

    /**
     * <p>getDescendants.</p>
     *
     * @param node a {@link SessionNode} object
     * @return a {@link java.util.Set} object
     */
    public static Set getDescendants(SessionNode node) {
        HashSet<SessionNode> descendants = new HashSet<>();
        SimulationStudy.doChildClosureVisit(node, descendants);
        return descendants;
    }

    /**
     * closure under the child relation
     */
    private static void doChildClosureVisit(SessionNode node, Set<SessionNode> closure) {
        if (!closure.contains(node)) {
            closure.add(node);
            Collection<SessionNode> children = node.getChildren();

            for (SessionNode child : children) {
                SimulationStudy.doChildClosureVisit(child, closure);
            }
        }
    }

    /**
     * Sets the number of times the given node (and all of its children) will be executed each time it is encountered in
     * a depth first traversal of the tree.
     *
     * @param repetition the repetition, an integer &gt; 0.
     * @param node       a {@link SessionNode} object
     */
    public void setRepetition(SessionNode node, int repetition) {
        if (node == null) {
            throw new NullPointerException();
        }

        if (repetition <= 0) {
            throw new IllegalArgumentException(
                    "Repeat must be > 0: " + repetition);
        }

        node.setRepetition(repetition);
        getSessionSupport().fireRepetitionChanged(node);
    }

    //===========================PRIVATE METHODS===========================//

    /**
     * Executes the given node the specified number of times.
     *
     * @param sessionNode a {@link SessionNode} object
     * @param overwrite   a boolean
     * @see #getRepetition
     */
    public void execute(SessionNode sessionNode, boolean overwrite) {
        if (!this.session.contains(sessionNode)) {
            throw new IllegalArgumentException("Session node not in the " +
                                               "session: " + sessionNode.getDisplayName());
        }

        this.nodesToExecute = this.session.getNodes();
//        this.nodesToExecute = nodesWithModels();

        // Begin the execution, making sure that each node's children are
        // executed in the order of the given tier ordering.
        LinkedList<SessionNode> tierOrdering = new LinkedList<>(getTierOrdering(sessionNode));
        notifyDownstreamOfStart(sessionNode);

        final boolean doRepetition = true;
        final boolean simulation = true;

        TetradLogger.getInstance().forceLogMessage("\n\n===STARTING SIMULATION STUDY===");
        long time1 = MillisecondTimes.timeMillis();

        execute(tierOrdering, doRepetition, simulation, overwrite);

        TetradLogger.getInstance().forceLogMessage("\n\n===FINISHING SIMULATION STUDY===");
        long time2 = MillisecondTimes.timeMillis();

        System.out.println("Elapsed time = " + (time2 - time1) / 1000. + " s");
    }

    /**
     * <p>createDescendantModels.</p>
     *
     * @param sessionNode a {@link SessionNode} object
     * @param overwrite   a boolean
     * @return a boolean
     */
    public boolean createDescendantModels(SessionNode sessionNode,
                                          boolean overwrite) {
        if (!this.session.contains(sessionNode)) {
            throw new IllegalArgumentException("Session node not in the " +
                                               "session: " + sessionNode.getDisplayName());
        }

        this.nodesToExecute = this.session.getNodes();

        // Begin the execution, making sure that each node's children are
        // executed in the order of the given tier ordering.
        LinkedList<SessionNode> tierOrdering = getTierOrdering(sessionNode);

        if (sessionNode.getModel() != null) {
            tierOrdering.remove(sessionNode);
        }

        notifyDownstreamOfStart(sessionNode);
        final boolean doRepetition = false;
        final boolean simulation = true;
        return execute(tierOrdering, doRepetition, simulation, overwrite);
    }

    /**
     * Adds a session listener.
     *
     * @param l a {@link SessionListener} object
     */
    public void addSessionListener(SessionListener l) {
        getSessionSupport().addSessionListener(l);
    }

    private HashSet<SessionNode> nodesWithModels() {
        HashSet<SessionNode> nodesWithModels = new HashSet<>();

        for (SessionNode node : this.session.getNodes()) {
            if (node.getModel() != null) {
                nodesWithModels.add(node);
            }
        }

        return nodesWithModels;
    }

    /**
     * Notify session nodes (and their parameters) downstream that a new execution has begun of a simulation
     * edu.cmu.tetrad.study.
     */
    private void notifyDownstreamOfStart(SessionNode sessionNode) {
        SessionSupport sessionSupport = new SessionSupport(this);
        sessionSupport.addSessionListener(sessionNode.getSessionHandler());
        sessionSupport.fireExecutionStarted();
    }

    /**
     * Executes the given node the specified number of times. Executes the children of the node. If the node has more
     * than one child, the nodes are executed in the order of the given tier ordering. This needs to be a tier ordering
     * over all of the nodes in the graph.
     *
     * @see #getRepetition
     */
    private boolean execute(LinkedList<SessionNode> tierOrdering, boolean doRepetition,
                            boolean simulation, boolean overwrite) {
        if (tierOrdering.isEmpty()) {
            return true;
        }

        SessionNode sessionNode = tierOrdering.getFirst();

        if (!this.session.contains(sessionNode)) {
            throw new IllegalArgumentException("Session node not in the " +
                                               "session: " + sessionNode.getDisplayName());
        }

        // Only fill in nodes that were already filled in.
        if (!this.nodesToExecute.contains(sessionNode)) {
            return false;
        }


        // Assume a node is repeated n times. Each time, the node's
        // model has to be destroyed first, which causes all of the
        // node models downstream to be destroyed in cascade through
        // events. (Please don't mess with the event structure!!) Then
        // the node's model is created. Then each of the child nodes'
        // models is created in the order of the tier ordering, unless
        // the model for a particular node already exists, in which
        // cases it is not created again. (This avoids repetition.)
        // jdramsey 1/11/01
        int repetition = doRepetition ? SimulationStudy.getRepetition(sessionNode) : 1;

        Preferences.userRoot().putBoolean("errorFound", false);

        for (int i = 0; i < repetition; i++) {
            if (Preferences.userRoot().getBoolean("experimental", false) &&
                Preferences.userRoot().getBoolean("errorFound", false)) {
//                break;
            }

            if (!overwrite && sessionNode.getModel() != null) {
                return false;
            }

            sessionNode.destroyModel();

            try {

                if (repetition > 1) {
                    TetradLogger.getInstance().forceLogMessage("\nREPETITION #" + (i + 1) + " FOR "
                                                               + sessionNode.getDisplayName() + "\n");
                }

                boolean created = sessionNode.createModel(simulation);

                if (created) {
                    SessionModel source = sessionNode.getModel();
                    Map<String, String> paramSettings = new LinkedHashMap<>();
                    collectParentParamSettings(sessionNode, paramSettings);

                    if (source instanceof SimulationParamsSource) {
                        ((SimulationParamsSource) source).setAllParamSettings(paramSettings);
                    }
                }

                if (!created) {
                    return false;
                }
            } catch (RuntimeException e) {
                e.printStackTrace();
                return false;
            }

            LinkedList<SessionNode> _tierOrdering = new LinkedList<>(tierOrdering);
            _tierOrdering.removeFirst();
            boolean success =
                    execute(_tierOrdering, doRepetition, simulation, overwrite);

            if (!success) {
                return false;
            }
        }

        return true;
    }

    private void collectParentParamSettings(SessionNode sessionNode,
                                            Map<String, String> paramSettings) {
        for (SessionNode parent : sessionNode.getParents()) {
            collectParentParamSettings(parent, paramSettings);
        }

    }

    /**
     * This method returns the nodes of a digraph in such an order that as one iterates through the list, the parents of
     * each node have already been encountered in the list.
     *
     * @return a tier ordering for the nodes in this graph.
     */
    private LinkedList<SessionNode> getTierOrdering(SessionNode node) {
        Session session = this.session;
        Set<SessionNode> sessionNodes = session.getNodes();

        LinkedList<SessionNode> found = new LinkedList<>();

        // The getVariableNodes() method already returns a copy, so there's no
        // need to make a new copy.
        Set<SessionNode> notFound = new HashSet<>(sessionNodes);

        while (!notFound.isEmpty()) {
            for (Iterator<SessionNode> it = notFound.iterator(); it.hasNext(); ) {
                SessionNode sessionNode = it.next();

                if (found.containsAll(sessionNode.getParents())) {
                    found.add(sessionNode);
                    it.remove();
                }
            }
        }

        found.retainAll(SimulationStudy.getDescendants(node));
        return found;
    }

    private SessionSupport getSessionSupport() {
        if (this.sessionSupport == null) {
            this.sessionSupport = new SessionSupport(this);
        }
        return this.sessionSupport;
    }

    /**
     * <p>Getter for the field <code>session</code>.</p>
     *
     * @return a {@link Session} object
     */
    public Session getSession() {
        return this.session;
    }
}





