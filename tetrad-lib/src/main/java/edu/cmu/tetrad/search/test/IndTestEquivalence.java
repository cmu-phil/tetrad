/// ////////////////////////////////////////////////////////////////////////////
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
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.math3.distribution.NormalDistribution;

import java.util.List;
import java.util.Set;

/**
 * Reports independence only on positive evidence that a partial correlation is negligible, by a
 * two-one-sided-test (TOST) equivalence test on the Fisher z scale.
 *
 * <p>An ordinary test answers "can I reject rho = 0?", and a constraint-based adjacency search
 * deletes an edge when it cannot. That treats absence of evidence as evidence of absence, and
 * the damage concentrates on the weakest real associations, because the ordinary removal region
 * |z| &lt; c*se <i>shrinks</i> with the sample size: a weak-but-real edge is deleted most eagerly
 * at exactly the sample sizes where deleting it is least justified. Worse, a search tries many
 * conditioning subsets per pair and removes the edge if <i>any</i> of them fails to reject, so
 * every extra subset is another chance to delete a real adjacency.
 *
 * <p>This test answers the question edge removal actually needs - "can I show |rho| is below a
 * margin?" - declaring independence just in case the (1 - 2*alpha) confidence interval for the
 * Fisher z of the partial correlation lies entirely inside (-z0, +z0), where z0 = atanh(margin):
 *
 * <pre>    |zhat| + c * se &lt; z0,     se = 1/sqrt(n - 3 - |Z|),  c = Phi^-1(1 - alpha)</pre>
 *
 * <p>The removal region now <i>grows</i> with n, toward |rho| &lt; margin, so nothing is deleted
 * until the data can show it is negligible. Measured on bivariate normal data at margin 0.10 and
 * alpha 0.05, the rate of declaring independence at rho = 0.10 - the boundary of this test's null
 * - sits at 0.052, 0.045, 0.055 and 0.048 for n = 500, 2000, 10000 and 50000, while at rho = 0.05
 * the ordinary Fisher Z test declares independence 81% of the time at n = 500 against this test's
 * 23%.
 *
 * <p><b>Choosing the margin.</b> It is squeezed from both sides. It must exceed
 * {@link #smallestUsableMargin(int, int)}, or no pair is ever removable and the search returns a
 * complete graph. It must also stay below the weakest association worth keeping, since
 * asymptotically every pair whose partial correlation falls below the margin <i>is</i> removed.
 * Only the first bound is knowable from the data, so {@link #autoMargin} sets the margin as a
 * multiple of it; a multiplier near 2 was a good default in testing on cyclic linear-Gaussian
 * models, and multipliers below about 1.6 traded away too much adjacency precision.
 *
 * <p>Intended for the adjacency phase of a constraint-based search. It is not a drop-in
 * replacement for a null-hypothesis test in an orientation rule, where a rule asking whether two
 * variables are <i>dependent</i> needs the ordinary meaning of that word rather than "not shown to
 * be negligible"; see {@link edu.cmu.tetrad.search.Ccd#setAdjacencyTest(IndependenceTest)}, which
 * exists so that the two phases can use different tests.
 *
 * @author josephramsey
 * @see IndTestFisherZ
 */
public final class IndTestEquivalence implements IndependenceTest {

    /**
     * Standard normal, for the critical value and the equivalence p-value.
     */
    private static final NormalDistribution NORMAL = new NormalDistribution(0, 1);

    /**
     * The underlying test, which supplies the partial correlation and its degrees of freedom.
     */
    private final IndTestFisherZ fisherZ;

    /**
     * The largest |partial correlation| counted as negligible.
     */
    private final double margin;

    /**
     * The one-sided level of each of the two tests.
     */
    private double alpha;

    /**
     * The critical value for the current alpha, cached.
     */
    private double critical;

    /**
     * Verbose logging toggle.
     */
    private boolean verbose;

    /**
     * Constructs an equivalence test over the given Fisher Z test.
     *
     * @param fisherZ the underlying Fisher Z test; must not be null.
     * @param margin  the largest |partial correlation| counted as negligible; must be in (0, 1).
     * @param alpha   the one-sided level of each of the two tests; must be in (0, 0.5).
     * @throws NullPointerException     if fisherZ is null.
     * @throws IllegalArgumentException if margin or alpha is out of range.
     */
    public IndTestEquivalence(IndTestFisherZ fisherZ, double margin, double alpha) {
        if (fisherZ == null) throw new NullPointerException("fisherZ must not be null");
        if (margin <= 0 || margin >= 1) {
            throw new IllegalArgumentException("margin must be in (0, 1): " + margin);
        }

        this.fisherZ = fisherZ;
        this.margin = margin;
        setAlpha(alpha);
    }

