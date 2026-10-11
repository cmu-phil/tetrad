package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.StatUtils;

import java.util.List;

/**
 * Computes Satorra-Bentler robust ("sandwich") standard errors and the Satorra-Bentler
 * scaled chi-square for an estimated SEM, correcting normal-theory inference for
 * non-normal (e.g., heavy-tailed) data.
 * <p>
 * Background. The normal-theory ML point estimates of a linear SEM depend on the data
 * only through the sample covariance matrix and remain consistent under non-normal
 * errors. The normal-theory standard errors of coefficient parameters are also
 * asymptotically correct when errors are independent (asymptotic robustness,
 * Anderson and Amemiya 1988; Satorra and Bentler 1990), but the standard errors of
 * variance parameters are not: they scale with the kurtosis of the errors. For
 * example, with Exp(1) errors the normal-theory standard error of an error-variance
 * parameter understates the true sampling variability by a factor of 2. The model
 * chi-square is likewise mildly miscalibrated at moderate sample sizes. This class
 * computes the standard corrections:
 * <ul>
 * <li>Robust standard errors from the sandwich covariance
 * (Delta' V Delta)^{-1} Delta' V Gamma V Delta (Delta' V Delta)^{-1} / (n - 1),
 * where Delta is the Jacobian of the implied covariance (half-vectorized) with
 * respect to the free parameters, V is the normal-theory weight matrix
 * 0.5 D' (Sigma^{-1} kron Sigma^{-1}) D evaluated at the estimates, and Gamma is the
 * asymptotic covariance of the sample covariances estimated from the data's fourth
 * moments (the ADF estimate).</li>
 * <li>The Satorra-Bentler scaled chi-square T_sb = T_ml / c with scaling factor
 * c = trace(U Gamma) / df, U = V - V Delta (Delta' V Delta)^{-1} Delta' V. Under
 * normality c is 1 in the limit, so the scaled statistic reduces to the usual one.</li>
 * </ul>
 * Because Gamma requires fourth moments, this class needs the raw data set; a
 * covariance matrix alone is not enough. The estimated SEM is not modified: free
 * parameters are perturbed to compute the numerical Jacobian and then restored,
 * mirroring {@link SemStdErrorEstimator}.
 * <p>
 * Usage:
 * <pre>
 * SemEstimator estimator = new SemEstimator(dataSet, pm);
 * SemIm est = estimator.estimate();
 * SemSandwichEstimator robust = new SemSandwichEstimator(est, dataSet);
 * double[] ses = robust.getRobustStdErrs();   // aligned with est.getFreeParameters()
 * double tsb = robust.getScaledChiSquare();
 * double p = robust.getScaledPValue();
 * </pre>
 * <p>
 * References: Satorra, A., and Bentler, P. M. (1994), "Corrections to test statistics
 * and standard errors in covariance structure analysis"; Browne, M. W. (1984),
 * "Asymptotically distribution-free methods for the analysis of covariance
 * structures"; Anderson, T. W., and Amemiya, Y. (1988), "The asymptotic normal
 * distribution of estimators in factor analysis under general conditions."
 *
 * @author josephramsey and claude
 */
public class SemSandwichEstimator {

    /**
     * Relative step size for the central-difference Jacobian of the implied covariance.
     */
    private static final double JACOBIAN_STEP = 1e-4;

    /**
     * Robust standard errors, in the order of estSem.getFreeParameters().
     */
    private final double[] robustStdErrs;

    /**
     * The Satorra-Bentler scaling factor c = trace(U Gamma) / df.
     */
    private final double scalingFactor;

    /**
     * The Satorra-Bentler scaled chi-square, T_ml / c.
     */
    private final double scaledChiSquare;

    /**
     * Model degrees of freedom, p(p + 1)/2 minus the number of free parameters.
     */
    private final int dof;

    /**
     * Sample size.
     */
    private final int sampleSize;

