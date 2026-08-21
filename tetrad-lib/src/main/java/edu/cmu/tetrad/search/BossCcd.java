/// ////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2026 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
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

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * BOSS-CCD: Cyclic Causal Discovery (Richardson's CCD) with the adjacency phase delegated to BOSS.
 *
 * <p>Motivation. CCD's Step A is a Fast Adjacency Search (FAS) starting from the complete graph, and in practice
 * FAS's finite-sample adjacency behavior is the weakest link: missed adjacencies and spurious adjacencies both
 * corrupt CCD's downstream orientation steps (B–F), which are already the fragile part of the algorithm. BOSS-CCD
 * instead runs BOSS first and uses the skeleton of the resulting CPDAG as an adjacency <i>superstructure</i> for
 * CCD: CCD's adjacency search starts from the BOSS skeleton (removals only, no additions), and Steps B–F proceed
 * as usual.</p>
 *
 * <p>Why this is sound for cyclic models. For a linear cyclic SEM, d-separation on the directed cyclic graph
 * characterizes the conditional independence structure of the distribution (Spirtes, 1995), and CCD's adjacencies
 * are exactly the <i>inseparable</i> pairs — including the "virtual edges" between a member of a cycle and the
 * parents of other members of that cycle (Richardson, 1996/2013). If a distribution P is Markov to a DAG G, then
 * any pair nonadjacent in G is separated (by a parent set in G); contrapositively, every pair inseparable in P is
 * adjacent in every DAG I-map of P. In the large-sample limit a correctly scored BOSS returns (the CPDAG of) an
 * edge-minimal DAG I-map, so its skeleton contains every CCD adjacency, including all virtual edges. Restricting
 * CCD's adjacency phase to that skeleton therefore loses nothing asymptotically, while (a) letting a global
 * score-based search make the adjacency decisions rather than local tests, (b) sharply reducing the number of
 * tests, and (c) shrinking the candidate pools used by CCD's sepset machinery (notably Step D's sup-sepset
 * enumeration), which improves both speed and stability.</p>
 *
 * <p>This addresses the covariance-matrix-only use case: unlike FASK, Two-Step, or other non-Gaussian methods,
 * both BOSS (with a BIC-style score) and CCD (with Fisher Z) run off second-order statistics alone.</p>
 *
 * <p>Caveats. CCD's orientation steps are unchanged, so the output remains a (partially oriented) cyclic PAG in
 * Richardson's sense, with underline/dotted-underline annotations; this method improves the inputs to those steps
 * rather than replacing them. Also, BOSS on data from a cyclic model is fitting a DAG to a non-DAG distribution;
 * the I-map covering argument is asymptotic, and at small samples BOSS may omit weak virtual edges, which CCD
 * cannot then restore.</p>
 *
 * <p>Knowledge is honored as in CCD: forbidden directed edges only; required edges are rejected.</p>
 *
 * @author josephramsey
 * @see Ccd
 * @see Boss
 */
public final class BossCcd implements IGraphSearch {

    /**
     * The score used by the BOSS adjacency phase.
     */
    private final Score score;

    /**
     * The independence test used by the CCD phase.
     */
    private IndependenceTest test;

    /**
     * Background knowledge: only forbidden directed edges are honored.
     */
    private Knowledge knowledge = new Knowledge();

    /**
     * Maximum conditioning depth for the CCD phase; -1 means unlimited.
     */
    private int depth = -1;

    /**
     * Whether to apply CCD's R1 push-away rule (default: true).
     */
    private boolean applyR1 = true;

    /**
     * Whether BOSS should run BES after each best-move sweep.
     */
    private boolean useBes = false;

    /**
     * Number of random restarts for BOSS.
     */
    private int numStarts = 1;

    /**
     * Whether BOSS should use the data variable order for the first start.
     */
    private boolean useDataOrder = true;

    /**
     * Verbose logging toggle.
     */
    private boolean verbose;

    /**
     * Constructs a BossCcd search with the given test (for the CCD phase) and score (for the BOSS phase). The two must
     * be defined over the same variable set (matched by name).
     *
     * @param test  the independence test for the CCD phase. Must not be null.
     * @param score the score for the BOSS phase. Must not be null.
     */
    public BossCcd(IndependenceTest test, Score score) {
        Objects.requireNonNull(test, "test");
        Objects.requireNonNull(score, "score");

        Set<String> testNames = test.getVariables().stream().map(Object::toString).collect(Collectors.toSet());
        Set<String> scoreNames = score.getVariables().stream().map(Object::toString).collect(Collectors.toSet());
        if (!testNames.equals(scoreNames)) {
            throw new IllegalArgumentException("Test and score must be over the same variables (by name).");
        }

        this.test = test;
        this.score = score;
    }

