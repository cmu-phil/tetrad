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
package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.data.KnowledgeEdge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.graph.GraphUtils.TwoCycleErrors;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.CombinationGenerator;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.collections4.map.MultiKeyMap;
import org.apache.commons.math3.util.FastMath;
import org.jetbrains.annotations.NotNull;

import java.io.PrintStream;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;

import static java.util.Collections.sort;
import static org.apache.commons.math3.util.FastMath.max;

/**
 * Provides some graph utilities for search algorithm.
 *
 * @author josephramsey
 */
public final class GraphSearchUtils {

    /**
     * Orients according to background knowledge.
     */
    public static void pcOrientbk(Knowledge bk, Graph graph, List<Node> nodes) {
        TetradLogger.getInstance().log("details", "Staring BK Orientation.");
        for (Iterator<KnowledgeEdge> it = bk.forbiddenEdgesIterator(); it.hasNext(); ) {
            KnowledgeEdge edge = it.next();

            //match strings to variables in the graph.
            Node from = GraphSearchUtils.translate(edge.getFrom(), nodes);
            Node to = GraphSearchUtils.translate(edge.getTo(), nodes);

            if (from == null || to == null) {
                continue;
            }

            if (graph.getEdge(from, to) == null) {
                continue;
            }

            // Orient to-->from
            graph.removeEdge(from, to);
            graph.addDirectedEdge(to, from);

            TetradLogger.getInstance().log("knowledgeOrientations", LogUtilsSearch.edgeOrientedMsg("Knowledge", graph.getEdge(to, from)));
        }

        for (Iterator<KnowledgeEdge> it = bk.requiredEdgesIterator(); it.hasNext(); ) {
            KnowledgeEdge edge = it.next();

            //match strings to variables in this graph
            Node from = GraphSearchUtils.translate(edge.getFrom(), nodes);
            Node to = GraphSearchUtils.translate(edge.getTo(), nodes);

            if (from == null || to == null) {
                continue;
            }

            if (graph.getEdge(from, to) == null) {
                continue;
            }

            // Orient from-->to
            graph.removeEdges(from, to);
            graph.addDirectedEdge(from, to);

            TetradLogger.getInstance().log("knowledgeOrientations", LogUtilsSearch.edgeOrientedMsg("Knowledge", graph.getEdge(from, to)));
        }

        TetradLogger.getInstance().log("details", "Finishing BK Orientation.");
    }

    /**
     * Performs step C of the algorithm, as indicated on page xxx of CPS, with the modification that X--W--Y is oriented
     * as X--&gt;W&lt;--Y if W is *determined by* the sepset of (X, Y), rather than W just being *in* the sepset of (X,
     * Y).
     */
    public static void pcdOrientC(IndependenceTest test, Knowledge knowledge, Graph graph) {
        TetradLogger.getInstance().log("info", "Starting Collider Orientation:");

        List<Node> nodes = graph.getNodes();

        for (Node y : nodes) {
            List<Node> adjacentNodes = new ArrayList<>(graph.getAdjacentNodes(y));

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

                Set<Node> sepset = GraphSearchUtils.sepset(graph, x, z, new HashSet<>(), new HashSet<>(), test);

                if (sepset == null) {
                    continue;
                }

                if (sepset.contains(y)) {
                    continue;
                }

                Set<Node> augmentedSet = new HashSet<>(sepset);

                augmentedSet.add(y);

                if (test.determines(sepset, x)) {
                    continue;
                }

                if (test.determines(sepset, z)) {
                    continue;
                }

                if (test.determines(augmentedSet, x)) {
                    continue;
                }

                if (test.determines(augmentedSet, z)) {
                    continue;
                }

                if (!GraphSearchUtils.isArrowheadAllowed(x, y, knowledge)
                        || !GraphSearchUtils.isArrowheadAllowed(z, y, knowledge)) {
                    continue;
                }

                graph.setEndpoint(x, y, Endpoint.ARROW);
                graph.setEndpoint(z, y, Endpoint.ARROW);

                System.out.println(LogUtilsSearch.colliderOrientedMsg(x, y, z) + " sepset = " + sepset);
                TetradLogger.getInstance().log("colliderOrientations", LogUtilsSearch.colliderOrientedMsg(x, y, z));
            }
        }

