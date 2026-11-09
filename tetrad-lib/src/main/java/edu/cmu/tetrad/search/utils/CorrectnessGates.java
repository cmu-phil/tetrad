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

package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.search.RecursiveBlocking;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.score.MagRicfBicScorer;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The two per-commit correctness gates shared by the latent-variable searches that remove edges from a Markov
 * start under a legality certificate: the *-FCI family ({@code StarFci} and its subclasses), FCIT, and FCIT-ZM.
 * Each search asks, after its own legality check has passed on a candidate, whether the candidate's Zhang MAG
 * also passes whichever of these gates is on; a veto means the candidate is not committed and the search tries
 * its next candidate. Keeping the gates in one place is what keeps the three searches' notion of each option
 * identical.
 *
 * <p><b>The three-way choice.</b> The wrappers expose one parameter, {@code fcitCorrectness}, interpreted by
 * {@link #applyOption(int, Score, double)}:
 * <ol>
 * <li><b>Guarantee I-map.</b> {@link #setRequireImapStates(boolean)}. A candidate passes iff every nonadjacent
 * pair of its Zhang MAG that is not already uncertified in the committed state has a test-confirmed separator at
 * {@link #setImapAlpha(double) imapAlpha}: a known separator that m-separates the pair in the MAG, or failing
 * that, the recursive-blocking separator in the MAG, tested once and cached if independent. Pairs uncertified in
 * the committed state are exempt, so a violation that predates the move cannot veto every later move. PROVED:
 * with it, every committed state asserts no independence the test has not confirmed. CONJECTURED, not proved:
 * that the gated search never stalls short of the true PAG (the generalized-Meek-for-projections conjecture,
 * exhaustively supported at five and six observed variables, open in general). OBSERVED on data (2026-10-21):
 * with imapAlpha equal to the removal alpha the gate reduced accuracy, each false "dependent" vetoing a correct
 * move; the smaller default imapAlpha is the response.</li>
 * <li><b>Score step.</b> {@link #setUseScoreStep(boolean)} with {@link #setGateScore(Score)}. The candidate's
 * Zhang MAG is scored by RICF BIC and passes iff its score exceeds the committed score by more than
 * {@link #SCORE_EPSILON}; while {@link #setTiesAccepted(boolean) ties are accepted} (Pass 0 of the *-FCI family,
 * where no edge is removed) a non-decreasing score passes. This is the rule of the BfciBryanScore fork, chosen
 * on 2026-11-08 over FCIT's earlier non-decreasing rule so that all three searches agree. Linear-Gaussian only:
 * a {@link SemBicScore} on a covariance matrix with zero structure prior is required, so that the DAG and MAG
 * scores are on one scale.</li>
 * <li><b>Saturating step.</b> Neither gate; the search runs its own joint-removal step for pairs that have a
 * separator but no legal single removal. Not a gate, so not implemented here; {@link #applyOption} returns
 * whether the caller should turn its saturating step on, which is exactly when neither gate is on.</li>
 * </ol>
 *
 * <p><b>Seeds.</b> The I-map check trusts a separator recorded in the trial sepset map unless the pair is a
 * refuted seed ({@link #addRefutedSeed(Node, Node)}), and it trusts any separator added through
 * {@link #addKnownSeparator(Node, Node, Set)}. The two entry points serve two styles of start: a total sepset
 * map seeded from the start DAG, in which refuted seeds are marked (the *-FCI family), and an empty map, in
 * which only test-confirmed seeds are entered as known separators (FCIT). The two styles give the same
 * verdicts.
 *
 * <p><b>Protocol.</b> Call {@link #beginRun(Graph, SepsetMap)} once the start PAG and its sepset map exist and
 * all seeds are declared; call {@link #accepts(Graph, SepsetMap, String)} on each candidate after the search's
 * own legality check passes, and commit the candidate iff it returns true; call
 * {@link #committed(Graph, SepsetMap)} after each commit. Not thread-safe; one instance per search run.
 *
 * @author bryanandrews
 * @author josephramsey
 */
public final class CorrectnessGates {

    /**
     * Score differences below this are ties.
     */
    public static final double SCORE_EPSILON = 1e-6;

    private final IndependenceTest test;
    private boolean verbose = false;

    // ---- I-map gate ----
    private boolean requireImapStates = false;
    private double imapAlpha = 0.001;
    private final Map<Set<Node>, List<Set<Node>>> knownSeparators = new HashMap<>();
    private final Set<Set<Node>> refutedSeeds = new HashSet<>();
    private Set<Set<Node>> baselineUncertified = new HashSet<>();
    private int imapCandidates = 0, imapVetoes = 0, imapTests = 0, imapIndeterminate = 0;

    // ---- Score step ----
    private boolean useScoreStep = false;
    private SemBicScore gateScore = null;
    private MagRicfBicScorer magScorer = null;
    private double currentScore = Double.NaN;
    private boolean tiesAccepted = false;
    private int scoreAccepted = 0, scoreRejected = 0, scoreUnavailable = 0;

    /**
     * Constructs the gates for a search using the given independence test.
     *
     * @param test the test the search removes edges by; the I-map gate's confirmations use it.
     */
    public CorrectnessGates(IndependenceTest test) {
        if (test == null) throw new NullPointerException("Test is null");
        this.test = test;
    }

    // ---- configuration ----

    /**
     * Applies one of the three correctness options and reports whether the caller's saturating step should run.
     *
     * @param option    1 = Guarantee I-map, 2 = Score step, 3 = Saturating step.
     * @param score     the SEM BIC score of the data, consulted only for option 2 (may be null otherwise).
     * @param imapAlpha the I-map gate alpha, consulted only for option 1.
     * @return true iff the saturating step should be on, which is exactly option 3.
     * @throws IllegalArgumentException if the option is unknown, or option 2 is chosen with a score the score step
     *                                  cannot use (which includes every non-continuous data type).
     */
    public boolean applyOption(int option, Score score, double imapAlpha) {
        switch (option) {
            case 1 -> {
                setRequireImapStates(true);
                setImapAlpha(imapAlpha);
                setUseScoreStep(false);
                return false;
            }
            case 2 -> {
                setRequireImapStates(false);
                setUseScoreStep(true);
                setGateScore(score);
                return false;
            }
            case 3 -> {
                setRequireImapStates(false);
                setUseScoreStep(false);
                return true;
            }
            default -> throw new IllegalArgumentException("Unknown correctness option: " + option
                                                          + " (1 = Guarantee I-map, 2 = Score step, 3 = Saturating step).");
        }
    }

    /**
     * @param verbose whether gate decisions are logged.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * @param requireImapStates whether the I-map gate is on. Default false.
     */
    public void setRequireImapStates(boolean requireImapStates) {
        this.requireImapStates = requireImapStates;
    }

    /**
     * @return whether the I-map gate is on.
     */
    public boolean requireImapStates() {
        return requireImapStates;
    }

    /**
     * @param imapAlpha the significance level of the I-map gate's tests, in (0, 1). Default 0.001.
     */
    public void setImapAlpha(double imapAlpha) {
        if (!(imapAlpha > 0 && imapAlpha < 1)) {
            throw new IllegalArgumentException("imapAlpha must be in (0, 1): " + imapAlpha);
        }
        this.imapAlpha = imapAlpha;
    }

    /**
     * @return the I-map gate alpha.
     */
    public double imapAlpha() {
        return imapAlpha;
    }

    /**
     * @param useScoreStep whether the score step is on. Requires {@link #setGateScore(Score)} before
     *                     {@link #beginRun}. Default false.
     */
    public void setUseScoreStep(boolean useScoreStep) {
        this.useScoreStep = useScoreStep;
    }

    /**
     * @return whether the score step is on.
     */
    public boolean useScoreStep() {
        return useScoreStep;
    }

    /**
     * @return whether either gate is on.
     */
    public boolean anyGateOn() {
        return requireImapStates || useScoreStep;
    }

    /**
     * Sets the score the score step takes its covariance matrix and penalty discount from.
     *
     * @param score a {@link SemBicScore} built on a covariance matrix with zero structure prior; null clears it.
     * @throws IllegalArgumentException if the score is not usable for the score step.
     */
    public void setGateScore(Score score) {
        if (score == null) {
            this.gateScore = null;
            return;
        }
        if (!(score instanceof SemBicScore semBic)) {
            throw new IllegalArgumentException("The score step requires a SemBicScore: its RICF MAG score is the "
                                               + "linear-Gaussian BIC and is comparable only with that DAG score (got "
                                               + score.getClass().getSimpleName() + ").");
        }
        if (semBic.getStructurePrior() != 0) {
            throw new IllegalArgumentException("The score step requires a zero structure prior on the SemBicScore: "
                                               + "the MAG score has no counterpart, so the DAG and MAG scales would "
                                               + "differ by a graph-dependent amount (got "
                                               + semBic.getStructurePrior() + ").");
        }
        if (semBic.getCovariances() == null) {
            throw new IllegalArgumentException("The score step requires a SemBicScore built on a covariance matrix.");
        }
        this.gateScore = semBic;
    }

    /**
     * @return the penalty discount of the gate score, for logging; NaN if none is set.
     */
    public double penaltyDiscount() {
        return gateScore == null ? Double.NaN : gateScore.getPenaltyDiscount();
    }

    /**
     * Sets whether the score step accepts a tie (a non-decreasing score) rather than requiring a strict
     * improvement. The *-FCI family sets this during Pass 0, where a separating set is re-selected for an
     * already nonadjacent pair and no edge is removed. Default false.
     *
     * @param tiesAccepted true to accept ties.
     */
    public void setTiesAccepted(boolean tiesAccepted) {
        this.tiesAccepted = tiesAccepted;
    }

    // ---- run protocol ----

    /**
     * Resets all per-run state. Call before declaring seeds for a new run.
     */
    public void reset() {
        knownSeparators.clear();
        refutedSeeds.clear();
        baselineUncertified = new HashSet<>();
        imapCandidates = imapVetoes = imapTests = imapIndeterminate = 0;
        scoreAccepted = scoreRejected = scoreUnavailable = 0;
        tiesAccepted = false;
        magScorer = null;
        currentScore = Double.NaN;
    }

    /**
     * Declares that the seeded separator for a pair was refuted by the test, so that the trial map's entry for
     * the pair is not trusted by the I-map check.
     *
     * @param a one node of the pair.
     * @param b the other.
     */
    public void addRefutedSeed(Node a, Node b) {
        refutedSeeds.add(Set.of(a, b));
    }

    /**
     * Declares a test-confirmed separator for a pair, trusted by the I-map check whenever it m-separates the pair
     * in a candidate MAG.
     *
     * @param x   one node of the pair.
     * @param z   the other.
     * @param sep the separator.
     */
    public void addKnownSeparator(Node x, Node z, Set<Node> sep) {
        knownSeparators.computeIfAbsent(Set.of(x, z), k -> new ArrayList<>()).add(new HashSet<>(sep));
    }

    /**
     * The I-map gate's verdict on one independence fact: the test's p-value against imapAlpha when the test
     * reports a probability, else the test's own verdict. Counted as an I-map test.
     *
     * @param x    one node.
     * @param z    the other.
     * @param cond the conditioning set.
     * @return true if judged independent.
     * @throws InterruptedException if interrupted.
     */
    public boolean judgedIndependent(Node x, Node z, Set<Node> cond) throws InterruptedException {
        imapTests++;
        IndependenceResult r = test.checkIndependence(x, z, cond);
        return test.isPValueAProbability() ? r.getPValue() > imapAlpha : r.isIndependent();
    }

    /**
     * Begins a run: builds the RICF scorer and scores the start graph when the score step is on, and computes the
     * start graph's uncertified pairs when the I-map gate is on. Call after {@link #reset()} and after all seeds
     * are declared.
     *
     * @param startPag     the start PAG.
     * @param committedMap the committed sepset map at the start.
     * @throws IllegalStateException if the score step is on with no gate score, or the start graph is unscorable.
     * @throws InterruptedException  if interrupted.
     */
    public void beginRun(Graph startPag, SepsetMap committedMap) throws InterruptedException {
        if (useScoreStep) {
            if (gateScore == null) {
                throw new IllegalStateException("The score step is on but no gate score was set; call "
                                                + "setGateScore with the SEM BIC score of the data.");
            }
            magScorer = new MagRicfBicScorer(gateScore.getCovariances());
            magScorer.setPenaltyDiscount(gateScore.getPenaltyDiscount());
            // Trusted-caller fast path, as in the BfciBryanScore fork: every scored MAG is the Zhang MAG of a
            // candidate over the test's variables, its edge types are re-checked in scoreMag, and (except for the
            // start graph, legal by construction) it has already passed the caller's legality check, so the scorer's
            // per-call node replacement, name validation, and cycle check are pure overhead. Tolerance 1e-8 is two
            // decades below SCORE_EPSILON, so acceptance decisions are unaffected.
            magScorer.setSkipChecks(true);
            magScorer.setTolerance(1e-8);

            Double initialScore = scoreMag(tryZhangMag(startPag));
            if (initialScore == null) {
                throw new IllegalStateException("The score step could not score the start PAG.");
            }
            currentScore = initialScore;
        }

        if (requireImapStates) {
            refreshBaseline(startPag, committedMap);
        }
    }

    /**
     * Whether a candidate that has passed the caller's legality check also passes the gates that are on. The
     * I-map gate is evaluated first, so a candidate it vetoes does not touch the committed score. A true return
     * means the caller must commit the candidate (the score step has already recorded its score as committed);
     * a false return means nothing was recorded and the caller should try its next candidate.
     *
     * @param mag      the candidate's Zhang MAG, or null if it has none (vetoed by either gate).
     * @param trialMap the trial sepset map, holding the candidate's separators.
     * @param what     a description of the move, for logging.
     * @return true iff the candidate passes every gate that is on.
     * @throws InterruptedException if interrupted.
     */
    public boolean accepts(Graph mag, SepsetMap trialMap, String what) throws InterruptedException {
        if (requireImapStates && !isImapOfTest(mag, trialMap, what)) return false;
        if (useScoreStep && !scoreAccepts(mag, what)) return false;
        return true;
    }

    /**
     * Records a commit: recomputes the committed state's uncertified pairs when the I-map gate is on.
     *
     * @param committedPag the committed PAG.
     * @param committedMap the committed sepset map.
     * @throws InterruptedException if interrupted.
     */
    public void committed(Graph committedPag, SepsetMap committedMap) throws InterruptedException {
        if (requireImapStates) refreshBaseline(committedPag, committedMap);
    }

    /**
     * One line describing the active gates, for a search's start-of-run log.
     *
     * @return the description, empty if no gate is on.
     */
    public String describeActive() {
        StringBuilder sb = new StringBuilder();
        if (requireImapStates) sb.append(", I-map gate at imapAlpha = ").append(imapAlpha);
        if (useScoreStep) sb.append(", RICF-BIC score step, c = ").append(penaltyDiscount());
        return sb.toString();
    }

    /**
     * Logs the end-of-run summaries of the gates that are on, when verbose.
     *
     * @param prefix the search's name, for the log lines.
     */
    public void logSummary(String prefix) {
        logSummary(prefix, false);
    }

    /**
     * Logs the end-of-run summaries of the gates that are on.
     *
     * @param prefix the search's name, for the log lines.
     * @param always true to log even when not verbose (FCIT reports its gate summaries unconditionally).
     */
    public void logSummary(String prefix, boolean always) {
        if (!verbose && !always) return;
        if (requireImapStates) {
            TetradLogger.getInstance().log(prefix + " I-map gate summary (imapAlpha = " + imapAlpha
                    + "): legal candidates checked = " + imapCandidates + ", vetoed = " + imapVetoes
                    + ", separator tests run = " + imapTests + ", no separator found = " + imapIndeterminate + ".");
        }
        if (useScoreStep) {
            TetradLogger.getInstance().log(prefix + " score step summary (c = " + penaltyDiscount()
                    + "): legal candidates accepted = " + scoreAccepted + ", rejected = " + scoreRejected
                    + ", unscorable = " + scoreUnavailable + "; final MAG score = " + currentScore + ".");
        }
    }

    /**
     * The Zhang MAG of the given PAG, or null if the conversion fails (which both gates treat as a veto).
     *
     * @param pag the PAG.
     * @return its Zhang MAG, or null.
     */
    public static Graph tryZhangMag(Graph pag) {
        try {
            return GraphTransforms.zhangMagFromPag(pag);
        } catch (Exception e) {
            return null;
        }
    }

    // ---- I-map gate internals ----

    private boolean isImapOfTest(Graph mag, SepsetMap trialMap, String what) throws InterruptedException {
        imapCandidates++;
        Set<Node> bad = firstNewUncertifiedPair(mag, trialMap, what);
        if (bad != null) imapVetoes++;
        return bad == null;
    }

    private void refreshBaseline(Graph committed, SepsetMap map) throws InterruptedException {
        Set<Set<Node>> base = new HashSet<>();
        Graph mag = tryZhangMag(committed);
        if (mag == null) {
            baselineUncertified = base;
            return;
        }
        List<Node> ns = mag.getNodes();
        for (int i = 0; i < ns.size(); i++) {
            for (int j = i + 1; j < ns.size(); j++) {
                Node x = ns.get(i), z = ns.get(j);
                if (x.getNodeType() != NodeType.MEASURED || z.getNodeType() != NodeType.MEASURED) continue;
                if (mag.isAdjacentTo(x, z)) continue;
                if (!certified(mag, map, x, z, null)) base.add(Set.of(x, z));
            }
        }
        if (verbose && !base.isEmpty()) {
            TetradLogger.getInstance().log("\tI-map gate: committed state has " + base.size()
                    + " uncertified pair(s), exempt until repaired: " + base);
        }
        baselineUncertified = base;
    }

    private Set<Node> firstNewUncertifiedPair(Graph mag, SepsetMap trialMap, String what)
            throws InterruptedException {
        if (mag == null) {
            if (verbose) TetradLogger.getInstance().log("\tI-map gate: no Zhang MAG for " + what + "; vetoed.");
            return Set.of();
        }

        List<Node> ns = mag.getNodes();
        for (int i = 0; i < ns.size(); i++) {
            for (int j = i + 1; j < ns.size(); j++) {
                Node x = ns.get(i), z = ns.get(j);
                if (x.getNodeType() != NodeType.MEASURED || z.getNodeType() != NodeType.MEASURED) continue;
                if (mag.isAdjacentTo(x, z)) continue;
                Set<Node> key = Set.of(x, z);
                if (baselineUncertified.contains(key)) continue;
                if (!certified(mag, trialMap, x, z, what)) return key;
            }
        }
        return null;
    }

    private boolean certified(Graph mag, SepsetMap map, Node x, Node z, String what) throws InterruptedException {
        Set<Node> key = Set.of(x, z);
        List<Set<Node>> known = new ArrayList<>();
        Set<Node> entry = map.get(x, z);
        if (entry != null && !refutedSeeds.contains(key)) known.add(entry);
        List<Set<Node>> cached = knownSeparators.get(key);
        if (cached != null) known.addAll(cached);

        for (Set<Node> sep : known) {
            if (mag.paths().isMSeparatedFrom(x, z, sep, false)) return true;
        }

        Set<Node> d;
        try {
            RecursiveBlocking.BlockingResult b = RecursiveBlocking.blockPathsRecursively(
                    mag, x, z, Set.of(), Set.of(), -1, -1, -1, 1, true);
            d = (b == null || b.indeterminate() || b.blockingSet() == null) ? null : new HashSet<>(b.blockingSet());
        } catch (Exception e) {
            d = null;
        }

        if (d == null || !mag.paths().isMSeparatedFrom(x, z, d, false)) {
            imapIndeterminate++;
            if (verbose && what != null) {
                TetradLogger.getInstance().log("\tI-map gate: no separator found for " + x + ", " + z
                        + " in the candidate MAG (" + what + "); vetoed.");
            }
            return false;
        }

        d.removeIf(n -> n.getNodeType() == NodeType.LATENT);
        if (!judgedIndependent(x, z, d)) {
            if (verbose && what != null) {
                TetradLogger.getInstance().log("\tI-map gate: " + what + " asserts " + x + " _||_ " + z + " | " + d
                        + ", judged dependent; vetoed.");
            }
            return false;
        }

        knownSeparators.computeIfAbsent(key, k -> new ArrayList<>()).add(d);
        return true;
    }

    // ---- Score step internals ----

    private boolean scoreAccepts(Graph mag, String what) {
        Double trialScore = scoreMag(mag);

        if (trialScore == null) {
            scoreUnavailable++;
            if (verbose) TetradLogger.getInstance().log("\tScore step: score unavailable for " + what + "; rejected.");
            return false;
        }

        boolean accept = tiesAccepted
                ? trialScore >= currentScore - SCORE_EPSILON
                : trialScore > currentScore + SCORE_EPSILON;

        if (accept) {
            if (verbose) {
                TetradLogger.getInstance().log("\tScore step accepted " + what + ": " + currentScore + " -> "
                        + trialScore + ".");
            }
            currentScore = trialScore;
            scoreAccepted++;
            return true;
        }

        scoreRejected++;
        if (verbose) {
            TetradLogger.getInstance().log("\tScore step: legal, but score did not improve for " + what + " ("
                    + trialScore + " <= " + currentScore + "); reverted, trying the next candidate.");
        }
        return false;
    }

    private Double scoreMag(Graph mag) {
        if (mag == null) return null;

        for (Edge e : mag.getEdges()) {
            if (!Edges.isDirectedEdge(e) && !Edges.isBidirectedEdge(e)) return null;
        }

        try {
            double sc = magScorer.score(mag);
            return Double.isFinite(sc) ? sc : null;
        } catch (Exception e) {
            return null;
        }
    }
}
