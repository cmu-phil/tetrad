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

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.CombinationGenerator;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.collections4.map.MultiKeyMap;

import java.io.PrintStream;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;

import static java.lang.Math.max;

/**
 * Graph utilities for search algorithm. Lots of orientation method, for
 * instance.
 *
 * @author Joseph Ramsey
 */
public final class SearchGraphUtils {

    /**
     * Orients according to background knowledge.
     */
    public static void pcOrientbk(final IKnowledge bk, final Graph graph, final List<Node> nodes) {
        TetradLogger.getInstance().log("details", "Staring BK Orientation.");
        for (final Iterator<KnowledgeEdge> it = bk.forbiddenEdgesIterator(); it.hasNext(); ) {
            final KnowledgeEdge edge = it.next();

            //match strings to variables in the graph.
            final Node from = translate(edge.getFrom(), nodes);
            final Node to = translate(edge.getTo(), nodes);

            if (from == null || to == null) {
                continue;
            }

            if (graph.getEdge(from, to) == null) {
                continue;
            }

            // Orient to-->from
            graph.removeEdge(from, to);
            graph.addDirectedEdge(to, from);
//            graph.setEndpoint(from, to, Endpoint.TAIL);
//            graph.setEndpoint(to, from, Endpoint.ARROW);

            TetradLogger.getInstance().log("knowledgeOrientations", SearchLogUtils.edgeOrientedMsg("IKnowledge", graph.getEdge(to, from)));
        }

        for (final Iterator<KnowledgeEdge> it = bk.requiredEdgesIterator(); it.hasNext(); ) {
            final KnowledgeEdge edge = it.next();

            //match strings to variables in this graph
            final Node from = translate(edge.getFrom(), nodes);
            final Node to = translate(edge.getTo(), nodes);

            if (from == null || to == null) {
                continue;
            }

            if (graph.getEdge(from, to) == null) {
                continue;
            }

            // Orient from-->to
            graph.removeEdges(from, to);
            graph.addDirectedEdge(from, to);

//            graph.setEndpoint(to, from, Endpoint.TAIL);
//            graph.setEndpoint(from, to, Endpoint.ARROW);
            TetradLogger.getInstance().log("knowledgeOrientations", SearchLogUtils.edgeOrientedMsg("IKnowledge", graph.getEdge(from, to)));
        }

        TetradLogger.getInstance().log("details", "Finishing BK Orientation.");
    }

    /**
     * Performs step C of the algorithm, as indicated on page xxx of CPS, with
     * the modification that X--W--Y is oriented as X-->W<--Y if W is
     * *determined by* the sepset of (X, Y), rather than W just being *in* the
     * sepset of (X, Y).
     */
    public static void pcdOrientC(final SepsetMap set, final IndependenceTest test,
                                  final IKnowledge knowledge, final Graph graph) {
        TetradLogger.getInstance().log("info", "Starting Collider Orientation:");

        final List<Node> nodes = graph.getNodes();

        for (final Node y : nodes) {
            final List<Node> adjacentNodes = graph.getAdjacentNodes(y);

            if (adjacentNodes.size() < 2) {
                continue;
            }

            final ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null) {
                final Node x = adjacentNodes.get(combination[0]);
                final Node z = adjacentNodes.get(combination[1]);

                // Skip triples that are shielded.
                if (graph.isAdjacentTo(x, z)) {
                    continue;
                }

                final List<Node> sepset = sepset(graph, x, z, new HashSet<Node>(), new HashSet<Node>(),
                        -1, test);
                //set.get(x, z);

                if (sepset == null) {
                    continue;
                }

                if (sepset.contains(y)) {
                    continue;
                }

                final List<Node> augmentedSet = new LinkedList<>(sepset);

                if (!augmentedSet.contains(y)) {
                    augmentedSet.add(y);
                }

                if (test.determines(sepset, x)) {
//                    System.out.println(SearchLogUtils.determinismDetected(sepset, x));
                    continue;
                }

                if (test.determines(sepset, z)) {
//                    System.out.println(SearchLogUtils.determinismDetected(sepset, z));
                    continue;
                }

                if (test.determines(augmentedSet, x)) {
//                    System.out.println(SearchLogUtils.determinismDetected(augmentedSet, x));
                    continue;
                }

                if (test.determines(augmentedSet, z)) {
//                    System.out.println(SearchLogUtils.determinismDetected(augmentedSet, z));
                    continue;
                }

                if (!isArrowpointAllowed(x, y, knowledge)
                        || !isArrowpointAllowed(z, y, knowledge)) {
                    continue;
                }

                graph.setEndpoint(x, y, Endpoint.ARROW);
                graph.setEndpoint(z, y, Endpoint.ARROW);

                System.out.println(SearchLogUtils.colliderOrientedMsg(x, y, z) + " sepset = " + sepset);
                TetradLogger.getInstance().log("colliderOrientations", SearchLogUtils.colliderOrientedMsg(x, y, z));
            }
        }

        TetradLogger.getInstance().log("info", "Finishing Collider Orientation.");
    }

    private static List<Node> sepset(final Graph graph, final Node a, final Node c, final Set<Node> containing, final Set<Node> notContaining, final int depth,
                                     final IndependenceTest independenceTest) {
        final List<Node> adj = graph.getAdjacentNodes(a);
        adj.addAll(graph.getAdjacentNodes(c));
        adj.remove(c);
        adj.remove(a);

        for (int d = 0; d <= Math.min((depth == -1 ? 1000 : depth), max(adj.size(), adj.size())); d++) {
            if (d <= adj.size()) {
                final ChoiceGenerator gen = new ChoiceGenerator(adj.size(), d);
                int[] choice;

                WHILE:
                while ((choice = gen.next()) != null) {
                    final Set<Node> v2 = GraphUtils.asSet(choice, adj);
                    v2.addAll(containing);
                    v2.removeAll(notContaining);
                    v2.remove(a);
                    v2.remove(c);

//                    if (isForbidden(a, c, new ArrayList<>(v2)))
                    independenceTest.isIndependent(a, c, new ArrayList<>(v2));
                    final double p2 = independenceTest.getScore();

                    if (p2 < 0) {
                        return new ArrayList<>(v2);
                    }
                }
            }
        }

        return null;
    }

    //    /**
