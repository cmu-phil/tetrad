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
import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.SvarFci;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.math3.util.FastMath;

import java.util.*;


/**
 * <p>Adapts FciOrient for the SvarFCI algorithm. The main difference is that if an edge is orient,
 * it will also orient all homologous edges to preserve the time-repeating structure assumed by SvarFCI. Based on (but
 * not identicial to) code by Entner and Hoyer for their 2010 paper. Modified by DMalinsky 4/20/2016.</p>
 *
 * <p>This class is configured to respect knowledge of forbidden and required
 * edges, including knowledge of temporal tiers.</p>
 *
 * @author dmalinsky
 * @see Knowledge
 * @see SvarFci
 */
public final class SvarFciOrient {

    /**
     * The SepsetMap being constructed.
     */
    private final SepsetProducer sepsets;

    private Knowledge knowledge = new Knowledge();

    private boolean changeFlag = true;

    /**
     * flag for complete rule set, true if one should use complete rule set, false otherwise.
     */
    private boolean completeRuleSetUsed;

    /**
     * The maximum length for any discriminating path. -1 if unlimited; otherwise, a positive integer.
     */
    private int maxPathLength = -1;

    /**
     * The logger to use.
     */
    private final TetradLogger logger = TetradLogger.getInstance();

    /**
     * True iff verbose output should be printed.
     */
    private boolean verbose;

    private Graph truePag;

    private final IndependenceTest independenceTest;

    //============================CONSTRUCTORS============================//

    /**
     * Constructs a new FCI search for the given independence test and background knowledge.
     */
    public SvarFciOrient(SepsetProducer sepsets, IndependenceTest independenceTest) {
        this.sepsets = sepsets;
        this.independenceTest = independenceTest;
    }

    public Graph orient(Graph graph) {

        this.logger.log("info", "Starting FCI algorithm.");

        ruleR0(graph);

        if (this.verbose) {
            System.out.println("R0");
        }


        // Step CI D. (Zhang's step F4.)
        doFinalOrientation(graph);

        if (this.verbose) {
            this.logger.log("graph", "Returning graph: " + graph);
        }

        return graph;
    }

    public SepsetProducer getSepsets() {
        return this.sepsets;
    }

    /**
     * The background knowledge.
     */
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    public void setKnowledge(Knowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException();
        }

