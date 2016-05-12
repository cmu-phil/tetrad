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
import edu.cmu.tetrad.util.TetradLogger;

import java.util.*;


/**
 * Extends Erin Korber's implementation of the Fast Causal Inference algorithm (found in FCI.java) with Jiji Zhang's
 * Augmented FCI rules (found in sec. 4.1 of Zhang's 2006 PhD dissertation, "Causal Inference and Reasoning in Causally
 * Insufficient Systems").
 * <p>
 * This class is based off a copy of FCI.java taken from the repository on 2008/12/16, revision 7306. The extension is
 * done by extending doFinalOrientation() with methods for Zhang's rules R5-R10 which implements the augmented search.
 * (By a remark of Zhang's, the rule applications can be staged in this way.)
 *
 * @author Erin Korber, June 2004
 * @author Alex Smith, December 2008
 */
public final class Cfci implements  GraphSearch {

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

    /**
     * The independence test.
     */
    private IndependenceTest independenceTest;

    /**
     * flag for complete rule set, true if should use complete rule set, false otherwise.
     */
    private boolean completeRuleSetUsed = true;

    /**
     * True iff the possible dsep search is done.
     */
    private boolean possibleDsepSearchDone = true;

    /**
     * The maximum length for any discriminating path. -1 if unlimited; otherwise, a positive integer.
     */
    private int maxReachablePathLength = -1;

    /**
     * Set of unshielded colliders from the triple orientation step.
     */
    private Set<Triple> colliderTriples;

    /**
     * Set of unshielded noncolliders from the triple orientation step.
     */
    private Set<Triple> noncolliderTriples;

    /**
     * Set of ambiguous unshielded triples.
     */
    private Set<Triple> ambiguousTriples;

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

    private boolean verbose = false;

    //============================CONSTRUCTORS============================//

    /**
     * Constructs a new FCI search for the given independence test and background knowledge.
     */
    public Cfci(IndependenceTest independenceTest) {
        if (independenceTest == null || knowledge == null) {
            throw new NullPointerException();
        }

        this.independenceTest = independenceTest;
        this.variables.addAll(independenceTest.getVariables());
    }

//    /**
//     * Constructs a new FCI search for the given independence test and background knowledge and a list of variables to
//     * search over.
//     */
//    public Cfci(IndependenceTest independenceTest, List<Node> searchVars) {
//        if (independenceTest == null || knowledge == null) {
//            throw new NullPointerException();
//        }
//
//        this.independenceTest = independenceTest;
//        this.variables.addAll(independenceTest.getVariables());
//
//        Set<Node> remVars = new HashSet<Node>();
//        for (Node node1 : this.variables) {
//            boolean search = false;
//            for (Node node2 : searchVars) {
//                if (node1.getName().equals(node2.getName())) {
//                    search = true;
//                }
//            }
//            if (!search) {
//                remVars.add(node1);
//            }
//        }
//        this.variables.removeAll(remVars);
//    }

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
        long beginTime = System.currentTimeMillis();
        if (verbose) {
            logger.log("info", "Starting FCI algorithm.");
            logger.log("info", "Independence test = " + independenceTest + ".");
        }

        setMaxReachablePathLength(maxReachablePathLength);

        List<Node> nodes = new LinkedList<>();

        for (Node variable : variables) {
            nodes.add(variable);
        }

        this.graph = new EdgeListGraph(nodes);

        Fas adj = new Fas(graph, independenceTest);
        adj.setKnowledge(getKnowledge());
        adj.setDepth(depth);
        adj.setVerbose(verbose);
        graph = adj.search();
        graph.reorientAllWith(Endpoint.CIRCLE);
        this.sepsets = adj.getSepsets();

