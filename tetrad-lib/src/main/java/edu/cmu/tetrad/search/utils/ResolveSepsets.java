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

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.test.IndTestChiSquare;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.util.ProbUtils;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TMath;

import java.util.*;

/**
 * <p>Provides some utilities for resolving inconsistencies that arise
 * between sepsets learned for overlapping datasets. This occurs frequently when using the DCI and ION algorithm. A
 * reference is here:</p>
 *
 * <p>Tillman, R. E., &amp; Eberhardt, F. (2014). Learning causal structure from
 * multiple datasets with similar variable sets. Behaviormetrika, 41(1), 41-64.</p>
 *
 * @author roberttillman
 * @version $Id: $Id
 */
public final class ResolveSepsets {

    /**
     * The method to use for resolving sepsets
     */
    public ResolveSepsets() {
    }

    /**
     * Tests for independence using one of the pooled methods
     *
     * @param method            a {@link edu.cmu.tetrad.search.utils.ResolveSepsets.Method} object
     * @param independenceTests a {@link java.util.List} object
     * @param x                 a {@link edu.cmu.tetrad.graph.Node} object
     * @param y                 a {@link edu.cmu.tetrad.graph.Node} object
     * @param condSet           a {@link java.util.Set} object
     * @return a boolean
     * @throws java.lang.InterruptedException if any.
     */
    public static boolean isIndependentPooled(Method method, List<IndependenceTest> independenceTests,
                                              Node x, Node y, Set<Node> condSet) throws InterruptedException {
        // The vote-style methods have no p-value; dispatch them directly. Everything else compares the combined
        // p-value to alpha. (Earlier code routed every non-Fisher method through getPValuePooled, which routed
        // back here: a StackOverflowError for stouffer, mudholkergeorge, average, majority, fdr, and random.)
        switch (method) {
            case majority:
                return isIndependentMajorityIndep(independenceTests, x, y, condSet);
            case fdr:
                return isIndependentMajorityFdr(independenceTests, x, y, condSet);
            case random:
                return isIndependentPooledRandom(independenceTests, x, y, condSet);
            case averagetest:
                return isIndependentPooledAverageTest(independenceTests, x, y, condSet);
            default:
                double p = getPValuePooled(method, independenceTests, x, y, condSet);
                double alpha = independenceTests.getFirst().getAlpha();
                return p > alpha;
        }
    }

