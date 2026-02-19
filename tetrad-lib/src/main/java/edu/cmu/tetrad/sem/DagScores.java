package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;

public final class DagScores {

    private DagScores() {}

    // -----------------------------
    // BIC variants (fill in)
    // -----------------------------

    public static double semBicLinearGaussian(DataSet data, Graph dag) {
        return semBicLinearGaussian(data, dag, 1.0);
    }

    public static double semBicLinearGaussian(DataSet data, Graph dag, double penaltyDiscount) {
        // TODO: Plug in the Tetrad class you want here.
        // Typical pattern (conceptually):
        //   var score = new SemBicScore(data);
        //   score.setPenaltyDiscount(penaltyDiscount);
        //   return score.scoreDag(dag) OR sum local scores over nodes/parents.
        throw new UnsupportedOperationException("Wire this to your SemBicScore implementation.");
    }

    public static double bicDiscrete(DataSet data, Graph dag) {
        // TODO: Plug in your discrete BIC scorer (if desired).
        throw new UnsupportedOperationException("Wire this to your discrete BIC scorer.");
    }

    public static double bicMixedConditionalGaussian(DataSet data, Graph dag) {
        // TODO: Plug in your mixed/CG BIC scorer (if desired).
        throw new UnsupportedOperationException("Wire this to your mixed CG-BIC scorer.");
    }

    // -----------------------------
    // MMD^2 (exact RBF) using the matrices you already build
    // -----------------------------

    public static double mmd2ExactRbf(DataSet data, Graph dag) {
        // This metric needs *two* datasets (real vs simulated). So this method signature
        // isn't sufficient unless you pass in the simulated dataset via a different metric.
        //
        // Recommended: register MMD as a metric that closes over the simulated dataset,
        // OR compute simulated inside the metric (if you want).
        throw new UnsupportedOperationException(
                "MMD needs real+simulated. Use the overload below that takes both.");
    }

    public static double mmd2ExactRbf(DataSet real, DataSet simulated, double sigma, int maxRows) {
        double[][] X = toNumericMatrix(real);       // or your helper
        double[][] Y = toNumericMatrix(simulated);  // or your helper
        return ExactRbfMMD.compute(X, Y, sigma, maxRows);
    }

    public static double[][] toNumericMatrix(DataSet ds) {
        int rows = ds.getNumRows();
        int cols = ds.getNumColumns();

        double[][] X = new double[rows][cols];

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                X[i][j] = ds.getDouble(i, j);
            }
        }
        return X;
    }
}