    /**
     * Computes robust standard errors and the scaled chi-square for the given estimated
     * SEM, using the raw data for fourth moments.
     *
     * @param estSem  the estimated SEM, e.g., from {@link SemEstimator#estimate()}. Its
     *                free parameter values are perturbed during the computation and
     *                restored afterward.
     * @param dataSet the continuous data set the SEM was estimated from. Must contain a
     *                column for every measured variable of the SEM and no missing
     *                values in those columns.
     * @throws IllegalArgumentException if a measured variable is missing from the data,
     *                                  the data contain missing values, or the sample
     *                                  size is too small.
     */
    public SemSandwichEstimator(ISemIm estSem, DataSet dataSet) {
        if (estSem == null) throw new NullPointerException("estSem");
        if (dataSet == null) throw new NullPointerException("dataSet");

        List<Node> measured = estSem.getMeasuredNodes();
        int p = measured.size();
        int ps = p * (p + 1) / 2;
        int n = dataSet.getNumRows();
        this.sampleSize = n;

        if (n < p + 2) {
            throw new IllegalArgumentException("Sample size " + n + " is too small for "
                                               + p + " measured variables.");
        }

        // Columns of the data in measured-node order.
        int[] cols = new int[p];
        List<String> names = dataSet.getVariableNames();
        for (int j = 0; j < p; j++) {
            int col = names.indexOf(measured.get(j).getName());
            if (col < 0) {
                throw new IllegalArgumentException("Measured variable "
                                                   + measured.get(j).getName() + " not in the data set.");
            }
            cols[j] = col;
        }

        Matrix all = dataSet.getDoubleData();
        double[][] z = new double[n][p];
        double[] means = new double[p];

        for (int j = 0; j < p; j++) {
            double sum = 0.0;
            for (int r = 0; r < n; r++) {
                double v = all.get(r, cols[j]);
                if (Double.isNaN(v)) {
                    throw new IllegalArgumentException("Missing value in variable "
                                                       + measured.get(j).getName()
                                                       + "; robust standard errors require complete data.");
                }
                z[r][j] = v;
                sum += v;
            }
            means[j] = sum / n;
            for (int r = 0; r < n; r++) z[r][j] -= means[j];
        }

        // Gamma: the ADF estimate of the asymptotic covariance of vech(S) --
        // the sample covariance (divisor n) of b_r = vech(z_r z_r').
        double[] bbar = new double[ps];
        double[][] b = new double[n][ps];

        for (int r = 0; r < n; r++) {
            int m = 0;
            for (int i = 0; i < p; i++) {
                for (int j = 0; j <= i; j++) {
                    double v = z[r][i] * z[r][j];
                    b[r][m] = v;
                    bbar[m] += v;
                    m++;
                }
            }
        }

        for (int m = 0; m < ps; m++) bbar[m] /= n;

        Matrix gamma = new Matrix(ps, ps);

        for (int r = 0; r < n; r++) {
            for (int m1 = 0; m1 < ps; m1++) {
                double d1 = b[r][m1] - bbar[m1];
                for (int m2 = m1; m2 < ps; m2++) {
                    gamma.set(m1, m2, gamma.get(m1, m2) + d1 * (b[r][m2] - bbar[m2]));
                }
            }
        }

        for (int m1 = 0; m1 < ps; m1++) {
            for (int m2 = m1; m2 < ps; m2++) {
                double v = gamma.get(m1, m2) / n;
                gamma.set(m1, m2, v);
                gamma.set(m2, m1, v);
            }
        }

        // V: the normal-theory weight matrix 0.5 D'(Sigma^-1 kron Sigma^-1)D at the
        // estimates, computed elementwise over vech index pairs.
        Matrix sigmaInv = estSem.getImplCovarMeas().inverse();
        Matrix v = new Matrix(ps, ps);

        int[] rowOf = new int[ps];
        int[] colOf = new int[ps];
        {
            int m = 0;
            for (int i = 0; i < p; i++) {
                for (int j = 0; j <= i; j++) {
                    rowOf[m] = i;
                    colOf[m] = j;
                    m++;
                }
            }
        }

        for (int m1 = 0; m1 < ps; m1++) {
            int i = rowOf[m1], j = colOf[m1];
            for (int m2 = m1; m2 < ps; m2++) {
                int k = rowOf[m2], l = colOf[m2];

                // Sum of A_ac A_bd over (a,b) in {(i,j),(j,i)} and (c,d) in {(k,l),(l,k)},
                // without double-counting diagonal pairs; then halved.
                double sum = term(sigmaInv, i, j, k, l);
                if (i != j) sum += term(sigmaInv, j, i, k, l);
                if (k != l) {
                    sum += term(sigmaInv, i, j, l, k);
                    if (i != j) sum += term(sigmaInv, j, i, l, k);
                }

                double val = 0.5 * sum;
                v.set(m1, m2, val);
                v.set(m2, m1, val);
            }
        }

        // Delta: numerical Jacobian of vech(Sigma(theta)) with respect to the free
        // parameters, by central differences. Parameters are restored afterward.
        List<Parameter> free = estSem.getFreeParameters();
        int q = free.size();
        this.dof = ps - q;

        estSem.setParameterBoundsEnforced(false);
        double[] theta0 = estSem.getFreeParamValues();
        Matrix delta = new Matrix(ps, q);

        try {
            for (int t = 0; t < q; t++) {
                double h = SemSandwichEstimator.JACOBIAN_STEP * Math.max(1.0, Math.abs(theta0[t]));

                double[] theta = new double[q];

                System.arraycopy(theta0, 0, theta, 0, q);
                theta[t] = theta0[t] + h;
                estSem.setFreeParamValues(theta);
                double[] up = vech(estSem.getImplCovarMeas(), ps, rowOf, colOf);

                theta[t] = theta0[t] - h;
                estSem.setFreeParamValues(theta);
                double[] down = vech(estSem.getImplCovarMeas(), ps, rowOf, colOf);

                for (int m = 0; m < ps; m++) {
                    delta.set(m, t, (up[m] - down[m]) / (2.0 * h));
                }
            }
        } finally {
            estSem.setFreeParamValues(theta0);
            estSem.setParameterBoundsEnforced(true);
        }

        // Sandwich covariance of the estimates.
        Matrix vd = v.times(delta);                         // ps x q
        Matrix bread = delta.transpose().times(vd).pseudoinverse();  // q x q
        Matrix meat = vd.transpose().times(gamma).times(vd);         // q x q
        Matrix acov = bread.times(meat).times(bread);

        this.robustStdErrs = new double[q];

        for (int t = 0; t < q; t++) {
            double var = acov.get(t, t) / (n - 1);
            this.robustStdErrs[t] = var >= 0 ? Math.sqrt(var) : Double.NaN;
        }

        // Satorra-Bentler scaling: c = trace(U Gamma) / df, U = V - VD bread D'V.
        if (this.dof > 0) {
            Matrix u = v.minus(vd.times(bread).times(vd.transpose()));
            double c = u.times(gamma).trace() / this.dof;
            this.scalingFactor = c;
            this.scaledChiSquare = c > 0 ? estSem.getChiSquare() / c : Double.NaN;
        } else {
            this.scalingFactor = Double.NaN;
            this.scaledChiSquare = Double.NaN;
        }
    }

