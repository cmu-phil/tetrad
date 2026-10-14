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
package edu.cmu.tetrad.data;

import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.RandomUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Injects missing values into a dataset under a per-cell logistic propensity, with optional terms that make the
 * mechanism MAR or MNAR as well as MCAR.
 *
 * <p>For variable j and case i,</p>
 *
 * <p style="margin-left: 24px;">logit P(missing<sub>ij</sub>) = &alpha;<sub>j</sub> +
 * &lambda; &middot; u<sub>i</sub> + &Sigma;<sub>k</sub> &beta;<sub>jk</sub> z<sub>ik</sub> +
 * &Sigma;<sub>k</sub> &gamma;<sub>jk</sub> M<sub>ik</sub>,&nbsp;&nbsp; u<sub>i</sub> ~ N(0, 1)</p>
 *
 * <p>where u is a latent per-case propensity shared across variables, z<sub>ik</sub> is the <i>true</i>
 * (pre-deletion) value of variable k in case i, standardized over the input column, and M<sub>ik</sub> is 1 if
 * variable k was deleted in case i. The &beta; terms are <i>value effects</i> and the &gamma; terms are
 * <i>indicator effects</i>; both are supplied through a {@link Mechanism}, and both are absent by default. With
 * no mechanism this is exactly the historical injector, and with the same seed it deletes exactly the same
 * cells.</p>
 *
 * <p>Separately, a {@link Mechanism} can name an <i>attrition order</i>. Variables in that order lose values
 * monotonically: each case drops out at some point along the order and is missing on every later variable in it.
 * The dropout hazard at each step has the same logistic form, so it too can carry a row effect and value
 * effects.</p>
 *
 * <h2>Calibration</h2>
 *
 * <p>Each &alpha;<sub>j</sub> is solved by bisection so that the <i>marginal</i> rate for variable j matches its
 * target rate whatever the other terms are. Without this, adding a row effect or a value effect would also move
 * the realized rates -- Jensen's inequality applied to the logistic link -- and level could not be varied
 * independently of mechanism, which would make any study using this tool hard to interpret. The marginal is
 * evaluated against the <i>realized</i> u values, values and indicators, so the calibration is exact for the
 * sample actually generated rather than in expectation. Under attrition the hazard at each step is calibrated
 * among the cases still present, so that the cumulative rate at that step matches its target; the targets must
 * therefore be nondecreasing along the order.</p>
 *
 * <h2>Mechanism classes</h2>
 *
 * <ul>
 *   <li><b>MCAR</b>: no value effects, directly or through indicator effects. The row effect alone stays MCAR:
 *   u is independent of the data, so it clusters the pattern without changing the mechanism class.</li>
 *   <li><b>MAR</b>: every value the deletion depends on is certain to be observed. That holds for a driver that
 *   is never deleted, and for an attrition hazard driven by variables earlier in the attrition order, which are
 *   observed in every case still present when the hazard applies (sequential, or monotone, MAR).</li>
 *   <li><b>MNAR</b>: anything else, including <i>self-masking</i> (a variable's deletion depends on its own
 *   value). The classification is conservative: a configuration reported as MNAR has not been certified MAR,
 *   which is not a proof that it is not MAR in Rubin's pattern-specific sense.</li>
 * </ul>
 *
 * <p>A <i>colluder</i>, in the sense of Nabi, Bhattacharya and Shpitser (2020), is flagged when one variable's
 * deletion depends on both the value and the indicator of the same other variable. Attrition is deliberately
 * not flagged, although its graph has the colluder shape: the dependence of a later indicator on an earlier one
 * is context-specific there, which a missing-data DAG cannot express, and sequential MAR is identified.</p>
 *
 * <p>The mechanism report also measures how monotone the realized pattern is along an order -- the attrition
 * order if one is given, otherwise column order -- since that decides whether test-wise local scores for one
 * child share their rows.</p>
 *
 * @author josephramsey
 * @see DataTransforms#addMissingData(DataSet, double[])
 */
public final class MissingnessInjector {

    /**
     * Bisection bracket for the intercepts. A rate of 1e-6 sits well inside +/- 40 on the logit scale even with a
     * large row effect.
     */
    private static final double ALPHA_BOUND = 40.0;

    /**
     * Bisection iterations. Sixty halvings of an 80-wide bracket is far below the precision that matters for a
     * rate.
     */
    private static final int BISECTION_STEPS = 60;

    private MissingnessInjector() {
    }

