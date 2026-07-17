///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
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

package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.graph.Node;
import org.apache.commons.math3.distribution.NormalDistribution;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * An immutable, symmetric, name-keyed store of per-edge prior quantities, for use with
 * {@link edu.cmu.tetrad.search.score.EdgePriorScore} and
 * {@link edu.cmu.tetrad.search.test.EdgePriorTest}.
 *
 * <p><b>Why name-keyed and not positional.</b> A caller naturally has a dense matrix indexed by
 * some variable ordering of their own. That ordering is not guaranteed to match the ordering of
 * whatever score or test the wrapper is applied to, and in a random-subspace ensemble it
 * definitely does not: each repeat builds a score or test over a different subset of the
 * variables, so positions shift on every repeat. Keying by variable name and resolving against
 * the delegate's own {@code getVariables()} at construction makes that class of error impossible.
 * Build one {@code EdgePriors} per locus and share it across all repeats; instances are immutable
 * and safe to publish to parallel workers.
 *
 * <p><b>Two semantics, deliberately not interchangeable.</b> {@link Semantics#LOG_ODDS} holds
 * prior log-odds beta_ij = log(p_ij / (1 - p_ij)) with default 0 (no information).
 * {@link Semantics#WEIGHTS} holds multiplicative p-value weights w_ij with default 1 (no
 * information). Both are "a number per edge", but they have different neutral elements and
 * different scales, and passing one where the other is expected is the obvious way to get a
 * silently wrong answer. The wrappers check.
 *
 * @author josephramsey
 */
public final class EdgePriors {

    private static final NormalDistribution NORMAL = new NormalDistribution(0.0, 1.0);
    private static final char SEP = '\u0000';

    private final Map<String, Double> values;
    private final Semantics semantics;
    private final double defaultValue;

    private EdgePriors(Map<String, Double> values, Semantics semantics) {
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        this.semantics = semantics;
        this.defaultValue = semantics.neutral();
    }

    /**
     * What the stored numbers mean.
     */
    public enum Semantics {
        /**
         * Prior log-odds beta_ij; neutral element 0.
         */
        LOG_ODDS(0.0),
        /**
         * Multiplicative p-value weights w_ij; neutral element 1.
         */
        WEIGHTS(1.0);

        private final double neutral;

        Semantics(double neutral) {
            this.neutral = neutral;
        }

        /**
         * Returns the neutral (no-information) value for this semantics.
         *
         * @return 0 for LOG_ODDS, 1 for WEIGHTS.
         */
        public double neutral() {
            return this.neutral;
        }
    }

    /**
     * Returns a matrix of the given size filled with the neutral value for the given semantics, as
     * a starting point for a caller who intends to fill in only the pairs they care about.
     *
     * <p>This exists because {@code new double[p][p]} is all zeros, which is the neutral value for
     * {@link Semantics#LOG_ODDS} but is emphatically <i>not</i> neutral for
     * {@link Semantics#WEIGHTS}: a zero weight forbids an edge. Use this rather than allocating
     * directly.
     *
     * @param p         The number of variables.
     * @param semantics Which semantics.
     * @return A p x p matrix filled with the neutral value.
     */
    public static double[][] neutralMatrix(int p, Semantics semantics) {
        Objects.requireNonNull(semantics, "semantics");

        if (p < 0) {
            throw new IllegalArgumentException("p must be non-negative: " + p);
        }

        double[][] out = new double[p][p];

        if (semantics.neutral() != 0.0) {
            for (double[] row : out) {
                java.util.Arrays.fill(row, semantics.neutral());
            }
        }

        return out;
    }

    /**
     * Builds a store from a dense symmetric matrix and the variable names it is indexed by. The
     * names need not match, or be ordered like, any particular score or test; resolution happens
     * later, at wrapper construction.
     *
     * <p>For {@link Semantics#WEIGHTS}, zero entries are rejected: see
     * {@link #fromMatrix(List, double[][], Semantics, boolean)} for why, and for the opt-in if you
     * really do mean to forbid edges.
     *
     * @param names     The variable names indexing both dimensions of {@code values}.
     * @param values    A symmetric {@code names.size() x names.size()} matrix.
     * @param semantics Whether {@code values} holds log-odds or weights.
     * @return The store.
     * @throws IllegalArgumentException If the matrix is ragged, non-square, asymmetric, contains
     *                                  non-finite entries, or the names contain duplicates; or if
     *                                  a weight is negative or zero.
     */
    public static EdgePriors fromMatrix(List<String> names, double[][] values, Semantics semantics) {
        return fromMatrix(names, values, semantics, false);
    }

    /**
     * Builds a store from a dense symmetric matrix, optionally permitting zero weights.
     *
     * <p>A zero weight sets alpha_ij = 0, so independence is never rejected and the edge is
     * deleted at the first conditioning set tried: it is forbidden knowledge, the p_ij -&gt; 0
     * limit of the prior. That is a legitimate thing to want and rare. It is also what a caller
     * accidentally requests for <i>every unfilled pair</i> when they allocate
     * {@code new double[p][p]} and populate a handful of entries, because a fresh double array is
     * all zeros while the neutral weight is one. Since that mistake deletes edges silently rather
     * than throwing, the safe overload rejects zeros and this one makes the intent explicit. See
     * {@link #neutralMatrix(int, Semantics)}.
     *
     * @param names             The variable names indexing both dimensions of {@code values}.
     * @param values            A symmetric {@code names.size() x names.size()} matrix.
     * @param semantics         Whether {@code values} holds log-odds or weights.
     * @param allowZeroWeights  If true, zero weights are accepted as deliberate edge forbidding.
     *                          Ignored for {@link Semantics#LOG_ODDS}, where zero is neutral.
     * @return The store.
     * @throws IllegalArgumentException If the matrix is ragged, non-square, asymmetric, contains
     *                                  non-finite entries, or the names contain duplicates; or if
     *                                  a weight is negative, or zero without the opt-in.
     */
    public static EdgePriors fromMatrix(List<String> names, double[][] values, Semantics semantics,
                                        boolean allowZeroWeights) {
        Objects.requireNonNull(names, "names");
        Objects.requireNonNull(values, "values");
        Objects.requireNonNull(semantics, "semantics");

        int p = names.size();

        if (values.length != p) {
            throw new IllegalArgumentException("Matrix has " + values.length
                    + " rows but " + p + " names were given.");
        }

        Map<String, Integer> seen = new LinkedHashMap<>();

        for (int i = 0; i < p; i++) {
            String name = names.get(i);

            if (name == null || name.isEmpty()) {
                throw new IllegalArgumentException("Name at index " + i + " is null or empty.");
            }

            if (name.indexOf(SEP) >= 0) {
                throw new IllegalArgumentException("Name '" + name + "' contains a NUL character.");
            }

            Integer prior = seen.put(name, i);

            if (prior != null) {
                throw new IllegalArgumentException("Duplicate name '" + name
                        + "' at indices " + prior + " and " + i + ".");
            }
        }

        Map<String, Double> map = new LinkedHashMap<>();

        for (int i = 0; i < p; i++) {
            if (values[i].length != p) {
                throw new IllegalArgumentException("Row " + i + " has length " + values[i].length
                        + "; expected " + p + ".");
            }

            for (int j = i + 1; j < p; j++) {
                double vij = values[i][j];
                double vji = values[j][i];

                if (!Double.isFinite(vij)) {
                    throw new IllegalArgumentException("Entry [" + i + "][" + j + "] is not finite: " + vij);
                }

                if (vij != vji) {
                    throw new IllegalArgumentException("Matrix is not symmetric at [" + i + "][" + j
                            + "]: " + vij + " vs " + vji
                            + ". Priors are on adjacencies, which are undirected;"
                            + " an asymmetric prior would break score equivalence.");
                }

                if (semantics == Semantics.WEIGHTS && vij < 0.0) {
                    throw new IllegalArgumentException("Negative weight at [" + i + "][" + j + "]: " + vij);
                }

                if (semantics == Semantics.WEIGHTS && vij == 0.0 && !allowZeroWeights) {
                    throw new IllegalArgumentException("Zero weight at [" + i + "][" + j + "] ("
                            + names.get(i) + ", " + names.get(j) + ")."
                            + " A zero weight forbids the edge outright."
                            + " If you meant 'no prior information for this"
                            + " pair', the neutral weight is 1, not 0 --"
                            + " note that a freshly allocated double[p][p]"
                            + " is all zeros, which is the usual cause of"
                            + " this. Start from"
                            + " EdgePriors.neutralMatrix(p, WEIGHTS)."
                            + " To forbid edges deliberately, pass"
                            + " allowZeroWeights = true.");
                }

                if (vij != semantics.neutral()) {
                    map.put(key(names.get(i), names.get(j)), vij);
                }
            }
        }

        return new EdgePriors(map, semantics);
    }

    /**
     * Returns an empty store: every pair takes the neutral value. Wrapping with this must
     * reproduce the delegate's behaviour exactly, which is the regression test.
     *
     * @param semantics Which semantics.
     * @return The store.
     */
    public static EdgePriors neutral(Semantics semantics) {
        Objects.requireNonNull(semantics, "semantics");
        return new EdgePriors(new LinkedHashMap<>(), semantics);
    }

    private static String key(String a, String b) {
        return (a.compareTo(b) <= 0) ? a + SEP + b : b + SEP + a;
    }

    /**
     * Returns the stored value for the unordered pair {a, b}, or the neutral value if none.
     *
     * @param a One variable name.
     * @param b The other variable name.
     * @return The value.
     */
    public double get(String a, String b) {
        Objects.requireNonNull(a, "a");
        Objects.requireNonNull(b, "b");

        if (a.equals(b)) {
            throw new IllegalArgumentException("No prior is defined for the pair (" + a + ", " + a + ").");
        }

        return this.values.getOrDefault(key(a, b), this.defaultValue);
    }

    /**
     * Returns the semantics of the stored values.
     *
     * @return The semantics.
     */
    public Semantics getSemantics() {
        return this.semantics;
    }

    /**
     * Returns the number of pairs carrying a non-neutral value.
     *
     * @return That count.
     */
    public int size() {
        return this.values.size();
    }

    /**
     * Resolves this store against a variable list into a dense symmetric matrix indexed by that
     * list's order. Pairs with no stored value take the neutral value.
     *
     * @param variables The variables, in the order the caller will index by.
     * @return A {@code variables.size() x variables.size()} symmetric matrix.
     * @throws IllegalArgumentException If {@code variables} contains duplicate names.
     */
    public double[][] resolve(List<Node> variables) {
        Objects.requireNonNull(variables, "variables");

        int p = variables.size();
        List<String> names = new ArrayList<>(p);
        Map<String, Integer> seen = new LinkedHashMap<>();

        for (int i = 0; i < p; i++) {
            String name = variables.get(i).getName();
            Integer prior = seen.put(name, i);

            if (prior != null) {
                throw new IllegalArgumentException("Duplicate variable name '" + name
                        + "' at indices " + prior + " and " + i + ".");
            }

            names.add(name);
        }

        double[][] out = new double[p][p];

        if (this.defaultValue != 0.0) {
            for (double[] row : out) {
                java.util.Arrays.fill(row, this.defaultValue);
            }
        }

        for (int i = 0; i < p; i++) {
            out[i][i] = this.defaultValue;

            for (int j = i + 1; j < p; j++) {
                double v = get(names.get(i), names.get(j));
                out[i][j] = v;
                out[j][i] = v;
            }
        }

        return out;
    }

    /**
     * Returns the names mentioned by this store that do not occur among the given variables. A
     * non-empty result usually means the prior was built against a different variable set than
     * the one it is about to be applied to, which is worth knowing about before a thousand
     * repeats run.
     *
     * @param variables The variables to check against.
     * @return The unmatched names, possibly empty.
     */
    public List<String> unmatchedNames(List<Node> variables) {
        Objects.requireNonNull(variables, "variables");

        Map<String, Boolean> present = new LinkedHashMap<>();

        for (Node node : variables) {
            present.put(node.getName(), Boolean.TRUE);
        }

        List<String> missing = new ArrayList<>();

        for (String k : this.values.keySet()) {
            int cut = k.indexOf(SEP);
            String a = k.substring(0, cut);
            String b = k.substring(cut + 1);

            if (!present.containsKey(a) && !missing.contains(a)) {
                missing.add(a);
            }

            if (!present.containsKey(b) && !missing.contains(b)) {
                missing.add(b);
            }
        }

        return missing;
    }

    /**
     * Converts prior log-odds to p-value weights via the BIC bridge. For each pair with a stored
     * beta_ij, set
     *
     * <pre>
     *   c_ij     = sqrt(max(0, lambda * log(n) - 2 * beta_ij))
     *   alpha_ij = 2 * (1 - Phi(c_ij))
     *   w_ij     = alpha_ij / alpha
     * </pre>
     *
     * so that the test's per-edge decision threshold matches what the prior-adjusted SEM BIC
     * would use for the same edge. Note that beta_ij &gt;= lambda * log(n) / 2 yields
     * alpha_ij = 1: the edge becomes undeletable, which is the required-knowledge limit.
     *
     * <p>This conversion needs a trustworthy {@code lambda * log(n)}. For summary-statistic data
     * where the effective sample size is ambiguous, prefer {@link #normalizedToMeanOne()}, whose
     * guarantee does not reference n at all.
     *
     * @param lambda The penalty discount used by the corresponding score.
     * @param n      The sample size.
     * @param alpha  The base significance level the test will compare against.
     * @return A WEIGHTS store.
     * @throws IllegalStateException    If this store does not hold log-odds.
     * @throws IllegalArgumentException If lambda, n, or alpha are out of range.
     */
    public EdgePriors toWeightsViaBicBridge(double lambda, int n, double alpha) {
        if (this.semantics != Semantics.LOG_ODDS) {
            throw new IllegalStateException("The BIC bridge converts log-odds to weights, but this "
                    + "store holds " + this.semantics + ".");
        }

        if (!(lambda > 0.0) || !Double.isFinite(lambda)) {
            throw new IllegalArgumentException("lambda must be positive and finite: " + lambda);
        }

        if (n < 2) {
            throw new IllegalArgumentException("n must be at least 2: " + n);
        }

        if (!(alpha > 0.0 && alpha < 1.0)) {
            throw new IllegalArgumentException("alpha must be in (0, 1): " + alpha);
        }

        double penalty = lambda * Math.log(n);
        Map<String, Double> out = new LinkedHashMap<>();

        for (Map.Entry<String, Double> e : this.values.entrySet()) {
            double c2 = penalty - 2.0 * e.getValue();
            double c = (c2 > 0.0) ? Math.sqrt(c2) : 0.0;
            double alphaIj = 2.0 * (1.0 - NORMAL.cumulativeProbability(c));
            double w = alphaIj / alpha;

            if (w != Semantics.WEIGHTS.neutral()) {
                out.put(e.getKey(), w);
            }
        }

        return new EdgePriors(out, Semantics.WEIGHTS);
    }

    /**
     * Returns a copy of this WEIGHTS store rescaled so that the stored weights average one. This
     * is the normalisation the Genovese-Roeder-Wasserman guarantee requires: with weights fixed
     * in advance and averaging one, FWER (weighted Bonferroni) or FDR (weighted BH) control is
     * preserved for any weights whatever, informative or not, and power improves to the extent
     * the weights correlate with the truth.
     *
     * <p>Pairs with no stored weight keep w = 1, so normalising the stored weights to mean one
     * among themselves leaves the mean over all pairs at one as well.
     *
     * <p><b>Normalise once, at the locus level.</b> The guarantee is about a fixed family of
     * weights. Renormalising inside each subsample repeat would make the weights data-dependent
     * across the family and void it.
     *
     * @return A normalised WEIGHTS store.
     * @throws IllegalStateException If this store does not hold weights, or if the stored weights
     *                               sum to zero.
     */
    public EdgePriors normalizedToMeanOne() {
        if (this.semantics != Semantics.WEIGHTS) {
            throw new IllegalStateException("Mean-one normalisation applies to weights, but this "
                    + "store holds " + this.semantics + ".");
        }

        if (this.values.isEmpty()) {
            return this;
        }

        double sum = 0.0;

        for (double w : this.values.values()) {
            sum += w;
        }

        if (!(sum > 0.0)) {
            throw new IllegalStateException("Stored weights sum to " + sum
                    + "; cannot normalise to mean one.");
        }

        double scale = this.values.size() / sum;
        Map<String, Double> out = new LinkedHashMap<>();

        for (Map.Entry<String, Double> e : this.values.entrySet()) {
            out.put(e.getKey(), e.getValue() * scale);
        }

        return new EdgePriors(out, Semantics.WEIGHTS);
    }

    /**
     * Returns a string representation of this store.
     *
     * @return This string.
     */
    public String toString() {
        return "EdgePriors[" + this.semantics + ", " + this.values.size() + " non-neutral pairs]";
    }
}