    private static double term(Matrix a, int i, int j, int k, int l) {
        return a.get(i, k) * a.get(j, l);
    }

    private static double[] vech(Matrix m, int ps, int[] rowOf, int[] colOf) {
        double[] out = new double[ps];
        for (int t = 0; t < ps; t++) out[t] = m.get(rowOf[t], colOf[t]);
        return out;
    }

    /**
     * Returns the robust (sandwich) standard errors of the free parameters, in the same
     * order as estSem.getFreeParameters(), matching the convention of
     * {@link SemStdErrorEstimator#getStdErrors()}.
     *
     * @return the robust standard errors.
     */
    public double[] getRobustStdErrs() {
        double[] copy = new double[this.robustStdErrs.length];
        System.arraycopy(this.robustStdErrs, 0, copy, 0, copy.length);
        return copy;
    }

    /**
     * Returns the Satorra-Bentler scaling factor c. Values near 1 indicate the
     * normal-theory chi-square is already well calibrated; values above 1 indicate the
     * normal-theory statistic is inflated by that factor.
     *
     * @return the scaling factor, or NaN if the model has no degrees of freedom.
     */
    public double getScalingFactor() {
        return this.scalingFactor;
    }

    /**
     * Returns the Satorra-Bentler scaled chi-square, the normal-theory chi-square
     * divided by the scaling factor.
     *
     * @return the scaled chi-square, or NaN if the model has no degrees of freedom.
     */
    public double getScaledChiSquare() {
        return this.scaledChiSquare;
    }

    /**
     * Returns the p-value of the scaled chi-square against the model degrees of freedom.
     *
     * @return the p-value, or NaN if the model has no degrees of freedom.
     */
    public double getScaledPValue() {
        if (this.dof <= 0 || Double.isNaN(this.scaledChiSquare)) return Double.NaN;
        return StatUtils.getChiSquareP(this.dof, this.scaledChiSquare);
    }

    /**
     * Returns the model degrees of freedom used for the scaled test.
     *
     * @return p(p + 1)/2 minus the number of free parameters.
     */
    public int getDof() {
        return this.dof;
    }

    /**
     * Returns the sample size of the data set used.
     *
     * @return the sample size.
     */
    public int getSampleSize() {
        return this.sampleSize;
    }
}
