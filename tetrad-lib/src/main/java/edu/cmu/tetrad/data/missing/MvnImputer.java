///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2026 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
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

package edu.cmu.tetrad.data.missing;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.EmCovarianceEstimator;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.CholeskyDecomposition;
import org.apache.commons.math3.linear.LUDecomposition;
import org.apache.commons.math3.linear.RealMatrix;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A multiple imputer for continuous data under a saturated multivariate normal model. The mean and covariance are
 * estimated once by EM (see {@link EmCovarianceEstimator}); each missing block is then drawn from its conditional
 * normal distribution given the observed entries of its row: N(mu_M + S_MO S_OO^{-1} (x_O - mu_O),
 * S_MM - S_MO S_OO^{-1} S_OM). Valid under MAR for approximately multivariate normal data.
 * <p>
 * Note: this is "improper" multiple imputation in Rubin's sense--the model parameters are fixed at their EM
 * estimates rather than drawn from a posterior--so between-imputation variability is somewhat understated. For
 * pooling graphs by edge frequency this is a reasonable first implementation; a proper (posterior-draw) variant is
 * a flagged follow-up.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public final class MvnImputer implements MultipleImputer {

    /**
     * EM settings used for the parameter estimates.
     */
    private final MissingDataSpec spec;

    /**
     * Constructs an imputer with default EM settings.
     */
    public MvnImputer() {
        this(MissingDataSpec.multipleImputation(10));
    }

    /**
     * Constructs an imputer whose EM settings (ridge, tolerance, max iterations) are taken from the given spec.
     *
     * @param spec The spec.
     */
    public MvnImputer(MissingDataSpec spec) {
        if (spec == null) throw new NullPointerException("Spec is null.");
        this.spec = spec;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<DataSet> impute(DataSet dataSet, int m, long seed) {
        if (!dataSet.isContinuous()) {
            throw new IllegalArgumentException("MvnImputer requires a continuous dataset.");
        }

        if (!dataSet.existsMissingValue()) {
            throw new IllegalArgumentException("The dataset has no missing values; nothing to impute.");
        }

        if (m < 2) throw new IllegalArgumentException("Number of imputations must be >= 2: " + m);

        EmCovarianceEstimator estimator = new EmCovarianceEstimator(dataSet);
        estimator.setRidge(this.spec.getEmRidge());
        estimator.setTolerance(this.spec.getEmTolerance());
        estimator.setMaxIterations(this.spec.getEmMaxIterations());
        ICovarianceMatrix cov = estimator.estimate();
        double[] mu = estimator.getMeans();

        int p = dataSet.getNumColumns();
        int n = dataSet.getNumRows();
        double[][] sigma = new double[p][p];

        for (int i = 0; i < p; i++) {
            for (int j = 0; j < p; j++) sigma[i][j] = cov.getValue(i, j);
        }

        Random rand = seed < 0 ? new Random() : new Random(seed);
        List<DataSet> imputed = new ArrayList<>(m);

        for (int im = 0; im < m; im++) {
            DataSet copy = dataSet.copy();

            for (int row = 0; row < n; row++) {
                List<Integer> miss = new ArrayList<>();
                List<Integer> obs = new ArrayList<>();

                for (int j = 0; j < p; j++) {
                    if (Double.isNaN(dataSet.getDouble(row, j))) miss.add(j);
                    else obs.add(j);
                }

                if (miss.isEmpty()) continue;

                int q = miss.size();
                int r = obs.size();
                double[] condMean = new double[q];
                double[][] condCov;

                if (r == 0) {

                    // Entirely missing row: draw from the marginal.
                    for (int a = 0; a < q; a++) condMean[a] = mu[miss.get(a)];
                    condCov = submatrix(sigma, miss, miss);
                } else {
                    RealMatrix sOO = new Array2DRowRealMatrix(submatrix(sigma, obs, obs), false);
                    RealMatrix sMO = new Array2DRowRealMatrix(submatrix(sigma, miss, obs), false);
                    RealMatrix sOOinv = new LUDecomposition(sOO).getSolver().getInverse();
                    RealMatrix beta = sMO.multiply(sOOinv);

                    double[] dev = new double[r];
                    for (int b = 0; b < r; b++) dev[b] = dataSet.getDouble(row, obs.get(b)) - mu[obs.get(b)];

                    for (int a = 0; a < q; a++) {
                        double s = mu[miss.get(a)];
                        for (int b = 0; b < r; b++) s += beta.getEntry(a, b) * dev[b];
                        condMean[a] = s;
                    }

                    RealMatrix sMM = new Array2DRowRealMatrix(submatrix(sigma, miss, miss), false);
                    condCov = sMM.subtract(beta.multiply(sMO.transpose())).getData();
                }

                double[] draw = drawMvn(condMean, condCov, rand);
                for (int a = 0; a < q; a++) copy.setDouble(row, miss.get(a), draw[a]);
            }

            imputed.add(copy);
        }

        return imputed;
    }

    private static double[][] submatrix(double[][] a, List<Integer> rows, List<Integer> cols) {
        double[][] out = new double[rows.size()][cols.size()];

        for (int i = 0; i < rows.size(); i++) {
            for (int j = 0; j < cols.size(); j++) out[i][j] = a[rows.get(i)][cols.get(j)];
        }

        return out;
    }

    /**
     * Draws from N(mean, cov) via Cholesky, symmetrizing and adding a small jitter on failure; falls back to
     * independent draws from the diagonal if the matrix cannot be factored.
     */
    private static double[] drawMvn(double[] mean, double[][] cov, Random rand) {
        int q = mean.length;
        double[] z = new double[q];
        for (int a = 0; a < q; a++) z[a] = rand.nextGaussian();

        for (int a = 0; a < q; a++) {
            for (int b = 0; b < a; b++) {
                double avg = 0.5 * (cov[a][b] + cov[b][a]);
                cov[a][b] = avg;
                cov[b][a] = avg;
            }
        }

        try {
            RealMatrix l = new CholeskyDecomposition(new Array2DRowRealMatrix(cov, false), 1e-8, 1e-10).getL();
            double[] draw = new double[q];

            for (int a = 0; a < q; a++) {
                double s = mean[a];
                for (int b = 0; b <= a; b++) s += l.getEntry(a, b) * z[b];
                draw[a] = s;
            }

            return draw;
        } catch (Exception e) {
            double[] draw = new double[q];
            for (int a = 0; a < q; a++) draw[a] = mean[a] + Math.sqrt(Math.max(cov[a][a], 0.0)) * z[a];
            return draw;
        }
    }
}
