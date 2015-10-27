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

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.*;

/**
 * This class provides the datastructures and methods for carrying out the Cyclic Causal Discovery algorithm (CCD)
 * described by Thomas Richardson and Peter Spirtes in Chapter 7 of Computation, Causation, & Discovery by Glymour and
 * Cooper eds.  The comments that appear below are keyed to the algorithm specification on pp. 269-271. </p> The search
 * method returns an instance of a Graph but it also constructs two lists of node triples which represent the underlines
 * and dotted underlines that the algorithm discovers.
 *
 * @author Frank C. Wimberly
 */
public final class CcdGes implements GraphSearch {
    private IndependenceTest test;
    private int depth = -1;
    private IKnowledge knowledge;
    private List<Node> nodes;
    private double penaltyDiscount = 2.0;
    private boolean faithfulnessAssumed = true;
    private double samplePrior = 10.0;
    private double structurePrior = 0.01;

    /**
     * The logger for this class. The config needs to be set.
     */
    private TetradLogger logger = TetradLogger.getInstance();

    /**
     * Whether verbose output about independencies is output.
     */
    private boolean verbose = true;
    private int sepsetType = 1;

    /**
     * The arguments of the constructor are an oracle which answers conditional independence questions.  In the case of
     * a continuous dataset it will most likely be an instance of the IndTestCramerT class.  The second argument is not
     * used at this time.  The author (Wimberly) asked Thomas Richardson about how to use background knowledge and his
     * answer was that it should be applied after steps A-F had been executed.  Any implementation of the use of
     * background knowledge will be done later.
     *
     * @param knowledge Background knowledge. Not used yet--can be null.
     */
    public CcdGes(IndependenceTest test, IKnowledge knowledge) {
        this.knowledge = knowledge;
        this.test = test;
        this.nodes = test.getVariables();
    }

    /**
     * The arguments of the constructor are an oracle which answers conditional independence questions.  In the case of
     * a continuous dataset it will most likely be an instance of the IndTestCramerT class.  The second argument is not
     * used at this time.  The author (Wimberly) asked Thomas Richardson about how to use background knowledge and his
     * answer was that it should be applied after steps A-F had been executed.  Any implementation of the use of
     * background knowledge will be done later.
     */
    public CcdGes(IndependenceTest test) {
        this(test, new Knowledge2());
    }