    /**
     * Tests for independence using one of the pooled methods and returns the combined p-value, on the scale where
     * "independent iff p &gt; alpha" is the correct decision for every method. Each is a valid one-sided combination
     * of the per-data-set p-values under the null that the independence holds in every data set (small p-values are
     * evidence of dependence):
     * <ul>
     * <li>fisher, fisher2: Fisher's chi-square on -2 sum log p (fisher2 skips NaN p-values).</li>
     * <li>tippett: min p, Sidak-adjusted: 1 - (1 - min p)^k.</li>
     * <li>stouffer: Phi(sum Phi^{-1}(p_i) / sqrt k), one-sided.</li>
     * <li>mudholkergeorge, mudholkergeorge2: the logit combination, one-sided t.</li>
     * <li>worsleyfriston: (max p)^k - a test of dependence in EVERY data set (conjunction), not in the shared
     * structure; included for comparison.</li>
     * <li>average: the mean p-value, referred to its exact null (Irwin-Hall) distribution.</li>
     * </ul>
     * The vote-style methods (majority, fdr, random, averagetest) carry no p-value; for them 1.0/0.0 is returned
     * according to the vote.
     *
     * @param method            a {@link edu.cmu.tetrad.search.utils.ResolveSepsets.Method} object
     * @param independenceTests a {@link java.util.List} object
     * @param x                 a {@link edu.cmu.tetrad.graph.Node} object
     * @param y                 a {@link edu.cmu.tetrad.graph.Node} object
     * @param condSet           a {@link java.util.Set} object
     * @return the pooled p-value.
     * @throws java.lang.InterruptedException if any.
     */
    public static double getPValuePooled(Method method, List<IndependenceTest> independenceTests,
                                         Node x, Node y, Set<Node> condSet) throws InterruptedException {
        switch (method) {
            case fisher:
                return getPValuePooledFisher(independenceTests, x, y, condSet);
            case fisher2:
                return getPValuePooledFisher2(independenceTests, x, y, condSet);
            case tippett: {
                List<Double> ps = getAvailablePValues(independenceTests, x, y, condSet);
                if (ps.isEmpty()) return 0.0;
                double min = 1.0;
                for (double pv : ps) min = TMath.min(min, pv);
                return 1.0 - TMath.pow(1.0 - min, ps.size());
            }
            case stouffer: {
                List<Double> ps = getAvailablePValues(independenceTests, x, y, condSet);
                if (ps.isEmpty()) return 0.0;
                double z = 0.0;
                for (double pv : ps) z += ProbUtils.normalQuantile(clampP(pv));
                z /= TMath.sqrt(ps.size());
                return RandomUtil.getInstance().normalCdf(0, 1, z);
            }
            case mudholkergeorge:
            case mudholkergeorge2: {
                List<Double> ps = getAvailablePValues(independenceTests, x, y, condSet);
                if (ps.isEmpty()) return 0.0;
                int k = ps.size();
                double c = TMath.sqrt(3.0 * (5 * k + 4) / (k * TMath.pow(TMath.PI, 2) * (5 * k + 2)));
                double t = 0.0;
                for (double pv : ps) {
                    double q = clampP(pv);
                    t += -c * TMath.log(q / (1 - q));
                }
                // Large t (small p's) is evidence of dependence; one-sided upper tail.
                return 1.0 - ProbUtils.tCdf(t, 5 * k + 4);
            }
            case worsleyfriston: {
                List<Double> ps = getAvailablePValues(independenceTests, x, y, condSet);
                if (ps.isEmpty()) return 0.0;
                double max = 0.0;
                for (double pv : ps) max = TMath.max(max, pv);
                return TMath.pow(max, ps.size());
            }
            case average: {
                List<Double> ps = getAvailablePValues(independenceTests, x, y, condSet);
                if (ps.isEmpty()) return 0.0;
                double sum = 0.0;
                for (double pv : ps) sum += pv;
                return irwinHallCdf(sum, ps.size());
            }
            default:
                return isIndependentPooled(method, independenceTests, x, y, condSet) ? 1.0 : 0.0;
        }
    }

    /**
     * Guards Fisher's sum against log(0) only. A p-value of exactly 0 (the normal or chi-square tail underflowed) is
     * the strongest possible evidence of dependence and should dominate the sum; the previous floor of 1e-8 capped
     * every data set's contribution at -2 log(1e-8) = 36.8, which distorted combined p-values whenever any
     * component p-value was below about 1e-7. With the floor at 1e-300 the largest contribution is 1381, finite but
     * decisive. NaN (a component test that could not be computed) is treated as uninformative, p = 0.5.
     */
    private static double floorP(double p) {
        if (Double.isNaN(p)) return 0.5;
        return TMath.max(1e-300, p);
    }

    private static double clampP(double p) {
        if (Double.isNaN(p)) return 0.5;
        return TMath.min(1 - 1e-12, TMath.max(1e-12, p));
    }

    /**
     * P(S &lt;= s) for S the sum of k independent Uniform(0,1) variables (Irwin-Hall); small sums are evidence of
     * dependence, so this is the one-sided p-value of the mean-p combination.
     */
    private static double irwinHallCdf(double s, int k) {
        if (s <= 0) return 0.0;
        if (s >= k) return 1.0;
        double sum = 0.0;
        double fact = 1.0;
        for (int j = 1; j <= k; j++) fact *= j;
        for (int j = 0; j <= (int) TMath.floor(s); j++) {
            double binom = 1.0;
            for (int i = 1; i <= j; i++) binom = binom * (k - i + 1) / i;
            sum += (j % 2 == 0 ? 1 : -1) * binom * TMath.pow(s - j, k);
        }
        return TMath.max(0.0, TMath.min(1.0, sum / fact));
    }

    /**
     * Checks independence from pooled samples using Fisher's method.
     * <p>
     * See R. A. Fisher. Statistical Methods for Research Workers. Oliver and Boyd, 11th edition, 1950.
     *
     * @param independenceTests a {@link java.util.List} object
     * @param x                 a {@link edu.cmu.tetrad.graph.Node} object
     * @param y                 a {@link edu.cmu.tetrad.graph.Node} object
     * @param condSet           a {@link java.util.Set} object
     * @return a boolean
     * @throws java.lang.InterruptedException if any.
     */
    public static boolean isIndependentPooledFisher(List<IndependenceTest> independenceTests, Node x, Node y,
                                                    Set<Node> condSet) throws InterruptedException {
        double alpha = independenceTests.getFirst().getAlpha();
        double p = getPValuePooledFisher(independenceTests, x, y, condSet);
        return (p > alpha);
    }

