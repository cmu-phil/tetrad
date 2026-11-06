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

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataTransforms;
import edu.cmu.tetrad.data.EmCovarianceEstimator;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.data.missing.CensoredMvnCovarianceEstimator;
import edu.cmu.tetrad.data.missing.MissingDataAudit;
import edu.cmu.tetrad.data.missing.MissingDataPolicy;
import edu.cmu.tetrad.data.missing.MissingDataSpec;
import edu.cmu.tetrad.data.missing.MissingDataUtils;
import edu.cmu.tetrad.data.missing.PairwiseCovarianceEstimator;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.rlcd.Chi2RankTest;
import edu.cmu.tetrad.search.rlcd.EmCovarianceRankTest;
import edu.cmu.tetrad.search.rlcd.LatentGroups;
import edu.cmu.tetrad.search.rlcd.PooledRankTest;
import edu.cmu.tetrad.search.rlcd.RankTester;
import edu.cmu.tetrad.search.rlcd.StratifiedChi2RankTest;
import edu.cmu.tetrad.search.rlcd.TestwiseDeletionRankTest;
import edu.cmu.tetrad.search.rlcd.RlcdClusterSearch;
import edu.cmu.tetrad.search.rlcd.TierPrecedence;
import edu.cmu.tetrad.search.rlcd.TwoIndicatorTest;
import edu.cmu.tetrad.search.blocks.BlockSpec;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.TrekSeparationBlocksIndependence;
import edu.cmu.tetrad.search.utils.MeekRules;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.*;

/**
 * Rank-based Latent Causal Discovery (RLCD), from Dong, Huang, Ng, Song, Zheng, Jin, Legaspi, Spirtes, and Zhang,
 * "A versatile causal discovery framework to allow causally-related hidden variables," ICLR 2024. This is a
 * translation of the implementation released in causal-learn
 * (<code>causallearn.search.HiddenCausal.RLCD</code>), for linear models with possibly causally related latent
 * variables.
 * <p>
 * The method has two stages. Stage 1 runs an ordinary score-based search over the observed variables (BOSS by
 * default here; GES in causal-learn) and partitions the variables into groups: maximal cliques of the resulting
 * skeleton with at least <code>partitionCliqueThreshold</code> members are merged when they share at least two
 * variables, and merged groups with at least four variables become partitions. Stage 2 runs the rank-deficiency
 * cluster search of {@link RlcdClusterSearch} within each partition, introducing latent variables. The output graph
 * is the stage-1 graph with, inside each partition, the observed-observed edges replaced by what stage 2 reports,
 * plus the latent variables and their edges.
 * <p>
 * Output conventions follow causal-learn: a directed edge for each parent-child relation found; an undirected edge
 * where the stage-1 graph had an undirected edge between variables not inside a common partition, and between
 * latent roots chained by the finish-up step; and no edge between
 * co-members of a multi-variable atomic cover (a rank-k cluster with k &gt; 1 is placed under k latents that are not
 * connected to one another). Latent nodes are of type {@link NodeType#LATENT} and named L1, L2, ...
 * <p>
 * Known limitations inherited from the released code: candidate sets with no observed member are never rank-tested
 * (see {@link RlcdClusterSearch}); the finish-up step chains leftover latent roots in an order that is arbitrary in
 * the Python and alphabetical here, without testing whether consecutive roots are dependent; and stage-1
 * edges within a partition in which at least one latent was introduced are dropped whether or not stage 2 explains
 * them, while a partition with no latent keeps its stage-1 edges unchanged.
 * <p>
 * The translation was checked against the causal-learn code on nine simulated structures (pure clusters, a latent
 * chain, a rank-2 cluster, an observed non-sink, an impure indicator, an observed DAG with no latents) by feeding both
 * the same stage-1 graph; all agreed exactly up to relabeling of latents and the finish-step chain order.
 * <p>
 * Deliberate differences from the released code. The edges of the finish-step chain are undirected here, where
 * the Python directs them in an order that depends on its hash seed. And the directions of edges between latents
 * are decided by rank tests rather than by the order in which stage 2 happened to build the covers: the skeleton
 * among the latent covers is kept, unshielded colliders are found by a PC-style search for separating sets with the
 * rank test as the conditional-independence oracle, and the Meek rules are closed over, so the latent part of the
 * output is a CPDAG (see {@link #orientLatentEdgesByRankTests}). The Python reports the direction in which stage 2
 * built each edge. And stage 2 is run on a partition only if the paper's condition for the existence of a latent
 * holds somewhere in it (see {@link #setLatentGate(boolean)}). See also {@link RlcdClusterSearch} for the
 * differences inside stage 2.
 *
 * @author josephramsey (translation)
 * @see RlcdClusterSearch
 * @see Chi2RankTest
 */
public class Rlcd {

    /**
     * Stage-1 search methods.
     */
    public enum Stage1Method {
        /**
         * BOSS with the SEM BIC score.
         */
        BOSS,
        /**
         * FGES with the SEM BIC score (closest to the GES stage of causal-learn).
         */
        FGES,
        /**
         * No stage-1 search: the complete graph over the observed variables is used, so all variables form one
         * partition and the stage-2 rank search runs unconstrained. This matches the reference implementation's
         * "all" setting. It gives up the pruning that makes RLCD tractable as the number of variables grows, so it
         * is an option for small variable sets, not a default; stage-1 knowledge is not consulted, though knowledge
         * still restricts non-sinks and restores required edges as usual.
         */
        NONE
    }

    private final DataSet dataSet;
    /**
     * The imputations searched over as one; just the data set when there is only one.
     */
    private final List<DataSet> imputations;
    private final List<Node> variables;
    private RankTester rankTester;
    /**
     * When non-null, stage 1 is scored on this covariance matrix instead of the data's; set by
     * {@link #setStratifiedRankTest}.
     */
    private CovarianceMatrix stage1CovarianceOverride = null;
    /**
     * True in testwise-deletion mode. Stage 1's covariance is then built from the listwise-complete rows, but only
     * if stage 1 is actually run: with a supplied stage-1 graph, or Stage1Method.NONE, it is never needed, and on
     * many variables at any real missingness rate it could not be built anyway.
     */
    private boolean testwiseDeletion = false;
    private MissingDataSpec missingDataSpec = null;
    private double alpha = 0.01;
    private double[] alphaByRank = null;
    private int maxK = 3;
    /**
     * The most rank tests {@link #latentIndicated} makes on one partition before giving up and letting stage 2 run.
     */
    private static final int MAX_GATE_TESTS = 20000;
    private boolean latentGate = true;
    private boolean resolveTwoIndicatorLatents = false;
    private boolean knowledgeInStage1 = true;
    private boolean qualifyObservedCauses = true;
    private boolean observedExplanationCheck = false;
    private double observedExplanationAlpha = 0.05;
    private int explainedAway = 0;
    private boolean allowNonLeafX = true;
    private boolean unfoldCovers = true;
    private boolean checkV = true;
    private int partitionCliqueThreshold = 3;
    private Stage1Method stage1Method = Stage1Method.BOSS;
    private double penaltyDiscount = 1.0;
    private Graph stage1Graph = null;
    private long seed = -1;
    private boolean verbose = false;
    private Knowledge knowledge = new Knowledge();

    private Graph stage1Result;
    private List<List<Node>> partition;
    private List<LatentGroups> latentGroups;
    private int[][] adjacency;
    private List<String> allVariableNames;

    /**
     * Constructs the search over a continuous data set.
     *
     * @param dataSet the data.
     */
    public Rlcd(DataSet dataSet) {
        if (dataSet == null) throw new NullPointerException("Data set is null.");
        if (!dataSet.isContinuous()) throw new IllegalArgumentException("RLCD requires continuous data.");
        this.dataSet = dataSet;
        this.imputations = List.of(dataSet);
        this.variables = new ArrayList<>(dataSet.getVariables());
    }

    /**
     * Constructs ONE search over several imputations of a data set with missing values. Stage 1 is run on the
     * average of the imputations' covariance matrices, and the stage-2 rank tests are pooled over the imputations
     * by {@link PooledRankTest}, so that every decision is made once, from all of the imputations. With a single
     * data set this is the same as {@link #Rlcd(DataSet)}.
     * <p>
     * The average covariance matrix is a consistent estimate of the covariance matrix when the values are missing
     * at random and the imputation model is right, but stage 1 scores it at the full sample size, which overstates
     * the information in it by the fraction that was imputed.
     *
     * @param imputations the imputed data sets: continuous, with the same variables and the same number of rows.
     *                    One with the variables in a different order is put in the order of the first.
     */
    public Rlcd(List<DataSet> imputations) {
        if (imputations == null || imputations.isEmpty()) {
            throw new IllegalArgumentException("At least one data set is required.");
        }

        DataSet first = imputations.getFirst();
        if (first == null) throw new NullPointerException("Data set is null.");
        imputations = DataTransforms.alignVariableOrder(imputations, first.getVariableNames());

        for (DataSet dataSet : imputations) {
            if (dataSet == null) throw new NullPointerException("Data set is null.");
            if (!dataSet.isContinuous()) throw new IllegalArgumentException("RLCD requires continuous data.");

            if (!dataSet.getVariableNames().equals(first.getVariableNames())
                || dataSet.getNumRows() != first.getNumRows()) {
                throw new IllegalArgumentException("To be pooled as imputations, the data sets must have the same "
                                                   + "variables and the same number of rows.");
            }
        }

        this.dataSet = first;
        this.imputations = new ArrayList<>(imputations);
        this.variables = new ArrayList<>(first.getVariables());
    }

    /**
     * Sets the rank test. The default is {@link Chi2RankTest} over the data.
     *
     * @param rankTester the test.
     */
    public void setRankTester(RankTester rankTester) {
        this.rankTester = rankTester;
    }