    /**
     * The search method assumes that the test provided to the constructor is a conditional independence
     * oracle for the SEM (or Bayes network) which describes the causal structure of the population. The method returns
     * a PAG instantiated as a Tetrad GaSearchGraph which represents the equivalence class of digraphs which are
     * d-separation equivalent to the digraph of the underlying model (SEM or BN). </p> Although they are not returned
     * by the search method it also computes two lists of triples which, respectively store the underlines and dotted
     * underlines of the PAG.
     */
    public Graph search() {
        Map<Triple, List<Node>> supSepsets = new HashMap<>();

        //Step A
        long r1 = System.currentTimeMillis();

        TetradLogger.getInstance().log("info", "\nStep A");

        DataSet dataSet = (DataSet) test.getData();

        FastGes ges;
        Graph gesGraph;
        ICovarianceMatrix covarianceMatrix = test.getCov();
        Graph graph;

        if (dataSet == null || dataSet.isContinuous()) {
            covarianceMatrix = test.getCov();
            ges = new FastGes(covarianceMatrix);
            ges.setKnowledge(getKnowledge());
            ges.setPenaltyDiscount(getPenaltyDiscount());
            ges.setVerbose(true);
            ges.setLog(false);
            ges.setDepth(getDepth());
            ges.setNumPatternsToStore(0);
            ges.setFaithfulnessAssumed(isFaithfulnessAssumed());
            graph = ges.search();
        } else if (dataSet.isDiscrete()) {
            ges = new FastGes(dataSet);
            ges.setKnowledge(getKnowledge());
            ges.setPenaltyDiscount(getPenaltyDiscount());
            ges.setSamplePrior(samplePrior);
            ges.setStructurePrior(structurePrior);
            ges.setStructurePrior(1);
            ges.setVerbose(false);
            ges.setLog(false);
            ges.setDepth(getDepth());
            ges.setNumPatternsToStore(0);
            ges.setFaithfulnessAssumed(isFaithfulnessAssumed());
            graph = ges.search();
        } else {
            throw new IllegalArgumentException("Mixed data not supported.");
        }

        long r1a = System.currentTimeMillis();

        System.out.println("GES done " + graph.getNumEdges() + " edges in graph");

        IFas search = new Fas(test);
        search.setDepth(depth);
        search.setKnowledge(getKnowledge());
        search.setVerbose(true);
        search.setInitialGraph(graph);
        Graph psi = search.search();
        SepsetMap sepsetsFromFas = search.getSepsets();
        SepsetProducer sepsets = null;

        System.out.println("FAS done " + psi.getNumEdges() + " edges in graph");

        sepsetType = 3;

        if (sepsetType == 1) {
            sepsets = new SepsetsSet(sepsetsFromFas, test);
        } else if (sepsetType == 2) {
            sepsets = new SepsetsMinPValue(psi, test, null, depth);
        } else if (sepsetType == 3) {
            sepsets = new SepsetsMaxPValue(psi, test, null, depth);
        } else if (sepsetType == 4) {
            sepsets = new SepsetsMaxPValuePossDsep(psi, test, null, depth, -1);
        } else if (sepsetType == 5) {
            sepsets = new SepsetsPossibleDsep(psi, test, getKnowledge(), depth, -1);
        } else if (sepsetType == 6) {
            sepsets = new SepsetsConservative(psi, test, null, depth);
        } else if (sepsetType == 7) {
            sepsets = new SepsetsConservativeMajority(psi, test, null, depth);
        } else {
            throw new IllegalStateException();
        }
//
        psi.reorientAllWith(Endpoint.CIRCLE);
        SearchGraphUtils.pcOrientbk(knowledge, psi, nodes);
        stepB(psi, sepsets);
        long r2 = System.currentTimeMillis();
        stepC2(psi, sepsets);
        long r3 = System.currentTimeMillis();
        stepD(psi, sepsets, supSepsets);
        long r4 = System.currentTimeMillis();
        if (stepE(supSepsets, psi)) return psi;
        long r5 = System.currentTimeMillis();
        stepF(psi, sepsets, supSepsets);
        long r6 = System.currentTimeMillis();
        ruleR1(psi);
        long r7 = System.currentTimeMillis();

        System.out.println("GES search " + (r1a - r1));
        System.out.println("FAS search " + (r1a - r1));
        System.out.println("Step B " + (r2 - r1a));
        System.out.println("Step C " + (r3 - r2));
        System.out.println("Step D " + (r4 - r3));
        System.out.println("Step E " + (r5 - r4));
        System.out.println("Step F " + (r6 - r5));
        System.out.println("Step R1 " + (r7 - r6));

        TetradLogger.getInstance().log("graph", "\nFinal Graph:");
        TetradLogger.getInstance().log("graph", psi.toString());

        this.logger.log("graph", "\nReturning this graph: " + psi);

        return psi;
    }


    private void stepB(Graph psi, SepsetProducer sepsets) {
        //Step B
        List<Node> nodes1 = test.getVariables();

        for (Node y : nodes1) {
            List<Node> adjacentNodes = psi.getAdjacentNodes(y);

            if (adjacentNodes.size() < 2) {
                continue;
            }

            ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null) {
                Node x = adjacentNodes.get(combination[0]);
                Node z = adjacentNodes.get(combination[1]);

                if (psi.isAdjacentTo(x, z)) {
                    continue;
                }

                if (sepsets.isCollider(x, y, z)) {
                    if (isArrowpointAllowed(x, y) && isArrowpointAllowed(z, y)) {
                        psi.removeEdge(x, y);
                        psi.removeEdge(y, z);
                        psi.addDirectedEdge(x, y);
                        psi.addDirectedEdge(z, y);
                    }
                } else if (sepsets.isNoncollider(x, y, z)) {
                    psi.addUnderlineTriple(x, y, z);
                }
            }
        }
    }


