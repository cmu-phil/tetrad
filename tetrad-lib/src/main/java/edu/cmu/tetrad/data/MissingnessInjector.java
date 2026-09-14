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
import java.util.List;

/**
 * Injects missing values into a dataset under a per-cell logistic propensity with two terms: a
 * per-variable level and a latent per-row propensity.
 *
 * <p>For variable j and case i,</p>
 *
 * <p style="margin-left: 24px;">logit P(missing<sub>ij</sub>) = &alpha;<sub>j</sub> +
 * &lambda; &middot; u<sub>i</sub>,&nbsp;&nbsp; u<sub>i</sub> ~ N(0, 1)</p>
 *
 * <p>where u is drawn once per case and shared across all variables. With &lambda; = 0 this reduces to
 * independent per-variable deletion, which is MCAR and is what
 * {@link DataTransforms#addMissingData(DataSet, double[])} does. With &lambda; above zero, cases differ
 * systematically in how much they lose, which is the dominant structural feature of real
 * attrition-driven missingness and the one a constant-rate injector cannot produce at all.</p>
 *
 * <h2>Calibration</h2>
 *
 * <p>Each &alpha;<sub>j</sub> is solved by bisection so that the <i>marginal</i> rate for variable j
 * matches its target rate whatever &lambda; is. Without this, raising &lambda; would also raise the
 * realized rates -- Jensen's inequality applied to the logistic link -- and level could not be varied
 * independently of structure, which would make any study using this tool hard to interpret. The
 * marginal is evaluated against the <i>realized</i> u values, not the standard normal, so the
 * calibration is exact for the sample actually generated rather than in expectation over u.</p>
 *
 * <p>What is intentionally absent: dependence on other variables' observed values (MAR beyond the row
 * effect), dependence on the deleted value itself (MNAR self-masking), and sequential attrition down a
 * variable order. Those are separable terms in the same logistic form and can be added without
 * disturbing what is here.</p>
 *
 * <h2>Scope</h2>
 *
 * <p>This produces missingness that is MCAR when &lambda; = 0 and, strictly, still MCAR when &lambda; is
 * above zero: u is an unobserved variable that is independent of the data, so deletion remains
 * independent of both observed and unobserved <i>data</i> values. The row effect changes the
 * <i>pattern</i> -- clustered rather than scattered -- without changing the mechanism class. It is
 * therefore the right tool for studying how methods degrade when complete cases become scarce, and the
 * wrong one for studying MAR or MNAR bias.</p>
 *
 * @author josephramsey
 * @see DataTransforms#addMissingData(DataSet, double[])
 */
public final class MissingnessInjector {

    /**
     * Bisection bracket for the intercepts. A rate of 1e-6 sits well inside +/- 40 on the logit scale even
     * with a large row effect.
     */
    private static final double ALPHA_BOUND = 40.0;

    /**
     * Bisection iterations. Sixty halvings of an 80-wide bracket is far below the precision that matters
     * for a rate.
     */
    private static final int BISECTION_STEPS = 60;

    private MissingnessInjector() {
    }

    /**
     * The injection specification.
     *
     * @param rates        Target marginal missingness rate per variable, in dataset column order, each in
     *                     [0, 1]. A rate of exactly 0 leaves the variable untouched; a rate of exactly 1
     *                     blanks it entirely.
     * @param rowPropensity The latent row effect &lambda;, at least 0. Zero gives independent deletion.
     *                     Around 1.0 produces roughly twice the spread in per-case missing counts that
     *                     independence would give, which is the magnitude seen in real attrition data.
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
     * A report of what was actually injected, for checking that the realized pattern is the intended one.
     *
     * @param realizedRates   Realized per-variable rate, in column order.
     * @param meanRowMissing  Mean number of missing cells per case.
     * @param sdRowMissing    Standard deviation of the per-case missing count.
     * @param sdIfIndependent The same standard deviation predicted under independent deletion at the
     *                        realized rates, namely the square root of the sum of r(1-r). The ratio of the
     *                        previous field to this one is the clustering actually achieved; it is 1 when
     *                        the row effect is 0.
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
     * Injects missing values into a copy of the data.
     *
     * @param data The data. Not modified.
     * @param spec The specification; its rate count must match the column count.
     * @return The data with values deleted, and a report of the realized pattern.
     * @throws IllegalArgumentException if the rate count does not match the column count.
     */
    public static Result inject(DataSet data, Spec spec) {
        if (spec.rates.length != data.getNumColumns()) {
            throw new IllegalArgumentException("Expected " + data.getNumColumns()
                                               + " missingness rates, one per variable, but got "
                                               + spec.rates.length + ".");
        }

        DataSet out = data.copy();
        int n = out.getNumRows();
        int p = out.getNumColumns();

        // One latent propensity per case, shared across variables. This is what clusters the pattern.
        double[] u = new double[n];
        if (spec.rowPropensity > 0.0) {
            for (int i = 0; i < n; i++) u[i] = RandomUtil.getInstance().nextGaussian(0, 1);
        }

        int[] rowMissing = new int[n];
        double[] realized = new double[p];

        for (int j = 0; j < p; j++) {
            double target = spec.rates[j];
            if (target <= 0.0) continue;

            Node node = out.getVariable(j);
            boolean continuous = node instanceof ContinuousVariable;
            boolean discrete = node instanceof DiscreteVariable;
            if (!continuous && !discrete) continue;

            double alpha = solveAlpha(target, u, spec.rowPropensity);

            int count = 0;
            for (int i = 0; i < n; i++) {
                double p_ij = logistic(alpha + spec.rowPropensity * u[i]);

                if (RandomUtil.getInstance().nextDouble() < p_ij) {
                    if (continuous) out.setDouble(i, j, Double.NaN);
                    else out.setInt(i, j, DiscreteVariable.MISSING_VALUE);
                    rowMissing[i]++;
                    count++;
                }
            }

            realized[j] = count / (double) n;
        }

        return new Result(out, report(realized, rowMissing, n));
    }

    /**
     * The injected data together with its report.
     *
     * @param data   The data with values deleted.
     * @param report What was realized.
     */
    public record Result(DataSet data, Report report) {
    }

    /**
     * Solves for the intercept that makes the mean deletion probability over the realized row effects equal
     * the target rate. Bisection rather than Newton: the objective is monotone in alpha and bounded, so
     * bisection cannot fail, and sixty steps cost nothing at these sizes.
     */
    private static double solveAlpha(double target, double[] u, double lambda) {
        if (lambda == 0.0) return Math.log(target / (1.0 - target));
        if (target >= 1.0) return ALPHA_BOUND;

        double lo = -ALPHA_BOUND;
        double hi = ALPHA_BOUND;

        for (int step = 0; step < BISECTION_STEPS; step++) {
            double mid = 0.5 * (lo + hi);

            double mean = 0.0;
            for (double ui : u) mean += logistic(mid + lambda * ui);
            mean /= u.length;

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