    /**
     * The injection specification.
     *
     * @param rates         Target marginal missingness rate per variable, in dataset column order, each in [0, 1].
     *                      A rate of exactly 0 leaves the variable untouched; a rate of exactly 1 blanks it
     *                      entirely.
     * @param rowPropensity The latent row effect &lambda;, at least 0. Zero gives independent deletion. Around 1.0
     *                      produces roughly twice the spread in per-case missing counts that independence would
     *                      give, which is the magnitude seen in real attrition data.
     */
    public record Spec(double[] rates, double rowPropensity) {

        /**
         * Canonical constructor, validating the rates and the row effect.
         */
        public Spec {
            if (rates == null) throw new NullPointerException("rates");

            for (double r : rates) {
                if (!(r >= 0.0 && r <= 1.0) || Double.isNaN(r)) {
                    throw new IllegalArgumentException("Missingness rate out of range [0, 1]: " + r);
                }
            }

            if (!(rowPropensity >= 0.0) || !Double.isFinite(rowPropensity)) {
                throw new IllegalArgumentException("Row propensity must be finite and at least 0: "
                                                   + rowPropensity);
            }

            rates = rates.clone();
        }

        /**
         * A uniform-rate spec, reproducing the historical constant-rate behavior.
         *
         * @param numVars       Number of variables.
         * @param rate          The rate to apply to every variable.
         * @param rowPropensity The latent row effect.
         * @return The spec.
         */
        public static Spec uniform(int numVars, double rate, double rowPropensity) {
            double[] rates = new double[numVars];
            Arrays.fill(rates, rate);
            return new Spec(rates, rowPropensity);
        }

        /**
         * Returns a copy of the rates, so the array cannot be mutated through the record.
         *
         * @return The rates.
         */
        @Override
        public double[] rates() {
            return rates.clone();
        }
    }

    /**
     * One term in the deletion propensity of a target variable: the effect of a driver variable's standardized
     * value, or of its missingness indicator, on the log odds that the target is deleted. Positive weights make
     * deletion more likely when the driver is high, or when the driver is missing.
     *
     * @param target Column index of the variable whose deletion this affects.
     * @param driver Column index of the variable whose value or indicator drives it.
     * @param weight The coefficient on the logit scale. Finite.
     */
    public record Effect(int target, int driver, double weight) {

        /**
         * Canonical constructor, validating the indices and the weight.
         */
        public Effect {
            if (target < 0 || driver < 0) {
                throw new IllegalArgumentException("Column indices must be nonnegative: " + target + ", " + driver);
            }

            if (!Double.isFinite(weight)) {
                throw new IllegalArgumentException("Effect weight must be finite: " + weight);
            }
        }
    }

    /**
     * The terms of the deletion mechanism beyond the per-variable level and the row effect. Immutable; build one
     * from {@link #none()} with the {@code with} methods. Column indices are checked against the data when the
     * mechanism is used, not when it is built. Weights for the same target and driver add.
     *
     * @param valueEffects     Value effects (&beta;). A term whose target equals its driver is self-masking.
     * @param indicatorEffects Indicator effects (&gamma;). These must not form a cycle, since a variable's
     *                         indicators must be realized before they can drive another's deletion; a self-loop is
     *                         a cycle.
     * @param attritionOrder   Columns that lose values by monotone attrition, in order, or null for none.
     */
    public record Mechanism(List<Effect> valueEffects, List<Effect> indicatorEffects, int[] attritionOrder) {

        /**
         * Canonical constructor, making defensive copies.
         */
        public Mechanism {
            valueEffects = valueEffects == null ? List.of() : List.copyOf(valueEffects);
            indicatorEffects = indicatorEffects == null ? List.of() : List.copyOf(indicatorEffects);
            attritionOrder = attritionOrder == null ? null : attritionOrder.clone();
        }

        /**
         * The empty mechanism: no value effects, no indicator effects, no attrition. With it the injector is MCAR.
         *
         * @return The empty mechanism.
         */
        public static Mechanism none() {
            return new Mechanism(List.of(), List.of(), null);
        }

        /**
         * Adds a value effect: the standardized true value of {@code driver} shifts the log odds that
         * {@code target} is deleted. With {@code target == driver} this is self-masking.
         *
         * @param target The target column.
         * @param driver The driver column.
         * @param weight The coefficient on the logit scale.
         * @return A new mechanism with the term added.
         */
        public Mechanism withValueEffect(int target, int driver, double weight) {
            List<Effect> v = new ArrayList<>(valueEffects);
            v.add(new Effect(target, driver, weight));
            return new Mechanism(v, indicatorEffects, attritionOrder);
        }

