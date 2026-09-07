///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
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
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.algcomparison.algorithm.oracle.pag;

import edu.cmu.tetrad.algcomparison.algorithm.*;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.AcceptsKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.TakesScoreWrapper;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.annotation.Experimental;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.test.CachedIndependenceQueries;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.test.MsepTest;
import edu.cmu.tetrad.search.utils.TsUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;


/**
 * This class represents the FCI Targeted Testing (FCIT) algorithm, which is variant of the *-FCI algorithm for learning
 * causal structures from observational data using the BOSS algorithm as an initial CPDAG and using all score-based
 * steps afterward.
 *
 * <p><b>Thoroughness settings.</b> The underlying search now exposes a set of knobs that trade completeness for speed.
 * Every one of them defaults to the full algorithm, so this wrapper leaves them alone; the {@code runSearch} method
 * carries a commented block showing each knob at its default with a note on what turning it down costs. The
 * output-identical optimizations (legality memo, negative sweep cache, independence-query cache) are always on and need
 * no configuration: the first two are unconditional inside the search, and the third is the
 * {@link CachedIndependenceQueries} wrap performed below.</p>
 *
 * <p>None of the thoroughness knobs are registered as {@link Params} constants, deliberately: turning one of them down
 * changes what the algorithm is guaranteed to find, and that is a decision to make in code with the reason recorded,
 * not a checkbox in a GUI. To promote one anyway, add a constant to {@code Params}, a description to the parameter
 * properties file, a line to {@link #getParameters()}, and replace the commented setter with a
 * {@code parameters.get...} call.</p>
 *
 * @author josephramsey
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "FCIT",
        command = "FCIT",
        algoType = AlgType.allow_latent_common_causes
)
@Bootstrapping
@Experimental
public class Fcit extends AbstractBootstrapAlgorithm implements Algorithm, TakesScoreWrapper, TakesIndependenceWrapper,
        AcceptsKnowledge, ReturnsBootstrapGraphs, TakesCovarianceMatrix, LatentStructureAlgorithm {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The independence test to use.
     */
    private IndependenceWrapper test;

    /**
     * The score to use.
     */
    private ScoreWrapper score;

    /**
     * The knowledge.
     */
    private Knowledge knowledge = new Knowledge();

    /**
     * This class represents a FCIT algorithm.
     *
     * <p>
     * The FCIT algorithm is a bootstrap algorithm that runs a search algorithm to find a graph structure based on a
     * given data set and parameters. It is a subclass of the Abstract BootstrapAlgorithm class and implements the
     * Algorithm interface.
     * </p>
     *
     * @see AbstractBootstrapAlgorithm
     * @see Algorithm
     */
    public Fcit() {
        // Used for reflection; do not delete.
    }

    /**
     * FCIT is a class that represents a FCIT algorithm.
     *
     * <p>
     * The FCIT algorithm is a bootstrap algorithm that runs a search algorithm to find a graph structure based on a
     * given data set and parameters. It is a subclass of the AbstractBootstrapAlgorithm class and implements the
     * Algorithm interface.
     * </p>
     *
     * @param test  The independence test to use.
     * @param score The score to use.
     * @see AbstractBootstrapAlgorithm
     * @see Algorithm
     */
    public Fcit(IndependenceWrapper test, ScoreWrapper score) {
        this.test = test;
        this.score = score;
    }

    /**
     * Runs the search algorithm to find a graph structure based on a given data model and parameters.
     *
     * @param dataModel  The data model to use for the search algorithm.
     * @param parameters The parameters to configure the search algorithm.
     * @return The resulting graph structure.
     * @throws IllegalArgumentException if the time lag is greater than 0 and the data model is not an instance of
     *                                  DataSet.
     */
    @Override
    public Graph runSearch(DataModel dataModel, Parameters parameters) throws InterruptedException {
        if (parameters.getInt(Params.TIME_LAG) > 0) {
            if (!(dataModel instanceof DataSet dataSet)) {
                throw new IllegalArgumentException("Expecting a dataset for time lagging.");
            }

            DataSet timeSeries = TsUtils.createLagData(dataSet, parameters.getInt(Params.TIME_LAG), knowledge);
            if (dataSet.getName() != null) {
                timeSeries.setName(dataSet.getName());
            }
            dataModel = timeSeries;
            knowledge = timeSeries.getKnowledge();
        }

        IndependenceTest test = this.test.getTest(dataModel, parameters);
        Score score = this.score.getScore(dataModel, parameters);

        if (test instanceof MsepTest) {
            if (parameters.getInt(Params.FCIT_STARTS_WITH) == 1) {
                throw new IllegalArgumentException("For d-separation oracle input, please use the GRaSP option.");
            }
        }

        // Tier-0 optimization (output-identical): the sweep retests the same (x, y, S) across the two proposal views,
        // across rounds, and in the completion layer. Caching those queries changes nothing about which candidates are
        // proposed or which separator is recorded.
        //
        // CAVEAT for tests with internal randomness (RCIT and friends): caching makes repeated queries CONSISTENT
        // rather than independently random, which is a behavior change -- arguably an improvement, since it makes the
        // run self-consistent, but not a no-op. If that is not wanted, drop the wrap for those tests.
        test = new CachedIndependenceQueries(test);

        edu.cmu.tetrad.search.FcitKeepKnowledgeOrientations fcit = new edu.cmu.tetrad.search.FcitKeepKnowledgeOrientations(test, score);

        // BOSS
        fcit.setUseDataOrder(parameters.getBoolean(Params.USE_DATA_ORDER));
        fcit.setNumStarts(parameters.getInt(Params.NUM_STARTS));
        fcit.setUseBes(parameters.getBoolean(Params.USE_BES));

        // FCIT
        fcit.setCompleteRuleSetUsed(parameters.getBoolean(Params.COMPLETE_RULE_SET_USED));
        fcit.setDepth(parameters.getInt(Params.DEPTH));
        fcit.setMaxDiscriminatingPathLength(parameters.getInt(Params.MAX_DISCRIMINATING_PATH_LENGTH));
        fcit.setRbRadius(parameters.getInt(Params.RB_RADIUS));
        fcit.setRecursiveDepth(parameters.getInt(Params.RECURSIVE_DEPTH));
        fcit.setExcludeSelectionBias(parameters.getBoolean(Params.EXCLUDE_SELECTION_BIAS));
//        fcit.setCompletionPolicy(edu.cmu.tetrad.fcit.Fcit.CompletionPolicy.OFF);

//        fcit.setUseMarkFlipEscalation(true);
//        fcit.setCompletionPolicy(edu.cmu.tetrad.search.Fcit.CompletionPolicy.OFF);  // or POOL_ONLY

        fcit.setUseMarkFlipEscalation(true);

        // Wall-clock per-pair budget. NOTE: this makes runs NONDETERMINISTIC -- a machine under load explores a
        // different candidate family and can return a different graph. For enumeration harnesses and any experiment
        // that must reproduce, set TEST_TIMEOUT to -1 and use setMaxTestsPerPair(...) below instead. A wall-clock
        // budget also degrades the negative sweep cache, since budget-truncated failures are indeterminate and are
        // deliberately not cached.
        fcit.setTimeout(parameters.getLong(Params.TEST_TIMEOUT));

        fcit.setReplicatingGraph(parameters.getBoolean(Params.TIME_LAG_REPLICATING_GRAPH));

        if (parameters.getInt(Params.FCIT_STARTS_WITH) == 1) {
            fcit.setStartWith(edu.cmu.tetrad.search.FcitKeepKnowledgeOrientations.START_WITH.BOSS);
        } else if (parameters.getInt(Params.FCIT_STARTS_WITH) == 2) {
            fcit.setStartWith(edu.cmu.tetrad.search.FcitKeepKnowledgeOrientations.START_WITH.GRASP);
        } else if (parameters.getInt(Params.FCIT_STARTS_WITH) == 3) {
            fcit.setStartWith(edu.cmu.tetrad.search.FcitKeepKnowledgeOrientations.START_WITH.SP);
        } else if (parameters.getInt(Params.FCIT_STARTS_WITH) == 4) {
            fcit.setStartWith(edu.cmu.tetrad.search.FcitKeepKnowledgeOrientations.START_WITH.COMPLETE_GRAPH);
        } else {
            throw new IllegalArgumentException("Unknown start with option: " + parameters.getInt(Params.FCIT_STARTS_WITH));
        }

        // =====================================================================================================
        // THOROUGHNESS SETTINGS
        //
        // Every line below is COMMENTED OUT and shows the value the fcit already uses by default, so the code as
        // written runs the full algorithm. Uncommenting any of them trades completeness for speed. The knobs are
        // listed in rough order of value-per-fidelity-lost: take them from the top.
        //
        // Anything uncommented here invalidates the exhaustive-enumeration certification, so re-run PKE12 before
        // trusting a tuned configuration on anything that matters.
        // =====================================================================================================

        // --- Sweep enumeration caps -------------------------------------------------------------------------
        //
        // The sweep's outer layer is a powerset over the ambiguous nodes (the not-followed enumeration), and it is
        // the only exponential on the common path: 2^|A| recursive-blocking calls per edge per view. Capping it is
        // by far the largest speedup available short of changing what gets recorded. Withholding many nodes from
        // traversal at once is rarely what finds a separator, so a cap of 2-3 keeps most of the reach.
        //
        // Cost: the family is no longer the one Def. rb-step specifies, and the coverage argument over labelings of
        // B no longer applies -- missed separators fall through to the completion layer, which is slower, so an
        // over-aggressive cap can be a net loss. Measure.
        //
//         fcit.setSweepNfMaxSize(3);        // default -1 (unlimited)
//         fcit.setSweepAddMaxSize(2);       // default -1 (unlimited)
//         fcit.setSweepRemoveMaxSize(-1);   // default -1 (unlimited)

        // --- Second proposal view ---------------------------------------------------------------------------
        //
        // Halves sweep cost by searching only the marked graph, never the bare skeleton. Forfeits Rem.
        // blind-proposal: separators hidden by a wrong interim mark become unreachable except through the
        // completion layer.
        //
        // fcit.setUseBlindView(false);      // default true

        // --- Deterministic per-pair budget ------------------------------------------------------------------
        //
        // Reproducible replacement for TEST_TIMEOUT: caps independence tests per pair across both views.
        // Exhausting it yields an INDETERMINATE sweep, not a not-found verdict, so the final report still
        // distinguishes "no phantom confirmed within budget" from "no phantom exists". Use this, not the
        // wall-clock timeout, in enumeration runs.
        //
        // fcit.setMaxTestsPerPair(20000L);  // default -1 (unlimited)

        // --- Completion layer -------------------------------------------------------------------------------
        //
        // This is the layer that makes the oracle guarantee unconditional (Prop. completion). Turn it down LAST.
        //
        //   FULL      possible-D-SEP stage plus all-nodes escalation. Default. The only setting under which
        //             every spurious edge is confirmable with no coverage hypothesis.
        //   POOL_ONLY drops the escalation. Sound, and complete unless an unsound TAIL hides a pool member --
        //             a case not yet observed at six variables, so this is cheap insurance to give up.
        //   OFF       reverts to the recursive-blocking family alone, i.e. to the out-of-B coverage condition,
        //             which is REFUTED at six observed variables. Use only to measure the gap.
        //
//         fcit.setCompletionPolicy(edu.cmu.tetrad.fcit.Fcit.CompletionPolicy.POOL_ONLY);  // default FULL
//         fcit.setCompletionMaxSubsetSize(4);   // default -1 (DEPTH still applies)

        // --- Final detection scan ---------------------------------------------------------------------------
        //
        // Skips the discriminating-path scan and the discharge it feeds. Confirmed-spurious legs that neither
        // single-edge removal nor the saturating pass caught are then neither reported nor removed. Reasonable in
        // a benchmark; wrong in a harness.
        //
//         fcit.setDetectionEnabled(false);  // default true

        // --- Small-subset shortcut pass (NOT output-identical) ----------------------------------------------
        //
        // Tests subsets of the common neighborhood by increasing size before invoking recursive blocking. Most
        // separators are small and live there, so this answers the majority of pairs in a handful of tests. It
        // records SMALLER separators, which changes orientations and therefore trajectories -- PKE12 must be
        // re-run to bless any nonzero value.
        //
        // Worth running as an experiment rather than adopting blind: per Rem. displacement, minimal separators may
        // be less likely to contain the far endpoint that arms R1's displacement, so this could lower the
        // completion-layer firing rate as well as the runtime. Compare violation counts AND "PDS COMPLETION" log
        // counts against a default run.
        //
        // fcit.setQuickSubsetDepth(2);      // default -1 (off)

        // --- Recursive-blocking sweep, i.e. completion-only mode (NOT output-identical) ---------------------
        //
        // Turns the sweep off entirely. The completion layer then becomes the sole source of separators: it
        // records them in foundSepsets, the legality-gated removal loop and the saturating pass discharge them
        // from there, and no recursive blocking runs anywhere. What is left is FCI-style subset enumeration with
        // a legality gate and a saturating close -- unconditionally complete at the oracle (Prop. completion),
        // since the guarantee never depended on the sweep in the first place.
        //
        // Counterintuitively this may be FASTER at small p, not slower: a pair admits at most 2^(p-2) candidate
        // sets, while the sweep runs a powerset of not-followed sets with a recursive-blocking call apiece. At
        // six observed variables that is 16 candidates against a considerably larger family, so completion-only
        // is a serious contender for enumeration harnesses. The trade inverts sharply as p grows, which is the
        // whole reason recursive blocking exists and why the sweep is the default.
        //
        // Recorded separators become minimal, since completion enumerates by increasing size, so orientations
        // and trajectories change and PKE12 must be re-run to bless it -- the same caveat as the shortcut pass
        // above, and the same reason to be curious: minimal separators may lower the displacement firing rate.
        //
        // Do not combine with CompletionPolicy.OFF: that configuration has no separator-finding machinery left
        // and will remove no edges at all.
        //
        // fcit.setUseRecursiveBlockingSweep(false);  // default true

        // --- Legality memo audit ----------------------------------------------------------------------------
        //
        // The legality memo assumes the from-scratch reorientation is a deterministic function of
        // (skeleton, sepsets). Audit mode recomputes every memo hit and logs mismatches, which is how residual
        // hash-order dependence in FciOrient would show itself. Slower than either memo-on or memo-off; run once
        // after touching the orientation code, then turn it back off.
        //
        // fcit.setAuditLegalityMemo(true);  // default false

        // =====================================================================================================

        // General
        fcit.setVerbose(parameters.getBoolean(Params.VERBOSE));
        fcit.setLogFinalOrientations(parameters.getBoolean(Params.LOG_FINAL_ORIENTATIONS));
        fcit.setKnowledge(this.knowledge);

        return fcit.search();
    }

    /**
     * Retrieves a comparison graph by transforming a true directed graph into a partially directed graph (PAG).
     *
     * @param graph The true directed graph, if there is one.
     * @return The comparison graph.
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        return GraphTransforms.dagToPag(graph, false);
    }

    /**
     * Returns a short, one-line description of this algorithm. The description is generated by concatenating the
     * descriptions of the test and score objects associated with this algorithm.
     *
     * @return The description of this algorithm.
     */
    @Override
    public String getDescription() {
        return "FCIT (FCI Targeted Testing) using " + this.test.getDescription() + " and " + this.score.getDescription();
    }

    /**
     * Retrieves the data type required by the search algorithm.
     *
     * @return The data type required by the search algorithm.
     */
    @Override
    public DataType getDataType() {
        return this.score.getDataType();
    }

    /**
     * Retrieves the list of parameters used by the algorithm.
     *
     * <p>The thoroughness knobs of {@link #runSearch} are intentionally absent: each of them changes what the
     * algorithm is guaranteed to find, which is a decision that belongs in code next to its justification rather than
     * in a settings dialog. To expose one, add a {@link Params} constant, a description in the parameter properties
     * file, a line here, and a {@code parameters.get...} call in place of the commented setter.</p>
     *
     * @return The list of parameters used by the algorithm.
     */
    @Override
    public List<String> getParameters() {
        List<String> params = new ArrayList<>();

        // BOSS
        params.add(Params.USE_BES);
        params.add(Params.USE_DATA_ORDER);
        params.add(Params.NUM_STARTS);

        // FCIT
        params.add(Params.FCIT_STARTS_WITH);
        params.add(Params.COMPLETE_RULE_SET_USED);
        params.add(Params.DEPTH);
        params.add(Params.MAX_DISCRIMINATING_PATH_LENGTH);
        params.add(Params.RB_RADIUS);
        params.add(Params.RECURSIVE_DEPTH);
        params.add(Params.EXCLUDE_SELECTION_BIAS);
        params.add(Params.TEST_TIMEOUT);

        // General
        params.add(Params.TIME_LAG);
        params.add(Params.TIME_LAG_REPLICATING_GRAPH);

        params.add(Params.VERBOSE);
        params.add(Params.LOG_FINAL_ORIENTATIONS);

        return params;
    }

    /**
     * Retrieves the knowledge object associated with this method.
     *
     * @return The knowledge object.
     */
    @Override
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * Sets the knowledge object associated with this method.
     *
     * @param knowledge the knowledge object to be set
     */
    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }

    /**
     * Retrieves the ScoreWrapper object associated with this method.
     *
     * @return The ScoreWrapper object associated with this method.
     */
    @Override
    public ScoreWrapper getScoreWrapper() {
        return this.score;
    }

    /**
     * Sets the score wrapper for the algorithm.
     *
     * @param score the score wrapper.
     */
    @Override
    public void setScoreWrapper(ScoreWrapper score) {
        this.score = score;
    }

    @Override
    public IndependenceWrapper getIndependenceWrapper() {
        return test;
    }

    @Override
    public void setIndependenceWrapper(IndependenceWrapper independenceWrapper) {
        this.test = independenceWrapper;
    }
}