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
import edu.cmu.tetrad.util.TetradLogger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * <p>
 * The regressions carry a small ridge penalty (as in the R mice package), so that collinear predictors, or more
 * predictors than observed rows, do not stop a fit; predictors that are constant over the rows used are left out.
 * Only if a variable has fewer than two observed rows, has no usable predictor, or the system still cannot be
 * solved does it fall back to marginal hot-deck draws for that update. Such draws are independent of every other
 * variable and so bias toward independence; every fallback, and every fit that was only possible because of the
 * ridge, is recorded (see {@link #getLoggedEvents()}) and logged, so that none of this happens silently.
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
     * The ridge penalty, as a fraction of each (standardized) predictor's sum of squares.
     */
    private final double ridge;

    /**
     * What went less than cleanly in the last call to impute, with the number of column updates each applied to.
     */
    private final Map<String, Integer> events = new LinkedHashMap<>();

    /**
     * The number of updates each incomplete column got in the last call to impute (imputations times sweeps).
     */
    private int updatesPerColumn = 0;

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
        this(numDonors, numSweeps, 1e-5);
    }

    /**
     * Constructs an imputer.
     *
     * @param numDonors The number of donor candidates for PMM; at least 1.
     * @param numSweeps The number of chained sweeps; at least 1.
     * @param ridge     The ridge penalty for the regressions, as a fraction of each standardized predictor's sum of
     *                  squares; positive. The default is 1e-5, small enough to leave a well-posed fit unchanged.
     */
    public MiceLiteImputer(int numDonors, int numSweeps, double ridge) {
        if (numDonors < 1) throw new IllegalArgumentException("Number of donors must be >= 1: " + numDonors);
        if (numSweeps < 1) throw new IllegalArgumentException("Number of sweeps must be >= 1: " + numSweeps);
        if (!(ridge > 0)) throw new IllegalArgumentException("Ridge must be > 0: " + ridge);
        this.numDonors = numDonors;
        this.numSweeps = numSweeps;
        this.ridge = ridge;
    }

    /**
     * What went less than cleanly in the last call to impute: fallbacks to marginal hot-deck draws, predictors left
     * out as constant, and fits that were possible only because of the ridge. Each entry names the variable being
     * imputed and says in how many of its updates the event occurred. Empty if every regression was well posed.
     *
     * @return The events, one per line of report.
     */
    public List<String> getLoggedEvents() {
        List<String> report = new ArrayList<>();

        for (Map.Entry<String, Integer> event : this.events.entrySet()) {
            report.add(event.getKey() + " (in " + event.getValue() + " of " + this.updatesPerColumn + " updates)");
        }

        return report;
    }

    private void event(String name, String what) {
        this.events.merge(name + ": " + what, 1, Integer::sum);
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

        this.events.clear();
        this.updatesPerColumn = m * this.numSweeps;

        String[] names = new String[p];
        for (int j = 0; j < p; j++) names[j] = dataSet.getVariables().get(j).getName();

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
                    imputeColumnPmm(work, j, obsRows.get(j), missRows.get(j), p, rand, names);
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

        for (String line : getLoggedEvents()) {
            TetradLogger.getInstance().log("MICE imputation, " + line);
        }

        return imputed;
    }

    /**
     * One PMM update of column j: fit a ridge regression of j on the other columns over the rows observed on j;
     * fill each missing row from a random donor among the numDonors observed rows with the closest fitted values.
     * Falls back to a marginal hot-deck draw, and records that it did, if no regression can be fit.
     */
    private void imputeColumnPmm(double[][] work, int j, List<Integer> obs, List<Integer> missing, int p,
                                 Random rand, String[] names) {
        int nObs = obs.size();
        double[] fittedObs;
        double[] beta = nObs < 2 ? null : ridgeFit(work, j, obs, p, names);

        if (beta == null) {
            if (nObs < 2) {
                event(names[j], "filled by random draws from its own observed values, ignoring the other"
                                + " variables, because it has fewer than 2 observed rows");
            }

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

    /**
     * The ridge regression of column j on the other columns over the given rows, as [intercept, coefficients...]
     * with one coefficient per other column in column order (zero for a predictor left out). Predictors are
     * centered and scaled to unit sum of squares, the penalty is added to the diagonal of their cross-product
     * matrix, and the system is solved by Cholesky decomposition. Returns null, having recorded why, if there is no
     * usable predictor or the system cannot be solved.
     */
    private double[] ridgeFit(double[][] work, int j, List<Integer> obs, int p, String[] names) {
        int nObs = obs.size();
        double meanY = 0.0;
        for (int row : obs) meanY += work[row][j];
        meanY /= nObs;

        // Usable predictors: those that vary over these rows.
        int[] cols = new int[p - 1];
        double[] mean = new double[p - 1];
        double[] scale = new double[p - 1];
        int q = 0;
        List<String> constant = new ArrayList<>();

        for (int k = 0; k < p; k++) {
            if (k == j) continue;
            double mu = 0.0;
            for (int row : obs) mu += work[row][k];
            mu /= nObs;
            double ss = 0.0;
            for (int row : obs) ss += (work[row][k] - mu) * (work[row][k] - mu);

            if (ss > 1e-12 * nObs * (1.0 + mu * mu)) {
                cols[q] = k;
                mean[q] = mu;
                scale[q] = Math.sqrt(ss);
                q++;
            } else {
                constant.add(names[k]);
            }
        }

        if (!constant.isEmpty()) {
            event(names[j], "predictors constant over its observed rows were left out: "
                            + String.join(", ", constant));
        }

        if (q == 0) {
            event(names[j], "filled by random draws from its own observed values, ignoring the other variables,"
                            + " because no predictor varies over its observed rows");
            return null;
        }

        // Cross products of the standardized predictors (unit diagonal), and with the centered target.
        double[][] g = new double[q][q];
        double[] r = new double[q];
        double[] z = new double[q];

        for (int row : obs) {
            for (int a = 0; a < q; a++) z[a] = (work[row][cols[a]] - mean[a]) / scale[a];
            double y = work[row][j] - meanY;

            for (int a = 0; a < q; a++) {
                r[a] += z[a] * y;
                for (int b = 0; b <= a; b++) g[a][b] += z[a] * z[b];
            }
        }

        for (int a = 0; a < q; a++) g[a][a] += this.ridge;

        // Cholesky decomposition in place (lower triangle). A pivot is the share of a predictor's variation not
        // explained by the predictors before it, plus the ridge; a very small one means near collinearity.
        double minPivot = Double.POSITIVE_INFINITY;

        for (int a = 0; a < q; a++) {
            for (int b = 0; b <= a; b++) {
                double sum = g[a][b];
                for (int c = 0; c < b; c++) sum -= g[a][c] * g[b][c];

                if (a == b) {
                    if (!(sum > 1e-12)) {
                        event(names[j], "filled by random draws from its own observed values, ignoring the other"
                                        + " variables, because the regression could not be solved");
                        return null;
                    }

                    minPivot = Math.min(minPivot, sum);
                    g[a][a] = Math.sqrt(sum);
                } else {
                    g[a][b] = sum / g[b][b];
                }
            }
        }

        if (nObs <= q + 2) {
            event(names[j], "only " + nObs + " observed rows for " + q + " predictors; the fit rests on the ridge"
                            + " penalty and is weak, so consider imputing with fewer variables");
        } else if (minPivot < 1e-3) {
            event(names[j], "its predictors are nearly collinear; the fit was stabilized by the ridge penalty");
        }

        // Solve L L' b = r.
        double[] b = new double[q];

        for (int a = 0; a < q; a++) {
            double sum = r[a];
            for (int c = 0; c < a; c++) sum -= g[a][c] * b[c];
            b[a] = sum / g[a][a];
        }

        for (int a = q - 1; a >= 0; a--) {
            double sum = b[a];
            for (int c = a + 1; c < q; c++) sum -= g[c][a] * b[c];
            b[a] = sum / g[a][a];
        }

        // Back to the original scale, in the layout fitted() expects.
        double[] beta = new double[p];
        beta[0] = meanY;

        for (int a = 0; a < q; a++) {
            double coef = b[a] / scale[a];
            int position = cols[a] < j ? cols[a] + 1 : cols[a];
            beta[position] = coef;
            beta[0] -= coef * mean[a];
        }

        return beta;
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
