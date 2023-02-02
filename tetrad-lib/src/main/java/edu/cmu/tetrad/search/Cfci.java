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

import edu.cmu.tetrad.data.CorrelationMatrix;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.data.KnowledgeEdge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.TetradLogger;

import java.lang.management.ManagementFactory;
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
public final class Cfci implements GraphSearch {

    /**
     * The PAG being constructed.
     */
    private Graph graph;

    /**
     * The SepsetMap being constructed.
     */
    private final SepsetMap sepsets = new SepsetMap();

    /**
     * The background knowledge.
     */
    private Knowledge knowledge = new Knowledge();

    /**
     * The variables to search over (optional)
     */
    private final List<Node> variables = new ArrayList<>();

    /**
     * The independence test.
     */
    private final IndependenceTest independenceTest;

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
    private final TetradLogger logger = TetradLogger.getInstance();

    private boolean verbose;
    private boolean doDiscriminatingPathRule;

    //============================CONSTRUCTORS============================//

    /**
     * Constructs a new FCI search for the given independence test and background knowledge.
     */
    public Cfci(IndependenceTest independenceTest) {
        if (independenceTest == null) {
            throw new NullPointerException();
        }

        this.independenceTest = independenceTest;
        this.variables.addAll(independenceTest.getVariables());

        CorrelationMatrix corr = new CorrelationMatrix(independenceTest.getCov());
    }

    //========================PUBLIC METHODS==========================//

    public int getDepth() {
        return this.depth;
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
        long beginTime = ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime();
        if (this.verbose) {
            this.logger.log("info", "Starting FCI algorithm.");
            this.logger.log("info", "Independence test = " + this.independenceTest + ".");
        }

        setMaxReachablePathLength(this.maxReachablePathLength);

        //List<Node> variables = independenceTest.getVariable();       - Robert Tillman 2008

        List<Node> nodes = new LinkedList<>(this.variables);

        this.graph = new EdgeListGraph(nodes);
        this.graph.fullyConnect(Endpoint.TAIL);

//        // Step FCI B.  (Zhang's step F2.)
        Fas adj = new Fas(this.independenceTest);
        adj.setKnowledge(getKnowledge());
        adj.setDepth(this.depth);
        adj.setVerbose(this.verbose);
        this.graph = adj.search();
        this.graph.reorientAllWith(Endpoint.CIRCLE);

        // Note we don't use the sepsets from this search.

        // Optional step: Possible Dsep. (Needed for correctness but very time consuming.)
        if (isPossibleDsepSearchDone()) {
            long time1 = ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime();
            ruleR0(this.independenceTest, this.depth, this.sepsets);

            long time2 = ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime();

            if (this.verbose) {
                this.logger.log("info", "Step C: " + (time2 - time1) / 1000. + "s");
            }

            // Step FCI D.
            long time3 = ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime();

            PossibleDsepFci possibleDSep = new PossibleDsepFci(this.graph, this.independenceTest);
            possibleDSep.setDepth(getDepth());
            possibleDSep.setKnowledge(getKnowledge());
            possibleDSep.setMaxPathLength(getMaxReachablePathLength());

            // We use these sepsets though.
            this.sepsets.addAll(possibleDSep.search());
            long time4 = ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime();

            if (this.verbose) {
                this.logger.log("info", "Step D: " + (time4 - time3) / 1000. + "s");
            }

            // Reorient all edges as o-o.
            this.graph.reorientAllWith(Endpoint.CIRCLE);
        }

        // Step CI C (Zhang's step F3.)
        long time5 = ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime();
        fciOrientbk(getKnowledge(), this.graph, this.variables);
        ruleR0(this.independenceTest, this.depth, this.sepsets);

        long time6 = ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime();

        if (this.verbose) {
            this.logger.log("info", "Step CI C: " + (time6 - time5) / 1000. + "s");
        }

        // Step CI D. (Zhang's step F4.)

        FciOrient fciOrient = new FciOrient(new SepsetsConservative(this.graph, this.independenceTest,
                new SepsetMap(), this.depth));

        fciOrient.setCompleteRuleSetUsed(this.completeRuleSetUsed);
        fciOrient.setDoDiscriminatingPathColliderRule(this.doDiscriminatingPathRule);
        fciOrient.setDoDiscriminatingPathTailRule(this.doDiscriminatingPathRule);
        fciOrient.setMaxPathLength(-1);
        fciOrient.setKnowledge(this.knowledge);
        fciOrient.ruleR0(this.graph);
        fciOrient.doFinalOrientation(this.graph);

        long endTime = ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime();
        this.elapsedTime = endTime - beginTime;

        if (this.verbose) {
            this.logger.log("graph", "Returning graph: " + this.graph);
        }

        return this.graph;
    }

