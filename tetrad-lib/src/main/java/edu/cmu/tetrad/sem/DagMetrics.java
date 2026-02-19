package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Parameters;
import org.jetbrains.annotations.NotNull;

import java.util.List;

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

    public static @NotNull DagMetric minimaxTrffBic() {
        return (data, dag) -> {
            var algScore = new edu.cmu.tetrad.algcomparison.score.MinimaxTRffBicScore();
            var score = (edu.cmu.tetrad.search.score.MinimaxTRffBicScore) algScore.getScore(data, new Parameters());
            double s = sumLocalScores(data, dag, score);
            return new DagMetricResult("Minimax t-RFF BIC", s, "General Mixed BIC Score");
        };
    }

    public static @NotNull DagMetric legendreBic() {
        return (data, dag) -> {
            var algScore = new edu.cmu.tetrad.algcomparison.score.MinimaxLegendreScore();
            var score = (edu.cmu.tetrad.search.score.MinimaxLegendreScore) algScore.getScore(data, new Parameters());
            double s = sumLocalScores(data, dag, score);
            return new DagMetricResult("Legendre BIC", s, "General Mixed BIC Score");
        };
    }

    public static @NotNull DagMetric ffml() {
        return (data, dag) -> {
            var algScore = new edu.cmu.tetrad.algcomparison.score.FfMl();
            var score = (edu.cmu.tetrad.search.score.FfMl) algScore.getScore(data, new Parameters());
            double s = sumLocalScores(data, dag, score);
            return new DagMetricResult("FFML", s, "General Mixed Likelihood Score");
        };
    }

    public static @NotNull DagMetric semBic() {
        return (data, dag) -> {
            var algScore = new edu.cmu.tetrad.algcomparison.score.SemBicScore();
            var score = (edu.cmu.tetrad.search.score.SemBicScore) algScore.getScore(data, new Parameters());
            double s = sumLocalScores(data, dag, score);
            return new DagMetricResult("LG BIC", s, "Linear Gaussian BIC");
        };
    }

    public static @NotNull DagMetric lgChiSquare() {
        return (data, dag) -> {
            SemPm pm = new SemPm(dag);
            SemEstimator est = new SemEstimator(data, pm);
            SemIm im = est.estimate();
            return new DagMetricResult("LG Chi Square", im.getChiSquare(), "Linear Gaussian Chi Square");
        };
    }

    public static @NotNull DagMetric cfi() {
        return (data, dag) -> {
            SemPm pm = new SemPm(dag);
            SemEstimator est = new SemEstimator(data, pm);
            SemIm im = est.estimate();
            return new DagMetricResult("CFI", im.getCfi(), "Comparative Fit Index");
        };
    }

    public static @NotNull DagMetric lgModelP() {
        return (data, dag) -> {
            SemPm pm = new SemPm(dag);
            SemEstimator est = new SemEstimator(data, pm);
            SemIm im = est.estimate();
            return new DagMetricResult("Model P", im.getPValue(), "LG Model P-Value");
        };
    }

    public static @NotNull DagMetric rmsea() {
        return (data, dag) -> {
            SemPm pm = new SemPm(dag);
            SemEstimator est = new SemEstimator(data, pm);
            SemIm im = est.estimate();
            return new DagMetricResult("RMSEA", im.getRmsea(), "RMSEA");
        };
    }

    public static @NotNull DagMetric mmd2() {
        return (data, dag) -> {
            TrainedDagSimulatorGNM.Params params = new TrainedDagSimulatorGNM.Params();
            params.seed = System.nanoTime();

            TrainedDagSimulatorGNM sim = new TrainedDagSimulatorGNM(data, dag, params);
            sim.fit();

            TrainedDagSimulatorGNM.SimResult simData = sim.simulate(data.getNumRows());

            AdequacyReport report = TrainedDagAdequacy.evaluate(
                    data,
                    simData.toDataSet(),
                    sim,
                    new AdequacyParams()
            );

            return new DagMetricResult("MMD2", report.mmd2, "Maximum Mean Discrepancy squared");
        };
    }
}