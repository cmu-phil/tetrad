/// ////////////////////////////////////////////////////////////////////////////
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
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.test.CachingIndependenceTest;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.utils.SepsetProducer;
import edu.cmu.tetrad.search.utils.SepsetsMaxP;
import edu.cmu.tetrad.search.utils.SepsetsSet;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.SublistGenerator;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.*;

/**
 * Implemented the Cyclic Causal Discovery (CCD) algorithm by Thomas Richardson. A reference to this is here:
 * <p>
 * Richardson, T. S. (2013). A discovery algorithm for directed cyclic graphs. arXiv preprint arXiv:1302.3599.
 * <p>
 * See also Chapter 7 of:
 * <p>
 * Glymour, C. N., &amp; Cooper, G. F. (Eds.). (1999). Computation, causation, and discovery. AAAI Press.
 * <p>
 * The graph takes continuous data from a cyclic model as input and returns a cyclic PAG graphs, with various types of
 * underlining, that represents a Markov equivalence of the true DAG.
 * <p>
 * This class is not configured to respect knowledge of forbidden and required edges (nor will be).
 *
 * @author Frank C. Wimberly
 * @author josephramsey
 * @version $Id: $Id
 */
public final class Ccd implements IGraphSearch {
    /**
     * The nodes in the graph.
     */
    private final List<Node> nodes;
    /**
     * The independence test to be used.
     */
    private IndependenceTest test;
    /**
     * Whether the R1 rule should be applied.
     */
    private boolean applyR1;
    /**
     * Indicates whether detailed logging or debugging information should be output during the execution of the CCD
     * algorithm. When set to true, additional information regarding the algorithm's operations and decisions may be
     * logged, which can be useful for debugging or understanding its behavior. Otherwise, minimal output is produced.
     */
    private boolean verbose;
    /**
     * The maximum depth of conditional independence tests in the CCD algorithm. This value limits the number of
     * variables that can be conditioned on during the search process. A smaller depth may speed up the algorithm but
     * might reduce the accuracy of the resulting graph, while a larger depth allows for more thorough searches at a
     * potential cost of increased computational time.
     */
    private int depth = -1;

    /**
     * Construct a CCD algorithm with the given independence test.
     *
     * @param test The test to be used.
     * @see IndependenceTest
     */
    public Ccd(IndependenceTest test) {
        if (test == null) throw new NullPointerException("Test is not provided");
        this.test = new CachingIndependenceTest(test);
        this.nodes = test.getVariables();
    }


    /**
     * The search method assumes that the IndependenceTest provided to the constructor is a conditional independence
     * oracle for the SEM (or Bayes network) which describes the causal structure of the population. The method returns
     * a PAG instantiated as a Tetrad GaSearchGraph which represents the equivalence class of digraphs which are
     * m-separation equivalent to the digraph of the underlying model (SEM or BN). Although they are not returned by the
     * search method, it also computes two lists of triples which, respectively, store the underlines and dotted
     * underlines of the PAG.
     *
     * @return The CCD cyclic PAG for the data.
     */
    public Graph search() throws InterruptedException {
        Map<Triple, Set<Node>> supSepsets = new HashMap<>();

        // Step A.
        if (verbose) TetradLogger.getInstance().log("Step A--running FAS");

        Fas fas = new Fas(this.test);
        Graph psi = fas.search();
        psi.reorientAllWith(Endpoint.CIRCLE);

//        SepsetProducer sepsets = new SepsetsSet(fas.getSepsets(), this.test);
        SepsetProducer sepsets = new SepsetsMaxP(psi, test, depth);

        stepB(psi, sepsets);
        stepC(psi, sepsets);
        stepD(psi, sepsets, supSepsets);
        stepE(supSepsets, psi);
        stepF(psi, sepsets, supSepsets);

        orientAwayFromArrow(psi);

        return psi;
    }

    public IndependenceTest getTest() {
        return test;
    }

    public void setTest(IndependenceTest test) {
        List<Node> nodes = this.test.getVariables();
        List<Node> _nodes = test.getVariables();

        if (!nodes.equals(_nodes)) {
            throw new IllegalArgumentException(String.format("The nodes of the proposed new test are not equal list-wise\n" + "to the nodes of the existing test."));
        }

        this.test = test;
    }

    /**
     * Returns true iff the R1 rule should be applied.
     *
     * @return True if the case.
     */
    public boolean isApplyR1() {
        return this.applyR1;
    }

