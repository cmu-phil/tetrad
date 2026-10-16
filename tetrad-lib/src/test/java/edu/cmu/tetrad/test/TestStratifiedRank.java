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

package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.test.IndTestStratifiedRank;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.util.RankTests;
import edu.cmu.tetrad.util.StratifiedRankTests;
import org.ejml.simple.SimpleMatrix;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests of the stratified (domain-pooled) rank test, StratifiedRankTests, and its conditional independence wrapper,
 * IndTestStratifiedRank. The simulations are the nonstationary linear setting of Dong et al. (ICML 2026): a fixed
 * graph with edge coefficients, intercepts, and noise scales redrawn in each domain.
 *
 * @author josephramsey
 */
public class TestStratifiedRank {

    /**
     * Correlation matrix of the columns of data.
     */
    private static SimpleMatrix correlation(double[][] data) {
        int n = data.length, d = data[0].length;
        double[] mean = new double[d];
        for (double[] row : data) for (int j = 0; j < d; j++) mean[j] += row[j];
        for (int j = 0; j < d; j++) mean[j] /= n;

        double[][] c = new double[d][d];
        for (double[] row : data) {
            for (int j = 0; j < d; j++) {
                for (int k = 0; k < d; k++) {
                    c[j][k] += (row[j] - mean[j]) * (row[k] - mean[k]);
                }
            }
        }
        for (int j = 0; j < d; j++) for (int k = 0; k < d; k++) c[j][k] /= (n - 1);

        SimpleMatrix out = new SimpleMatrix(d, d);
        for (int j = 0; j < d; j++) {
            for (int k = 0; k < d; k++) {
                out.set(j, k, c[j][k] / Math.sqrt(c[j][j] * c[k][k]));
            }
        }
        return out;
    }

    /**
     * One domain of the collider-free model Z -&gt; X, Z -&gt; Y, with the given coefficients, intercepts, and an
     * extra X -&gt; Y coefficient bxy (zero for the CI case). Columns: Z, X, Y.
     */
    private static double[][] simulateDomain(int n, double bzx, double bzy, double bxy,
                                             double mx, double my, Random rng) {
        double[][] data = new double[n][3];
        for (int i = 0; i < n; i++) {
            double z = rng.nextGaussian();
            double x = mx + bzx * z + rng.nextGaussian();
            double y = my + bzy * z + bxy * x + rng.nextGaussian();
            data[i][0] = z;
            data[i][1] = x;
            data[i][2] = y;
        }
        return data;
    }

    private static double[][] concatenate(List<double[][]> parts) {
        int n = 0;
        for (double[][] p : parts) n += p.length;
        double[][] out = new double[n][];
        int i = 0;
        for (double[][] p : parts) for (double[] row : p) out[i++] = row;
        return out;
    }

    /**
     * With a single stratum, the pooled p-value is the ordinary Bartlett p-value of RankTests.rankLeByWilks on the
     * same matrix, up to the small differences in ridging.
     */
    @Test
    public void testSingleStratumAgreesWithWilks() {
        Random rng = new Random(38482L);
        int n = 1000;
        double[][] data = simulateDomain(n, 0.7, 0.6, 0.5, 0.0, 0.0, rng);
        SimpleMatrix S = correlation(data);

        StratifiedRankTests pooled = new StratifiedRankTests(Collections.singletonList(S), new int[]{n});

        int[] x = {0, 1};
        int[] y = {2};

        double pPooled = pooled.pValueRankLe(x, y, 0);
        double pWilks = RankTests.rankLeByWilks(S, x, y, n, 0);

        assertEquals(pWilks, pPooled, 1e-4);
    }

    /**
     * Type I calibration under nonstationarity for the hypothesis X _||_ Y | Z, true within every domain. The
     * coefficients of Z on X and on Y, and the intercepts of X and Y, are redrawn in each domain. The pooled test
     * should reject at about the nominal rate; the same statistic computed on the concatenated data, ignoring
     * domains, over-rejects badly because the domain shifts induce dependence between X and Y given Z in the
     * mixture.
     */
    @Test
    public void testTypeICalibrationUnderNonstationarity() {
        Random rng = new Random(2062L);
        int reps = 200;
        int domains = 5;
        int nPerDomain = 300;
        double alpha = 0.05;

        int pooledRejections = 0;
        int naiveRejections = 0;

        for (int rep = 0; rep < reps; rep++) {
            List<SimpleMatrix> covs = new ArrayList<>();
            int[] ns = new int[domains];
            List<double[][]> parts = new ArrayList<>();

            for (int t = 0; t < domains; t++) {
                double bzx = 0.3 + 0.9 * rng.nextDouble();  // coefficient changes by domain
                double bzy = 0.3 + 0.9 * rng.nextDouble();
                double mx = 3.0 * rng.nextGaussian();       // intercept changes by domain
                double my = 3.0 * rng.nextGaussian();
                double[][] part = simulateDomain(nPerDomain, bzx, bzy, 0.0, mx, my, rng);
                parts.add(part);
                covs.add(correlation(part));
                ns[t] = nPerDomain;
            }

            StratifiedRankTests pooled = new StratifiedRankTests(covs, ns);
            double pPooled = pooled.pValueRankLeConditioned(new int[]{1}, new int[]{2}, new int[]{0}, 0);
            if (pPooled <= alpha) pooledRejections++;

            double[][] all = concatenate(parts);
            StratifiedRankTests naive = new StratifiedRankTests(
                    Collections.singletonList(correlation(all)), new int[]{all.length});
            double pNaive = naive.pValueRankLeConditioned(new int[]{1}, new int[]{2}, new int[]{0}, 0);
            if (pNaive <= alpha) naiveRejections++;
        }

        double pooledRate = pooledRejections / (double) reps;
        double naiveRate = naiveRejections / (double) reps;

        assertTrue("Pooled Type I rate " + pooledRate + " should be near alpha = " + alpha, pooledRate <= 0.12);
        assertTrue("Naive Type I rate " + naiveRate + " should be badly inflated; got " + naiveRate,
                naiveRate >= 0.5);
    }

