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

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.graph.*;

import java.util.*;

/**
 * Finds possible d-connecting undirectedPaths for the IonSearch.
 * <p>
 * Not thread safe.
 *
 * @author Tyler Gibson
 */
public class PossibleDConnectingPath {


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


    private PossibleDConnectingPath(final Graph p, final Set<Node> conditions, final List<Node> path) {
        if (p == null || conditions == null || path == null) {
            throw new NullPointerException();
        }
        this.conditions = conditions;
        this.path = path;
        this.pag = p;
    }


    //========================= Public methods ======================//


    public Graph getPag() {
        return this.pag;
    }

    public Set<Node> getConditions() {
        return Collections.unmodifiableSet(this.conditions);
    }

    public List<Node> getPath() {
        return Collections.unmodifiableList(this.path);
    }

    public String toString() {
        return this.path.toString();
    }


    /**
     * Finds all possible D-connection undirectedPaths as sub-graphs of the pag given at construction time from x to y given z.
     */
    public static List<PossibleDConnectingPath> findDConnectingPaths(final Graph pag, final Node x, final Node y, final Collection<Node> z) {
        if (!pag.containsNode(x) || !pag.containsNode(y) || x.equals(y)) {
            return Collections.emptyList();
        }
        for (final Node node : z) {
            if (!pag.containsNode(node)) {
                return Collections.emptyList();
            }
        }
        if (pag.isAdjacentTo(x, y)) {
            return Collections.singletonList(new PossibleDConnectingPath(pag, new HashSet<>(z), Arrays.asList(x, y)));
        }
        final List<PossibleDConnectingPath> connectingPaths = new LinkedList<>();
        final Set<Node> conditions = new HashSet<>(z);
        final Set<Node> closure = getConditioningClosure(pag, z);
        final Set<List<Node>> paths = new HashSet<>();
        findPaths(pag, paths, null, x, y, conditions, closure, new LinkedList<Node>());
        for (final List<Node> path : paths) {
            connectingPaths.add(new PossibleDConnectingPath(pag, conditions, path));
        }
        return connectingPaths;
    }

    /**
     * Finds all possible D-connection undirectedPaths as sub-graphs of the pag given at construction time from x to y given z for
     * a particular path length.
     */
    public static List<PossibleDConnectingPath> findDConnectingPathsOfLength(final Graph pag, final Node x, final Node y, final Collection<Node> z, final Integer length) {
        if (!pag.containsNode(x) || !pag.containsNode(y) || x.equals(y)) {
            return Collections.emptyList();
        }
        for (final Node node : z) {
            if (!pag.containsNode(node)) {
                return Collections.emptyList();
            }
        }
        if (pag.isAdjacentTo(x, y)) {
            return Collections.singletonList(new PossibleDConnectingPath(pag, new HashSet<>(z), Arrays.asList(x, y)));
        }
        final List<PossibleDConnectingPath> connectingPaths = new LinkedList<>();
        final Set<Node> conditions = new HashSet<>(z);
        final Set<Node> closure = getConditioningClosure(pag, z);
        final Set<List<Node>> paths = new HashSet<>();
        findPathsOfLength(pag, paths, null, x, y, conditions, closure, new LinkedList<Node>(), length);
        for (final List<Node> path : paths) {
            connectingPaths.add(new PossibleDConnectingPath(pag, conditions, path));
        }
        return connectingPaths;
    }


    public boolean equals(final Object o) {
        if (!(o instanceof PossibleDConnectingPath)) {
            return false;
        }
        final PossibleDConnectingPath p = (PossibleDConnectingPath) o;
        return p.pag.equals(this.pag) && p.path.equals(this.path) && p.conditions.equals(this.conditions);
    }

    /*
     public int hashCode(){
         int result = 17;
         result += 19 * pag.hashCode();
         result += 23 * path.hashCode();
         result += 27 * conditions.hashCode();

         return result;
     }

    */

    //================================== Private methods =======================//


    private static Set<Node> getConditioningClosure(final Graph pag, final Collection<Node> z) {
        final Set<Node> closure = new HashSet<>();
        for (final Node node : z) {
            doParentClosureVisit(pag, node, closure);
        }
        return closure;
    }


    /**
     * Find the closure of a conditioning set of nodes under the parent relation.
     *
     * @param node    the node in question
     * @param closure the closure of the conditioning set uner the parent relation (to be calculated recursively).
     */
    private static void doParentClosureVisit(final Graph pag, final Node node, final Set<Node> closure) {
        if (!closure.contains(node)) {
            closure.add(node);

            for (final Edge edge1 : pag.getEdges(node)) {
                final Node sub = Edges.traverseReverseDirected(node, edge1);

                if (sub == null) {
                    continue;
                }

                doParentClosureVisit(pag, sub, closure);
            }
        }
    }


    /**
     * Recursive methods that finds all the undirectedPaths.
     */
    private static void findPaths(final Graph pag, final Set<List<Node>> paths, final Node previous, final Node current,
                                  final Node target, final Set<Node> condition, final Set<Node> conditionClosure, final List<Node> history) {

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
        final List<Node> adjacencies = pag.getAdjacentNodes(current);
        for (final Node adj : adjacencies) {
            if (previous == null) {
                final List<Node> h = new ArrayList<>(history);
                h.add(current);
                findPaths(pag, paths, current, adj, target, condition, conditionClosure, h);
                continue;
            }
            final boolean pass;
            final boolean isConditionClosure = conditionClosure.contains(current);
            final boolean isCondition = condition.contains(current);
            if (pag.isDefCollider(previous, current, adj)) {
                pass = isConditionClosure;
            } else {
                pass = !isCondition || !pag.isUnderlineTriple(previous, current, adj) && isOpen(pag, previous, current, adj);
            }

            if (pass) {
                final List<Node> h = new ArrayList<>(history);
                h.add(current);
                findPaths(pag, paths, current, adj, target, condition, conditionClosure, h);
            }
        }

    }

    /**
     * Recursive methods that finds all the undirectedPaths of a specified length.
     */
    private static void findPathsOfLength(final Graph pag, final Set<List<Node>> paths, final Node previous, final Node current,
                                          final Node target, final Set<Node> condition, final Set<Node> conditionClosure, final List<Node> history, final Integer length) {
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
        final List<Node> adjacencies = pag.getAdjacentNodes(current);
        for (final Node adj : adjacencies) {
            if (previous == null) {
                final List<Node> h = new ArrayList<>(history);
                h.add(current);
                findPathsOfLength(pag, paths, current, adj, target, condition, conditionClosure, h, length);
                continue;
            }
            final boolean pass;
            final boolean isConditionClosure = conditionClosure.contains(current);
            final boolean isCondition = condition.contains(current);
            if (pag.isDefCollider(previous, current, adj)) {
                pass = isConditionClosure;
            } else {
                pass = !isCondition || !pag.isUnderlineTriple(previous, current, adj) && isOpen(pag, previous, current, adj);
            }

            if (pass) {
                final List<Node> h = new ArrayList<>(history);
                h.add(current);
                findPathsOfLength(pag, paths, current, adj, target, condition, conditionClosure, h, length);
            }
        }

    }


    private static boolean isOpen(final Graph pag, final Node x, final Node y, final Node z) {
        Edge edge = pag.getEdge(x, y);
        if (edge.getEndpoint1() != Endpoint.CIRCLE || edge.getEndpoint2() != Endpoint.CIRCLE) {
            return false;
        }
        edge = pag.getEdge(y, z);
        return edge.getEndpoint1() == Endpoint.CIRCLE && edge.getEndpoint2() == Endpoint.CIRCLE;
    }

}