    /**
     * Puts the search into testwise-deletion mode. Equivalent to {@code setMissingDataSpec(MissingDataSpec.testwise())}
     * when on, and to clearing the spec when off; see {@link #setMissingDataSpec(MissingDataSpec)}.
     *
     * @param on true to switch the mode on.
     */
    public void setTestwiseDeletion(boolean on) {
        setMissingDataSpec(on ? MissingDataSpec.testwise() : null);
    }

    /**
     * Tells the search how to treat missing values in a single data set, by Tetrad's standard
     * {@link MissingDataSpec}. Pass null to clear a previous setting. The policies, and what each does to the two
     * stages of RLCD:
     * <ul>
     *     <li><b>EM_COVARIANCE</b> (recommended): one covariance matrix is estimated from all of the incomplete rows
     *     by EM ({@link EmCovarianceEstimator}), which is consistent under MAR and positive semidefinite. Stage 1
     *     scores it, with the spec's effective sample size; every stage-2 rank test runs on it through
     *     {@link EmCovarianceRankTest}, with a sample size chosen per query from the pairwise-complete counts of
     *     the columns that query mentions, by the spec's {@link MissingDataSpec.EffectiveSampleSizeMode}. The mean
     *     pairwise rule is recommended; the default FULL_N overstates the information and loses clusters. One
     *     matrix serves the whole search, so stage 1 and every stage-2 decision see the same estimate.</li>
     *     <li><b>PAIRWISE_COVARIANCE</b>: as EM_COVARIANCE but with the available-case matrix of
     *     {@link PairwiseCovarianceEstimator}, which need not be positive semidefinite. Stage 1's score can misbehave
     *     on such a matrix; supply a stage-1 graph or use {@link Stage1Method#NONE} if it does.</li>
     *     <li><b>TESTWISE</b>: each stage-2 rank query is answered from the rows complete on that query's columns, by
     *     {@link TestwiseDeletionRankTest}. Invents nothing, but a query over u columns keeps only about
     *     (1 − rate)^u of the rows, so it is for few variables or low missingness. When stage 1 is run in this
     *     mode it runs on the <i>listwise-complete</i> rows, the only choice here guaranteed positive semidefinite;
     *     with many variables there may be none, in which case supply a stage-1 graph or use
     *     {@link Stage1Method#NONE} and the listwise matrix is never built.</li>
     *     <li><b>LISTWISE</b>: both stages run on the rows complete in every column.</li>
     *     <li><b>MULTIPLE_IMPUTATION</b>: not handled here. Impute, and construct the search over the imputations
     *     with {@link #Rlcd(List)}, which pools the rank tests by the D2 rule. Be aware that D2 at a handful of
     *     imputations accepts rank deficiency whenever the statistic varies much across imputations, which on many
     *     variables returns one latent over nearly everything; see the simulation notes.</li>
     *     <li><b>FAIL</b>: an exception if the data have any missing value.</li>
     * </ul>
     * All of these are decided here, before stage 1, from the single data set the search was constructed over.
     *
     * @param spec the spec, or null.
     * @throws IllegalArgumentException if called on a search constructed over several imputations, or if the policy
     *                                 is MULTIPLE_IMPUTATION (use the other constructor), or FAIL with missing
     *                                 values present.
     */
    public void setMissingDataSpec(MissingDataSpec spec) {
        this.rankTester = null;
        this.stage1CovarianceOverride = null;
        this.testwiseDeletion = false;
        this.missingDataSpec = spec;
        if (spec == null) return;

        if (imputations.size() > 1) {
            throw new IllegalArgumentException("A missing-data policy applies to a single data set with missing "
                                               + "values, not to a set of imputations; use one constructor or the "
                                               + "other.");
        }

        MissingDataAudit audit = new MissingDataAudit(dataSet);
        int n = dataSet.getNumRows();

        switch (spec.getPolicy()) {
            case FAIL -> {
                if (audit.anyMissing()) {
                    throw new IllegalArgumentException("RLCD: the data have missing values and the policy is to "
                                                       + "fail. " + TestwiseDeletionRankTest.describe(dataSet));
                }
            }
            case LISTWISE -> {
                DataSet complete = MissingDataUtils.listwiseDelete(dataSet);
                if (complete.getNumRows() < dataSet.getNumColumns() + 2) {
                    throw new IllegalArgumentException("RLCD: listwise deletion leaves " + complete.getNumRows()
                                                       + " complete rows of " + n + ", too few. "
                                                       + TestwiseDeletionRankTest.describe(dataSet));
                }
                this.rankTester = new Chi2RankTest(complete);
                this.stage1CovarianceOverride = new CovarianceMatrix(complete);
                TetradLogger.getInstance().log("RLCD listwise: " + complete.getNumRows() + " complete rows of " + n);
            }
            case TESTWISE -> {
                this.rankTester = new TestwiseDeletionRankTest(dataSet);
                this.testwiseDeletion = true;
            }
            case EM_COVARIANCE, PAIRWISE_COVARIANCE -> {
                ICovarianceMatrix cov;
                int[][] counts;
                if (spec.getPolicy() == MissingDataPolicy.EM_COVARIANCE) {
                    EmCovarianceEstimator estimator = new EmCovarianceEstimator(dataSet);
                    estimator.setRidge(spec.getEmRidge());
                    estimator.setTolerance(spec.getEmTolerance());
                    estimator.setMaxIterations(spec.getEmMaxIterations());
                    cov = estimator.estimate();
                    counts = estimator.getPairwiseCounts();
                    TetradLogger.getInstance().log("RLCD EM covariance: " + estimator.getNumIterations()
                                                   + " iterations, converged " + estimator.isConverged()
                                                   + ", min pairwise count " + estimator.getMinPairwiseCount());
                } else {
                    cov = PairwiseCovarianceEstimator.estimate(dataSet);
                    counts = audit.getPairwiseCompleteCounts();
                }
                this.rankTester = new EmCovarianceRankTest(cov, counts, n, spec.getEssMode(), 1.0);
                int ess = MissingDataUtils.effectiveSampleSize(dataSet, spec);
                this.stage1CovarianceOverride = new CovarianceMatrix(variables, cov.getMatrix(), ess < 0 ? n : ess);
            }
            case MULTIPLE_IMPUTATION -> throw new IllegalArgumentException("RLCD: for multiple imputation, impute "
                                                                           + "and construct the search over the "
                                                                           + "imputations with Rlcd(List<DataSet>).");
        }
    }

    /**
     * The missing-data spec in force, or null.
     *
     * @return the spec.
     */
    public MissingDataSpec getMissingDataSpec() {
        return missingDataSpec;
    }

    /**
     * Runs both stages on a covariance matrix estimated with censored values treated as censored -- a grade at the
     * top of its scale is at least that, not missing and not exactly that -- by
     * {@link CensoredMvnCovarianceEstimator}. Stage 1 scores the estimate; every stage-2 rank test runs on it through
     * {@link EmCovarianceRankTest} with the given sample-size rule over the pairwise <i>uncensored</i> counts. This is
     * the treatment for ceilings and floors, which no missing-value policy handles: a value missing or clipped because
     * of its own size is missing-not-at-random, and the MAR estimate of {@link MissingDataSpec#emCovariance()} is
     * biased toward attenuated correlations under it. Under Gaussian errors the censored estimate is close to exact;
     * under skewed errors the correction is partial.
     *
     * @param estimator the estimator, constructed over this search's data set with its bounds set.
     * @param essMode   how a query's sample size is chosen from the pairwise uncensored counts; mean pairwise
     *                  recommended.
     * @throws IllegalArgumentException if called on a search constructed over several imputations.
     */
    public void setCensoredCovariance(CensoredMvnCovarianceEstimator estimator,
                                      MissingDataSpec.EffectiveSampleSizeMode essMode) {
        if (imputations.size() > 1) {
            throw new IllegalArgumentException("A censored covariance applies to a single data set, not to a set "
                                               + "of imputations.");
        }
        this.missingDataSpec = null;
        this.testwiseDeletion = false;
        ICovarianceMatrix cov = estimator.estimate();
        int[][] counts = estimator.getPairwiseCounts();
        int n = dataSet.getNumRows();
        this.rankTester = new EmCovarianceRankTest(cov, counts, n, essMode, 1.0);
        double sum = 0;
        int pairs = 0;
        for (int a = 0; a < counts.length; a++) {
            for (int b = a + 1; b < counts.length; b++) {
                sum += counts[a][b];
                pairs++;
            }
        }
        int ess = essMode == MissingDataSpec.EffectiveSampleSizeMode.FULL_N || pairs == 0 ? n
                : (int) Math.round(sum / pairs);
        this.stage1CovarianceOverride = new CovarianceMatrix(variables, cov.getMatrix(), ess);
        TetradLogger.getInstance().log("RLCD censored covariance: " + estimator.getCensoredCells()
                                       + " censored cells, " + estimator.getMissingCells() + " missing cells.");
    }

    /**
     * The covariance matrix of the rows with no missing value in any column, for stage 1 under testwise deletion.
     */
    private CovarianceMatrix listwiseCompleteCovariance() {
        int n = dataSet.getNumRows(), p = dataSet.getNumColumns();
        List<Integer> rows = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            boolean ok = true;
            for (int j = 0; j < p; j++) {
                if (Double.isNaN(dataSet.getDouble(i, j))) {
                    ok = false;
                    break;
                }
            }
            if (ok) rows.add(i);
        }
        int m = rows.size();
        if (m < p + 2) {
            throw new IllegalArgumentException("Testwise deletion needs a stage-1 covariance matrix, and these data "
                                               + "have only " + m + " rows complete in all " + p + " columns. Set "
                                               + "Stage1Method.NONE or supply a stage-1 graph. Missingness: "
                                               + TestwiseDeletionRankTest.describe(dataSet));
        }
        double[] mean = new double[p];
        for (int i : rows) for (int j = 0; j < p; j++) mean[j] += dataSet.getDouble(i, j);
        for (int j = 0; j < p; j++) mean[j] /= m;