        this.knowledge = knowledge;
    }

    /**
     * @return true if Zhang's complete rule set should be used, false if only R1-R4 (the rule set of the original FCI)
     * should be used. False by default.
     */
    public boolean isCompleteRuleSetUsed() {
        return this.completeRuleSetUsed;
    }

    /**
     * @param completeRuleSetUsed set to true if Zhang's complete rule set should be used, false if only R1-R4 (the rule
     *                            set of the original FCI) should be used. False by default.
     */
    public void setCompleteRuleSetUsed(boolean completeRuleSetUsed) {
        this.completeRuleSetUsed = completeRuleSetUsed;
    }

    //===========================PRIVATE METHODS=========================//

    /**
     * Orients colliders in the graph.  (FCI Step C)
     * <p>
     * Zhang's step F3, rule R0.
     */
    public void ruleR0(Graph graph) {
        graph.reorientAllWith(Endpoint.CIRCLE);
        fciOrientbk(this.knowledge, graph, graph.getNodes());

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
                if (this.knowledge.isInWhichTier(a) == 0 && this.knowledge.isInWhichTier(b) == 0 && this.knowledge.isInWhichTier(c) == 0) {
                    System.out.println("Skipping triple a,b,c : " + a + " , " + b + " , " + c);
                    continue; // This is added as a temporary measure. Sepsets for lagged vars may be out of window, leading to incorrect collider orientations
                }
                // Skip triples that are shielded.
                if (graph.isAdjacentTo(a, c)) {
                    continue;
                }

                if (graph.isDefCollider(a, b, c)) {
                    continue;
                }

                if (this.sepsets.isUnshieldedCollider(a, b, c)) {
                    if (!FciOrient.isArrowheadAllowed(a, b, graph, knowledge)) {
                        continue;
                    }

                    if (!FciOrient.isArrowheadAllowed(c, b, graph, knowledge)) {
                        continue;
                    }

                    graph.setEndpoint(a, b, Endpoint.ARROW);
                    graph.setEndpoint(c, b, Endpoint.ARROW);
                    if (this.verbose) {
                        this.logger.log("colliderOrientations", LogUtilsSearch.colliderOrientedMsg(a, b, c));
                        System.out.println(LogUtilsSearch.colliderOrientedMsg(a, b, c));
                        printWrongColliderMessage(a, b, c, graph);
                    }
                    this.orientSimilarPairs(graph, this.knowledge, a, b, Endpoint.ARROW);
                    this.orientSimilarPairs(graph, this.knowledge, c, b, Endpoint.ARROW);
                }
            }
        }
    }

    /**
     * Orients the graph according to rules in the graph (FCI step D).
     * <p>
     * Zhang's step F4, rules R1-R10.
     */
    public void doFinalOrientation(Graph graph) {
        if (this.completeRuleSetUsed) {
            zhangFinalOrientation(graph);
        } else {
            spirtesFinalOrientation(graph);
        }
    }

    private void printWrongColliderMessage(Node a, Node b, Node c, Graph graph) {
        if (this.truePag != null && graph.isDefCollider(a, b, c) && !this.truePag.isDefCollider(a, b, c)) {
            System.out.println("R0" + ": Orienting collider by mistake: " + a + "*->" + b + "<-*" + c);
        }
    }

    private void spirtesFinalOrientation(Graph graph) {
        this.changeFlag = true;
        boolean firstTime = true;

        while (this.changeFlag) {
            this.changeFlag = false;
            rulesR1R2cycle(graph);
            ruleR3(graph);

            // R4 requires an arrow orientation.
            if (this.changeFlag || (firstTime && !this.knowledge.isEmpty())) {
                ruleR4B(graph);
                firstTime = false;
            }

            if (this.verbose) {
                System.out.println("Epoch");
            }
        }
    }

    private void zhangFinalOrientation(Graph graph) {
        this.changeFlag = true;
        boolean firstTime = true;

        while (this.changeFlag) {
            this.changeFlag = false;
            rulesR1R2cycle(graph);
            ruleR3(graph);

            // R4 requires an arrow orientation.
            if (this.changeFlag || (firstTime && !this.knowledge.isEmpty())) {
                ruleR4B(graph);
                firstTime = false;
            }

            if (this.verbose) {
                System.out.println("Epoch");
            }
        }

        if (isCompleteRuleSetUsed()) {
            // Now, by a remark on page 100 of Zhang's dissertation, we apply rule
            // R5 once.
            ruleR5(graph);

            // Now, by a further remark on page 102, we apply R6,R7 as many times
            // as possible.
            this.changeFlag = true;

            while (this.changeFlag) {
                this.changeFlag = false;
                ruleR6R7(graph);
            }

            // Finally, we apply R8-R10 as many times as possible.
            this.changeFlag = true;

            while (this.changeFlag) {
                this.changeFlag = false;
                rulesR8R9R10(graph);
            }

        }
    }

    //Does all 3 of these rules at once instead of going through all
    // triples multiple times per iteration of doFinalOrientation.

    public void rulesR1R2cycle(Graph graph) {
        List<Node> nodes = graph.getNodes();

        for (Node B : nodes) {
            List<Node> adj = new ArrayList<>(graph.getAdjacentNodes(B));

            if (adj.size() < 2) {
                continue;
            }

            ChoiceGenerator cg = new ChoiceGenerator(adj.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null) {
                Node A = adj.get(combination[0]);
                Node C = adj.get(combination[1]);

                //choice gen doesn't do diff orders, so must switch A & C around.
                ruleR1(A, B, C, graph);
                ruleR1(C, B, A, graph);
                ruleR2(A, B, C, graph);
                ruleR2(C, B, A, graph);
            }
        }
    }

    /// R1, away from collider
    // If a*-&gt;bo-*c and a, c not adjacent then a*-&gt;b->c
    private void ruleR1(Node a, Node b, Node c, Graph graph) {
        if (graph.isAdjacentTo(a, c)) {
            return;
        }

        if (graph.getEndpoint(a, b) == Endpoint.ARROW && graph.getEndpoint(c, b) == Endpoint.CIRCLE) {
            if (!FciOrient.isArrowheadAllowed(b, c, graph, knowledge)) {
                return;
            }

            graph.setEndpoint(c, b, Endpoint.TAIL);
            graph.setEndpoint(b, c, Endpoint.ARROW);
            this.changeFlag = true;

            if (this.verbose) {
                this.logger.log("impliedOrientations", LogUtilsSearch.edgeOrientedMsg("Away from collider", graph.getEdge(b, c)));
                System.out.println(LogUtilsSearch.edgeOrientedMsg("Away from collider", graph.getEdge(b, c)));
            }
            this.orientSimilarPairs(graph, this.getKnowledge(), c, b, Endpoint.TAIL);
            this.orientSimilarPairs(graph, this.getKnowledge(), b, c, Endpoint.ARROW);
        }
    }

    //if a*-oc and either a-->b*-&gt;c or a*-&gt;b-->c, then a*-&gt;c
    // This is Zhang's rule R2.
    private void ruleR2(Node a, Node b, Node c, Graph graph) {
        if ((graph.isAdjacentTo(a, c)) &&
                (graph.getEndpoint(a, c) == Endpoint.CIRCLE)) {

            if ((graph.getEndpoint(a, b) == Endpoint.ARROW) &&
                    (graph.getEndpoint(b, c) == Endpoint.ARROW) && (
                    (graph.getEndpoint(b, a) == Endpoint.TAIL) ||
                            (graph.getEndpoint(c, b) == Endpoint.TAIL))) {

                if (!FciOrient.isArrowheadAllowed(a, c, graph, knowledge)) {
                    return;
                }

                graph.setEndpoint(a, c, Endpoint.ARROW);
                this.orientSimilarPairs(graph, this.getKnowledge(), a, c, Endpoint.ARROW);
                if (this.verbose) {
                    this.logger.log("impliedOrientations", LogUtilsSearch.edgeOrientedMsg("Away from ancestor", graph.getEdge(a, c)));
                    System.out.println(LogUtilsSearch.edgeOrientedMsg("Away from ancestor", graph.getEdge(a, c)));
                }

                this.changeFlag = true;
            }
        }
    }

    /**
     * Implements the double-triangle orientation rule, which states that if D*-oB, A*-&gt;B&lt;-*C and A*-oDo-*C, then
     * D*-&gt;B.
     * <p>
     * This is Zhang's rule R3.
     */
    public void ruleR3(Graph graph) {
        List<Node> nodes = graph.getNodes();

        for (Node B : nodes) {

            List<Node> intoBArrows = graph.getNodesInTo(B, Endpoint.ARROW);
            List<Node> intoBCircles = graph.getNodesInTo(B, Endpoint.CIRCLE);

            for (Node D : intoBCircles) {
                if (intoBArrows.size() < 2) {
                    continue;
                }

                ChoiceGenerator gen = new ChoiceGenerator(intoBArrows.size(), 2);
                int[] choice;

                while ((choice = gen.next()) != null) {
                    Node A = intoBArrows.get(choice[0]);
                    Node C = intoBArrows.get(choice[1]);

                    if (graph.isAdjacentTo(A, C)) {
                        continue;
                    }

                    if (!graph.isAdjacentTo(A, D) ||
                            !graph.isAdjacentTo(C, D)) {
                        continue;
                    }

                    if (graph.getEndpoint(A, D) != Endpoint.CIRCLE) {
                        continue;
                    }

                    if (graph.getEndpoint(C, D) != Endpoint.CIRCLE) {
                        continue;
                    }

                    if (!FciOrient.isArrowheadAllowed(D, B, graph, knowledge)) {
                        continue;
                    }

                    graph.setEndpoint(D, B, Endpoint.ARROW);
                    this.orientSimilarPairs(graph, this.getKnowledge(), D, B, Endpoint.ARROW);
                    if (this.verbose) {
                        this.logger.log("impliedOrientations", LogUtilsSearch.edgeOrientedMsg("Double triangle", graph.getEdge(D, B)));
                        System.out.println(LogUtilsSearch.edgeOrientedMsg("Double triangle", graph.getEdge(D, B)));
                    }

                    this.changeFlag = true;
                }
            }
        }
    }


    /**
     * The triangles that must be oriented this way (won't be done by another rule) all look like the ones below, where
     * the dots are a collider path from L to A with each node on the path (except L) a parent of C.
     * <pre>
     *          B
     *         xo           x is either an arrowhead or a circle
     *        /  \
     *       v    v
     * L....A --&gt; C
     * </pre>
     * <p>
     * This is Zhang's rule R4, discriminating undirectedPaths.
     */
    public void ruleR4B(Graph graph) {
        List<Node> nodes = graph.getNodes();

        for (Node b : nodes) {

            //potential A and C candidate pairs are only those
            // that look like this:   A&lt;-*Bo-*C
            List<Node> possA = graph.getNodesOutTo(b, Endpoint.ARROW);
            List<Node> possC = graph.getNodesInTo(b, Endpoint.CIRCLE);

            for (Node a : possA) {
                for (Node c : possC) {
                    if (!graph.isParentOf(a, c)) {
                        continue;
                    }

                    if (graph.getEndpoint(b, c) != Endpoint.ARROW) {
                        continue;
                    }

                    ddpOrient(a, b, c, graph);
                }
            }
        }
    }

    /**
     * a method to search "back from a" to find a DDP. It is called with a reachability list (first consisting only of
     * a). This is breadth-first, utilizing "reachability" concept from Geiger, Verma, and Pearl 1990. The body of a DDP
     * consists of colliders that are parents of c.
     */
    public void ddpOrient(Node a, Node b, Node c, Graph graph) {
        Queue<Node> Q = new ArrayDeque<>();
        Set<Node> V = new HashSet<>();

        Node e = null;
        int distance = 0;

        Map<Node, Node> previous = new HashMap<>();

        Set<Node> cParents = graph.getParents(c);

        Q.offer(a);
        V.add(a);
        V.add(b);
        previous.put(a, b);

        while (!Q.isEmpty()) {
            Node t = Q.poll();

            if (e == null || e == t) {
                e = t;
                distance++;
                if (distance > 0 && distance > (this.maxPathLength == -1 ? 1000 : this.maxPathLength)) return;
            }

            List<Node> nodesInTo = graph.getNodesInTo(t, Endpoint.ARROW);

            for (Node d : nodesInTo) {
                if (V.contains(d)) continue;

                previous.put(d, t);
                Node p = previous.get(t);

                if (!graph.isDefCollider(d, t, p)) {
                    continue;
                }

                previous.put(d, t);

                if (!graph.isAdjacentTo(d, c)) {
                    if (doDdpOrientation(d, a, b, c, previous, graph)) {
                        return;
                    }
                }

                if (cParents.contains(d)) {
                    Q.offer(d);
                    V.add(d);
                }
            }
        }
    }

    /**
     * Orients the edges inside the definite discriminating path triangle. Takes the left endpoint, and a,b,c as
     * arguments.
     */
    private boolean doDdpOrientation(Node d, Node a, Node b, Node c, Map<Node, Node> previous, Graph graph) {
        if (graph.isAdjacentTo(d, c)) {
            throw new IllegalArgumentException();
        }

        List<Node> path = getPath(d, previous);

        boolean ind = getSepsets().isIndependent(d, c, new HashSet<>(path));

        List<Node> path2 = new ArrayList<>(path);

        path2.remove(b);

        boolean ind2 = getSepsets().isIndependent(d, c, new HashSet<>(path2));

        if (!ind && !ind2) {
            Set<Node> sepset = getSepsets().getSepset(d, c);

            if (this.verbose) {
                System.out.println("Sepset for d = " + d + " and c = " + c + " = " + sepset);
            }

            if (sepset == null) {
                if (this.verbose) {
                    System.out.println("Must be a sepset: " + d + " and " + c + "; they're non-adjacent.");
                }
                return false;
            }

            ind = sepset.contains(b);
        }

        if (ind) {
            graph.setEndpoint(c, b, Endpoint.TAIL);
            this.orientSimilarPairs(graph, this.getKnowledge(), c, b, Endpoint.TAIL);
            if (this.verbose) {
                this.logger.log("impliedOrientations", LogUtilsSearch.edgeOrientedMsg("Definite discriminating path d = " + d, graph.getEdge(b, c)));
                System.out.println(LogUtilsSearch.edgeOrientedMsg("Definite discriminating path d = " + d, graph.getEdge(b, c)));
            }

        } else {
            if (!FciOrient.isArrowheadAllowed(a, b, graph, knowledge)) {
                return false;
            }

            if (!FciOrient.isArrowheadAllowed(c, b, graph, knowledge)) {
                return false;
            }

            graph.setEndpoint(a, b, Endpoint.ARROW);
            graph.setEndpoint(c, b, Endpoint.ARROW);
            this.orientSimilarPairs(graph, this.getKnowledge(), a, b, Endpoint.ARROW);
            this.orientSimilarPairs(graph, this.getKnowledge(), c, b, Endpoint.ARROW);
            if (this.verbose) {
                this.logger.log("impliedOrientations", LogUtilsSearch.colliderOrientedMsg("Definite discriminating path.. d = " + d, a, b, c));
                System.out.println(LogUtilsSearch.colliderOrientedMsg("Definite discriminating path.. d = " + d, a, b, c));
            }

        }
        this.changeFlag = true;
        return true;
    }

    private List<Node> getPath(Node c, Map<Node, Node> previous) {
        List<Node> l = new ArrayList<>();

        Node p = c;

        do {
            p = previous.get(p);

            if (p != null) {
                l.add(p);
            }
        } while (p != null);

        return l;
    }

    /**
     * Implements Zhang's rule R5, orient circle undirectedPaths: for any Ao-oB, if there is an uncovered circle path u
     * = [A,C,...,D,B] such that A,D nonadjacent and B,C nonadjacent, then A---B and orient every edge on u undirected.
     */
    public void ruleR5(Graph graph) {
        List<Node> nodes = graph.getNodes();

        for (Node a : nodes) {
            List<Node> adjacents = graph.getNodesInTo(a, Endpoint.CIRCLE);

            for (Node b : adjacents) {
                if (!(graph.getEndpoint(a, b) == Endpoint.CIRCLE)) continue;
                // We know Ao-oB.

                List<List<Node>> ucCirclePaths = FciOrient.getUcCirclePaths(a, b, graph);

                for (List<Node> u : ucCirclePaths) {
                    if (u.size() < 3) continue;

                    Node c = u.get(1);
                    Node d = u.get(u.size() - 2);

                    if (graph.isAdjacentTo(a, d)) continue;
                    if (graph.isAdjacentTo(b, c)) continue;
                    // We know u is as required: R5 applies!

                    this.logger.log("colliderOrientations", LogUtilsSearch.edgeOrientedMsg("Orient circle path", graph.getEdge(a, b)));

                    graph.setEndpoint(a, b, Endpoint.TAIL);
                    this.orientSimilarPairs(graph, this.getKnowledge(), a, b, Endpoint.TAIL);
                    graph.setEndpoint(b, a, Endpoint.TAIL);
                    this.orientSimilarPairs(graph, this.getKnowledge(), b, a, Endpoint.TAIL);
                    orientTailPath(u, graph);
                    this.changeFlag = true;
                }
            }
        }
    }

    /**
     * Implements Zhang's rules R6 and R7, applies them over the graph once. Orient single tails. R6: If A---Bo-*C then
     * A---B--*C. R7: If A--oBo-*C and A,C nonadjacent, then A--oB--*C
     */
    public void ruleR6R7(Graph graph) {
        List<Node> nodes = graph.getNodes();

        for (Node b : nodes) {
            List<Node> adjacents = new ArrayList<>(graph.getAdjacentNodes(b));

            if (adjacents.size() < 2) continue;

            ChoiceGenerator cg = new ChoiceGenerator(adjacents.size(), 2);

            for (int[] choice = cg.next(); choice != null; choice = cg.next()) {
                Node a = adjacents.get(choice[0]);
                Node c = adjacents.get(choice[1]);

                if (graph.isAdjacentTo(a, c)) continue;

                if (!(graph.getEndpoint(b, a) == Endpoint.TAIL)) continue;
                if (!(graph.getEndpoint(c, b) == Endpoint.CIRCLE)) continue;
                // We know A--*Bo-*C.

                if (graph.getEndpoint(a, b) == Endpoint.TAIL) {

                    // We know A---Bo-*C: R6 applies!
                    graph.setEndpoint(c, b, Endpoint.TAIL);
                    this.orientSimilarPairs(graph, this.getKnowledge(), c, b, Endpoint.TAIL);
                    this.logger.log("impliedOrientations", LogUtilsSearch.edgeOrientedMsg("Single tails (tail)", graph.getEdge(c, b)));

                    this.changeFlag = true;
                }

                if (graph.getEndpoint(a, b) == Endpoint.CIRCLE) {
//                    if (graph.isAdjacentTo(a, c)) continue;

                    this.logger.log("impliedOrientations", LogUtilsSearch.edgeOrientedMsg("Single tails (tail)", graph.getEdge(c, b)));

                    // We know A--oBo-*C and A,C nonadjacent: R7 applies!
                    graph.setEndpoint(c, b, Endpoint.TAIL);
                    this.orientSimilarPairs(graph, this.getKnowledge(), c, b, Endpoint.TAIL);
                    this.changeFlag = true;
                }

            }
        }
    }

    /**
     * Implements Zhang's rules R8, R9, R10, applies them over the graph once. Orient arrow tails. I.e., tries R8, R9,
     * and R10 in that sequence on each Ao-&gt;C in the graph.
     */
    public void rulesR8R9R10(Graph graph) {
        List<Node> nodes = graph.getNodes();

        for (Node c : nodes) {
            List<Node> intoCArrows = graph.getNodesInTo(c, Endpoint.ARROW);

            for (Node a : intoCArrows) {
                if (!(graph.getEndpoint(c, a) == Endpoint.CIRCLE)) continue;
                // We know Ao->C.

                // Try each of R8, R9, R10 in that order, stopping ASAP.
                if (!ruleR8(a, c, graph)) {
                    boolean b = ruleR9(a, c, graph);

                    if (!b) {
                        ruleR10(a, c, graph);
                    }
                }
            }
        }

    }

    /**
     * Orients every edge on a path as undirected (i.e. A---B).
     * <p>
     * DOES NOT CHECK IF SUCH EDGES ACTUALLY EXIST: MAY DO WEIRD THINGS IF PASSED AN ARBITRARY LIST OF NODES THAT IS NOT
     * A PATH.
     *
     * @param path The path to orient as all tails.
     */
    private void orientTailPath(List<Node> path, Graph graph) {
        for (int i = 0; i < path.size() - 1; i++) {
            Node n1 = path.get(i);
            Node n2 = path.get(i + 1);

            graph.setEndpoint(n1, n2, Endpoint.TAIL);
            this.orientSimilarPairs(graph, this.getKnowledge(), n1, n2, Endpoint.TAIL);
            graph.setEndpoint(n2, n1, Endpoint.TAIL);
            this.orientSimilarPairs(graph, this.getKnowledge(), n2, n1, Endpoint.TAIL);
            this.changeFlag = true;

            this.logger.log("impliedOrientations", LogUtilsSearch.edgeOrientedMsg("Orient circle undirectedPaths", graph.getEdge(n1, n2)));
        }
    }

    /**
     * Tries to apply Zhang's rule R8 to a pair of nodes A and C which are assumed to be such that Ao->C.
     * <p>
     * MAY HAVE WEIRD EFFECTS ON ARBITRARY NODE PAIRS.
     * <p>
     * R8: If Ao->C and A-->B-->C or A--oB-->C, then A-->C.
     *
     * @param a The node A.
     * @param c The node C.
     * @return Whether R8 was successfully applied.
     */
    private boolean ruleR8(Node a, Node c, Graph graph) {
        List<Node> intoCArrows = graph.getNodesInTo(c, Endpoint.ARROW);

        for (Node b : intoCArrows) {
            // We have B*-&gt;C.
            if (!graph.isAdjacentTo(a, b)) continue;
            if (!graph.isAdjacentTo(b, c)) continue;

            // We have A*-*B*-&gt;C.
            if (!(graph.getEndpoint(b, a) == Endpoint.TAIL)) continue;
            if (!(graph.getEndpoint(c, b) == Endpoint.TAIL)) continue;
            // We have A--*B-->C.

            if (graph.getEndpoint(a, b) == Endpoint.TAIL) continue;
            // We have A-->B-->C or A--oB-->C: R8 applies!

            this.logger.log("impliedOrientations", LogUtilsSearch.edgeOrientedMsg("R8", graph.getEdge(c, a)));

            graph.setEndpoint(c, a, Endpoint.TAIL);
            this.orientSimilarPairs(graph, this.getKnowledge(), c, a, Endpoint.TAIL);
            this.changeFlag = true;
            return true;
        }

        return false;
    }

    /**
     * Tries to apply Zhang's rule R9 to a pair of nodes A and C which are assumed to be such that Ao->C.
     * <p>
     * MAY HAVE WEIRD EFFECTS ON ARBITRARY NODE PAIRS.
     * <p>
     * R9: If Ao->C and there is an uncovered p.d. path u=<A,B,..,C> such that C,B nonadjacent, then A-->C.
     *
     * @param a The node A.
     * @param c The node C.
     * @return Whether R9 was successfully applied.
     */
    private boolean ruleR9(Node a, Node c, Graph graph) {
        List<List<Node>> ucPdPsToC = FciOrient.getUcPdPaths(a, c, graph);

        for (List<Node> u : ucPdPsToC) {
            Node b = u.get(1);
            if (graph.isAdjacentTo(b, c)) continue;
            if (b == c) continue;
            // We know u is as required: R9 applies!

            this.logger.log("impliedOrientations", LogUtilsSearch.edgeOrientedMsg("R9", graph.getEdge(c, a)));

            graph.setEndpoint(c, a, Endpoint.TAIL);
            this.orientSimilarPairs(graph, this.getKnowledge(), c, a, Endpoint.TAIL);
            this.changeFlag = true;
            return true;
        }

        return false;
    }

    /**
     * Tries to apply Zhang's rule R10 to a pair of nodes A and C which are assumed to be such that Ao->C.
     * <p>
     * MAY HAVE WEIRD EFFECTS ON ARBITRARY NODE PAIRS.
     * <p>
     * R10: If Ao->C, B-->C&lt;--D, there is an uncovered p.d. path u1=<A,M,...,B> and an uncovered p.d. path
     * u2=<A,N,...,D> with M != N and M,N nonadjacent then A-->C.
     *
     * @param a The node A.
     * @param c The node C.
     */
    private void ruleR10(Node a, Node c, Graph graph) {
        List<Node> intoCArrows = graph.getNodesInTo(c, Endpoint.ARROW);

        for (Node b : intoCArrows) {
            if (b == a) continue;

            if (!(graph.getEndpoint(c, b) == Endpoint.TAIL)) continue;
            // We know Ao->C and B-->C.

            for (Node d : intoCArrows) {
                if (d == a || d == b) continue;

                if (!(graph.getEndpoint(d, c) == Endpoint.TAIL)) continue;
                // We know Ao->C and B-->C<--D.

                List<List<Node>> ucPdPsToB = FciOrient.getUcPdPaths(a, b, graph);
                List<List<Node>> ucPdPsToD = FciOrient.getUcPdPaths(a, d, graph);
                for (List<Node> u1 : ucPdPsToB) {
                    Node m = u1.get(1);
                    for (List<Node> u2 : ucPdPsToD) {
                        Node n = u2.get(1);

                        if (m.equals(n)) continue;
                        if (graph.isAdjacentTo(m, n)) continue;
                        // We know B,D,u1,u2 as required: R10 applies!

                        this.logger.log("impliedOrientations", LogUtilsSearch.edgeOrientedMsg("R10", graph.getEdge(c, a)));

                        graph.setEndpoint(c, a, Endpoint.TAIL);
                        this.changeFlag = true;
                        this.orientSimilarPairs(graph, this.getKnowledge(), c, a, Endpoint.TAIL);
                        return;
                    }
                }
            }
        }

    }

    /**
     * Orients according to background knowledge
     */
    private void fciOrientbk(Knowledge bk, Graph graph, List<Node> variables) {
        this.logger.log("info", "Starting BK Orientation.");

        for (Iterator<KnowledgeEdge> it =
             bk.forbiddenEdgesIterator(); it.hasNext(); ) {
            KnowledgeEdge edge = it.next();

            //match strings to variables in the graph.
            Node from = GraphSearchUtils.translate(edge.getFrom(), variables);
            Node to = GraphSearchUtils.translate(edge.getTo(), variables);


            if (from == null || to == null) {
                continue;
            }

            if (graph.getEdge(from, to) == null) {
                continue;
            }

            // Orient to*-&gt;from
            graph.setEndpoint(to, from, Endpoint.ARROW);
            graph.setEndpoint(from, to, Endpoint.CIRCLE);
            this.changeFlag = true;
            this.logger.log("knowledgeOrientation", LogUtilsSearch.edgeOrientedMsg("Knowledge", graph.getEdge(from, to)));
        }

        for (Iterator<KnowledgeEdge> it =
             bk.requiredEdgesIterator(); it.hasNext(); ) {
            KnowledgeEdge edge = it.next();

            //match strings to variables in this graph
            Node from = GraphSearchUtils.translate(edge.getFrom(), variables);
            Node to = GraphSearchUtils.translate(edge.getTo(), variables);

            if (from == null || to == null) {
                continue;
            }

            if (graph.getEdge(from, to) == null) {
                continue;
            }

            graph.setEndpoint(to, from, Endpoint.TAIL);
            graph.setEndpoint(from, to, Endpoint.ARROW);
            this.changeFlag = true;
            this.logger.log("knowledgeOrientation", LogUtilsSearch.edgeOrientedMsg("Knowledge", graph.getEdge(from, to)));
        }

        this.logger.log("info", "Finishing BK Orientation.");
    }


    /**
     * @return the maximum length of any discriminating path, or -1 of unlimited.
     */
    public int getMaxPathLength() {
        return this.maxPathLength;
    }

    /**
     * @param maxPathLength the maximum length of any discriminating path, or -1 if unlimited.
     */
    public void setMaxPathLength(int maxPathLength) {
        if (maxPathLength < -1) {
            throw new IllegalArgumentException("Max path length must be -1 (unlimited) or >= 0: " + maxPathLength);
        }

        this.maxPathLength = maxPathLength;
    }

    /**
     * True iff verbose output should be printed.
     */
    public boolean isVerbose() {
        return this.verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public void setTruePag(Graph truePag) {
        this.truePag = truePag;
    }

    /**
     * The true PAG if available. Can be null.
     */
    public Graph getTruePag() {
        return this.truePag;
    }

    private void orientSimilarPairs(Graph graph, Knowledge knowledge, Node x, Node y, Endpoint mark) {
        if (x.getName().equals("time") || y.getName().equals("time")) {
            return;
        }
        System.out.println("Entering orient similar pairs method for x and y: " + x + ", " + y);
        int ntiers = knowledge.getNumTiers();
        int indx_tier = knowledge.isInWhichTier(x);
        int indy_tier = knowledge.isInWhichTier(y);
        int tier_diff = FastMath.max(indx_tier, indy_tier) - FastMath.min(indx_tier, indy_tier);
        int indx_comp = -1;
        int indy_comp = -1;
        List<String> tier_x = knowledge.getTier(indx_tier);
//        Collections.sort(tier_x);
        List<String> tier_y = knowledge.getTier(indy_tier);
//        Collections.sort(tier_y);

        int i;
        for (i = 0; i < tier_x.size(); ++i) {
            if (getNameNoLag(x.getName()).equals(getNameNoLag(tier_x.get(i)))) {
                indx_comp = i;
                break;
            }
        }

        for (i = 0; i < tier_y.size(); ++i) {
            if (getNameNoLag(y.getName()).equals(getNameNoLag(tier_y.get(i)))) {
                indy_comp = i;
                break;
            }
        }

        if (indx_comp == -1) System.out.println("WARNING: indx_comp = -1!!!! ");
        if (indy_comp == -1) System.out.println("WARNING: indy_comp = -1!!!! ");

        for (i = 0; i < ntiers - tier_diff; ++i) {
            if (knowledge.getTier(i).size() == 1) continue;
            String A;
            Node x1;
            String B;
            Node y1;
            if (indx_tier >= indy_tier) {
                List<String> tmp_tier1 = knowledge.getTier(i + tier_diff);
//                Collections.sort(tmp_tier1);
                List<String> tmp_tier2 = knowledge.getTier(i);
//                Collections.sort(tmp_tier2);
                A = tmp_tier1.get(indx_comp);
                B = tmp_tier2.get(indy_comp);
                if (A.equals(B)) continue;
                if (A.equals(tier_x.get(indx_comp)) && B.equals(tier_y.get(indy_comp))) continue;
                if (B.equals(tier_x.get(indx_comp)) && A.equals(tier_y.get(indy_comp))) continue;
                x1 = this.independenceTest.getVariable(A);
                y1 = this.independenceTest.getVariable(B);

                if (graph.isAdjacentTo(x1, y1) && graph.getEndpoint(x1, y1) == Endpoint.CIRCLE) {
                    System.out.print("Orient edge " + graph.getEdge(x1, y1).toString());
                    graph.setEndpoint(x1, y1, mark);
                    System.out.println(" by structure knowledge as: " + graph.getEdge(x1, y1).toString());
                }
            }
        }

    }


    public String getNameNoLag(Object obj) {
        String tempS = obj.toString();
        if (tempS.indexOf(':') == -1) {
            return tempS;
        } else return tempS.substring(0, tempS.indexOf(':'));
    }


}

