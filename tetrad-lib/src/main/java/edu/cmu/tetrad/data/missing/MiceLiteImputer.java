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
 * missingness, a linear regression of that variable on all other variables is fit over the rows where it is
 * observed; each missing entry is then filled by copying the observed value of a donor row chosen at random from
 * the k rows whose fitted values are closest to the missing row's fitted value.
 * <p>
 * Discrete variables with three or more categories are never used as numbers, since their codes carry no order.
 * As a predictor, such a variable enters as one indicator per category (less a reference category). As a target,
 * one regression is fit per category indicator, giving each row a vector of fitted category scores, and donors are
 * the rows nearest in that vector. Both are unchanged by relabeling the categories. A two-category variable is its
 * own indicator and is used as is.
 * Because imputed values are always copied from observed donors, discrete imputations are automatically valid
 * category codes and continuous imputations respect the observed distribution (no Gaussianity assumption). The
 * chain is initialized by marginal hot-deck draws and swept a fixed number of times.
 * <p>
 * "Lite" caveats, flagged: the conditional models are linear and additive (no interactions); a discrete target is
 * matched on linear fits to its category indicators, not on a multinomial model; ordered categories are treated
 * like unordered ones, which is valid but ignores the order; and as with {@link MvnImputer} this is improper MI (no
 * parameter draws).
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

        // For a discrete variable with three or more categories, the number of categories; otherwise 0, meaning
        // the variable is used as a single number (continuous, or a two-category code).
        int[] numCategories = new int[p];

        for (int j = 0; j < p; j++) {
            discrete[j] = dataSet.getVariables().get(j) instanceof DiscreteVariable;

            if (dataSet.getVariables().get(j) instanceof DiscreteVariable variable
                && variable.getNumCategories() >= 3) {
                numCategories[j] = variable.getNumCategories();
            }
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
                    imputeColumnPmm(work, j, obsRows.get(j), missRows.get(j), p, rand, names, numCategories);
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
     * One PMM update of column j: fit a ridge regression of j (or of each of its category indicators) on the other
     * columns over the rows observed on j; fill each missing row from a random donor among the numDonors observed
     * rows with the closest fitted values. Falls back to a marginal hot-deck draw, and records that it did, if no
     * regression can be fit.
     * <p>
     * Predictors are centered and scaled to unit sum of squares, the penalty is added to the diagonal of their
     * cross-product matrix, and the system is solved by Cholesky decomposition.
     */
    private void imputeColumnPmm(double[][] work, int j, List<Integer> obs, List<Integer> missing, int p,
                                 Random rand, String[] names, int[] numCategories) {
        int nObs = obs.size();

        if (nObs < 2) {
            event(names[j], "filled by random draws from its own observed values, ignoring the other variables,"
                            + " because it has fewer than 2 observed rows");
            hotDeck(work, j, obs, missing, rand);
            return;
        }

        // Candidate predictor features: a column as a number, or one indicator per non-reference category.
        List<int[]> candidates = new ArrayList<>();

        for (int k = 0; k < p; k++) {
            if (k == j) continue;

            if (numCategories[k] == 0) {
                candidates.add(new int[]{k, -1});
            } else {
                for (int c = 1; c < numCategories[k]; c++) candidates.add(new int[]{k, c});
            }
        }

        // Usable features: those that vary over these rows.
        int[] col = new int[candidates.size()];
        int[] cat = new int[candidates.size()];
        double[] mean = new double[candidates.size()];
        double[] scale = new double[candidates.size()];
        boolean[] columnUsed = new boolean[p];
        int q = 0;

        for (int[] candidate : candidates) {
            double mu = 0.0;
            for (int row : obs) mu += feature(work, row, candidate[0], candidate[1]);
            mu /= nObs;
            double ss = 0.0;

            for (int row : obs) {
                double d = feature(work, row, candidate[0], candidate[1]) - mu;
                ss += d * d;
            }

            if (ss > 1e-12 * nObs * (1.0 + mu * mu)) {
                col[q] = candidate[0];
                cat[q] = candidate[1];
                mean[q] = mu;
                scale[q] = Math.sqrt(ss);
                columnUsed[candidate[0]] = true;
                q++;
            }
        }

        // A category absent from these rows just joins the reference category; only a wholly constant variable is
        // worth reporting.
        List<String> constant = new ArrayList<>();

        for (int k = 0; k < p; k++) {
            if (k != j && !columnUsed[k]) constant.add(names[k]);
        }

        if (!constant.isEmpty()) {
            event(names[j], "predictors constant over its observed rows were left out: "
                            + String.join(", ", constant));
        }

        if (q == 0) {
            event(names[j], "filled by random draws from its own observed values, ignoring the other variables,"
                            + " because no predictor varies over its observed rows");
            hotDeck(work, j, obs, missing, rand);
            return;
        }

        // Standardized predictors over the observed rows, and their cross products (unit diagonal) plus ridge.
        double[][] z = new double[nObs][q];
        double[][] g = new double[q][q];

        for (int a = 0; a < nObs; a++) {
            int row = obs.get(a);
            for (int f = 0; f < q; f++) z[a][f] = (feature(work, row, col[f], cat[f]) - mean[f]) / scale[f];

            for (int f = 0; f < q; f++) {
                for (int h = 0; h <= f; h++) g[f][h] += z[a][f] * z[a][h];
            }
        }

        for (int f = 0; f < q; f++) g[f][f] += this.ridge;

        // Cholesky decomposition in place (lower triangle). A pivot is the share of a predictor's variation not
        // explained by the predictors before it, plus the ridge; a very small one means near collinearity.
        double minPivot = Double.POSITIVE_INFINITY;

        for (int f = 0; f < q; f++) {
            for (int h = 0; h <= f; h++) {
                double sum = g[f][h];
                for (int c = 0; c < h; c++) sum -= g[f][c] * g[h][c];

                if (f == h) {
                    if (!(sum > 1e-12)) {
                        event(names[j], "filled by random draws from its own observed values, ignoring the other"
                                        + " variables, because the regression could not be solved");
                        hotDeck(work, j, obs, missing, rand);
                        return;
                    }

                    minPivot = Math.min(minPivot, sum);
                    g[f][f] = Math.sqrt(sum);
                } else {
                    g[f][h] = sum / g[h][h];
                }
            }
        }

        if (nObs <= q + 2) {
            event(names[j], "only " + nObs + " observed rows for " + q + " predictors; the fit rests on the ridge"
                            + " penalty and is weak, so consider imputing with fewer variables");
        } else if (minPivot < 1e-3) {
            event(names[j], "its predictors are nearly collinear; the fit was stabilized by the ridge penalty");
        }

        // One regression per target: the variable itself, or each of its category indicators.
        int numTargets = numCategories[j] == 0 ? 1 : numCategories[j];
        double[] meanY = new double[numTargets];
        double[][] b = new double[numTargets][q];

        for (int t = 0; t < numTargets; t++) {
            int category = numCategories[j] == 0 ? -1 : t;
            for (int row : obs) meanY[t] += feature(work, row, j, category);
            meanY[t] /= nObs;

            double[] x = b[t];

            for (int a = 0; a < nObs; a++) {
                double y = feature(work, obs.get(a), j, category) - meanY[t];
                for (int f = 0; f < q; f++) x[f] += z[a][f] * y;
            }

            // Solve L L' x = Z'y.
            for (int f = 0; f < q; f++) {
                double sum = x[f];
                for (int c = 0; c < f; c++) sum -= g[f][c] * x[c];
                x[f] = sum / g[f][f];
            }

            for (int f = q - 1; f >= 0; f--) {
                double sum = x[f];
                for (int c = f + 1; c < q; c++) sum -= g[c][f] * x[c];
                x[f] = sum / g[f][f];
            }
        }

        double[][] fittedObs = new double[nObs][numTargets];

        for (int a = 0; a < nObs; a++) {
            for (int t = 0; t < numTargets; t++) {
                double sum = meanY[t];
                for (int f = 0; f < q; f++) sum += b[t][f] * z[a][f];
                fittedObs[a][t] = sum;
            }
        }

        // The observed rows are scanned in a shuffled order, from a random start for each missing row, so that
        // among rows tied in fitted value (common when the predictors are discrete) the donors are a random
        // choice. Scanned in data order, ties would always go to the same first few rows.
        int[] order = new int[nObs];
        for (int a = 0; a < nObs; a++) order[a] = a;

        for (int a = nObs - 1; a > 0; a--) {
            int other = rand.nextInt(a + 1);
            int swap = order[a];
            order[a] = order[other];
            order[other] = swap;
        }

        double[] zRow = new double[q];
        double[] fit = new double[numTargets];

        for (int i : missing) {
            for (int f = 0; f < q; f++) zRow[f] = (feature(work, i, col[f], cat[f]) - mean[f]) / scale[f];

            for (int t = 0; t < numTargets; t++) {
                double sum = meanY[t];
                for (int f = 0; f < q; f++) sum += b[t][f] * zRow[f];
                fit[t] = sum;
            }

            // Find the numDonors observed rows with fitted values closest to this row's (linear scan; nObs is
            // modest).
            int k = Math.min(this.numDonors, nObs);
            int[] best = new int[k];
            double[] bestDist = new double[k];
            java.util.Arrays.fill(bestDist, Double.POSITIVE_INFINITY);

            int start = rand.nextInt(nObs);

            for (int step = 0; step < nObs; step++) {
                int a = order[(start + step) % nObs];
                double dist = 0.0;

                for (int t = 0; t < numTargets; t++) {
                    double d = fittedObs[a][t] - fit[t];
                    dist += d * d;
                }

                for (int h = 0; h < k; h++) {
                    if (dist < bestDist[h]) {
                        for (int c = k - 1; c > h; c--) {
                            bestDist[c] = bestDist[c - 1];
                            best[c] = best[c - 1];
                        }
                        bestDist[h] = dist;
                        best[h] = a;
                        break;
                    }
                }
            }

            work[i][j] = work[obs.get(best[rand.nextInt(k)])][j];
        }
    }

    /**
     * The value of a column in a row as a regression feature: the number itself if category is negative, otherwise
     * the indicator that the (discrete) value is that category.
     */
    private static double feature(double[][] work, int row, int column, int category) {
        if (category < 0) return work[row][column];
        return Math.round(work[row][column]) == category ? 1.0 : 0.0;
    }

    private static void hotDeck(double[][] work, int j, List<Integer> obs, List<Integer> missing, Random rand) {
        for (int i : missing) work[i][j] = work[obs.get(rand.nextInt(obs.size()))][j];
    }
}
