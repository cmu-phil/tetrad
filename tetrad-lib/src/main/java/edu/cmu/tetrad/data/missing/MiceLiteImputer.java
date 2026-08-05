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
import edu.cmu.tetrad.data.DiscreteVariable;
import org.apache.commons.math3.stat.regression.OLSMultipleLinearRegression;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A "lite" chained-equations (MICE-style) multiple imputer for continuous, discrete, or mixed data, using
 * predictive mean matching (PMM) as the single imputation engine for both variable types. For each variable with
 * missingness, an OLS regression of that variable (discrete variables numerically coded) on all other variables is
 * fit over the rows where it is observed; each missing entry is then filled by copying the observed value of a
 * donor row chosen at random from the k rows whose fitted values are closest to the missing row's fitted value.
 * Because imputed values are always copied from observed donors, discrete imputations are automatically valid
 * category codes and continuous imputations respect the observed distribution (no Gaussianity assumption). The
 * chain is initialized by marginal hot-deck draws and swept a fixed number of times.
 * <p>
 * "Lite" caveats, flagged: the conditional models are linear in the numeric codings (no interactions, no proper
 * multinomial model for discrete targets), and as with {@link MvnImputer} this is improper MI (no parameter draws).
 * If a regression cannot be fit (singularity, too few rows), the affected variable falls back to marginal hot-deck
 * draws for that sweep.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public final class MiceLiteImputer implements MultipleImputer {

    /**
     * The number of donor candidates for predictive mean matching.
     */
    private final int numDonors;

    /**
     * The number of chained sweeps per imputation.
     */
    private final int numSweeps;

    /**
     * Constructs an imputer with the defaults: 5 donors, 5 sweeps.
     */
    public MiceLiteImputer() {
        this(5, 5);
    }

    /**
     * Constructs an imputer.
     *
     * @param numDonors The number of donor candidates for PMM; at least 1.
     * @param numSweeps The number of chained sweeps; at least 1.
     */
    public MiceLiteImputer(int numDonors, int numSweeps) {
        if (numDonors < 1) throw new IllegalArgumentException("Number of donors must be >= 1: " + numDonors);
        if (numSweeps < 1) throw new IllegalArgumentException("Number of sweeps must be >= 1: " + numSweeps);
        this.numDonors = numDonors;
        this.numSweeps = numSweeps;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<DataSet> impute(DataSet dataSet, int m, long seed) {
        if (!dataSet.existsMissingValue()) {
            throw new IllegalArgumentException("The dataset has no missing values; nothing to impute.");
        }

        if (m < 2) throw new IllegalArgumentException("Number of imputations must be >= 2: " + m);

        int n = dataSet.getNumRows();
        int p = dataSet.getNumColumns();
        boolean[] discrete = new boolean[p];

        for (int j = 0; j < p; j++) {
            discrete[j] = dataSet.getVariables().get(j) instanceof DiscreteVariable;
        }

        // Numeric working copy and missingness mask.
        double[][] base = new double[n][p];
        boolean[][] miss = new boolean[n][p];
        List<List<Integer>> obsRows = new ArrayList<>();
        List<List<Integer>> missRows = new ArrayList<>();

        for (int j = 0; j < p; j++) {
            obsRows.add(new ArrayList<>());
            missRows.add(new ArrayList<>());
        }

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < p; j++) {
                miss[i][j] = MissingDataAudit.isMissing(dataSet, i, j);

                if (miss[i][j]) {
                    missRows.get(j).add(i);
                } else {
                    base[i][j] = discrete[j] ? dataSet.getInt(i, j) : dataSet.getDouble(i, j);
                    obsRows.get(j).add(i);
                }
            }
        }

        for (int j = 0; j < p; j++) {
            if (!missRows.get(j).isEmpty() && obsRows.get(j).isEmpty()) {
                throw new IllegalArgumentException("Variable " + dataSet.getVariables().get(j).getName()
                        + " has no observed values; it cannot be imputed.");
            }
        }

        Random rand = seed < 0 ? new Random() : new Random(seed);
        List<DataSet> imputed = new ArrayList<>(m);

        for (int im = 0; im < m; im++) {
            double[][] work = new double[n][];
            for (int i = 0; i < n; i++) work[i] = base[i].clone();

            // Initialize by marginal hot deck.
            for (int j = 0; j < p; j++) {
                List<Integer> obs = obsRows.get(j);
                for (int i : missRows.get(j)) work[i][j] = work[obs.get(rand.nextInt(obs.size()))][j];
            }

            for (int sweep = 0; sweep < this.numSweeps; sweep++) {
                for (int j = 0; j < p; j++) {
                    if (missRows.get(j).isEmpty()) continue;
                    imputeColumnPmm(work, j, obsRows.get(j), missRows.get(j), p, rand);
                }
            }

            DataSet copy = dataSet.copy();

            for (int j = 0; j < p; j++) {
                for (int i : missRows.get(j)) {
                    if (discrete[j]) copy.setInt(i, j, (int) Math.round(work[i][j]));
                    else copy.setDouble(i, j, work[i][j]);
                }
            }

            imputed.add(copy);
        }

        return imputed;
    }

    /**
     * One PMM update of column j: fit OLS of j on the other columns over the rows observed on j; fill each missing
     * row from a random donor among the numDonors observed rows with the closest fitted values. Falls back to a
     * marginal hot-deck draw if the regression cannot be fit.
     */
    private void imputeColumnPmm(double[][] work, int j, List<Integer> obs, List<Integer> missing, int p,
                                 Random rand) {
        int nObs = obs.size();
        double[] fittedObs;
        double[] beta = null;

        if (nObs > p + 2) {
            try {
                double[] y = new double[nObs];
                double[][] x = new double[nObs][p - 1];

                for (int a = 0; a < nObs; a++) {
                    int row = obs.get(a);
                    y[a] = work[row][j];
                    int c = 0;
                    for (int k = 0; k < p; k++) {
                        if (k != j) x[a][c++] = work[row][k];
                    }
                }

                OLSMultipleLinearRegression ols = new OLSMultipleLinearRegression();
                ols.newSampleData(y, x);
                beta = ols.estimateRegressionParameters(); // [intercept, coefs...]
            } catch (Exception e) {
                beta = null;
            }
        }

        if (beta == null) {

            // Fallback: marginal hot deck.
            for (int i : missing) work[i][j] = work[obs.get(rand.nextInt(nObs))][j];
            return;
        }

        fittedObs = new double[nObs];
        for (int a = 0; a < nObs; a++) fittedObs[a] = fitted(work, obs.get(a), j, p, beta);

        for (int i : missing) {
            double f = fitted(work, i, j, p, beta);

            // Find the numDonors observed rows with fitted values closest to f (linear scan; nObs is modest).
            int k = Math.min(this.numDonors, nObs);
            int[] best = new int[k];
            double[] bestDist = new double[k];
            java.util.Arrays.fill(bestDist, Double.POSITIVE_INFINITY);

            for (int a = 0; a < nObs; a++) {
                double dist = Math.abs(fittedObs[a] - f);

                for (int b = 0; b < k; b++) {
                    if (dist < bestDist[b]) {
                        for (int c = k - 1; c > b; c--) {
                            bestDist[c] = bestDist[c - 1];
                            best[c] = best[c - 1];
                        }
                        bestDist[b] = dist;
                        best[b] = a;
                        break;
                    }
                }
            }

            work[i][j] = work[obs.get(best[rand.nextInt(k)])][j];
        }
    }

    private static double fitted(double[][] work, int row, int j, int p, double[] beta) {
        double f = beta[0];
        int c = 1;
        for (int k = 0; k < p; k++) {
            if (k != j) f += beta[c++] * work[row][k];
        }
        return f;
    }
}