    /**
     * Returns the pooled p-value using Fisher's method.
     *
     * @param independenceTests a {@link java.util.List} object
     * @param x                 a {@link edu.cmu.tetrad.graph.Node} object
     * @param y                 a {@link edu.cmu.tetrad.graph.Node} object
     * @param condSet           a {@link java.util.Set} object
     * @return the pooled p-value.
     * @throws java.lang.InterruptedException if any.
     */
    public static double getPValuePooledFisher(List<IndependenceTest> independenceTests, Node x, Node y,
                                               Set<Node> condSet) throws InterruptedException {
        double tf = 0.0;
        int numTests = 0;
        for (IndependenceTest independenceTest : independenceTests) {
            if (ResolveSepsets.missingVariable(x, y, condSet, independenceTest)) continue;
            Set<Node> localCondSet = new HashSet<>();
            for (Node node : condSet) {
                localCondSet.add(independenceTest.getVariable(node.getName()));
            }
            IndependenceResult result = independenceTest.checkIndependence(independenceTest.getVariable(x.getName()), independenceTest.getVariable(y.getName()), localCondSet);
            tf += -2.0 * TMath.log(floorP(result.getPValue()));
            numTests++;
        }

        if (numTests == 0) {
            return 0.0;
        }

        return 1.0 - ProbUtils.chisqCdf(tf, 2 * numTests);
    }

    /**
     * Eliminates from considerations independence tests that cannot be evaluated (due to missing variables mainly).
     *
     * @param independenceTests a {@link java.util.List} object
     * @param x                 a {@link edu.cmu.tetrad.graph.Node} object
     * @param y                 a {@link edu.cmu.tetrad.graph.Node} object
     * @param condSet           a {@link java.util.Set} object
     * @return a boolean
     */
    public static boolean isIndependentPooledFisher2(List<IndependenceTest> independenceTests, Node x, Node y,
                                                     Set<Node> condSet) {
        double alpha = independenceTests.iterator().next().getAlpha();
        double p = getPValuePooledFisher2(independenceTests, x, y, condSet);
        return (p > alpha);
    }

    /**
     * Returns the pooled p-value using Fisher's method 2.
     *
     * @param independenceTests a {@link java.util.List} object
     * @param x                 a {@link edu.cmu.tetrad.graph.Node} object
     * @param y                 a {@link edu.cmu.tetrad.graph.Node} object
     * @param condSet           a {@link java.util.Set} object
     * @return the pooled p-value.
     */
    public static double getPValuePooledFisher2(List<IndependenceTest> independenceTests, Node x, Node y,
                                                Set<Node> condSet) {
        List<Double> pValues = ResolveSepsets.getAvailablePValues(independenceTests, x, y, condSet);

        double tf = 0.0;
        int numPValues = 0;

        for (double p : pValues) {
            tf += -2.0 * TMath.log(floorP(p));
            numPValues++;
        }

        return 1.0 - ProbUtils.chisqCdf(tf, 2 * numPValues);
    }

    /**
     * Checks independence from pooled samples using Tippett's method
     * <p>
     * See L. H. C. Tippett. The Method of Statistics. Williams and Norgate, 1st edition, 1950.
     *
     * @param independenceTests a {@link java.util.List} object
     * @param x                 a {@link edu.cmu.tetrad.graph.Node} object
     * @param y                 a {@link edu.cmu.tetrad.graph.Node} object
     * @param condSet           a {@link java.util.Set} object
     * @return a boolean
     * @throws java.lang.InterruptedException if any.
     */
    public static boolean isIndependentPooledTippett(List<IndependenceTest> independenceTests, Node x, Node y,
                                                     Set<Node> condSet) throws InterruptedException {
        double alpha = independenceTests.iterator().next().getAlpha();
        double p = getPValuePooledTippett(independenceTests, x, y, condSet);
        return (p > (1 - TMath.pow(1 - alpha, (1 / (double) independenceTests.size()))));
    }

