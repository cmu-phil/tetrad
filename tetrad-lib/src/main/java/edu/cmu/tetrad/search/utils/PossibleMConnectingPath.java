///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.graph.*;

import java.util.*;

/**
 * Finds possible d-connecting undirectedPaths for the IonSearch.
 * <p>
 * Not thread safe.
 *
 * @author Tyler Gibson
 * @version $Id: $Id
 */
public class PossibleMConnectingPath {

    /**
     * The pag we are searching in.
     */
    private final Graph pag;

    /**
     * The conditions.
     */
    private final Set<Node> conditions;

    /**
     * The path.
     */
    private final List<Node> path;

    private PossibleMConnectingPath(Graph p, Set<Node> conditions, List<Node> path) {
        if (p == null || conditions == null || path == null) {
            throw new NullPointerException();
        }
        this.conditions = conditions;
        this.path = path;
        this.pag = p;
    }

    //========================= Public methods ======================//

    /**
     * Finds all possible D-connection undirectedPaths as sub-graphs of the pag given at construction time from x to y
     * given z.
     *
     * @param pag a {@link edu.cmu.tetrad.graph.Graph} object
     * @param x   a {@link edu.cmu.tetrad.graph.Node} object
     * @param y   a {@link edu.cmu.tetrad.graph.Node} object
     * @param z   a {@link java.util.Collection} object
     * @return a {@link java.util.List} object
     */
    public static List<PossibleMConnectingPath> findMConnectingPaths(Graph pag, Node x, Node y, Collection<Node> z) {
        if (!pag.containsNode(x) || !pag.containsNode(y) || x.equals(y)) {
            return Collections.emptyList();
        }
        for (Node node : z) {
            if (!pag.containsNode(node)) {
                return Collections.emptyList();
            }
        }
        if (pag.isAdjacentTo(x, y)) {
            return Collections.singletonList(new PossibleMConnectingPath(pag, new HashSet<>(z), Arrays.asList(x, y)));
        }
        List<PossibleMConnectingPath> connectingPaths = new LinkedList<>();
        Set<Node> conditions = new HashSet<>(z);
        Set<Node> closure = PossibleMConnectingPath.getConditioningClosure(pag, z);
        Set<List<Node>> paths = new HashSet<>();
        PossibleMConnectingPath.findPaths(pag, paths, null, x, y, conditions, closure, new LinkedList<>());
        for (List<Node> path : paths) {
            connectingPaths.add(new PossibleMConnectingPath(pag, conditions, path));
        }
        return connectingPaths;
    }

    /**
     * Finds all possible D-connection undirectedPaths as sub-graphs of the pag given at construction time from x to y
     * given z for a particular path length.
     *
     * @param pag    a {@link edu.cmu.tetrad.graph.Graph} object
     * @param x      a {@link edu.cmu.tetrad.graph.Node} object
     * @param y      a {@link edu.cmu.tetrad.graph.Node} object
     * @param z      a {@link java.util.Collection} object
     * @param length a {@link java.lang.Integer} object
     * @return a {@link java.util.List} object
     */
    public static List<PossibleMConnectingPath> findMConnectingPathsOfLength(Graph pag, Node x, Node y, Collection<Node> z, Integer length) {
        if (!pag.containsNode(x) || !pag.containsNode(y) || x.equals(y)) {
            return Collections.emptyList();
        }
        for (Node node : z) {
            if (!pag.containsNode(node)) {
                return Collections.emptyList();
            }
        }
        if (pag.isAdjacentTo(x, y)) {
            return Collections.singletonList(new PossibleMConnectingPath(pag, new HashSet<>(z), Arrays.asList(x, y)));
        }
        List<PossibleMConnectingPath> connectingPaths = new LinkedList<>();
        Set<Node> conditions = new HashSet<>(z);
        Set<Node> closure = PossibleMConnectingPath.getConditioningClosure(pag, z);
        Set<List<Node>> paths = new HashSet<>();
        PossibleMConnectingPath.findPathsOfLength(pag, paths, null, x, y, conditions, closure, new LinkedList<>(), length);
        for (List<Node> path : paths) {
            connectingPaths.add(new PossibleMConnectingPath(pag, conditions, path));
        }
        return connectingPaths;
    }

    private static Set<Node> getConditioningClosure(Graph pag, Collection<Node> z) {
        Set<Node> closure = new HashSet<>();
        for (Node node : z) {
            PossibleMConnectingPath.doParentClosureVisit(pag, node, closure);
        }
        return closure;
    }

