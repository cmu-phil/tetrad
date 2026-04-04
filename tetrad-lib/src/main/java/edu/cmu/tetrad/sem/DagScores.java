package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;

/**
 * Utility class containing various scoring methods for Directed Acyclic Graphs (DAGs).
 * This class provides implementations for scoring DAGs based on metrics like BIC (Bayesian Information Criterion)
 * and MMD^2 (Maximum Mean Discrepancy with Radial Basis Functions). Specific implementations for
 * Linear Gaussian, Discrete, and Mixed Conditional Gaussian BIC variants are included, as well as
 * MMD^2 scoring with exact RBF.
 *
 * This class is not intended to be instantiated.
 */
public final class DagScores {

    private DagScores() {}

    // -----------------------------
    // BIC variants (fill in)
    // -----------------------------

    /**
     * Computes the structural equation model Bayesian Information Criterion (SEM-BIC) score
     * for a given dataset and directed acyclic graph (DAG) under the assumption of linear
     * Gaussian relationships.
     *
     * @param data the dataset to compute the SEM-BIC score for
     * @param dag the directed acyclic graph (DAG) representing the structure of the model
     * @return the SEM-BIC score for the given dataset and DAG
     */
    public static double semBicLinearGaussian(DataSet data, Graph dag) {
        return semBicLinearGaussian(data, dag, 1.0);
    }

    /**
     * Computes the structural equation model Bayesian Information Criterion (SEM-BIC) score
     * for a given dataset and directed acyclic graph (DAG) under the assumption of
     * linear Gaussian relationships, with a specified penalty discount factor.
     *
     * @param data the dataset to compute the SEM-BIC score for
     * @param dag the directed acyclic graph (DAG) representing the structure of the model
     * @param penaltyDiscount the penalty discount factor to adjust the model complexity penalty
     * @return the SEM-BIC score for the given dataset and DAG
     */
    public static double semBicLinearGaussian(DataSet data, Graph dag, double penaltyDiscount) {
        // TODO: Plug in the Tetrad class you want here.
        // Typical pattern (conceptually):
        //   var score = new SemBicScore(data);
        //   score.setPenaltyDiscount(penaltyDiscount);
        //   return score.scoreDag(dag) OR sum local scores over nodes/parents.
        throw new UnsupportedOperationException("Wire this to your SemBicScore implementation.");
    }

    /**
     * Computes the Bayesian Information Criterion (BIC) score for a given dataset
     * and directed acyclic graph (DAG) under the assumption of discrete variables.
     *
     * @param data the dataset to compute the BIC score for
     * @param dag the directed acyclic graph (DAG) representing the structure of the model
     * @return the BIC score for the given dataset and DAG
     */
    public static double bicDiscrete(DataSet data, Graph dag) {
        // TODO: Plug in your discrete BIC scorer (if desired).
        throw new UnsupportedOperationException("Wire this to your discrete BIC scorer.");
    }

    /**
     * Computes the Bayesian Information Criterion (BIC) score for a given dataset
     * and directed acyclic graph (DAG) under the assumption of mixed or conditional
     * Gaussian relationships.
     *
     * @param data the dataset to compute the BIC score for
     * @param dag the directed acyclic graph (DAG) representing the structure of the model
     * @return the BIC score for the given dataset and DAG
     */
    public static double bicMixedConditionalGaussian(DataSet data, Graph dag) {
        // TODO: Plug in your mixed/CG BIC scorer (if desired).
        throw new UnsupportedOperationException("Wire this to your mixed CG-BIC scorer.");
    }

    // -----------------------------
    // MMD^2 (exact RBF) using the matrices you already build
    // -----------------------------

    /**
     * Computes the Maximum Mean Discrepancy (MMD) score using the Radial Basis Function (RBF) kernel
     * between the real dataset and a simulated dataset. This method expects two datasets
     * (real and simulated) but does not directly accept a simulated dataset in its parameter list.
     *
     * @param data the real dataset to evaluate
     * @param dag the directed acyclic graph (DAG) representing the structure of the model
     * @return the exact MMD score using the RBF kernel
     * @throws UnsupportedOperationException when a simulated dataset is required but not provided
     */
    public static double mmd2ExactRbf(DataSet data, Graph dag) {
        // This metric needs *two* datasets (real vs simulated). So this method signature
        // isn't sufficient unless you pass in the simulated dataset via a different metric.
        //
        // Recommended: register MMD as a metric that closes over the simulated dataset,
        // OR compute simulated inside the metric (if you want).
        throw new UnsupportedOperationException(
                "MMD needs real+simulated. Use the overload below that takes both.");
    }

    /**
     * Computes the Maximum Mean Discrepancy (MMD) score using the Radial Basis Function (RBF) kernel
     * between two datasets: real and simulated. This method evaluates the dissimilarity between
     * the two data distributions.
     *
     * @param real the real dataset represented as a {@code DataSet} object
     * @param simulated the simulated dataset represented as a {@code DataSet} object
     * @param sigma the width of the RBF kernel; a smaller value emphasizes nearby data points,
     *              while a larger value considers global structure
     * @param maxRows the maximum number of rows to use from the datasets; helpful for reducing
     *                computation time by limiting the dataset size
     * @return the computed MMD score as a {@code double} value
     */
    public static double mmd2ExactRbf(DataSet real, DataSet simulated, double sigma, int maxRows) {
        double[][] X = toNumericMatrix(real);       // or your helper
        double[][] Y = toNumericMatrix(simulated);  // or your helper
        return ExactRbfMMD.compute(X, Y, sigma, maxRows);
    }

    /**
     * Converts the given dataset into a numeric matrix representation, where each value
     * in the dataset is cast to a double.
     *
     * @param ds the dataset to be converted into a numeric matrix
     * @return a 2D array of doubles representing the numeric values of the dataset,
     *         where each row corresponds to the rows in the dataset and each column
     *         corresponds to the columns in the dataset
     */
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