        /**
         * Adds self-masking: the variable's own true value shifts the log odds that it is deleted. A negative
         * weight deletes low values preferentially.
         *
         * @param target The column.
         * @param weight The coefficient on the logit scale.
         * @return A new mechanism with the term added.
         */
        public Mechanism withSelfMasking(int target, double weight) {
            return withValueEffect(target, target, weight);
        }

        /**
         * Adds an indicator effect: whether {@code driver} was deleted in a case shifts the log odds that
         * {@code target} is deleted in that case.
         *
         * @param target The target column.
         * @param driver The driver column.
         * @param weight The coefficient on the logit scale.
         * @return A new mechanism with the term added.
         */
        public Mechanism withIndicatorEffect(int target, int driver, double weight) {
            List<Effect> g = new ArrayList<>(indicatorEffects);
            g.add(new Effect(target, driver, weight));
            return new Mechanism(valueEffects, g, attritionOrder);
        }

        /**
         * Sets the attrition order. Variables in it lose values monotonically along it, with the per-variable
         * rates read as cumulative dropout rates, which must therefore be nondecreasing along the order. Value
         * effects whose target is in the order act on the dropout hazard at that step. Indicator effects may not
         * target a variable in the order.
         *
         * @param order Column indices, distinct, earliest first.
         * @return A new mechanism with this attrition order.
         */
        public Mechanism withAttrition(int... order) {
            return new Mechanism(valueEffects, indicatorEffects, order);
        }

        /**
         * Returns a copy of the attrition order, or null.
         *
         * @return The attrition order.
         */
        @Override
        public int[] attritionOrder() {
            return attritionOrder == null ? null : attritionOrder.clone();
        }
    }

    /**
     * A report of what was actually injected, for checking that the realized pattern is the intended one.
     *
     * @param realizedRates   Realized per-variable rate, in column order.
     * @param meanRowMissing  Mean number of missing cells per case.
     * @param sdRowMissing    Standard deviation of the per-case missing count.
     * @param sdIfIndependent The same standard deviation predicted under independent deletion at the realized
     *                        rates, namely the square root of the sum of r(1-r). The ratio of the previous field to
     *                        this one is the clustering actually achieved; it is 1 when the row effect is 0.
     * @param completeRows    Cases with no missing cell at all.
     */
    public record Report(double[] realizedRates, double meanRowMissing, double sdRowMissing,
                         double sdIfIndependent, int completeRows) {

        /**
         * A one-line summary.
         *
         * @return The summary.
         */
        public String description() {
            return String.format(
                    "per-case missing: mean %.2f, sd %.2f (sd %.2f under independence, clustering %.2fx);"
                    + " %d complete case(s).",
                    meanRowMissing, sdRowMissing, sdIfIndependent,
                    sdIfIndependent > 0 ? sdRowMissing / sdIfIndependent : 1.0, completeRows);
        }
    }

    /**
     * The mechanism class of an injection.
     */
    public enum MechanismClass {

        /**
         * Deletion depends on no data value.
         */
        MCAR,

        /**
         * Deletion depends only on values certain to be observed.
         */
        MAR,

        /**
         * Not certified MAR; includes self-masking.
         */
        MNAR
    }

    /**
     * What the mechanism was, and how monotone the realized pattern is.
     *
     * @param overall                  The worst class over the deleted variables (MNAR worse than MAR worse than
     *                                 MCAR).
     * @param perVariable              The class of each variable, in column order; MCAR for a variable with
     *                                 nothing deleted.
     * @param selfMasking              Whether each variable's deletion depends on its own value, in column order.
     * @param colluded                 Whether each variable's deletion depends on both the value and the
     *                                 indicator of some other variable, in column order.
     * @param order                    The order the monotonicity measures are taken along: the attrition order
     *                                 if one was given, otherwise all columns in column order.
     * @param monotoneRows             Cases in which, along that order, no value is observed after one is
     *                                 missing.
     * @param earlierMissingGivenLater Over pairs s before t in the order and cases where t is observed, the
     *                                 fraction in which s is missing. Zero for a monotone pattern; this is the
     *                                 share of a child's rows that an earlier-variable parent would remove under
     *                                 test-wise deletion, pooled over pairs.
     */
    public record MechanismReport(MechanismClass overall, List<MechanismClass> perVariable, boolean[] selfMasking,
                                  boolean[] colluded, int[] order, int monotoneRows,
                                  double earlierMissingGivenLater) {

        /**
         * A one-line summary.
         *
         * @return The summary.
         */
        public String description() {
            int sm = 0, co = 0;
            for (boolean b : selfMasking) if (b) sm++;
            for (boolean b : colluded) if (b) co++;
            return String.format("mechanism %s; %d self-masking, %d colluded variable(s); along an order of %d"
                                 + " variable(s), P(earlier missing | later observed) = %.3f.",
                    overall, sm, co, order.length, earlierMissingGivenLater);
        }
    }

