// src/main/java/edu/cmu/tetrad/algcomparison/algorithm/mag/Gps.java
package edu.cmu.tetrad.algcomparison.algorithm.mag;

import edu.cmu.tetrad.algcomparison.algorithm.*;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.graph.EdgeListGraph;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * GPS (score-based MAG search using RICF-BIC).
 * Wraps edu.cmu.tetrad.search.mag.gps.Gps for AlgComparison.
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "GPS (RICF-BIC)",
        command = "gps",
        algoType = AlgType.allow_latent_common_causes // MAGs allow latents
)
@Bootstrapping
public final class Gps extends AbstractBootstrapAlgorithm implements Algorithm,
        HasKnowledge, ReturnsBootstrapGraphs, LatentStructureAlgorithm,
        TakesCovarianceMatrix {

    @Serial
    private static final long serialVersionUID = 1L;

    private Knowledge knowledge = new Knowledge();

    public Gps() { }

    @Override
    protected Graph runSearch(DataModel dataModel, Parameters parameters) throws InterruptedException {
        // Accept either raw continuous data or a covariance matrix.
        ICovarianceMatrix cov;
        if (dataModel instanceof ICovarianceMatrix) {
            cov = (ICovarianceMatrix) dataModel;
        } else if (dataModel instanceof DataSet ds) {
            if (!ds.isContinuous()) {
                throw new IllegalArgumentException("GPS requires continuous data or a covariance matrix.");
            }
            cov = new CovarianceMatrix(ds);
        } else {
            throw new IllegalArgumentException("Unsupported DataModel for GPS: " + dataModel.getClass());
        }

        // Parameters (use common Tetrad keys; fallback defaults are conservative)
        final double tolerance = parameters.getDouble(Params.FAST_ICA_TOLERANCE, 1e-6);
        final double ridge = parameters.getDouble(Params.GIN_RIDGE, 0.0);
        final int restarts = parameters.getInt(Params.NUM_STARTS, 10);
        final int maxIters = parameters.getInt(Params.MAX_ITERATIONS, 2000);
        final boolean verbose = parameters.getBoolean(Params.VERBOSE, false);

        // Build + run search
        edu.cmu.tetrad.search.mag.gps.Gps core =
                new edu.cmu.tetrad.search.mag.gps.Gps(cov, tolerance, ridge, restarts, maxIters);

        Graph result = core.search();

        if (verbose) {
            // small, safe summary
            System.out.println("[GPS] nodes=" + result.getNumNodes()
                               + " edges=" + result.getNumEdges()
                               + " restarts=" + restarts + " maxIters=" + maxIters);
        }
        return result;
    }

    /**
     * What to compare against in AlgComparison. For MAG algorithms, we typically compare as a MAG.
     * If your comparison framework expects a PAG (depending on the study), you can convert here.
     * Keeping identity is the safest default.
     */
    @Override
    public Graph getComparisonGraph(Graph graph) {
        // If needed: return GraphTransforms.magToPag(graph);
        return new EdgeListGraph(graph);
    }

    @Override
    public String getDescription() {
        return "GPS: greedy local search over MAGs scored by RICF-BIC (uses RICF likelihood).";
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        List<String> params = new ArrayList<>();
        params.add(Params.FAST_ICA_TOLERANCE);
        params.add(Params.GIN_RIDGE);
        params.add(Params.NUM_STARTS);
        params.add(Params.MAX_ITERATIONS);
        params.add(Params.VERBOSE);
        return params;
    }

    @Override
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    @Override
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = new Knowledge(knowledge);
    }
}