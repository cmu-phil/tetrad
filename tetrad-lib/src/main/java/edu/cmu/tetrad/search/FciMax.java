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

import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.data.KnowledgeEdge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;


/**
 * Extends Erin Korber's implementation of the Fast Causal Inference algorithm (found in Fci.java) with Jiji Zhang's
 * Augmented FCI rules (found in sec. 4.1 of Zhang's 2006 PhD dissertation, "Causal Inference and Reasoning in Causally
 * Insufficient Systems").
 * <p>
 * This class is based off a copy of Fci.java taken from the repository on 2008/12/16, revision 7306. The extension is
 * done by extending doFinalOrientation() with methods for Zhang's rules R5-R10 which implements the augmented search.
 * (By a remark of Zhang's, the rule applications can be staged in this way.)
 *
 * @author Erin Korber, June 2004
 * @author Alex Smith, December 2008
 * @author Joseph Ramsey
 * @author Choh-Man Teng
 */
public final class FciMax implements GraphSearch {

    /**
     * The PAG being constructed.
     */
    private Graph graph;

    /**
     * The SepsetMap being constructed.
     */
    private SepsetMap sepsets;

    /**
     * The background knowledge.
     */
    private IKnowledge knowledge = new Knowledge2();

    /**
     * The variables to search over (optional)
     */
    private List<Node> variables = new ArrayList<Node>();

    private IndependenceTest independenceTest;

    /**
     * flag for complete rule set, true if should use complete rule set, false otherwise.
     */
    private boolean completeRuleSetUsed = false;

    /**
     * True iff the possible dsep search is done.
     */
    private boolean possibleDsepSearchDone = true;

    /**
     * The maximum length for any discriminating path. -1 if unlimited; otherwise, a positive integer.
     */
    private int maxPathLength = -1;

    /**
     * The depth for the fast adjacency search.
     */
    private int depth = -1;

    /**
     * Elapsed time of last search.
     */
    private long elapsedTime;

    /**
     * The logger to use.
     */
    private TetradLogger logger = TetradLogger.getInstance();

    /**
     * True iff verbose output should be printed.
     */
    private boolean verbose = false;
    private Graph trueDag;
    private ConcurrentMap<Node, Integer> hashIndices;
    private ICovarianceMatrix covarianceMatrix;
    private double penaltyDiscount = 2;
    private SepsetMap possibleDsepSepsets = new SepsetMap();
    private Graph initialGraph;
    private int possibleDsepDepth = -1;


    //============================CONSTRUCTORS============================//

    /**
     * Constructs a new FCI search for the given independence test and background knowledge.
     */
    public FciMax(IndependenceTest independenceTest) {
        if (independenceTest == null || knowledge == null) {
            throw new NullPointerException();
        }

        this.independenceTest = independenceTest;
        this.variables.addAll(independenceTest.getVariables());
        buildIndexing(independenceTest.getVariables());
    }

    /**
     * Constructs a new FCI search for the given independence test and background knowledge and a list of variables to
     * search over.
     */
    public FciMax(IndependenceTest independenceTest, List<Node> searchVars) {
        if (independenceTest == null || knowledge == null) {
            throw new NullPointerException();
        }

        this.independenceTest = independenceTest;
        this.variables.addAll(independenceTest.getVariables());

        Set<Node> remVars = new HashSet<Node>();
        for (Node node1 : this.variables) {
            boolean search = false;
            for (Node node2 : searchVars) {
                if (node1.getName().equals(node2.getName())) {
                    search = true;
                }
            }
            if (!search) {
                remVars.add(node1);
            }
        }
        this.variables.removeAll(remVars);
    }

    //========================PUBLIC METHODS==========================//

    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
        if (depth < -1) {
            throw new IllegalArgumentException(
                    "Depth must be -1 (unlimited) or >= 0: " + depth);
        }

