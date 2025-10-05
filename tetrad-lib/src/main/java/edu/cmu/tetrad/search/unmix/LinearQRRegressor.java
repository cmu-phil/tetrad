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

package edu.cmu.tetrad.search.unmix;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import org.ejml.simple.SimpleMatrix;

import java.util.List;

/**
 * Linear regression using EJML's QR-based solve (stable). Model: y = beta0 + sum_j beta_j * x_j. If the design is
 * ill-conditioned, applies a tiny ridge to stabilize.
 */
public class LinearQRRegressor implements ResidualRegressor {
    /**
     * Threshold for the condition number of a matrix. If the condition number exceeds this threshold,
     * ridge regression is triggered to address numerical instability issues.
     */
    private final double condWarn;   // condition number threshold to trigger ridge
    // knobs
    /**
     * Tiny ridge added on ill-conditioning to stabilize regression coefficients.
     */
    private double ridgeEps;   // tiny ridge added on ill-conditioning
    /**
     * Represents the coefficients of a linear regression model, including the intercept as the first element.
     * This matrix is structured as a column vector with dimensions (p+1) x 1, where p is the number of predictors.
     */
    private SimpleMatrix B;     // (p+1) x 1 coefficients, intercept first
    /**
     * Column indices of parents in the *fitted* dataset schema.
     */
    private int[] parentCols;   // column indices of parents in the *fitted* dataset schema
    /**
     * Index of the response variable in the dataset schema.
     */
    private int yCol;

    /**
     * Constructor with default values for ridgeEps and condWarn.
     */
    public LinearQRRegressor() {
        this(1e-8, 1e10); // safe defaults
    }

    /**
     * Constructs a LinearQRRegressor instance with specified ridgeEps and condWarn parameters.
     *
     * @param ridgeEps The threshold for applying L2 ridge regularization when ill-conditioning is detected.
     * @param condWarn A warning threshold indicating significant matrix condition number degradation.
     */
    public LinearQRRegressor(double ridgeEps, double condWarn) {
        this.ridgeEps = ridgeEps;
        this.condWarn = condWarn;
    }

    /**
     * Computes the condition number of a symmetric, positive-definite matrix. The condition number
     * is calculated as the ratio of the maximum eigenvalue to the minimum eigenvalue. If the matrix
     * has non-positive eigenvalues or if eigenvalue decomposition fails, the method returns
     * Double.POSITIVE_INFINITY.
     *
     * @param A The symmetric, positive-definite matrix for which the condition number is computed.
     * @return The condition number of the matrix, or Double.POSITIVE_INFINITY if the computation
     *         encounters an error or the matrix has non-positive eigenvalues.
     */
    private static double conditionNumberSymPD(SimpleMatrix A) {
        // For small p (typical here), eig is fine; fall back to NaN if it fails
        try {
            var evd = A.eig();
            double min = Double.POSITIVE_INFINITY, max = 0.0;
            for (int i = 0; i < evd.getNumberOfEigenvalues(); i++) {
                double re = evd.getEigenvalue(i).getReal();
                if (re <= 0) continue;
                min = Math.min(min, re);
                max = Math.max(max, re);
            }
            if (min == Double.POSITIVE_INFINITY || max == 0.0) return Double.POSITIVE_INFINITY;
            return max / min;
        } catch (Exception e) {
            return Double.POSITIVE_INFINITY;
        }
    }

    /**
     * Fits the linear regression model to the given data.
     *
     * @param data    The dataset containing the data.
     * @param target  The target node for regression.
     * @param parents The parent nodes for regression.
     */
    @Override
    public void fit(DataSet data, Node target, List<Node> parents) {
        this.yCol = data.getColumn(target);
        this.parentCols = parents.stream().mapToInt(data::getColumn).toArray();

        int n = data.getNumRows();
        int p = parentCols.length;

        // Intercept-only fast path
        if (p == 0) {
            double mean = 0.0;
            for (int i = 0; i < n; i++) mean += data.getDouble(i, yCol);
            mean /= Math.max(n, 1);
            this.B = new SimpleMatrix(p + 1, 1);
            this.B.set(0, 0, mean);
            return;
        }

        // Build design with intercept
        SimpleMatrix X = new SimpleMatrix(n, p + 1);
        for (int i = 0; i < n; i++) {
            X.set(i, 0, 1.0);
            for (int j = 0; j < p; j++) {
                X.set(i, j + 1, data.getDouble(i, parentCols[j]));
            }
        }
        SimpleMatrix y = new SimpleMatrix(n, 1);
        for (int i = 0; i < n; i++) y.set(i, 0, data.getDouble(i, yCol));

        // Try QR solve first
        SimpleMatrix beta = null;
        try {
            beta = X.solve(y); // QR under the hood for overdetermined systems
            // Check conditioning via (X^T X) condition number (cheap proxy)
            SimpleMatrix XtX = X.transpose().mult(X);
            double cond = conditionNumberSymPD(XtX);
            if (!Double.isFinite(cond) || cond > condWarn) {
                // add tiny ridge (do NOT penalize intercept at (0,0))
                int d = XtX.numRows();
                for (int j = 1; j < d; j++) {
                    XtX.set(j, j, XtX.get(j, j) + ridgeEps);
                }
                beta = XtX.solve(X.transpose().mult(y));
            }
        } catch (RuntimeException ex) {
            // fallback to tiny ridge if QR fails
            SimpleMatrix XtX = X.transpose().mult(X);
            int d = XtX.numRows();
            // add tiny ridge (do NOT penalize intercept)
            for (int j = 1; j < d; j++) {
                XtX.set(j, j, XtX.get(j, j) + ridgeEps);
            }
            beta = XtX.solve(X.transpose().mult(y));
        }
        this.B = beta;
    }

    // ---- helpers ----

    /**
     * Predicts the regression output for the given dataset, target node,
     * and parent nodes using the model parameters. If the model has not
     * been fitted yet, it is automatically trained on the provided inputs.
     *
     * @param data The dataset containing the feature values for prediction.
     * @param target The target node for which predictions are to be generated.
     * @param parents The list of parent nodes considered as predictors.
     * @return A double array containing the predicted values for each data row.
     */
    @Override
    public double[] predict(DataSet data, Node target, List<Node> parents) {
        if (B == null) fit(data, target, parents); // lazy fit
        int n = data.getNumRows();
        int p = B.numRows() - 1;

        // If schema differs, recompute column indices defensively
        if (parents != null && (parentCols == null || parentCols.length != parents.size())) {
            this.parentCols = parents.stream().mapToInt(data::getColumn).toArray();
        }

        double[] out = new double[n];
        for (int i = 0; i < n; i++) {
            double v = B.get(0, 0); // intercept
            for (int j = 0; j < p; j++) {
                int col = parentCols[j];
                v += B.get(j + 1, 0) * data.getDouble(i, col);
            }
            out[i] = v;
        }
        return out;
    }

    /**
     * Set L2 ridge strength (applied only when ill-conditioning is detected).
     *
     * @param v The ridege lambda
     * @return this
     */
    public LinearQRRegressor setRidgeLambda(double v) {
        this.ridgeEps = Math.max(0.0, v);
        return this;
    }
}