//     * Performs step D of the algorithm, as indicated on page xxx of CPS. This
//     * method should be called again if it returns true.
//     *
//     * <pre>
//     * Meek-Orient(G, t)
//     * 1.while orientations can be made, for arbitrary a, b, c, and d:
//     * 2.    If a --> b, b --> c, a not in adj(c), and Is-Noncollider(a, b, c) then orient b --> c.
//     * 3.    If a --> b, b --> c, a --- c, then orient a --> c.
//     * 4.    If a --- b, a --- c, a --- d, c --> b, d --> b, then orient a --> b.
//     * 5.    If a --> b, b in adj(d) a in adj(c), a --- d, b --> c, c --> d, then orient a --> d.
//     * </pre>
//     */
//    public static void orientUsingMeekRules(IKnowledge knowledge, Graph graph) {
//        LogUtils.getInstance().info("Starting Orientation Step D.");
//        boolean changed;
//
//        do {
//            changed = meekR1(graph, knowledge) || meekR2(graph, knowledge) ||
//                    meekR3(graph, knowledge) || meekR4(graph, knowledge);
//        } while (changed);
//
//        LogUtils.getInstance().info("Finishing Orientation Step D.");
//    }

    /**
     * Orients using Meek rules, double checking noncolliders locally.
     */
    public static void orientUsingMeekRulesLocally(final IKnowledge knowledge,
                                                   final Graph graph, final IndependenceTest test, final int depth) {
        TetradLogger.getInstance().log("info", "Starting Orientation Step D.");
        boolean changed;

        do {
            changed = meekR1Locally(graph, knowledge, test, depth)
                    || meekR2(graph, knowledge) || meekR3(graph, knowledge)
                    || meekR4(graph, knowledge);
        } while (changed);

        TetradLogger.getInstance().log("info", "Finishing Orientation Step D.");
    }

    public static void orientUsingMeekRulesLocally2(final IKnowledge knowledge,
                                                    final Graph graph, final IndependenceTest test, final int depth) {
        TetradLogger.getInstance().log("info", "Starting Orientation Step D.");
        boolean changed;

        do {
            changed = meekR1Locally2(graph, knowledge, test, depth)
                    || meekR2(graph, knowledge) || meekR3(graph, knowledge)
                    || meekR4(graph, knowledge);
        } while (changed);

        TetradLogger.getInstance().log("info", "Finishing Orientation Step D.");
    }

    /**
     * Step C of PC; orients colliders using specified sepset. That is, orients
     * x *-* y *-* z as x *-> y <-* z just in case y is in Sepset({x, z}).
     */
    public static List<Triple> orientCollidersUsingSepsets(final SepsetMap set, final IKnowledge knowledge, final Graph graph, final boolean verbose,
                                                           final boolean enforceCpdag) {
        TetradLogger.getInstance().log("details", "Starting Collider Orientation:");
        final List<Triple> colliders = new ArrayList<>();

        final List<Node> nodes = graph.getNodes();

        for (final Node b : nodes) {
            final List<Node> adjacentNodes = graph.getAdjacentNodes(b);

            if (adjacentNodes.size() < 2) {
                continue;
            }

            final ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null) {
                final Node a = adjacentNodes.get(combination[0]);
                final Node c = adjacentNodes.get(combination[1]);

                // Skip triples that are shielded.
                if (graph.isAdjacentTo(a, c)) {
                    continue;
                }

                final List<Node> sepset = set.get(a, c);

                //I think the null check needs to be here --AJ
                if (sepset != null && !sepset.contains(b)
                        && isArrowpointAllowed(a, b, knowledge)
                        && isArrowpointAllowed(c, b, knowledge)) {
                    if (verbose) {
                        System.out.println("Collider orientation <" + a + ", " + b + ", " + c + "> sepset = " + sepset);
                    }

                    if (enforceCpdag) {
                        if (graph.getEndpoint(b, a) == Endpoint.ARROW || graph.getEndpoint(b, c) == Endpoint.ARROW) {
                            continue;
                        }
                    }

//                    graph.setEndpoint(a, b, Endpoint.ARROW);
//                    graph.setEndpoint(c, b, Endpoint.ARROW);
                    graph.removeEdge(a, b);
                    graph.removeEdge(c, b);

                    graph.addDirectedEdge(a, b);
                    graph.addDirectedEdge(c, b);

                    colliders.add(new Triple(a, b, c));
                    TetradLogger.getInstance().log("colliderOrientations", SearchLogUtils.colliderOrientedMsg(a, b, c, sepset));
                }
            }
        }

        TetradLogger.getInstance().log("details", "Finishing Collider Orientation.");

        return colliders;
    }

    private static List<Node> union(final List<Node> nodes, final Node a) {
        final List<Node> union = new ArrayList<>(nodes);
        union.add(a);
        return union;
    }

    public static void orientCollidersUsingSepsets(final SepsetProducer sepset, final IKnowledge knowledge, final Graph graph, final boolean verbose) {
        TetradLogger.getInstance().log("details", "Starting Collider Orientation:");

        final List<Node> nodes = graph.getNodes();

        for (final Node b : nodes) {
            final List<Node> adjacentNodes = graph.getAdjacentNodes(b);

            if (adjacentNodes.size() < 2) {
                continue;
            }

            final ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null) {
                final Node a = adjacentNodes.get(combination[0]);
                final Node c = adjacentNodes.get(combination[1]);

                // Skip triples that are shielded.
                if (graph.isAdjacentTo(a, c)) {
                    continue;
                }

                // Skip triple already oriented.
                if (graph.getEdge(a, b).pointsTowards(b) && graph.getEdge(b, c).pointsTowards(b)) {
                    continue;
                }

                final List<Node> sepset1 = sepset.getSepset(a, c);

                if (!sepset1.contains(b) && isArrowpointAllowed(a, b, knowledge)
                        && isArrowpointAllowed(c, b, knowledge)) {
                    graph.setEndpoint(a, b, Endpoint.ARROW);
                    graph.setEndpoint(c, b, Endpoint.ARROW);

                    TetradLogger.getInstance().log("colliderOrientations", SearchLogUtils.colliderOrientedMsg(a, b, c, sepset1));

                    if (verbose) {
                        System.out.println(SearchLogUtils.colliderOrientedMsg(a, b, c, sepset1));
                    }
                }
            }
        }

        TetradLogger.getInstance().log("details", "Finishing Collider Orientation.");
    }

    //use this for oritentation with an initial graph if using null trick for unconditional independence
    //AJ
    public static List<Triple> orientCollidersUsingSepsets(final SepsetMap set, final IKnowledge knowledge, final Graph graph, final Graph externalGraph, final boolean verbose) {
        TetradLogger.getInstance().log("details", "Starting Collider Orientation:");
        final List<Triple> colliders = new ArrayList<>();

        final List<Node> nodes = graph.getNodes();

        for (final Node b : nodes) {
            final List<Node> adjacentNodes = graph.getAdjacentNodes(b);

            if (adjacentNodes.size() < 2) {
                continue;
            }

            final ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null) {
                final Node a = adjacentNodes.get(combination[0]);
                final Node c = adjacentNodes.get(combination[1]);

                // Skip triples that are shielded.
                if (graph.isAdjacentTo(a, c)) {
                    continue;
                }

                //for vanilla pc, I think a check if already oriented might need to be here -AJ
                // Skip triples with parents not adjacent in externalGraph
                // may need a similar check for knowledge... -AJ
                if (externalGraph != null && !externalGraph.isAdjacentTo(a, c)) {
                    continue;
                }

                final List<Node> sepset = set.get(a, c);

                //Null check needs to be here if sepsets.setReturnEmptyIfNotSet(false)--AJ
                if (sepset != null && !sepset.contains(b)
                        && isArrowpointAllowed(a, b, knowledge)
                        && isArrowpointAllowed(c, b, knowledge)) {
                    if (verbose) {
                        System.out.println("Collider orientation <" + a + ", " + b + ", " + c + "> sepset = " + sepset);
                    }

                    graph.setEndpoint(a, b, Endpoint.ARROW);
                    graph.setEndpoint(c, b, Endpoint.ARROW);
                    colliders.add(new Triple(a, b, c));
                    TetradLogger.getInstance().log("colliderOrientations", SearchLogUtils.colliderOrientedMsg(a, b, c, sepset));
                }
            }
        }

        TetradLogger.getInstance().log("details", "Finishing Collider Orientation.");

        return colliders;
    }

    private static boolean createsBidirectedEdge(final Node a, final Node b, final Node c, final Graph graph) {
        if (graph.getEdge(b, a).getDistalEndpoint(b) == Endpoint.ARROW) {
            return true;
        }
        if (graph.getEdge(b, c).getDistalEndpoint(b) == Endpoint.ARROW) {
            return true;
        }
        return false;
    }

    // Tests whether adding a for b--a--c to the sepset (if it's not there) yields independence. Poor man's CPC.
    public static void orientCollidersUsingSepsets(final SepsetMap set,
                                                   final IKnowledge knowledge, final Graph graph,
                                                   final IndependenceTest test) {

        TetradLogger.getInstance().log("details", "Starting Collider Orientation:");

//        verifySepsetIntegrity(set, graph);
        final List<Node> nodes = graph.getNodes();

        for (final Node b : nodes) {
            final List<Node> adjacentNodes = graph.getAdjacentNodes(b);
            Collections.sort(adjacentNodes);

            if (adjacentNodes.size() < 2) {
                continue;
            }

            final ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null) {
                final Node a = adjacentNodes.get(combination[0]);
                final Node c = adjacentNodes.get(combination[1]);

                // Skip triples that are shielded.
                if (graph.isAdjacentTo(a, c)) {
                    continue;
                }

                List<Node> sepset = set.get(a, c);

                final List<Node> sepset2 = new ArrayList<>(sepset);

                if (!sepset2.contains(b)) {
                    System.out.println("\nADDING " + b);

                    sepset2.add(b);
                    final double alpha = test.getAlpha();
                    test.setAlpha(alpha);

                    if (test.isIndependent(a, c, sepset2)) {
                        sepset = sepset2;
                    }
                }

                if (!sepset.contains(b)
                        && isArrowpointAllowed(a, b, knowledge)
                        && isArrowpointAllowed(c, b, knowledge)) {
                    System.out.println("Collider orientation <" + a + ", " + b + ", " + c + "> sepset = " + sepset);

                    graph.setEndpoint(a, b, Endpoint.ARROW);
                    graph.setEndpoint(c, b, Endpoint.ARROW);
                    TetradLogger.getInstance().log("colliderOrientations", SearchLogUtils.colliderOrientedMsg(a, b, c, sepset));
                }
            }
        }

        TetradLogger.getInstance().log("details", "Finishing Collider Orientation.");
    }

    public static void orientCollidersLocally(final IKnowledge knowledge, final Graph graph,
                                              final IndependenceTest test,
                                              final int depth) {
        orientCollidersLocally(knowledge, graph, test, depth, null);
    }

    public static void orientCollidersLocally(final IKnowledge knowledge, final Graph graph,
                                              final IndependenceTest test,
                                              final int depth, Set<Node> nodesToVisit) {
        TetradLogger.getInstance().log("details", "Starting Collider Orientation:");

        if (nodesToVisit == null) {
            nodesToVisit = new HashSet<>(graph.getNodes());
        }

        for (final Node a : nodesToVisit) {
            final List<Node> adjacentNodes = graph.getAdjacentNodes(a);

            if (adjacentNodes.size() < 2) {
                continue;
            }

            final ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null) {
                final Node b = adjacentNodes.get(combination[0]);
                final Node c = adjacentNodes.get(combination[1]);

                // Skip triples that are shielded.
                if (graph.isAdjacentTo(b, c)) {
                    continue;
                }

                if (isArrowpointAllowed1(b, a, knowledge)
                        && isArrowpointAllowed1(c, a, knowledge)) {
                    if (!existsLocalSepsetWith(b, a, c, test, graph, depth)) {
                        graph.setEndpoint(b, a, Endpoint.ARROW);
                        graph.setEndpoint(c, a, Endpoint.ARROW);
                        TetradLogger.getInstance().log("colliderOrientations", SearchLogUtils.colliderOrientedMsg(b, a, c));
                    }
                }
            }
        }

        TetradLogger.getInstance().log("details", "Finishing Collider Orientation.");
    }

    public static boolean existsLocalSepsetWith(final Node x, final Node y, final Node z,
                                                final IndependenceTest test, final Graph graph, final int depth) {
        final Set<Node> __nodes = new HashSet<>(graph.getAdjacentNodes(x));
        __nodes.addAll(graph.getAdjacentNodes(z));
        __nodes.remove(x);
        __nodes.remove(z);

        final List<Node> _nodes = new LinkedList<>(__nodes);
        TetradLogger.getInstance().log("adjacencies", "Adjacents for " + x + "--" + y + "--" + z + " = " + _nodes);

        int _depth = depth;
        if (_depth == -1) {
            _depth = 1000;
        }
        _depth = Math.min(_depth, _nodes.size());

        for (int d = 1; d <= _depth; d++) {
            if (_nodes.size() >= d) {
                final ChoiceGenerator cg2 = new ChoiceGenerator(_nodes.size(), d);
                int[] choice;

                while ((choice = cg2.next()) != null) {
                    final List<Node> condSet = GraphUtils.asList(choice, _nodes);

                    if (!condSet.contains(y)) {
                        continue;
                    }

//                    LogUtils.getInstance().finest("Trying " + condSet);
                    if (test.isIndependent(x, z, condSet)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public static boolean existsLocalSepsetWithout(final Node x, final Node y, final Node z,
                                                   final IndependenceTest test, final Graph graph, final int depth) {
        final Set<Node> __nodes = new HashSet<>(graph.getAdjacentNodes(x));
        __nodes.addAll(graph.getAdjacentNodes(z));
        __nodes.remove(x);
        __nodes.remove(z);
        final List<Node> _nodes = new LinkedList<>(__nodes);
        TetradLogger.getInstance().log("adjacencies",
                "Adjacents for " + x + "--" + y + "--" + z + " = " + _nodes);

        int _depth = depth;
        if (_depth == -1) {
            _depth = 1000;
        }
        _depth = Math.min(_depth, _nodes.size());

        for (int d = 0; d <= _depth; d++) {
            if (_nodes.size() >= d) {
                final ChoiceGenerator cg2 = new ChoiceGenerator(_nodes.size(), d);
                int[] choice;

                while ((choice = cg2.next()) != null) {
                    final List<Node> condSet = GraphUtils.asList(choice, _nodes);

                    if (condSet.contains(y)) {
                        continue;
                    }

                    //            LogUtils.getInstance().finest("Trying " + condSet);
                    if (test.isIndependent(x, z, condSet)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public static boolean existsLocalSepsetWithoutDet(final Node x, final Node y, final Node z,
                                                      final IndependenceTest test, final Graph graph, final int depth) {
        final Set<Node> __nodes = new HashSet<>(graph.getAdjacentNodes(x));
        __nodes.addAll(graph.getAdjacentNodes(z));
        __nodes.remove(x);
        __nodes.remove(z);
        final List<Node> _nodes = new LinkedList<>(__nodes);
        TetradLogger.getInstance().log("adjacencies",
                "Adjacents for " + x + "--" + y + "--" + z + " = " + _nodes);

        int _depth = depth;
        if (_depth == -1) {
            _depth = 1000;
        }
        _depth = Math.min(_depth, _nodes.size());

        for (int d = 0; d <= _depth; d++) {
            if (_nodes.size() >= d) {
                final ChoiceGenerator cg2 = new ChoiceGenerator(_nodes.size(), d);
                int[] choice;

                while ((choice = cg2.next()) != null) {
                    final List<Node> condSet = GraphUtils.asList(choice, _nodes);

                    if (condSet.contains(y)) {
                        continue;
                    }

                    if (test.determines(condSet, y)) {
                        continue;
                    }

                    //        LogUtils.getInstance().finest("Trying " + condSet);
                    if (test.isIndependent(x, z, condSet)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

//    public static boolean meekR1(Graph graph, IKnowledge knowledge) {
//        List<Node> nodes = graph.getNodes();
//        boolean changed = true;
//
//        while (changed) {
//            changed = false;
//
//            for (Node a : nodes) {
//                List<Node> adjacentNodes = graph.getAdjacentNodes(a);
//
//                if (adjacentNodes.size() < 2) {
//                    continue;
//                }
//
//                ChoiceGenerator cg =
//                        new ChoiceGenerator(adjacentNodes.size(), 2);
//                int[] combination;
//
//                while ((combination = cg.next()) != null) {
//                    Node b = adjacentNodes.get(combination[0]);
//                    Node c = adjacentNodes.get(combination[1]);
//
//                    // Skip triples that are shielded.
//                    if (graph.isAdjacentTo(b, c)) {
//                        continue;
//                    }
//
//                    if (graph.getEndpoint(b, a) == Endpoint.ARROW &&
//                            graph.isUndirectedFromTo(a, c)) {
//                        if (isArrowpointAllowed(a, c, knowledge)) {
//                            graph.setEndpoint(a, c, Endpoint.ARROW);
//                            SearchLogUtils.logEdgeOriented("Meek R1",
//                                    graph.getEdge(a, c), LOGGER);
//                            changed = true;
//                        }
//                    }
//                    else if (graph.getEndpoint(c, a) == Endpoint.ARROW &&
//                            graph.isUndirectedFromTo(a, b)) {
//                        if (isArrowpointAllowed(a, b, knowledge)) {
//                            graph.setEndpoint(a, b, Endpoint.ARROW);
//                            SearchLogUtils.logEdgeOriented("Meek R1",
//                                    graph.getEdge(a, b), LOGGER);
//                            changed = true;
//                        }
//                    }
//                }
//            }
//        }
//
//        return changed;
//    }

    /**
     * Orient away from collider.
     */
    public static boolean meekR1Locally(final Graph graph, final IKnowledge knowledge,
                                        final IndependenceTest test, final int depth) {
        final List<Node> nodes = graph.getNodes();
        boolean changed = true;

        while (changed) {
            changed = false;

            for (final Node a : nodes) {
                final List<Node> adjacentNodes = graph.getAdjacentNodes(a);

                if (adjacentNodes.size() < 2) {
                    continue;
                }

                final ChoiceGenerator cg
                        = new ChoiceGenerator(adjacentNodes.size(), 2);
                int[] combination;

                while ((combination = cg.next()) != null) {
                    final Node b = adjacentNodes.get(combination[0]);
                    final Node c = adjacentNodes.get(combination[1]);

                    // Skip triples that are shielded.
                    if (graph.isAdjacentTo(b, c)) {
                        continue;
                    }

                    if (graph.getEndpoint(b, a) == Endpoint.ARROW
                            && graph.isUndirectedFromTo(a, c)) {
                        if (existsLocalSepsetWithout(b, a, c, test, graph,
                                depth)) {
                            continue;
                        }

                        if (isArrowpointAllowed(a, c, knowledge)) {
                            graph.setEndpoint(a, c, Endpoint.ARROW);
                            TetradLogger.getInstance().log("impliedOrientation", SearchLogUtils.edgeOrientedMsg("Meek R1", graph.getEdge(a, c)));
                            changed = true;
                        }
                    } else if (graph.getEndpoint(c, a) == Endpoint.ARROW
                            && graph.isUndirectedFromTo(a, b)) {
                        if (existsLocalSepsetWithout(b, a, c, test, graph,
                                depth)) {
                            continue;
                        }

                        if (isArrowpointAllowed(a, b, knowledge)) {
                            graph.setEndpoint(a, b, Endpoint.ARROW);
                            TetradLogger.getInstance().log("impliedOrientation", SearchLogUtils.edgeOrientedMsg("Meek R1", graph.getEdge(a, b)));
                            changed = true;
                        }
                    }
                }
            }
        }

        return changed;
    }

    public static boolean meekR1Locally2(final Graph graph, final IKnowledge knowledge,
                                         final IndependenceTest test, final int depth) {
        final List<Node> nodes = graph.getNodes();
        boolean changed = true;

        while (changed) {
            changed = false;

            for (final Node a : nodes) {
                final List<Node> adjacentNodes = graph.getAdjacentNodes(a);

                if (adjacentNodes.size() < 2) {
                    continue;
                }

                final ChoiceGenerator cg
                        = new ChoiceGenerator(adjacentNodes.size(), 2);
                int[] combination;

                while ((combination = cg.next()) != null) {
                    final Node b = adjacentNodes.get(combination[0]);
                    final Node c = adjacentNodes.get(combination[1]);

                    // Skip triples that are shielded.
                    if (graph.isAdjacentTo(b, c)) {
                        continue;
                    }

                    if (graph.getEndpoint(b, a) == Endpoint.ARROW
                            && graph.isUndirectedFromTo(a, c)) {
                        if (existsLocalSepsetWithoutDet(b, a, c, test, graph,
                                depth)) {
                            continue;
                        }

                        if (isArrowpointAllowed(a, c, knowledge)) {
                            graph.setEndpoint(a, c, Endpoint.ARROW);
                            TetradLogger.getInstance().log("impliedOrientation", SearchLogUtils.edgeOrientedMsg("Meek R1", graph.getEdge(a, c)));
                            changed = true;
                        }
                    } else if (graph.getEndpoint(c, a) == Endpoint.ARROW
                            && graph.isUndirectedFromTo(a, b)) {
                        if (existsLocalSepsetWithoutDet(b, a, c, test, graph,
                                depth)) {
                            continue;
                        }

                        if (isArrowpointAllowed(a, b, knowledge)) {
                            graph.setEndpoint(a, b, Endpoint.ARROW);
                            TetradLogger.getInstance().log("impliedOrientation", SearchLogUtils.edgeOrientedMsg("Meek R1", graph.getEdge(a, b)));
                            changed = true;
                        }
                    }
                }
            }
        }

        return changed;
    }

    /**
     * If
     */
    public static boolean meekR2(final Graph graph, final IKnowledge knowledge) {
        final List<Node> nodes = graph.getNodes();
        final boolean changed = false;

        for (final Node a : nodes) {
            final List<Node> adjacentNodes = graph.getAdjacentNodes(a);

            if (adjacentNodes.size() < 2) {
                continue;
            }

            final ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null) {
                final Node b = adjacentNodes.get(combination[0]);
                final Node c = adjacentNodes.get(combination[1]);

                if (graph.isDirectedFromTo(b, a)
                        && graph.isDirectedFromTo(a, c)
                        && graph.isUndirectedFromTo(b, c)) {
                    if (isArrowpointAllowed(b, c, knowledge)) {
                        graph.setEndpoint(b, c, Endpoint.ARROW);
                        TetradLogger.getInstance().log("impliedOrientation", SearchLogUtils.edgeOrientedMsg("Meek R2", graph.getEdge(b, c)));
                    }
                } else if (graph.isDirectedFromTo(c, a)
                        && graph.isDirectedFromTo(a, b)
                        && graph.isUndirectedFromTo(c, b)) {
                    if (isArrowpointAllowed(c, b, knowledge)) {
                        graph.setEndpoint(c, b, Endpoint.ARROW);
                        TetradLogger.getInstance().log("impliedOrientation", SearchLogUtils.edgeOrientedMsg("Meek R2", graph.getEdge(c, b)));
                    }
                }
            }
        }

        return changed;
    }

    /**
     * Meek's rule R3. If a--b, a--c, a--d, c-->b, c-->b, then orient a-->b.
     */
    public static boolean meekR3(final Graph graph, final IKnowledge knowledge) {

        final List<Node> nodes = graph.getNodes();
        boolean changed = false;

        for (final Node a : nodes) {
            final List<Node> adjacentNodes = graph.getAdjacentNodes(a);

            if (adjacentNodes.size() < 3) {
                continue;
            }

            for (final Node b : adjacentNodes) {
                final List<Node> otherAdjacents = new LinkedList<>(adjacentNodes);
                otherAdjacents.remove(b);

                if (!graph.isUndirectedFromTo(a, b)) {
                    continue;
                }

                final ChoiceGenerator cg
                        = new ChoiceGenerator(otherAdjacents.size(), 2);
                int[] combination;

                while ((combination = cg.next()) != null) {
                    final Node c = otherAdjacents.get(combination[0]);
                    final Node d = otherAdjacents.get(combination[1]);

                    if (graph.isAdjacentTo(c, d)) {
                        continue;
                    }

                    if (!graph.isUndirectedFromTo(a, c)) {
                        continue;
                    }

                    if (!graph.isUndirectedFromTo(a, d)) {
                        continue;
                    }

                    if (graph.isDirectedFromTo(c, b)
                            && graph.isDirectedFromTo(d, b)) {
                        if (isArrowpointAllowed(a, b, knowledge)) {
                            graph.setEndpoint(a, b, Endpoint.ARROW);
                            TetradLogger.getInstance().log("impliedOrientation", SearchLogUtils.edgeOrientedMsg("Meek R3", graph.getEdge(a, b)));
                            changed = true;
                            break;
                        }
                    }
                }
            }
        }

        return changed;
    }

    public static boolean meekR4(final Graph graph, final IKnowledge knowledge) {
        if (knowledge == null) {
            return false;
        }

        final List<Node> nodes = graph.getNodes();
        boolean changed = false;

        for (final Node a : nodes) {
            final List<Node> adjacentNodes = graph.getAdjacentNodes(a);

            if (adjacentNodes.size() < 3) {
                continue;
            }

            for (final Node d : adjacentNodes) {
                if (!graph.isAdjacentTo(a, d)) {
                    continue;
                }

                final List<Node> otherAdjacents = new LinkedList<>(adjacentNodes);
                otherAdjacents.remove(d);

                final ChoiceGenerator cg
                        = new ChoiceGenerator(otherAdjacents.size(), 2);
                int[] combination;

                while ((combination = cg.next()) != null) {
                    final Node b = otherAdjacents.get(combination[0]);
                    final Node c = otherAdjacents.get(combination[1]);

                    if (!graph.isUndirectedFromTo(a, b)) {
                        continue;
                    }

                    if (!graph.isUndirectedFromTo(a, c)) {
                        continue;
                    }

//                    if (!isUnshieldedNoncollider(c, a, b, graph)) {
//                        continue;
//                    }
                    if (graph.isDirectedFromTo(b, c)
                            && graph.isDirectedFromTo(d, c)) {
                        if (isArrowpointAllowed(a, c, knowledge)) {
                            graph.setEndpoint(a, c, Endpoint.ARROW);
                            TetradLogger.getInstance().log("impliedOrientation", SearchLogUtils.edgeOrientedMsg("Meek T1", graph.getEdge(a, c)));
                            changed = true;
                            break;
                        }
                    } else if (graph.isDirectedFromTo(c, d)
                            && graph.isDirectedFromTo(d, b)) {
                        if (isArrowpointAllowed(a, b, knowledge)) {
                            graph.setEndpoint(a, b, Endpoint.ARROW);
                            TetradLogger.getInstance().log("impliedOrientation", SearchLogUtils.edgeOrientedMsg("Meek T1", graph.getEdge(a, b)));
                            changed = true;
                            break;
                        }
                    }
                }
            }
        }

        return changed;
    }

//    /**
//     *
//     */
//    public static boolean meekR4(Graph graph, IKnowledge knowledge) {
//        if (knowledge == null) {
//            return false;
//        }
//
//        List<Node> nodes = graph.getNodes();
//        boolean changed = false;
//
//        for (Node a : nodes) {
//            List<Node> adjacentNodes = graph.getAdjacentNodes(a);
//
//            if (adjacentNodes.size() < 3) {
//                continue;
//            }
//
//            for (Node d: adjacentNodes) {
//                if (!graph.isUndirectedFromTo(a, d)) {
//                    continue;
//                }
//
//                List<Node> otherAdjacents = new LinkedList<Node>(adjacentNodes);
//                otherAdjacents.remove(d);
//
//                ChoiceGenerator cg =
//                        new ChoiceGenerator(otherAdjacents.size(), 2);
//                int[] combination;
//
//                while ((combination = cg.next()) != null) {
//                    Node b = otherAdjacents.get(combination[0]);
//                    Node c = otherAdjacents.get(combination[1]);
//
//                    if (graph.isAdjacentTo(b, d)) {
//                        continue;
//                    }
//
//                    if (!graph.isUndirectedFromTo(a, b)) {
//                        continue;
//                    }
//
//                    if (!graph.isAdjacentTo(a, c)) {
//                        continue;
//                    }
//
////                    if (!(graph.isUndirectedFromTo(a, c) ||
////                            graph.isDirectedFromTo(a, c) ||
////                            graph.isDirectedFromTo(c, a))) {
////                        continue;
////                    }
//
//                    if (graph.isDirectedFromTo(b, c) &&
//                            graph.isDirectedFromTo(c, d)) {
//                        if (isArrowpointAllowed(a, d, knowledge)) {
//                            graph.setEndpoint(a, d, Endpoint.ARROW);
//                            SearchLogUtils.logEdgeOriented("Meek T1",
//                                    graph.getEdge(a, d), LOGGER);
//                            changed = true;
//                            break;
//                        }
//                    }
//                    else if (graph.isDirectedFromTo(d, c) &&
//                            graph.isDirectedFromTo(c, b)) {
//                        if (isArrowpointAllowed(a, b, knowledge)) {
//                            graph.setEndpoint(a, b, Endpoint.ARROW);
//                            SearchLogUtils.logEdgeOriented("Meek T1",
//                                    graph.getEdge(a, b), LOGGER);
//                            changed = true;
//                            break;
//                        }
//                    }
//                }
//            }
//        }
//
//        return changed;
//    }

    /**
     * Checks if an arrowpoint is allowed by background knowledge.
     */
    public static boolean isArrowpointAllowed(final Object from, final Object to,
                                              final IKnowledge knowledge) {
        if (knowledge == null) {
            return true;
        }
        return !knowledge.isRequired(to.toString(), from.toString())
                && !knowledge.isForbidden(from.toString(), to.toString());
    }

    /**
     * Transforms a maximally directed cpdag (PDAG) represented in graph
     * <code>g</code> into an arbitrary DAG by modifying <code>g</code> itself.
     * Based on the algorithm described in </p> Chickering (2002) "Optimal
     * structure identification with greedy search" Journal of Machine Learning
     * Research. </p> R. Silva, June 2004
     */
    public static void pdagToDag(final Graph g) {
        final Graph p = new EdgeListGraph(g);
        final List<Edge> undirectedEdges = new ArrayList<>();

        for (final Edge edge : g.getEdges()) {
            if (edge.getEndpoint1() == Endpoint.TAIL
                    && edge.getEndpoint2() == Endpoint.TAIL
                    && !undirectedEdges.contains(edge)) {
                undirectedEdges.add(edge);
            }
        }
        g.removeEdges(undirectedEdges);
        final List<Node> pNodes = p.getNodes();

        do {
            Node x = null;

            for (final Node pNode : pNodes) {
                x = pNode;

                if (p.getChildren(x).size() > 0) {
                    continue;
                }

                final Set<Node> neighbors = new HashSet<>();

                for (final Edge edge : p.getEdges()) {
                    if (edge.getNode1() == x || edge.getNode2() == x) {
                        if (edge.getEndpoint1() == Endpoint.TAIL
                                && edge.getEndpoint2() == Endpoint.TAIL) {
                            if (edge.getNode1() == x) {
                                neighbors.add(edge.getNode2());
                            } else {
                                neighbors.add(edge.getNode1());
                            }
                        }
                    }
                }
                if (neighbors.size() > 0) {
                    final Collection<Node> parents = p.getParents(x);
                    final Set<Node> all = new HashSet<>(neighbors);
                    all.addAll(parents);
                    if (!GraphUtils.isClique(all, p)) {
                        continue;
                    }
                }

                for (final Node neighbor : neighbors) {
                    final Node node1 = g.getNode(neighbor.getName());
                    final Node node2 = g.getNode(x.getName());

                    g.addDirectedEdge(node1, node2);
                }
                p.removeNode(x);
                break;
            }
            pNodes.remove(x);
        } while (pNodes.size() > 0);
    }

    /**
     * Get a graph and direct only the unshielded colliders.
     */
    public static void basicCPDAG(final Graph graph) {
        final Set<Edge> undirectedEdges = new HashSet<>();

        NEXT_EDGE:
        for (final Edge edge : graph.getEdges()) {
            if (!edge.isDirected()) {
                continue;
            }

            final Node x = Edges.getDirectedEdgeTail(edge);
            final Node y = Edges.getDirectedEdgeHead(edge);

            for (final Node parent : graph.getParents(y)) {
                if (parent != x) {
                    if (!graph.isAdjacentTo(parent, x)) {
                        continue NEXT_EDGE;
                    }
                }
            }

            undirectedEdges.add(edge);
        }

        for (final Edge nextUndirected : undirectedEdges) {
            final Node node1 = nextUndirected.getNode1();
            final Node node2 = nextUndirected.getNode2();

            graph.removeEdges(node1, node2);
            graph.addUndirectedEdge(node1, node2);
        }
    }

    public static void basicCpdagRestricted(final Graph graph, final Set<Edge> edges) {
        final Set<Edge> undirectedEdges = new HashSet<>();

        NEXT_EDGE:
        for (final Edge edge : edges) {
            if (!edge.isDirected()) {
                continue;
            }

            final Node _x = Edges.getDirectedEdgeTail(edge);
            final Node _y = Edges.getDirectedEdgeHead(edge);

            for (final Node parent : graph.getParents(_y)) {
                if (parent != _x) {
                    if (!graph.isAdjacentTo(parent, _x)) {
                        continue NEXT_EDGE;
                    }
                }
            }

            undirectedEdges.add(edge);
        }

        for (final Edge nextUndirected : undirectedEdges) {
            final Node node1 = nextUndirected.getNode1();
            final Node node2 = nextUndirected.getNode2();

            graph.removeEdge(nextUndirected);
            graph.addUndirectedEdge(node1, node2);
        }
    }

    public static void basicCpdagRestricted2(final Graph graph, final Node node) {
        final Set<Edge> undirectedEdges = new HashSet<>();

        NEXT_EDGE:
        for (final Edge edge : graph.getEdges(node)) {
            if (!edge.isDirected()) {
                continue;
            }

            final Node _x = Edges.getDirectedEdgeTail(edge);
            final Node _y = Edges.getDirectedEdgeHead(edge);

            for (final Node parent : graph.getParents(_y)) {
                if (parent != _x) {
                    if (!graph.isAdjacentTo(parent, _x)) {
                        continue NEXT_EDGE;
                    }
                }
            }

            undirectedEdges.add(edge);
        }

        for (final Edge nextUndirected : undirectedEdges) {
            final Node node1 = nextUndirected.getNode1();
            final Node node2 = nextUndirected.getNode2();

            graph.removeEdge(nextUndirected);
            graph.addUndirectedEdge(node1, node2);
        }
    }

    /**
     * Transforms a DAG represented in graph <code>graph</code> into a maximally
     * directed cpdag (PDAG) by modifying <code>g</code> itself. Based on the
     * algorithm described in </p> Chickering (2002) "Optimal structure
     * identification with greedy search" Journal of Machine Learning Research.
     * It works for both BayesNets and SEMs.
     * </p> R. Silva, June 2004
     */
    public static void dagToPdag(final Graph graph) {
        //do topological sort on the nodes
        final Graph graphCopy = new EdgeListGraph(graph);
        final Node[] orderedNodes = new Node[graphCopy.getNodes().size()];
        int count = 0;
        while (graphCopy.getNodes().size() > 0) {
            final Set<Node> exogenousNodes = new HashSet<>();

            for (final Node next : graphCopy.getNodes()) {
                if (graphCopy.isExogenous(next)) {
                    exogenousNodes.add(next);
                    orderedNodes[count++] = graph.getNode(next.getName());
                }
            }

            graphCopy.removeNodes(new ArrayList<>(exogenousNodes));
        }
        //ordered edges - improvised, inefficient implementation
        count = 0;
        final Edge[] edges = new Edge[graph.getNumEdges()];
        final boolean[] edgeOrdered = new boolean[graph.getNumEdges()];
        final Edge[] orderedEdges = new Edge[graph.getNumEdges()];

        for (final Edge edge : graph.getEdges()) {
            edges[count++] = edge;
        }

        for (int i = 0; i < edges.length; i++) {
            edgeOrdered[i] = false;
        }

        while (count > 0) {
            for (final Node orderedNode : orderedNodes) {
                for (int k = orderedNodes.length - 1; k >= 0; k--) {
                    for (int q = 0; q < edges.length; q++) {
                        if (!edgeOrdered[q]
                                && edges[q].getNode1() == orderedNodes[k]
                                && edges[q].getNode2() == orderedNode) {
                            edgeOrdered[q] = true;
                            orderedEdges[orderedEdges.length - count]
                                    = edges[q];
                            count--;
                        }
                    }
                }
            }
        }

        //label edges
        final boolean[] compelledEdges = new boolean[graph.getNumEdges()];
        final boolean[] reversibleEdges = new boolean[graph.getNumEdges()];
        for (int i = 0; i < graph.getNumEdges(); i++) {
            compelledEdges[i] = false;
            reversibleEdges[i] = false;
        }
        for (int i = 0; i < graph.getNumEdges(); i++) {
            if (compelledEdges[i] || reversibleEdges[i]) {
                continue;
            }
            final Node x = orderedEdges[i].getNode1();
            final Node y = orderedEdges[i].getNode2();
            for (int j = 0; j < orderedEdges.length; j++) {
                if (orderedEdges[j].getNode2() == x && compelledEdges[j]) {
                    final Node w = orderedEdges[j].getNode1();
                    if (!graph.isParentOf(w, y)) {
                        for (int k = 0; k < orderedEdges.length; k++) {
                            if (orderedEdges[k].getNode2() == y) {
                                compelledEdges[k] = true;
                                break;
                            }
                        }
                    } else {
                        for (int k = 0; k < orderedEdges.length; k++) {
                            if (orderedEdges[k].getNode1() == w
                                    && orderedEdges[k].getNode2() == y) {
                                compelledEdges[k] = true;
                                break;
                            }
                        }
                    }
                }
                if (compelledEdges[i]) {
                    break;
                }
            }
            if (compelledEdges[i]) {
                continue;
            }
            boolean foundZ = false;

            for (final Edge orderedEdge : orderedEdges) {
                final Node z = orderedEdge.getNode1();
                if (z != x && orderedEdge.getNode2() == y
                        && !graph.isParentOf(z, x)) {
                    compelledEdges[i] = true;
                    for (int k = i + 1; k < graph.getNumEdges(); k++) {
                        if (orderedEdges[k].getNode2() == y
                                && !reversibleEdges[k]) {
                            compelledEdges[k] = true;
                        }
                    }
                    foundZ = true;
                    break;
                }
            }

            if (!foundZ) {
                reversibleEdges[i] = true;

                for (int j = i + 1; j < orderedEdges.length; j++) {
                    if (!compelledEdges[j] && orderedEdges[j].getNode2() == y) {
                        reversibleEdges[j] = true;
                    }
                }
            }
        }

        //undirect edges that are reversible
        for (int i = 0; i < reversibleEdges.length; i++) {
            if (reversibleEdges[i]) {
                graph.setEndpoint(orderedEdges[i].getNode1(),
                        orderedEdges[i].getNode2(), Endpoint.TAIL);
                graph.setEndpoint(orderedEdges[i].getNode2(),
                        orderedEdges[i].getNode1(), Endpoint.TAIL);
            }
        }
    }

    /**
     * @return the cpdag to which the given DAG belongs.
     */
    public static Graph cpdagFromDag(final Graph dag) {
//        IndTestDSep test = new IndTestDSep(dag);
//        return new PC(test).search();
//
        final Graph graph = new EdgeListGraph(dag);
        SearchGraphUtils.basicCPDAG(graph);
        final MeekRules rules = new MeekRules();
        rules.orientImplied(graph);
        return graph;
    }

    public static Graph dagFromCPDAG(final Graph graph) {
        return dagFromCPDAG(graph, null);
    }

    public static Graph dagFromCPDAG(final Graph graph, final IKnowledge knowledge) {
        final Graph dag = new EdgeListGraph(graph);

        for (final Edge edge : dag.getEdges()) {
            if (Edges.isBidirectedEdge(edge)) {
                throw new IllegalArgumentException("That 'cpdag' contains a bidirected edge.");
            }
        }

        if (graph.existsDirectedCycle()) {
            throw new IllegalArgumentException("That 'cpdag' contains a directed cycle.");
        }

        final MeekRules rules = new MeekRules();

        if (knowledge != null) {
            rules.setKnowledge(knowledge);
        }

        rules.setRevertToUnshieldedColliders(false);

        NEXT:
        while (true) {
            for (final Edge edge : dag.getEdges()) {
                final Node x = edge.getNode1();
                final Node y = edge.getNode2();

                if (Edges.isUndirectedEdge(edge) && !graph.isAncestorOf(y, x)) {
                    direct(x, y, dag);
                    rules.orientImplied(dag);
                    continue NEXT;
                }
            }

            break;
        }

        return dag;
    }

    public static Graph cpdagFromECpdag(Graph eCpdag) {
        eCpdag = new EdgeListGraph(eCpdag);

        final MeekRules rules = new MeekRules();
        rules.orientImplied(eCpdag);

        final List<Triple> ambiguousTriples = new ArrayList<>(eCpdag.getAmbiguousTriples());
        removeExtraAmbiguousTriples(eCpdag, new ArrayList<>(ambiguousTriples));

        while (!ambiguousTriples.isEmpty()) {
            final Triple triple = ambiguousTriples.get(0);

            final Node x = triple.getX();
            final Node y = triple.getY();
            final Node z = triple.getZ();

            eCpdag.removeAmbiguousTriple(x, y, z);
            ambiguousTriples.remove(triple);

//            if (!eCpdag.isDefCollider(x, y, z)) {
//                eCpdag.removeEdge(x, y);
//                eCpdag.removeEdge(z, y);
//                eCpdag.addDirectedEdge(x, y);
//                eCpdag.addDirectedEdge(z, y);
//            }

            rules.orientImplied(eCpdag);
            removeExtraAmbiguousTriples(eCpdag, ambiguousTriples);
        }

        return eCpdag;
    }

    private static void removeExtraAmbiguousTriples(final Graph graph, final List<Triple> ambiguousTriples) {
        final Set<Triple> ambiguities = graph.getAmbiguousTriples();

        for (final Triple triple : new HashSet<>(ambiguities)) {
            final Node x = triple.getX();
            final Node y = triple.getY();
            final Node z = triple.getZ();

            if (!graph.isAdjacentTo(x, y) || !graph.isAdjacentTo(y, x)) {
                graph.removeAmbiguousTriple(x, y, z);
                ambiguousTriples.remove(triple);
            }

            if (graph.isDefCollider(x, y, z)) {
                graph.removeAmbiguousTriple(x, y, z);
                ambiguousTriples.remove(triple);
            }

            if (graph.getEdge(x, y).pointsTowards(x) || graph.getEdge(y, z).pointsTowards(z)) {
                graph.removeAmbiguousTriple(x, y, z);
                ambiguousTriples.remove(triple);
            }
        }
    }

    public static Graph bestCpdagFromECpdag(Graph eCpdag, final DataSet dataSet, final int maxCount) {
        eCpdag = new EdgeListGraph(eCpdag);
        Graph out = new EdgeListGraph();

        final MeekRules rules = new MeekRules();
        rules.orientImplied(eCpdag);
        double bestBIC = Double.NEGATIVE_INFINITY;

        final List<Triple> _ambiguousTriples = new ArrayList<>(eCpdag.getAmbiguousTriples());

        for (int c = 0; c < maxCount; c++) {
            final Graph _eCpdag = new EdgeListGraph(eCpdag);

            final List<Triple> ambiguousTriples = new ArrayList<>(_ambiguousTriples);
            Collections.shuffle(ambiguousTriples);

            while (!ambiguousTriples.isEmpty()) {
                final Triple triple = ambiguousTriples.get(0);

                final Node x = triple.getX();
                final Node y = triple.getY();
                final Node z = triple.getZ();

                if (!_eCpdag.isDefCollider(x, y, z)) {
                    _eCpdag.removeEdge(x, y);
                    _eCpdag.removeEdge(z, y);
                    _eCpdag.addDirectedEdge(x, y);
                    _eCpdag.addDirectedEdge(z, y);
                }

                rules.orientImplied(_eCpdag);
                removeExtraAmbiguousTriples(_eCpdag, ambiguousTriples);
            }

            final Graph dag = chooseDagInCpdag(_eCpdag);
            final double bic = SemBicScorer.scoreDag(dag, dataSet);

            if (bic > bestBIC) {
                bestBIC = bic;
                out = _eCpdag;
            }
        }


        return out;
    }

    private static List<Triple> asList(final int[] indices, final List<Triple> nodes) {
        final List<Triple> list = new LinkedList<>();

        for (final int i : indices) {
            list.add(nodes.get(i));
        }

        return list;
    }

    private static void direct(final Node a, final Node c, final Graph graph) {
        final Edge before = graph.getEdge(a, c);
        final Edge after = Edges.directedEdge(a, c);
        graph.removeEdge(before);
        graph.addEdge(after);
    }

    public static Graph pagToMag(final Graph pag) {
        final Graph graph = new EdgeListGraph(pag);
        final SepsetProducer sepsets = new DagSepsets(graph);
        final FciOrient fciOrient = new FciOrient(sepsets);

        while (true) {
            final boolean oriented = orientOneCircle(graph);
            if (!oriented) {
                break;
            }
            fciOrient.doFinalOrientation(graph);
        }

        return graph;
    }

    private static boolean orientOneCircle(final Graph graph) {
        for (final Edge edge : graph.getEdges()) {
            final Node x = edge.getNode1();
            final Node y = edge.getNode2();

            if (graph.getEndpoint(x, y) == Endpoint.CIRCLE) {
                graph.setEndpoint(x, y, Endpoint.ARROW);
                return true;
            }

            if (graph.getEndpoint(y, x) == Endpoint.CIRCLE) {
                graph.setEndpoint(y, x, Endpoint.ARROW);
                return true;
            }
        }

        return false;
    }

    public static void arrangeByKnowledgeTiers(final Graph graph,
                                               final IKnowledge knowledge) {
        if (knowledge.getNumTiers() == 0) {
            throw new IllegalArgumentException("There are no Tiers to arrange.");
        }

        final List<Node> nodes = graph.getNodes();
        int ySpace = 500 / knowledge.getNumTiers();
        ySpace = Math.max(ySpace, 50);

        final List<String> notInTier = knowledge.getVariablesNotInTiers();
        Collections.sort(notInTier);

        int x = 0;
        int y = 50 - ySpace;

        if (notInTier.size() > 0) {
            y += ySpace;

            for (final String name : notInTier) {
                x += 90;
                final Node node = graph.getNode(name);

                if (node != null) {
                    node.setCenterX(x);
                    node.setCenterY(y);
                }
            }
        }

        for (int i = 0; i < knowledge.getNumTiers(); i++) {
            final List<String> tier = knowledge.getTier(i);
            Collections.sort(tier);
            y += ySpace;
            x = -25;

            for (final String name : tier) {
                x += 90;
                final Node node = graph.getNode(name);

                if (node != null) {
                    node.setCenterX(x);
                    node.setCenterY(y);
                }
            }
        }
    }

    public static void arrangeByKnowledgeTiers(final Graph graph) {
        int maxLag = 0;

        for (final Node node : graph.getNodes()) {
            final String name = node.getName();

            final String[] tokens1 = name.split(":");

            final int index = tokens1.length > 1 ? Integer.parseInt(tokens1[tokens1.length - 1]) : 0;

            if (index >= maxLag) {
                maxLag = index;
            }
        }

//        if (maxLag == 0) {
//            GraphUtils.circleLayout(graph, 225, 200, 150);
//            return;
//        }

        final List<List<Node>> tiers = new ArrayList<>();

        for (int i = 0; i <= maxLag; i++) {
            tiers.add(new ArrayList<>());
        }

        for (final Node node : graph.getNodes()) {
            final String name = node.getName();

            final String[] tokens = name.split(":");

            final int index = tokens.length > 1 ? Integer.parseInt(tokens[tokens.length - 1]) : 0;

            if (!tiers.get(index).contains(node)) {
                tiers.get(index).add(node);
            }
        }

        for (int i = 0; i <= maxLag; i++) {
            Collections.sort(tiers.get(i));
        }

        final int ySpace = maxLag == 0 ? 150 : 150 / maxLag;
        int y = 60;

        for (int i = maxLag; i >= 0; i--) {
            final List<Node> tier = tiers.get(i);
            int x = 60;

            for (final Node node : tier) {
                System.out.println(node + " " + x + " " + y);
                node.setCenterX(x);
                node.setCenterY(y);
                x += 90;
            }

            y += ySpace;

        }
    }

    /**
     * Double checks a sepset map against a cpdag to make sure that X is
     * adjacent to Y in the cpdag iff {X, Y} is not in the domain of the
     * sepset map.
     *
     * @param sepset a sepset map, over variables v.
     * @param cpdag  a cpdag over variables W, v subset of W.
     * @return true if the sepset map is consistent with the cpdag.
     */
    public static boolean verifySepsetIntegrity(final SepsetMap sepset, final Graph cpdag) {
        for (final Node x : cpdag.getNodes()) {
            for (final Node y : cpdag.getNodes()) {
                if (x == y) {
                    continue;
                }

                if ((cpdag.isAdjacentTo(y, x)) != (sepset.get(x, y) == null)) {
                    System.out.println("Sepset not consistent with graph for {" + x + ", " + y + "}");
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * @param initialNodes The nodes that reachability undirectedPaths start
     *                     from.
     * @param legalPairs   Specifies initial edges (given initial nodes) and legal
     *                     edge pairs.
     * @param c            a set of vertices (intuitively, the set of variables to be
     *                     conditioned on.
     * @param d            a set of vertices (intuitively to be used in tests of legality,
     *                     for example, the set of ancestors of c).
     * @param graph        the graph with respect to which reachability is
     * @return the set of nodes reachable from the given set of initial nodes in
     * the given graph according to the criteria in the given legal pairs
     * object.
     * <p>
     * A variable v is reachable from initialNodes iff for some variable X in
     * initialNodes thers is a path U [X, Y1, ..., v] such that
     * legalPairs.isLegalFirstNode(X, Y1) and for each [H1, H2, H3] as subpaths
     * of U, legalPairs.isLegalPairs(H1, H2, H3).
     * <p>
     * The algorithm used is a variant of Algorithm 1 from Geiger, Verma, &
     * Pearl (1990).
     */
    public static Set<Node> getReachableNodes(final List<Node> initialNodes,
                                              final LegalPairs legalPairs, final List<Node> c, final List<Node> d, final Graph graph, final int maxPathLength) {
        final HashSet<Node> reachable = new HashSet<>();
        final MultiKeyMap visited = new MultiKeyMap();
        List<ReachabilityEdge> nextEdges = new LinkedList<>();

        for (final Node x : initialNodes) {
            final List<Node> adjX = graph.getAdjacentNodes(x);

            for (final Node y : adjX) {
                if (legalPairs.isLegalFirstEdge(x, y)) {
                    reachable.add(y);
                    nextEdges.add(new ReachabilityEdge(x, y));
                    visited.put(x, y, Boolean.TRUE);
                }
            }
        }

        int pathLength = 1;

        while (nextEdges.size() > 0) {
//            System.out.println("Path length = " + pathLength);
            if (++pathLength > maxPathLength) {
                return reachable;
            }

            final List<ReachabilityEdge> currEdges = nextEdges;
            nextEdges = new LinkedList<>();

            for (final ReachabilityEdge edge : currEdges) {
                final Node x = edge.getFrom();
                final Node y = edge.getTo();
                final List<Node> adjY = graph.getAdjacentNodes(y);

                for (final Node z : adjY) {
                    if ((visited.get(y, z)) == Boolean.TRUE) {
                        continue;
                    }

                    if (legalPairs.isLegalPair(x, y, z, c, d)) {
                        reachable.add(z);

                        nextEdges.add(new ReachabilityEdge(y, z));
                        visited.put(y, z, Boolean.TRUE);
                    }
                }
            }
        }

        return reachable;
    }

    /**
     * @return the string in nodelist which matches string in BK.
     */
    public static Node translate(final String a, final List<Node> nodes) {
        for (final Node node : nodes) {
            if ((node.getName()).equals(a)) {
                return node;
            }
        }

        return null;
    }

    public static List<Set<Node>> powerSet(final List<Node> nodes) {
        final List<Set<Node>> subsets = new ArrayList<>();
        final int total = (int) Math.pow(2, nodes.size());
        for (int i = 0; i < total; i++) {
            final Set<Node> newSet = new HashSet<>();
            final String selection = Integer.toBinaryString(i);
            for (int j = selection.length() - 1; j >= 0; j--) {
                if (selection.charAt(j) == '1') {
                    newSet.add(nodes.get(selection.length() - j - 1));
                }
            }
            subsets.add(newSet);
        }
        return subsets;
    }

    /**
     * Checks if an arrowpoint is allowed by background knowledge.
     */
    public static boolean isArrowpointAllowed1(final Node from, final Node to,
                                               final IKnowledge knowledge) {
        if (knowledge == null) {
            return true;
        }

        return !knowledge.isRequired(to.toString(), from.toString())
                && !knowledge.isForbidden(from.toString(), to.toString());
    }

    public static boolean isArrowpointAllowed2(final Node from, final Node to,
                                               final IKnowledge knowledge, final Graph graph) {
        if (knowledge == null) {
            return true;
        }

        if (!graph.getNodesInTo(to, Endpoint.ARROW).isEmpty()) {
            return false;
        }

        return !knowledge.isRequired(to.toString(), from.toString())
                && !knowledge.isForbidden(from.toString(), to.toString());
    }

    /**
     * Generates the list of DAGs in the given cpdag.
     */
    public static List<Graph> generateCpdagDags(Graph cpdag, final boolean orientBidirectedEdges) {
        if (orientBidirectedEdges) {
            cpdag = GraphUtils.removeBidirectedOrientations(cpdag);
        }

        return getDagsInCpdagMeek(cpdag, new Knowledge2());
    }

    public static List<Graph> getDagsInCpdagMeek(final Graph cpdag, final IKnowledge knowledge) {
        final DagInCPDAGIterator iterator = new DagInCPDAGIterator(cpdag, knowledge);
        final List<Graph> dags = new ArrayList<>();

        while (iterator.hasNext()) {
            final Graph graph = iterator.next();

            try {
                if (knowledge.isViolatedBy(graph)) {
                    continue;
                }

                dags.add(graph);
            } catch (final IllegalArgumentException e) {
                System.out.println("Found a non-DAG: " + graph);
            }
        }

        return dags;
    }

    public static List<Dag> getAllDagsInUndirectedGraph(final Graph graph) {
        final Graph undirected = GraphUtils.undirectedGraph(graph);

        final DagIterator iterator = new DagIterator(undirected);
        final List<Dag> dags = new ArrayList<>();

        while (iterator.hasNext()) {
            final Graph _graph = iterator.next();

            try {
                final Dag dag = new Dag(_graph);
                dags.add(dag);
            } catch (final IllegalArgumentException e) {
                //
            }
        }

        return dags;
    }

    public static List<Graph> getAllGraphsByDirectingUndirectedEdges(final Graph skeleton) {
        final List<Graph> graphs = new ArrayList<>();
        final List<Edge> edges = new ArrayList<>(skeleton.getEdges());

        final List<Integer> undirectedIndices = new ArrayList<>();

        for (int i = 0; i < edges.size(); i++) {
            if (Edges.isUndirectedEdge(edges.get(i))) {
                undirectedIndices.add(i);
            }
        }

        final int[] dims = new int[undirectedIndices.size()];

        for (int i = 0; i < undirectedIndices.size(); i++) {
            dims[i] = 2;
        }

        final CombinationGenerator gen = new CombinationGenerator(dims);
        int[] comb;

        while ((comb = gen.next()) != null) {
            final Graph graph = new EdgeListGraph(skeleton.getNodes());

            for (final Edge edge : edges) {
                if (!Edges.isUndirectedEdge(edge)) {
                    graph.addEdge(edge);
                }
            }

            for (int i = 0; i < undirectedIndices.size(); i++) {
                final Edge edge = edges.get(undirectedIndices.get(i));
                final Node node1 = edge.getNode1();
                final Node node2 = edge.getNode2();

                if (comb[i] == 1) {
                    graph.addEdge(Edges.directedEdge(node1, node2));
                } else {
                    graph.addEdge(Edges.directedEdge(node2, node1));
                }
            }

            graphs.add(graph);
        }

        return graphs;
    }

    public static Graph bestGuessCycleOrientation(final Graph graph, final IndependenceTest test) {
        while (true) {
            final List<Node> cycle = GraphUtils.directedCycle(graph);

            if (cycle == null) {
                break;
            }

            final LinkedList<Node> _cycle = new LinkedList<>(cycle);

            final Node first = _cycle.getFirst();
            final Node last = _cycle.getLast();

            _cycle.addFirst(last);
            _cycle.addLast(first);

            int _j = -1;
            double minP = Double.POSITIVE_INFINITY;

            for (int j = 1; j < _cycle.size() - 1; j++) {
                final int i = j - 1;
                final int k = j + 1;

                final Node x = test.getVariable(_cycle.get(i).getName());
                final Node y = test.getVariable(_cycle.get(j).getName());
                final Node z = test.getVariable(_cycle.get(k).getName());

                test.isIndependent(x, z, Collections.singletonList(y));

                final double p = test.getPValue();

                if (p < minP) {
                    _j = j;
                    minP = p;
                }
            }

            final Node x = _cycle.get(_j - 1);
            final Node y = _cycle.get(_j);
            final Node z = _cycle.get(_j + 1);

            graph.removeEdge(x, y);
            graph.removeEdge(z, y);
            graph.addDirectedEdge(x, y);
            graph.addDirectedEdge(z, y);
        }

        return graph;
    }

    // The published version.
    public static CpcTripleType getCpcTripleType(final Node x, final Node y, final Node z,
                                                 final IndependenceTest test, final int depth,
                                                 final Graph graph, final boolean verbose) {
        int numSepsetsContainingY = 0;
        int numSepsetsNotContainingY = 0;

        List<Node> _nodes = graph.getAdjacentNodes(x);
        _nodes.remove(z);
        TetradLogger.getInstance().log("adjacencies", "Adjacents for " + x + "--" + y + "--" + z + " = " + _nodes);

        int _depth = depth;
        if (_depth == -1) {
            _depth = 1000;
        }
        _depth = Math.min(_depth, _nodes.size());

        for (int d = 0; d <= _depth; d++) {
            final ChoiceGenerator cg = new ChoiceGenerator(_nodes.size(), d);
            int[] choice;

            while ((choice = cg.next()) != null) {
                final List<Node> cond = GraphUtils.asList(choice, _nodes);

                if (test.isIndependent(x, z, cond)) {
                    if (cond.contains(y)) {
                        numSepsetsContainingY++;
                    } else {
                        numSepsetsNotContainingY++;
                    }
                }

                if (numSepsetsContainingY > 0 && numSepsetsNotContainingY > 0) {
                    return CpcTripleType.AMBIGUOUS;
                }
            }
        }

        _nodes = graph.getAdjacentNodes(z);
        _nodes.remove(x);
        TetradLogger.getInstance().log("adjacencies", "Adjacents for " + x + "--" + y + "--" + z + " = " + _nodes);

        if (_depth == -1) {
            _depth = 1000;
        }

        _depth = Math.min(_depth, _nodes.size());

        for (int d = 0; d <= _depth; d++) {
            final ChoiceGenerator cg = new ChoiceGenerator(_nodes.size(), d);
            int[] choice;

            while ((choice = cg.next()) != null) {
                final List<Node> cond = GraphUtils.asList(choice, _nodes);

                if (test.isIndependent(x, z, cond)) {
                    if (cond.contains(y)) {
                        numSepsetsContainingY++;
                    } else {
                        numSepsetsNotContainingY++;
                    }
                }

                if (numSepsetsContainingY > 0 && numSepsetsNotContainingY > 0) {
                    return CpcTripleType.AMBIGUOUS;
                }
            }
        }

        if (numSepsetsContainingY > 0) {
            return CpcTripleType.NONCOLLIDER;
        } else {
            return CpcTripleType.COLLIDER;
        }
    }

    // Using a heuristic cutoff for determining independence, dependence, and ambiguity
    public static CpcTripleType getCpcTripleType2(final Node x, final Node y, final Node z,
                                                  final IndependenceTest test, final int depth,
                                                  final Graph graph) {
        int numSepsetsContainingY = 0;
        int numSepsetsNotContainingY = 0;

        final Set<Set<Node>> withY = new HashSet<>();
        final Set<Set<Node>> withoutY = new HashSet<>();

        List<Node> _nodes = graph.getAdjacentNodes(x);
        _nodes.remove(z);
        TetradLogger.getInstance().log("adjacencies", "Adjacents for " + x + "--" + y + "--" + z + " = " + _nodes);

        int _depth = depth;
        if (_depth == -1) {
            _depth = 1000;
        }
        _depth = Math.min(_depth, _nodes.size());

        for (int d = 0; d <= _depth; d++) {
            final ChoiceGenerator cg = new ChoiceGenerator(_nodes.size(), d);
            int[] choice;

            while ((choice = cg.next()) != null) {
                final List<Node> cond = GraphUtils.asList(choice, _nodes);

                if (test.isIndependent(x, z, cond)) {
                    if (cond.contains(y)) {
                        numSepsetsContainingY++;
                        withY.add(new HashSet<>(cond));
                    } else {
                        numSepsetsNotContainingY++;
                        withoutY.add(new HashSet<>(cond));
                    }
                }
            }
        }

        _nodes = graph.getAdjacentNodes(z);
        _nodes.remove(x);
        TetradLogger.getInstance().log("adjacencies", "Adjacents for " + x + "--" + y + "--" + z + " = " + _nodes);

        _depth = depth;
        if (_depth == -1) {
            _depth = 1000;
        }
        _depth = Math.min(_depth, _nodes.size());

        for (int d = 0; d <= _depth; d++) {
            final ChoiceGenerator cg = new ChoiceGenerator(_nodes.size(), d);
            int[] choice;

            while ((choice = cg.next()) != null) {
                final List<Node> cond = GraphUtils.asList(choice, _nodes);

                if (test.isIndependent(x, z, cond)) {
                    if (cond.contains(y)) {
                        numSepsetsContainingY++;
                        withY.add(new HashSet<>(cond));
                    } else {
                        numSepsetsNotContainingY++;
                        withoutY.add(new HashSet<>(cond));
                    }
                }
            }
        }

        final int factor = 1;

        numSepsetsContainingY = withY.size();
        numSepsetsNotContainingY = withoutY.size();

        if (numSepsetsContainingY > factor * numSepsetsNotContainingY) {
            return CpcTripleType.NONCOLLIDER;
        } else if (numSepsetsNotContainingY > factor * numSepsetsContainingY) {
            return CpcTripleType.COLLIDER;
        } else {
            return CpcTripleType.AMBIGUOUS;
        }
    }

    public static Graph chooseDagInCpdag(final Graph graph) {
        final Graph newGraph = new EdgeListGraph(graph);

        for (final Edge edge : newGraph.getEdges()) {
            if (Edges.isBidirectedEdge(edge)) {
                newGraph.removeEdge(edge);
            }
        }

        final Graph dag = SearchGraphUtils.dagFromCPDAG(newGraph);
        GraphUtils.arrangeBySourceGraph(dag, graph);
        return dag;
    }

    public static Graph chooseMagInPag(Graph graph) {
        graph = new EdgeListGraph(graph);
        final SepsetProducer sepsets = new DagSepsets(graph);

        final FciOrient orient = new FciOrient(sepsets);

        while (true) {
            final boolean oriented = orientCircle(graph);
            if (!oriented) {
                break;
            }
            orient.doFinalOrientation(graph);
        }

        return graph;
    }

    private static boolean orientCircle(final Graph graph) {
        for (final Edge edge : graph.getEdges()) {
            final Node node1 = edge.getNode1();
            final Node node2 = edge.getNode2();

            if (edge.getEndpoint1() == Endpoint.CIRCLE) {
                graph.setEndpoint(node2, node1, Endpoint.ARROW);
                return true;
            }

            if (edge.getEndpoint2() == Endpoint.CIRCLE) {
                graph.setEndpoint(node1, node2, Endpoint.ARROW);
                return true;
            }
        }

        return false;
    }

    public static Graph cpdagForDag(final Graph dag) {
        final Graph cpdag = new EdgeListGraph(dag);
        final MeekRules rules = new MeekRules();
        rules.setRevertToUnshieldedColliders(true);
        rules.orientImplied(cpdag);
        return cpdag;
    }

    /**
     * Tsamardinos, I., Brown, L. E., & Aliferis, C. F. (2006). The max-min hill-climbing Bayesian network structure
     * learning algorithm. Machine learning, 65(1), 31-78.
     * <p>
     * Converts each graph (DAG or CPDAG) into its CPDAG before scoring.
     */
    public static int structuralHammingDistance(Graph trueGraph, Graph estGraph) {
        int shd = 0;

        try {
            estGraph = GraphUtils.replaceNodes(estGraph, trueGraph.getNodes());
            trueGraph = SearchGraphUtils.cpdagForDag(trueGraph);
            estGraph = SearchGraphUtils.cpdagForDag(estGraph);

            // Will check mixedness later.
            if (trueGraph.existsDirectedCycle()) {
                TetradLogger.getInstance().forceLogMessage("SHD failed: True graph couldn't be converted to a CPDAG");
            }

            if (estGraph.existsDirectedCycle()) {
                TetradLogger.getInstance().forceLogMessage("SHD failed: Estimated graph couldn't be converted to a CPDAG");
                return -99;
            }

            final List<Node> _allNodes = estGraph.getNodes();

            for (int i1 = 0; i1 < _allNodes.size(); i1++) {
                for (int i2 = i1 + 1; i2 < _allNodes.size(); i2++) {
                    final Node n1 = _allNodes.get(i1);
                    final Node n2 = _allNodes.get(i2);

                    final Edge e1 = trueGraph.getEdge(n1, n2);
                    final Edge e2 = estGraph.getEdge(n1, n2);

                    if (e1 != null && !(Edges.isDirectedEdge(e1) || Edges.isUndirectedEdge(e1))) {
                        TetradLogger.getInstance().forceLogMessage("SHD failed: True graph couldn't be converted to a CPDAG");
                        return -99;
                    }

                    if (e2 != null && !(Edges.isDirectedEdge(e2) || Edges.isUndirectedEdge(e2))) {
                        TetradLogger.getInstance().forceLogMessage("SHD failed: Estimated graph couldn't be converted to a CPDAG");
                        return -99;
                    }

                    final int error = structuralHammingDistanceOneEdge(e1, e2);
                    shd += error;
                }
            }
        } catch (final Exception e) {
            e.printStackTrace();
            return -99;
        }

        return shd;
    }

    private static int structuralHammingDistanceOneEdge(final Edge e1, final Edge e2) {
        int error = 0;

        if (!(e1 == null && e2 == null)) {
            if (e1 != null && e2 != null) {
                if (!e1.equals(e2)) {
                    System.out.println("Difference " + e1 + " " + e2);
                    error++;
                }
            } else if (e2 == null) {
                if (Edges.isUndirectedEdge(e1)) {
                    error++;
                } else {
                    error++;
                    error++;
                }
            } else {
                if (Edges.isUndirectedEdge(e2)) {
                    error++;
                } else {
                    error++;
                    error++;
                }
            }
        }

        return error;
    }

    private static boolean directed(final Edge e2) {
        return e2 != null && Edges.isDirectedEdge(e2);
    }

    private static boolean bidirected(final Edge e2) {
        return e2 != null && Edges.isBidirectedEdge(e2);
    }

    private static boolean undirected(final Edge e2) {
        return e2 != null && Edges.isUndirectedEdge(e2);
    }

    private static boolean noEdge(final Edge e1) {
        return e1 == null;
    }


    public static GraphUtils.GraphComparison getGraphComparison(Graph graph, final Graph trueGraph) {
        graph = GraphUtils.replaceNodes(graph, trueGraph.getNodes());

        final int adjFn = GraphUtils.countAdjErrors(trueGraph, graph);
        final int adjFp = GraphUtils.countAdjErrors(graph, trueGraph);
        final int adjCorrect = trueGraph.getNumEdges() - adjFn;

        final int arrowptFn = GraphUtils.countArrowptErrors(trueGraph, graph);
        final int arrowptFp = GraphUtils.countArrowptErrors(graph, trueGraph);
        final int arrowptCorrect = GraphUtils.getNumCorrectArrowpts(trueGraph, graph);

        final double adjPrec = (double) adjCorrect / (adjCorrect + adjFp);
        final double adjRec = (double) adjCorrect / (adjCorrect + adjFn);
        final double arrowptPrec = (double) arrowptCorrect / (arrowptCorrect + arrowptFp);
        final double arrowptRec = (double) arrowptCorrect / (arrowptCorrect + arrowptFn);

        final int twoCycleCorrect = 0;
        final int twoCycleFn = 0;
        final int twoCycleFp = 0;

        final List<Edge> edgesAdded = new ArrayList<>();
        final List<Edge> edgesRemoved = new ArrayList<>();
        final List<Edge> edgesReorientedFrom = new ArrayList<>();
        final List<Edge> edgesReorientedTo = new ArrayList<>();
        final List<Edge> correctAdjacency = new ArrayList<>();

        for (final Edge edge : trueGraph.getEdges()) {
            final Node n1 = edge.getNode1();
            final Node n2 = edge.getNode2();
            if (!graph.isAdjacentTo(n1, n2)) {
                final Edge trueGraphEdge = trueGraph.getEdge(n1, n2);
                final Edge graphEdge = graph.getEdge(n1, n2);
                edgesRemoved.add((trueGraphEdge == null) ? graphEdge : trueGraphEdge);
            }
        }

        for (final Edge edge : graph.getEdges()) {
            final Node n1 = edge.getNode1();
            final Node n2 = edge.getNode2();
            if (!trueGraph.isAdjacentTo(n1, n2)) {
                final Edge trueGraphEdge = trueGraph.getEdge(n1, n2);
                final Edge graphEdge = graph.getEdge(n1, n2);
                edgesAdded.add((trueGraphEdge == null) ? graphEdge : trueGraphEdge);
            }
        }

        for (final Edge edge : trueGraph.getEdges()) {
            if (graph.containsEdge(edge)) {
                continue;
            }

            final Node node1 = edge.getNode1();
            final Node node2 = edge.getNode2();

            for (final Edge _edge : graph.getEdges(node1, node2)) {
                final Endpoint e1a = edge.getProximalEndpoint(node1);
                final Endpoint e1b = edge.getProximalEndpoint(node2);
                final Endpoint e2a = _edge.getProximalEndpoint(node1);
                final Endpoint e2b = _edge.getProximalEndpoint(node2);

                if (!((e1a != Endpoint.CIRCLE && e2a != Endpoint.CIRCLE && e1a != e2a)
                        || (e1b != Endpoint.CIRCLE && e2b != Endpoint.CIRCLE && e1b != e2b))) {
                    continue;
                }

                edgesReorientedFrom.add(edge);
                edgesReorientedTo.add(_edge);
            }
        }

        for (final Edge edge : trueGraph.getEdges()) {
            if (graph.isAdjacentTo(edge.getNode1(), edge.getNode2())) {
                correctAdjacency.add(edge);
            }
        }

        final int shd = structuralHammingDistance(trueGraph, graph);

        final int[][] counts = graphComparison(graph, trueGraph, null);

        return new GraphUtils.GraphComparison(
                adjFn, adjFp, adjCorrect, arrowptFn, arrowptFp, arrowptCorrect,
                adjPrec, adjRec, arrowptPrec, arrowptRec, shd,
                twoCycleCorrect, twoCycleFn, twoCycleFp,
                edgesAdded, edgesRemoved, edgesReorientedFrom, edgesReorientedTo,
                correctAdjacency,
                counts);
    }

    /**
     * Just counts arrowpoint errors--for cyclic edges counts an arrowpoint at
     * each node.
     */
    public static GraphUtils.GraphComparison getGraphComparison2(Graph graph, final Graph trueGraph) {
        graph = GraphUtils.replaceNodes(graph, trueGraph.getNodes());
        final GraphUtils.TwoCycleErrors twoCycleErrors = GraphUtils.getTwoCycleErrors(trueGraph, graph);

        final int adjFn = GraphUtils.countAdjErrors(trueGraph, graph);
        final int adjFp = GraphUtils.countAdjErrors(graph, trueGraph);

        final Graph undirectedGraph = GraphUtils.undirectedGraph(graph);
        final int adjCorrect = undirectedGraph.getNumEdges() - adjFp;

        final List<Edge> edgesAdded = new ArrayList<>();
        final List<Edge> edgesRemoved = new ArrayList<>();
        final List<Edge> edgesReorientedFrom = new ArrayList<>();
        final List<Edge> edgesReorientedTo = new ArrayList<>();
        final List<Edge> correctAdjacency = new ArrayList<>();

        for (final Edge edge : trueGraph.getEdges()) {
            final Node n1 = edge.getNode1();
            final Node n2 = edge.getNode2();
            if (!graph.isAdjacentTo(n1, n2)) {
                final Edge trueGraphEdge = trueGraph.getEdge(n1, n2);
                final Edge graphEdge = graph.getEdge(n1, n2);
                edgesRemoved.add((trueGraphEdge == null) ? graphEdge : trueGraphEdge);
            }
        }

        for (final Edge edge : graph.getEdges()) {
            final Node n1 = edge.getNode1();
            final Node n2 = edge.getNode2();
            if (!trueGraph.isAdjacentTo(n1, n2)) {
                final Edge trueGraphEdge = trueGraph.getEdge(n1, n2);
                final Edge graphEdge = graph.getEdge(n1, n2);
                edgesAdded.add((trueGraphEdge == null) ? graphEdge : trueGraphEdge);
            }
        }

        final List<Node> nodes = trueGraph.getNodes();

        int arrowptFn = 0;
        int arrowptFp = 0;
        int arrowptCorrect = 0;

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                if (i == j) {
                    continue;
                }

                final Node x = nodes.get(i);
                final Node y = nodes.get(j);

                final Edge edge = trueGraph.getEdge(x, y);
                final Edge _edge = graph.getEdge(x, y);

                final boolean existsArrow = edge != null && edge.getProximalEndpoint(y) == Endpoint.ARROW;
                final boolean _existsArrow = _edge != null && _edge.getProximalEndpoint(y) == Endpoint.ARROW;

                if (existsArrow && !_existsArrow) {
                    arrowptFn++;
                }

                if (!existsArrow && _existsArrow) {
                    arrowptFp++;
                }

                if (existsArrow && _existsArrow) {
                    arrowptCorrect++;
                }
            }
        }

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                if (i == j) {
                    continue;
                }

                final Node x = nodes.get(i);
                final Node y = nodes.get(j);

                final Node _x = graph.getNode(x.getName());
                final Node _y = graph.getNode(y.getName());

                final Edge edge = trueGraph.getEdge(x, y);
                final Edge _edge = graph.getEdge(_x, _y);

                final boolean existsArrow = edge != null && edge.getDistalEndpoint(y) == Endpoint.ARROW;
                final boolean _existsArrow = _edge != null && _edge.getDistalEndpoint(_y) == Endpoint.ARROW;

                if (existsArrow && !_existsArrow) {
                    arrowptFn++;
                }

                if (!existsArrow && _existsArrow) {
                    arrowptFp++;
                }

                if (existsArrow && _existsArrow) {
                    arrowptCorrect++;
                }
            }
        }

        for (final Edge edge : trueGraph.getEdges()) {
            if (graph.containsEdge(edge)) {
                continue;
            }

            final Node node1 = edge.getNode1();
            final Node node2 = edge.getNode2();

            for (final Edge _edge : graph.getEdges(node1, node2)) {
                final Endpoint e1a = edge.getProximalEndpoint(node1);
                final Endpoint e1b = edge.getProximalEndpoint(node2);
                final Endpoint e2a = _edge.getProximalEndpoint(node1);
                final Endpoint e2b = _edge.getProximalEndpoint(node2);

                if (!((e1a != Endpoint.CIRCLE && e2a != Endpoint.CIRCLE && e1a != e2a)
                        || (e1b != Endpoint.CIRCLE && e2b != Endpoint.CIRCLE && e1b != e2b))) {
                    continue;
                }

                edgesReorientedFrom.add(edge);
                edgesReorientedTo.add(_edge);
            }
        }

        for (final Edge edge : trueGraph.getEdges()) {
            if (graph.isAdjacentTo(edge.getNode1(), edge.getNode2())) {
                correctAdjacency.add(edge);
            }
        }

        final double adjPrec = (double) adjCorrect / (adjCorrect + adjFp);
        final double adjRec = (double) adjCorrect / (adjCorrect + adjFn);
        final double arrowptPrec = (double) arrowptCorrect / (arrowptCorrect + arrowptFp);
        final double arrowptRec = (double) arrowptCorrect / (arrowptCorrect + arrowptFn);

        final int shd = structuralHammingDistance(trueGraph, graph);

        graph = GraphUtils.replaceNodes(graph, trueGraph.getNodes());

        final int[][] counts = GraphUtils.edgeMisclassificationCounts(trueGraph, graph, false);

        return new GraphUtils.GraphComparison(
                adjFn, adjFp, adjCorrect, arrowptFn, arrowptFp, arrowptCorrect,
                adjPrec, adjRec, arrowptPrec, arrowptRec,
                shd,
                twoCycleErrors.twoCycCor, twoCycleErrors.twoCycFn, twoCycleErrors.twoCycFp,
                edgesAdded, edgesRemoved, edgesReorientedFrom, edgesReorientedTo,
                correctAdjacency,
                counts);
    }

    public static String graphComparisonString(final String name1, final Graph graph1, final String name2, Graph graph2, final boolean printStars) {
        final StringBuilder builder = new StringBuilder();
        graph2 = GraphUtils.replaceNodes(graph2, graph1.getNodes());

        final String trueGraphAndTarget = "Target graph from " + name1 + "\nTrue graph from " + name2;
        builder.append(trueGraphAndTarget + "\n");

        final GraphUtils.GraphComparison comparison = getGraphComparison(graph1, graph2);

        final List<Edge> edgesAdded = comparison.getEdgesAdded();

        builder.append("\nEdges added:");

        if (edgesAdded.isEmpty()) {
            builder.append("\n  --NONE--");
        } else {
            for (int i = 0; i < edgesAdded.size(); i++) {
                final Edge edge = edgesAdded.get(i);

                final Node node1 = graph1.getNode(edge.getNode1().getName());
                final Node node2 = graph1.getNode(edge.getNode2().getName());

                builder.append("\n").append(i + 1).append(". ").append(edge);

                if (printStars) {
                    boolean directedInGraph2 = false;

                    if (Edges.isDirectedEdge(edge) && GraphUtils.existsSemidirectedPath(node1, node2, graph2)) {
                        directedInGraph2 = true;
                    } else if ((Edges.isUndirectedEdge(edge) || Edges.isBidirectedEdge(edge))
                            && (GraphUtils.existsSemidirectedPath(node1, node2, graph2)
                            || GraphUtils.existsSemidirectedPath(node2, node1, graph2))) {
                        directedInGraph2 = true;
                    }

                    if (directedInGraph2) {
                        builder.append(" *");
                    }
                }

            }
        }

        builder.append("\n\nEdges removed:");
        final List<Edge> edgesRemoved = comparison.getEdgesRemoved();

        if (edgesRemoved.isEmpty()) {
            builder.append("\n  --NONE--");
        } else {
            for (int i = 0; i < edgesRemoved.size(); i++) {
                final Edge edge = edgesRemoved.get(i);

                final Node node1 = graph2.getNode(edge.getNode1().getName());
                final Node node2 = graph2.getNode(edge.getNode2().getName());

                builder.append("\n").append(i + 1).append(". ").append(edge);

                if (printStars) {
                    boolean directedInGraph1 = false;

                    if (Edges.isDirectedEdge(edge) && GraphUtils.existsSemidirectedPath(node1, node2, graph1)) {
                        directedInGraph1 = true;
                    } else if ((Edges.isUndirectedEdge(edge) || Edges.isBidirectedEdge(edge))
                            && (GraphUtils.existsSemidirectedPath(node1, node2, graph1)
                            || GraphUtils.existsSemidirectedPath(node2, node1, graph1))) {
                        directedInGraph1 = true;
                    }

                    if (directedInGraph1) {
                        builder.append(" *");
                    }
                }
            }
        }

        builder.append("\n\n"
                + "Edges reoriented:");
        final List<Edge> edgesReorientedFrom = comparison.getEdgesReorientedFrom();
        final List<Edge> edgesReorientedTo = comparison.getEdgesReorientedTo();

        if (edgesReorientedFrom.isEmpty()) {
            builder.append("\n  --NONE--");
        } else {
            for (int i = 0; i < edgesReorientedFrom.size(); i++) {
                final Edge from = edgesReorientedFrom.get(i);
                final Edge to = edgesReorientedTo.get(i);
                builder.append("\n").append(i + 1).append(". ").append(from)
                        .append(" ====> ").append(to);
            }
        }

        builder.append("\n\n"
                + "Edges in true correctly adjacent in estimated");

        final List<Edge> correctAdjacies = comparison.getCorrectAdjacencies();

        if (edgesReorientedFrom.isEmpty()) {
            builder.append("\n  --NONE--");
        } else {
            for (int i = 0; i < correctAdjacies.size(); i++) {
                final Edge adj = correctAdjacies.get(i);
                builder.append("\n").append(i + 1).append(". ").append(adj);
            }
        }
        return builder.toString();
    }

    public static int[][] graphComparison(Graph estCpdag, final Graph trueCpdag, final PrintStream out) {
        final GraphUtils.GraphComparison comparison = getGraphComparison2(estCpdag, trueCpdag);

        if (out != null) {
            out.println("Adjacencies:");
        }

        final int adjTp = comparison.getAdjCor();
        final int adjFp = comparison.getAdjFp();
        final int adjFn = comparison.getAdjFn();

        final int arrowptTp = comparison.getAhdCor();
        final int arrowptFp = comparison.getAhdFp();
        final int arrowptFn = comparison.getAhdFn();

        if (out != null) {
            out.println("TP " + adjTp + " FP = " + adjFp + " FN = " + adjFn);
            out.println("Arrow Orientations:");
            out.println("TP " + arrowptTp + " FP = " + arrowptFp + " FN = " + arrowptFn);
        }

        estCpdag = GraphUtils.replaceNodes(estCpdag, trueCpdag.getNodes());

        assert estCpdag != null;
        final int[][] counts = GraphUtils.edgeMisclassificationCounts(trueCpdag, estCpdag, false);

        if (out != null) {
            out.println(GraphUtils.edgeMisclassifications(counts));
        }

        final double adjRecall = adjTp / (double) (adjTp + adjFn);

        final double adjPrecision = adjTp / (double) (adjTp + adjFp);

        final double arrowRecall = arrowptTp / (double) (arrowptTp + arrowptFn);
        final double arrowPrecision = arrowptTp / (double) (arrowptTp + arrowptFp);

        final NumberFormat nf = new DecimalFormat("0.0");

        if (out != null) {
            out.println();
            out.println("APRE\tAREC\tOPRE\tOREC");
            out.println(nf.format(adjPrecision * 100) + "%\t" + nf.format(adjRecall * 100)
                    + "%\t" + nf.format(arrowPrecision * 100) + "%\t" + nf.format(arrowRecall * 100) + "%");
            out.println();
        }

        return counts;
    }

    public static Graph reorient(final Graph graph, final DataModel dataModel, final IKnowledge knowledge) {
        if (dataModel instanceof DataModelList) {
            final DataModelList list = (DataModelList) dataModel;
            final List<DataModel> dataSets = new ArrayList<>(list);
            final Fges images = new Fges(new SemBicScoreImages(dataSets));

            images.setBoundGraph(graph);
            images.setKnowledge(knowledge);
            return images.search();
        } else if (dataModel instanceof DataSet) {
            final DataSet dataSet = (DataSet) dataModel;

            final Score score;

            if (dataModel.isContinuous()) {
                score = new SemBicScore(new CovarianceMatrix(dataSet));
            } else if (dataSet.isDiscrete()) {
                score = new BDeuScore(dataSet);
            } else {
                throw new NullPointerException();
            }

            final Fges ges = new Fges(score);

            ges.setBoundGraph(graph);
            ges.setKnowledge(knowledge);
            return ges.search();
        } else if (dataModel instanceof CovarianceMatrix) {
            final ICovarianceMatrix cov = (CovarianceMatrix) dataModel;
            final Score score = new SemBicScore(cov);

            final Fges ges = new Fges(score);

            ges.setBoundGraph(graph);
            ges.setKnowledge(knowledge);
            return ges.search();
        }

        throw new IllegalStateException("Can do that that reorientation.");
    }

    public enum CpcTripleType {
        COLLIDER, NONCOLLIDER, AMBIGUOUS
    }

    /**
     * Simple class to store edges for the reachability search.
     *
     * @author Joseph Ramsey
     */
    private static class ReachabilityEdge {

        private final Node from;
        private final Node to;

        public ReachabilityEdge(final Node from, final Node to) {
            this.from = from;
            this.to = to;
        }

        public int hashCode() {
            int hash = 17;
            hash += 63 * getFrom().hashCode();
            hash += 71 * getTo().hashCode();
            return hash;
        }

        public boolean equals(final Object obj) {
            if (!(obj instanceof ReachabilityEdge)) return false;

            final ReachabilityEdge edge = (ReachabilityEdge) obj;

            if (!(edge.getFrom().equals(this.getFrom()))) {
                return false;
            }

            return edge.getTo().equals(this.getTo());
        }

        public Node getFrom() {
            return this.from;
        }

        public Node getTo() {
            return this.to;
        }
    }
}