    /**
     * Injects missing values into a copy of the data, MCAR. This is {@link #inject(DataSet, Spec, Mechanism)}
     * with {@link Mechanism#none()}, and deletes exactly the cells the injector always has for a given seed.
     *
     * @param data The data. Not modified.
     * @param spec The specification; its rate count must match the column count.
     * @return The data with values deleted, and a report of the realized pattern.
     * @throws IllegalArgumentException if the rate count does not match the column count.
     */
    public static Result inject(DataSet data, Spec spec) {
        return inject(data, spec, Mechanism.none());
    }

    /**
     * Injects missing values into a copy of the data under the given mechanism.
     *
     * <p>Value effects read the input's values, which are taken to be the true values; a cell already missing in
     * the input contributes 0, the column mean, to its standardized value. Variables that are neither continuous
     * nor discrete are never deleted, but may drive deletion.</p>
     *
     * @param data      The data. Not modified.
     * @param spec      The specification; its rate count must match the column count.
     * @param mechanism The mechanism terms.
     * @return The data with values deleted, a report of the realized pattern, and a report of the mechanism.
     * @throws IllegalArgumentException if the rate count does not match the column count, a column index is out
     *                                  of range, the indicator effects form a cycle, an indicator effect targets an
     *                                  attrition variable, the attrition order repeats a column or names one that
     *                                  cannot be deleted, or the rates decrease along it.
     */
    public static Result inject(DataSet data, Spec spec, Mechanism mechanism) {
        if (mechanism == null) throw new NullPointerException("mechanism");

        if (spec.rates.length != data.getNumColumns()) {
            throw new IllegalArgumentException("Expected " + data.getNumColumns()
                                               + " missingness rates, one per variable, but got "
                                               + spec.rates.length + ".");
        }

        DataSet out = data.copy();
        int n = out.getNumRows();
        int p = out.getNumColumns();

        int[] attrition = mechanism.attritionOrder == null ? new int[0] : mechanism.attritionOrder;
        int[] position = validate(out, spec, mechanism, attrition, p);

        // Per target, its value and indicator terms, summed by driver.
        double[][] beta = sumEffects(mechanism.valueEffects, p);
        double[][] gamma = sumEffects(mechanism.indicatorEffects, p);

        // One latent propensity per case, shared across variables. This is what clusters the pattern. Drawn
        // first, and only when used, so that the random stream matches the historical injector.
        double[] u = new double[n];
        if (spec.rowPropensity > 0.0) {
            for (int i = 0; i < n; i++) u[i] = RandomUtil.getInstance().nextGaussian(0, 1);
        }

        // Standardized true values, for the columns that drive something.
        double[][] z = new double[p][];
        for (Effect e : mechanism.valueEffects) {
            if (z[e.driver] == null) z[e.driver] = standardize(data, e.driver);
        }

        boolean[][] missing = new boolean[p][n];
        int[] rowMissing = new int[n];
        double[] realized = new double[p];

        // Attrition first: its indicators may drive the per-cell variables.
        if (attrition.length > 0) {
            boolean[] present = new boolean[n];
            Arrays.fill(present, true);
            int absent = 0;

            for (int j : attrition) {
                double frac = absent / (double) n;
                double hazard = frac >= 1.0 ? 0.0 : (spec.rates[j] - frac) / (1.0 - frac);
                hazard = Math.max(0.0, Math.min(1.0, hazard));

                if (hazard > 0.0) {
                    int numPresent = n - absent;
                    double[] offsets = new double[numPresent];
                    int[] rows = new int[numPresent];
                    boolean zero = spec.rowPropensity == 0.0;

                    for (int i = 0, r = 0; i < n; i++) {
                        if (!present[i]) continue;
                        rows[r] = i;
                        offsets[r] = linearPredictor(i, j, spec.rowPropensity, u, beta, z, null, missing);
                        if (offsets[r] != 0.0) zero = false;
                        r++;
                    }

                    double alpha = solveAlpha(hazard, offsets, zero);

                    for (int r = 0; r < numPresent; r++) {
                        if (RandomUtil.getInstance().nextDouble() < logistic(alpha + offsets[r])) {
                            present[rows[r]] = false;
                            absent++;
                        }
                    }
                }

                int count = 0;
                for (int i = 0; i < n; i++) {
                    if (!present[i]) {
                        delete(out, i, j);
                        missing[j][i] = true;
                        rowMissing[i]++;
                        count++;
                    }
                }

                realized[j] = count / (double) n;
            }
        }

        // Per-cell deletion, drivers before the variables they drive.
        for (int j : perCellOrder(gamma, position, p)) {
            double target = spec.rates[j];
            if (target <= 0.0) continue;

            Node node = out.getVariable(j);
            if (!(node instanceof ContinuousVariable) && !(node instanceof DiscreteVariable)) continue;

            double[] offsets = new double[n];
            boolean zero = spec.rowPropensity == 0.0;

            for (int i = 0; i < n; i++) {
                offsets[i] = linearPredictor(i, j, spec.rowPropensity, u, beta, z, gamma, missing);
                if (offsets[i] != 0.0) zero = false;
            }

            double alpha = solveAlpha(target, offsets, zero);

            int count = 0;
            for (int i = 0; i < n; i++) {
                double p_ij = logistic(alpha + offsets[i]);

                if (RandomUtil.getInstance().nextDouble() < p_ij) {
                    delete(out, i, j);
                    missing[j][i] = true;
                    rowMissing[i]++;
                    count++;
                }
            }

            realized[j] = count / (double) n;
        }

        MechanismReport mechanismReport = classify(beta, gamma, attrition, position, realized, missing, n, p);
        return new Result(out, report(realized, rowMissing, n), mechanismReport);
    }