    /**
     * Returns the pooled p-value using Tippett's method.
     *
     * @param independenceTests a {@link java.util.List} object
     * @param x                 a {@link edu.cmu.tetrad.graph.Node} object
     * @param y                 a {@link edu.cmu.tetrad.graph.Node} object
     * @param condSet           a {@link java.util.Set} object
     * @return the pooled p-value.
     * @throws java.lang.InterruptedException if any.
     */
    public static double getPValuePooledTippett(List<IndependenceTest> independenceTests, Node x, Node y,
                                                Set<Node> condSet) throws InterruptedException {
        double p = -1.0;
        for (IndependenceTest independenceTest : independenceTests) {
            if (ResolveSepsets.missingVariable(x, y, condSet, independenceTest)) continue;
            Set<Node> localCondSet = new HashSet<>();
            for (Node node : condSet) {
                localCondSet.add(independenceTest.getVariable(node.getName()));
            }
            IndependenceResult result = independenceTest.checkIndependence(
                    independenceTest.getVariable(x.getName()),
                    independenceTest.getVariable(y.getName()), localCondSet);
            if (Double.isNaN(result.getPValue())) {
                continue;
            }
            double newp = result.getPValue();
            if (p == -1.0 || newp < p) {
                p = newp;
            }
        }
        return p;
    }

    /**
     * Checks independence from pooled samples using Wilkinson's method
     * <p>
     * I don't have a reference for this but its basically in between Tippett and Worsley and Friston.
     *
     * @param independenceTests a {@link java.util.List} object
     * @param x                 a {@link edu.cmu.tetrad.graph.Node} object
     * @param y                 a {@link edu.cmu.tetrad.graph.Node} object
     * @param condSet           a {@link java.util.Set} object
     * @param r                 a int
     * @return a boolean
     * @throws java.lang.InterruptedException if any.
     */
    public static boolean isIndependentPooledWilkinson(List<IndependenceTest> independenceTests, Node x, Node y,
                                                       Set<Node> condSet, int r) throws InterruptedException {
        double alpha = independenceTests.get(0).getAlpha();
        double[] p = new double[independenceTests.size()];
        int k = 0;
        for (IndependenceTest independenceTest : independenceTests) {
            IndependenceResult result = independenceTest.checkIndependence(x, y, condSet);
            p[k] = result.getPValue();
            k++;
        }
        java.util.Arrays.sort(p);
        return (p[r] > (1 - TMath.pow(1 - TMath.pow(alpha, 1.0 / (double) r), (r / (double) independenceTests.size()))));
    }

    /**
     * Checks independence from pooled samples using Worsley and Friston's method
     * <p>
     * See K. J. Worsely and K. J. Friston. A test for conjunction. Statistics and Probability Letters 2000.
     *
     * @param independenceTests a {@link java.util.List} object
     * @param x                 a {@link edu.cmu.tetrad.graph.Node} object
     * @param y                 a {@link edu.cmu.tetrad.graph.Node} object
     * @param condSet           a {@link java.util.Set} object
     * @return a boolean
     * @throws java.lang.InterruptedException if any.
     */
    public static boolean isIndependentPooledWorsleyFriston(List<IndependenceTest> independenceTests, Node x, Node y,
                                                            Set<Node> condSet) throws InterruptedException {
        double alpha = independenceTests.iterator().next().getAlpha();
        double p = -1.0;
        for (IndependenceTest independenceTest : independenceTests) {
            Set<Node> localCondSet = new HashSet<>();
            if (ResolveSepsets.missingVariable(x, y, condSet, independenceTest)) continue;
            for (Node node : condSet) {
                localCondSet.add(independenceTest.getVariable(node.getName()));
            }
            IndependenceResult result = independenceTest.checkIndependence(independenceTest.getVariable(x.getName()), independenceTest.getVariable(y.getName()), localCondSet);
            if (Double.isNaN(result.getPValue())) {
//                p = result.getPValue();
                continue;
            }
            double newp = result.getPValue();
            if (newp > p) {
                p = newp;
            }
        }
        return (p > TMath.pow(alpha, (1 / (double) independenceTests.size())));
    }