        if (isPossibleDsepSearchDone()) {
            long time1 = System.currentTimeMillis();
            orientUnshieldedTriples(knowledge);

            long time2 = System.currentTimeMillis();

            if (verbose) {
                logger.log("info", "Step C: " + (time2 - time1) / 1000. + "s");
            }

            // Step FCI D.
            long time3 = System.currentTimeMillis();

            PossibleDsepFci possibleDSep = new PossibleDsepFci(graph, independenceTest);
            possibleDSep.setDepth(getDepth());
            possibleDSep.setKnowledge(getKnowledge());
            possibleDSep.setMaxPathLength(getMaxReachablePathLength());
            sepsets.addAll(possibleDSep.search());
            long time4 = System.currentTimeMillis();

            if (verbose) {
                logger.log("info", "Step D: " + (time4 - time3) / 1000. + "s");
            }

            // Reorient all edges as o-o.
            graph.reorientAllWith(Endpoint.CIRCLE);
        }

        // Step CI C (Zhang's step F3.)
        long time5 = System.currentTimeMillis();
        //fciOrientbk(getKnowledge(), graph, independenceTest.getVariables());    - Robert Tillman 2008
        fciOrientbk(getKnowledge(), graph, variables);
        orientUnshieldedTriples(knowledge);

        long time6 = System.currentTimeMillis();

        if (verbose) {
            logger.log("info", "Step CI C: " + (time6 - time5) / 1000. + "s");
        }

        // Step CI D. (Zhang's step F4.)
        SepsetProducer sepsets = new SepsetsGreedy(graph, independenceTest, null, -1);

        FciOrient fciOrient = new FciOrient(sepsets);
        fciOrient.setKnowledge(getKnowledge());
        fciOrient.setCompleteRuleSetUsed(completeRuleSetUsed);
        fciOrient.setMaxPathLength(-1);
        fciOrient.doFinalOrientation(graph);

        long endTime = System.currentTimeMillis();
        this.elapsedTime = endTime - beginTime;

//        graph.closeInducingPaths();   //to make sure it's a legal PAG