    /**
     * The injected data together with its reports.
     *
     * @param data      The data with values deleted.
     * @param report    What was realized.
     * @param mechanism The mechanism class and the monotonicity of the realized pattern.
     */
    public record Result(DataSet data, Report report, MechanismReport mechanism) {
    }

    // ---------------------------------------------------------------- injection helpers

    /**
     * Checks the mechanism against the data and returns each column's position in the attrition order, or -1.
     */
    private static int[] validate(DataSet data, Spec spec, Mechanism mechanism, int[] attrition, int p) {
        for (Effect e : mechanism.valueEffects) checkRange(e, p, "value");
        for (Effect e : mechanism.indicatorEffects) checkRange(e, p, "indicator");

        int[] position = new int[p];
        Arrays.fill(position, -1);

        for (int t = 0; t < attrition.length; t++) {
            int j = attrition[t];

            if (j < 0 || j >= p) {
                throw new IllegalArgumentException("Attrition order names column " + j + ", but there are " + p
                                                   + ".");
            }

            if (position[j] >= 0) {
                throw new IllegalArgumentException("Attrition order repeats column " + j + ".");
            }

            Node node = data.getVariable(j);
            if (!(node instanceof ContinuousVariable) && !(node instanceof DiscreteVariable)) {
                throw new IllegalArgumentException("Attrition variable " + node.getName()
                                                   + " is neither continuous nor discrete, so cannot be deleted.");
            }

            if (t > 0 && spec.rates[j] < spec.rates[attrition[t - 1]]) {
                throw new IllegalArgumentException("Under attrition the rates are cumulative and must not decrease"
                                                   + " along the order, but " + node.getName() + " has "
                                                   + spec.rates[j] + " after "
                                                   + data.getVariable(attrition[t - 1]).getName() + " with "
                                                   + spec.rates[attrition[t - 1]] + ".");
            }

            position[j] = t;
        }

        for (Effect e : mechanism.indicatorEffects) {
            if (position[e.target] >= 0) {
                throw new IllegalArgumentException("Indicator effects may not target an attrition variable, but one"
                                                   + " targets " + data.getVariable(e.target).getName() + ".");
            }
        }

        return position;
    }

    private static void checkRange(Effect e, int p, String kind) {
        if (e.target >= p || e.driver >= p) {
            throw new IllegalArgumentException("A " + kind + " effect names column " + Math.max(e.target, e.driver)
                                               + ", but there are " + p + ".");
        }
    }

    private static double[][] sumEffects(List<Effect> effects, int p) {
        double[][] w = new double[p][];
        for (Effect e : effects) {
            if (w[e.target] == null) w[e.target] = new double[p];
            w[e.target][e.driver] += e.weight;
        }
        return w;
    }

