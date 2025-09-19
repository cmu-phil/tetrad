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
import edu.cmu.tetrad.search.FastIca;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.RandomUtil;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for the Two-Step algorithm (ICA-based, allows cycles) using FastIca.
 * Returns the directed graph and prints the B matrix to stdout.
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "Two-Step (FastICA, cyclic)",
        command = "twostep",
        algoType = AlgType.forbid_latent_common_causes
)
@Deprecated
@Bootstrapping
public class TwoStep extends AbstractBootstrapAlgorithm implements Algorithm {

    @Serial
    private static final long serialVersionUID = 2L;

    private Knowledge knowledge = new Knowledge(); // kept for API parity (not used here)

    public TwoStep() { }

    @Override
    protected Graph runSearch(DataModel dataModel, Parameters ignored) throws InterruptedException {
        DataSet data = (DataSet) dataModel;

        // -------- Hardcoded settings (tweak here while iterating) --------
        // Lasso / mask
        double lambda = 0.01;
        boolean normalizeLossByN = false;   // scale λ by n in Gram-lasso (often stabilizes selection)
        boolean useAdaptive = true;        // Adaptive Lasso weights from OLS
        double adaptiveGamma = 1.0;        // 1 / (|beta_ols| + eps)^gamma
        double maskThr = 0.01;             // threshold for support from Lasso (mask)
        double coefThr = 0.01;             // final pruning threshold for B -> graph

        // ICA (FastICA)
        int icaMaxIter = 10000;
        double icaTol = 1e-5;
        long seed = RandomUtil.getInstance().nextLong();                  // used internally by FastICA’s RandomUtil
        boolean verbose = true;
        double condWarn = 1e8;

        int icaAlg = FastIca.PARALLEL;     // PARALLEL (symmetric) or DEFLATION
        int icaFunc = FastIca.LOGCOSH;     // LOGCOSH or EXP
        double icaAlpha = 1.1;             // for LOGCOSH in [1,2]
        boolean icaRowNorm = false;

        // Two-cycle breaker (final deterministic pass)
        boolean break2 = true;
        double twoCycleMinAbs = coefThr;   // keep the same as coefThr by default

        // -------- Configure and run the core algorithm --------
        edu.cmu.tetrad.search.TwoStep algo = new edu.cmu.tetrad.search.TwoStep();

        // Lasso & mask
        algo.setLambda(lambda);
        algo.setNormalizeLossByN(normalizeLossByN);
        algo.setUseAdaptiveLasso(useAdaptive);
        algo.setAdaptiveGamma(adaptiveGamma);
        algo.setMaskThreshold(maskThr);
        algo.setCoefThreshold(coefThr);

        // ICA general
        algo.setIcaMaxIter(icaMaxIter);
        algo.setIcaTol(icaTol);
        algo.setRandomSeed(seed);
        algo.setVerbose(verbose);
        algo.setCondWarnThreshold(condWarn);

        // FastICA specifics
        algo.setFastIcaAlgorithm(icaAlg);
        algo.setFastIcaFunction(icaFunc);
        algo.setFastIcaAlpha(icaAlpha);
        algo.setFastIcaRowNorm(icaRowNorm);

        // Two-cycle breaker
        algo.setBreakTwoCyclesEnabled(break2);
        algo.setTwoCycleMinAbs(twoCycleMinAbs);

        // Run
        edu.cmu.tetrad.search.TwoStep.Result result = algo.search(data);

        // Print B for visibility (no UI slot for it)
        System.out.println("[TwoStep] Estimated B matrix:");
        System.out.println(result.B);

        // Optional: also print aligned mixing A ~ (I - B)^{-1}
        // System.out.println("[TwoStep] Mixing A (aligned):");
        // System.out.println(algo.getLastA());

        return result.graph;
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        // Two-Step may return cycles; just return as-is for comparison.
        return graph;
    }

    @Override
    public String getDescription() {
        return "Two-Step (FastICA-based, allows cycles) over continuous data.";
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        // Parameters are hardcoded above for now; return an empty list.
        return new ArrayList<>();
    }

    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }
}