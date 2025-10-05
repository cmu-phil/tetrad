///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.score;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.utils.Embedding;
import edu.cmu.tetrad.util.Matrix;
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
 * @author josephramsey
 * @author bryanandrews
 * @see DegenerateGaussianScore
 * @see BasisFunctionBicScore
 */
public class BasisFunctionBicScoreFullSample implements Score {
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
     * Singularity lambda.
     */
    private final double lambda;
    /**
     * Represents the penalty discount factor used in the Basis Function BIC (Bayesian Information Criterion) score
     * calculations. This value modifies the penalty applied for model complexity in BIC scoring, allowing for
     * adjustments in the likelihood penalty term.
     */
    private double penaltyDiscount = 2;
    /**
     * When calculation the score for X = &lt;X1 = X, X2, X3,..., Xp&gt; use the equation for X1 only, if true;
     * otherwise, use equations for all of X1, X2,...,Xp.
     */
    private boolean doOneEquationOnly;

    /**
     * Constructs a BasisFunctionBicScore object with the specified parameters.
     *
     * @param dataSet         the data set on which the score is to be calculated. May contain a mixture of discrete and
     *                        continuous variables.
     * @param truncationLimit the truncation limit of the basis.
     * @param lambda          Singularity lambda
     * @see StatUtils#basisFunctionValue(int, int, double)
     */
    public BasisFunctionBicScoreFullSample(DataSet dataSet, int truncationLimit, double lambda) {
        this.variables = dataSet.getVariables();

        Embedding.EmbeddedData result = Embedding.getEmbeddedData(dataSet, truncationLimit, 1, 1);
        this.embedding = result.embedding();
        this.lambda = lambda;
        embeddedData = result.embeddedData();
    }

    /**
     * Computes the Ordinary Least Squares (OLS) regression coefficients with L2 regularization.
     *
     * @param B      The design matrix where each row represents a sample and each column represents a feature.
     * @param X      The matrix containing the target values for corresponding samples.
     * @param lambda The regularization parameter to control overfitting (L2 regularization).
     * @return A matrix representing the OLS regression coefficients.
     */
    public static SimpleMatrix computeOLS(SimpleMatrix B, SimpleMatrix X, double lambda) {
//        if (lambda < 0) {
//            throw new IllegalArgumentException("The lambda cannot be negative for the basis function");
//        }

        int numCols = B.getNumCols();

        SimpleMatrix BtB = B.transpose().mult(B);
        SimpleMatrix inverse;

        if (lambda >= 0.0) {
            BtB = StatUtils.chooseMatrix(BtB, lambda);
//            inverse = new Matrix(BtB).inverse().getData();

//            // Parallelized inversion using EJML's lower-level operations
            inverse = new SimpleMatrix(numCols, numCols);
            CommonOps_DDRM.invert(BtB.getDDRM(), inverse.getDDRM());
        } else {
            inverse = new Matrix(BtB).chooseInverse(lambda).getData();
        }

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

        if (doOneEquationOnly) {
            embedded_x = embedded_x.subList(0, 1);
        }

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
        return "Basis Function Score BIC Full Sample (BF-BIC-FS)";
    }

    /**
     * Sets the penalty discount value, which is used to adjust the penalty term in the BIC score calculation.
     *
     * @param penaltyDiscount The multiplier on the penalty term for this score.
     */
    public void setPenaltyDiscount(double penaltyDiscount) {
        this.penaltyDiscount = penaltyDiscount;
    }

    private double getSequentialLocalScoreSumLikelihood(SimpleMatrix X_basis, SimpleMatrix Y_basis) {
        int N = X_basis.getNumRows();
        int pX = X_basis.getNumCols();
        double totalLikelihood = 0;
        int totalDof = 0;

        for (int i = 0; i < pX; i++) {
            // Define parent variables for Xi
//            SimpleMatrix Z = (i == 0) ? Y_basis : Y_basis.combine(0, Y_basis.getNumCols(), X_basis.extractMatrix(0, N, 0, i));
            SimpleMatrix Z = Y_basis;

            // Fit regression: Xi ~ Z
            SimpleMatrix x = X_basis.extractMatrix(0, N, i, i + 1);
            SimpleMatrix betaZ = computeOLS(Z, x, lambda);
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

    /**
     * When calculation the score for X = &lt;X1 = X, X2, X3,..., Xp&gt; use the equation for X1 only, if true;
     * otherwise, use equations for all of X1, X2,...,Xp.
     *
     * @param doOneEquationOnly True if only the equation for X1 is to be used for X = X1,...,Xp.     *
     */
    public void setDoOneEquationOnly(boolean doOneEquationOnly) {
        this.doOneEquationOnly = doOneEquationOnly;
    }
}