    public SepsetMap getSepsets() {
        return this.sepsets;
    }

    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge((Knowledge) knowledge);
    }

    /**
     * @return true if Zhang's complete rule set should be used, false if only R1-T1 (the rule set of the original FCI)
     * should be used. False by default.
     */
    public boolean isCompleteRuleSetUsed() {
        return this.completeRuleSetUsed;
    }

    /**
     * @param completeRuleSetUsed set to true if Zhang's complete rule set should be used, false if only R1-T1 (the rule
     *                            set of the original FCI) should be used. False by default.
     */
    public void setCompleteRuleSetUsed(boolean completeRuleSetUsed) {
        this.completeRuleSetUsed = completeRuleSetUsed;
    }

    public Set<Triple> getColliderTriples() {
        return new HashSet<>(this.colliderTriples);
    }

    public Set<Triple> getNoncolliderTriples() {
        return new HashSet<>(this.noncolliderTriples);
    }

    public Set<Triple> getAmbiguousTriples() {
        return new HashSet<>(this.ambiguousTriples);
    }

    //===========================PRIVATE METHODS=========================//

    private Graph getGraph() {
        return this.graph;
    }

    private void ruleR0(IndependenceTest test, int depth, SepsetMap sepsets) {
        if (this.verbose) {
            TetradLogger.getInstance().log("info", "Starting Collider Orientation:");
        }

        this.colliderTriples = new HashSet<>();
        this.noncolliderTriples = new HashSet<>();
        this.ambiguousTriples = new HashSet<>();

        for (Node y : getGraph().getNodes()) {
            List<Node> adjacentNodes = getGraph().getAdjacentNodes(y);

            if (adjacentNodes.size() < 2) {
                continue;
            }

            ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null) {
                Node x = adjacentNodes.get(combination[0]);
                Node z = adjacentNodes.get(combination[1]);

                if (this.getGraph().isAdjacentTo(x, z)) {
                    continue;
                }

                TripleType type = getTripleType(x, y, z, test, depth);
                List<Node> sepset = sepsets.get(x, z);

                if (type == TripleType.COLLIDER || (sepset != null && !sepset.contains(y))) {
                    if (isArrowpointAllowed(x, y) &&
                            isArrowpointAllowed(z, y)) {
                        getGraph().setEndpoint(x, y, Endpoint.ARROW);
                        getGraph().setEndpoint(z, y, Endpoint.ARROW);

                        if (this.verbose) {
                            TetradLogger.getInstance().log("tripleClassifications", "Collider: " + Triple.pathString(this.graph, x, y, z));
                        }
                    }

                    this.colliderTriples.add(new Triple(x, y, z));
                } else if (type == TripleType.NONCOLLIDER || (sepset != null && sepset.contains(y))) {
                    this.noncolliderTriples.add(new Triple(x, y, z));
                    if (this.verbose) {
                        TetradLogger.getInstance().log("tripleClassifications", "Noncollider: " + Triple.pathString(this.graph, x, y, z));
                    }
                } else {
                    Triple triple = new Triple(x, y, z);
                    this.ambiguousTriples.add(triple);
                    getGraph().underlines().addAmbiguousTriple(triple.getX(), triple.getY(), triple.getZ());
                    if (this.verbose) {
                        TetradLogger.getInstance().log("tripleClassifications", "AmbiguousTriples: " + Triple.pathString(this.graph, x, y, z));
                    }
                }
            }
        }

        if (this.verbose) {
            TetradLogger.getInstance().log("info", "Finishing Collider Orientation.");
        }
    }

    /**
     * Helper method. Appears to check if an arrowpoint is permitted by background knowledge.
     *
     * @param x The possible other node.
     * @param y The possible point node.
     * @return Whether the arrowpoint is allowed.
     */
    private boolean isArrowpointAllowed(Node x, Node y) {
        if (this.graph.getEndpoint(x, y) == Endpoint.ARROW) {
            return true;
        }

        if (this.graph.getEndpoint(x, y) == Endpoint.TAIL) {
            return false;
        }

        if (this.graph.getEndpoint(y, x) == Endpoint.ARROW) {
            if (!this.knowledge.isForbidden(x.getName(), y.getName())) return true;
        }

        if (this.graph.getEndpoint(y, x) == Endpoint.TAIL) {
            if (!this.knowledge.isForbidden(x.getName(), y.getName())) return true;
        }

        return this.graph.getEndpoint(y, x) == Endpoint.CIRCLE;
    }

    private TripleType getTripleType(Node x, Node y, Node z,
                                     IndependenceTest test, int depth) {
        boolean existsSepsetContainingY = false;
        boolean existsSepsetNotContainingY = false;

        Set<Node> __nodes = new HashSet<>(this.getGraph().getAdjacentNodes(x));
        __nodes.remove(z);

        List<Node> _nodes = new LinkedList<>(__nodes);

        int _depth = depth;
        if (_depth == -1) {
            _depth = 1000;
        }
        _depth = Math.min(_depth, _nodes.size());

        for (int d = 0; d <= _depth; d++) {
            ChoiceGenerator cg = new ChoiceGenerator(_nodes.size(), d);
            int[] choice;

            while ((choice = cg.next()) != null) {
                List<Node> condSet = Cfci.asList(choice, _nodes);

                if (test.checkIndependence(x, z, condSet).independent()) {
                    if (condSet.contains(y)) {
                        existsSepsetContainingY = true;
                    } else {
                        existsSepsetNotContainingY = true;
                    }
                }
            }
        }

        __nodes = new HashSet<>(this.getGraph().getAdjacentNodes(z));
        __nodes.remove(x);

        _nodes = new LinkedList<>(__nodes);

        _depth = depth;
        if (_depth == -1) {
            _depth = 1000;
        }
        _depth = Math.min(_depth, _nodes.size());

        for (int d = 0; d <= _depth; d++) {
            ChoiceGenerator cg = new ChoiceGenerator(_nodes.size(), d);
            int[] choice;

            while ((choice = cg.next()) != null) {
                List<Node> condSet = Cfci.asList(choice, _nodes);

                if (test.checkIndependence(x, z, condSet).independent()) {
                    if (condSet.contains(y)) {
                        existsSepsetContainingY = true;
                    } else {
                        existsSepsetNotContainingY = true;
                    }
                }
            }
        }

        // Note: Unless sepsets are being collected during fas, most likely
        // this will be null. (Only sepsets found during possible dsep search
        // will be here.)
        List<Node> condSet = getSepsets().get(x, z);

        if (condSet != null) {
            if (condSet.contains(y)) {
                existsSepsetContainingY = true;
            } else {
                existsSepsetNotContainingY = true;
            }
        }

        if (existsSepsetContainingY == existsSepsetNotContainingY) {
            return TripleType.AMBIGUOUS;
        } else if (!existsSepsetNotContainingY) {
            return TripleType.NONCOLLIDER;
        } else {
            return TripleType.COLLIDER;
        }
    }

    private static List<Node> asList(int[] indices, List<Node> nodes) {
        List<Node> list = new LinkedList<>();

        for (int i : indices) {
            list.add(nodes.get(i));
        }

        return list;
    }


    /**
     * Whether verbose output (about independencies) is output.
     */
    public boolean isVerbose() {
        return this.verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public void setMaxReachablePathLength(int maxReachablePathLength) {
        this.maxReachablePathLength = maxReachablePathLength;
    }

    public boolean isPossibleDsepSearchDone() {
        return this.possibleDsepSearchDone;
    }

    public void setPossibleDsepSearchDone(boolean possibleDsepSearchDone) {
        this.possibleDsepSearchDone = possibleDsepSearchDone;
    }

    public int getMaxReachablePathLength() {
        return this.maxReachablePathLength;
    }

    public void setDoDiscriminatingPathRule(boolean doDiscriminatingPathRule) {
        this.doDiscriminatingPathRule = doDiscriminatingPathRule;
    }

    private enum TripleType {
        COLLIDER, NONCOLLIDER, AMBIGUOUS
    }

    /**
     * Orients according to background knowledge
     */
    private void fciOrientbk(Knowledge bk, Graph graph, List<Node> variables) {
        if (this.verbose) {
            this.logger.log("info", "Starting BK Orientation.");
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

            // Orient to*-&gt;from
            graph.setEndpoint(to, from, Endpoint.ARROW);

            if (this.verbose) {
                this.logger.log("knowledgeOrientation", SearchLogUtils.edgeOrientedMsg("Knowledge", graph.getEdge(from, to)));
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

            // Orient from*-&gt;to (?)
            // Orient from-->to

            if (this.verbose) {
                System.out.println("Rule T3: Orienting " + from + "-->" + to);
            }

            graph.setEndpoint(to, from, Endpoint.TAIL);
            graph.setEndpoint(from, to, Endpoint.ARROW);

            if (this.verbose) {
                this.logger.log("knowledgeOrientation", SearchLogUtils.edgeOrientedMsg("Knowledge", graph.getEdge(from, to)));
            }
        }

        if (this.verbose) {
            this.logger.log("info", "Finishing BK Orientation.");
        }
    }
}


