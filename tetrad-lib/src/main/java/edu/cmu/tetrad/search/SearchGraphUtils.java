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
package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.graph.GraphUtils.TwoCycleErrors;
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
    public static void pcOrientbk(IKnowledge bk, Graph graph, List<Node> nodes) {
        TetradLogger.getInstance().log("details", "Staring BK Orientation.");
        for (Iterator<KnowledgeEdge> it = bk.forbiddenEdgesIterator(); it.hasNext(); ) {
            KnowledgeEdge edge = it.next();

            //match strings to variables in the graph.
            Node from = SearchGraphUtils.translate(edge.getFrom(), nodes);
            Node to = SearchGraphUtils.translate(edge.getTo(), nodes);

            if (from == null || to == null) {
                continue;
            }

            if (graph.getEdge(from, to) == null) {
                continue;
            }

            // Orient to-->from
            graph.removeEdge(from, to);
            graph.addDirectedEdge(to, from);

            TetradLogger.getInstance().log("knowledgeOrientations", SearchLogUtils.edgeOrientedMsg("IKnowledge", graph.getEdge(to, from)));
        }

        for (Iterator<KnowledgeEdge> it = bk.requiredEdgesIterator(); it.hasNext(); ) {
            KnowledgeEdge edge = it.next();

            //match strings to variables in this graph
            Node from = SearchGraphUtils.translate(edge.getFrom(), nodes);
            Node to = SearchGraphUtils.translate(edge.getTo(), nodes);

            if (from == null || to == null) {
                continue;
            }

            if (graph.getEdge(from, to) == null) {
                continue;
            }

            // Orient from-->to
            graph.removeEdges(from, to);
            graph.addDirectedEdge(from, to);

            TetradLogger.getInstance().log("knowledgeOrientations", SearchLogUtils.edgeOrientedMsg("IKnowledge", graph.getEdge(from, to)));
        }

        TetradLogger.getInstance().log("details", "Finishing BK Orientation.");
    }

    /**
     * Performs step C of the algorithm, as indicated on page xxx of CPS, with
     * the modification that X--W--Y is oriented as X--&gt;W&lt;--Y if W is
     * *determined by* the sepset of (X, Y), rather than W just being *in* the
     * sepset of (X, Y).
     */
    public static void pcdOrientC(IndependenceTest test, IKnowledge knowledge, Graph graph) {
        TetradLogger.getInstance().log("info", "Starting Collider Orientation:");

        List<Node> nodes = graph.getNodes();

        for (Node y : nodes) {
            List<Node> adjacentNodes = graph.getAdjacentNodes(y);

            if (adjacentNodes.size() < 2) {
                continue;
            }

            ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null) {
                Node x = adjacentNodes.get(combination[0]);
                Node z = adjacentNodes.get(combination[1]);

                // Skip triples that are shielded.
                if (graph.isAdjacentTo(x, z)) {
                    continue;
                }

                List<Node> sepset = SearchGraphUtils.sepset(graph, x, z, new HashSet<>(), new HashSet<>(),
                        test);

                if (sepset == null) {
                    continue;
                }

                if (sepset.contains(y)) {
                    continue;
                }

                List<Node> augmentedSet = new LinkedList<>(sepset);

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

                if (!SearchGraphUtils.isArrowpointAllowed(x, y, knowledge)
                        || !SearchGraphUtils.isArrowpointAllowed(z, y, knowledge)) {
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

    private static List<Node> sepset(Graph graph, Node a, Node c, Set<Node> containing, Set<Node> notContaining,
                                     IndependenceTest independenceTest) {
        List<Node> adj = graph.getAdjacentNodes(a);
        adj.addAll(graph.getAdjacentNodes(c));
        adj.remove(c);
        adj.remove(a);

        for (int d = 0; d <= Math.min(1000, adj.size()); d++) {
            ChoiceGenerator gen = new ChoiceGenerator(adj.size(), d);
            int[] choice;

            while ((choice = gen.next()) != null) {
                Set<Node> v2 = GraphUtils.asSet(choice, adj);
                v2.addAll(containing);
                v2.removeAll(notContaining);
                v2.remove(a);
                v2.remove(c);

//                    if (isForbidden(a, c, new ArrayList<>(v2)))
                independenceTest.checkIndependence(a, c, new ArrayList<>(v2));
                double p2 = independenceTest.getScore();

                if (p2 < 0) {
                    return new ArrayList<>(v2);
                }
            }
        }

        return null;
    }

    /**
     * Orients using Meek rules, double checking noncolliders locally.
     */
    public static void orientUsingMeekRulesLocally(IKnowledge knowledge,
                                                   Graph graph, IndependenceTest test, int depth) {
        TetradLogger.getInstance().log("info", "Starting Orientation Step D.");
        boolean changed;

        do {
            changed = SearchGraphUtils.meekR1Locally(graph, knowledge, test, depth)
                    || SearchGraphUtils.meekR2(graph, knowledge) || SearchGraphUtils.meekR3(graph, knowledge)
                    || SearchGraphUtils.meekR4(graph, knowledge);
        } while (changed);

        TetradLogger.getInstance().log("info", "Finishing Orientation Step D.");
    }

    /**
     * Step C of PC; orients colliders using specified sepset. That is, orients
     * x *-* y *-* z as x *-&gt; y &lt;-* z just in case y is in Sepset({x, z}).
     */
    public static void orientCollidersUsingSepsets(SepsetMap set, IKnowledge knowledge, Graph graph, boolean verbose,
                                                   boolean enforceCpdag) {
        TetradLogger.getInstance().log("details", "Starting Collider Orientation:");
        List<Node> nodes = graph.getNodes();

        for (Node b : nodes) {
            List<Node> adjacentNodes = graph.getAdjacentNodes(b);

            if (adjacentNodes.size() < 2) {
                continue;
            }

            ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null) {
                Node a = adjacentNodes.get(combination[0]);
                Node c = adjacentNodes.get(combination[1]);

                // Skip triples that are shielded.
                if (graph.isAdjacentTo(a, c)) {
                    continue;
                }

                List<Node> sepset = set.get(a, c);

                //I think the null check needs to be here --AJ
                if (sepset != null && !sepset.contains(b)
                        && SearchGraphUtils.isArrowpointAllowed(a, b, knowledge)
                        && SearchGraphUtils.isArrowpointAllowed(c, b, knowledge)) {
                    if (verbose) {
                        System.out.println("Collider orientation <" + a + ", " + b + ", " + c + "> sepset = " + sepset);
                    }

                    if (enforceCpdag) {
                        if (graph.getEndpoint(b, a) == Endpoint.ARROW || graph.getEndpoint(b, c) == Endpoint.ARROW) {
                            continue;
                        }
                    }

                    graph.removeEdge(a, b);
                    graph.removeEdge(c, b);

                    graph.addDirectedEdge(a, b);
                    graph.addDirectedEdge(c, b);

                    TetradLogger.getInstance().log("colliderOrientations", SearchLogUtils.colliderOrientedMsg(a, b, c, sepset));
                }
            }
        }

        TetradLogger.getInstance().log("details", "Finishing Collider Orientation.");

    }

    public static boolean existsLocalSepsetWithout(Node x, Node y, Node z,
                                                   IndependenceTest test, Graph graph, int depth) {
        Set<Node> __nodes = new HashSet<>(graph.getAdjacentNodes(x));
        __nodes.addAll(graph.getAdjacentNodes(z));
        __nodes.remove(x);
        __nodes.remove(z);
        List<Node> _nodes = new LinkedList<>(__nodes);
        TetradLogger.getInstance().log("adjacencies",
                "Adjacents for " + x + "--" + y + "--" + z + " = " + _nodes);

        int _depth = depth;
        if (_depth == -1) {
            _depth = 1000;
        }
        _depth = Math.min(_depth, _nodes.size());

        for (int d = 0; d <= _depth; d++) {
            if (_nodes.size() >= d) {
                ChoiceGenerator cg2 = new ChoiceGenerator(_nodes.size(), d);
                int[] choice;

                while ((choice = cg2.next()) != null) {
                    List<Node> condSet = GraphUtils.asList(choice, _nodes);

                    if (condSet.contains(y)) {
                        continue;
                    }

                    //            LogUtils.getInstance().finest("Trying " + condSet);
                    if (test.checkIndependence(x, z, condSet).independent()) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Orient away from collider.
     */
    public static boolean meekR1Locally(Graph graph, IKnowledge knowledge,
                                        IndependenceTest test, int depth) {
        List<Node> nodes = graph.getNodes();
        boolean changed = true;

        while (changed) {
            changed = false;

            for (Node a : nodes) {
                List<Node> adjacentNodes = graph.getAdjacentNodes(a);

                if (adjacentNodes.size() < 2) {
                    continue;
                }

                ChoiceGenerator cg
                        = new ChoiceGenerator(adjacentNodes.size(), 2);
                int[] combination;

                while ((combination = cg.next()) != null) {
                    Node b = adjacentNodes.get(combination[0]);
                    Node c = adjacentNodes.get(combination[1]);

                    // Skip triples that are shielded.
                    if (graph.isAdjacentTo(b, c)) {
                        continue;
                    }

                    if (graph.getEndpoint(b, a) == Endpoint.ARROW
                            && graph.isUndirectedFromTo(a, c)) {
                        if (SearchGraphUtils.existsLocalSepsetWithout(b, a, c, test, graph,
                                depth)) {
                            continue;
                        }

                        if (SearchGraphUtils.isArrowpointAllowed(a, c, knowledge)) {
                            graph.setEndpoint(a, c, Endpoint.ARROW);
                            TetradLogger.getInstance().log("impliedOrientation", SearchLogUtils.edgeOrientedMsg("Meek R1", graph.getEdge(a, c)));
                            changed = true;
                        }
                    } else if (graph.getEndpoint(c, a) == Endpoint.ARROW
                            && graph.isUndirectedFromTo(a, b)) {
                        if (SearchGraphUtils.existsLocalSepsetWithout(b, a, c, test, graph,
                                depth)) {
                            continue;
                        }

                        if (SearchGraphUtils.isArrowpointAllowed(a, b, knowledge)) {
                            graph.setEndpoint(a, b, Endpoint.ARROW);
                            TetradLogger.getInstance().log("impliedOrientation", SearchLogUtils.edgeOrientedMsg("Meek R1", graph.getEdge(a, b)));
                            changed = true;
                        }
                    }
                }
            }
        }

        return false;
    }

    /**
     * If
     */
    public static boolean meekR2(Graph graph, IKnowledge knowledge) {
        List<Node> nodes = graph.getNodes();
        final boolean changed = false;

        for (Node a : nodes) {
            List<Node> adjacentNodes = graph.getAdjacentNodes(a);

            if (adjacentNodes.size() < 2) {
                continue;
            }

            ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null) {
                Node b = adjacentNodes.get(combination[0]);
                Node c = adjacentNodes.get(combination[1]);

                if (graph.isDirectedFromTo(b, a)
                        && graph.isDirectedFromTo(a, c)
                        && graph.isUndirectedFromTo(b, c)) {
                    if (SearchGraphUtils.isArrowpointAllowed(b, c, knowledge)) {
                        graph.setEndpoint(b, c, Endpoint.ARROW);
                        TetradLogger.getInstance().log("impliedOrientation", SearchLogUtils.edgeOrientedMsg("Meek R2", graph.getEdge(b, c)));
                    }
                } else if (graph.isDirectedFromTo(c, a)
                        && graph.isDirectedFromTo(a, b)
                        && graph.isUndirectedFromTo(c, b)) {
                    if (SearchGraphUtils.isArrowpointAllowed(c, b, knowledge)) {
                        graph.setEndpoint(c, b, Endpoint.ARROW);
                        TetradLogger.getInstance().log("impliedOrientation", SearchLogUtils.edgeOrientedMsg("Meek R2", graph.getEdge(c, b)));
                    }
                }
            }
        }

        return changed;
    }

    /**
     * Meek's rule R3. If a--b, a--c, a--d, c--&gt;b, c--&gt;b, then orient a--&gt;b.
     */
    public static boolean meekR3(Graph graph, IKnowledge knowledge) {

        List<Node> nodes = graph.getNodes();
        boolean changed = false;

        for (Node a : nodes) {
            List<Node> adjacentNodes = graph.getAdjacentNodes(a);

            if (adjacentNodes.size() < 3) {
                continue;
            }

            for (Node b : adjacentNodes) {
                List<Node> otherAdjacents = new LinkedList<>(adjacentNodes);
                otherAdjacents.remove(b);

                if (!graph.isUndirectedFromTo(a, b)) {
                    continue;
                }

                ChoiceGenerator cg
                        = new ChoiceGenerator(otherAdjacents.size(), 2);
                int[] combination;

                while ((combination = cg.next()) != null) {
                    Node c = otherAdjacents.get(combination[0]);
                    Node d = otherAdjacents.get(combination[1]);

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
                        if (SearchGraphUtils.isArrowpointAllowed(a, b, knowledge)) {
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

    public static boolean meekR4(Graph graph, IKnowledge knowledge) {
        if (knowledge == null) {
            return false;
        }

        List<Node> nodes = graph.getNodes();
        boolean changed = false;

        for (Node a : nodes) {
            List<Node> adjacentNodes = graph.getAdjacentNodes(a);

            if (adjacentNodes.size() < 3) {
                continue;
            }

            for (Node d : adjacentNodes) {
                if (!graph.isAdjacentTo(a, d)) {
                    continue;
                }

                List<Node> otherAdjacents = new LinkedList<>(adjacentNodes);
                otherAdjacents.remove(d);

                ChoiceGenerator cg
                        = new ChoiceGenerator(otherAdjacents.size(), 2);
                int[] combination;

                while ((combination = cg.next()) != null) {
                    Node b = otherAdjacents.get(combination[0]);
                    Node c = otherAdjacents.get(combination[1]);

                    if (!graph.isUndirectedFromTo(a, b)) {
                        continue;
                    }

                    if (!graph.isUndirectedFromTo(a, c)) {
                        continue;
                    }

                    if (graph.isDirectedFromTo(b, c)
                            && graph.isDirectedFromTo(d, c)) {
                        if (SearchGraphUtils.isArrowpointAllowed(a, c, knowledge)) {
                            graph.setEndpoint(a, c, Endpoint.ARROW);
                            TetradLogger.getInstance().log("impliedOrientation", SearchLogUtils.edgeOrientedMsg("Meek T1", graph.getEdge(a, c)));
                            changed = true;
                            break;
                        }
                    } else if (graph.isDirectedFromTo(c, d)
                            && graph.isDirectedFromTo(d, b)) {
                        if (SearchGraphUtils.isArrowpointAllowed(a, b, knowledge)) {
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

    /**
     * Checks if an arrowpoint is allowed by background knowledge.
     */
    public static boolean isArrowpointAllowed(Object from, Object to,
                                              IKnowledge knowledge) {
        if (knowledge == null) {
            return true;
        }
        return !knowledge.isRequired(to.toString(), from.toString())
                && !knowledge.isForbidden(from.toString(), to.toString());
    }

    /**
     * Get a graph and direct only the unshielded colliders.
     */
    public static void basicCPDAG(Graph graph) {
        Set<Edge> undirectedEdges = new HashSet<>();

        NEXT_EDGE:
        for (Edge edge : graph.getEdges()) {
            if (!edge.isDirected()) {
                continue;
            }

            Node x = Edges.getDirectedEdgeTail(edge);
            Node y = Edges.getDirectedEdgeHead(edge);

            for (Node parent : graph.getParents(y)) {
                if (parent != x) {
                    if (!graph.isAdjacentTo(parent, x)) {
                        continue NEXT_EDGE;
                    }
                }
            }

            undirectedEdges.add(edge);
        }

        for (Edge nextUndirected : undirectedEdges) {
            Node node1 = nextUndirected.getNode1();
            Node node2 = nextUndirected.getNode2();

            graph.removeEdges(node1, node2);
            graph.addUndirectedEdge(node1, node2);
        }
    }

    public static void basicCpdagRestricted2(Graph graph, Node node) {
        Set<Edge> undirectedEdges = new HashSet<>();

        NEXT_EDGE:
        for (Edge edge : graph.getEdges(node)) {
            if (!edge.isDirected()) {
                continue;
            }

            Node _x = Edges.getDirectedEdgeTail(edge);
            Node _y = Edges.getDirectedEdgeHead(edge);

            for (Node parent : graph.getParents(_y)) {
                if (parent != _x) {
                    if (!graph.isAdjacentTo(parent, _x)) {
                        continue NEXT_EDGE;
                    }
                }
            }

            undirectedEdges.add(edge);
        }

        for (Edge nextUndirected : undirectedEdges) {
            Node node1 = nextUndirected.getNode1();
            Node node2 = nextUndirected.getNode2();

            graph.removeEdge(nextUndirected);
            graph.addUndirectedEdge(node1, node2);
        }
    }

    /**
     * @return the cpdag to which the given DAG belongs.
     */
    public static Graph cpdagFromDag(Graph dag) {
        Graph graph = new EdgeListGraph(dag);
        SearchGraphUtils.basicCPDAG(graph);
        MeekRules rules = new MeekRules();
        rules.orientImplied(graph);
        return graph;
    }

    public static Graph dagFromCPDAG(Graph graph) {
        return SearchGraphUtils.dagFromCPDAG(graph, null);
    }

    public static Graph dagFromCPDAG(Graph graph, IKnowledge knowledge) {
        Graph dag = new EdgeListGraph(graph);

        for (Edge edge : dag.getEdges()) {
            if (Edges.isBidirectedEdge(edge)) {
                throw new IllegalArgumentException("That 'cpdag' contains a bidirected edge.");
            }
        }

        // FGES with incoherent forbidden knowledge may produce a cycle
//        if (graph.existsDirectedCycle()) {
//            throw new IllegalArgumentException("That 'cpdag' contains a directed cycle.");
//        }

        MeekRules rules = new MeekRules();

        if (knowledge != null) {
            rules.setKnowledge(knowledge);
        }

        rules.setRevertToUnshieldedColliders(false);

        NEXT:
        while (true) {
            for (Edge edge : dag.getEdges()) {
                Node x = edge.getNode1();
                Node y = edge.getNode2();

                if (Edges.isUndirectedEdge(edge) && !graph.isAncestorOf(y, x)) {
                    SearchGraphUtils.direct(x, y, dag);
                    rules.orientImplied(dag);
                    continue NEXT;
                }
            }

            break;
        }

        return dag;
    }

    private static void direct(Node a, Node c, Graph graph) {
        Edge before = graph.getEdge(a, c);
        Edge after = Edges.directedEdge(a, c);
        graph.removeEdge(before);
        graph.addEdge(after);
    }

    public static Graph pagToMag(Graph pag) {
        Graph graph = new EdgeListGraph(pag);
        SepsetProducer sepsets = new DagSepsets(graph);
        FciOrient fciOrient = new FciOrient(sepsets);

        while (true) {
            boolean oriented = SearchGraphUtils.orientOneCircle(graph);
            if (!oriented) {
                break;
            }
            fciOrient.doFinalOrientation(graph);
        }

        return graph;
    }

    private static boolean orientOneCircle(Graph graph) {
        for (Edge edge : graph.getEdges()) {
            Node x = edge.getNode1();
            Node y = edge.getNode2();

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

    public static void arrangeByKnowledgeTiers(Graph graph,
                                               IKnowledge knowledge) {
        if (knowledge.getNumTiers() == 0) {
            throw new IllegalArgumentException("There are no Tiers to arrange.");
        }

        int ySpace = 500 / knowledge.getNumTiers();
        ySpace = max(ySpace, 50);

        List<String> notInTier = knowledge.getVariablesNotInTiers();
        Collections.sort(notInTier);

        int x = 0;
        int y = 50 - ySpace;

        if (notInTier.size() > 0) {
            y += ySpace;

            for (String name : notInTier) {
                x += 90;
                Node node = graph.getNode(name);

                if (node != null) {
                    node.setCenterX(x);
                    node.setCenterY(y);
                }
            }
        }

        for (int i = 0; i < knowledge.getNumTiers(); i++) {
            List<String> tier = knowledge.getTier(i);
            Collections.sort(tier);
            y += ySpace;
            x = -25;

            for (String name : tier) {
                x += 90;
                Node node = graph.getNode(name);

                if (node != null) {
                    node.setCenterX(x);
                    node.setCenterY(y);
                }
            }
        }
    }

    public static void arrangeByKnowledgeTiers(Graph graph) {
        int maxLag = 0;

        for (Node node : graph.getNodes()) {
            String name = node.getName();

            String[] tokens1 = name.split(":");

            int index = tokens1.length > 1 ? Integer.parseInt(tokens1[tokens1.length - 1]) : 0;

            if (index >= maxLag) {
                maxLag = index;
            }
        }

        List<List<Node>> tiers = new ArrayList<>();

        for (int i = 0; i <= maxLag; i++) {
            tiers.add(new ArrayList<>());
        }

        for (Node node : graph.getNodes()) {
            String name = node.getName();

            String[] tokens = name.split(":");

            int index = tokens.length > 1 ? Integer.parseInt(tokens[tokens.length - 1]) : 0;

            if (!tiers.get(index).contains(node)) {
                tiers.get(index).add(node);
            }
        }

        for (int i = 0; i <= maxLag; i++) {
            Collections.sort(tiers.get(i));
        }

        int ySpace = maxLag == 0 ? 150 : 150 / maxLag;
        int y = 60;

        for (int i = maxLag; i >= 0; i--) {
            List<Node> tier = tiers.get(i);
            int x = 60;

            for (Node node : tier) {
                System.out.println(node + " " + x + " " + y);
                node.setCenterX(x);
                node.setCenterY(y);
                x += 90;
            }

            y += ySpace;

        }
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
     * The algorithm used is a variant of Algorithm 1 from Geiger, Verma, and
     * Pearl (1990).
     */
    public static Set<Node> getReachableNodes(List<Node> initialNodes,
                                              LegalPairs legalPairs, List<Node> c, List<Node> d, Graph graph, int maxPathLength) {
        HashSet<Node> reachable = new HashSet<>();
        MultiKeyMap visited = new MultiKeyMap();
        List<ReachabilityEdge> nextEdges = new LinkedList<>();

        for (Node x : initialNodes) {
            List<Node> adjX = graph.getAdjacentNodes(x);

            for (Node y : adjX) {
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

            List<ReachabilityEdge> currEdges = nextEdges;
            nextEdges = new LinkedList<>();

            for (ReachabilityEdge edge : currEdges) {
                Node x = edge.getFrom();
                Node y = edge.getTo();
                List<Node> adjY = graph.getAdjacentNodes(y);

                for (Node z : adjY) {
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
    public static Node translate(String a, List<Node> nodes) {
        for (Node node : nodes) {
            if ((node.getName()).equals(a)) {
                return node;
            }
        }

        return null;
    }

    public static List<Set<Node>> powerSet(List<Node> nodes) {
        List<Set<Node>> subsets = new ArrayList<>();
        int total = (int) Math.pow(2, nodes.size());
        for (int i = 0; i < total; i++) {
            Set<Node> newSet = new HashSet<>();
            String selection = Integer.toBinaryString(i);
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
    public static boolean isArrowpointAllowed1(Node from, Node to,
                                               IKnowledge knowledge) {
        if (knowledge == null) {
            return true;
        }

        return !knowledge.isRequired(to.toString(), from.toString())
                && !knowledge.isForbidden(from.toString(), to.toString());
    }

    /**
     * Generates the list of DAGs in the given cpdag.
     */
    public static List<Graph> generateCpdagDags(Graph cpdag, boolean orientBidirectedEdges) {
        if (orientBidirectedEdges) {
            cpdag = GraphUtils.removeBidirectedOrientations(cpdag);
        }

        return SearchGraphUtils.getDagsInCpdagMeek(cpdag, new Knowledge2());
    }

    public static List<Graph> getDagsInCpdagMeek(Graph cpdag, IKnowledge knowledge) {
        DagInCPDAGIterator iterator = new DagInCPDAGIterator(cpdag, knowledge);
        List<Graph> dags = new ArrayList<>();

        while (iterator.hasNext()) {
            Graph graph = iterator.next();

            try {
                if (knowledge.isViolatedBy(graph)) {
                    continue;
                }

                dags.add(graph);
            } catch (IllegalArgumentException e) {
                System.out.println("Found a non-DAG: " + graph);
            }
        }

        return dags;
    }

    public static List<Graph> getAllGraphsByDirectingUndirectedEdges(Graph skeleton) {
        List<Graph> graphs = new ArrayList<>();
        List<Edge> edges = new ArrayList<>(skeleton.getEdges());

        List<Integer> undirectedIndices = new ArrayList<>();

        for (int i = 0; i < edges.size(); i++) {
            if (Edges.isUndirectedEdge(edges.get(i))) {
                undirectedIndices.add(i);
            }
        }

        int[] dims = new int[undirectedIndices.size()];

        for (int i = 0; i < undirectedIndices.size(); i++) {
            dims[i] = 2;
        }

        CombinationGenerator gen = new CombinationGenerator(dims);
        int[] comb;

        while ((comb = gen.next()) != null) {
            Graph graph = new EdgeListGraph(skeleton.getNodes());

            for (Edge edge : edges) {
                if (!Edges.isUndirectedEdge(edge)) {
                    graph.addEdge(edge);
                }
            }

            for (int i = 0; i < undirectedIndices.size(); i++) {
                Edge edge = edges.get(undirectedIndices.get(i));
                Node node1 = edge.getNode1();
                Node node2 = edge.getNode2();

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

    // The published version.
    public static CpcTripleType getCpcTripleType(Node x, Node y, Node z,
                                                 IndependenceTest test, int depth,
                                                 Graph graph) {
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
            ChoiceGenerator cg = new ChoiceGenerator(_nodes.size(), d);
            int[] choice;

            while ((choice = cg.next()) != null) {
                List<Node> cond = GraphUtils.asList(choice, _nodes);

                if (test.checkIndependence(x, z, cond).independent()) {
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

        _depth = Math.min(_depth, _nodes.size());

        for (int d = 0; d <= _depth; d++) {
            ChoiceGenerator cg = new ChoiceGenerator(_nodes.size(), d);
            int[] choice;

            while ((choice = cg.next()) != null) {
                List<Node> cond = GraphUtils.asList(choice, _nodes);

                if (test.checkIndependence(x, z, cond).independent()) {
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

    public static Graph cpdagForDag(Graph dag) {
        Graph cpdag = new EdgeListGraph(dag);
        MeekRules rules = new MeekRules();
        rules.setRevertToUnshieldedColliders(true);
        rules.orientImplied(cpdag);
        return cpdag;
    }

    /**
     * Tsamardinos, I., Brown, L. E., and Aliferis, C. F. (2006). The max-min hill-climbing Bayesian network structure
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

            List<Node> _allNodes = estGraph.getNodes();

            for (int i1 = 0; i1 < _allNodes.size(); i1++) {
                for (int i2 = i1 + 1; i2 < _allNodes.size(); i2++) {
                    Node n1 = _allNodes.get(i1);
                    Node n2 = _allNodes.get(i2);

                    Edge e1 = trueGraph.getEdge(n1, n2);
                    Edge e2 = estGraph.getEdge(n1, n2);

                    if (e1 != null && !(Edges.isDirectedEdge(e1) || Edges.isUndirectedEdge(e1))) {
                        TetradLogger.getInstance().forceLogMessage("SHD failed: True graph couldn't be converted to a CPDAG");
                        return -99;
                    }

                    if (e2 != null && !(Edges.isDirectedEdge(e2) || Edges.isUndirectedEdge(e2))) {
                        TetradLogger.getInstance().forceLogMessage("SHD failed: Estimated graph couldn't be converted to a CPDAG");
                        return -99;
                    }

                    int error = SearchGraphUtils.structuralHammingDistanceOneEdge(e1, e2);
                    shd += error;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return -99;
        }

        return shd;
    }

    private static int structuralHammingDistanceOneEdge(Edge e1, Edge e2) {
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

    public static GraphUtils.GraphComparison getGraphComparison(Graph graph, Graph trueGraph) {
        graph = GraphUtils.replaceNodes(graph, trueGraph.getNodes());

        int adjFn = GraphUtils.countAdjErrors(trueGraph, graph);
        int adjFp = GraphUtils.countAdjErrors(graph, trueGraph);
        int adjCorrect = trueGraph.getNumEdges() - adjFn;

        int arrowptFn = GraphUtils.countArrowptErrors(trueGraph, graph);
        int arrowptFp = GraphUtils.countArrowptErrors(graph, trueGraph);
        int arrowptCorrect = GraphUtils.getNumCorrectArrowpts(trueGraph, graph);

        double adjPrec = (double) adjCorrect / (adjCorrect + adjFp);
        double adjRec = (double) adjCorrect / (adjCorrect + adjFn);
        double arrowptPrec = (double) arrowptCorrect / (arrowptCorrect + arrowptFp);
        double arrowptRec = (double) arrowptCorrect / (arrowptCorrect + arrowptFn);

        final int twoCycleCorrect = 0;
        final int twoCycleFn = 0;
        final int twoCycleFp = 0;

        List<Edge> edgesAdded = new ArrayList<>();
        List<Edge> edgesRemoved = new ArrayList<>();
        List<Edge> edgesReorientedFrom = new ArrayList<>();
        List<Edge> edgesReorientedTo = new ArrayList<>();
        List<Edge> correctAdjacency = new ArrayList<>();

        for (Edge edge : trueGraph.getEdges()) {
            Node n1 = edge.getNode1();
            Node n2 = edge.getNode2();
            if (!graph.isAdjacentTo(n1, n2)) {
                Edge trueGraphEdge = trueGraph.getEdge(n1, n2);
                Edge graphEdge = graph.getEdge(n1, n2);
                edgesRemoved.add((trueGraphEdge == null) ? graphEdge : trueGraphEdge);
            }
        }

        for (Edge edge : graph.getEdges()) {
            Node n1 = edge.getNode1();
            Node n2 = edge.getNode2();
            if (!trueGraph.isAdjacentTo(n1, n2)) {
                Edge trueGraphEdge = trueGraph.getEdge(n1, n2);
                Edge graphEdge = graph.getEdge(n1, n2);
                edgesAdded.add((trueGraphEdge == null) ? graphEdge : trueGraphEdge);
            }
        }

        for (Edge edge : trueGraph.getEdges()) {
            if (graph.containsEdge(edge)) {
                continue;
            }

            Node node1 = edge.getNode1();
            Node node2 = edge.getNode2();

            for (Edge _edge : graph.getEdges(node1, node2)) {
                Endpoint e1a = edge.getProximalEndpoint(node1);
                Endpoint e1b = edge.getProximalEndpoint(node2);
                Endpoint e2a = _edge.getProximalEndpoint(node1);
                Endpoint e2b = _edge.getProximalEndpoint(node2);

                if (!((e1a != Endpoint.CIRCLE && e2a != Endpoint.CIRCLE && e1a != e2a)
                        || (e1b != Endpoint.CIRCLE && e2b != Endpoint.CIRCLE && e1b != e2b))) {
                    continue;
                }

                edgesReorientedFrom.add(edge);
                edgesReorientedTo.add(_edge);
            }
        }

        for (Edge edge : trueGraph.getEdges()) {
            if (graph.isAdjacentTo(edge.getNode1(), edge.getNode2())) {
                correctAdjacency.add(edge);
            }
        }

        int shd = structuralHammingDistance(trueGraph, graph);

        int[][] counts = graphComparison(graph, trueGraph, null);

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
    public static GraphUtils.GraphComparison getGraphComparison2(Graph graph, Graph trueGraph) {
        graph = GraphUtils.replaceNodes(graph, trueGraph.getNodes());
        TwoCycleErrors twoCycleErrors = GraphUtils.getTwoCycleErrors(trueGraph, graph);

        int adjFn = GraphUtils.countAdjErrors(trueGraph, graph);
        int adjFp = GraphUtils.countAdjErrors(graph, trueGraph);

        Graph undirectedGraph = GraphUtils.undirectedGraph(graph);
        int adjCorrect = undirectedGraph.getNumEdges() - adjFp;

        List<Edge> edgesAdded = new ArrayList<>();
        List<Edge> edgesRemoved = new ArrayList<>();
        List<Edge> edgesReorientedFrom = new ArrayList<>();
        List<Edge> edgesReorientedTo = new ArrayList<>();
        List<Edge> correctAdjacency = new ArrayList<>();

        for (Edge edge : trueGraph.getEdges()) {
            Node n1 = edge.getNode1();
            Node n2 = edge.getNode2();
            if (!graph.isAdjacentTo(n1, n2)) {
                Edge trueGraphEdge = trueGraph.getEdge(n1, n2);
                Edge graphEdge = graph.getEdge(n1, n2);
                edgesRemoved.add((trueGraphEdge == null) ? graphEdge : trueGraphEdge);
            }
        }

        for (Edge edge : graph.getEdges()) {
            Node n1 = edge.getNode1();
            Node n2 = edge.getNode2();
            if (!trueGraph.isAdjacentTo(n1, n2)) {
                Edge trueGraphEdge = trueGraph.getEdge(n1, n2);
                Edge graphEdge = graph.getEdge(n1, n2);
                edgesAdded.add((trueGraphEdge == null) ? graphEdge : trueGraphEdge);
            }
        }

        List<Node> nodes = trueGraph.getNodes();

        int arrowptFn = 0;
        int arrowptFp = 0;
        int arrowptCorrect = 0;

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                if (i == j) {
                    continue;
                }

                Node x = nodes.get(i);
                Node y = nodes.get(j);

                Edge edge = trueGraph.getEdge(x, y);
                Edge _edge = graph.getEdge(x, y);

                boolean existsArrow = edge != null && edge.getProximalEndpoint(y) == Endpoint.ARROW;
                boolean _existsArrow = _edge != null && _edge.getProximalEndpoint(y) == Endpoint.ARROW;

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

                Node x = nodes.get(i);
                Node y = nodes.get(j);

                Node _x = graph.getNode(x.getName());
                Node _y = graph.getNode(y.getName());

                Edge edge = trueGraph.getEdge(x, y);
                Edge _edge = graph.getEdge(_x, _y);

                boolean existsArrow = edge != null && edge.getDistalEndpoint(y) == Endpoint.ARROW;
                boolean _existsArrow = _edge != null && _edge.getDistalEndpoint(_y) == Endpoint.ARROW;

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

        for (Edge edge : trueGraph.getEdges()) {
            if (graph.containsEdge(edge)) {
                continue;
            }

            Node node1 = edge.getNode1();
            Node node2 = edge.getNode2();

            for (Edge _edge : graph.getEdges(node1, node2)) {
                Endpoint e1a = edge.getProximalEndpoint(node1);
                Endpoint e1b = edge.getProximalEndpoint(node2);
                Endpoint e2a = _edge.getProximalEndpoint(node1);
                Endpoint e2b = _edge.getProximalEndpoint(node2);

                if (!((e1a != Endpoint.CIRCLE && e2a != Endpoint.CIRCLE && e1a != e2a)
                        || (e1b != Endpoint.CIRCLE && e2b != Endpoint.CIRCLE && e1b != e2b))) {
                    continue;
                }

                edgesReorientedFrom.add(edge);
                edgesReorientedTo.add(_edge);
            }
        }

        for (Edge edge : trueGraph.getEdges()) {
            if (graph.isAdjacentTo(edge.getNode1(), edge.getNode2())) {
                correctAdjacency.add(edge);
            }
        }

        double adjPrec = (double) adjCorrect / (adjCorrect + adjFp);
        double adjRec = (double) adjCorrect / (adjCorrect + adjFn);
        double arrowptPrec = (double) arrowptCorrect / (arrowptCorrect + arrowptFp);
        double arrowptRec = (double) arrowptCorrect / (arrowptCorrect + arrowptFn);

        int shd = structuralHammingDistance(trueGraph, graph);

        graph = GraphUtils.replaceNodes(graph, trueGraph.getNodes());

        int[][] counts = GraphUtils.edgeMisclassificationCounts(trueGraph, graph, false);

        return new GraphUtils.GraphComparison(
                adjFn, adjFp, adjCorrect, arrowptFn, arrowptFp, arrowptCorrect,
                adjPrec, adjRec, arrowptPrec, arrowptRec,
                shd,
                twoCycleErrors.twoCycCor, twoCycleErrors.twoCycFn, twoCycleErrors.twoCycFp,
                edgesAdded, edgesRemoved, edgesReorientedFrom, edgesReorientedTo,
                correctAdjacency,
                counts);
    }

    public static String graphComparisonString(String name1, Graph graph1, String name2, Graph graph2, boolean printStars) {
        StringBuilder builder = new StringBuilder();
        graph2 = GraphUtils.replaceNodes(graph2, graph1.getNodes());

        String trueGraphAndTarget = "Target graph from " + name1 + "\nTrue graph from " + name2;
        builder.append(trueGraphAndTarget).append("\n");

        GraphUtils.GraphComparison comparison = getGraphComparison(graph1, graph2);

        List<Edge> edgesAdded = comparison.getEdgesAdded();

        builder.append("\nEdges added:");

        if (edgesAdded.isEmpty()) {
            builder.append("\n  --NONE--");
        } else {
            for (int i = 0; i < edgesAdded.size(); i++) {
                Edge edge = edgesAdded.get(i);

                Node node1 = graph1.getNode(edge.getNode1().getName());
                Node node2 = graph1.getNode(edge.getNode2().getName());

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
        List<Edge> edgesRemoved = comparison.getEdgesRemoved();

        if (edgesRemoved.isEmpty()) {
            builder.append("\n  --NONE--");
        } else {
            for (int i = 0; i < edgesRemoved.size(); i++) {
                Edge edge = edgesRemoved.get(i);

                Node node1 = graph2.getNode(edge.getNode1().getName());
                Node node2 = graph2.getNode(edge.getNode2().getName());

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
        List<Edge> edgesReorientedFrom = comparison.getEdgesReorientedFrom();
        List<Edge> edgesReorientedTo = comparison.getEdgesReorientedTo();

        if (edgesReorientedFrom.isEmpty()) {
            builder.append("\n  --NONE--");
        } else {
            for (int i = 0; i < edgesReorientedFrom.size(); i++) {
                Edge from = edgesReorientedFrom.get(i);
                Edge to = edgesReorientedTo.get(i);
                builder.append("\n").append(i + 1).append(". ").append(from)
                        .append(" ====> ").append(to);
            }
        }

        builder.append("\n\n"
                + "Edges in true correctly adjacent in estimated");

        List<Edge> correctAdjacies = comparison.getCorrectAdjacencies();

        if (edgesReorientedFrom.isEmpty()) {
            builder.append("\n  --NONE--");
        } else {
            for (int i = 0; i < correctAdjacies.size(); i++) {
                Edge adj = correctAdjacies.get(i);
                builder.append("\n").append(i + 1).append(". ").append(adj);
            }
        }
        return builder.toString();
    }

    public static int[][] graphComparison(Graph estCpdag, Graph trueCpdag, PrintStream out) {
        GraphUtils.GraphComparison comparison = SearchGraphUtils.getGraphComparison2(estCpdag, trueCpdag);

        if (out != null) {
            out.println("Adjacencies:");
        }

        int adjTp = comparison.getAdjCor();
        int adjFp = comparison.getAdjFp();
        int adjFn = comparison.getAdjFn();

        int arrowptTp = comparison.getAhdCor();
        int arrowptFp = comparison.getAhdFp();
        int arrowptFn = comparison.getAhdFn();

        if (out != null) {
            out.println("TP " + adjTp + " FP = " + adjFp + " FN = " + adjFn);
            out.println("Arrow Orientations:");
            out.println("TP " + arrowptTp + " FP = " + arrowptFp + " FN = " + arrowptFn);
        }

        estCpdag = GraphUtils.replaceNodes(estCpdag, trueCpdag.getNodes());

        int[][] counts = GraphUtils.edgeMisclassificationCounts(trueCpdag, estCpdag, false);

        if (out != null) {
            out.println(GraphUtils.edgeMisclassifications(counts));
        }

        double adjRecall = adjTp / (double) (adjTp + adjFn);

        double adjPrecision = adjTp / (double) (adjTp + adjFp);

        double arrowRecall = arrowptTp / (double) (arrowptTp + arrowptFn);
        double arrowPrecision = arrowptTp / (double) (arrowptTp + arrowptFp);

        NumberFormat nf = new DecimalFormat("0.0");

        if (out != null) {
            out.println();
            out.println("APRE\tAREC\tOPRE\tOREC");
            out.println(nf.format(adjPrecision * 100) + "%\t" + nf.format(adjRecall * 100)
                    + "%\t" + nf.format(arrowPrecision * 100) + "%\t" + nf.format(arrowRecall * 100) + "%");
            out.println();
        }

        return counts;
    }

    public static Graph reorient(Graph graph, DataModel dataModel, IKnowledge knowledge) {
        if (dataModel instanceof DataModelList) {
            DataModelList list = (DataModelList) dataModel;
            List<DataModel> dataSets = new ArrayList<>(list);
            Fges images = new Fges(new SemBicScoreImages(dataSets));

            images.setBoundGraph(graph);
            images.setKnowledge(knowledge);
            return images.search();
        } else if (dataModel instanceof DataSet) {
            DataSet dataSet = (DataSet) dataModel;

            Score score;

            if (dataModel.isContinuous()) {
                score = new SemBicScore(new CovarianceMatrix(dataSet));
            } else if (dataSet.isDiscrete()) {
                score = new BDeuScore(dataSet);
            } else {
                throw new NullPointerException();
            }

            Fges ges = new Fges(score);

            ges.setBoundGraph(graph);
            ges.setKnowledge(knowledge);
            return ges.search();
        } else if (dataModel instanceof CovarianceMatrix) {
            ICovarianceMatrix cov = (CovarianceMatrix) dataModel;
            Score score = new SemBicScore(cov);

            Fges ges = new Fges(score);

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

        public ReachabilityEdge(Node from, Node to) {
            this.from = from;
            this.to = to;
        }

        public int hashCode() {
            int hash = 17;
            hash += 63 * getFrom().hashCode();
            hash += 71 * getTo().hashCode();
            return hash;
        }

        public boolean equals(Object obj) {
            if (!(obj instanceof ReachabilityEdge)) return false;

            ReachabilityEdge edge = (ReachabilityEdge) obj;

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