    /**
     * Find the closure of a conditioning set of nodes under the parent relation.
     *
     * @param node    the node in question
     * @param closure the closure of the conditioning set uner the parent relation (to be calculated recursively).
     */
    private static void doParentClosureVisit(Graph pag, Node node, Set<Node> closure) {
        if (!closure.contains(node)) {
            closure.add(node);

            for (Edge edge1 : pag.getEdges(node)) {
                Node sub = Edges.traverseReverseDirected(node, edge1);

                if (sub == null) {
                    continue;
                }

                PossibleMConnectingPath.doParentClosureVisit(pag, sub, closure);
            }
        }
    }

    /**
     * Recursive methods that finds all the undirectedPaths.
     */
    private static void findPaths(Graph pag, Set<List<Node>> paths, Node previous, Node current,
                                  Node target, Set<Node> condition, Set<Node> conditionClosure, List<Node> history) {

        // check for cycles.
        if (history.contains(current)) {
            return;
        }
        // add path if we've reached the target.
        if (current.equals(target)) {
            history.add(current);
            paths.add(history);
            return;
        }
        // recurse
        List<Node> adjacencies = pag.getAdjacentNodes(current);
        for (Node adj : adjacencies) {
            if (previous == null) {
                List<Node> h = new ArrayList<>(history);
                h.add(current);
                PossibleMConnectingPath.findPaths(pag, paths, current, adj, target, condition, conditionClosure, h);
                continue;
            }
            boolean pass;
            boolean isConditionClosure = conditionClosure.contains(current);
            boolean isCondition = condition.contains(current);
            if (pag.isDefCollider(previous, current, adj)) {
                pass = isConditionClosure;
            } else {
                pass = !isCondition || !pag.isUnderlineTriple(previous, current, adj) && PossibleMConnectingPath.isOpen(pag, previous, current, adj);
            }

            if (pass) {
                List<Node> h = new ArrayList<>(history);
                h.add(current);
                PossibleMConnectingPath.findPaths(pag, paths, current, adj, target, condition, conditionClosure, h);
            }
        }

    }

    /**
     * Recursive methods that finds all the undirectedPaths of a specified length.
     */
    private static void findPathsOfLength(Graph pag, Set<List<Node>> paths, Node previous, Node current,
                                          Node target, Set<Node> condition, Set<Node> conditionClosure, List<Node> history, Integer length) {
        // checks if size greater than length
        if (history.size() > length) {
            return;
        }

        // check for cycles.
        if (history.contains(current)) {
            return;
        }
        // add path if we've reached the target and is of correct length.
        if (current.equals(target) && history.size() == length) {
            history.add(current);
            paths.add(history);
            return;
        }
        // recurse
        List<Node> adjacencies = pag.getAdjacentNodes(current);
        for (Node adj : adjacencies) {
            if (previous == null) {
                List<Node> h = new ArrayList<>(history);
                h.add(current);
                PossibleMConnectingPath.findPathsOfLength(pag, paths, current, adj, target, condition, conditionClosure, h, length);
                continue;
            }
            boolean pass;
            boolean isConditionClosure = conditionClosure.contains(current);
            boolean isCondition = condition.contains(current);
            if (pag.isDefCollider(previous, current, adj)) {
                pass = isConditionClosure;
            } else {
                pass = !isCondition || !pag.isUnderlineTriple(previous, current, adj) && PossibleMConnectingPath.isOpen(pag, previous, current, adj);
            }

            if (pass) {
                List<Node> h = new ArrayList<>(history);
                h.add(current);
                PossibleMConnectingPath.findPathsOfLength(pag, paths, current, adj, target, condition, conditionClosure, h, length);
            }
        }

    }

    private static boolean isOpen(Graph pag, Node x, Node y, Node z) {
        Edge edge = pag.getEdge(x, y);
        if (edge.getEndpoint1() != Endpoint.CIRCLE || edge.getEndpoint2() != Endpoint.CIRCLE) {
            return false;
        }
        edge = pag.getEdge(y, z);
        return edge.getEndpoint1() == Endpoint.CIRCLE && edge.getEndpoint2() == Endpoint.CIRCLE;
    }

    //================================== Private methods =======================//

    /**
     * <p>Getter for the field <code>pag</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public Graph getPag() {
        return this.pag;
    }

    /**
     * <p>Getter for the field <code>conditions</code>.</p>
     *
     * @return a {@link java.util.Set} object
     */
    public Set<Node> getConditions() {
        return Collections.unmodifiableSet(this.conditions);
    }

    /**
     * <p>Getter for the field <code>path</code>.</p>
     *
     * @return a {@link java.util.List} object
     */
    public List<Node> getPath() {
        return Collections.unmodifiableList(this.path);
    }

    /**
     * <p>toString.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String toString() {
        return this.path.toString();
    }

    /**
     * {@inheritDoc}
     */
    public boolean equals(Object o) {
        if (!(o instanceof PossibleMConnectingPath p)) {
            return false;
        }
        return p.pag.equals(this.pag) && p.path.equals(this.path) && p.conditions.equals(this.conditions);
    }
}




