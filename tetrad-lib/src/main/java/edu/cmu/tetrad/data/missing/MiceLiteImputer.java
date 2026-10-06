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
 * "Lite" caveats, flagged: the conditional models are additive (no interactions), and linear unless powers of the
 * continuous predictors are asked for (see {@link #setPredictorDegree(int)}); donors for a continuous target are
 * matched on its fitted mean alone unless more is asked for (see {@link #setTargetDegree(int)}); a discrete target is
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
 * <p>
 * Every other variable predicts each incomplete variable unless that would leave too few observed rows per predictor
 * term, in which case only the variables most correlated with it, or with whether it is missing, are used (see
 * {@link #setRowsPerPredictor(int)}); a minimum correlation can also be required (see
 * {@link #setMinCorrelation(double)}). Any such selection is recorded with the other events.
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
     * A variable is used as a predictor of a target only if its score for that target is at least this; 0 means no
     * such requirement. See {@link #setMinCorrelation(double)}.
     */
    private double minCorrelation = 0.0;

    /**
     * The fewest observed rows of a target allowed per predictor term; if using every eligible variable would
     * give fewer, only the highest-scoring ones are used. 0 means no limit. See {@link #setRowsPerPredictor(int)}.
     */
    private int rowsPerPredictor = 3;

    /**
     * The highest power of a continuous predictor used in the regressions; 1 means linear. See
     * {@link #setPredictorDegree(int)}.
     */
    private int predictorDegree = 1;

    /**
     * The number of powers of a continuous variable whose fitted values donors are matched on when it is imputed;
     * 1 means the variable itself only. See {@link #setTargetDegree(int)}.
     */
    private int targetDegree = 1;

    /**
     * For each column, the number of terms it contributes as a number: the predictor degree if it is continuous,
     * otherwise 1. Set at the start of each call to impute.
     */
    private int[] powers;

    /**
     * For each column, the number of fitted values donors are matched on when it is imputed as a number: the
     * target degree if it is continuous, otherwise 1. Set at the start of each call to impute.
     */
    private int[] moments;

    /**
     * For each continuous column, the mean and standard deviation of its observed values, by which it is
     * standardized before powers are taken. Set at the start of each call to impute.
     */
    private double[] center;
    private double[] spread;

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
     * Sets the minimum score a variable needs to be used as a predictor of a target (as quickpred does in the R
     * mice package). The score is the larger of two absolute correlations, computed once from the observed data:
     * the variable with the target, over rows where both are observed, and the variable with the indicator of
     * whether the target is missing. For a discrete variable with three or more categories, the largest over its
     * category indicators is used.
     * <p>
     * The default, 0, sets no minimum, and every other variable is a predictor unless the row limit applies. A
     * minimum above 0 makes each imputed value independent of the variables left out, given those kept, which
     * weakens weak dependencies in the completed data; for causal search, prefer leaving this at 0 and relying on
     * the row limit, which only acts when a regression would otherwise be poorly determined.
     *
     * @param minCorrelation the minimum, in [0, 1)
     */
    public void setMinCorrelation(double minCorrelation) {
        if (!(minCorrelation >= 0 && minCorrelation < 1)) {
            throw new IllegalArgumentException("Minimum correlation must be in [0, 1): " + minCorrelation);
        }

        this.minCorrelation = minCorrelation;
    }

    /**
     * Sets the fewest observed rows of a target allowed per predictor term (a continuous or two-category variable
     * is one term; a variable with K categories is K - 1). When using every eligible variable would give fewer,
     * the variables are taken in order of score (see {@link #setMinCorrelation(double)}) until the limit is
     * reached, and the rest are left out for that target. The default is 3.
     *
     * @param rowsPerPredictor the limit, at least 1; or 0 for no limit, so that every eligible variable is used
     *                         however few rows there are
     */
    public void setRowsPerPredictor(int rowsPerPredictor) {
        if (rowsPerPredictor < 0) {
            throw new IllegalArgumentException("Rows per predictor must be >= 0: " + rowsPerPredictor);
        }

        this.rowsPerPredictor = rowsPerPredictor;
    }

    /**
     * Sets the highest power of a continuous predictor used in the regressions. With degree d, a continuous
     * predictor x enters as z, z^2, ..., z^d, where z is x standardized by the mean and standard deviation of its
     * observed values. The default, 1, is the linear model. A degree of 2 or 3 lets the fitted value of a variable
     * follow a curved (including non-monotone) dependence on a predictor, such as y = x^2 + e, which a linear fit
     * misses entirely. Discrete predictors are unaffected.
     * <p>
     * Caveats: the model is still additive, so a pure interaction (y = x * w + e) is not captured; each continuous
     * predictor now costs d terms against the row limit (see {@link #setRowsPerPredictor(int)}), so fewer
     * variables may be used on small samples; and this only helps where the dependence shows in the mean of the
     * variable being imputed given the predictor, which for y = x^2 + e is true of y given x but not of x given y.
     *
     * @param predictorDegree the degree, from 1 to 5
     */
    public void setPredictorDegree(int predictorDegree) {
        if (predictorDegree < 1 || predictorDegree > 5) {
            throw new IllegalArgumentException("Predictor degree must be from 1 to 5: " + predictorDegree);
        }

        this.predictorDegree = predictorDegree;
    }

    /**
     * Sets the number of powers of a continuous variable whose fitted values donors are matched on when that
     * variable is imputed. With degree d, a regression is fit for each of z, z^2, ..., z^d, where z is the variable
     * standardized by the mean and standard deviation of its observed values, and donors are the observed rows
     * nearest in the resulting vector of fitted values. Each power is scaled to unit variance, so a power the
     * predictors cannot predict contributes little to the distance. The default, 1, is ordinary predictive mean
     * matching.
     * <p>
     * A degree of 2 matches on the fitted spread as well as the fitted mean, which matters when the predictors say
     * nothing about the variable's mean but do say how far from it the variable is. The case in point is imputing x
     * where y = x^2 + e and x is symmetric about zero: the mean of x given y is zero whatever y is, so matching on
     * the mean picks donors without regard to y, while the mean of x^2 given y tracks y. Since every variable in a
     * chain is imputed from its effects as well as its causes, this is the counterpart of
     * {@link #setPredictorDegree(int)} for the other direction. Discrete variables are unaffected.
     * <p>
     * Caveat: the powers of the variable are themselves fit by regressions that are linear in the predictor terms,
     * so this pays off most with the predictor degree raised as well.
     *
     * @param targetDegree the degree, from 1 to 4
     */
    public void setTargetDegree(int targetDegree) {
        if (targetDegree < 1 || targetDegree > 4) {
            throw new IllegalArgumentException("Target degree must be from 1 to 4: " + targetDegree);
        }

        this.targetDegree = targetDegree;
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

        if (m < 1) throw new IllegalArgumentException("Number of imputations must be >= 1: " + m);

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

        this.powers = new int[p];
        this.moments = new int[p];
        this.center = new double[p];
        this.spread = new double[p];

        for (int j = 0; j < p; j++) {
            this.powers[j] = discrete[j] ? 1 : this.predictorDegree;
            this.moments[j] = discrete[j] ? 1 : this.targetDegree;
            this.spread[j] = 1.0;
            List<Integer> obs = obsRows.get(j);
            if (discrete[j] || obs.isEmpty()) continue;

            double mu = 0.0;
            for (int i : obs) mu += base[i][j];
            mu /= obs.size();
            double ss = 0.0;
            for (int i : obs) ss += (base[i][j] - mu) * (base[i][j] - mu);
            double sd = Math.sqrt(ss / obs.size());

            this.center[j] = mu;
            if (sd > 1e-12 * (1.0 + Math.abs(mu))) this.spread[j] = sd;
        }

        this.events.clear();
        this.updatesPerColumn = m * this.numSweeps;

        String[] names = new String[p];
        for (int j = 0; j < p; j++) names[j] = dataSet.getVariables().get(j).getName();

        // Which variables predict each incomplete variable: null for all the others, the usual case.
        int[][] predictors = new int[p][];
        String[] selectionNotes = new String[p];

        for (int j = 0; j < p; j++) {
            if (!missRows.get(j).isEmpty()) {
                predictors[j] = selectPredictors(base, miss, j, obsRows.get(j), numCategories, names,
                        selectionNotes);
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
                    if (selectionNotes[j] != null) event(names[j], selectionNotes[j]);
                    imputeColumnPmm(work, j, obsRows.get(j), missRows.get(j), p, rand, names, numCategories,
                            predictors[j]);
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
                                 Random rand, String[] names, int[] numCategories, int[] predictors) {
        int nObs = obs.size();

        // The variables that may predict j: the selected ones, or every other variable.
        boolean[] eligible = new boolean[p];

        if (predictors == null) {
            java.util.Arrays.fill(eligible, true);
        } else {
            for (int k : predictors) eligible[k] = true;
        }

        eligible[j] = false;

        if (nObs < 2) {
            event(names[j], "filled by random draws from its own observed values, ignoring the other variables,"
                            + " because it has fewer than 2 observed rows");
            hotDeck(work, j, obs, missing, rand);
            return;
        }

        // Candidate predictor features: a column as a number (and, for a continuous column, its higher powers), or
        // one indicator per non-reference category.
        List<int[]> candidates = new ArrayList<>();

        for (int k = 0; k < p; k++) {
            if (!eligible[k]) continue;

            if (numCategories[k] == 0) {
                for (int d = 1; d <= this.powers[k]; d++) candidates.add(new int[]{k, -d});
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
            if (eligible[k] && !columnUsed[k]) constant.add(names[k]);
        }

        if (!constant.isEmpty()) {
            event(names[j], "predictors constant over its observed rows were left out: "
                            + String.join(", ", constant));
        }

        if (q == 0) {
            event(names[j], "filled by random draws from its own observed values, ignoring the other variables,"
                            + (candidates.isEmpty() ? " because no variable was selected as a predictor"
                    : " because no predictor varies over its observed rows"));
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

        // One regression per target: the variable itself (and, for a continuous variable, its higher powers if
        // asked for), or each of its category indicators.
        int numTargets = numCategories[j] == 0 ? this.moments[j] : numCategories[j];
        double[] meanY = new double[numTargets];
        double[][] b = new double[numTargets][q];

        // The weight of each target in the donor distance. Powers of a variable are on different scales, so each
        // is weighted by the inverse of its variance; its share of the distance then grows with how well it is
        // predicted. Otherwise 1, as before.
        double[] weight = new double[numTargets];

        for (int t = 0; t < numTargets; t++) {
            int category = numCategories[j] == 0 ? -(t + 1) : t;
            for (int row : obs) meanY[t] += feature(work, row, j, category);
            meanY[t] /= nObs;
            weight[t] = 1.0;

            if (numCategories[j] == 0 && numTargets > 1) {
                double ss = 0.0;

                for (int row : obs) {
                    double d = feature(work, row, j, category) - meanY[t];
                    ss += d * d;
                }

                weight[t] = ss > 0 ? nObs / ss : 0.0;
            }

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
                    dist += weight[t] * d * d;
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
     * Chooses the variables that predict column j, from the data as observed (before any imputation). Returns null
     * if every other variable is to be used, which is the case unless a minimum correlation is set or there are
     * too few observed rows of j for that many predictor terms. Otherwise returns the chosen columns, and puts a
     * sentence saying what was done in notes[j].
     */
    private int[] selectPredictors(double[][] base, boolean[][] miss, int j, List<Integer> obs,
                                   int[] numCategories, String[] names, String[] notes) {
        int p = numCategories.length;
        int nObs = obs.size();
        int allTerms = 0;

        for (int k = 0; k < p; k++) {
            if (k != j) allTerms += numCategories[k] == 0 ? this.powers[k] : numCategories[k] - 1;
        }

        int maxTerms = this.rowsPerPredictor > 0 ? Math.max(1, nObs / this.rowsPerPredictor) : Integer.MAX_VALUE;

        if (this.minCorrelation <= 0 && allTerms <= maxTerms) return null;

        // Score each other variable for this target.
        int n = base.length;
        double[] score = new double[p];
        double[] a = new double[n];
        double[] b = new double[n];
        int numTargets = numCategories[j] == 0 ? this.moments[j] : numCategories[j];

        for (int k = 0; k < p; k++) {
            if (k == j) continue;
            int numFeatures = numCategories[k] == 0 ? this.powers[k] : numCategories[k];

            for (int f = 0; f < numFeatures; f++) {
                int category = numCategories[k] == 0 ? -(f + 1) : f;

                // With the indicator that j is missing, over the rows where k is observed.
                int count = 0;

                for (int i = 0; i < n; i++) {
                    if (miss[i][k]) continue;
                    a[count] = feature(base, i, k, category);
                    b[count] = miss[i][j] ? 1.0 : 0.0;
                    count++;
                }

                score[k] = Math.max(score[k], absCorrelation(a, b, count));

                // With j itself (or each of its category indicators), over the rows where both are observed.
                for (int t = 0; t < numTargets; t++) {
                    count = 0;

                    for (int i : obs) {
                        if (miss[i][k]) continue;
                        a[count] = feature(base, i, k, category);
                        b[count] = feature(base, i, j, numCategories[j] == 0 ? -(t + 1) : t);
                        count++;
                    }

                    score[k] = Math.max(score[k], absCorrelation(a, b, count));
                }
            }
        }

        // Highest score first; those under the minimum are out; then as many as the row limit allows.
        List<Integer> order = new ArrayList<>();

        for (int k = 0; k < p; k++) {
            if (k != j && score[k] >= this.minCorrelation) order.add(k);
        }

        int belowMinimum = p - 1 - order.size();
        order.sort((x, y) -> score[x] != score[y] ? Double.compare(score[y], score[x]) : Integer.compare(x, y));

        List<Integer> chosen = new ArrayList<>();
        int terms = 0;

        for (int k : order) {
            int width = numCategories[k] == 0 ? this.powers[k] : numCategories[k] - 1;
            if (terms + width > maxTerms && !chosen.isEmpty()) break;
            chosen.add(k);
            terms += width;
        }

        int overLimit = order.size() - chosen.size();
        StringBuilder note = new StringBuilder("used " + chosen.size() + " of the " + (p - 1)
                                               + " other variables as predictors");

        if (belowMinimum > 0) {
            note.append("; ").append(belowMinimum).append(" scored below the minimum correlation of ")
                    .append(this.minCorrelation);
        }

        if (overLimit > 0) {
            note.append("; ").append(overLimit).append(" more were left out, lowest scores first, to keep at least ")
                    .append(this.rowsPerPredictor).append(" of its ").append(nObs)
                    .append(" observed rows per predictor term");
        }

        notes[j] = note.toString();

        int[] result = new int[chosen.size()];
        for (int c = 0; c < result.length; c++) result[c] = chosen.get(c);
        return result;
    }

    /**
     * The absolute correlation of the first count entries of a and b; 0 if there are fewer than 3 or either is
     * constant.
     */
    private static double absCorrelation(double[] a, double[] b, int count) {
        if (count < 3) return 0.0;
        double meanA = 0.0;
        double meanB = 0.0;

        for (int i = 0; i < count; i++) {
            meanA += a[i];
            meanB += b[i];
        }

        meanA /= count;
        meanB /= count;
        double sab = 0.0;
        double saa = 0.0;
        double sbb = 0.0;

        for (int i = 0; i < count; i++) {
            sab += (a[i] - meanA) * (b[i] - meanB);
            saa += (a[i] - meanA) * (a[i] - meanA);
            sbb += (b[i] - meanB) * (b[i] - meanB);
        }

        if (!(saa > 1e-12) || !(sbb > 1e-12)) return 0.0;
        return Math.abs(sab / Math.sqrt(saa * sbb));
    }

    /**
     * The value of a column in a row as a regression feature. If category is -1, the number itself; if it is -d
     * for d of 2 or more, the dth power of the number standardized by the column's observed mean and standard
     * deviation; otherwise the indicator that the (discrete) value is that category.
     */
    private double feature(double[][] work, int row, int column, int category) {
        if (category == -1) return work[row][column];

        if (category < 0) {
            double z = (work[row][column] - this.center[column]) / this.spread[column];
            double value = z;
            for (int d = 1; d < -category; d++) value *= z;
            return value;
        }

        return Math.round(work[row][column]) == category ? 1.0 : 0.0;
    }

    private static void hotDeck(double[][] work, int j, List<Integer> obs, List<Integer> missing, Random rand) {
        for (int i : missing) work[i][j] = work[obs.get(rand.nextInt(obs.size()))][j];
    }
}