    /**
     * Checks independence from pooled samples using Stouffer et al.'s method
     * <p>
     * See S. A. Stouffer, E. A. Suchman, L. C. Devinney, S. A. Star, and R. M. Williams. The American Soldier: Vol. 1.
     * Adjustment During Army Life. Princeton University Press, 1949.
     *
     * @param independenceTests a {@link java.util.List} object
     * @param x                 a {@link edu.cmu.tetrad.graph.Node} object
     * @param y                 a {@link edu.cmu.tetrad.graph.Node} object
     * @param condSet           a {@link java.util.Set} object
     * @return a boolean
     * @throws java.lang.InterruptedException if any.
     */
    public static boolean isIndependentPooledStouffer(List<IndependenceTest> independenceTests, Node x, Node y,
                                                      Set<Node> condSet) throws InterruptedException {
        double alpha = independenceTests.iterator().next().getAlpha();
        double ts = 0.0;
        for (IndependenceTest independenceTest : independenceTests) {
            Set<Node> localCondSet = new HashSet<>();
            for (Node node : condSet) {
                localCondSet.add(independenceTest.getVariable(node.getName()));
            }
            IndependenceResult result = independenceTest.checkIndependence(independenceTest.getVariable(x.getName()),
                    independenceTest.getVariable(y.getName()), localCondSet);
            ts += ProbUtils.normalQuantile(result.getPValue()) / TMath.sqrt(independenceTests.size());
        }
        double p = 2.0 * (1.0 - RandomUtil.getInstance().normalCdf(0, 1, TMath.abs(ts)));
        return (p > alpha);
    }

    /**
     * Checks independence from pooled samples using Mudholker and George's method
     * <p>
     * See G. S. Mudholkar and E. O. George. The logit method for combining probabilities. In J. Rustagi, editor,
     * Symposium on Optimizing Method in Statistics, pages 345-366. Academic Press, 1979.
     *
     * @param independenceTests a {@link java.util.List} object
     * @param x                 a {@link edu.cmu.tetrad.graph.Node} object
     * @param y                 a {@link edu.cmu.tetrad.graph.Node} object
     * @param condSet           a {@link java.util.Set} object
     * @return a boolean
     * @throws java.lang.InterruptedException if any.
     */
    public static boolean isIndependentPooledMudholkerGeorge(List<IndependenceTest> independenceTests, Node x, Node y, Set<Node> condSet) throws InterruptedException {
        double alpha = independenceTests.iterator().next().getAlpha();
        double c = TMath.sqrt(3 * (5 * independenceTests.size() + 4) / (independenceTests.size() * TMath.pow(TMath.PI, 2) * (5 * independenceTests.size() + 2)));
        double tm = 0.0;
        for (IndependenceTest independenceTest : independenceTests) {
            Set<Node> localCondSet = new HashSet<>();
            for (Node node : condSet) {
                localCondSet.add(independenceTest.getVariable(node.getName()));
            }
            IndependenceResult result = independenceTest.checkIndependence(independenceTest.getVariable(x.getName()), independenceTest.getVariable(y.getName()), localCondSet);
            double pk = result.getPValue();
            if (pk != 0 && pk != 1) {
                tm += -c * TMath.log(pk / (1 - pk));
            }
        }
        double p = 2.0 * (1.0 - ProbUtils.tCdf(TMath.abs(tm), 5 * independenceTests.size() + 4));
        return (p > alpha);
    }

    /**
     * The same as isIndepenentPooledMudholkerGeoerge, except that only available independence tests are used.
     *
     * @param independenceTests a {@link java.util.List} object
     * @param x                 a {@link edu.cmu.tetrad.graph.Node} object
     * @param y                 a {@link edu.cmu.tetrad.graph.Node} object
     * @param condSet           a {@link java.util.Set} object
     * @return a boolean
     */
    public static boolean isIndependentPooledMudholkerGeorge2(List<IndependenceTest> independenceTests, Node x, Node y,
                                                              Set<Node> condSet) {
        double alpha = independenceTests.iterator().next().getAlpha();
        List<Double> pValues = ResolveSepsets.getAvailablePValues(independenceTests, x, y, condSet);
        double c = TMath.sqrt(3 * (5 * pValues.size() + 4) / (pValues.size() * TMath.pow(TMath.PI, 2) * (5 * pValues.size() + 2)));
        double tm = 0.0;
        for (double pk : pValues) {
            tm += -c * TMath.log(pk / (1 - pk));
        }
        double p = 2.0 * (1.0 - ProbUtils.tCdf(TMath.abs(tm), 5 * pValues.size() + 4));
        return (p > alpha);
    }