    /**
     * Power under masking: X -&gt; Y within every domain, with the sign of the coefficient alternating by domain, so
     * that the correlation in the concatenated data cancels toward zero. The pooled test should detect the
     * dependence; the test on the concatenated data mostly misses it.
     */
    @Test
    public void testPowerUnderMasking() {
        Random rng = new Random(77031L);
        int reps = 200;
        int domains = 4;
        int nPerDomain = 300;
        double alpha = 0.05;

        int pooledRejections = 0;
        int naiveRejections = 0;

        for (int rep = 0; rep < reps; rep++) {
            List<SimpleMatrix> covs = new ArrayList<>();
            int[] ns = new int[domains];
            List<double[][]> parts = new ArrayList<>();

            for (int t = 0; t < domains; t++) {
                double bxy = (t % 2 == 0 ? 1 : -1) * 0.5;  // masking: sign alternates by domain
                double[][] part = simulateDomain(nPerDomain, 0.0, 0.0, bxy, 0.0, 0.0, rng);
                parts.add(part);
                covs.add(correlation(part));
                ns[t] = nPerDomain;
            }

            StratifiedRankTests pooled = new StratifiedRankTests(covs, ns);
            if (pooled.pValueRankLe(new int[]{1}, new int[]{2}, 0) <= alpha) pooledRejections++;

            double[][] all = concatenate(parts);
            StratifiedRankTests naive = new StratifiedRankTests(
                    Collections.singletonList(correlation(all)), new int[]{all.length});
            if (naive.pValueRankLe(new int[]{1}, new int[]{2}, 0) <= alpha) naiveRejections++;
        }

        double pooledRate = pooledRejections / (double) reps;
        double naiveRate = naiveRejections / (double) reps;

        assertTrue("Pooled power " + pooledRate + " should be high", pooledRate >= 0.9);
        assertTrue("Naive test should mostly miss the masked dependence; got " + naiveRate, naiveRate <= 0.3);
    }

    /**
     * Rank recovery in a nonstationary one-factor model: one latent with six indicators, loadings and indicator
     * intercepts redrawn in each domain. Within every domain the cross-covariance of the first three indicators
     * against the last three has rank one; the pooled estimate should say so, while the estimate from the
     * concatenated data is usually inflated by the domain shifts.
     */
    @Test
    public void testOneFactorRankRecovery() {
        Random rng = new Random(90125L);
        int reps = 50;
        int domains = 5;
        int nPerDomain = 500;
        double alpha = 0.05;

        int pooledCorrect = 0;
        int naiveCorrect = 0;

        int[] x = {0, 1, 2};
        int[] y = {3, 4, 5};

        for (int rep = 0; rep < reps; rep++) {
            List<SimpleMatrix> covs = new ArrayList<>();
            int[] ns = new int[domains];
            List<double[][]> parts = new ArrayList<>();

            for (int t = 0; t < domains; t++) {
                double[] loading = new double[6];
                double[] shift = new double[6];
                for (int j = 0; j < 6; j++) {
                    loading[j] = 0.5 + 0.8 * rng.nextDouble();
                    shift[j] = 3.0 * rng.nextGaussian();
                }
                double[][] part = new double[nPerDomain][6];
                for (int i = 0; i < nPerDomain; i++) {
                    double l = rng.nextGaussian();
                    for (int j = 0; j < 6; j++) {
                        part[i][j] = shift[j] + loading[j] * l + 0.7 * rng.nextGaussian();
                    }
                }
                parts.add(part);
                covs.add(correlation(part));
                ns[t] = nPerDomain;
            }

            StratifiedRankTests pooled = new StratifiedRankTests(covs, ns);
            if (pooled.estimateRank(x, y, alpha) == 1) pooledCorrect++;

            double[][] all = concatenate(parts);
            StratifiedRankTests naive = new StratifiedRankTests(
                    Collections.singletonList(correlation(all)), new int[]{all.length});
            if (naive.estimateRank(x, y, alpha) == 1) naiveCorrect++;
        }

        assertTrue("Pooled rank should usually be the true rank 1; correct in " + pooledCorrect + "/" + reps,
                pooledCorrect >= 0.8 * reps);
        assertTrue("Concatenated rank should usually be inflated; correct in " + naiveCorrect + "/" + reps,
                naiveCorrect <= 0.5 * reps);
    }

