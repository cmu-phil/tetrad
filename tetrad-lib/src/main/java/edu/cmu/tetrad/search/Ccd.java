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

import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.DepthChoiceGenerator;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.*;

/**
 * This class provides the data structures and methods for carrying out the Cyclic Causal Discovery algorithm (CCD)
 * described by Thomas Richardson and Peter Spirtes in Chapter 7 of Computation, Causation, & Discovery by Glymour and
 * Cooper eds.  The comments that appear below are keyed to the algorithm specification on pp. 269-271. </p> The search
 * method returns an instance of a Graph but it also constructs two lists of node triples which represent the underlines
 * and dotted underlines that the algorithm discovers.
 *
 * @author Frank C. Wimberly
 * @author Joseph Ramsey
 */
public final class Ccd implements GraphSearch {
    private final IndependenceTest independenceTest;
    private int depth = -1;
    private IKnowledge knowledge;
    private final List<Node> nodes;
    private boolean applyR1 = false;

    public Ccd(final IndependenceTest test) {
        if (test == null) throw new NullPointerException();
        this.independenceTest = test;
        this.nodes = test.getVariables();
    }

    //======================================== PUBLIC METHODS ====================================//

    /**
     * The search method assumes that the IndependenceTest provided to the constructor is a conditional independence
     * oracle for the SEM (or Bayes network) which describes the causal structure of the population. The method returns
     * a PAG instantiated as a Tetrad GaSearchGraph which represents the equivalence class of digraphs which are
     * d-separation equivalent to the digraph of the underlying model (SEM or BN). </p> Although they are not returned
     * by the search method it also computes two lists of triples which, respectively store the underlines and dotted
     * underlines of the PAG.
     */
    public Graph search() {
        final Map<Triple, Set<Node>> supSepsets = new HashMap<>();

        // Step A.
        final Fas fas = new Fas(this.independenceTest);
        final Graph psi = fas.search();
        psi.reorientAllWith(Endpoint.CIRCLE);

        final SepsetProducer sepsets = new SepsetsSet(fas.getSepsets(), this.independenceTest);

        stepB(psi);
        stepC(psi, sepsets);
        stepD(psi, sepsets, supSepsets);
        stepE(supSepsets, psi);
        stepF(psi, sepsets, supSepsets);

        orientAwayFromArrow(psi);

        return psi;
    }

    private void orientAwayFromArrow(final Graph graph) {
        for (Edge edge : graph.getEdges()) {
            final Node n1 = edge.getNode1();
            final Node n2 = edge.getNode2();

            edge = graph.getEdge(n1, n2);

            if (edge.pointsTowards(n1)) {
                orientAwayFromArrow(n2, n1, graph);
            } else if (edge.pointsTowards(n2)) {
                orientAwayFromArrow(n1, n2, graph);
            }
        }
    }

    public IKnowledge getKnowledge() {
        return this.knowledge;
    }

    public int getDepth() {
        return this.depth;
    }

    public void setDepth(final int depth) {
        this.depth = depth;
    }