    /**
     * Checks independence from pooled samples by taking the average p value
     *
     * @param independenceTests a {@link java.util.List} object
     * @param x                 a {@link edu.cmu.tetrad.graph.Node} object
     * @param y                 a {@link edu.cmu.tetrad.graph.Node} object
     * @param condSet           a {@link java.util.Set} object
     * @return a boolean
     * @throws java.lang.InterruptedException if any.
     */
    public static boolean isIndependentPooledAverage(List<IndependenceTest> independenceTests, Node x, Node y,
                                                     Set<Node> condSet) throws InterruptedException {
        double alpha = independenceTests.iterator().next().getAlpha();
        double sum = 0.0;
        int numTests = 0;

        for (IndependenceTest independenceTest : independenceTests) {
            if (ResolveSepsets.missingVariable(x, y, condSet, independenceTest)) continue;

            Set<Node> localCondSet = new HashSet<>();
            for (Node node : condSet) {
                localCondSet.add(independenceTest.getVariable(node.getName()));
            }

            IndependenceResult result = independenceTest.checkIndependence(independenceTest.getVariable(x.getName()), independenceTest.getVariable(y.getName()), localCondSet);
            double p = result.getPValue();

            if (Double.isNaN(p)) continue;

            sum += p;
            numTests++;
        }


        return (sum / numTests > alpha);
    }

