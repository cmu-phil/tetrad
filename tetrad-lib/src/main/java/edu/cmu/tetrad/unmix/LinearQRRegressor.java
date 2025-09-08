package edu.cmu.tetrad.unmix;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import org.ejml.simple.SimpleMatrix;

import java.util.List;

/**
 * Linear regression regressor using a QR-based normal equations solve.
 * Model: y = beta0 + sum_j beta_j * x_j. Provides fitted values and residuals via ResidualRegressor.
 */
public class LinearQRRegressor implements ResidualRegressor {
    private SimpleMatrix B;   // coefficients (pParents+1 x 1), includes intercept
    private int[] parentCols;
    private int yCol;

    /** {@inheritDoc}
     * Fits OLS coefficients including an intercept using parent columns from the dataset.
     */
    @Override
    public void fit(DataSet data, Node target, List<Node> parents) {
        this.yCol = data.getColumnIndex(target);
        this.parentCols = parents.stream().mapToInt(data::getColumnIndex).toArray();

        int n = data.getNumRows(), p = parentCols.length;
        SimpleMatrix X = new SimpleMatrix(n, p + 1);
        for (int i = 0; i < n; i++) {
            X.set(i, 0, 1.0); // intercept
            for (int j = 0; j < p; j++) {
                X.set(i, j + 1, data.getDouble(i, parentCols[j]));
            }
        }
        SimpleMatrix y = new SimpleMatrix(n, 1);
        for (int i = 0; i < n; i++) y.set(i, 0, data.getDouble(i, yCol));
        // B = (X^T X)^-1 X^T y via QR
        SimpleMatrix XtX = X.transpose().mult(X);
        SimpleMatrix XtY = X.transpose().mult(y);
        this.B = XtX.solve(XtY);
    }

    /** {@inheritDoc}
     * Produces fitted values for each row; fits lazily if not already fitted.
     */
    @Override
    public double[] predict(DataSet data, Node target, List<Node> parents) {
        int n = data.getNumRows(), p = parentCols.length;
        if (B == null) fit(data, target, parents);
        double[] out = new double[n];
        for (int i = 0; i < n; i++) {
            double v = B.get(0,0); // intercept
            for (int j = 0; j < p; j++) v += B.get(j+1,0) * data.getDouble(i, parentCols[j]);
            out[i] = v;
        }
        return out;
    }
}