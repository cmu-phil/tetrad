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
    private IndependenceTest independenceTest;
    private int depth = -1;
    private IKnowledge knowledge;
    private List<Node> nodes;
    private boolean applyR1 = false;

    public Ccd(IndependenceTest test) {
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
        Map<Triple, Set<Node>> supSepsets = new HashMap<>();

        // Step A.
        Fas fas = new Fas(independenceTest);
        Graph psi = fas.search();
        psi.reorientAllWith(Endpoint.CIRCLE);

        SepsetProducer sepsets = new SepsetsSet(fas.getSepsets(), independenceTest);

        stepB(psi);
        stepC(psi, sepsets);
        stepD(psi, sepsets, supSepsets);
        stepE(supSepsets, psi);
        stepF(psi, sepsets, supSepsets);

        orientAwayFromArrow(psi);

        return psi;
    }

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

    public IKnowledge getKnowledge() {
        return knowledge;
    }

    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }

    public void setKnowledge(IKnowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException();
        }
        this.knowledge = knowledge;
    }

    public long getElapsedTime() {
        return 0;
    }

    //======================================== PRIVATE METHODS ====================================//

    private void stepB(Graph graph) {
        final Map<Triple, Double> colliders = new HashMap<>();
        final Map<Triple, Double> noncolliders = new HashMap<>();

        for (Node node : nodes) {
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

            graph.addUnderlineTriple(a, b, c);
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

            DepthChoiceGenerator cg2 = new DepthChoiceGenerator(adja.size(), -1);
            int[] comb2;

            while ((comb2 = cg2.next()) != null) {
                List<Node> s = GraphUtils.asList(comb2, adja);
                independenceTest.isIndependent(a, c, s);
                double _score = independenceTest.getScore();

                if (_score < score) {
                    score = _score;
                    S = s;
                }
            }

            List<Node> adjc = graph.getAdjacentNodes(c);

            DepthChoiceGenerator cg3 = new DepthChoiceGenerator(adjc.size(), -1);
            int[] comb3;

            while ((comb3 = cg3.next()) != null) {
                List<Node> s = GraphUtils.asList(comb3, adjc);
                independenceTest.isIndependent(c, a, s);
                double _score = independenceTest.getScore();

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
                        && psi.isUnderlineTriple(y, x, node)) {
                    continue EDGE;
                }
            }

            // Check each A
            for (Node a : nodes) {
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

    private void stepD(Graph psi, SepsetProducer sepsets, final Map<Triple, Set<Node>> supSepsets) {
        Map<Node, List<Node>> local = new HashMap<>();

        for (Node node : psi.getNodes()) {
            local.put(node, local(psi, node));
        }

        for (Node node : nodes) {
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

            DepthChoiceGenerator gen2 = new DepthChoiceGenerator(TT.size(), -1);
            int[] choice2;

            while ((choice2 = gen2.next()) != null) {
                Set<Node> T = GraphUtils.asSet(choice2, TT);
                Set<Node> B = new HashSet<>(T);
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

    private void stepE(Map<Triple, Set<Node>> supSepset, Graph psi) {
        TetradLogger.getInstance().log("info", "\nStep E");

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

    private void stepF(Graph psi, SepsetProducer sepsets, Map<Triple, Set<Node>> supSepsets) {
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

    public boolean isApplyR1() {
        return applyR1;
    }

    public void setApplyR1(boolean applyR1) {
        this.applyR1 = applyR1;
    }
}