    /**
     * Sets whether the R1 rule should be applied.
     *
     * @param applyR1 True if the case.
     */
    public void setApplyR1(boolean applyR1) {
        this.applyR1 = applyR1;
    }

    /**
     * Orients the edges of the graph away from the arrow direction.
     *
     * @param graph The graph to orient.
     */
    private void orientAwayFromArrow(Graph graph) {
        for (Edge edge : graph.getEdges()) {
            Node n1 = edge.getNode1();
            Node n2 = edge.getNode2();

            edge = graph.getEdge(n1, n2);

            if (edge.pointsTowards(n1)) {
                orientAwayFromArrow(n2, n1, graph);
            } else if (edge.pointsTowards(n2)) {
                orientAwayFromArrow(n1, n2, graph);
            }
        }
    }

    /**
     * Perform step B of the CCD algorithm on the given graph.
     *
     * @param psi The graph on which step B is performed.
     */
    private void stepB(Graph psi, SepsetProducer sepsets) throws InterruptedException {
        if (verbose) TetradLogger.getInstance().log("Step B - Add underlines and colliders");

        // For every node b, inspect unshielded triples aâbâc
        for (Node b : this.nodes) {
            List<Node> adj = new ArrayList<>(psi.getAdjacentNodes(b));
            if (adj.size() < 2) continue;

            ChoiceGenerator cg = new ChoiceGenerator(adj.size(), 2);
            int[] idx;
            while ((idx = cg.next()) != null) {
                Node a = adj.get(idx[0]);
                Node c = adj.get(idx[1]);

                // Only unshielded triples
                if (psi.isAdjacentTo(a, c)) continue;

                // Use the stored sepset that justified removing aâc in Step A
                Set<Node> S = sepsets.getSepset(a, c, -1, null);
                if (S == null) continue; // be defensive

                if (S.contains(b)) {
                    // non-collider at b
                    psi.addUnderlineTriple(a, b, c);
                    if (verbose)
                        TetradLogger.getInstance().log("StepB: underline (non-collider) " + a + "-" + b + "-" + c + " ; sepset(a,c)=" + S);
                } else {
                    // collider at b: a -> b <- c
                    psi.removeEdge(a, b);
                    psi.removeEdge(c, b);
                    psi.addDirectedEdge(a, b);
                    psi.addDirectedEdge(c, b);
                    if (verbose)
                        TetradLogger.getInstance().log("StepB: collider " + a + "->" + b + "<-" + c + " ; sepset(a,c)=" + S);
                }
            }
        }
    }

    /**
     * Performs step C of the CCD algorithm on the given graph.
     *
     * @param psi     The graph on which step C is performed.
     * @param sepsets The sepsets used for conditional independence tests.
     */
    private void stepC(Graph psi, SepsetProducer sepsets) throws InterruptedException {
        TetradLogger.getInstance().log("\nStep C - Propagating some orientations");

        EDGE:
        for (Edge edge : psi.getEdges()) {
            Node x = edge.getNode1();
            Node y = edge.getNode2();

            // x and y are adjacent.

            List<Node> adjx = psi.getAdjacentNodes(x);
            List<Node> adjy = psi.getAdjacentNodes(y);

            for (Node node : adjx) {
                if (psi.getEdge(node, x).getProximalEndpoint(x) == Endpoint.ARROW && psi.isUnderlineTriple(y, x, node)) {
                    continue EDGE;
                }
            }

            // Check each A
            for (Node a : this.nodes) {
                if (a == x) continue;
                if (a == y) continue;

                //...A is not adjacent to X and A is not adjacent to Y...
                if (adjx.contains(a)) continue;
                if (adjy.contains(a)) continue;

                // Orientable...
                if (!(psi.getEndpoint(y, x) == Endpoint.CIRCLE && (psi.getEndpoint(x, y) == Endpoint.CIRCLE || psi.getEndpoint(x, y) == Endpoint.TAIL))) {
                    continue;
                }

                //...X is not in sepset<A, Y>...
                Set<Node> sepset = sepsets.getSepset(a, y, -1, null);

                if (sepset == null) {
                    continue;
                }

                if (sepset.contains(x)) continue;

                if (!sepsets.isIndependent(a, x, sepset)) {
                    psi.removeEdge(x, y);
                    psi.addDirectedEdge(y, x);
                    orientAwayFromArrow(y, x, psi);
                    break;
                }
            }
        }
    }