    /**
     * The order for per-cell deletion: the variables not under attrition, each after every variable whose
     * indicator drives it, ties broken by column index. With no indicator effects this is column order, which
     * keeps the random stream the historical one.
     *
     * @throws IllegalArgumentException if the indicator effects among these variables form a cycle.
     */
    private static List<Integer> perCellOrder(double[][] gamma, int[] position, int p) {
        int[] inDegree = new int[p];
        List<List<Integer>> drives = new ArrayList<>();
        for (int k = 0; k < p; k++) drives.add(new ArrayList<>());

        for (int j = 0; j < p; j++) {
            if (gamma[j] == null || position[j] >= 0) continue;
            for (int k = 0; k < p; k++) {
                // An attrition driver is realized before any per-cell variable, so it imposes no ordering here.
                if (gamma[j][k] != 0.0 && position[k] < 0) {
                    inDegree[j]++;
                    drives.get(k).add(j);
                }
            }
        }

        PriorityQueue<Integer> ready = new PriorityQueue<>();
        int count = 0;
        for (int j = 0; j < p; j++) {
            if (position[j] >= 0) continue;
            count++;
            if (inDegree[j] == 0) ready.add(j);
        }

        List<Integer> order = new ArrayList<>();
        while (!ready.isEmpty()) {
            int k = ready.poll();
            order.add(k);
            for (int j : drives.get(k)) {
                if (--inDegree[j] == 0) ready.add(j);
            }
        }

        if (order.size() != count) {
            throw new IllegalArgumentException("The indicator effects form a cycle, so no variable in it can be"
                                               + " deleted first.");
        }

        return order;
    }

    /**
     * Everything in the log odds of deleting (i, j) except the intercept. Terms are added only when present, so
     * that with no mechanism the value is exactly lambda u_i, bit for bit.
     */
    private static double linearPredictor(int i, int j, double lambda, double[] u, double[][] beta, double[][] z,
                                          double[][] gamma, boolean[][] missing) {
        double eta = lambda * u[i];

        if (beta[j] != null) {
            for (int k = 0; k < beta[j].length; k++) {
                if (beta[j][k] != 0.0) eta += beta[j][k] * z[k][i];
            }
        }

        if (gamma != null && gamma[j] != null) {
            for (int k = 0; k < gamma[j].length; k++) {
                if (gamma[j][k] != 0.0 && missing[k][i]) eta += gamma[j][k];
            }
        }

        return eta;
    }

    /**
     * A column's values standardized over its non-missing cells, with missing cells and constant columns at 0.
     * Discrete columns are standardized on their category indices.
     */
    private static double[] standardize(DataSet data, int col) {
        int n = data.getNumRows();
        Node node = data.getVariable(col);
        boolean discrete = node instanceof DiscreteVariable;

        double[] x = new double[n];
        boolean[] ok = new boolean[n];
        double sum = 0.0;
        int m = 0;

        for (int i = 0; i < n; i++) {
            double v;
            if (discrete) {
                int c = data.getInt(i, col);
                v = c == DiscreteVariable.MISSING_VALUE ? Double.NaN : c;
            } else {
                v = data.getDouble(i, col);
            }

            if (!Double.isNaN(v)) {
                x[i] = v;
                ok[i] = true;
                sum += v;
                m++;
            }
        }

        double[] zed = new double[n];
        if (m < 2) return zed;

        double mean = sum / m;
        double ss = 0.0;
        for (int i = 0; i < n; i++) if (ok[i]) ss += (x[i] - mean) * (x[i] - mean);
        double sd = Math.sqrt(ss / (m - 1));
        if (!(sd > 0.0)) return zed;

        for (int i = 0; i < n; i++) if (ok[i]) zed[i] = (x[i] - mean) / sd;
        return zed;
    }

    private static void delete(DataSet out, int i, int j) {
        if (out.getVariable(j) instanceof DiscreteVariable) out.setInt(i, j, DiscreteVariable.MISSING_VALUE);
        else out.setDouble(i, j, Double.NaN);
    }

    /**
     * Solves for the intercept that makes the mean deletion probability over the realized offsets equal the target
     * rate. Bisection rather than Newton: the objective is monotone in alpha and bounded, so bisection cannot fail,
     * and sixty steps cost nothing at these sizes. With every offset zero the answer is the logit of the target,
     * in closed form.
     */
    private static double solveAlpha(double target, double[] offsets, boolean allZero) {
        if (allZero) return Math.log(target / (1.0 - target));
        if (target >= 1.0) return ALPHA_BOUND;

        double lo = -ALPHA_BOUND;
        double hi = ALPHA_BOUND;

        for (int step = 0; step < BISECTION_STEPS; step++) {
            double mid = 0.5 * (lo + hi);

            double mean = 0.0;
            for (double o : offsets) mean += logistic(mid + o);
            mean /= offsets.length;

            if (mean < target) lo = mid;
            else hi = mid;
        }

        return 0.5 * (lo + hi);
    }