        if (verbose) {
            logger.log("graph", "Returning graph: " + graph);
        }
        return graph;
    }

    public SepsetMap getSepsets() {
        return sepsets;
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
     * @return true if Zhang's complete rule set should be used, false if only R1-T1 (the rule set of the original FCI)
     * should be used. False by default.
     */
    public boolean isCompleteRuleSetUsed() {
        return completeRuleSetUsed;
    }

    /**
     * @param completeRuleSetUsed set to true if Zhang's complete rule set should be used, false if only R1-T1 (the rule
     *                            set of the original FCI) should be used. False by default.
     */
    public void setCompleteRuleSetUsed(boolean completeRuleSetUsed) {
        this.completeRuleSetUsed = completeRuleSetUsed;
    }

    public Set<Triple> getColliderTriples() {
        return new HashSet<>(colliderTriples);
    }

    public Set<Triple> getNoncolliderTriples() {
        return new HashSet<>(noncolliderTriples);
    }

    public Set<Triple> getAmbiguousTriples() {
        return new HashSet<>(ambiguousTriples);
    }

    //===========================PRIVATE METHODS=========================//

    private void orientUnshieldedTriples(IKnowledge knowledge) {
        TetradLogger.getInstance().log("info", "Starting Collider Orientation:");

        colliderTriples = new HashSet<>();
        noncolliderTriples = new HashSet<>();
        ambiguousTriples = new HashSet<>();
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

                if (this.graph.isAdjacentTo(x, z)) {
                    continue;
                }

                List<List<Node>> sepsetsxz = getSepsets(x, z, graph);

                if (isColliderSepset(y, sepsetsxz)) {
                    if (colliderAllowed(x, y, z, knowledge)) {
                        graph.setEndpoint(x, y, Endpoint.ARROW);
                        graph.setEndpoint(z, y, Endpoint.ARROW);

                        TetradLogger.getInstance().log("colliderOrientations", SearchLogUtils.colliderOrientedMsg(x, y, z));
                    }

                    colliderTriples.add(new Triple(x, y, z));
                } else if (isNoncolliderSepset(y, sepsetsxz)) {
                    noncolliderTriples.add(new Triple(x, y, z));
                } else {
                    Triple triple = new Triple(x, y, z);
                    ambiguousTriples.add(triple);
                    graph.addAmbiguousTriple(triple.getX(), triple.getY(), triple.getZ());
                }
            }
        }

        TetradLogger.getInstance().log("info", "Finishing Collider Orientation.");
    }

    private List<List<Node>> getSepsets(Node i, Node k, Graph g) {
        List<Node> adji = g.getAdjacentNodes(i);
        List<Node> adjk = g.getAdjacentNodes(k);
        List<List<Node>> sepsets = new ArrayList<>();

        for (int d = 0; d <= Math.max(adji.size(), adjk.size()); d++) {
            if (adji.size() >= 2 && d <= adji.size()) {
                ChoiceGenerator gen = new ChoiceGenerator(adji.size(), d);
                int[] choice;

                while ((choice = gen.next()) != null) {
                    List<Node> v = GraphUtils.asList(choice, adji);
                    if (getIndependenceTest().isIndependent(i, k, v)) sepsets.add(v);
                }
            }

            if (adjk.size() >= 2 && d <= adjk.size()) {
                ChoiceGenerator gen = new ChoiceGenerator(adjk.size(), d);
                int[] choice;

                while ((choice = gen.next()) != null) {
                    List<Node> v = GraphUtils.asList(choice, adjk);
                    if (getIndependenceTest().isIndependent(i, k, v)) sepsets.add(v);
                }
            }
        }

        return sepsets;
    }

    private boolean isColliderSepset(Node j, List<List<Node>> sepsets) {
        if (sepsets.isEmpty()) return false;

        for (List<Node> sepset : sepsets) {
            if (sepset.contains(j)) return false;
        }

        return true;
    }

    private boolean isNoncolliderSepset(Node j, List<List<Node>> sepsets) {
        if (sepsets.isEmpty()) return false;

        for (List<Node> sepset : sepsets) {
            if (!sepset.contains(j)) return false;
        }

        return true;
    }

    private boolean colliderAllowed(Node x, Node y, Node z, IKnowledge knowledge) {
        return Cpc.isArrowpointAllowed1(x, y, knowledge) &&
                Cpc.isArrowpointAllowed1(z, y, knowledge);
    }

    public static boolean isArrowpointAllowed1(Node from, Node to,
                                               IKnowledge knowledge) {
        return knowledge == null || !knowledge.isRequired(to.toString(), from.toString()) &&
                !knowledge.isForbidden(from.toString(), to.toString());
    }

    /**
     * Orients according to background knowledge
     */
    private void fciOrientbk(IKnowledge bk, Graph graph, List<Node> variables) {
        if (verbose) {
            logger.log("info", "Starting BK Orientation.");
        }

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

            if (verbose) {
                logger.log("knowledgeOrientation", SearchLogUtils.edgeOrientedMsg("Knowledge", graph.getEdge(from, to)));
            }
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

            // Orient from*->to (?)
            // Orient from-->to

            if (verbose) {
                System.out.println("Rule T3: Orienting " + from + "-->" + to);
            }

            graph.setEndpoint(to, from, Endpoint.TAIL);
            graph.setEndpoint(from, to, Endpoint.ARROW);

            if (verbose) {
                logger.log("knowledgeOrientation", SearchLogUtils.edgeOrientedMsg("Knowledge", graph.getEdge(from, to)));
            }
        }

        if (verbose) {
            logger.log("info", "Finishing BK Orientation.");
        }
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
    public int getMaxReachablePathLength() {
        return maxReachablePathLength == Integer.MAX_VALUE ? -1 : maxReachablePathLength;
    }

    /**
     * @param maxReachablePathLength the maximum length of any discriminating path, or -1 if unlimited.
     */
    public void setMaxReachablePathLength(int maxReachablePathLength) {
        if (maxReachablePathLength < -1) {
            throw new IllegalArgumentException("Max path length must be -1 (unlimited) or >= 0: " + maxReachablePathLength);
        }

        this.maxReachablePathLength = maxReachablePathLength == -1
                ? Integer.MAX_VALUE : maxReachablePathLength;
    }

    /**
     * Whether verbose output (about independencies) is output.
     */
    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public IndependenceTest getIndependenceTest() {
        return independenceTest;
    }
}