        Matrix cov = new Matrix(p, p);
        for (int a = 0; a < p; a++) {
            for (int b = a; b < p; b++) {
                double s = 0.0;
                for (int i : rows) {
                    s += (dataSet.getDouble(i, a) - mean[a]) * (dataSet.getDouble(i, b) - mean[b]);
                }
                double v = s / (m - 1.0);
                cov.set(a, b, v);
                cov.set(b, a, v);
            }
        }
        TetradLogger.getInstance().log("RLCD testwise deletion: stage-1 covariance from " + m + " complete rows of "
                                       + n + ".");
        return new CovarianceMatrix(dataSet.getVariables(), cov, m);
    }

    /**
     * Puts the search into stratified (domain-pooled) mode for data gathered in several domains -- batches, sites,
     * cohorts -- whose causal structure is shared but whose linear coefficients, means, and noise variances may
     * differ by domain; the Phase 1 upgrade of LCD-NOD (Dong et al., ICML 2026) applied to RLCD. Two things change
     * at once. Every rank decision is made by {@link StratifiedChi2RankTest}, pooling the Lawley statistic over the
     * domains, which carries the paper's guarantee. And stage 1 is scored on the within-domain pooled covariance
     * (the data recentered at each domain's means) instead of the raw covariance of the concatenated data, whose
     * between-domain mean shifts would otherwise dominate; that part is exactly right when only the means shift and
     * a heuristic initialization otherwise, as the helper's javadoc says.
     * <p>
     * The domains must have the same variables, in the same order, as the data set this search was constructed
     * with -- construct the search on the concatenation of the domain data sets, without the domain column.
     * Calling {@link #setRankTester} with the stratified test instead would stratify the rank stages but leave
     * stage 1 on the raw concatenated covariance, which is almost never wanted.
     *
     * @param stratified the stratified rank test, or null to return to the ordinary single-sample behavior.
     */
    public void setStratifiedRankTest(StratifiedChi2RankTest stratified) {
        if (stratified == null) {
            this.rankTester = null;
            this.stage1CovarianceOverride = null;
            return;
        }

        List<String> domainNames = stratified.getDomains().getFirst().getVariableNames();
        List<String> myNames = this.dataSet.getVariableNames();
        if (!domainNames.equals(myNames)) {
            throw new IllegalArgumentException("The stratified test's domains have variables " + domainNames
                                               + ", but this search was constructed over " + myNames + "; they must "
                                               + "be the same variables in the same order.");
        }

        this.rankTester = stratified;
        this.stage1CovarianceOverride = stratified.withinDomainPooledCovariance();
    }

    /**
     * Sets the significance level used for all rank tests (default 0.01, as in causal-learn).
     *
     * @param alpha the level.
     */
    public void setAlpha(double alpha) {
        if (alpha <= 0 || alpha >= 1) throw new IllegalArgumentException("alpha must be in (0, 1).");
        this.alpha = alpha;
    }

    /**
     * Optionally sets a different significance level for each hypothesized rank: entry k is used when testing rank
     * ≤ k. Ranks beyond the array fall back to the single alpha. Pass null to use the single alpha throughout.
     *
     * @param alphaByRank the levels by rank.
     */
    public void setAlphaByRank(double[] alphaByRank) {
        this.alphaByRank = alphaByRank == null ? null : alphaByRank.clone();
    }

    /**
     * Sets whether a partition is searched for latents only if its rank constraints call for one (default true).
     * Before stage 2 is run on a partition, the condition of Theorems 5 and 9 of Dong et al. for the existence of
     * a latent is checked on it; where no latent is indicated, the partition keeps its stage-1 edges. Without the
     * check, stage 2 run on a dense group of observed variables with no latent behind them can report a latent that
     * stands in for structure among the observed variables. The Python has no such check.
     *
     * @param latentGate true to make the check.
     */
    public void setLatentGate(boolean latentGate) {
        this.latentGate = latentGate;
    }

    /**
     * Sets whether to try to replace two-indicator latents by direct edges, using non-Gaussianity or, failing that,
     * the temporal tiers of the background knowledge (default false).
     * Rank constraints cannot tell L --&gt; {X1, X2} with X1 --&gt; X2 from a second latent over {X1, X2}, and RLCD
     * reports the second latent. With this set, each latent of the output that has exactly two observed children,
     * no observed parent and exactly one latent neighbor M, where M has at least three pure observed children, is
     * tested by {@link TwoIndicatorTest} with M's pure children as references. If the test shows X1 --&gt; X2 (or
     * X2 --&gt; X1), and knowledge allows it, the latent is removed and replaced by M --&gt; X1, M --&gt; X2 and the
     * edge; otherwise the latent is left as it is. The test is conservative (it needs positive evidence for the
     * edge) and needs skewed data; with Gaussian data nothing changes. Not done under a stratified rank test, whose
     * pooled domains make the data non-Gaussian whatever the model. With several imputations, the edge is taken
     * only if every imputation shows it.
     * <p>
     * When the test is unavailable (no reference cluster: the latent has no latent neighbor, as a latent standing for
     * one link of a chain of observed variables has none) or undecided, and the knowledge's tiers order the two
     * indicators, the edge is drawn in the tier direction instead. A two-indicator latent and a direct edge between
     * its indicators are rank-equivalent, so this changes the representation of the same covariance to the more
     * parsimonious one, not the fit. In that no-neighbor case the indicators may have observed parents of their own
     * (which they keep) but not latent ones. {@link #getAdjacency()} and {@link #getLatentGroups()} describe the
     * search before this step.
     *
     * @param resolve true to try.
     */
    public void setResolveTwoIndicatorLatents(boolean resolve) {
        this.resolveTwoIndicatorLatents = resolve;
    }

    /**
     * Sets whether background knowledge is given to stage 1 as well as stage 2 (default true). Stage 2 always
     * honours it: no non-sink forbidden as a parent, tier precedence in cluster placement, orientation of what the
     * tiers order. Stage 1 is the question. Under tier constraints BOSS returns a denser skeleton (about eight more
     * edges on thirty variables, October 2026), the clique merge then puts more variables in one partition, and
     * stage 2's rank tests over a larger set with weak correlations fail to reject rank one across it -- the
     * fourteen-variable cluster seen on the chem data whenever knowledge was on. Measured on mixed30: knowledge in
     * stage 2 alone did slightly better on latent structure than no knowledge, knowledge in stage 1 did worse; stage
     * 1 knowledge also removed observed false positives at the partition boundary. So: true where the observed
     * edges and their directions are the point, false where the latent structure is and the knowledge is rich.
     *
     * @param knowledgeInStage1 whether stage 1 sees the knowledge.
     */
    public void setKnowledgeInStage1(boolean knowledgeInStage1) {
        this.knowledgeInStage1 = knowledgeInStage1;
    }

    /**
     * Sets whether a rank deficiency must survive an observed-explanation check before it becomes a cluster
     * (default false). See {@link RlcdClusterSearch#setObservedExplanationCheck(boolean, double, int)}. In short: a
     * set is made a cluster of t latents only if no t observed variables explain its rank deficiency equally well.
     * This is the guard against reading a chain of observed variables -- a sequence of unit scores, say -- as a
     * measurement model, which rank constraints alone license, since a chain is rank one across any cut.
     *
     * @param check whether to check.
     * @param alpha the alpha of the explanation test; 0.05 by default, larger than the cluster alpha on purpose.
     */
    public void setObservedExplanationCheck(boolean check, double alpha) {
        this.observedExplanationCheck = check;
        this.observedExplanationAlpha = alpha;
    }

    /**
     * The number of rank deficiencies the last search explained away by observed variables, summed over partitions.
     *
     * @return the count; 0 when the check is off.
     */
    public int getExplainedAway() {
        return explainedAway;
    }

    /**
     * Sets the largest cluster cardinality to search for (default 3).
     *
     * @param maxK the maximum k.
     */
    public void setMaxK(int maxK) {
        if (maxK < 1) throw new IllegalArgumentException("maxK must be at least 1.");
        this.maxK = maxK;
    }

    /**
     * Whether observed variables may act as non-sinks (parents conditioned on in the rank tests). Default true.
     *
     * @param allowNonLeafX the flag.
     */
    public void setAllowNonLeafX(boolean allowNonLeafX) {
        this.allowNonLeafX = allowNonLeafX;
    }

    /**
     * Whether discovered latent covers are unfolded into their children for further rank tests. Default true.
     *
     * @param unfoldCovers the flag.
     */
    public void setUnfoldCovers(boolean unfoldCovers) {
        this.unfoldCovers = unfoldCovers;
    }

    /**
     * Whether a rank-deficient set is discarded when one of its proper subsets is already rank deficient at the
     * corresponding lower rank. Default true.
     *
     * @param checkV the flag.
     */
    public void setCheckV(boolean checkV) {
        this.checkV = checkV;
    }

    /**
     * Sets the minimum size of a stage-1 clique to be considered for a partition (default 3).
     *
     * @param threshold the threshold.
     */
    public void setPartitionCliqueThreshold(int threshold) {
        if (threshold < 1) throw new IllegalArgumentException("threshold must be at least 1.");
        this.partitionCliqueThreshold = threshold;
    }

    /**
     * Sets the stage-1 search method (default BOSS).
     *
     * @param method the method.
     */
    public void setStage1Method(Stage1Method method) {
        this.stage1Method = method;
    }

    /**
     * Sets the SEM BIC penalty discount for the stage-1 search (default 1, the standard BIC penalty; causal-learn's
     * default GES sparsity of 0.5 corresponds to this value).
     *
     * @param penaltyDiscount the penalty discount.
     */
    public void setPenaltyDiscount(double penaltyDiscount) {
        this.penaltyDiscount = penaltyDiscount;
    }

    /**
     * Supplies a stage-1 graph over the data variables directly, skipping the stage-1 search. Directed edges are
     * kept directed, any other edge is treated as undirected.
     *
     * @param stage1Graph the graph, or null to run the stage-1 search.
     */
    public void setStage1Graph(Graph stage1Graph) {
        this.stage1Graph = stage1Graph;
    }

    /**
     * Sets the random seed for the stage-1 BOSS search; −1 (default) leaves it unseeded.
     *
     * @param seed the seed.
     */
    public void setSeed(long seed) {
        this.seed = seed;
    }

    /**
     * Sets background knowledge over the observed variables. Knowledge is applied in three places: the stage-1
     * search honors it as any search does; in stage 2 an observed variable is never used as a non-sink (parent) of a
     * candidate member it is forbidden to cause, and a cluster whose cover would create a forbidden observed-to-
     * observed edge is rejected; and required edges between observed variables are restored in the output if stage
     * 2 dropped them, oriented as required. Temporal tiers (the main tiers and any additional tier structures) are
     * also read as constraining ancestry, since a cause cannot come after its effect: stage 2 rejects a cluster that
     * would make an observed variable, through latents, an ancestor of one in an earlier tier, and an edge between
     * latents is oriented away from the direction that would do so. Other knowledge (explicitly forbidden edges,
     * groups, edges forbidden within a tier) constrains direct edges only. Knowledge cannot refer to latent
     * variables, which are created by the search, so it cannot keep an observed variable from being placed under a
     * latent.
     *
     * @param knowledge the knowledge; null means none.
     */
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = knowledge == null ? new Knowledge() : knowledge;
    }

    /**
     * Whether to log the search trace through {@link TetradLogger}.
     *
     * @param verbose the flag.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Runs the search.
     *
     * @return the output graph over observed and latent variables.
     * @throws InterruptedException if interrupted.
     */
    public Graph search() throws InterruptedException {
        int nx = variables.size();
        List<String> xvars = new ArrayList<>();
        for (Node v : variables) xvars.add(v.getName());

        if (rankTester == null) {
            rankTester = imputations.size() > 1 ? new PooledRankTest(imputations) : new Chi2RankTest(dataSet);
        }

        // Stage 1.
        Graph g1 = stage1Graph != null ? stage1Graph : runStage1();
        this.stage1Result = g1;
        int[][] adj = stage1Adjacency(g1, xvars);
        int[][] adjStage1 = copy(adj);

        this.partition = getPartition(adjStage1, partitionCliqueThreshold);
        log("Partition of cliques:");
        for (List<Node> group : partition) log("   " + group);

        String latentPrefix = choosePrefix(xvars);

        // Stage 2, per partition.
        this.latentGroups = new ArrayList<>();
        RlcdClusterSearch search = new RlcdClusterSearch(xvars, rankTester, this::alphaFor, maxK,
                allowNonLeafX, unfoldCovers, checkV);
        search.setKnowledge(knowledge);
        search.setObservedExplanationCheck(observedExplanationCheck, observedExplanationAlpha, maxK, 20000);
        if (verbose) search.setLog(this::log);

        for (List<Node> group : partition) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();

            List<String> currentXvars = new ArrayList<>();
            List<Integer> currentIdx = new ArrayList<>();
            for (Node node : group) {
                currentXvars.add(node.getName());
                currentIdx.add(xvars.indexOf(node.getName()));
            }

            Set<String> neighbourSet = new TreeSet<>();
            for (int i : currentIdx) {
                for (int j = 0; j < nx; j++) {
                    if (!currentIdx.contains(j) && adj[i][j] != 0) neighbourSet.add(xvars.get(j));
                }
            }

            int m = currentXvars.size();
            boolean[][] localAdj = new boolean[m][m];
            for (int a = 0; a < m; a++) {
                for (int b = 0; b < m; b++) {
                    localAdj[a][b] = adj[currentIdx.get(a)][currentIdx.get(b)] != 0;
                }
            }

            // A variable that knowledge forbids from being a parent of every other member cannot be a non-sink.
            List<String> nonSinkCandidates = new ArrayList<>();
            for (String c : currentXvars) {
                boolean canParentSomething = false;
                for (String x : currentXvars) {
                    if (!x.equals(c) && !knowledge.isForbidden(c, x)) {
                        canParentSomething = true;
                        break;
                    }
                }
                if (canParentSomething) nonSinkCandidates.add(c);
            }

            LatentGroups current = new LatentGroups(currentXvars, nonSinkCandidates, neighbourSet, localAdj,
                    latentPrefix);

            if (latentGate && !latentIndicated(currentIdx, adjStage1, nx)) {
                // Nothing in this partition calls for a latent; its stage-1 edges stand.
                log("No latent is indicated among " + currentXvars + "; keeping the stage-1 edges.");
                latentGroups.add(current);
                continue;
            }

            current = search.findClusters(current);
            latentGroups.add(current);
            explainedAway = search.getExplainedAway();

            LatentGroups.AdjacencyResult out = current.toAdjacency();
            int[][] outAdj = getReducedAdj(out.adjacency(), m);
            int numNewLatent = outAdj.length - m;

            if (numNewLatent > 0) {
                int oldSize = adj.length;
                int[][] temp = new int[oldSize + numNewLatent][oldSize + numNewLatent];
                for (int i = 0; i < oldSize; i++) System.arraycopy(adj[i], 0, temp[i], 0, oldSize);
                adj = temp;

                List<Integer> latentIdx = new ArrayList<>();
                for (int i = oldSize; i < oldSize + numNewLatent; i++) latentIdx.add(i);

                copyByIdx(adj, outAdj, 0, m, 0, m, currentIdx, currentIdx);
                copyByIdx(adj, outAdj, 0, m, m, m + numNewLatent, currentIdx, latentIdx);
                copyByIdx(adj, outAdj, m, m + numNewLatent, 0, m, latentIdx, currentIdx);
                copyByIdx(adj, outAdj, m, m + numNewLatent, m, m + numNewLatent, latentIdx, latentIdx);
            }
            // As in the Python, a partition in which no latent was introduced leaves its stage-1 block untouched.
        }

        // Assemble the output graph.
        List<String> allVars = new ArrayList<>(xvars);
        Set<String> taken = new HashSet<>(xvars);
        int li = 1;
        for (int i = nx; i < adj.length; i++) {
            String name;
            do {
                name = "L" + li++;
            } while (taken.contains(name));
            taken.add(name);
            allVars.add(name);
        }
        this.allVariableNames = allVars;

        // Required edges between observed variables are kept, oriented as required, if stage 2 dropped them.
        for (int i = 0; i < nx; i++) {
            for (int j = 0; j < nx; j++) {
                if (i == j || !knowledge.isRequired(xvars.get(i), xvars.get(j))) continue;
                if (adj[i][j] == -1 && adj[j][i] == 1) continue;             // already i --> j
                if (adj[i][j] == 1 && adj[j][i] == -1) {                      // found j --> i: conflict, keep
                    log("Knowledge requires " + xvars.get(i) + " --> " + xvars.get(j)
                        + " but the search oriented it the other way; left as found.");
                    continue;
                }
                adj[i][j] = -1;
                adj[j][i] = 1;
                log("Adding required edge " + xvars.get(i) + " --> " + xvars.get(j) + " from knowledge.");
            }
        }

        // Co-members of an atomic cover (−2) get no edge.
        for (int i = 0; i < adj.length; i++) {
            for (int j = 0; j < adj.length; j++) {
                if (adj[i][j] == -2) adj[i][j] = 0;
            }
        }
        this.adjacency = adj;

        List<Node> nodes = new ArrayList<>(variables);
        for (int i = nx; i < adj.length; i++) {
            GraphNode latent = new GraphNode(allVars.get(i));
            latent.setNodeType(NodeType.LATENT);
            nodes.add(latent);
        }
        Graph graph = adjacencyToGraph(adj, nodes);
        orientLatentEdgesByRankTests(graph);
        if (qualifyObservedCauses) qualifyObservedCausesOfLatents(graph);
        if (resolveTwoIndicatorLatents) resolveTwoIndicatorLatents(graph);
        return graph;
    }

    // ---------------------------------------------------------------- observed causes of latents

    /**
     * Sets whether a directed edge between an observed variable O and a latent L is kept only when the data identify
     * its direction (default true). Rank constraints cannot tell an observed cause of a latent from an indicator of
     * it: O --&gt; L --&gt; X and L --&gt; O, L --&gt; X give the same covariance over the observed variables, so the
     * direction stage 2 reports is a placement, not a finding. What does identify it is a directed observed parent P
     * of O not itself attached to L. Under the chain P --&gt; O --&gt; L --&gt; X, P is dependent on the indicators
     * marginally and independent of them given O; under the collider P --&gt; O &lt;-- L --&gt; X, P is independent of
     * them marginally and dependent given O. Each directed observed-latent edge is checked against every such parent:
     * all chain signatures give O --&gt; L, all collider signatures give L --&gt; O, anything else is unidentified. An
     * unidentified O --&gt; L (including one with no directed observed parent) is made undirected; an unidentified
     * L --&gt; O is left as the measurement-model default unless the parents actively disagree, when it too is made
     * undirected. The tests are rank tests on the search's own rank tester at the cluster alphas (rank 0 marginally,
     * rank 1 with O added to both sides), so they pool over imputations or honour censoring as the rest of the search
     * does.
     *
     * @param qualify whether to qualify such edges.
     */
    public void setQualifyObservedCauses(boolean qualify) {
        this.qualifyObservedCauses = qualify;
    }

    /**
     * See {@link #setQualifyObservedCauses(boolean)}.
     *
     * @param graph the output graph, changed in place.
     */
    private void qualifyObservedCausesOfLatents(Graph graph) {
        List<String> xvars = new ArrayList<>();
        for (Node v : variables) xvars.add(v.getName());

        // Every directed edge between an observed O and a latent L, in either direction.
        List<Edge> candidates = new ArrayList<>();
        for (Edge e : graph.getEdges()) {
            if (!Edges.isDirectedEdge(e)) continue;
            Node from = Edges.getDirectedEdgeTail(e), to = Edges.getDirectedEdgeHead(e);
            boolean fromLatent = from.getNodeType() == NodeType.LATENT, toLatent = to.getNodeType() == NodeType.LATENT;
            if (fromLatent != toLatent) candidates.add(e);
        }
        candidates.sort(Comparator.comparing(Edge::toString));

        for (Edge e : candidates) {
            Node tail = Edges.getDirectedEdgeTail(e), head = Edges.getDirectedEdgeHead(e);
            boolean placedAsCause = head.getNodeType() == NodeType.LATENT;
            Node o = placedAsCause ? tail : head, l = placedAsCause ? head : tail;
            String placed = o + " --> " + l;

            List<Node> indicators = new ArrayList<>();
            for (Node c : graph.getChildren(l)) {
                if (c.getNodeType() != NodeType.LATENT && c != o) indicators.add(c);
            }
            // Directed observed parents of O that are not themselves attached to L.
            List<Node> parents = new ArrayList<>();
            for (Node pa : graph.getParents(o)) {
                if (pa.getNodeType() == NodeType.LATENT) continue;
                if (graph.isAdjacentTo(pa, l)) continue;
                parents.add(pa);
            }

            if (indicators.isEmpty() || parents.isEmpty()) {
                if (!placedAsCause) continue; // L --> O is the measurement-model default; leave it.
                String why = indicators.isEmpty() ? "no indicators to test against" : "no directed observed parent of " + o;
                graph.removeEdge(e);
                graph.addUndirectedEdge(o, l);
                log("Observed cause " + placed + ": " + why + "; direction undetermined, made undirected.");
                continue;
            }

            // Two signatures per parent P. Chain P --> O --> L --> X: P dependent on the indicators marginally,
            // independent given O. Collider P --> O <-- L --> X: P independent marginally, dependent given O.
            // Anything else (another path from P to the indicators) is uninformative.
            int oCol = xvars.indexOf(o.getName());
            int[] qcols = indicators.stream().mapToInt(v -> xvars.indexOf(v.getName())).toArray();
            int[] qcolsO = Arrays.copyOf(qcols, qcols.length + 1);
            qcolsO[qcols.length] = oCol;
            int chain = 0, collider = 0, other = 0;
            StringBuilder detail = new StringBuilder();
            for (Node pa : parents) {
                int pCol = xvars.indexOf(pa.getName());
                boolean margIndep = rankTester.failToReject(new int[]{pCol}, qcols, 0, alphaFor(0));
                boolean condIndep = rankTester.failToReject(new int[]{pCol, oCol}, qcolsO, 1, alphaFor(1));
                String sig;
                if (!margIndep && condIndep) {
                    chain++;
                    sig = "chain";
                } else if (margIndep && !condIndep) {
                    collider++;
                    sig = "collider";
                } else {
                    other++;
                    sig = "uninformative";
                }
                detail.append(' ').append(pa).append(':').append(sig);
            }

            if (chain > 0 && collider == 0 && other == 0) {
                if (!placedAsCause) {
                    graph.removeEdge(e);
                    graph.addDirectedEdge(o, l);
                    log("Indicator " + l + " --> " + o + ":" + detail + "; reversed to " + placed
                        + " (its observed parents reach the indicators only through " + o + ").");
                } else {
                    log("Observed cause " + placed + ":" + detail + "; kept.");
                }
            } else if (collider > 0 && chain == 0 && other == 0) {
                if (placedAsCause) {
                    graph.removeEdge(e);
                    graph.addDirectedEdge(l, o);
                    log("Observed cause " + placed + ":" + detail + "; reversed to " + l + " --> " + o
                        + " (" + o + " is a collider between its observed parents and " + l + ", hence a child).");
                } else {
                    log("Indicator " + l + " --> " + o + ":" + detail + "; kept.");
                }
            } else if (chain == 0 && collider == 0 && !placedAsCause) {
                log("Indicator " + l + " --> " + o + ":" + detail + "; kept (default).");
            } else {
                graph.removeEdge(e);
                graph.addUndirectedEdge(o, l);
                log((placedAsCause ? "Observed cause " + placed : "Indicator " + l + " --> " + o) + ":" + detail
                    + "; not identified, made undirected.");
            }
        }
    }

    // ---------------------------------------------------------------- two-indicator latents

    /**
     * Equivalence margin for {@link TwoIndicatorTest}.
     */
    private static final double TWO_INDICATOR_DELTA = 0.1;
    /**
     * Bootstrap resamples for {@link TwoIndicatorTest}.
     */
    private static final int TWO_INDICATOR_BOOT = 400;
    /**
     * At most this many references are used (pairs grow quadratically).
     */
    private static final int TWO_INDICATOR_MAX_REFS = 5;

    /**
     * See {@link #setResolveTwoIndicatorLatents(boolean)}.
     *
     * @param graph the output graph, changed in place.
     */
    private void resolveTwoIndicatorLatents(Graph graph) {
        if (rankTester instanceof StratifiedChi2RankTest) {
            log("Two-indicator latents are not resolved under a stratified rank test.");
            return;
        }

        List<Node> latents = new ArrayList<>();
        for (Node v : graph.getNodes()) if (v.getNodeType() == NodeType.LATENT) latents.add(v);
        latents.sort(Comparator.comparing(Node::getName));

        for (Node lp : latents) {
            if (!graph.containsNode(lp)) continue;

            // Exactly two observed neighbors, both children, no parent, and at most one latent neighbor.
            List<Node> children = new ArrayList<>();
            Node m = null;
            boolean ok = true;
            for (Node a : graph.getAdjacentNodes(lp)) {
                if (a.getNodeType() == NodeType.LATENT) {
                    if (m != null) {
                        ok = false;
                        break;
                    }
                    m = a;
                } else if (graph.isParentOf(lp, a)) {
                    children.add(a);
                } else {
                    ok = false;
                    break;
                }
            }
            if (!ok || children.size() != 2) continue;
            children.sort(Comparator.comparing(Node::getName));
            Node x1 = children.get(0), x2 = children.get(1);
            if (graph.isAdjacentTo(x1, x2)) continue;

            // With a latent neighbor M, the children must be pure (M's cluster is the reference set); without one,
            // the children may have observed parents of their own -- the chain case, where Z --> X1, Z --> X2 and a
            // latent over X1, X2 stands for X1 --> X2 -- but no latent parent.
            boolean pure = isPureChildOf(graph, x1, lp) && isPureChildOf(graph, x2, lp);
            if (m != null && !pure) continue;
            if (m == null && (!hasOnlyObservedParentsBesides(graph, x1, lp)
                              || !hasOnlyObservedParentsBesides(graph, x2, lp))) continue;

            // First choice: the non-Gaussian test, when there is a reference cluster to run it against.
            TwoIndicatorTest.Answer answer = TwoIndicatorTest.Answer.UNDECIDED;
            if (m != null) {
                List<Node> refs = new ArrayList<>();
                for (Node c : graph.getChildren(m)) {
                    if (c.getNodeType() != NodeType.LATENT && isPureChildOf(graph, c, m)) refs.add(c);
                }
                if (refs.size() >= 3) {
                    refs.sort(Comparator.comparing(Node::getName));
                    if (refs.size() > TWO_INDICATOR_MAX_REFS) refs = refs.subList(0, TWO_INDICATOR_MAX_REFS);
                    answer = null;
                    StringBuilder detail = new StringBuilder();
                    for (DataSet d : imputations) {
                        TwoIndicatorTest.Result r = TwoIndicatorTest.test(column(d, x1), column(d, x2),
                                columns(d, refs), TWO_INDICATOR_DELTA, TWO_INDICATOR_BOOT, seed == -1 ? 17L : seed);
                        detail.append(String.format(" [n12 = %.3f, n21 = %.3f, N = %d: %s]", r.n12(), r.n21(),
                                r.n(), r.answer()));
                        if (answer == null) answer = r.answer();
                        else if (answer != r.answer()) answer = TwoIndicatorTest.Answer.UNDECIDED;
                    }
                    log("Two-indicator latent " + lp + " over " + x1 + ", " + x2 + ", references " + refs
                        + " under " + m + ":" + detail);
                }
            }

            Node from, to;
            String basis;
            if (answer == TwoIndicatorTest.Answer.X1_TO_X2) {
                from = x1;
                to = x2;
                basis = "non-Gaussianity";
            } else if (answer == TwoIndicatorTest.Answer.X2_TO_X1) {
                from = x2;
                to = x1;
                basis = "non-Gaussianity";
            } else if (knowledge.isForbidden(x2.getName(), x1.getName())
                       && !knowledge.isForbidden(x1.getName(), x2.getName())) {
                // Second choice: the temporal tiers order the pair. A two-indicator latent is rank-equivalent to a
                // direct edge between its indicators (or to correlated errors); when knowledge says which way the
                // edge would run, the edge is the more parsimonious reading of the same covariance.
                from = x1;
                to = x2;
                basis = "knowledge";
            } else if (knowledge.isForbidden(x1.getName(), x2.getName())
                       && !knowledge.isForbidden(x2.getName(), x1.getName())) {
                from = x2;
                to = x1;
                basis = "knowledge";
            } else {
                if (m == null) log("Two-indicator latent " + lp + " over " + x1 + ", " + x2 + " has no reference "
                                   + "cluster and knowledge does not order the pair; kept.");
                continue;
            }
            log("   Direction " + from + " --> " + to + " by " + basis + ".");

            if (knowledge.isForbidden(from.getName(), to.getName())) {
                log("   Knowledge forbids " + from + " --> " + to + "; " + lp + " kept.");
                continue;
            }
            Set<String> below = new HashSet<>();
            for (Node d : graph.paths().getDescendants(to)) {
                if (d.getNodeType() != NodeType.LATENT) below.add(d.getName());
            }
            if (TierPrecedence.findViolation(knowledge, Set.of(from.getName()), below) != null) {
                log("   The temporal tiers rule out " + from + " as an ancestor of " + to + "'s descendants; "
                    + lp + " kept.");
                continue;
            }

            graph.removeNode(lp);
            if (m != null) {
                graph.addDirectedEdge(m, x1);
                graph.addDirectedEdge(m, x2);
            }
            graph.addDirectedEdge(from, to);
            log("   Replaced " + lp + " by " + (m != null ? m + " --> " + x1 + ", " + m + " --> " + x2 + ", " : "")
                + from + " --> " + to + ".");
        }
    }

    /**
     * Whether every edge at an observed node, other than the one from the given latent, is either directed away
     * from it or directed into it from an observed node.
     */
    private static boolean hasOnlyObservedParentsBesides(Graph graph, Node x, Node latent) {
        for (Edge e : graph.getEdges(x)) {
            Node other = e.getDistalNode(x);
            if (other == latent) {
                if (!graph.isParentOf(latent, x)) return false;
            } else if (!Edges.isDirectedEdge(e)) {
                return false;
            } else if (Edges.getDirectedEdgeHead(e) == x && other.getNodeType() == NodeType.LATENT) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether an observed node's only parent is the given latent and every other edge at it points away from it.
     */
    private static boolean isPureChildOf(Graph graph, Node x, Node latent) {
        for (Edge e : graph.getEdges(x)) {
            Node other = e.getDistalNode(x);
            if (other == latent) {
                if (!graph.isParentOf(latent, x)) return false;
            } else if (!(Edges.isDirectedEdge(e) && Edges.getDirectedEdgeTail(e) == x)) {
                return false;
            }
        }
        return true;
    }

    private static double[] column(DataSet d, Node v) {
        int j = d.getColumnIndex(v.getName());
        double[] out = new double[d.getNumRows()];
        for (int i = 0; i < out.length; i++) out[i] = d.getDouble(i, j);
        return out;
    }

    private static double[][] columns(DataSet d, List<Node> vs) {
        double[][] out = new double[vs.size()][];
        for (int k = 0; k < vs.size(); k++) out[k] = column(d, vs.get(k));
        return out;
    }

    /**
     * Returns the stage-1 graph.
     *
     * @return the stage-1 graph used (either supplied or searched); null before {@link #search()}.
     */
    public Graph getStage1Graph() {
        return stage1Result;
    }

    /**
     * Returns the stage-2 partition.
     *
     * @return the partition of observed variables searched in stage 2; null before {@link #search()}.
     */
    public List<List<Node>> getPartition() {
        return partition;
    }

    /**
     * Returns the per-partition latent structures.
     *
     * @return the per-partition latent structures, for inspection; null before {@link #search()}.
     */
    public List<LatentGroups> getLatentGroups() {
        return latentGroups;
    }

    /**
     * The largest number of latent covers conditioned on at once when searching for a separating set between two
     * nonadjacent latent covers.
     */
    private static final int LATENT_ORIENTATION_DEPTH = 3;

    /**
     * The number of random splits of each conditioning cover's indicators tried per test; the split giving the
     * smallest estimated rank is kept.
     */
    private static final int LATENT_ORIENTATION_SPLIT_TRIALS = 3;

    /**
     * Decides the directions of the edges between latents by rank tests, replacing the directions stage 2 built,
     * which record only the order in which covers were found (L1 --> L2 and L2 --> L1 imply the same rank
     * constraints unless a collider is involved, so the search cannot have chosen between them).
     * <p>
     * The latents are first grouped into covers: latents with identical child sets and no edge between them are
     * co-members of one atomic cover of cardinality k. Each cover becomes a block of a {@link BlockSpec}, its
     * block being its pure indicators (observed children with no other parent), or all its observed children if
     * it has fewer pure indicators than its cardinality, and its block rank being its cardinality. The skeleton
     * among covers is the one stage 2 found. Conditional independence between covers is then tested by
     * {@link TrekSeparationBlocksIndependence}, the trek-separation blocks test of Brodie and Spirtes: a
     * conditioning cover's indicators are split into two halves, one joined to each side, and the pair is
     * separated when the rank of the cross-covariance is at most the sum of the conditioning covers' ranks. The
     * test runs on this search's rank tester, so that with multiple imputations it is pooled in the same way as
     * stage 2. A cover with fewer than 2k indicators is not used as a conditioner.
     * <p>
     * For each pair of nonadjacent covers a separating set is sought among the covers adjacent to either, of size
     * up to {@link #LATENT_ORIENTATION_DEPTH}; of the candidate sets, the one whose test has the largest p-value is
     * taken if that p-value exceeds alpha (the max-p rule, as in PC-Max), rather than the first set that is not
     * rejected, since a conditioning set that is a common descendant of the pair can leave only a weak dependence
     * that a single test at alpha fails to reject. An unshielded triple A — C — B is oriented as a collider when
     * C is not in the separating set found for A and B; conflicting collider orientations leave the edge
     * undirected; the Meek rules are then closed over the cover graph; and the result is written back onto the
     * edges between latents. An edge between a latent and an observed variable is left as stage 2 built it: by
     * convention latent --> indicator, and observed --> latent where an observed variable was placed as a cause.
     * Pairs for which no separating set is found within the depth limit leave their triples unoriented.
     *
     * @param graph the output graph; its latent-latent edges are rewritten in place.
     */
    private void orientLatentEdgesByRankTests(Graph graph) {
        List<String> xvars = new ArrayList<>();
        for (Node v : variables) xvars.add(v.getName());

        // Group latents into covers by identical child sets.
        List<Node> latents = new ArrayList<>();
        for (Node v : graph.getNodes()) if (v.getNodeType() == NodeType.LATENT) latents.add(v);
        if (latents.size() < 2) return;

        List<List<Node>> covers = new ArrayList<>();
        Map<Node, Integer> coverOf = new HashMap<>();
        for (Node l : latents) {
            if (coverOf.containsKey(l)) continue;
            Set<Node> children = new HashSet<>(graph.getChildren(l));
            List<Node> cover = new ArrayList<>(List.of(l));
            for (Node m : latents) {
                if (m == l || coverOf.containsKey(m) || graph.isAdjacentTo(l, m)) continue;
                if (children.equals(new HashSet<>(graph.getChildren(m)))) cover.add(m);
            }
            for (Node m : cover) coverOf.put(m, covers.size());
            covers.add(cover);
        }
        int nc = covers.size();
        if (nc < 2) return;

        // One block per cover: its pure indicators, or all its observed children if too few are pure.
        List<List<Integer>> blocks = new ArrayList<>();
        List<Node> blockVars = new ArrayList<>();
        List<Integer> ranks = new ArrayList<>();
        for (List<Node> cover : covers) {
            Set<Node> members = new HashSet<>(cover);
            List<Integer> pure = new ArrayList<>(), all = new ArrayList<>();
            for (Node c : graph.getChildren(cover.get(0))) {
                if (c.getNodeType() == NodeType.LATENT) continue;
                int idx = xvars.indexOf(c.getName());
                if (idx < 0) continue;
                all.add(idx);
                if (members.equals(new HashSet<>(graph.getParents(c)))) pure.add(idx);
            }
            blocks.add(pure.size() >= cover.size() ? pure : all);
            // Block variables must be ContinuousVariables: BlockSpec records each block's rank on its node.
            Node blockVar = new ContinuousVariable(cover.size() == 1 ? cover.get(0).getName()
                    : cover.stream().map(Node::getName).reduce((a, b) -> a + "," + b).orElse(""));
            blockVar.setNodeType(NodeType.LATENT);
            blockVars.add(blockVar);
            ranks.add(cover.size());
        }
        TrekSeparationBlocksIndependence test = new TrekSeparationBlocksIndependence(
                new BlockSpec(dataSet, blocks, blockVars, ranks), rankTester);
        test.setAlpha(alpha);
        test.setNumTrials(LATENT_ORIENTATION_SPLIT_TRIALS);
        test.setRandomizeSplits(true, seed == -1 ? 17L : seed);

        // Skeleton among covers.
        boolean[][] adjacent = new boolean[nc][nc];
        for (Edge e : graph.getEdges()) {
            Node a = e.getNode1(), b = e.getNode2();
            if (a.getNodeType() != NodeType.LATENT || b.getNodeType() != NodeType.LATENT) continue;
            int i = coverOf.get(a), j = coverOf.get(b);
            if (i != j) adjacent[i][j] = adjacent[j][i] = true;
        }

        // Separating sets by a PC-style search with the max-p rule.
        Map<Long, Set<Integer>> sepsets = new HashMap<>();
        for (int i = 0; i < nc; i++) {
            for (int j = i + 1; j < nc; j++) {
                if (adjacent[i][j]) continue;
                if (blocks.get(i).isEmpty() || blocks.get(j).isEmpty()) continue;
                List<Integer> candidates = new ArrayList<>();
                for (int c = 0; c < nc; c++) {
                    if (c != i && c != j && (adjacent[i][c] || adjacent[j][c])
                        && blocks.get(c).size() >= 2 * ranks.get(c)) {
                        candidates.add(c);
                    }
                }
                Set<Integer> found = null;
                double bestP = -1;
                for (int depth = 0; depth <= Math.min(LATENT_ORIENTATION_DEPTH, candidates.size()); depth++) {
                    for (List<Integer> s : combinations(candidates, depth)) {
                        Set<Node> z = new HashSet<>();
                        int target = 0;
                        for (int c : s) {
                            z.add(blockVars.get(c));
                            target += ranks.get(c);
                        }
                        IndependenceResult res = test.checkIndependence(blockVars.get(i), blockVars.get(j), z);
                        double pv = res.getPValue();
                        if (pv > bestP) {
                            bestP = pv;
                            found = new HashSet<>(s);
                        }
                    }
                }
                if (found != null && bestP > alphaFor(conditioningRank(found, covers))) {
                    sepsets.put(pairKey(i, j), found);
                    log("Latent covers " + covers.get(i) + " and " + covers.get(j) + " separated by "
                        + found.stream().map(covers::get).toList() + " (p = " + bestP + ")");
                }
            }
        }

        // Cover graph: skeleton, colliders, Meek.
        List<Node> coverNodes = new ArrayList<>();
        for (int i = 0; i < nc; i++) coverNodes.add(new GraphNode("C" + i));
        Graph coverGraph = new EdgeListGraph(coverNodes);
        for (int i = 0; i < nc; i++) {
            for (int j = i + 1; j < nc; j++) {
                if (adjacent[i][j]) coverGraph.addUndirectedEdge(coverNodes.get(i), coverNodes.get(j));
            }
        }

        // Temporal tiers, first: cover i --> cover j is ruled out when an observed parent of i lies in a later tier
        // than an observed child of j, since the edge would make the one an ancestor of the other.
        List<Set<String>> obsParents = new ArrayList<>(), obsChildren = new ArrayList<>();
        for (List<Node> cover : covers) {
            Set<String> pa = new TreeSet<>(), ch = new TreeSet<>();
            for (Node l : cover) {
                for (Node p : graph.getParents(l)) if (p.getNodeType() != NodeType.LATENT) pa.add(p.getName());
                for (Node c : graph.getChildren(l)) if (c.getNodeType() != NodeType.LATENT) ch.add(c.getName());
            }
            obsParents.add(pa);
            obsChildren.add(ch);
        }
        boolean[][] tierForbids = new boolean[nc][nc];   // [i][j]: cover i --> cover j is ruled out
        for (int i = 0; i < nc; i++) {
            for (int j = 0; j < nc; j++) {
                if (i != j && adjacent[i][j]) {
                    tierForbids[i][j] = TierPrecedence.findViolation(knowledge, obsParents.get(i),
                            obsChildren.get(j)) != null;
                }
            }
        }
        for (int i = 0; i < nc; i++) {
            for (int j = i + 1; j < nc; j++) {
                if (!adjacent[i][j] || tierForbids[i][j] == tierForbids[j][i]) {
                    if (adjacent[i][j] && tierForbids[i][j]) {
                        log("Temporal tiers rule out both directions between latent covers " + covers.get(i)
                            + " and " + covers.get(j) + "; left unoriented.");
                    }
                    continue;
                }
                int from = tierForbids[i][j] ? j : i, to = tierForbids[i][j] ? i : j;
                coverGraph.removeEdge(coverNodes.get(i), coverNodes.get(j));
                coverGraph.addDirectedEdge(coverNodes.get(from), coverNodes.get(to));
                log("Temporal tiers orient latent covers " + covers.get(from) + " --> " + covers.get(to));
            }
        }

        Set<Long> colliderInto = new HashSet<>();   // directed cover edges a --> c requested by colliders
        for (int c = 0; c < nc; c++) {
            for (int a = 0; a < nc; a++) {
                if (a == c || !adjacent[a][c]) continue;
                for (int b = a + 1; b < nc; b++) {
                    if (b == c || !adjacent[b][c] || adjacent[a][b]) continue;
                    if (tierForbids[a][c] || tierForbids[b][c]) continue;
                    Set<Integer> sep = sepsets.get(pairKey(a, b));
                    if (sep == null || sep.contains(c)) continue;
                    colliderInto.add(dirKey(a, c));
                    colliderInto.add(dirKey(b, c));
                    log("Latent collider " + covers.get(a) + " --> " + covers.get(c) + " <-- " + covers.get(b));
                }
            }
        }
        for (long key : colliderInto) {
            int from = (int) (key >> 32), to = (int) key;
            if (colliderInto.contains(dirKey(to, from))) continue;  // conflict: leave undirected
            Node f = coverNodes.get(from), t = coverNodes.get(to);
            if (coverGraph.getEdge(f, t) != null && Edges.isUndirectedEdge(coverGraph.getEdge(f, t))) {
                coverGraph.removeEdge(f, t);
                coverGraph.addDirectedEdge(f, t);
            }
        }
        new MeekRules().orientImplied(coverGraph);
        for (int i = 0; i < nc; i++) {
            for (int j = 0; j < nc; j++) {
                if (tierForbids[i][j] && coverGraph.isParentOf(coverNodes.get(i), coverNodes.get(j))) {
                    log("Warning: the rank tests orient latent covers " + covers.get(i) + " --> " + covers.get(j)
                        + " against the temporal tiers.");
                }
            }
        }

        // Write back onto the latent-latent edges.
        for (Edge edge : new ArrayList<>(graph.getEdges())) {
            Node a = edge.getNode1(), b = edge.getNode2();
            if (a.getNodeType() != NodeType.LATENT || b.getNodeType() != NodeType.LATENT) continue;
            int i = coverOf.get(a), j = coverOf.get(b);
            if (i == j) continue;
            Edge ce = coverGraph.getEdge(coverNodes.get(i), coverNodes.get(j));
            if (ce == null) continue;
            Edge wanted;
            if (Edges.isUndirectedEdge(ce)) {
                wanted = Edges.undirectedEdge(a, b);
            } else if (Edges.getDirectedEdgeTail(ce) == coverNodes.get(i)) {
                wanted = Edges.directedEdge(a, b);
            } else {
                wanted = Edges.directedEdge(b, a);
            }
            if (!wanted.equals(edge)) {
                graph.removeEdge(edge);
                graph.addEdge(wanted);
            }
        }
    }

    private static int conditioningRank(Collection<Integer> s, List<List<Node>> covers) {
        int rank = 0;
        for (int c : s) rank += covers.get(c).size();
        return rank;
    }

    private static long pairKey(int i, int j) {
        return dirKey(Math.min(i, j), Math.max(i, j));
    }

    private static long dirKey(int from, int to) {
        return ((long) from << 32) | (to & 0xffffffffL);
    }

    /**
     * The signed adjacency over observed then latent variables as stage 2 left it, in causal-learn's encoding:
     * A[i][j] = −1 and A[j][i] = 1 for i → j; −1 both ways for an undirected edge. Edges between latents are as the
     * search built them, before the returned graph's are reduced to what the equivalence class determines.
     *
     * @return the matrix; null before {@link #search()}.
     */
    public int[][] getAdjacency() {
        return adjacency;
    }

    /**
     * Returns the variable names for the adjacency matrix.
     *
     * @return observed variable names followed by latent names, indexing {@link #getAdjacency()}.
     */
    public List<String> getAllVariableNames() {
        return allVariableNames;
    }

    // ---------------------------------------------------------------- stage 1

    /**
     * Whether the rank constraints call for a latent variable somewhere among the given observed variables, by the
     * condition of Theorems 5 and 9 of Dong et al.: there are disjoint sets A, B and C of observed variables with
     * |B| &ge; |A| &ge; 2, the members of A pairwise adjacent in the skeleton, every member of A adjacent to every
     * member of B, every member of C adjacent to some member of A or B, and rank(&Sigma;[A &cup; C, B &cup; C]) less
     * than |A| + |C|. The paper shows that condition sufficient for a latent on a trek between A and B and, under
     * its Condition 1, necessary as well, so where it fails stage 2 has nothing to find, and anything it did find
     * would be a latent standing in for structure among the observed variables.
     * <p>
     * The skeleton here is the stage-1 skeleton, not the paper's CI skeleton, and the search is limited to |B| = |A|
     * &le; maxK + 1 and |C| &le; 1, so the check is an approximation of the condition. It errs toward saying that a
     * latent is indicated: the answer is yes as soon as one test fails to reject a rank deficiency, and also if the
     * number of tests passes a limit.
     *
     * @param members  the columns of the observed variables in the partition.
     * @param skeleton the stage-1 adjacency over the observed variables.
     * @param nx       the number of observed variables.
     */
    private boolean latentIndicated(List<Integer> members, int[][] skeleton, int nx) {
        int[] budget = {MAX_GATE_TESTS};

        for (int size = 2; size <= maxK + 1 && 2 * size <= members.size(); size++) {
            for (List<Integer> a : combinations(members, size)) {
                if (!pairwiseAdjacent(a, skeleton)) continue;

                List<Integer> common = new ArrayList<>();
                for (int v : members) {
                    if (a.contains(v)) continue;
                    boolean all = true;
                    for (int u : a) all = all && (skeleton[u][v] != 0 || skeleton[v][u] != 0);
                    if (all) common.add(v);
                }
                if (common.size() < size) continue;

                for (List<Integer> b : combinations(common, size)) {
                    List<Integer> neighbours = new ArrayList<>();
                    for (int v = 0; v < nx; v++) {
                        if (a.contains(v) || b.contains(v)) continue;
                        boolean adjacent = false;
                        for (int u : a) adjacent = adjacent || skeleton[u][v] != 0 || skeleton[v][u] != 0;
                        for (int u : b) adjacent = adjacent || skeleton[u][v] != 0 || skeleton[v][u] != 0;
                        if (adjacent) neighbours.add(v);
                    }

                    List<List<Integer>> conditioning = new ArrayList<>();
                    conditioning.add(List.of());
                    for (int v : neighbours) conditioning.add(List.of(v));

                    for (List<Integer> c : conditioning) {
                        if (--budget[0] < 0) return true;

                        int[] p = new int[size + c.size()], q = new int[size + c.size()];
                        for (int i = 0; i < size; i++) {
                            p[i] = a.get(i);
                            q[i] = b.get(i);
                        }
                        for (int i = 0; i < c.size(); i++) p[size + i] = q[size + i] = c.get(i);

                        int rank = size + c.size() - 1;
                        if (rankTester.failToReject(p, q, rank, alphaFor(rank))) return true;
                    }
                }
            }
        }

        return false;
    }

    private static boolean pairwiseAdjacent(List<Integer> vars, int[][] skeleton) {
        for (int i = 0; i < vars.size(); i++) {
            for (int j = i + 1; j < vars.size(); j++) {
                if (skeleton[vars.get(i)][vars.get(j)] == 0 && skeleton[vars.get(j)][vars.get(i)] == 0) return false;
            }
        }
        return true;
    }

    private static List<List<Integer>> combinations(List<Integer> items, int r) {
        List<List<Integer>> out = new ArrayList<>();
        combine(items, r, 0, new ArrayList<>(), out);
        return out;
    }

    private static void combine(List<Integer> items, int r, int start, List<Integer> current,
                                List<List<Integer>> out) {
        if (current.size() == r) {
            out.add(new ArrayList<>(current));
            return;
        }
        for (int i = start; i < items.size(); i++) {
            current.add(items.get(i));
            combine(items, r, i + 1, current, out);
            current.remove(current.size() - 1);
        }
    }

    private double alphaFor(int rank) {
        if (alphaByRank != null && rank >= 0 && rank < alphaByRank.length) return alphaByRank[rank];
        return alpha;
    }

    private Graph runStage1() throws InterruptedException {
        if (stage1Method == Stage1Method.NONE) {
            Graph complete = new EdgeListGraph(variables);
            for (int i = 0; i < variables.size(); i++) {
                for (int j = i + 1; j < variables.size(); j++) {
                    complete.addUndirectedEdge(variables.get(i), variables.get(j));
                }
            }
            return complete;
        }

        SemBicScore score = new SemBicScore(stage1Covariance());
        score.setPenaltyDiscount(penaltyDiscount);
        if (stage1Method == Stage1Method.FGES) {
            Fges fges = new Fges(score);
            if (knowledgeInStage1) fges.setKnowledge(knowledge);
            fges.setVerbose(false);
            return fges.search();
        } else {
            Boss boss = new Boss(score);
            boss.setVerbose(false);
            PermutationSearch ps = new PermutationSearch(boss);
            if (knowledgeInStage1) ps.setKnowledge(knowledge);
            if (seed != -1) ps.setSeed(seed);
            return ps.search();
        }
    }

    /**
     * The covariance matrix stage 1 is scored on: the data set's, or with several imputations the average of
     * theirs, at the common sample size.
     */
    private CovarianceMatrix stage1Covariance() {
        if (stage1CovarianceOverride != null) return stage1CovarianceOverride;
        if (testwiseDeletion) {
            stage1CovarianceOverride = listwiseCompleteCovariance();
            return stage1CovarianceOverride;
        }

        CovarianceMatrix first = new CovarianceMatrix(dataSet);
        if (imputations.size() == 1) return first;

        Matrix sum = first.getMatrix();

        for (int i = 1; i < imputations.size(); i++) {
            sum = sum.plus(new CovarianceMatrix(imputations.get(i)).getMatrix());
        }

        return new CovarianceMatrix(first.getVariables(), sum.scalarMult(1.0 / imputations.size()),
                first.getSampleSize());
    }

    /**
     * Encodes a graph over the data variables in causal-learn's convention.
     */
    private int[][] stage1Adjacency(Graph g, List<String> xvars) {
        int n = xvars.size();
        int[][] adj = new int[n][n];
        for (Edge edge : g.getEdges()) {
            int i = xvars.indexOf(edge.getNode1().getName());
            int j = xvars.indexOf(edge.getNode2().getName());
            if (i < 0 || j < 0) {
                throw new IllegalArgumentException("Stage-1 graph node not in data: " + edge);
            }
            if (edge.getEndpoint1() == Endpoint.TAIL && edge.getEndpoint2() == Endpoint.ARROW) {
                adj[i][j] = -1;
                adj[j][i] = 1;
            } else if (edge.getEndpoint1() == Endpoint.ARROW && edge.getEndpoint2() == Endpoint.TAIL) {
                adj[j][i] = -1;
                adj[i][j] = 1;
            } else {
                adj[i][j] = -1;
                adj[j][i] = -1;
            }
        }
        return adj;
    }

    /**
     * Partitions the observed variables (Python <code>getPartition</code>): maximal cliques of the skeleton with
     * at least <code>threshold</code> members, merged when two cliques share at least two variables; merged groups
     * of at least four variables are returned, each ordered by data column.
     */
    private List<List<Node>> getPartition(int[][] adjStage1, int threshold) {
        int n = variables.size();
        Graph skeleton = new EdgeListGraph(variables);
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (adjStage1[i][j] != 0 || adjStage1[j][i] != 0) {
                    skeleton.addUndirectedEdge(variables.get(i), variables.get(j));
                }
            }
        }

        List<Set<Integer>> cliques = new ArrayList<>();
        for (Set<Node> clique : GraphUtils.maximalCliques(skeleton, variables)) {
            if (clique.size() < threshold) continue;
            Set<Integer> idx = new TreeSet<>();
            for (Node node : clique) idx.add(variables.indexOf(node));
            cliques.add(idx);
        }
        cliques.sort(Comparator.comparing(Object::toString));

        int c = cliques.size();
        int[] root = new int[c];
        for (int i = 0; i < c; i++) root[i] = i;
        for (int i = 0; i < c; i++) {
            for (int j = 0; j < c; j++) {
                if (i == j) continue;
                Set<Integer> common = new HashSet<>(cliques.get(i));
                common.retainAll(cliques.get(j));
                if (common.size() >= 2) {
                    int a = find(root, i), b = find(root, j);
                    if (a != b) root[b] = a;
                }
            }
        }

        Map<Integer, SortedSet<Integer>> groups = new TreeMap<>();
        for (int i = 0; i < c; i++) {
            groups.computeIfAbsent(find(root, i), k -> new TreeSet<>()).addAll(cliques.get(i));
        }

        List<List<Node>> out = new ArrayList<>();
        for (SortedSet<Integer> group : groups.values()) {
            if (group.size() >= 4) {
                List<Node> nodes = new ArrayList<>();
                for (int i : group) nodes.add(variables.get(i));
                out.add(nodes);
            }
        }
        out.sort(Comparator.comparingInt((List<Node> group) -> variables.indexOf(group.get(0)))
                .thenComparingInt(List::size));
        return out;
    }

    private static int find(int[] root, int k) {
        while (root[k] != k) {
            root[k] = root[root[k]];
            k = root[k];
        }
        return k;
    }

    /**
     * Keeps the observed indices 0..numObserved−1 and every latent that lies on a simple path between two observed
     * variables, dropping the rest (Python <code>getReducedAdj</code>).
     */
    private static int[][] getReducedAdj(int[][] adj, int numObserved) {
        int n = adj.length;
        SortedSet<Integer> keep = new TreeSet<>();
        for (int i = 0; i < numObserved; i++) keep.add(i);
        for (int start = 0; start < numObserved; start++) {
            boolean[] onPath = new boolean[n];
            onPath[start] = true;
            dfsPaths(adj, numObserved, start, start, onPath, keep);
        }
        List<Integer> idx = new ArrayList<>(keep);
        int[][] out = new int[idx.size()][idx.size()];
        for (int a = 0; a < idx.size(); a++) {
            for (int b = 0; b < idx.size(); b++) out[a][b] = adj[idx.get(a)][idx.get(b)];
        }
        return out;
    }

    private static void dfsPaths(int[][] adj, int numObserved, int start, int current, boolean[] onPath,
                                 Set<Integer> keep) {
        if (current < numObserved && current != start) {
            for (int i = 0; i < onPath.length; i++) if (onPath[i]) keep.add(i);
            return;
        }
        for (int j = 0; j < adj.length; j++) {
            if (adj[current][j] != 0 && !onPath[j]) {
                onPath[j] = true;
                dfsPaths(adj, numObserved, start, j, onPath, keep);
                onPath[j] = false;
            }
        }
    }

    private static void copyByIdx(int[][] target, int[][] source, int r0, int r1, int c0, int c1,
                                  List<Integer> rowsInTarget, List<Integer> colsInTarget) {
        for (int r = r0; r < r1; r++) {
            for (int c = c0; c < c1; c++) {
                target[rowsInTarget.get(r - r0)][colsInTarget.get(c - c0)] = source[r][c];
            }
        }
    }

    private static int[][] copy(int[][] a) {
        int[][] out = new int[a.length][];
        for (int i = 0; i < a.length; i++) out[i] = a[i].clone();
        return out;
    }

    /**
     * Chooses an internal prefix for latent names that cannot collide with an observed variable name.
     */
    private static String choosePrefix(List<String> xvars) {
        String prefix = "L";
        while (true) {
            boolean collides = false;
            for (String x : xvars) {
                if (x.startsWith(prefix) && x.substring(prefix.length()).matches("\\d+")) {
                    collides = true;
                    break;
                }
            }
            if (!collides) return prefix;
            prefix = "_" + prefix;
        }
    }

    /**
     * Converts a causal-learn-encoded adjacency into a Tetrad graph (Python <code>_adjacency_to_causal_graph</code>).
     */
    private static Graph adjacencyToGraph(int[][] adj, List<Node> nodes) {
        Graph graph = new EdgeListGraph(nodes);
        for (int i = 0; i < adj.length; i++) {
            for (int j = i + 1; j < adj.length; j++) {
                if (adj[i][j] == 0 && adj[j][i] == 0) continue;
                if (adj[i][j] == -1 && adj[j][i] == 1) {
                    graph.addDirectedEdge(nodes.get(i), nodes.get(j));
                } else if (adj[i][j] == 1 && adj[j][i] == -1) {
                    graph.addDirectedEdge(nodes.get(j), nodes.get(i));
                } else {
                    graph.addUndirectedEdge(nodes.get(i), nodes.get(j));
                }
            }
        }
        return graph;
    }

    private void log(String message) {
        if (verbose) TetradLogger.getInstance().log(message);
    }
}