    private static double logistic(double z) {
        // Split by sign to avoid overflow of exp for large positive z.
        if (z >= 0) return 1.0 / (1.0 + Math.exp(-z));
        double e = Math.exp(z);
        return e / (1.0 + e);
    }

    private static Report report(double[] realized, int[] rowMissing, int n) {
        double mean = 0.0;
        for (int c : rowMissing) mean += c;
        mean /= n;

        double ss = 0.0;
        for (int c : rowMissing) ss += (c - mean) * (c - mean);
        double sd = n > 1 ? Math.sqrt(ss / (n - 1)) : 0.0;

        double varIndep = 0.0;
        for (double r : realized) varIndep += r * (1.0 - r);

        int complete = 0;
        for (int c : rowMissing) if (c == 0) complete++;

        return new Report(realized, mean, sd, Math.sqrt(varIndep), complete);
    }

    // ---------------------------------------------------------------- classification

    /**
     * Classifies the mechanism, variable by variable, from the terms that were actually in play, and measures the
     * monotonicity of the realized pattern.
     */
    private static MechanismReport classify(double[][] beta, double[][] gamma, int[] attrition, int[] position,
                                            double[] realized, boolean[][] missing, int n, int p) {
        List<MechanismClass> perVariable = new ArrayList<>();
        boolean[] selfMasking = new boolean[p];
        boolean[] colluded = new boolean[p];
        MechanismClass overall = MechanismClass.MCAR;

        // Memo of value dependences: for each variable, the columns whose values its deletion depends on, each with
        // whether that value is certain to be observed whenever the dependence acts.
        List<Set<Dependence>> memo = new ArrayList<>(Collections.nCopies(p, null));

        for (int j = 0; j < p; j++) {
            if (realized[j] == 0.0) {
                perVariable.add(MechanismClass.MCAR);
                continue;
            }

            Set<Dependence> deps = dependences(j, beta, gamma, attrition, position, realized, memo);

            MechanismClass c = MechanismClass.MCAR;
            for (Dependence d : deps) {
                if (d.column == j) selfMasking[j] = true;
                if (d.observed && d.column != j) {
                    if (c == MechanismClass.MCAR) c = MechanismClass.MAR;
                } else {
                    c = MechanismClass.MNAR;
                }
            }

            if (beta[j] != null && gamma[j] != null) {
                for (int k = 0; k < p; k++) {
                    if (k != j && beta[j][k] != 0.0 && gamma[j][k] != 0.0) colluded[j] = true;
                }
            }

            perVariable.add(c);
            if (c.compareTo(overall) > 0) overall = c;
        }

        int[] order = attrition.length > 0 ? attrition.clone() : identity(p);

        int monotoneRows = 0;
        for (int i = 0; i < n; i++) {
            boolean seenMissing = false, monotone = true;
            for (int j : order) {
                if (missing[j][i]) seenMissing = true;
                else if (seenMissing) { monotone = false; break; }
            }
            if (monotone) monotoneRows++;
        }

        // Pooled over pairs s before t: cases with t observed and s missing, over cases with t observed, counted
        // once per earlier variable.
        long numerator = 0, denominator = 0;
        int[] missingSoFar = new int[n];
        for (int t = 0; t < order.length; t++) {
            int j = order[t];
            for (int i = 0; i < n; i++) {
                if (!missing[j][i]) {
                    numerator += missingSoFar[i];
                    denominator += t;
                }
            }
            for (int i = 0; i < n; i++) if (missing[j][i]) missingSoFar[i]++;
        }

        double earlierMissingGivenLater = denominator == 0 ? 0.0 : numerator / (double) denominator;

        return new MechanismReport(overall, List.copyOf(perVariable), selfMasking, colluded, order, monotoneRows,
                earlierMissingGivenLater);
    }

    /**
     * A dependence of some variable's deletion on the value of {@code column}; {@code observed} if that value is
     * certain to be observed whenever the dependence acts.
     */
    private record Dependence(int column, boolean observed) {
    }

