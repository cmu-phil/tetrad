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
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.data.KnowledgeEdge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.DepthChoiceGenerator;
import edu.cmu.tetrad.util.ForkJoinPoolInstance;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RecursiveTask;


/**
 * Extends Erin Korber's implementation of the Fast Causal Inference algorithm (found in FCI.java) with Jiji Zhang's
 * Augmented FCI rules (found in sec. 4.1 of Zhang's 2006 PhD dissertation, "Causal Inference and Reasoning in Causally
 * Insufficient Systems").
 * <p>
 * This class is based off a copy of FCI.java taken from the repository on 2008/12/16, revision 7306. The extension is
 * done by extending doFinalOrientation() with methods for Zhang's rules R5-R10 which implements the augmented search.
 * (By a remark of Zhang's, the rule applications can be staged in this way.)
 *
 * @author Joseph Ramsey
 * @author Vineet Rhagu
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
    private List<Node> variables = new ArrayList<>();

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

    /**
     * An initial graph if there is one.
     */
    private Graph initialGraph;

    /**
     * Max path length for the possible dsep search.
     */
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
        FasConcurrent fas = new FasConcurrent(initialGraph, getIndependenceTest());
        fas.setVerbose(verbose);
        return search(fas);
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
        this.graph = fas.search();
        this.sepsets = fas.getSepsets();

        graph.reorientAllWith(Endpoint.CIRCLE);

        SepsetProducer sp = new SepsetsMaxPValuePossDsep(graph, independenceTest, null, depth, maxPathLength);

        // The original FCI, with or without JiJi Zhang's orientation rules
        if (isPossibleDsepSearchDone()) {
            addColliders(graph);

            for (Edge edge : new ArrayList<>(graph.getEdges())) {
                Node x = edge.getNode1();
                Node y = edge.getNode2();

                List<Node> sepset = sp.getSepset(x, y);

                if (sepset != null) {
                    graph.removeEdge(x, y);
                    sepsets.set(x, y, sepset);
                }
            }

            // Reorient all edges as o-o.
            graph.reorientAllWith(Endpoint.CIRCLE);
        }

        addColliders(graph);

        // Step CI C (Zhang's step F3.)
        long time5 = System.currentTimeMillis();

        long time6 = System.currentTimeMillis();
        logger.log("info", "Step CI C: " + (time6 - time5) / 1000. + "s");

        final FciOrient fciOrient = new FciOrient(new SepsetsSet(this.sepsets, independenceTest));

        fciOrient.setCompleteRuleSetUsed(completeRuleSetUsed);
        fciOrient.setMaxPathLength(maxPathLength);
        fciOrient.setKnowledge(knowledge);
        fciOrient.doFinalOrientation(graph);
        graph.setPag(true);

        return graph;
    }

    private void addColliders(Graph graph) {
        final Map<Triple, Double> scores = new ConcurrentHashMap<>();

        List<Node> nodes = graph.getNodes();

        class Task extends RecursiveTask<Boolean> {
            int from;
            int to;
            int chunk = 20;
            List<Node> nodes;
            Graph graph;

            public Task(List<Node> nodes, Graph graph, Map<Triple, Double> scores, int from, int to) {
                this.nodes = nodes;
                this.graph = graph;
                this.from = from;
                this.to = to;
            }

            @Override
            protected Boolean compute() {
                if (to - from <= chunk) {
                    for (int i = from; i < to; i++) {
                        doNode(graph, scores, nodes.get(i));
                    }

                    return true;
                } else {
                    int mid = (to + from) / 2;

                    Task left = new Task(nodes, graph, scores, from, mid);
                    Task right = new Task(nodes, graph, scores, mid, to);

                    left.fork();
                    right.compute();
                    left.join();

                    return true;
                }
            }
        }

        Task task = new Task(nodes, graph, scores, 0, nodes.size());

        ForkJoinPoolInstance.getInstance().getPool().invoke(task);

        List<Triple> tripleList = new ArrayList<>(scores.keySet());

        // Most independent ones first.
        Collections.sort(tripleList, new Comparator<Triple>() {

            @Override
            public int compare(Triple o1, Triple o2) {
                return Double.compare(scores.get(o2), scores.get(o1));
            }
        });

        for (Triple triple : tripleList) {
            Node a = triple.getX();
            Node b = triple.getY();
            Node c = triple.getZ();

            graph.setEndpoint(a, b, Endpoint.ARROW);
            graph.setEndpoint(c, b, Endpoint.ARROW);
        }
    }

    private void doNode(Graph graph, Map<Triple, Double> scores, Node b) {
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

            // S actually has to be non-null here, but the compiler doesn't know that.
            if (S != null && !S.contains(b)) {
                scores.put(new Triple(a, b, c), score);
            }
        }
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

    //===========================PRIVATE METHODS=========================//

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
}




