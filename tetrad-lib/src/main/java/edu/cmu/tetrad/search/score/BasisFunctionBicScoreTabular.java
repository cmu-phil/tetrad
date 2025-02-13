package edu.cmu.tetrad.search.score;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.utils.Embedding;
import edu.cmu.tetrad.util.StatUtils;
import org.apache.commons.math3.util.FastMath;
import org.ejml.dense.row.CommonOps_DDRM;
import org.ejml.simple.SimpleMatrix;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.apache.commons.math3.util.FastMath.log;

/**
 * Calculates the basis function BIC score for a given dataset. This is a generalization of the Degenerate Gaussian
 * score by adding basis functions of the continuous variables and retains the function of the degenerate Gaussian for
 * discrete variables by adding indicator variables per category.
 * <p>
 * This version uses a tabular approach to calculate the score rather than using covariance matrices.
 *
 * @author bandrews
 * @author josephramsey
 * @see DegenerateGaussianScore
 */
public class BasisFunctionBicScoreTabular implements Score {
    /**
     * A list containing nodes that represent the variables in the basis function score.
     */
    private final List<Node> variables;
    /**
     * A mapping used to store the embeddings of basis functions for continuous variables and indicator variables per
     * category for discrete variables. The key is an integer representing the index of the basis function variable or
     * indicator variable.
     */
    private final Map<Integer, List<Integer>> embedding;
    /**
     * A private and immutable field that stores a reference to the dataset used in the computation of the Basis
     * Function BIC score. This dataset serves as the foundational data against which various scoring calculations and
     * operations are performed. It is initialized during the creation of the containing object and remains constant
     * thereafter.
     */
    private final DataSet embeddedData;
    /**
     * Represents the penalty discount factor used in the Basis Function BIC (Bayesian Information Criterion) score
     * calculations. This value modifies the penalty applied for model complexity in BIC scoring, allowing for
     * adjustments in the likelihood penalty term.
     */
    private double penaltyDiscount = 2;
    /**
     * The lambda variable is used in the context of regularization for the OLS (Ordinary Least Squares) regression
     * calculations. It helps to prevent overfitting by adding a penalty term to the loss function.
     */
    private double lambda = 1e-6;

    /**
     * Constructs a BasisFunctionBicScore object with the specified parameters.
     *
     * @param dataSet         the data set on which the score is to be calculated. May contain a mixture of discrete and
     *                        continuous variables.
     * @param truncationLimit the truncation limit of the basis.
     * @param basisType       the type of basis function used in the BIC score computation.
     * @param basisScale      the basisScale factor used in the calculation of the BIC score for basis functions. All
     *                        variables are scaled to [-basisScale, basisScale], or standardized if 0.
     * @see StatUtils#basisFunctionValue(int, int, double)
     */
    public BasisFunctionBicScoreTabular(DataSet dataSet, int truncationLimit, int basisType, double basisScale) {
        this.variables = dataSet.getVariables();

        boolean usePseudoInverse = true;

        Embedding.EmbeddedData result = Embedding.getEmbeddedData(dataSet, truncationLimit, basisType, basisScale,
                usePseudoInverse);
        this.embedding = result.embedding();
        embeddedData = result.embeddedData();
    }

    /**
     * Computes OLS coefficients: beta = (Z^T Z + lambda I)^(-1) Z^T X
     */
    public static SimpleMatrix computeOLS(SimpleMatrix B, SimpleMatrix X, double lambda) {
        int numCols = B.getNumCols();
        SimpleMatrix BtB = B.transpose().mult(B);
        SimpleMatrix regularization = SimpleMatrix.identity(numCols).scale(lambda);

        // Parallelized inversion using EJML's lower-level operations
        SimpleMatrix inverse = new SimpleMatrix(numCols, numCols);
        CommonOps_DDRM.invert(BtB.plus(regularization).getDDRM(), inverse.getDDRM());

        return inverse.mult(B.transpose()).mult(X);
    }

    /**
     * Calculates the local score for a given node and its parent nodes.
     *
     * @param i       The index of the node whose score is being calculated.
     * @param parents The indices for the parent nodes of the given node.
     * @return The calculated local score as a double value.
     */
    public double localScore(int i, int... parents) {

        // Grab the embedded data for _x, _y, and _z. These are columns in the embeddedData dataset.
        List<Integer> embedded_x = embedding.get(i);
        List<Integer> embedded_z = new ArrayList<>();
        for (int value : parents) {
            embedded_z.addAll(embedding.get(value));
        }

        // For i and for the parents, form a SimpleMatrix of the embedded data.
        SimpleMatrix X_basis = new SimpleMatrix(embeddedData.getNumRows(), embedded_x.size());
        for (int _i = 0; _i < embedded_x.size(); _i++) {
            for (int j = 0; j < embeddedData.getNumRows(); j++) {
                X_basis.set(j, _i, embeddedData.getDouble(j, embedded_x.get(_i)));
            }
        }

        SimpleMatrix Z_basis = new SimpleMatrix(embeddedData.getNumRows(), embedded_z.size() + 1);

        for (int _i = 0; _i < embedded_z.size(); _i++) {
            for (int j = 0; j < embeddedData.getNumRows(); j++) {
                Z_basis.set(j, _i, embeddedData.getDouble(j, embedded_z.get(_i)));
            }
        }

        for (int j = 0; j < embeddedData.getNumRows(); j++) {
            Z_basis.set(j, embedded_z.size(), 1);
        }

        // For the tabular formulation, it doesn't matter whether we use the sum-likelihood or the sum-BIC
        // formulation. The two given identical performance for the test cases we tried. However, Chat thinks
        // the sum likelihood formulation is more correct, so we will use that. We will revisit this issue
        // later as light dawns. jdramsey 2025-2-13
        return getSequentialLocalScoreSumLikelihood(X_basis, Z_basis);
    }