    public void setKnowledge(final IKnowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException();
        }
        this.knowledge = knowledge;
    }

    public long getElapsedTime() {
        return 0;
    }

    //======================================== PRIVATE METHODS ====================================//

    private void stepB(final Graph graph) {
        final Map<Triple, Double> colliders = new HashMap<>();
        final Map<Triple, Double> noncolliders = new HashMap<>();

        for (final Node node : this.nodes) {
            doNodeCollider(graph, colliders, noncolliders, node);
        }

        final List<Triple> collidersList = new ArrayList<>(colliders.keySet());
        final List<Triple> noncollidersList = new ArrayList<>(noncolliders.keySet());

        for (final Triple triple : collidersList) {
            final Node a = triple.getX();
            final Node b = triple.getY();
            final Node c = triple.getZ();

            graph.removeEdge(a, b);
            graph.removeEdge(c, b);
            graph.addDirectedEdge(a, b);
            graph.addDirectedEdge(c, b);
        }

        for (final Triple triple : noncollidersList) {
            final Node a = triple.getX();
            final Node b = triple.getY();
            final Node c = triple.getZ();

            graph.addUnderlineTriple(a, b, c);
        }
    }

    private void doNodeCollider(final Graph graph, final Map<Triple, Double> colliders, final Map<Triple, Double> noncolliders, final Node b) {
        final List<Node> adjacentNodes = graph.getAdjacentNodes(b);

        if (adjacentNodes.size() < 2) {
            return;
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

            final List<Node> adja = graph.getAdjacentNodes(a);
            double score = Double.POSITIVE_INFINITY;
            List<Node> S = null;

            final DepthChoiceGenerator cg2 = new DepthChoiceGenerator(adja.size(), -1);
            int[] comb2;

            while ((comb2 = cg2.next()) != null) {
                final List<Node> s = GraphUtils.asList(comb2, adja);
                this.independenceTest.isIndependent(a, c, s);
                final double _score = this.independenceTest.getScore();

                if (_score < score) {
                    score = _score;
                    S = s;
                }
            }

            final List<Node> adjc = graph.getAdjacentNodes(c);

            final DepthChoiceGenerator cg3 = new DepthChoiceGenerator(adjc.size(), -1);
            int[] comb3;

            while ((comb3 = cg3.next()) != null) {
                final List<Node> s = GraphUtils.asList(comb3, adjc);
                this.independenceTest.isIndependent(c, a, s);
                final double _score = this.independenceTest.getScore();

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

    private void stepC(final Graph psi, final SepsetProducer sepsets) {
        TetradLogger.getInstance().log("info", "\nStep C");

        EDGE:
        for (final Edge edge : psi.getEdges()) {
            final Node x = edge.getNode1();
            final Node y = edge.getNode2();

            // x and y are adjacent.

            final List<Node> adjx = psi.getAdjacentNodes(x);
            final List<Node> adjy = psi.getAdjacentNodes(y);

            for (final Node node : adjx) {
                if (psi.getEdge(node, x).getProximalEndpoint(x) == Endpoint.ARROW
                        && psi.isUnderlineTriple(y, x, node)) {
                    continue EDGE;
                }
            }

            // Check each A
            for (final Node a : this.nodes) {
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
                final List<Node> sepset = sepsets.getSepset(a, y);

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

    private void stepD(final Graph psi, final SepsetProducer sepsets, final Map<Triple, Set<Node>> supSepsets) {
        final Map<Node, List<Node>> local = new HashMap<>();

        for (final Node node : psi.getNodes()) {
            local.put(node, local(psi, node));
        }

        for (final Node node : this.nodes) {
            doNodeStepD(psi, sepsets, supSepsets, local, node);
        }
    }

    private void doNodeStepD(final Graph psi, final SepsetProducer sepsets, final Map<Triple, Set<Node>> supSepsets,
                             final Map<Node, List<Node>> local, final Node b) {
        final List<Node> adj = psi.getAdjacentNodes(b);

        if (adj.size() < 2) {
            return;
        }

        final ChoiceGenerator gen = new ChoiceGenerator(adj.size(), 2);
        int[] choice;

        while ((choice = gen.next()) != null) {
            final List<Node> _adj = GraphUtils.asList(choice, adj);
            final Node a = _adj.get(0);
            final Node c = _adj.get(1);

            if (!psi.isDefCollider(a, b, c)) continue;

            final List<Node> S = sepsets.getSepset(a, c);
            if (S == null) continue;
            final ArrayList<Node> TT = new ArrayList<>(local.get(a));
            TT.removeAll(S);
            TT.remove(b);
            TT.remove(c);

            final DepthChoiceGenerator gen2 = new DepthChoiceGenerator(TT.size(), -1);
            int[] choice2;

            while ((choice2 = gen2.next()) != null) {
                final Set<Node> T = GraphUtils.asSet(choice2, TT);
                final Set<Node> B = new HashSet<>(T);
                B.addAll(S);
                B.add(b);

                if (sepsets.isIndependent(a, c, new ArrayList<>(B))) {
                    psi.addDottedUnderlineTriple(a, b, c);
                    supSepsets.put(new Triple(a, b, c), B);
                    break;
                }
            }
        }
    }

    private void stepE(final Map<Triple, Set<Node>> supSepset, final Graph psi) {
        TetradLogger.getInstance().log("info", "\nStep E");

        for (final Triple triple : psi.getDottedUnderlines()) {
            final Node a = triple.getX();
            final Node b = triple.getY();
            final Node c = triple.getZ();

            final List<Node> aAdj = psi.getAdjacentNodes(a);

            for (final Node d : aAdj) {
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

            final List<Node> cAdj = psi.getAdjacentNodes(c);

            for (final Node d : cAdj) {
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

    private void stepF(final Graph psi, final SepsetProducer sepsets, final Map<Triple, Set<Node>> supSepsets) {
        for (final Triple triple : psi.getDottedUnderlines()) {
            final Node a = triple.getX();
            final Node b = triple.getY();
            final Node c = triple.getZ();

            final Set<Node> adj = new HashSet<>(psi.getAdjacentNodes(a));
            adj.addAll(psi.getAdjacentNodes(c));

            for (final Node d : adj) {
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

                final Set<Node> supSepUnionD = new HashSet<>();
                supSepUnionD.add(d);
                supSepUnionD.addAll(supSepsets.get(triple));
                final List<Node> listSupSepUnionD = new ArrayList<>(supSepUnionD);

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

    private List<Node> local(final Graph psi, final Node x) {
        final Set<Node> nodes = new HashSet<>(psi.getAdjacentNodes(x));

        for (final Node y : new HashSet<>(nodes)) {
            for (final Node z : psi.getAdjacentNodes(y)) {
                if (psi.isDefCollider(x, y, z)) {
                    if (z != x) {
                        nodes.add(z);
                    }
                }
            }
        }

        return new ArrayList<>(nodes);
    }

    private void orientAwayFromArrow(final Node a, final Node b, final Graph graph) {
        if (!isApplyR1()) return;

        for (final Node c : graph.getAdjacentNodes(b)) {
            if (c == a) continue;
            orientAwayFromArrowVisit(a, b, c, graph);
        }
    }

    private boolean orientAwayFromArrowVisit(final Node a, final Node b, final Node c, final Graph graph) {
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

        for (final Node d : graph.getAdjacentNodes(c)) {
            if (d == b) return true;

            final Edge bc = graph.getEdge(b, c);

            if (!orientAwayFromArrowVisit(b, c, d, graph)) {
                graph.removeEdge(b, c);
                graph.addEdge(bc);
            }
        }

        return true;
    }

    public boolean isApplyR1() {
        return this.applyR1;
    }

    public void setApplyR1(final boolean applyR1) {
        this.applyR1 = applyR1;
    }
}






