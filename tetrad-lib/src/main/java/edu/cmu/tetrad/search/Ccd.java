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

import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.utils.SepsetProducer;
import edu.cmu.tetrad.search.utils.SepsetsSet;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.SublistGenerator;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.*;

/**
 * <p>Implemented the Cyclic Causal Discovery (CCD) algorithm by Thomas Richardson.
 * A reference for this is here:</p>
 * <p>Mooij, J. M., &amp; Claassen, T. (2020, August). Constraint-based causal discovery
 * using partial ancestral graphs in the presence of cycles. In Conference on Uncertainty
 * in Artificial Intelligence (pp. 1159-1168). PMLR.</p>
 * <p>See also Chapter 7 of Glymour and Cooper, eds., Computation, Causation, and Discovery</p>
 * <p>The graph takes continuous data from a cyclic model as input and returns a cyclic
 * PAG graphs, with various types of underlining, that represents a Markov equivalence of
 * the true DAG.</p>
 *
 * <p>This class is not configured to respect knowledge of forbidden and required
 * edges.</p>
 *
 * @author Frank C. Wimberly
 * @author josephramsey
 */
public final class Ccd implements IGraphSearch {
    private final IndependenceTest independenceTest;
    private final List<Node> nodes;
    private boolean applyR1;

    /**
     * Construct a CCD algorithm with the given independence test.
     *
     * @param test The test to be used.
     * @see IndependenceTest
     */
    public Ccd(IndependenceTest test) {
        if (test == null) throw new NullPointerException("Test is not provided");
        this.independenceTest = test;
        this.nodes = test.getVariables();
    }

    //======================================== PUBLIC METHODS ====================================//