    /*
     * Calculates the difference in the local score when a node `x` is added to the set of parent nodes `z` for a node
     * `y`.
     *
     * @param x The index of the node to be added.
     * @param y The index of the node whose score difference is being calculated.
     * @param z The indices of the parent nodes of the node `y`.
     * @return The difference in the local score as a double value.
     */
    public double localScoreDiff(int x, int y, int[] z) {
        return localScore(y, append(z, x)) - localScore(y, z);
    }

    /**
     * Retrieves the list of nodes representing the variables in the basis function score.
     *
     * @return a list containing the nodes that represent the variables in the basis function score.
     */
    @Override
    public List<Node> getVariables() {
        return this.variables;
    }

    /**
     * Determines if the given bump value represents an effect edge.
     *
     * @param bump the bump value to be evaluated.
     * @return true if the bump is an effect edge, false otherwise.
     */
    @Override
    public boolean isEffectEdge(double bump) {
        return bump > 0;
    }

    /**
     * Retrieves the sample size from the underlying BIC score component.
     *
     * @return the sample size as an integer
     */
    @Override
    public int getSampleSize() {
        return embeddedData.getNumRows();
    }

    /**
     * Retrieves the maximum degree from the underlying BIC score component.
     *
     * @return the maximum degree as an integer.
     */
    @Override
    public int getMaxDegree() {
        return (int) FastMath.ceil(log(getSampleSize()));
    }

    /**
     * Returns a string representation of the BasisFunctionBicScore object.
     *
     * @return A string detailing the degenerate Gaussian score penalty with the penalty discount formatted to two
     * decimal places.
     */
    @Override
    public String toString() {
        return "Basis Function Score Tabular (BFS-Tab)";
    }

    /**
     * Sets the penalty discount value, which is used to adjust the penalty term in the BIC score calculation.
     *
     * @param penaltyDiscount The multiplier on the penalty term for this score.
     */
    public void setPenaltyDiscount(double penaltyDiscount) {
        this.penaltyDiscount = penaltyDiscount;
    }

    /**
     * Sets the regularization parameter lambda, which is used in the OLS coefficient computation to control
     * overfitting.
     *
     * @param lambda The regularization parameter value to be used. A higher value applies more regularization.
     */
    public void setLambda(double lambda) {
        this.lambda = lambda;
    }

    private double getSequentialLocalScoreSumBic(SimpleMatrix X_basis, SimpleMatrix Y_basis) {
        int N = X_basis.getNumRows();
        int pX = X_basis.getNumCols();
        double totalBic = 0;

        for (int i = 0; i < pX; i++) {
            // Define parent variables for Xi
            SimpleMatrix Z = (i == 0) ? Y_basis : Y_basis.combine(0, Y_basis.numCols(), X_basis.extractMatrix(0, N, 0, i));

            // Fit regression: Xi ~ Z
            SimpleMatrix betaZ = computeOLS(Z, X_basis.extractMatrix(0, N, i, i + 1), 1e-6);
            SimpleMatrix residuals = X_basis.extractMatrix(0, N, i, i + 1).minus(Z.mult(betaZ));

            // Compute residual variance with epsilon
            double sigma_sq = computeVariance(residuals) + 1e-10;

            // Compute log-likelihood
            double logLikelihood = -0.5 * N * (Math.log(2 * Math.PI * sigma_sq) + 1);
            int k = Z.getNumCols() + 1;
            totalBic += 2 * logLikelihood - penaltyDiscount * k * Math.log(N);
            ;
        }

        return totalBic;
    }

    private double getSequentialLocalScoreSumLikelihood(SimpleMatrix X_basis, SimpleMatrix Y_basis) {
        int N = X_basis.getNumRows();
        int pX = X_basis.getNumCols();
        double totalLikelihood = 0;
        int totalDof = 0;

        for (int i = 0; i < pX; i++) {
            // Define parent variables for Xi
            SimpleMatrix Z = (i == 0) ? Y_basis : Y_basis.combine(0, Y_basis.getNumCols(), X_basis.extractMatrix(0, N, 0, i));

            // Fit regression: Xi ~ Z
            SimpleMatrix betaZ = computeOLS(Z, X_basis.extractMatrix(0, N, i, i + 1), 1e-6);
            SimpleMatrix residuals = X_basis.extractMatrix(0, N, i, i + 1).minus(Z.mult(betaZ));

            // Compute residual variance with epsilon
            double sigma_sq = computeVariance(residuals) + 1e-10;

            // Compute log-likelihood
            double logLikelihood = -0.5 * N * (Math.log(2 * Math.PI * sigma_sq) + 1);
            totalLikelihood += logLikelihood;
            totalDof += Z.getNumCols() + 1;
        }

        return 2 * totalLikelihood - penaltyDiscount * totalDof * Math.log(N);
    }

    /**
     * Computes variance of residuals: Var(R) = sum(R^2) / N
     */
    private double computeVariance(SimpleMatrix residuals) {
        return residuals.elementMult(residuals).elementSum() / residuals.getNumRows();
    }

    // Compute the covariance matrix of a dataset
    public SimpleMatrix computeCovarianceMatrix(SimpleMatrix data) {
        int N = data.getNumRows();
        SimpleMatrix mean = data.transpose().mult(new SimpleMatrix(N, 1, true, new double[N]).divide(N));
        SimpleMatrix centered = data.minus(mean.transpose());
        return centered.transpose().mult(centered).divide(N - 1);
    }
}