    /**
     * Performs step D of the CCD algorithm on the given graph.
     *
     * @param psi        The graph on which step D is performed.
     * @param sepsets    The sepsets used for conditional independence tests.
     * @param supSepsets The map of sepsets.
     */
    private void stepD(Graph psi, SepsetProducer sepsets, Map<Triple, Set<Node>> supSepsets) throws InterruptedException {
        if (verbose) TetradLogger.getInstance().log("Step D - Adding dotted underline triples");

        Map<Node, List<Node>> local = new HashMap<>();

        for (Node node : psi.getNodes()) {
            local.put(node, local(psi, node));
        }

        for (Node node : this.nodes) {
            doNodeStepD(psi, sepsets, supSepsets, local, node);
        }
    }

    /**
     * Performs step D of the CCD algorithm on the given graph.
     *
     * @param psi        The graph on which step D is performed.
     * @param sepsets    The sepsets used for conditional independence tests.
     * @param supSepsets The map of sepsets.
     * @param local      The map of local nodes.
     * @param b          The node to consider.
     */
    private void doNodeStepD(Graph psi, SepsetProducer sepsets, Map<Triple, Set<Node>> supSepsets, Map<Node, List<Node>> local, Node b) throws InterruptedException {
        List<Node> adj = new ArrayList<>(psi.getAdjacentNodes(b));

        if (adj.size() < 2) {
            return;
        }

        ChoiceGenerator gen = new ChoiceGenerator(adj.size(), 2);
        int[] choice;

        while ((choice = gen.next()) != null) {
            List<Node> _adj = GraphUtils.asList(choice, adj);
            Node a = _adj.get(0);
            Node c = _adj.get(1);

            if (!psi.isDefCollider(a, b, c)) continue;

            Set<Node> S = sepsets.getSepset(a, c, -1, null);
            if (S == null) continue;
            ArrayList<Node> TT = new ArrayList<>(local.get(a));
            TT.removeAll(S);
            TT.remove(b);
            TT.remove(c);

            SublistGenerator gen2 = new SublistGenerator(TT.size(), -1);
            int[] choice2;

            while ((choice2 = gen2.next()) != null) {
                Set<Node> T = GraphUtils.asSet(choice2, TT);
                Set<Node> B = new HashSet<>(T);
                B.addAll(S);
                B.add(b);

                if (sepsets.isIndependent(a, c, new HashSet<>(B))) {
                    psi.addDottedUnderlineTriple(a, b, c);
                    supSepsets.put(new Triple(a, b, c), B);
                    break;
                }
            }
        }
    }

    /**
     * Performs step E of the CCD algorithm on the given graph.
     *
     * @param supSepset The map containing the sepsets.
     * @param psi       The graph on which step E is performed.
     */
    private void stepE(Map<Triple, Set<Node>> supSepset, Graph psi) {
        TetradLogger.getInstance().log("\nStep E - Propagating some orientations");

        for (Triple triple : psi.getDottedUnderlines()) {
            Node a = triple.getX();
            Node b = triple.getY();
            Node c = triple.getZ();

            List<Node> aAdj = psi.getAdjacentNodes(a);

            for (Node d : aAdj) {
                if (d == b) continue;

                if (psi.getEndpoint(b, d) != Endpoint.CIRCLE) {
                    continue;
                }

                if (supSepset.get(triple).contains(d)) {

                    // Orient B*-oD as B*-D
                    psi.setEndpoint(b, d, Endpoint.TAIL);
                } else {
                    if (psi.getEndpoint(d, b) == Endpoint.ARROW) {
                        continue;
                    }

                    // Or orient Bo-oD or B-oD as B->D...
                    psi.removeEdge(b, d);
                    psi.addDirectedEdge(b, d);
                    orientAwayFromArrow(b, d, psi);
                }
            }

            List<Node> cAdj = psi.getAdjacentNodes(c);

            for (Node d : cAdj) {
                if (d == b) continue;

                if (psi.getEndpoint(b, d) != Endpoint.CIRCLE) {
                    continue;
                }

                if (supSepset.get(triple).contains(d)) {

                    // Orient B*-oD as B*-D
                    psi.setEndpoint(b, d, Endpoint.TAIL);
                } else {
                    if (psi.getEndpoint(d, b) == Endpoint.ARROW) {
                        continue;
                    }

                    // Or orient Bo-oD or B-oD as B->D...
                    psi.removeEdge(b, d);
                    psi.addDirectedEdge(b, d);
                    orientAwayFromArrow(b, d, psi);
                }
            }
        }
    }