    private static Set<Dependence> dependences(int j, double[][] beta, double[][] gamma, int[] attrition,
                                               int[] position, double[] realized, List<Set<Dependence>> memo) {
        if (memo.get(j) != null) return memo.get(j);

        Set<Dependence> deps = new LinkedHashSet<>();

        if (position[j] >= 0) {
            // An attrition variable is missing if the case dropped out at its step or any earlier one, so it
            // depends on every earlier hazard's drivers. A driver earlier in the order than the hazard's own step is
            // observed in every case still present there; so is one never deleted.
            for (int s = 0; s <= position[j]; s++) {
                int h = attrition[s];
                if (beta[h] == null) continue;
                for (int k = 0; k < beta[h].length; k++) {
                    if (beta[h][k] == 0.0) continue;
                    boolean observed = k != h && (realized[k] == 0.0 || (position[k] >= 0 && position[k] < s));
                    deps.add(new Dependence(k, observed));
                }
            }
        } else {
            if (beta[j] != null) {
                for (int k = 0; k < beta[j].length; k++) {
                    if (beta[j][k] == 0.0) continue;
                    deps.add(new Dependence(k, k != j && realized[k] == 0.0));
                }
            }

            // Through an indicator, j inherits the driver's dependences. Those certified only by position in the
            // attrition order are not certified for j, which is not deleted at that step; only never-deleted
            // columns remain certainly observed.
            if (gamma[j] != null) {
                for (int k = 0; k < gamma[j].length; k++) {
                    if (gamma[j][k] == 0.0) continue;
                    for (Dependence d : dependences(k, beta, gamma, attrition, position, realized, memo)) {
                        deps.add(new Dependence(d.column, d.column != j && realized[d.column] == 0.0));
                    }
                }
            }
        }

        memo.set(j, deps);
        return deps;
    }

    private static int[] identity(int p) {
        int[] order = new int[p];
        for (int j = 0; j < p; j++) order[j] = j;
        return order;
    }

    // ---------------------------------------------------------------- parsing

    /**
     * Parses a hand-specified rate profile.
     *
     * <p>Accepts a comma or whitespace separated list of rates, one per variable in dataset order, or a
     * list of {@code name=rate} assignments in any order, with variables not named taking
     * {@code defaultRate}. The two forms may not be mixed. An empty or blank text gives the default rate
     * throughout.</p>
     *
     * @param text        The profile text.
     * @param variables   The dataset variables, in column order.
     * @param defaultRate The rate for variables not named, used only by the {@code name=rate} form.
     * @return The rates, in column order.
     * @throws IllegalArgumentException on an unparsable number, an out-of-range rate, an unknown variable
     *                                  name, or a positional list of the wrong length. The message says
     *                                  which.
     */
    public static double[] parseRateProfile(String text, List<Node> variables, double defaultRate) {
        int p = variables.size();

        if (text == null || text.isBlank()) {
            double[] rates = new double[p];
            Arrays.fill(rates, defaultRate);
            return rates;
        }

        List<String> tokens = new ArrayList<>();
        for (String t : text.trim().split("[,\\s]+")) {
            if (!t.isBlank()) tokens.add(t.trim());
        }

        boolean named = tokens.stream().anyMatch(t -> t.indexOf('=') >= 0);

        if (named) {
            if (!tokens.stream().allMatch(t -> t.indexOf('=') >= 0)) {
                throw new IllegalArgumentException(
                        "Mixed forms: use either a positional list of rates or all name=rate assignments,"
                        + " not both.");
            }

            double[] rates = new double[p];
            Arrays.fill(rates, defaultRate);

            for (String t : tokens) {
                int eq = t.indexOf('=');
                String name = t.substring(0, eq).trim();
                double rate = parseRate(t.substring(eq + 1).trim(), name);

                int col = -1;
                for (int j = 0; j < p; j++) {
                    if (variables.get(j).getName().equals(name)) { col = j; break; }
                }

                if (col < 0) {
                    throw new IllegalArgumentException("No variable named " + name + " in this dataset.");
                }

                rates[col] = rate;
            }

            return rates;
        }

        if (tokens.size() != p) {
            throw new IllegalArgumentException("Expected " + p + " rates, one per variable in dataset order,"
                                               + " but got " + tokens.size()
                                               + ". Use name=rate assignments to set only some.");
        }

        double[] rates = new double[p];
        for (int j = 0; j < p; j++) {
            rates[j] = parseRate(tokens.get(j), variables.get(j).getName());
        }

        return rates;
    }

    private static double parseRate(String s, String where) {
        double r;

        try {
            r = Double.parseDouble(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Could not read a rate for " + where + " from \"" + s + "\".");
        }

        if (!(r >= 0.0 && r <= 1.0)) {
            throw new IllegalArgumentException("Rate for " + where + " must be in [0, 1] but was " + r + ".");
        }

        return r;
    }
}
