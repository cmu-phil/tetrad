package edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag;

import edu.cmu.tetrad.algcomparison.algorithm.AbstractBootstrapAlgorithm;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.RandomUtil;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for the Two-Step algorithm (ICA-based, allows cycles).
 * Returns the directed graph; prints the B matrix to stdout.
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "Two-Step (ICA, cyclic)",
        command = "twostep",
        // Two-Step assumes no latent confounding; use the same category CD-NOD used.
        algoType = AlgType.forbid_latent_common_causes
)
@Deprecated
@Bootstrapping
public class TwoStep2 extends AbstractBootstrapAlgorithm implements Algorithm {

    @Serial
    private static final long serialVersionUID = 1L;

    // ---- Parameter keys (local to this wrapper; shown in UI) ----
    public static final String LAMBDA = "twostepLambda";
//    public static final String NORMALIZE_LOSS_BY_N = "twostepNormalizeLossByN";
    public static final String USE_ADAPTIVE_LASSO = "twostepUseAdaptiveLasso";
    public static final String ADAPTIVE_GAMMA = "twostepAdaptiveGamma";
    public static final String MASK_THRESHOLD = "twostepMaskThreshold";
    public static final String COEF_THRESHOLD = "twostepCoefThreshold";
    public static final String ICA_MAX_ITER = "twostepIcaMaxIter";
    public static final String ICA_TOL = "twostepIcaTol";
    public static final String RANDOM_SEED = "twostepRandomSeed";
    public static final String VERBOSE = "twostepVerbose";
    public static final String COND_WARN_THRESHOLD = "twostepCondWarnThreshold";

    private Knowledge knowledge = new Knowledge(); // not used but kept for consistency

    public TwoStep2() {}

    @Override
    protected Graph runSearch(DataModel dataModel, Parameters parameters) throws InterruptedException {
        DataSet data = (DataSet) dataModel;

        // ---- Pull knobs (with defaults matching TwoStep) ----
        double lambda = .01;// parameters.getDouble(LAMBDA, 0.05);
//        boolean normalizeLossByN = parameters.getBoolean(NORMALIZE_LOSS_BY_N, false);
        boolean useAdaptive = parameters.getBoolean(USE_ADAPTIVE_LASSO, true);
        double adaptiveGamma = parameters.getDouble(ADAPTIVE_GAMMA, 1.0);
        double maskThr = 1e-2;// parameters.getDouble(MASK_THRESHOLD, 1e-2);
        double coefThr = 1e-2;//parameters.getDouble(COEF_THRESHOLD, 1e-2);
        int icaMaxIter = parameters.getInt(ICA_MAX_ITER, 1000);
        double icaTol = parameters.getDouble(ICA_TOL, 1e-5);
        long seed = RandomUtil.getInstance().nextLong();  // (long) parameters.getDouble(RANDOM_SEED, 123L);
        boolean verbose = parameters.getBoolean(VERBOSE, true);
        double condWarn = parameters.getDouble(COND_WARN_THRESHOLD, 1e8);

        // ---- Configure and run search ----
        edu.cmu.tetrad.search.TwoStep algo = new edu.cmu.tetrad.search.TwoStep();
        algo.setLambda(lambda);
        algo.setNormalizeLossByN(true);
        algo.setUseAdaptiveLasso(useAdaptive);
        algo.setAdaptiveGamma(adaptiveGamma);
        algo.setMaskThreshold(maskThr);
        algo.setCoefThreshold(.1);
        algo.setIcaMaxIter(icaMaxIter);
        algo.setIcaTol(icaTol);
        algo.setRandomSeed(seed);
//        algo.setVerbose(verbose);
//        algo.setCondWarnThreshold(condWarn);

        edu.cmu.tetrad.search.TwoStep.Result result = algo.search(data);

        // Print B to stdout for users (no UI facility to show it)
        System.out.println("[TwoStep] Estimated B matrix:");
        System.out.println(result.B);

        // If you want extra diagnostics:
        // System.out.println("[TwoStep] ICA converged: " + algo.isIcaConverged() + " in " + algo.getIcaIters() + " iters.");
        // System.out.println("[TwoStep] Mixing A:");
        // System.out.println(algo.getLastA());

        return result.graph;
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        // For algorithms that may return cycles, just return the graph itself.
        return graph;
    }

    @Override
    public String getDescription() {
        return "Two-Step (ICA-based, allows cycles) over continuous data.";
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        List<String> ps = new ArrayList<>();
        ps.add(LAMBDA);
//        ps.add(NORMALIZE_LOSS_BY_N);
        ps.add(USE_ADAPTIVE_LASSO);
        ps.add(ADAPTIVE_GAMMA);
        ps.add(MASK_THRESHOLD);
        ps.add(COEF_THRESHOLD);
        ps.add(ICA_MAX_ITER);
        ps.add(ICA_TOL);
        ps.add(RANDOM_SEED);
        ps.add(VERBOSE);
        ps.add(COND_WARN_THRESHOLD);
        return ps;
    }

    public Knowledge getKnowledge() { return this.knowledge; }
    public void setKnowledge(Knowledge knowledge) { this.knowledge = new Knowledge(knowledge); }
}