        this.depth = depth;
    }

    public long getElapsedTime() {
        return this.elapsedTime;
    }

    public Graph search() {
        return search(getIndependenceTest().getVariables());
    }

    public Graph search(List<Node> nodes) {
        FasStableConcurrent fas = new FasStableConcurrent(getIndependenceTest());
        fas.setVerbose(verbose);
        return search(fas);
//        return search(new Fas(getIndependenceTest()));
    }

    public void setInitialGraph(Graph initialGraph) {
        this.initialGraph = initialGraph;
    }

    public Graph search(IFas fas) {
        logger.log("info", "Starting FCI algorithm.");
        logger.log("info", "Independence test = " + getIndependenceTest() + ".");
        fas.setVerbose(verbose);

        fas.setKnowledge(getKnowledge());
        fas.setDepth(depth);
        fas.setVerbose(verbose);
        fas.setInitialGraph(initialGraph);
        this.graph = fas.search();
        this.sepsets = fas.getSepsets();

        graph.reorientAllWith(Endpoint.CIRCLE);

//        SepsetProducer sp = new SepsetsPossibleDsep(graph, independenceTest, knowledge, depth, maxPathLength);
        SepsetProducer sp = new SepsetsMaxPValuePossDsep(graph, independenceTest, null, depth, maxPathLength);
        SepsetProducer sp2 = new SepsetsMaxPValue(graph, independenceTest, null, depth);

        // The original FCI, with or without JiJi Zhang's orientation rules
        //        // Optional step: Possible Dsep. (Needed for correctness but very time consuming.)
        if (isPossibleDsepSearchDone()) {
//            long time1 = System.currentTimeMillis();
//            new FciOrient(new SepsetsSet(this.sepsets, independenceTest)).ruleR0(graph);
//            SepsetProducer sepsetProducer = new SepsetsSet(sepsets, independenceTest);

            addColliders(graph, sp2, knowledge);

            System.out.println("Possible dsep add colliders done");

            for (Edge edge : new ArrayList<>(graph.getEdges())) {
                Node x = edge.getNode1();
                Node y = edge.getNode2();

                List<Node> sepset = sp.getSepset(x, y);

                if (sepset != null) {
                    graph.removeEdge(x, y);
                    sepsets.set(x, y, sepset);
                    System.out.println("Possible DSEP Removed " + x + "--- " + y + " sepset = " + sepset);
                }
            }

            System.out.println("Possible dsep done");


//            long time2 = System.currentTimeMillis();
//            logger.log("info", "Step C: " + (time2 - time1) / 1000. + "s");
//
//            // Step FCI D.
//            long time3 = System.currentTimeMillis();
//
//            System.out.println("Starting possible dsep search");
//            PossibleDsepFci possibleDSep = new PossibleDsepFci(graph, independenceTest);
//            possibleDSep.setDepth(getPossibleDsepDepth());
//            possibleDSep.setKnowledge(getKnowledge());
//            possibleDSep.setMaxPathLength(maxPathLength);
//            this.sepsets.addAll(possibleDSep.search());
//            long time4 = System.currentTimeMillis();
//            logger.log("info", "Step D: " + (time4 - time3) / 1000. + "s");
//            System.out.println("Starting possible dsep search");

            // Reorient all edges as o-o.
            graph.reorientAllWith(Endpoint.CIRCLE);

            System.out.println("Reoriented with circles");
        }

        addColliders(graph, sp2, knowledge);

        System.out.println("Added colliders again");

        // Step CI C (Zhang's step F3.)
        long time5 = System.currentTimeMillis();

        long time6 = System.currentTimeMillis();
        logger.log("info", "Step CI C: " + (time6 - time5) / 1000. + "s");

        final FciOrient fciOrient = new FciOrient(new SepsetsSet(this.sepsets, independenceTest));

        fciOrient.setCompleteRuleSetUsed(completeRuleSetUsed);
        fciOrient.setMaxPathLength(maxPathLength);
        fciOrient.setKnowledge(knowledge);
        fciOrient.doFinalOrientation(graph);

        System.out.println("Final orientation done");

        return graph;
    }

    /**
     * Step C of PC; orients colliders using specified sepset. That is, orients x *-* y *-* z as x *-> y <-* z just in
     * case y is in Sepset({x, z}).
     */
    public Map<Triple, Double> findCollidersUsingSepsets(SepsetProducer sepsetProducer, Graph graph, boolean verbose, IKnowledge knowledge) {
        TetradLogger.getInstance().log("details", "Starting Collider Orientation:");
        Map<Triple, Double> colliders = new HashMap<>();

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

                List<Node> sepset = sepsetProducer.getSepset(a, c);

                if (sepset == null) continue;

//
                if (sepsetProducer.getPValue() < getIndependenceTest().getAlpha()) continue;

                if (!sepset.contains(b)) {
                    System.out.println("sepset = " + sepset + " b = " + b + " p = " + sepsetProducer.getPValue());

                    if (verbose) {
                        System.out.println("Collider orientation <" + a + ", " + b + ", " + c + "> sepset = " + sepset);
                    }

                    IndependenceTest test2 = new IndTestDSep(trueDag);
                    SepsetProducer sp2 = new SepsetsMaxPValue(graph, test2, null, depth);

                    System.out.println("Dsep sepset = " + sp2.getSepset(a, c));

                    colliders.put(new Triple(a, b, c), sepsetProducer.getPValue());

//                    colliders.add(new Triple(a, b, c));
                    TetradLogger.getInstance().log("colliderOrientations", SearchLogUtils.colliderOrientedMsg(a, b, c, sepset));
                }
            }
        }

        TetradLogger.getInstance().log("details", "Finishing Collider Orientation.");

        System.out.println("Done finding colliders");

        return colliders;
    }

    private void addColliders(Graph graph, final SepsetProducer sepsetProducer, IKnowledge knowledge) {
        final Map<Triple, Double> collidersPs = findCollidersUsingSepsets(sepsetProducer, graph, verbose, knowledge);

        final List<Triple> colliders = new ArrayList<>(collidersPs.keySet());

        for (Triple collider : colliders) {
            if (collidersPs.get(collider) < getIndependenceTest().getAlpha()) continue;

            Node a = collider.getX();
            Node b = collider.getY();
            Node c = collider.getZ();

//            if (!(isArrowpointAllowed(a, b, knowledge) && isArrowpointAllowed(c, b, knowledge))) {
//                continue;
//            }

//            if (!graph.getEdge(a, b).pointsTowards(a) && !graph.getEdge(b, c).pointsTowards(c)) {
//                graph.removeEdge(a, b);
//                graph.removeEdge(c, b);
//                graph.addDirectedEdge(a, b);
//                graph.addDirectedEdge(c, b);
//            }

            graph.setEndpoint(a, b, Endpoint.ARROW);
            graph.setEndpoint(c, b, Endpoint.ARROW);
        }
    }

    private static List<Node> union(List<Node> nodes, Node a) {
        List<Node> union = new ArrayList<>(nodes);
        union.add(a);
        return union;
    }


    public SepsetMap getSepsets() {
        return this.sepsets;
    }

    public IKnowledge getKnowledge() {
        return knowledge;
    }

    public void setKnowledge(IKnowledge knowledge) {
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
        return completeRuleSetUsed;
    }

    /**
     * @param completeRuleSetUsed set to true if Zhang's complete rule set should be used, false if only R1-R4 (the rule
     *                            set of the original FCI) should be used. False by default.
     */
    public void setCompleteRuleSetUsed(boolean completeRuleSetUsed) {
        this.completeRuleSetUsed = completeRuleSetUsed;
    }

    public boolean isPossibleDsepSearchDone() {
        return possibleDsepSearchDone;
    }

    public void setPossibleDsepSearchDone(boolean possibleDsepSearchDone) {
        this.possibleDsepSearchDone = possibleDsepSearchDone;
    }

    /**
     * @return the maximum length of any discriminating path, or -1 of unlimited.
     */
    public int getMaxPathLength() {
        return maxPathLength == Integer.MAX_VALUE ? -1 : maxPathLength;
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
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * The independence test.
     */
    public IndependenceTest getIndependenceTest() {
        return independenceTest;
    }

    public void setTrueDag(Graph trueDag) {
        this.trueDag = trueDag;
    }

    public double getPenaltyDiscount() {
        return penaltyDiscount;
    }

    public void setPenaltyDiscount(double penaltyDiscount) {
        this.penaltyDiscount = penaltyDiscount;
    }

    //===========================PRIVATE METHODS=========================//

    private void buildIndexing(List<Node> nodes) {
        this.hashIndices = new ConcurrentHashMap<Node, Integer>();
        for (Node node : nodes) {
            this.hashIndices.put(node, variables.indexOf(node));
        }
    }

    /**
     * Orients according to background knowledge
     */
    private void fciOrientbk(IKnowledge bk, Graph graph, List<Node> variables) {
        logger.log("info", "Starting BK Orientation.");

        for (Iterator<KnowledgeEdge> it =
             bk.forbiddenEdgesIterator(); it.hasNext(); ) {
            KnowledgeEdge edge = it.next();

            //match strings to variables in the graph.
            Node from = SearchGraphUtils.translate(edge.getFrom(), variables);
            Node to = SearchGraphUtils.translate(edge.getTo(), variables);


            if (from == null || to == null) {
                continue;
            }

            if (graph.getEdge(from, to) == null) {
                continue;
            }

            // Orient to*->from
            graph.setEndpoint(to, from, Endpoint.ARROW);
            graph.setEndpoint(from, to, Endpoint.CIRCLE);
            logger.log("knowledgeOrientation", SearchLogUtils.edgeOrientedMsg("Knowledge", graph.getEdge(from, to)));
        }

        for (Iterator<KnowledgeEdge> it =
             bk.requiredEdgesIterator(); it.hasNext(); ) {
            KnowledgeEdge edge = it.next();

            //match strings to variables in this graph
            Node from = SearchGraphUtils.translate(edge.getFrom(), variables);
            Node to = SearchGraphUtils.translate(edge.getTo(), variables);

            if (from == null || to == null) {
                continue;
            }

            if (graph.getEdge(from, to) == null) {
                continue;
            }

            graph.setEndpoint(to, from, Endpoint.TAIL);
            graph.setEndpoint(from, to, Endpoint.ARROW);
            logger.log("knowledgeOrientation", SearchLogUtils.edgeOrientedMsg("Knowledge", graph.getEdge(from, to)));
        }

        logger.log("info", "Finishing BK Orientation.");
    }

    public int getPossibleDsepDepth() {
        return possibleDsepDepth;
    }

    public void setPossibleDsepDepth(int possibleDsepDepth) {
        this.possibleDsepDepth = possibleDsepDepth;
    }
}