    /**
     * An equivalence test whose margin is scaled to the sample size, as a multiple of the smallest
     * margin that sample size could certify at all.
     *
     * <p>This collapses two knobs into one. The right margin depends on n, and only its lower
     * bound - {@link #smallestUsableMargin(int, int)} - is knowable from the data; this sets the
     * margin just above that bound. On the cyclic linear-Gaussian models tested, the best
     * hand-picked margin at each sample size (0.10 at n = 500, 0.05 at n = 2000, 0.01 at
     * n = 50000) was close to 1.35 times that bound, so the reparameterisation is faithful; a
     * multiplier near 2 gave the better accuracy trade-off overall.
     *
     * @param fisherZ    the underlying Fisher Z test.
     * @param multiplier how far above the smallest certifiable margin to sit; must exceed 1.
     * @param alpha      the one-sided level of each of the two tests; must be in (0, 0.5).
     * @param sampleSize the sample size to calibrate the margin to; must exceed 4.
     * @return an equivalence test with the scaled margin.
     * @throws IllegalArgumentException if any argument is out of range.
     */
    public static IndTestEquivalence autoMargin(IndTestFisherZ fisherZ, double multiplier,
                                                double alpha, int sampleSize) {
        if (multiplier <= 1) {
            throw new IllegalArgumentException("multiplier must exceed 1: " + multiplier);
        }
        if (sampleSize <= 4) {
            throw new IllegalArgumentException("sampleSize must exceed 4: " + sampleSize);
        }

        double c = NORMAL.inverseCumulativeProbability(1 - alpha);
        double floor = Math.tanh(c / Math.sqrt(sampleSize - 3.0));
        return new IndTestEquivalence(fisherZ, Math.min(0.99, multiplier * floor), alpha);
    }

    /**
     * Checks whether the partial correlation of x and y given z is small enough to call
     * negligible, by the TOST criterion described in the class comment.
     *
     * <p>A result is marked invalid, and not independent, when the partial correlation cannot be
     * computed or the degrees of freedom are nonpositive.
     *
     * @param x the first variable.
     * @param y the second variable.
     * @param z the conditioning set.
     * @return the result, whose p-value is that of the equivalence test (the larger of the two
     * one-sided p-values) and whose score is the margin's slack on the Fisher z scale.
     */
    @Override
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> z) {
        IndependenceFact fact = new IndependenceFact(x, y, z);

        IndTestFisherZ.Result result;
        try {
            result = this.fisherZ.getResult(x, y, z);
        } catch (Exception e) {
            return new IndependenceResult(fact, false, Double.NaN, Double.NaN, false);
        }

        double df = result.df();
        if (df < 1 || Double.isNaN(result.r())) {
            return new IndependenceResult(fact, false, Double.NaN, Double.NaN, false);
        }

        double r = Math.min(1 - 1e-12, Math.abs(result.r()));
        double zHat = 0.5 * (Math.log1p(r) - Math.log1p(-r));
        double se = 1.0 / Math.sqrt(df);
        double z0 = 0.5 * (Math.log1p(this.margin) - Math.log1p(-this.margin));

        double slack = z0 - zHat;
        boolean equivalent = slack > this.critical * se;
        double p = 1 - NORMAL.cumulativeProbability(slack / se);

        if (this.verbose) {
            TetradLogger.getInstance().log(fact
                    + (equivalent ? " negligible" : " not shown negligible")
                    + " (|r|=" + r + ", margin=" + this.margin + ", p=" + p + ")");
        }

        return new IndependenceResult(fact, equivalent, p, slack, true);
    }

    /**
     * The smallest margin this sample size could certify at all, at the current alpha. A margin at
     * or below this value makes every pair unremovable, so a search using it returns a complete
     * graph.
     *
     * @param sampleSize       the sample size.
     * @param conditioningSize the size of the conditioning set.
     * @return the smallest usable margin, on the correlation scale, or NaN if the degrees of
     * freedom are nonpositive.
     */
    public double smallestUsableMargin(int sampleSize, int conditioningSize) {
        double df = sampleSize - 3.0 - conditioningSize;
        if (df < 1) return Double.NaN;
        return Math.tanh(this.critical / Math.sqrt(df));
    }

    /**
     * Returns the largest |partial correlation| this test counts as negligible.
     *
     * @return the margin.
     */
    public double getMargin() {
        return this.margin;
    }

    /**
     * Returns the underlying Fisher Z test.
     *
     * @return the underlying test.
     */
    public IndTestFisherZ getFisherZ() {
        return this.fisherZ;
    }

    /**
     * Returns the variables over which this test is defined.
     *
     * @return the variables of the underlying test.
     */
    @Override
    public List<Node> getVariables() {
        return this.fisherZ.getVariables();
    }

    /**
     * Returns the data this test reads.
     *
     * @return the data model of the underlying test.
     */
    @Override
    public DataModel getData() {
        return this.fisherZ.getData();
    }

    /**
     * Returns whether verbose output is enabled.
     *
     * @return true if verbose.
     */
    @Override
    public boolean isVerbose() {
        return this.verbose;
    }

    /**
     * Sets whether verbose output is enabled, for this test and the underlying one.
     *
     * @param verbose true for verbose output.
     */
    @Override
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
        this.fisherZ.setVerbose(verbose);
    }

    /**
     * Returns the one-sided level of each of the two tests.
     *
     * @return alpha.
     */
    @Override
    public double getAlpha() {
        return this.alpha;
    }

    /**
     * Sets the one-sided level of each of the two tests. Note that alpha here bounds the rate of
     * wrongly calling a non-negligible association negligible, which is the opposite of what alpha
     * bounds in an ordinary test.
     *
     * @param alpha the level; must be in (0, 0.5).
     * @throws IllegalArgumentException if alpha is out of range.
     */
    @Override
    public void setAlpha(double alpha) {
        if (alpha <= 0 || alpha >= 0.5) {
            throw new IllegalArgumentException("alpha must be in (0, 0.5): " + alpha);
        }
        this.alpha = alpha;
        this.critical = NORMAL.inverseCumulativeProbability(1 - alpha);
    }

    /**
     * Returns a description of this test.
     *
     * @return the description.
     */
    @Override
    public String toString() {
        return "Equivalence (TOST) test on Fisher z, margin = " + this.margin
                + ", alpha = " + this.alpha;
    }
}
