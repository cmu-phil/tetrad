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

package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.utils.EdgePriors;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Wraps an {@link IndependenceTest} and gives each pair of variables its own significance level,
 * alpha_ij = w_ij * alpha, by testing the weighted p-value p_ij / w_ij against a common alpha.
 *
 * <p>The pair (x, y) is fixed across every conditioning set that could delete the adjacency, so
 * w_xy is well defined for the whole sequence of tests on that pair, and the threshold does not
 * depend on the order in which pairs or conditioning sets are visited. PC-Stable's
 * order-independence is therefore preserved.
 *
 * <p><b>Direction of the effect.</b> PC removes an edge when it fails to reject independence,
 * that is when p &gt; alpha. A larger threshold makes rejection easier and so protects the edge;
 * a smaller one makes deletion easier. Hence w_xy &gt; 1 shields an edge from deletion and
 * w_xy &lt; 1 accelerates it. Note which way round this is before choosing weights: if the
 * failure mode being addressed is <i>under</i>-deletion, the useful weights are below one.
 *
 * <p><b>Two calibrations, both supported through {@link EdgePriors}.</b> Either derive w_ij from
 * prior log-odds via {@link EdgePriors#toWeightsViaBicBridge(double, int, double)}, which matches
 * the per-edge threshold that a prior-adjusted SEM BIC would use for the same edge and so puts
 * the score-based and constraint-based algorithms on one prior scale; or supply weights directly
 * and call {@link EdgePriors#normalizedToMeanOne()}, which is the Genovese-Roeder-Wasserman
 * formulation and carries an error-control guarantee for any fixed mean-one weights. The bridge
 * needs a trustworthy lambda log(n); the GRW route mentions no n at all. For summary-statistic
 * data, prefer GRW.
 *
 * <p><b>What the bridge does not do.</b> The BIC bridge equates a per-decision threshold. It does
 * not equate procedures. PC deletes an edge on finding <i>any</i> separating set, which at
 * conditioning depth 3 over a hundred nodes is a minimum taken over on the order of 10^5 tests,
 * whereas a score-based search makes one global choice of parent set. At matched lambda, PC
 * therefore deletes considerably more, and a comparison of tuned operating points across the two
 * families has to carry that caveat.
 *
 * @author josephramsey
 * @see EdgePriors
 * @see edu.cmu.tetrad.search.score.EdgePriorScore
 */
public class EdgePriorTest implements IndependenceTest {

    private final IndependenceTest test;
    private final Map<Node, Integer> indices;
    private final double[][] weights;
    private final double alpha;
    private boolean verbose;

    /**
     * Constructs a prior-adjusted test.
     *
     * @param test    The test to wrap; typically an {@link IndTestFisherZ}.
     * @param priors  P-value weights, keyed by variable name. Resolved against
     *                {@code test.getVariables()} here, once, so the caller's matrix ordering is
     *                irrelevant. Build this once per locus and share it across subsample repeats:
     *                the GRW guarantee requires the weights to be fixed across the family of
     *                tests, so they must not be rebuilt or renormalised per repeat.
     * @param alpha   The base significance level. Each pair is judged against w_xy * alpha.
     * @throws IllegalStateException    If {@code priors} does not hold weights.
     * @throws IllegalArgumentException If {@code priors} mentions variables the test does not
     *                                  have, or alpha is out of range.
     */
    public EdgePriorTest(IndependenceTest test, EdgePriors priors, double alpha) {
        this.test = Objects.requireNonNull(test, "test");
        Objects.requireNonNull(priors, "priors");

        if (priors.getSemantics() != EdgePriors.Semantics.WEIGHTS) {
            throw new IllegalStateException("EdgePriorTest takes p-value weights, but was given "
                    + priors.getSemantics() + ". Convert with"
                    + " toWeightsViaBicBridge(...), or supply weights"
                    + " directly and call normalizedToMeanOne().");
        }

        if (!(alpha > 0.0 && alpha < 1.0)) {
            throw new IllegalArgumentException("alpha must be in (0, 1): " + alpha);
        }

        List<Node> variables = test.getVariables();
        List<String> unmatched = priors.unmatchedNames(variables);

        if (!unmatched.isEmpty()) {
            throw new IllegalArgumentException("The prior mentions variables the test does not have: "
                    + unmatched + ". If the prior was built for a larger"
                    + " variable set on purpose -- for instance one store"
                    + " per locus, applied to each subsample repeat --"
                    + " narrow it first with priors.restrictTo(test"
                    + ".getVariables()). Otherwise the prior was probably"
                    + " built against the wrong variable set.");
        }

        this.weights = priors.resolve(variables);
        this.alpha = alpha;
        this.indices = new LinkedHashMap<>();

        for (int i = 0; i < variables.size(); i++) {
            this.indices.put(variables.get(i), i);
        }
    }

    /**
     * Checks the independence of x and y given z, judging the weighted p-value against the base
     * alpha.
     *
     * @param x The first variable.
     * @param y The second variable.
     * @param z The conditioning set.
     * @return The result, carrying the weighted p-value.
     * @throws InterruptedException If the process is interrupted.
     */
    @Override
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> z) throws InterruptedException {
        double w = weight(x, y);
        IndependenceResult result = this.test.checkIndependence(x, y, z);
        double p = result.getPValue();

        // w = 0 forbids the edge outright: independence is declared unconditionally.
        if (w == 0.0) {
            IndependenceFact fact = new IndependenceFact(x, y, z);
            return new IndependenceResult(fact, true, 1.0, this.alpha - 1.0);
        }

        double pTilde = p / w;

        if (pTilde > 1.0) {
            pTilde = 1.0;
        }

        boolean independent = pTilde > this.alpha;

        if (this.verbose && independent) {
            System.out.println(x + " _||_ " + y + " | " + z + " p_tilde = " + pTilde
                    + " (p = " + p + ", w = " + w + ")");
        }

        IndependenceFact fact = new IndependenceFact(x, y, z);
        return new IndependenceResult(fact, independent, pTilde, this.alpha - pTilde);
    }

    /**
     * Returns the weight for the unordered pair {x, y}.
     *
     * @param x One variable.
     * @param y The other.
     * @return The weight.
     */
    public double weight(Node x, Node y) {
        Integer i = this.indices.get(x);
        Integer j = this.indices.get(y);

        if (i == null) {
            throw new IllegalArgumentException("Not a variable of this test: " + x);
        }

        if (j == null) {
            throw new IllegalArgumentException("Not a variable of this test: " + y);
        }

        return this.weights[i][j];
    }

    /**
     * Returns the effective significance level for the unordered pair {x, y}, namely
     * w_xy * alpha, capped at 1.
     *
     * @param x One variable.
     * @param y The other.
     * @return The effective level.
     */
    public double getAlpha(Node x, Node y) {
        return Math.min(1.0, weight(x, y) * this.alpha);
    }

    /**
     * Returns the variables of the test.
     *
     * @return This list.
     */
    @Override
    public List<Node> getVariables() {
        return this.test.getVariables();
    }

    /**
     * Returns the data model of the test.
     *
     * @return This data model.
     */
    @Override
    public DataModel getData() {
        return this.test.getData();
    }

    /**
     * Returns true if this test prints verbose output.
     *
     * @return True if so.
     */
    @Override
    public boolean isVerbose() {
        return this.verbose;
    }

    /**
     * Sets whether this test prints verbose output.
     *
     * @param verbose True if so.
     */
    @Override
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
        this.test.setVerbose(verbose);
    }

    /**
     * Returns the base significance level, before per-edge weighting.
     *
     * @return This level.
     */
    @Override
    public double getAlpha() {
        return this.alpha;
    }

    /**
     * Not supported. The base alpha is fixed at construction, since the weights were calibrated
     * against it; changing it afterwards would silently rescale every per-edge threshold.
     *
     * @param alpha Ignored.
     */
    @Override
    public void setAlpha(double alpha) {
        throw new UnsupportedOperationException("alpha is fixed at construction for EdgePriorTest,"
                + " because the weights are calibrated against it."
                + " Construct a new test instead.");
    }

    /**
     * Returns the covariance matrix of the wrapped test.
     *
     * @return This matrix.
     */
    @Override
    public ICovarianceMatrix getCov() {
        return this.test.getCov();
    }

    /**
     * Returns the datasets of the wrapped test.
     *
     * @return These datasets.
     */
    @Override
    public List<DataSet> getDataSets() {
        return this.test.getDataSets();
    }

    /**
     * Returns true if y is determined by z, per the wrapped test.
     *
     * @param z The conditioning set.
     * @param y The node.
     * @return True if so.
     */
    @Override
    public boolean determines(Set<Node> z, Node y) {
        return this.test.determines(z, y);
    }

    /**
     * Returns the sample size of the wrapped test.
     *
     * @return This size.
     */
    @Override
    public int getSampleSize() {
        return this.test.getSampleSize();
    }

    /**
     * Returns the wrapped test.
     *
     * @return The delegate.
     */
    public IndependenceTest getWrappedTest() {
        return this.test;
    }

    /**
     * Returns a string representation of this test.
     *
     * @return This string.
     */
    public String toString() {
        return "EdgePriorTest(" + this.test + ", alpha = " + this.alpha + ")";
    }
}