    /**
     * The IndependenceTest wrapper, built from a single data set with a domain column: X _||_ Y | Z should be
     * accepted when true within every domain, and X _||_ Y rejected when X -&gt; Y within every domain.
     */
    @Test
    public void testIndTestWrapper() throws InterruptedException {
        Random rng = new Random(5150L);
        int domains = 5;
        int nPerDomain = 500;

        List<Node> vars = new ArrayList<>();
        vars.add(new ContinuousVariable("Z"));
        vars.add(new ContinuousVariable("X"));
        vars.add(new ContinuousVariable("Y"));
        vars.add(new ContinuousVariable("domain"));

        double[][] data = new double[domains * nPerDomain][4];
        int row = 0;
        for (int t = 0; t < domains; t++) {
            double bzx = 0.3 + 0.9 * rng.nextDouble();
            double bzy = 0.3 + 0.9 * rng.nextDouble();
            double mx = 3.0 * rng.nextGaussian();
            double my = 3.0 * rng.nextGaussian();
            double[][] part = simulateDomain(nPerDomain, bzx, bzy, 0.0, mx, my, rng);
            for (double[] r : part) {
                data[row][0] = r[0];
                data[row][1] = r[1];
                data[row][2] = r[2];
                data[row][3] = t;
                row++;
            }
        }

        DataSet dataSet = new BoxDataSet(new DoubleDataBox(data), vars);
        IndTestStratifiedRank test = new IndTestStratifiedRank(dataSet, "domain", 0.01);

        assertEquals(3, test.getVariables().size());
        assertEquals(domains * nPerDomain, test.getSampleSize());

        Node z = test.getVariables().get(0);
        Node x = test.getVariables().get(1);
        Node y = test.getVariables().get(2);

        Set<Node> cond = new HashSet<>(Collections.singletonList(z));
        IndependenceResult ci = test.checkIndependence(x, y, cond);
        assertTrue("X _||_ Y | Z holds within every domain and should be accepted; p = " + ci.getPValue(),
                ci.isIndependent());

        IndependenceResult marginal = test.checkIndependence(x, z, new HashSet<>());
        assertTrue("Z -> X within every domain; marginal independence should be rejected; p = "
                   + marginal.getPValue(), !marginal.isIndependent());
    }

    /**
     * Querying over more strata than are usable: with every stratum too small for the Bartlett multiplier to be
     * positive, the p-value is NaN and the rank estimate conservatively reports full rank.
     */
    @Test
    public void testAllStrataTooSmall() {
        Random rng = new Random(314L);
        int[] x = {0, 1, 2};
        int[] y = {3, 4, 5};

        List<SimpleMatrix> covs = new ArrayList<>();
        double[][] part = new double[4][6];  // Bartlett multiplier (n - 1) - (p + q + 1)/2 <= 0 at n = 4, p = q = 3
        for (double[] r : part) for (int j = 0; j < 6; j++) r[j] = rng.nextGaussian();
        covs.add(correlation(part));

        StratifiedRankTests pooled = new StratifiedRankTests(covs, new int[]{4});
        assertTrue(Double.isNaN(pooled.pValueRankLe(x, y, 0)));
        assertEquals(3, pooled.estimateRank(x, y, 0.05));
    }

    /**
     * The row splitter: rows are routed to strata by the integral values of the domain column, in ascending order
     * of value, and the domain column is dropped.
     */
    @Test
    public void testSplit() {
        List<Node> vars = new ArrayList<>();
        vars.add(new ContinuousVariable("A"));
        vars.add(new ContinuousVariable("domain"));
        vars.add(new ContinuousVariable("B"));

        double[][] data = {
                {1.0, 1, 10.0},
                {2.0, 0, 20.0},
                {3.0, 1, 30.0},
                {4.0, 0, 40.0},
                {5.0, 1, 50.0},
        };

        DataSet dataSet = new BoxDataSet(new DoubleDataBox(data), vars);
        List<DataSet> strata = StratifiedRankTests.split(dataSet, vars.get(1));

        assertEquals(2, strata.size());
        assertEquals(2, strata.get(0).getNumRows());   // domain 0
        assertEquals(3, strata.get(1).getNumRows());   // domain 1
        assertEquals(Arrays.asList("A", "B"), strata.get(0).getVariableNames());
        assertEquals(2.0, strata.get(0).getDouble(0, 0), 1e-12);
        assertEquals(50.0, strata.get(1).getDouble(2, 1), 1e-12);
    }
}