        TetradLogger.getInstance().log("info", "Finishing Collider Orientation.");
    }

    private static Set<Node> sepset(Graph graph, Node a, Node c, Set<Node> containing, Set<Node> notContaining, IndependenceTest independenceTest) {
        List<Node> adj = new ArrayList<>(graph.getAdjacentNodes(a));
        adj.addAll(graph.getAdjacentNodes(c));
        adj.remove(c);
        adj.remove(a);

        for (int d = 0; d <= FastMath.min(1000, adj.size()); d++) {
            ChoiceGenerator gen = new ChoiceGenerator(adj.size(), d);
            int[] choice;

            while ((choice = gen.next()) != null) {
                Set<Node> v2 = GraphUtils.asSet(choice, adj);
                v2.addAll(containing);
                v2.removeAll(notContaining);
                v2.remove(a);
                v2.remove(c);

//                    if (isForbidden(a, c, new ArrayList<>(v2)))
                IndependenceResult result = independenceTest.checkIndependence(a, c, new HashSet<>(v2));
                double p2 = result.getScore();

                if (p2 < 0) {
                    return v2;
                }
            }
        }

        return null;
    }

    /**
     * Step C of PC; orients colliders using specified sepset. That is, orients x *-* y *-* z as x *-&gt; y &lt;-* z
     * just in case y is in Sepset({x, z}).
     */
    public static void orientCollidersUsingSepsets(SepsetMap set, Knowledge knowledge, Graph graph, boolean verbose,
                                                   boolean enforceCpdag) {
        TetradLogger.getInstance().log("details", "Starting Collider Orientation:");
        List<Node> nodes = graph.getNodes();

        for (Node b : nodes) {
            List<Node> adjacentNodes = new ArrayList<>(graph.getAdjacentNodes(b));

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

                Set<Node> sepset = set.get(a, c);

                //I think the null check needs to be here --AJ
                if (sepset != null && !sepset.contains(b)
                        && GraphSearchUtils.isArrowheadAllowed(a, b, knowledge)) {
                    boolean result = true;
                    if (knowledge != null) {
                        result = !knowledge.isRequired(((Object) b).toString(), ((Object) c).toString())
                                && !knowledge.isForbidden(((Object) c).toString(), ((Object) b).toString());
                    }
                    if (result) {
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

                        TetradLogger.getInstance().log("colliderOrientations", LogUtilsSearch.colliderOrientedMsg(a, b, c, sepset));
                    }
                }
            }
        }

        TetradLogger.getInstance().log("details", "Finishing Collider Orientation.");

    }

    /**
     * Checks if an arrowhead is allowed by background knowledge.
     */
    public static boolean isArrowheadAllowed(Object from, Object to,
                                             Knowledge knowledge) {
        if (knowledge == null) {
            return true;
        }
        return !knowledge.isRequired(to.toString(), from.toString())
                && !knowledge.isForbidden(from.toString(), to.toString());
    }

    /**
     * Get a graph and direct only the unshielded colliders.
     */
    public static void basicCpdag(Graph graph) {
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
        GraphSearchUtils.basicCpdag(graph);
        MeekRules rules = new MeekRules();
        rules.orientImplied(graph);
        return graph;
    }

    public static Graph dagFromCPDAG(Graph graph) {
        return GraphSearchUtils.dagFromCPDAG(graph, null);
    }

    public static Graph dagFromCPDAG(Graph graph, Knowledge knowledge) {
        Graph dag = new EdgeListGraph(graph);

        for (Edge edge : dag.getEdges()) {
            if (Edges.isBidirectedEdge(edge)) {
                throw new IllegalArgumentException("That 'cpdag' contains a bidirected edge.");
            }
        }

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

                if (Edges.isUndirectedEdge(edge) && !graph.paths().isAncestorOf(y, x)) {
                    GraphSearchUtils.direct(x, y, dag);
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

    // Zhang 2008 Theorem 2
    public static Graph pagToMag(Graph pag) {
        Graph mag = new EdgeListGraph(pag.getNodes());
        for (Edge e : pag.getEdges()) mag.addEdge(new Edge(e));

        List<Node> nodes = mag.getNodes();

        Graph pcafci = new EdgeListGraph(nodes);

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = 0; j < nodes.size(); j++) {
                if (i == j) continue;

                Node x = nodes.get(i);
                Node y = nodes.get(j);

                if (mag.getEndpoint(y, x) == Endpoint.CIRCLE && mag.getEndpoint(x, y) == Endpoint.ARROW) {
                    mag.setEndpoint(y, x, Endpoint.TAIL);
                }

                if (mag.getEndpoint(y, x) == Endpoint.TAIL && mag.getEndpoint(x, y) == Endpoint.CIRCLE) {
                    mag.setEndpoint(x, y, Endpoint.ARROW);
                }

                if (mag.getEndpoint(y, x) == Endpoint.CIRCLE && mag.getEndpoint(x, y) == Endpoint.CIRCLE) {
                    pcafci.addEdge(mag.getEdge(x, y));
                }
            }
        }

        for (Edge e : pcafci.getEdges()) {
            e.setEndpoint1(Endpoint.TAIL);
            e.setEndpoint2(Endpoint.TAIL);
        }

        W:
        while (true) {
            for (Edge e : pcafci.getEdges()) {
                if (Edges.isUndirectedEdge(e)) {
                    Node x = e.getNode1();
                    Node y = e.getNode2();

                    pcafci.setEndpoint(y, x, Endpoint.TAIL);
                    pcafci.setEndpoint(x, y, Endpoint.ARROW);

                    MeekRules meekRules = new MeekRules();
                    meekRules.setRevertToUnshieldedColliders(false);
                    meekRules.orientImplied(pcafci);

                    continue W;
                }
            }

            break;
        }

        for (Edge e : pcafci.getEdges()) {
            mag.removeEdge(e.getNode1(), e.getNode2());
            mag.addEdge(e);
        }

        return mag;
    }

    /**
     * Stores a result for checking whether a graph is a legal PAG--(a) whether it is (a boolean), and (b) the reason
     * why it is not, if it is not (a String).
     */
    public static class LegalPagRet {
        private final boolean legalPag;
        private final String reason;

        public LegalPagRet(boolean legalPag, String reason) {
            if (reason == null) throw new NullPointerException("Reason must be given.");
            this.legalPag = legalPag;
            this.reason = reason;
        }

        public boolean isLegalPag() {
            return legalPag;
        }

        public String getReason() {
            return reason;
        }
    }

    public static LegalPagRet isLegalPag(Graph pag) {

        for (Node n : pag.getNodes()) {
            if (n.getNodeType() != NodeType.MEASURED) {
                return new LegalPagRet(false,
                        "Node " + n + " is not measured");
            }
        }

        Graph mag = pagToMag(pag);

        LegalMagRet legalMag = isLegalMag(mag);

        if (!legalMag.isLegalMag()) {
            return new LegalPagRet(false, legalMag.getReason() + " in a MAG implied by this graph");
        }

        Graph pag2 = GraphSearchUtils.dagToPag(mag);

        if (!pag.equals(pag2)) {
            String edgeMismatch = "";

            for (Edge e : pag.getEdges()) {
                Edge e2 = pag2.getEdge(e.getNode1(), e.getNode2());

                if (!e.equals(e2)) {
                    edgeMismatch = "For example, the original PAG has edge " + e
                            + " whereas the reconstituted graph has edge " + e2;
                }
            }

            String reason;

            if (legalMag.isLegalMag()) {
                reason = "The MAG implied by this graph was a legal MAG, but still one cannot recover the original graph " +
                        "by finding the PAG of an implied MAG, so this is between a MAG and PAG";

            } else {
                reason = "The MAG implied by this graph was not legal MAG, but in any case one cannot recover " +
                        "the original graph by finding the PAG of an implied MAG, so this is could be between " +
                        "a MAG and PAG";
            }

            if (!edgeMismatch.equals("")) {
                reason = reason + ". " + edgeMismatch;
            }

            return new LegalPagRet(false, reason);
        }

        return new LegalPagRet(true, "This is a legal PAG");
    }

    /**
     * Stores a result for checking whether a graph is a legal MAG--(a) whether it is (a boolean), and (b) the reason
     * why it is not, if it is not (a String).
     */
    public static class LegalMagRet {
        private final boolean legalMag;
        private final String reason;

        public LegalMagRet(boolean legalPag, String reason) {
            if (reason == null) throw new NullPointerException("Reason must be given.");
            this.legalMag = legalPag;
            this.reason = reason;
        }

        public boolean isLegalMag() {
            return legalMag;
        }

        public String getReason() {
            return reason;
        }
    }

    private static LegalMagRet isLegalMag(Graph mag) {
        for (Node n : mag.getNodes()) {
            if (n.getNodeType() == NodeType.LATENT)
                return new LegalMagRet(false,
                        "Node " + n + " is not measured");
        }

        List<Node> nodes = mag.getNodes();

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                Node x = nodes.get(i);
                Node y = nodes.get(j);

                if (!mag.isAdjacentTo(x, y)) continue;

                if (mag.getEdges(x, y).size() > 1) {
                    return new LegalMagRet(false,
                            "There is more than one edge between " + x + " and " + y);
                }

                Edge e = mag.getEdge(x, y);

                if (!(Edges.isDirectedEdge(e) || Edges.isBidirectedEdge(e) || Edges.isUndirectedEdge(e))) {
                    return new LegalMagRet(false,
                            "Edge " + e + " should be dir" +
                                    "ected, bidirected, or undirected.");
                }
            }
        }

        for (Node n : mag.getNodes()) {
            if (mag.paths().existsDirectedPathFromTo(n, n))
                return new LegalMagRet(false,
                        "Acyclicity violated: There is a directed cyclic path from from " + n + " to itself");
        }

        for (Edge e : mag.getEdges()) {
            Node x = e.getNode1();
            Node y = e.getNode2();

            if (Edges.isBidirectedEdge(e)) {
                if (mag.paths().existsDirectedPathFromTo(x, y)) {
                    List<Node> path = mag.paths().directedPathsFromTo(x, y, 100).get(0);
                    return new LegalMagRet(false,
                            "Bidirected edge semantics is violated: there is a directed path for " + e + " from " + x + " to " + y
                                    + ". This is \"almost cyclic\"; for <-> edges there should not be a path from either endpoint to the other. "
                                    + "An example path is " + GraphUtils.pathString(mag, path));
                } else if (mag.paths().existsDirectedPathFromTo(y, x)) {
                    List<Node> path = mag.paths().directedPathsFromTo(y, x, 100).get(0);
                    return new LegalMagRet(false,
                            "Bidirected edge semantics is violated: There is an a directed path for " + e + " from " + y + " to " + x +
                                    ". This is \"almost cyclic\"; for <-> edges there should not be a path from either endpoint to the other. "
                                    + "An example path is " + GraphUtils.pathString(mag, path));
                }
            }
        }

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                Node x = nodes.get(i);
                Node y = nodes.get(j);

                if (!mag.isAdjacentTo(x, y)) {
                    if (mag.paths().existsInducingPath(x, y))
                        return new LegalMagRet(false,
                                "This is not maximal; there is an inducing path between non-adjacent " + x + " and " + y);
                }
            }
        }

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                Node x = nodes.get(i);
                Node y = nodes.get(j);

                if (!mag.isAdjacentTo(x, y)) continue;

                Edge e = mag.getEdge(x, y);

                if (Edges.isUndirectedEdge(e)) {
                    for (Node z : mag.getAdjacentNodes(x)) {
                        if (mag.isParentOf(z, x) || Edges.isBidirectedEdge(mag.getEdge(z, x))) {
                            return new LegalMagRet(false,
                                    "For undirected edge " + e + ", " + z + " should not be a parent or a spouse of " + x);
                        }
                    }

                    for (Node z : mag.getAdjacentNodes(y)) {
                        if (mag.isParentOf(z, y) || Edges.isBidirectedEdge(mag.getEdge(z, y))) {
                            return new LegalMagRet(false,
                                    "For undirected edge " + e + ", " + z + " should not be a parent or a spouse of " + y);
                        }
                    }
                }
            }
        }

        return new LegalMagRet(true, "This is a legal MAG");
    }

    public static void arrangeByKnowledgeTiers(Graph graph,
                                               Knowledge knowledge) {
        if (knowledge.getNumTiers() == 0) {
            throw new IllegalArgumentException("There are no Tiers to arrange.");
        }

        int ySpace = 500 / knowledge.getNumTiers();
        ySpace = max(ySpace, 50);

        List<String> notInTier = knowledge.getVariablesNotInTiers();
        sort(notInTier);

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
            sort(tier);
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
            sort(tiers.get(i));
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
     * @param initialNodes The nodes that reachability undirectedPaths start from.
     * @param legalPairs   Specifies initial edges (given initial nodes) and legal edge pairs.
     * @param c            a set of vertices (intuitively, the set of variables to be conditioned on.
     * @param d            a set of vertices (intuitively to be used in tests of legality, for example, the set of
     *                     ancestors of c).
     * @param graph        the graph with respect to which reachability is
     * @return the set of nodes reachable from the given set of initial nodes in the given graph according to the
     * criteria in the given legal pairs object.
     * <p>
     * A variable v is reachable from initialNodes iff for some variable X in initialNodes thers is a path U [X, Y1,
     * ..., v] such that legalPairs.isLegalFirstNode(X, Y1) and for each [H1, H2, H3] as subpaths of U,
     * legalPairs.isLegalPairs(H1, H2, H3).
     * <p>
     * The algorithm used is a variant of Algorithm 1 from Geiger, Verma, and Pearl (1990).
     */
    public static Set<Node> getReachableNodes(List<Node> initialNodes,
                                              LegalPairs legalPairs, List<Node> c, List<Node> d, Graph graph, int maxPathLength) {
        HashSet<Node> reachable = new HashSet<>();
        MultiKeyMap<Node, Boolean> visited = new MultiKeyMap<>();
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
        int total = (int) FastMath.pow(2, nodes.size());
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
     * Generates the list of DAGs in the given cpdag.
     */
    public static List<Graph> generateCpdagDags(Graph cpdag, boolean orientBidirectedEdges) {
        if (orientBidirectedEdges) {
            cpdag = GraphUtils.removeBidirectedOrientations(cpdag);
        }

        return GraphSearchUtils.getDagsInCpdagMeek(cpdag, new Knowledge());
    }

    public static List<Graph> getDagsInCpdagMeek(Graph cpdag, Knowledge knowledge) {
        DagInCpcagIterator iterator = new DagInCpcagIterator(cpdag, knowledge);
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

        List<Node> _nodes = new ArrayList<>(graph.getAdjacentNodes(x));
        _nodes.remove(z);
        TetradLogger.getInstance().log("adjacencies", "Adjacents for " + x + "--" + y + "--" + z + " = " + _nodes);

        int _depth = depth;
        if (_depth == -1) {
            _depth = 1000;
        }
        _depth = FastMath.min(_depth, _nodes.size());

        for (int d = 0; d <= _depth; d++) {
            ChoiceGenerator cg = new ChoiceGenerator(_nodes.size(), d);
            int[] choice;

            while ((choice = cg.next()) != null) {
                Set<Node> cond = GraphUtils.asSet(choice, _nodes);

                if (test.checkIndependence(x, z, cond).isIndependent()) {
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

        _nodes = new ArrayList<>(graph.getAdjacentNodes(z));
        _nodes.remove(x);
        TetradLogger.getInstance().log("adjacencies", "Adjacents for " + x + "--" + y + "--" + z + " = " + _nodes);

        _depth = FastMath.min(_depth, _nodes.size());

        for (int d = 0; d <= _depth; d++) {
            ChoiceGenerator cg = new ChoiceGenerator(_nodes.size(), d);
            int[] choice;

            while ((choice = cg.next()) != null) {
                Set<Node> cond = GraphUtils.asSet(choice, _nodes);

                if (test.checkIndependence(x, z, cond).isIndependent()) {
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
            trueGraph = GraphSearchUtils.cpdagForDag(trueGraph);
            estGraph = GraphSearchUtils.cpdagForDag(estGraph);

            // Will check mixedness later.
            if (trueGraph.paths().existsDirectedCycle()) {
                TetradLogger.getInstance().forceLogMessage("SHD failed: True graph couldn't be converted to a CPDAG");
            }

            if (estGraph.paths().existsDirectedCycle()) {
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

                    int error = GraphSearchUtils.structuralHammingDistanceOneEdge(e1, e2);
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

    public static GraphUtils.GraphComparison getGraphComparison(Graph trueGraph, Graph targetGraph) {
        targetGraph = GraphUtils.replaceNodes(targetGraph, trueGraph.getNodes());

        int adjFn = GraphUtils.countAdjErrors(trueGraph, targetGraph);
        int adjFp = GraphUtils.countAdjErrors(targetGraph, trueGraph);
        int adjCorrect = trueGraph.getNumEdges() - adjFn;

        int arrowptFn = GraphUtils.countArrowptErrors(trueGraph, targetGraph);
        int arrowptFp = GraphUtils.countArrowptErrors(targetGraph, trueGraph);
        int arrowptCorrect = GraphUtils.getNumCorrectArrowpts(trueGraph, targetGraph);

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
            if (!targetGraph.isAdjacentTo(n1, n2)) {
                Edge trueGraphEdge = trueGraph.getEdge(n1, n2);
                edgesRemoved.add(trueGraphEdge);
            }
        }

        for (Edge edge : targetGraph.getEdges()) {
            Node n1 = edge.getNode1();
            Node n2 = edge.getNode2();
            if (!trueGraph.isAdjacentTo(n1, n2)) {
                Edge graphEdge = targetGraph.getEdge(n1, n2);
                edgesAdded.add(graphEdge);
            }
        }

        for (Edge edge : trueGraph.getEdges()) {
            if (targetGraph.containsEdge(edge)) {
                continue;
            }

            Node node1 = edge.getNode1();
            Node node2 = edge.getNode2();

            for (Edge _edge : targetGraph.getEdges(node1, node2)) {
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
            if (targetGraph.isAdjacentTo(edge.getNode1(), edge.getNode2())) {
                correctAdjacency.add(edge);
            }
        }

        int shd = structuralHammingDistance(trueGraph, targetGraph);

        int[][] counts = graphComparison(trueGraph, targetGraph, null);

        return new GraphUtils.GraphComparison(
                adjFn, adjFp, adjCorrect, arrowptFn, arrowptFp, arrowptCorrect,
                adjPrec, adjRec, arrowptPrec, arrowptRec, shd,
                twoCycleCorrect, twoCycleFn, twoCycleFp,
                edgesAdded, edgesRemoved, edgesReorientedFrom, edgesReorientedTo,
                correctAdjacency,
                counts);
    }

    /**
     * Just counts arrowhead errors--for cyclic edges counts an arrowhead at each node.
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

    public static String graphComparisonString(String trueGraphName, Graph trueGraph,
                                               String targetGraphName, Graph targetGraph, boolean printStars) {
        trueGraph = new EdgeListGraph(trueGraph);
        targetGraph = new EdgeListGraph(targetGraph);

        StringBuilder builder = new StringBuilder();
        targetGraph = GraphUtils.replaceNodes(targetGraph, trueGraph.getNodes());

        String trueGraphAndTarget = "True graph from " + trueGraphName + "\nTarget graph from " + targetGraphName;
        builder.append(trueGraphAndTarget).append("\n");

        GraphUtils.GraphComparison comparison = getGraphComparison(trueGraph, targetGraph);

        List<Edge> edgesAdded = comparison.getEdgesAdded();
        List<Edge> edgesAdded2 = new ArrayList<>();

        for (Edge e1 : edgesAdded) {
            Node n1 = e1.getNode1();
            Node n2 = e1.getNode2();

            boolean twoCycle1 = trueGraph.getDirectedEdge(n1, n2) != null && trueGraph.getDirectedEdge(n2, n1) != null;
            boolean twoCycle2 = targetGraph.getDirectedEdge(n1, n2) != null && targetGraph.getDirectedEdge(n2, n1) != null;

            if (!(twoCycle1 || twoCycle2)) {
                edgesAdded2.add(e1);
            }
        }

        sort(edgesAdded2);

        builder.append("\nAdjacencies added (not involving 2-cycles and not reoriented):");

        if (edgesAdded2.isEmpty()) {
            builder.append("\n  --NONE--");
        } else {
            for (int i = 0; i < edgesAdded2.size(); i++) {
                Edge _edge = edgesAdded2.get(i);
                Edge edge1 = targetGraph.getEdge(_edge.getNode1(), _edge.getNode2());

                Node node1 = targetGraph.getNode(edge1.getNode1().getName());
                Node node2 = targetGraph.getNode(edge1.getNode2().getName());

                builder.append("\n").append(i + 1).append(". ").append(edge1);

                if (printStars) {
                    boolean directedInGraph2 = false;

                    if (Edges.isDirectedEdge(edge1) && targetGraph.paths().existsSemidirectedPath(node1, node2)) {
                        directedInGraph2 = true;
                    } else if ((Edges.isUndirectedEdge(edge1) || Edges.isBidirectedEdge(edge1))
                            && (targetGraph.paths().existsSemidirectedPath(node1, node2)
                            || targetGraph.paths().existsSemidirectedPath(node2, node1))) {
                        directedInGraph2 = true;
                    }

                    if (directedInGraph2) {
                        builder.append(" *");
                    }
                }
            }
        }

        builder.append("\n\nAdjacencies removed:");
        List<Edge> edgesRemoved = comparison.getEdgesRemoved();
        sort(edgesRemoved);

        if (edgesRemoved.isEmpty()) {
            builder.append("\n  --NONE--");
        } else {
            for (int i = 0; i < edgesRemoved.size(); i++) {
                Edge edge = edgesRemoved.get(i);

                Node node1 = trueGraph.getNode(edge.getNode1().getName());
                Node node2 = trueGraph.getNode(edge.getNode2().getName());

                builder.append("\n").append(i + 1).append(". ").append(edge);

                if (printStars) {
                    boolean directedInGraph1 = false;

                    if (Edges.isDirectedEdge(edge) && trueGraph.paths().existsSemidirectedPath(node1, node2)) {
                        directedInGraph1 = true;
                    } else if ((Edges.isUndirectedEdge(edge) || Edges.isBidirectedEdge(edge))
                            && (trueGraph.paths().existsSemidirectedPath(node1, node2)
                            || trueGraph.paths().existsSemidirectedPath(node2, node1))) {
                        directedInGraph1 = true;
                    }

                    if (directedInGraph1) {
                        builder.append(" *");
                    }
                }
            }
        }

        List<Edge> edges1 = new ArrayList<>(trueGraph.getEdges());

        List<Edge> twoCycles = new ArrayList<>();
        List<Edge> allSingleEdges = new ArrayList<>();

        for (Edge edge : edges1) {
            if (edge.isDirected() && targetGraph.containsEdge(edge) && targetGraph.containsEdge(edge.reverse())) {
                twoCycles.add(edge);
            } else if (trueGraph.containsEdge(edge)) {
                allSingleEdges.add(edge);
            }
        }

        builder.append("\n\n"
                + "Two-cycles in true correctly adjacent in estimated");

        sort(allSingleEdges);

        if (twoCycles.isEmpty()) {
            builder.append("\n  --NONE--");
        } else {
            for (int i = 0; i < twoCycles.size(); i++) {
                Edge adj = edges1.get(i);
                builder.append("\n").append(i + 1).append(". ").append(adj).append(" ").append(adj.reverse())
                        .append(" ====> ").append(trueGraph.getEdge(twoCycles.get(i).getNode1(), twoCycles.get(i).getNode2()));
            }
        }

        List<Edge> incorrect = new ArrayList<>();

        for (Edge adj : allSingleEdges) {
            Edge edge1 = trueGraph.getEdge(adj.getNode1(), adj.getNode2());
            Edge edge2 = targetGraph.getEdge(adj.getNode1(), adj.getNode2());

            if (!edge1.equals(edge2)) {
                incorrect.add(adj);
            }
        }

        {
            builder.append("\n\n" + "Edges incorrectly oriented");

            if (incorrect.isEmpty()) {
                builder.append("\n  --NONE--");
            } else {
                int j1 = 0;
                sort(incorrect);

                for (Edge adj : incorrect) {
                    Edge edge1 = trueGraph.getEdge(adj.getNode1(), adj.getNode2());
                    Edge edge2 = targetGraph.getEdge(adj.getNode1(), adj.getNode2());
                    if (edge1 == null || edge2 == null) continue;
                    builder.append("\n").append(++j1).append(". ").append(edge1).append(" ====> ").append(edge2);
                }
            }
        }

        {
            builder.append("\n\n" + "Edges correctly oriented");

            List<Edge> correct = new ArrayList<>();

            for (Edge adj : allSingleEdges) {
                Edge edge1 = trueGraph.getEdge(adj.getNode1(), adj.getNode2());
                Edge edge2 = targetGraph.getEdge(adj.getNode1(), adj.getNode2());
                if (edge1.equals(edge2)) {
                    correct.add(edge1);
                }
            }

            if (correct.isEmpty()) {
                builder.append("\n  --NONE--");
            } else {
                sort(correct);

                int j2 = 0;

                for (Edge edge : correct) {
                    builder.append("\n").append(++j2).append(". ").append(edge);
                }
            }
        }

        return builder.toString();
    }

    public static int[][] graphComparison(Graph trueCpdag, Graph estCpdag, PrintStream out) {
        GraphUtils.GraphComparison comparison = GraphSearchUtils.getGraphComparison2(estCpdag, trueCpdag);

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

    @NotNull
    public static Graph dagToPag(Graph trueGraph) {
        return new DagToPag(trueGraph).convert();
    }

    /**
     * Gives the options for triple type for a conservative unshielded collider orientation, which may be "collider" or
     * "noncollider" or "ambiguous".
     */
    public enum CpcTripleType {
        COLLIDER, NONCOLLIDER, AMBIGUOUS
    }

    /**
     * Simple class to store edges for the reachability search.
     *
     * @author josephramsey
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