    /**
     * Runs BOSS to obtain a CPDAG, extracts its skeleton, and runs CCD with that skeleton as an adjacency
     * superstructure.
     *
     * @return the cyclic PAG produced by CCD (with underline/dotted-underline annotations).
     * @throws InterruptedException if interrupted.
     */
    @Override
    public Graph search() throws InterruptedException {
        if (verbose) TetradLogger.getInstance().log("BOSS-CCD: Phase 1 — BOSS adjacency superstructure");

        Boss boss = new Boss(this.score);
        boss.setUseBes(this.useBes);
        boss.setNumStarts(this.numStarts);
        boss.setUseDataOrder(this.useDataOrder);
        boss.setVerbose(this.verbose);

        PermutationSearch permutationSearch = new PermutationSearch(boss);
        permutationSearch.setKnowledge(this.knowledge);
        Graph cpdag = permutationSearch.search();

        Graph superstructure = GraphUtils.undirectedGraph(cpdag);

        if (verbose) {
            TetradLogger.getInstance().log("BOSS-CCD: superstructure has " + superstructure.getNumEdges()
                    + " edges over " + superstructure.getNumNodes() + " nodes.");
            TetradLogger.getInstance().log("BOSS-CCD: Phase 2 — CCD restricted to superstructure");
        }

        Ccd ccd = new Ccd(this.test);
        ccd.setKnowledge(this.knowledge);
        ccd.setDepth(this.depth);
        ccd.setApplyR1(this.applyR1);
        ccd.setVerbose(this.verbose);
        ccd.setSuperstructure(superstructure);

        return ccd.search();
    }

    /**
     * Retrieves the independence test used by the CCD phase.
     *
     * @return the current IndependenceTest instance.
     */
    @Override
    public IndependenceTest getTest() {
        return this.test;
    }

    /**
     * Sets the independence test used by the CCD phase. The provided test must have the same variable set as the
     * current test. (This supports, e.g., the FDR wrapper loop.)
     *
     * @param test the new independence test. Must not be null.
     */
    @Override
    public void setTest(IndependenceTest test) {
        Objects.requireNonNull(test, "test");
        Set<String> oldSet = this.test.getVariables().stream().map(Object::toString).collect(Collectors.toSet());
        Set<String> newSet = new HashSet<>(test.getVariables().stream().map(Object::toString).collect(Collectors.toSet()));
        if (!oldSet.equals(newSet)) {
            throw new IllegalArgumentException("New test must have the same variable set as the existing test.");
        }
        this.test = test;
    }

    /**
     * Set background knowledge (forbidden directed edges only, as in CCD). Required edges are not supported.
     *
     * @param knowledge the knowledge to be set.
     */
    public void setKnowledge(Knowledge knowledge) {
        if (knowledge == null) throw new NullPointerException("knowledge must not be null");
        if (!knowledge.getListOfRequiredEdges().isEmpty()) {
            throw new IllegalArgumentException("Required edges are not supported for BOSS-CCD.");
        }
        this.knowledge = knowledge;
    }

    /**
     * Sets the maximum conditioning depth for the CCD phase; -1 means unlimited.
     *
     * @param depth the depth cap.
     */
    public void setDepth(int depth) {
        this.depth = depth;
    }

    /**
     * Sets whether CCD's R1 push-away rule should be applied.
     *
     * @param applyR1 true to apply R1.
     */
    public void setApplyR1(boolean applyR1) {
        this.applyR1 = applyR1;
    }

    /**
     * Sets whether BOSS should run BES after each best-move sweep.
     *
     * @param useBes true to use BES.
     */
    public void setUseBes(boolean useBes) {
        this.useBes = useBes;
    }

    /**
     * Sets the number of random restarts for BOSS.
     *
     * @param numStarts the number of starts; must be at least 1.
     */
    public void setNumStarts(int numStarts) {
        if (numStarts < 1) throw new IllegalArgumentException("numStarts must be at least 1: " + numStarts);
        this.numStarts = numStarts;
    }

    /**
     * Sets whether BOSS should use the data variable order for its first start.
     *
     * @param useDataOrder true to use the data order.
     */
    public void setUseDataOrder(boolean useDataOrder) {
        this.useDataOrder = useDataOrder;
    }

    /**
     * Sets whether verbose output should be enabled.
     *
     * @param verbose true for verbose logging.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }
}