//    private void stepC(Graph psi, SepsetProducer sepsets) {
//        //Step C
//        TetradLogger.getInstance().log("info", "\nStep C");
//
//        EDGE:
//        for (Edge edge : psi.getEdges()) {
//            Node x = edge.getNode1();
//            Node y = edge.getNode2();
//
//            for (Node node : psi.getAdjacentNodes(x)) {
//                if (psi.getEdge(node, x).getProximalEndpoint(x) == Endpoint.ARROW && psi.isUnderlineTriple(y, x, node)) {
//                    continue EDGE;
//                }
//            }
//
//            // Check each A
//            for (Node a : nodes) {
//                if (a == x || a == y) {
//                    continue;  //distinctness
//                }
//
//                // Orientable...
//                if (psi.getEndpoint(y, x) != Endpoint.CIRCLE) {
//                    continue;
//                }
//
//                //...A is not adjacent to X and A is not adjacent to Y...
//                if (psi.isAdjacentTo(x, a) || psi.isAdjacentTo(y, a)) {
//                    continue;
//                }
////
//                //...X is not in sepset<A, Y>...
//                List<Node> sepset = sepsets.getSepset(a, y);
//                if (sepset == null) continue;
//
//                if (sepset.contains(x)) {
//                    continue;
//                }
//
//                if (!sepsets.isIndependent(a, x, sepset)) {
////                    System.out.println("C. New orienting " + psi.getEdge(x, y) + " as " + y + " --> " + x);
//                    psi.removeEdge(x, y);
//                    psi.addDirectedEdge(y, x);
//                    break;
//                }
//            }
//        }
//    }


    private void stepC2(Graph psi, SepsetProducer sepsets) {

        //Step C
        TetradLogger.getInstance().log("info", "\nStep C");

        EDGE:
        for (Edge edge : psi.getEdges()) {
            Node x = edge.getNode1();
            Node y = edge.getNode2();

            List<Node> adjx = psi.getAdjacentNodes(x);
            List<Node> adjy = psi.getAdjacentNodes(y);

            for (Node node : adjx) {
                if (psi.getEdge(node, x).getProximalEndpoint(x) == Endpoint.ARROW && psi.isUnderlineTriple(y, x, node)) {
                    continue EDGE;
                }
            }

            int count = 0;

            // Check each A
            for (Node a : nodes) {
                if (a == x || a == y) {
                    continue;  //distinctness
                }


                // Orientable...
                if (psi.getEndpoint(y, x) != Endpoint.CIRCLE) {
                    continue;
                }

                //...A is not adjacent to X and A is not adjacent to Y...
                if (adjx.contains(a)) continue;
                if (adjy.contains(a)) continue;

                //...X is not in sepset<A, Y>...
                List<Node> sepset = sepsets.getSepset(a, y);
                if (sepset == null) continue;

                if (sepset.contains(x)) {
                    continue;
                }

                if (!sepsets.isIndependent(a, x, sepset)) {
                    count++;
                    System.out.println("count = " + count);
                }

//                if (count > 4) break;
            }

            if (count >= 3) {
                System.out.println("C. Orienting " + psi.getEdge(x, y) + " as " + y + " --> " + x);
                psi.removeEdge(x, y);
                psi.addDirectedEdge(y, x);
            }
        }
    }

    private void stepD(Graph psi, SepsetProducer sepsets, Map<Triple, List<Node>> supSepsets) {
        Map<Node, List<Node>> local = new HashMap<>();

        for (Node node : psi.getNodes()) {
            local.put(node, local(psi, node));
        }

        //Step D
        TetradLogger.getInstance().log("info", "\nStep D");

        for (Node b : nodes) {
            List<Node> adj = psi.getAdjacentNodes(b);

            if (adj.size() < 2) continue;

            ChoiceGenerator gen1 = new ChoiceGenerator(adj.size(), 2);
            int[] choice1;

            while ((choice1 = gen1.next()) != null) {
                Node a = adj.get(choice1[0]);
                Node c = adj.get(choice1[1]);

                if (psi.isAdjacentTo(a, c)) {
                    continue;
                }

                List<List<List<Node>>> ret = getSepsetsLists(a, b, c, sepsets, depth, local);
                List<List<Node>> sepsetsContainingY = ret.get(0);
                List<List<Node>> sepsetsNotContainingY = ret.get(1);

                if (psi.isUnderlineTriple(a, b, c)) continue;

                if (sepsetsContainingY.isEmpty() || sepsetsNotContainingY.isEmpty()) {
                    continue;
                }

                if (psi.getEdge(a, b).pointsTowards(a) || psi.getEdge(b, c).pointsTowards(c)) {
                    continue;
                }

                psi.removeEdge(a, b);
                psi.removeEdge(b, c);
                psi.addDirectedEdge(a, b);
                psi.addDirectedEdge(c, b);
                psi.addDottedUnderlineTriple(a, b, c);

                supSepsets.put(new Triple(a, b, c), sepsetsContainingY.get(0));
            }
        }
    }


    private boolean stepE(Map<Triple, List<Node>> supSepset, Graph psi) {
        //Step E
        TetradLogger.getInstance().log("info", "\nStep E");

        //Steps E and F require at least 4 vertices
        if (nodes.size() < 4) {
            return true;
        }

        for (Triple triple : psi.getDottedUnderlines()) {
            Node a = triple.getX();
            Node b = triple.getY();

            List<Node> aAdj = psi.getAdjacentNodes(a);

            for (Node d : aAdj) {
                if (d == b) continue;

                if (supSepset.get(triple).contains(d)) {
                    if (psi.getEndpoint(b, d) != Endpoint.CIRCLE) {
                        continue;
                    }

                    // Orient B*-oD as B*-D
                    psi.setEndpoint(b, d, Endpoint.TAIL);
                } else {
                    if (psi.getEndpoint(d, b) == Endpoint.ARROW) {
                        continue;
                    }

                    if (psi.getEndpoint(b, d) != Endpoint.CIRCLE) {
                        continue;
                    }

                    // Or orient Bo-oD or B-oD as B->D...
                    psi.removeEdge(b, d);
                    psi.addDirectedEdge(b, d);
                }

            }

            for (Node d : aAdj) {
                if (d == b) continue;

                if (supSepset.get(triple).contains(d)) {
                    if (psi.getEndpoint(b, d) != Endpoint.CIRCLE) {
                        continue;
                    }

                    // Orient B*-oD as B*-D
                    psi.setEndpoint(b, d, Endpoint.TAIL);
                } else {
                    if (psi.getEndpoint(d, b) == Endpoint.ARROW) {
                        continue;
                    }

                    if (psi.getEndpoint(b, d) != Endpoint.CIRCLE) {
                        continue;
                    }

                    // Or orient Bo-oD or B-oD as B->D...
                    psi.removeEdge(b, d);
                    psi.addDirectedEdge(b, d);
                }
            }
        }

        return false;
    }


    private void stepF(Graph psi, SepsetProducer sepsets, Map<Triple, List<Node>> supSepsets) {

        //Step F
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
                }
            }
        }
    }

    private void ruleR1(Graph psi) {
        boolean changed;

        do {
            changed = rulesR1cycle(psi);
        } while (changed);
    }

    private List<Node> local(Graph psi, Node z) {
        List<Node> local = new ArrayList<>();

        //Is X p-adjacent to V in psi?
        for (Node x : nodes) {
            if (x == z) {
                continue;
            }

            if (psi.isAdjacentTo(z, x)) {
                local.add(x);
            }

            //or is there a collider between X and V in psi?
            for (Node y : nodes) {
                if (y == z || y == x) {
                    continue;
                }

                if (psi.isDefCollider(x, y, z)) {
                    if (!local.contains(x)) {
                        local.add(x);
                    }
                }
            }
        }

        return local;
    }

    private void orientCollider(Graph psi, Node a, Node b, Node c) {
        psi.removeEdge(a, b);
        psi.removeEdge(b, c);
        psi.addDirectedEdge(a, b);
        psi.addDirectedEdge(c, b);
    }

    private boolean rulesR1cycle(Graph graph) {
        List<Node> nodes = graph.getNodes();
        boolean changed = false;

        for (Node B : nodes) {
            List<Node> adj = graph.getAdjacentNodes(B);

            if (adj.size() < 2) {
                continue;
            }

            ChoiceGenerator cg = new ChoiceGenerator(adj.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null) {
                Node A = adj.get(combination[0]);
                Node C = adj.get(combination[1]);

                //choice gen doesn't do diff orders, so must switch A & C around.
                changed = changed || ruleR1(A, B, C, graph);
                changed = changed || ruleR1(C, B, A, graph);
            }
        }

        return changed;
    }

    private boolean ruleR1(Node a, Node b, Node c, Graph graph) {
        if (graph.isAdjacentTo(a, c)) {
            return false;
        }

        if (graph.getEndpoint(a, b) == Endpoint.ARROW && graph.getEndpoint(c, b) == Endpoint.CIRCLE) {
            if (!graph.isUnderlineTriple(a, b, c)) {
                return false;
            }

            graph.setEndpoint(c, b, Endpoint.TAIL);
            graph.setEndpoint(b, c, Endpoint.ARROW);

            return true;
        }

        return false;
    }

    public long getElapsedTime() {
        return 0;
    }

    public List<Triple> orientCollidersUsingSepsets(SepsetMap set, Graph psi, boolean verbose) {
        TetradLogger.getInstance().log("details", "Starting Collider Orientation:");
        List<Triple> colliders = new ArrayList<Triple>();

        List<Node> nodes = psi.getNodes();

        for (Node b : nodes) {
            List<Node> adjacentNodes = psi.getAdjacentNodes(b);

            if (adjacentNodes.size() < 2) {
                continue;
            }

            ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null) {
                Node a = adjacentNodes.get(combination[0]);
                Node c = adjacentNodes.get(combination[1]);

                // Skip triples that are shielded.
                if (psi.isAdjacentTo(a, c)) {
                    continue;
                }

                List<Node> sepset = set.get(a, c);

                if (!sepset.contains(b)) {
                    orientCollider(psi, a, b, c);
                    TetradLogger.getInstance().log("colliderOrientations", "A. Orienting collider " + a + "-->" +
                            b + "<--" + c);
                } else {
                    psi.addUnderlineTriple(a, b, c);
                    TetradLogger.getInstance().log("colliderOrientations", "A. Orienting underline " + a + "---" +
                            b + "---" + c);
                    TetradLogger.getInstance().log("underlines", "Adding underline " + new Triple(a, b, c));
                }
            }
        }

        TetradLogger.getInstance().log("details", "Finishing Collider Orientation.");

        return colliders;
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

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public List<List<List<Node>>> getSepsetsLists(Node x, Node y, Node z,
                                                  SepsetProducer sepsets, int depth,
                                                  Map<Node, List<Node>> local) {
        List<List<Node>> sepsetsContainingY = new ArrayList<List<Node>>();
        List<List<Node>> sepsetsNotContainingY = new ArrayList<List<Node>>();

        List<Node> _nodes = local.get(x);

        _nodes.remove(z);
        TetradLogger.getInstance().log("adjacencies", "Adjacents for " + x + "--" + y + "--" + z + " = " + _nodes);

        int _depth = depth;
        if (_depth == -1) {
            _depth = 1000;
        }
        _depth = Math.min(_depth, _nodes.size());

        while (true) {
            for (int d = 0; d <= _depth; d++) {
                ChoiceGenerator cg = new ChoiceGenerator(_nodes.size(), d);
                int[] choice;

                while ((choice = cg.next()) != null) {
                    List<Node> cond = GraphUtils.asList(choice, _nodes);

                    if (sepsets.isIndependent(x, z, cond)) {
                        if (cond.contains(y)) {
                            sepsetsContainingY.add(cond);
                        } else {
                            sepsetsNotContainingY.add(cond);
                        }
                    }
                }
            }

            _nodes = local.get(z);
            _nodes.remove(x);
            TetradLogger.getInstance().log("adjacencies", "Adjacents for " + x + "--" + y + "--" + z + " = " + _nodes);

            _depth = depth;
            if (_depth == -1) {
                _depth = 1000;
            }
            _depth = Math.min(_depth, _nodes.size());

            for (int d = 0; d <= _depth; d++) {
                ChoiceGenerator cg = new ChoiceGenerator(_nodes.size(), d);
                int[] choice;

                while ((choice = cg.next()) != null) {
                    List<Node> cond = GraphUtils.asList(choice, _nodes);

                    if (sepsets.isIndependent(x, z, cond)) {
                        if (cond.contains(y)) {
                            sepsetsContainingY.add(cond);
                        } else {
                            sepsetsNotContainingY.add(cond);
                        }
                    }
                }
            }

            break;
        }

        List<List<List<Node>>> ret = new ArrayList<>();
        ret.add(sepsetsContainingY);
        ret.add(sepsetsNotContainingY);

        return ret;
    }

    public void setSepsetType(int sepsetType) {
        this.sepsetType = sepsetType;
    }

    private boolean isArrowpointAllowed(Node from, Node to) {
        return !getKnowledge().isRequired(to.toString(), from.toString()) &&
                !getKnowledge().isForbidden(from.toString(), to.toString());
    }

    public void setKnowledge(IKnowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException();
        }
        this.knowledge = knowledge;
    }

    public double getPenaltyDiscount() {
        return penaltyDiscount;
    }

    public void setPenaltyDiscount(double penaltyDiscount) {
        this.penaltyDiscount = penaltyDiscount;
    }

    public boolean isFaithfulnessAssumed() {
        return faithfulnessAssumed;
    }

    public void setFaithfulnessAssumed(boolean faithfulnessAssumed) {
        this.faithfulnessAssumed = faithfulnessAssumed;
    }
}