    /**
     * The search method assumes that the IndependenceTest provided to the constructor is a conditional independence
     * oracle for the SEM (or Bayes network) which describes the causal structure of the population. The method returns
     * a PAG instantiated as a Tetrad GaSearchGraph which represents the equivalence class of digraphs which are
     * d-separation equivalent to the digraph of the underlying model (SEM or BN). Although they are not returned
     * by the search method it also computes two lists of triples which, respectively store the underlines and dotted
     * underlines of the PAG.
     */
    public Graph search() {
        Map<Triple, Set<Node>> supSepsets = new HashMap<>();

        // Step A.
        Fas fas = new Fas(this.independenceTest);
        Graph psi = fas.search();
        psi.reorientAllWith(Endpoint.CIRCLE);

        SepsetProducer sepsets = new SepsetsSet(fas.getSepsets(), this.independenceTest);

        stepB(psi);
        stepC(psi, sepsets);
        stepD(psi, sepsets, supSepsets);
        stepE(supSepsets, psi);
        stepF(psi, sepsets, supSepsets);

        orientAwayFromArrow(psi);

        return psi;
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

    //======================================== PRIVATE METHODS ====================================//

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

    private void stepB(Graph graph) {
        Map<Triple, Double> colliders = new HashMap<>();
        Map<Triple, Double> noncolliders = new HashMap<>();

        for (Node node : this.nodes) {
            doNodeCollider(graph, colliders, noncolliders, node);
        }

        List<Triple> collidersList = new ArrayList<>(colliders.keySet());
        List<Triple> noncollidersList = new ArrayList<>(noncolliders.keySet());

        for (Triple triple : collidersList) {
            Node a = triple.getX();
            Node b = triple.getY();
            Node c = triple.getZ();

            graph.removeEdge(a, b);
            graph.removeEdge(c, b);
            graph.addDirectedEdge(a, b);
            graph.addDirectedEdge(c, b);
        }

        for (Triple triple : noncollidersList) {
            Node a = triple.getX();
            Node b = triple.getY();
            Node c = triple.getZ();

            graph.underlines().addUnderlineTriple(a, b, c);
        }
    }

    private void doNodeCollider(Graph graph, Map<Triple, Double> colliders, Map<Triple, Double> noncolliders, Node b) {
        List<Node> adjacentNodes = graph.getAdjacentNodes(b);

        if (adjacentNodes.size() < 2) {
            return;
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

            List<Node> adja = graph.getAdjacentNodes(a);
            double score = Double.POSITIVE_INFINITY;
            List<Node> S = null;

            SublistGenerator cg2 = new SublistGenerator(adja.size(), -1);
            int[] comb2;

            while ((comb2 = cg2.next()) != null) {
                List<Node> s = GraphUtils.asList(comb2, adja);
                this.independenceTest.checkIndependence(a, c, s);
                double _score = this.independenceTest.getScore();

                if (_score < score) {
                    score = _score;
                    S = s;
                }
            }

            List<Node> adjc = graph.getAdjacentNodes(c);

            SublistGenerator cg3 = new SublistGenerator(adjc.size(), -1);
            int[] comb3;

            while ((comb3 = cg3.next()) != null) {
                List<Node> s = GraphUtils.asList(comb3, adjc);
                this.independenceTest.checkIndependence(c, a, s);
                double _score = this.independenceTest.getScore();

                if (_score < score) {
                    score = _score;
                    S = s;
                }
            }

            // This could happen if there are undefined values and such.
            if (S == null) {
                continue;
            }

            if (S.contains(b)) {
                noncolliders.put(new Triple(a, b, c), score);
            } else {
                colliders.put(new Triple(a, b, c), score);
            }
        }
    }

    private void stepC(Graph psi, SepsetProducer sepsets) {
        TetradLogger.getInstance().log("info", "\nStep C");

        EDGE:
        for (Edge edge : psi.getEdges()) {
            Node x = edge.getNode1();
            Node y = edge.getNode2();

            // x and y are adjacent.

            List<Node> adjx = psi.getAdjacentNodes(x);
            List<Node> adjy = psi.getAdjacentNodes(y);

            for (Node node : adjx) {
                if (psi.getEdge(node, x).getProximalEndpoint(x) == Endpoint.ARROW
                        && psi.underlines().isUnderlineTriple(y, x, node)) {
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
                if (!(psi.getEndpoint(y, x) == Endpoint.CIRCLE &&
                        (psi.getEndpoint(x, y) == Endpoint.CIRCLE || psi.getEndpoint(x, y) == Endpoint.TAIL))) {
                    continue;
                }

                //...X is not in sepset<A, Y>...
                List<Node> sepset = sepsets.getSepset(a, y);

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

    private void stepD(Graph psi, SepsetProducer sepsets, Map<Triple, Set<Node>> supSepsets) {
        Map<Node, List<Node>> local = new HashMap<>();

        for (Node node : psi.getNodes()) {
            local.put(node, local(psi, node));
        }

        for (Node node : this.nodes) {
            doNodeStepD(psi, sepsets, supSepsets, local, node);
        }
    }

    private void doNodeStepD(Graph psi, SepsetProducer sepsets, Map<Triple, Set<Node>> supSepsets,
                             Map<Node, List<Node>> local, Node b) {
        List<Node> adj = psi.getAdjacentNodes(b);

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

            List<Node> S = sepsets.getSepset(a, c);
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

                if (sepsets.isIndependent(a, c, new ArrayList<>(B))) {
                    psi.underlines().addDottedUnderlineTriple(a, b, c);
                    supSepsets.put(new Triple(a, b, c), B);
                    break;
                }
            }
        }
    }

    private void stepE(Map<Triple, Set<Node>> supSepset, Graph psi) {
        TetradLogger.getInstance().log("info", "\nStep E");

        for (Triple triple : psi.underlines().getDottedUnderlines()) {
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

    private void stepF(Graph psi, SepsetProducer sepsets, Map<Triple, Set<Node>> supSepsets) {
        for (Triple triple : psi.underlines().getDottedUnderlines()) {
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
                List<Node> listSupSepUnionD = new ArrayList<>(supSepUnionD);

                //If A and C are a pair of vertices d-connected given
                //SupSepset<A,B,C> union {D} then orient Bo-oD or B-oD
                //as B->D in psi.
                if (!sepsets.isIndependent(a, c, listSupSepUnionD)) {
                    psi.removeEdge(b, d);
                    psi.addDirectedEdge(b, d);
                    orientAwayFromArrow(b, d, psi);
                }
            }
        }
    }

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

    private void orientAwayFromArrow(Node a, Node b, Graph graph) {
        if (!isApplyR1()) return;

        for (Node c : graph.getAdjacentNodes(b)) {
            if (c == a) continue;
            orientAwayFromArrowVisit(a, b, c, graph);
        }
    }

    private boolean orientAwayFromArrowVisit(Node a, Node b, Node c, Graph graph) {
        if (!Edges.isNondirectedEdge(graph.getEdge(b, c))) {
            return false;
        }

        if (!(graph.underlines().isUnderlineTriple(a, b, c))) {
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
}