    private static boolean missingVariable(Node x, Node y, Set<Node> condSet, IndependenceTest independenceTest) {
        DataSet dataSet = (DataSet) independenceTest.getData();

        if (ResolveSepsets.isMissing(x, dataSet)) {
            return true;
        }

        if (ResolveSepsets.isMissing(y, dataSet)) {
            return true;
        }

        for (Node z : condSet) {
            if (ResolveSepsets.isMissing(z, dataSet)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isMissing(Node node, DataSet dataSet) {
        Node _node = dataSet.getVariable(node.getName());

        int col = dataSet.getColumnIndex(_node);

        for (int i = 0; i < dataSet.getNumRows(); i++) {
            if (Double.isNaN(dataSet.getDouble(i, col))) {
                return true;
            }
        }

        return false;
    }

    /**
     * Checks independence from pooled samples by taking the average test statistic CURRENTLY ONLY WORKS FOR CHISQUARE
     * TEST
     *
     * @param independenceTests a {@link java.util.List} object
     * @param x                 a {@link edu.cmu.tetrad.graph.Node} object
     * @param y                 a {@link edu.cmu.tetrad.graph.Node} object
     * @param condSet           a {@link java.util.Set} object
     * @return a boolean
     * @throws java.lang.InterruptedException if any.
     */
    public static boolean isIndependentPooledAverageTest(List<IndependenceTest> independenceTests, Node x, Node y,
                                                         Set<Node> condSet) throws InterruptedException {
        double alpha = independenceTests.iterator().next().getAlpha();
        double ts = 0.0;
        int df = 0;
        for (IndependenceTest independenceTest : independenceTests) {
            if (!(independenceTest instanceof IndTestChiSquare)) {
                throw new RuntimeException("Must be ChiSquare Test");
            }
            Set<Node> localCondSet = new HashSet<>();
            for (Node node : condSet) {
                localCondSet.add(independenceTest.getVariable(node.getName()));
            }
            independenceTest.checkIndependence(independenceTest.getVariable(x.getName()), independenceTest.getVariable(y.getName()), localCondSet);
            ts += ((IndTestChiSquare) independenceTest).getChiSquare() / independenceTests.size();
            df += ((IndTestChiSquare) independenceTest).getDf();
        }
        df = df / independenceTests.size();
        double p = 1.0 - ProbUtils.chisqCdf(ts, df);
        return (p > alpha);
    }

    /**
     * Checks independence from pooled samples by randomly selecting a p value
     *
     * @param independenceTests a {@link java.util.List} object
     * @param x                 a {@link edu.cmu.tetrad.graph.Node} object
     * @param y                 a {@link edu.cmu.tetrad.graph.Node} object
     * @param condSet           a {@link java.util.Set} object
     * @return a boolean
     * @throws java.lang.InterruptedException if any.
     */
    public static boolean isIndependentPooledRandom(List<IndependenceTest> independenceTests, Node x, Node y,
                                                    Set<Node> condSet) throws InterruptedException {
        List<IndependenceTest> _tests = new ArrayList<>(independenceTests);

        double alpha = independenceTests.iterator().next().getAlpha();
        int r = RandomUtil.getInstance().nextInt(independenceTests.size());
        IndependenceTest independenceTest = _tests.get(r);
        Set<Node> localCondSet = new HashSet<>();
        for (Node node : condSet) {
            localCondSet.add(independenceTest.getVariable(node.getName()));
        }
        IndependenceResult result = independenceTest.checkIndependence(independenceTest.getVariable(x.getName()), independenceTest.getVariable(y.getName()), localCondSet);
        double p = result.getPValue();
        return (p > alpha);
    }

    /**
     * Judges x to be independent of y conditional on condSet if the false discovery rate of the p values for the
     * separate judgements for their collective alpha level identifies no more than # p values / 2 values below
     * threshold.
     */
    private static boolean isIndependentMajorityFdr(List<IndependenceTest> independenceTests, Node x, Node y,
                                                    Set<Node> condSet) {
        List<Double> allPValues = ResolveSepsets.getAvailablePValues(independenceTests, x, y, condSet);

        Collections.sort(allPValues);
        int c = 0;
        while (c < allPValues.size() &&
               allPValues.get(c) < independenceTests.iterator().next().getAlpha() * (c + 1.) / allPValues.size()) {
            c++;
        }


        // At least half of the judgments are for independence.
        boolean independent = c < allPValues.size() / 2;

        if (independent) {
            String message = "***FDR judges " + LogUtilsSearch.independenceFact(x, y, condSet) + " independent";
            TetradLogger.getInstance().log(message);
        } else {
            String message = "###FDR judges " + LogUtilsSearch.independenceFact(x, y, condSet) + " dependent";
            TetradLogger.getInstance().log(message);
        }
        TetradLogger.getInstance().log("c = " + c);

        return independent;
    }

    private static List<Double> getAvailablePValues(List<IndependenceTest> independenceTests, Node x, Node y,
                                                    Set<Node> condSet) {
        List<Double> allPValues = new ArrayList<>();

        for (IndependenceTest test : independenceTests) {
            if (ResolveSepsets.missingVariable(x, y, condSet, test)) continue;
            Set<Node> localCondSet = new HashSet<>();
            for (Node node : condSet) {
                localCondSet.add(test.getVariable(node.getName()));
            }

            try {
                IndependenceResult result = test.checkIndependence(test.getVariable(x.getName()), test.getVariable(y.getName()), localCondSet);
                allPValues.add(result.getPValue());
            } catch (Exception e) {
                // Skip that test.
            }
        }

        return allPValues;
    }

    /**
     * Judges x to be independent of y conditional on condSet if the false discovery rate of the p values for the
     * separate judgements for their collective alpha level identifies no more than # p values / 2 values below
     * threshold.
     */
    private static boolean isIndependentMajorityIndep(List<IndependenceTest> independenceTests, Node x, Node y, Set<Node> condSet) {
        List<Double> allPValues = ResolveSepsets.getAvailablePValues(independenceTests, x, y, condSet);

        Collections.sort(allPValues);
        int c = 0;
        while (c < allPValues.size() && allPValues.get(c) < independenceTests.iterator().next().getAlpha()) {
            c++;
        }

        // At least half of the judgments are for independence.
        boolean independent = c < allPValues.size() / 2;

        if (independent) {
            String message = "***Majority = " + LogUtilsSearch.independenceFact(x, y, condSet) + " independent";
            TetradLogger.getInstance().log(message);
        } else {
            String message = "###Majority = " + LogUtilsSearch.independenceFact(x, y, condSet) + " dependent";
            TetradLogger.getInstance().log(message);
        }
        TetradLogger.getInstance().log("c = " + c);

        return independent;
    }

    /**
     * Gives the method to be used to resolve sepsets when they conflict.
     */
    public enum Method {
        /**
         * Fisher's method
         */
        fisher,
        /**
         * Fisher's method
         */
        fisher2,
        /**
         * Tippett's method
         */
        tippett,
        /**
         * Worsley and Friston's method
         */
        worsleyfriston,
        /**
         * Stouffer et al.'s method
         */
        stouffer,
        /**
         * Mudholker and George's method
         */
        mudholkergeorge,
        /**
         * Mudholker and George's method
         */
        mudholkergeorge2,
        /**
         * Wilkinson's method
         */
        average,
        /**
         * Average method
         */
        averagetest,
        /**
         * Random method
         */
        random,
        /**
         * False discovery rate method
         */
        fdr,
        /**
         * Majority method
         */
        majority
    }
}