    /**
     * Performs step F of the CCD algorithm on the given graph.
     *
     * @param psi        The graph on which step F is performed.
     * @param sepsets    The sepsets used for conditional independence tests.
     * @param supSepsets The map of sepsets.
     */
    private void stepF(Graph psi, SepsetProducer sepsets, Map<Triple, Set<Node>> supSepsets) throws InterruptedException {
        if (verbose) TetradLogger.getInstance().log("Step F - Propagating more orientations");

        for (Triple triple : psi.getDottedUnderlines()) {
            Node a = triple.getX();
            Node b = triple.getY();
            Node c = triple.getZ();

            Set<Node> adj = new HashSet<>(psi.getAdjacentNodes(a));
            adj.addAll(psi.getAdjacentNodes(c));

            for (Node d : adj) {
                if (psi.getEndpoint(b, d) != Endpoint.CIRCLE) {
                    continue;
                }

                if (psi.getEndpoint(d, b) == Endpoint.ARROW) {
                    continue;
                }

                //...and D is not adjacent to both A and C in psi...
                if (psi.isAdjacentTo(a, d) && psi.isAdjacentTo(c, d)) {
                    continue;
                }

                //...and B and D are adjacent...
                if (!psi.isAdjacentTo(b, d)) {
                    continue;
                }

                Set<Node> supSepUnionD = new HashSet<>();
                supSepUnionD.add(d);
                supSepUnionD.addAll(supSepsets.get(triple));
                Set<Node> listSupSepUnionD = new HashSet<>(supSepUnionD);

                //If A and C are a pair of vertices d-connected given
                //SupSepset<A, B, C> union {D} then orient Bo-oD or B-oD
                //as B->D in psi.
                if (!sepsets.isIndependent(a, c, listSupSepUnionD)) {
                    psi.removeEdge(b, d);
                    psi.addDirectedEdge(b, d);
                    orientAwayFromArrow(b, d, psi);
                }
            }
        }
    }

    /**
     * Performs the local algorithm for finding the nodes adjacent to a given node in a graph.
     *
     * @param psi The graph in which to perform the local algorithm.
     * @param x   The node for which to find the adjacent nodes.
     * @return The list of adjacent nodes to the given node.
     */
    private List<Node> local(Graph psi, Node x) {
        Set<Node> nodes = new HashSet<>(psi.getAdjacentNodes(x));

        for (Node y : new HashSet<>(nodes)) {
            for (Node z : psi.getAdjacentNodes(y)) {
                if (psi.isDefCollider(x, y, z)) {
                    if (z != x) {
                        nodes.add(z);
                    }
                }
            }
        }

        return new ArrayList<>(nodes);
    }

    /**
     * Orients the edges of the graph away from the arrow direction.
     *
     * @param a     The node A.
     * @param b     The node B.
     * @param graph The graph to orient.
     */
    private void orientAwayFromArrow(Node a, Node b, Graph graph) {
        if (!isApplyR1()) return;

        for (Node c : graph.getAdjacentNodes(b)) {
            if (c == a) continue;
            orientAwayFromArrowVisit(a, b, c, graph);
        }
    }

    /**
     * Orients the edges of the graph away from the arrow direction.
     *
     * @param a     The node A.
     * @param b     The node B.
     * @param c     The node C.
     * @param graph The graph to orient.
     * @return True if the edges are successfully oriented away from the arrow direction, otherwise false.
     */
    private boolean orientAwayFromArrowVisit(Node a, Node b, Node c, Graph graph) {
        if (!Edges.isNondirectedEdge(graph.getEdge(b, c))) {
            return false;
        }

        if (!(graph.isUnderlineTriple(a, b, c))) {
            return false;
        }


        if (graph.getEdge(b, c).pointsTowards(b)) {
            return false;
        }

        graph.removeEdge(b, c);
        graph.addDirectedEdge(b, c);

        for (Node d : graph.getAdjacentNodes(c)) {
            if (d == b) return true;

            Edge bc = graph.getEdge(b, c);

            if (!orientAwayFromArrowVisit(b, c, d, graph)) {
                graph.removeEdge(b, c);
                graph.addEdge(bc);
            }
        }

        return true;
    }

    /**
     * Sets whether verbose output should be logged
     *
     * @param verbose true to enable verbose output, false to disable it.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
        test.setVerbose(verbose);
    }

    /**
     * Sets the depth for the CCD algorithm.
     *
     * @param depth the maximum depth to be used when performing independence tests.
     */
    public void setDepth(int depth) {
        this.depth = depth;
    }
}







