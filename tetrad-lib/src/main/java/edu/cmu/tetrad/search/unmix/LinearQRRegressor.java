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
    private final double condWarn;   // condition number threshold to trigger ridge
    // knobs
    private double ridgeEps;   // tiny ridge added on ill-conditioning
    private SimpleMatrix B;     // (p+1) x 1 coefficients, intercept first
    private int[] parentCols;   // column indices of parents in the *fitted* dataset schema
    private int yCol;

    public LinearQRRegressor() {
        this(1e-8, 1e10); // safe defaults
    }

    public LinearQRRegressor(double ridgeEps, double condWarn) {
        this.ridgeEps = ridgeEps;
        this.condWarn = condWarn;
    }

    /**
     * Rough condition number for symmetric positive (semi)definite matrix via eigenvalue ratio.
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
     */
    public LinearQRRegressor setRidgeLambda(double v) {
        this.ridgeEps = Math.max(0.0, v);
        return this;
    }
}