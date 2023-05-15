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

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.data.KnowledgeEdge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.utils.*;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.MillisecondTimes;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.math3.util.FastMath;

import java.util.*;


/**
 * <p></p></>Adjusts FCI (see) to use conservative orientation as in CPC (see). Because the
 * collider orientatation is conservative, there may be ambiguous triples; these
 * may be retrieved using that accessor method.</p>
 *
 * <p>This class is configured to respect knowledge of forbidden and required
 * edges, including knowledge of temporal tiers.</p>
 *
 * @author josephramsey
 * @see Fci
 * @see Cpc
 * @see #getAmbiguousTriples()
 * @see Knowledge
 */
public final class Cfci implements IGraphSearch {

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
     * Flag for complete rule set, true if you should use complete rule set, false otherwise.
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
     *
     * @param independenceTest The independence to use as an oracle.
     */
    public Cfci(IndependenceTest independenceTest) {
        if (independenceTest == null) {
            throw new NullPointerException();
        }

        this.independenceTest = independenceTest;
        this.variables.addAll(independenceTest.getVariables());
    }

    //========================PUBLIC METHODS==========================//

    /**
     * Performs the search and returns the PAG.
     *
     * @return The search PAG.
     */
    public Graph search() {
        long beginTime = MillisecondTimes.timeMillis();
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
        adj.setKnowledge(this.knowledge);
        adj.setDepth(this.depth);
        adj.setVerbose(this.verbose);
        this.graph = adj.search();
        this.graph.reorientAllWith(Endpoint.CIRCLE);

        // Note we don't use the sepsets from this search.

        // Optional step: Possible Dsep. (Needed for correctness but very time consuming.)
        if (isPossibleDsepSearchDone()) {
            long time1 = MillisecondTimes.timeMillis();
            ruleR0(this.independenceTest, this.depth, this.sepsets);

            long time2 = MillisecondTimes.timeMillis();

            if (this.verbose) {
                this.logger.log("info", "Step C: " + (time2 - time1) / 1000. + "s");
            }

            // Step FCI D.
            long time3 = MillisecondTimes.timeMillis();

            PossibleDsepFci possibleDSep = new PossibleDsepFci(this.graph, this.independenceTest);
            possibleDSep.setDepth(this.depth);
            possibleDSep.setKnowledge(this.knowledge);
            possibleDSep.setMaxPathLength(getMaxReachablePathLength());

            // We use these sepsets though.
            this.sepsets.addAll(possibleDSep.search());
            long time4 = MillisecondTimes.timeMillis();

            if (this.verbose) {
                this.logger.log("info", "Step D: " + (time4 - time3) / 1000. + "s");
            }

            // Reorient all edges as o-o.
            this.graph.reorientAllWith(Endpoint.CIRCLE);
        }

        // Step CI C (Zhang's step F3.)
        long time5 = MillisecondTimes.timeMillis();
        fciOrientbk(this.knowledge, this.graph, this.variables);
        ruleR0(this.independenceTest, this.depth, this.sepsets);

        long time6 = MillisecondTimes.timeMillis();

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

        long endTime = MillisecondTimes.timeMillis();
        this.elapsedTime = endTime - beginTime;

        if (this.verbose) {
            this.logger.log("graph", "Returning graph: " + this.graph);
        }

        return this.graph;
    }

    /**
     * Sets the depth--i.e., the maximum number of variables conditioned on in any test.
     *
     * @param depth This maximum.
     */
    public void setDepth(int depth) {
        if (depth < -1) {
            throw new IllegalArgumentException(
                    "Depth must be -1 (unlimited) or >= 0: " + depth);
        }

        this.depth = depth;
    }

    /**
     * Returns the elapsed time ot the search.
     *
     * @return This time.
     */
    public long getElapsedTime() {
        return this.elapsedTime;
    }

    /**
     * Returns the map from nodes to their sepsets. For x _||_ y | z1,...,zn, this
     * would map {x, y} to {z1,..,zn}.
     *
     * @return This map.
     */
    public SepsetMap getSepsets() {
        return this.sepsets;
    }

    /**
     * Set the knowledge used in the search.
     *
     * @param knowledge This knowledge.
     * @see Knowledge
     */
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }

    /**
     * Returns true if Zhang's complete rule set should be used, false if only R1-T1 (the rule set of the original FCI)
     * should be used. False by default.
     *
     * @return True for the complete ruleset.
     */
    public boolean isCompleteRuleSetUsed() {
        return this.completeRuleSetUsed;
    }

    /**
     * Sets whether the complete ruleset should be used.
     *
     * @param completeRuleSetUsed set to true if Zhang's complete rule set should be used, false if only R1-T1 (the rule
     *                            set of the original FCI) should be used. False by default.
     */
    public void setCompleteRuleSetUsed(boolean completeRuleSetUsed) {
        this.completeRuleSetUsed = completeRuleSetUsed;
    }

    /**
     * Returns the ambiguous triples found in the search.
     *
     * @return This set.
     * @see Cpc
     */
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
                    if (isArrowheadAllowed(x, y) &&
                            isArrowheadAllowed(z, y)) {
                        getGraph().setEndpoint(x, y, Endpoint.ARROW);
                        getGraph().setEndpoint(z, y, Endpoint.ARROW);

                        if (this.verbose) {
                            TetradLogger.getInstance().log("tripleClassifications", "Collider: " + Triple.pathString(this.graph, x, y, z));
                        }
                    }

                } else {
                    Triple triple = new Triple(x, y, z);
                    this.ambiguousTriples.add(triple);
                    getGraph().addAmbiguousTriple(triple.getX(), triple.getY(), triple.getZ());
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
     * Helper method. Appears to check if an arrowhead is permitted by background knowledge.
     *
     * @param x The possible other node.
     * @param y The possible point node.
     * @return Whether the arrowhead is allowed.
     */
    private boolean isArrowheadAllowed(Node x, Node y) {
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
        _depth = FastMath.min(_depth, _nodes.size());

        for (int d = 0; d <= _depth; d++) {
            ChoiceGenerator cg = new ChoiceGenerator(_nodes.size(), d);
            int[] choice;

            while ((choice = cg.next()) != null) {
                List<Node> condSet = Cfci.asList(choice, _nodes);

                if (test.checkIndependence(x, z, condSet).isIndependent()) {
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
        _depth = FastMath.min(_depth, _nodes.size());

        for (int d = 0; d <= _depth; d++) {
            ChoiceGenerator cg = new ChoiceGenerator(_nodes.size(), d);
            int[] choice;

            while ((choice = cg.next()) != null) {
                List<Node> condSet = Cfci.asList(choice, _nodes);

                if (test.checkIndependence(x, z, condSet).isIndependent()) {
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

            if (this.verbose) {
                this.logger.log("knowledgeOrientation", LogUtilsSearch.edgeOrientedMsg("Knowledge", graph.getEdge(from, to)));
            }
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

            // Orient from*-&gt;to (?)
            // Orient from-->to

            if (this.verbose) {
                System.out.println("Rule T3: Orienting " + from + "-->" + to);
            }

            graph.setEndpoint(to, from, Endpoint.TAIL);
            graph.setEndpoint(from, to, Endpoint.ARROW);

            if (this.verbose) {
                this.logger.log("knowledgeOrientation", LogUtilsSearch.edgeOrientedMsg("Knowledge", graph.getEdge(from, to)));
            }
        }

        if (this.verbose) {
            this.logger.log("info", "Finishing BK Orientation.");
        }
    }
}


