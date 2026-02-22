package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Parameters;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Utility class that provides a set of predefined metrics for evaluating
 * Directed Acyclic Graphs (DAGs).
 *
 * The provided metrics are implemented as factory methods that return instances
 * of the {@link DagMetric} functional interface. These metrics are commonly used
 * to assess the quality, adequacy, or fitness of a given DAG structure based
 * on observational data and various scoring or fitting criteria.
 *
 * This class is designed to be non-instantiable.
 */
public final class DagMetrics {

    private DagMetrics() {}

    // --------- helper for local-score summation ---------
    private static double sumLocalScores(DataSet data, Graph dag, edu.cmu.tetrad.search.score.Score score) {
        dag = GraphUtils.replaceNodes(dag, data.getVariables());
        List<Node> nodes = data.getVariables();

        double total = 0.0;
        for (Node node : nodes) {
            List<Node> parents = dag.getParents(node);
            int i = nodes.indexOf(node);
            int[] parentIdx = parents.stream().mapToInt(nodes::indexOf).toArray();
            total += score.localScore(i, parentIdx);
        }
        return total;
    }

    /**
     * Computes the Minimax t-RFF BIC metric for a given data set and directed acyclic graph (DAG).
     * The Minimax t-RFF BIC score is a general mixed BIC-based metric that evaluates the fit of the graph
     * to the dataset using a specific scoring algorithm.
     *
     * @return A {@link DagMetric} instance that calculates the Minimax t-RFF BIC metric, where higher scores
     *         indicate a better fit according to the evaluation criterion.
     */
    public static @NotNull DagMetric minimaxTrffBic() {
        return (data, dag) -> {
            var algScore = new edu.cmu.tetrad.algcomparison.score.MinimaxTRffBicScore();
            var score = (edu.cmu.tetrad.search.score.MinimaxTRffBicScore) algScore.getScore(data, new Parameters());
            double s = sumLocalScores(data, dag, score);
            return new DagMetricResult("Minimax t-RFF BIC", s, "General Mixed BIC Score", DagMetricResult.Better.HIGHER);
        };
    }

    /**
     * Computes the Legendre BIC score for a given dataset and DAG.
     * @return A {@link DagMetric} instance that calculates the Legendre BIC metric.
     */
    public static @NotNull DagMetric legendreBic() {
        return (data, dag) -> {
            var algScore = new edu.cmu.tetrad.algcomparison.score.MinimaxLegendreScore();
            var score = (edu.cmu.tetrad.search.score.MinimaxLegendreScore) algScore.getScore(data, new Parameters());
            double s = sumLocalScores(data, dag, score);
            return new DagMetricResult("Legendre BIC", s, "General Mixed BIC Score", DagMetricResult.Better.HIGHER);
        };
    }

    /**
     * Computes the FFML score for a given dataset and DAG.
     * @return A {@link DagMetric} instance that calculates the FFML metric.
     */
    public static @NotNull DagMetric ffml() {
        return (data, dag) -> {
            var algScore = new edu.cmu.tetrad.algcomparison.score.FfMl();
            var score = (edu.cmu.tetrad.search.score.FfMl) algScore.getScore(data, new Parameters());
            double s = sumLocalScores(data, dag, score);
            return new DagMetricResult("FFML", s, "General Mixed Likelihood Score", DagMetricResult.Better.HIGHER);
        };
    }

    /**
     * Computes the Linear Gaussian BIC score for a given dataset and DAG.
     * @return A {@link DagMetric} instance that calculates the Linear Gaussian BIC metric.
     */
    public static @NotNull DagMetric semBic() {
        return (data, dag) -> {
            var algScore = new edu.cmu.tetrad.algcomparison.score.SemBicScore();
            var score = (edu.cmu.tetrad.search.score.SemBicScore) algScore.getScore(data, new Parameters());
            double s = sumLocalScores(data, dag, score);
            return new DagMetricResult("LG BIC", s, "Linear Gaussian BIC", DagMetricResult.Better.HIGHER);
        };
    }

    /**
     * Computes the Linear Gaussian Chi Square score for a given dataset and DAG.
     * @return A {@link DagMetric} instance that calculates the Linear Gaussian Chi Square metric.
     */
    public static @NotNull DagMetric lgChiSquare() {
        return (data, dag) -> {
            SemPm pm = new SemPm(dag);
            SemEstimator est = new SemEstimator(data, pm);
            SemIm im = est.estimate();
            return new DagMetricResult("LG Chi Square", im.getChiSquare(), "Linear Gaussian Chi Square", DagMetricResult.Better.LOWER);
        };
    }

    /**
     * Computes the Comparative Fit Index (CFI) for a given dataset and DAG.
     * @return A {@link DagMetric} instance that calculates the CFI metric.
     */
    public static @NotNull DagMetric cfi() {
        return (data, dag) -> {
            SemPm pm = new SemPm(dag);
            SemEstimator est = new SemEstimator(data, pm);
            SemIm im = est.estimate();
            return new DagMetricResult("CFI", im.getCfi(), "Comparative Fit Index", DagMetricResult.Better.HIGHER);
        };
    }

    /**
     * Computes the Model P-Value for a given dataset and DAG.
     * @return A {@link DagMetric} instance that calculates the Model P-Value metric.
     */
    public static @NotNull DagMetric lgModelP() {
        return (data, dag) -> {
            SemPm pm = new SemPm(dag);
            SemEstimator est = new SemEstimator(data, pm);
            SemIm im = est.estimate();
            return new DagMetricResult("Model P", im.getPValue(), "LG Model P-Value", DagMetricResult.Better.HIGHER);
        };
    }

    /**
     * Computes the Root Mean Square Error of Approximation (RMSEA)
     * @return A {@link DagMetric} instance that calculates the RMSEA metric.
     */
    public static @NotNull DagMetric rmsea() {
        return (data, dag) -> {
            SemPm pm = new SemPm(dag);
            SemEstimator est = new SemEstimator(data, pm);
            SemIm im = est.estimate();
            return new DagMetricResult("RMSEA", im.getRmsea(), "RMSEA", DagMetricResult.Better.LOWER);
        };
    }

    /**
     * Computes the Maximum Mean Discrepancy (MMD)² score for a given dataset and DAG.
     * @return A {@link DagMetric} instance that calculates the MMD² metric.
     */
    public static @NotNull DagMetric mmd2() {
        return (data, dag) -> {
            TrainedDagSimulatorGNM.Params params = new TrainedDagSimulatorGNM.Params();
            params.seed = System.nanoTime();

            TrainedDagSimulatorGNM sim = new TrainedDagSimulatorGNM(data, dag, params);
            sim.fit();

            TrainedDagSimulatorGNM.SimResult simData = sim.simulate(10000);//data.getNumRows());

            AdequacyReport report = TrainedDagAdequacy.evaluate(
                    data,
                    simData.toDataSet(),
                    sim,
                    new AdequacyParams()
            );

            return new DagMetricResult("MMD2", report.mmd2, "Maximum Mean Discrepancy squared", DagMetricResult.Better.LOWER);
        };
    }
}