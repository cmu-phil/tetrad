package edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesScoreWrapper;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.CyclicBoss;
import edu.cmu.tetrad.search.PermutationSearch;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.util.Parameters;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * GUI-ready wrapper that exposes CyclicBoss as a first-class Algorithm.
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "Cyclic BOSS",
        command = "cyclic-boss",
        algoType = AlgType.forbid_latent_common_causes
)
@Deprecated
public final class CyclicBossAlgorithm implements Algorithm, HasKnowledge, TakesScoreWrapper {

    // ----- Parameter keys -----
    public static final String CYB_CANDIDATE_POOL_SIZE   = "CYB_CANDIDATE_POOL_SIZE";
    public static final String CYB_MAX_BACK_EDGES        = "CYB_MAX_BACK_EDGES";
    public static final String CYB_MAX_SCC_SIZE          = "CYB_MAX_SCC_SIZE";
    public static final String CYB_STABILITY_TOL         = "CYB_STABILITY_TOL";
    public static final String CYB_BIC_HIGHER_IS_BETTER  = "CYB_BIC_HIGHER_IS_BETTER";
    public static final String CYB_MIN_IMPROVE           = "CYB_MIN_IMPROVE";   // ΔBIC margin
    public static final String CYB_MIN_CORR              = "CYB_MIN_CORR";      // bidirectional residual cutoff

    private Knowledge knowledge = new Knowledge();
    private ScoreWrapper scoreWrapper;          // may be null
    private Parameters fallbackParams = defaultParameters();

    // ----- UI helpers -----
    public String getAlgorithmShortName() { return "CyclicBOSS"; }
    public String getAlgorithmGroup()     { return "Permutation-based (with cycles)"; }
    public String getDescription() {
        return "Runs BOSS to get a DAG backbone, then greedily adds a small number of "
               + "feedback (back-)edges when a cyclic SEM BIC improves, with stability and SCC guards.";
    }

    public static Parameters defaultParameters() {
        Parameters p = new Parameters();
        p.set(CYB_CANDIDATE_POOL_SIZE,  2000);
        p.set(CYB_MAX_BACK_EDGES,       Integer.MAX_VALUE);
        p.set(CYB_MAX_SCC_SIZE,         2);        // small feedback modules by default
        p.set(CYB_STABILITY_TOL,        0.999);
        p.set(CYB_BIC_HIGHER_IS_BETTER, true);
        p.set(CYB_MIN_IMPROVE,          3.0);      // require meaningful ΔBIC
        p.set(CYB_MIN_CORR,             0.10);     // bidirectional residual correlation cutoff
        return p;
    }

    // pick a sensible default if UI doesn’t supply a Score
    private static Score defaultScoreFor(DataSet data) {
        return new SemBicScore(new CovarianceMatrix(data));
    }

    // Base permutation algorithm (BOSS wrapped in PermutationSearch).
    private PermutationSearch baseFactory(Score s) {
        PermutationSearch boss = new PermutationSearch(new edu.cmu.tetrad.search.Boss(s));
        boss.setKnowledge(knowledge);
        return boss;
    }

    @Override
    public Graph search(DataModel dataModel, Parameters parameters) throws InterruptedException {
        Objects.requireNonNull(dataModel, "data");
        DataSet data = (DataSet) dataModel;

        // Resolve Parameters (runtime > fallback defaults)
        final Parameters p = fallbackParams;// (parameters != null) ? parameters : this.fallbackParams;

        // Resolve Score
        final Score sc = (this.scoreWrapper != null)
                ? this.scoreWrapper.getScore(dataModel, p)
                : defaultScoreFor(data);

        // Backbone search
        final PermutationSearch base = baseFactory(sc);

        // Cyclic wrapper with all knobs forwarded
        CyclicBoss cyclic = new CyclicBoss(base)
                .withCandidatePoolSize(p.getInt(CYB_CANDIDATE_POOL_SIZE))
                .withMaxBackEdges(p.getInt(CYB_MAX_BACK_EDGES))
                .withMaxSccSize(p.getInt(CYB_MAX_SCC_SIZE))
                .withStabilityTol(p.getDouble(CYB_STABILITY_TOL))
                .withHigherIsBetterBic(p.getBoolean(CYB_BIC_HIGHER_IS_BETTER))
                .withMinImprove(p.getDouble(CYB_MIN_IMPROVE))
                .withMinCorr(p.getDouble(CYB_MIN_CORR));

        cyclic.setKnowledge(knowledge);

        return cyclic.search(data);
    }

    @Override
    public Knowledge getKnowledge() { return knowledge.copy(); }

    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = (knowledge == null) ? new Knowledge() : knowledge;
    }

    public List<String> getParameters() { return new ArrayList<>(); }

    public void setParameters(Parameters parameters) {
        if (parameters != null) this.fallbackParams = parameters;
    }

    @Override
    public Graph getComparisonGraph(Graph graph) { return new EdgeListGraph(graph); }

    @Override
    public DataType getDataType() { return DataType.Continuous; }

    // ----- Score wrapper plumbing -----
    @Override
    public ScoreWrapper getScoreWrapper() { return this.scoreWrapper; }

    @Override
    public void setScoreWrapper(ScoreWrapper score) { this.scoreWrapper = score; }
}