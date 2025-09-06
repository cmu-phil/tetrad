package edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag;

import edu.cmu.tetrad.algcomparison.algorithm.*;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.annotation.Experimental;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.RawMarginalIndependenceTest;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * GIN (Generalized Independent Noise Search)
 *
 * @author josephramsey
 * @version $Id: $Id
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "GIN",
        command = "gin",
        algoType = AlgType.forbid_latent_common_causes
)
@Bootstrapping
@Experimental
public class Gin extends AbstractBootstrapAlgorithm implements Algorithm, TakesIndependenceWrapper,
        ReturnsBootstrapGraphs, TakesCovarianceMatrix {
    @Serial
    private static final long serialVersionUID = 23L;

    private IndependenceWrapper test;

    public Gin() {
        // Used in reflection; do not delete.
    }

    @Override
    protected Graph runSearch(DataModel dataModel, Parameters parameters) throws InterruptedException {
        DataSet dataSet = (DataSet) dataModel;
        IndependenceTest itest = this.test.getTest(dataSet, parameters);

        if (!(itest instanceof RawMarginalIndependenceTest)) {
            throw new IllegalArgumentException("Test must implement RawMarginalIndependenceTest");
        }

        edu.cmu.tetrad.search.Gin gin = new edu.cmu.tetrad.search.Gin(
                parameters.getDouble(Params.ALPHA),
                (RawMarginalIndependenceTest) itest
        );

        gin.setVerbose(parameters.getBoolean(Params.VERBOSE));

        // Always keep these numerics; they’re cheap and robust.
        safeCall(() -> gin.setWhitenBeforeSVD(true));
        safeCall(() -> gin.setRidge(1e-8));

        // === Auto-pick a preset (fast, no extra passes) ===
        applyPresetFor(dataSet, itest, gin);

        return gin.search(dataSet);
    }

    @Override
    public Graph getComparisonGraph(Graph graph) {
        return new EdgeListGraph(graph);
    }

    @Override
    public String getDescription() {
        return "GIN (Generalized Independent Noise Search)";
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        ArrayList<String> params = new ArrayList<>();
        params.add(Params.ALPHA);
        params.add(Params.VERBOSE);
        return params;
    }

    @Override
    public IndependenceWrapper getIndependenceWrapper() {
        return this.test;
    }

    @Override
    public void setIndependenceWrapper(IndependenceWrapper test) {
        this.test = test;
    }

    // ---------------------------------------------------------------------
    // Preset selection (rule-based; zero extra runtime)
    // ---------------------------------------------------------------------

    private static void applyPresetFor(DataSet data, IndependenceTest itest, edu.cmu.tetrad.search.Gin gin) {
        final int n = data.getNumRows();
        final String tname = (itest == null) ? "" : itest.getClass().getSimpleName().toLowerCase();

        // Baseline by n
        Preset preset;
//        if (n <= 300) preset = Preset.CONSERVATIVE;
//        else if (n >= 1500) preset = Preset.EXPLORATORY;
//        else preset = Preset.CONSERVATIVE;

        preset = Preset.CONSERVATIVE;

//        // Nudge based on “strength” of the marginal test (very rough heuristic)
//        boolean strong = tname.contains("kci") || tname.contains("hsic");
//        boolean weak   = tname.contains("fisher") || tname.contains("z") || tname.contains("pearson");
//
//        if (strong && n >= 600) {
//            preset = preset.bumpMoreExploratory();
//        } else if (weak && n <= 800) {
//            preset = preset.bumpMoreConservative();
//        }

        // Apply the final preset
        switch (preset) {
            case CONSERVATIVE -> applyConservative(gin);
            case BALANCED     -> applyBalanced(gin);
            case EXPLORATORY  -> applyExploratory(gin);
        }
    }

    private enum Preset { CONSERVATIVE, BALANCED, EXPLORATORY;
        Preset bumpMoreExploratory() {
            return switch (this) {
                case CONSERVATIVE -> BALANCED;
                case BALANCED -> EXPLORATORY;
                case EXPLORATORY -> EXPLORATORY;
            };
        }
        Preset bumpMoreConservative() {
            return switch (this) {
                case EXPLORATORY -> BALANCED;
                case BALANCED -> CONSERVATIVE;
                case CONSERVATIVE -> CONSERVATIVE;
            };
        }
    }

    // --- Preset definitions (use try/catch so it compiles even if setters are missing) ---

    /** Good first try (default). */
    private static void applyBalanced(edu.cmu.tetrad.search.Gin g) {
//        safeCall(() -> g.setAsymmetryDelta(0.08)); // prefer direction by ≥ 0.08
//        safeCall(() -> g.setAddMargin(0.002));     // require p ≥ alpha + 0.002
//        safeCall(() -> g.setGapThreshold(0.90));   // σ_min/σ_next ≤ 0.90
//        safeCall(() -> g.setMaxInDegree(5));       // ≤ 1 parent per latent
//        safeCall(() -> g.setRidge(1e-2));
    }

    /** Fewer false edges; for small n or noisier linear tests. */
    private static void applyConservative(edu.cmu.tetrad.search.Gin g) {
//        safeCall(() -> g.setPureMode(true));
//        safeCall(() -> g.setPureGapThreshold(0.95));
//        safeCall(() -> g.setPureMinVarE(.2));
//        safeCall(() -> g.setAsymmetryDelta(0.12));
//        safeCall(() -> g.setAddMargin(0.005));
//        safeCall(() -> g.setGapThreshold(0.85));
//        safeCall(() -> g.setMaxInDegree(1));
//        safeCall(() -> g.setRidge(3e-8));
    }

    /** More recall; when signals are strong and/or n is large. */
    private static void applyExploratory(edu.cmu.tetrad.search.Gin gin) {
//        safeCall(() -> g.setAsymmetryDelta(0.05));
//        safeCall(() -> g.setAddMargin(0.001));
//        safeCall(() -> g.setGapThreshold(0.95));
//        safeCall(() -> g.setMaxInDegree(2));
//        safeCall(() -> g.setRidge(1e-8));

//        gin.setPureMode(false);
//        gin.setUseHoldout(true);
//        gin.setConsensusRepeats(5);
//        gin.setConsensusMode(edu.cmu.tetrad.search.Gin.ConsensusMode.MEDIAN);
//        gin.setGapThreshold(0.8);   // a tad stricter
    }

    // Utility: ignore missing setter methods so this compiles on older Gin versions too.
    private static void safeCall(Runnable r) {
        try { r.run(); } catch (Throwable ignore) { }
    }
}