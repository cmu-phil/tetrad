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

package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.search.score.BasisFunctionBicScore;
import edu.cmu.tetrad.search.score.PenaltyDiscountCalibration;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.search.utils.Embedding;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import edu.cmu.tetrad.search.score.PenaltyDiscountCalibration.NullFit;

/**
 * Tests for the df-aware penalty discount calibration.
 *
 * @author josephramsey
 */
public class TestPenaltyDiscountCalibration {

    /**
     * The conventional discount falls out of the derivation: one expected false edge at p = 100, N = 1000 is c = 2.
     */
    @Test
    public void testConventionalDiscountIsOneFalseEdgeAtP100N1000() {
        Map<Integer, Long> hist = PenaltyDiscountCalibration.uniformPairDofHistogram(100, 1);
        double c = PenaltyDiscountCalibration.penaltyDiscountForExpectedFalseEdges(hist, 1000, 1.0);
        assertEquals(2.00, c, 0.01);
    }

    /**
     * With every pair at df = 1 the general routine must agree with SemBicScore's closed form.
     */
    @Test
    public void testAgreesWithSemBicScoreAtDfOne() {
        int[][] cases = {{100, 1000}, {500, 1000}, {5000, 1000}, {5000, 5000}};
        for (int[] pn : cases) {
            Map<Integer, Long> hist = PenaltyDiscountCalibration.uniformPairDofHistogram(pn[0], 1);
            double general = PenaltyDiscountCalibration.penaltyDiscountForFalseDiscoveryRate(hist, pn[1], pn[0], 6.0, 0.01);
            double closed = SemBicScore.penaltyDiscountForFalseDiscoveryRate(pn[0], pn[1], 6.0, 0.01);
            assertEquals("p=" + pn[0] + " N=" + pn[1], closed, general, 1e-4);
        }
    }

    /**
     * alpha must match the closed-form chi-square(1) tail for df = 1, stay accurate deep in the tail for larger df,
     * and fall monotonically in both c and df.
     */
    @Test
    public void testAlpha() {
        assertEquals(2.02e-4, PenaltyDiscountCalibration.alpha(2.0, 1, 1000), 2e-6);
        // df = 9 at c = 2, N = 1000: about 1.7e-22. 1 - CDF would return exactly 0 here.
        double a9 = PenaltyDiscountCalibration.alpha(2.0, 9, 1000);
        assertTrue(a9 > 1e-23 && a9 < 3e-22);
        double prev = 1.0;
        for (int df = 1; df <= 16; df++) {
            double a = PenaltyDiscountCalibration.alpha(2.0, df, 1000);
            assertTrue("alpha should fall with df at df=" + df, a < prev);
            prev = a;
        }
        prev = 1.0;
        for (double c = 0.5; c <= 4.0; c += 0.5) {
            double a = PenaltyDiscountCalibration.alpha(c, 1, 1000);
            assertTrue("alpha should fall with c at c=" + c, a < prev);
            prev = a;
        }
    }

    /**
     * penaltyDiscountForAlpha must invert alpha, for df = 1 and for larger df.
     */
    @Test
    public void testAlphaRoundTrip() {
        for (int df : new int[]{1, 4, 9}) {
            for (double alpha : new double[]{1e-2, 1e-4, 1e-8}) {
                double c = PenaltyDiscountCalibration.penaltyDiscountForAlpha(alpha, df, 1000);
                assertEquals("df=" + df + " alpha=" + alpha, alpha,
                        PenaltyDiscountCalibration.alpha(c, df, 1000), 1e-3 * alpha);
            }
        }
        // SemBicScore's df = 1 convenience: 2.02e-4 at N = 1000 is c = 2, and c = 2 implies 2.02e-4.
        assertEquals(2.00, SemBicScore.penaltyDiscountForAlpha(2.02e-4, 1000), 0.01);
    }

    /**
     * The returned discount must reproduce the budget when fed back in.
     */
    @Test
    public void testRoundTrip() {
        Map<Integer, Long> hist = new TreeMap<>();
        hist.put(1, 3000L);
        hist.put(4, 2000L);
        hist.put(9, 1000L);
        double budget = 25.0;
        double c = PenaltyDiscountCalibration.penaltyDiscountForExpectedFalseEdges(hist, 1000, budget);
        assertEquals(budget, PenaltyDiscountCalibration.expectedFalseEdges(c, hist, 1000), 1e-3 * budget);
    }

    /**
     * Higher df per pair calls for a smaller discount at the same budget, and a mixed population lands between
     * its pure components.
     */
    @Test
    public void testDiscountFallsWithDf() {
        int p = 5000, n = 1000;
        double c1 = PenaltyDiscountCalibration.penaltyDiscountForFalseDiscoveryRate(
                PenaltyDiscountCalibration.uniformPairDofHistogram(p, 1), n, p, 6.0, 0.01);
        double c3 = PenaltyDiscountCalibration.penaltyDiscountForFalseDiscoveryRate(
                PenaltyDiscountCalibration.uniformPairDofHistogram(p, 3), n, p, 6.0, 0.01);
        assertEquals(2.77, c1, 0.02);
        assertEquals(0.63, c3, 0.02);   // Basis Function BIC, truncation 3, all continuous
        assertTrue(c3 < c1);

        // Half the variables continuous (block 3), half binary (block 1): pairs at df 1, 3, 9.
        int[] sizes = new int[p];
        for (int i = 0; i < p; i++) sizes[i] = (i % 2 == 0) ? 3 : 1;
        Map<Integer, Long> mixed = PenaltyDiscountCalibration.pairDofHistogram(sizes);
        assertEquals(3, mixed.size());
        long total = mixed.values().stream().mapToLong(Long::longValue).sum();
        assertEquals((long) p * (p - 1) / 2, total);
        double cm = PenaltyDiscountCalibration.penaltyDiscountForFalseDiscoveryRate(mixed, n, p, 6.0, 0.01);
        assertTrue(cm > c3 && cm < c1);
    }

    /**
     * An exact NullFit must reproduce the chi-square alpha, and with one column per block the chain-rule LRT
     * must equal -N ln(1 - r^2).
     */
    @Test
    public void testExactFitAndOneColumnLrt() {
        for (int df : new int[]{1, 4, 9}) {
            assertEquals(PenaltyDiscountCalibration.alpha(2.0, df, 1000),
                    PenaltyDiscountCalibration.alpha(2.0, df, NullFit.exact(10, df), 1000), 1e-15);
        }
        Random rnd = new Random(0);
        int n = 500;
        double[] x = new double[n], y = new double[n];
        for (int i = 0; i < n; i++) {
            x[i] = rnd.nextGaussian();
            y[i] = 0.5 * x[i] + rnd.nextGaussian();
        }
        int[] id = new int[n];
        for (int i = 0; i < n; i++) id[i] = i;
        double lrt = PenaltyDiscountCalibration.chainRuleLrt(new double[][]{x, y}, new int[]{1}, new int[]{0}, id);
        double mx = 0, my = 0;
        for (int i = 0; i < n; i++) { mx += x[i]; my += y[i]; }
        mx /= n; my /= n;
        double sxx = 0, syy = 0, sxy = 0;
        for (int i = 0; i < n; i++) { sxx += (x[i]-mx)*(x[i]-mx); syy += (y[i]-my)*(y[i]-my); sxy += (x[i]-mx)*(y[i]-my); }
        double r2 = sxy * sxy / (sxx * syy);
        assertEquals(-n * Math.log(1 - r2), lrt, 1e-8);
    }

    /**
     * Permutation fitting recovers chi-square(1) for Gaussian single-column blocks, and finds the inflated null
     * for polynomial-embedded blocks; the fitted calibration is then more conservative than the exact one.
     */
    @Test
    public void testPermutationFit() {
        Random rnd = new Random(1);
        int p = 30, n = 800;
        double[][] raw = new double[p][n];
        for (int j = 0; j < p; j++) for (int i = 0; i < n; i++) raw[j][i] = rnd.nextGaussian();
        List<int[]> one = new ArrayList<>();
        for (int j = 0; j < p; j++) one.add(new int[]{j});
        NullFit f1 = PenaltyDiscountCalibration.fitNullsByPermutation(raw, one, 600, 0).get(1);
        assertEquals(1.0, f1.kappa(), 0.15);
        assertEquals(1.0, f1.nu(), 0.15);
        assertEquals((long) p * (p - 1) / 2, f1.pairs());

        // Embed each variable as (z, z^2, z^3): nine parameters per pair, but not a chi-square(9) null.
        double[][] emb = new double[3 * p][n];
        List<int[]> blocks = new ArrayList<>();
        for (int j = 0; j < p; j++) {
            for (int i = 0; i < n; i++) {
                double z = raw[j][i];
                emb[3 * j][i] = z; emb[3 * j + 1][i] = z * z; emb[3 * j + 2][i] = z * z * z;
            }
            blocks.add(new int[]{3 * j, 3 * j + 1, 3 * j + 2});
        }
        Map<Integer, NullFit> fits = PenaltyDiscountCalibration.fitNullsByPermutation(emb, blocks, 600, 0);
        NullFit f9 = fits.get(9);
        assertEquals(9.0, f9.kappa() * f9.nu(), 1.0);   // mean is preserved
        assertTrue("variance should be inflated: kappa=" + f9.kappa(), f9.kappa() > 1.5);
        assertTrue("effective df should shrink: nu=" + f9.nu(), f9.nu() < 6.0);

        double cExact = PenaltyDiscountCalibration.penaltyDiscountForFalseDiscoveryRate(
                PenaltyDiscountCalibration.uniformPairDofHistogram(p, 3), n, p, 6.0, 0.01);
        double cFit = PenaltyDiscountCalibration.penaltyDiscountForFalseDiscoveryRateFitted(fits, n, p, 6.0, 0.01);
        assertTrue("fitted null should raise c: " + cExact + " -> " + cFit, cFit > cExact);
    }

    /**
     * The rank transform must map each continuous column to the mid-rank grid on [-1, 1] in the original order,
     * and a rank-transformed Basis Function BIC must have a chi-square null on its nominal df, where the min-max
     * embedding does not.
     */
    @Test
    public void testRankTransformedEmbeddingHasChiSquareNull() throws Exception {
        int p = 30, n = 800;
        List<Node> nodes = new ArrayList<>();
        for (int i = 1; i <= p; i++) nodes.add(new ContinuousVariable("X" + i));
        DataSet data = new SemIm(new SemPm(RandomGraph.randomGraph(nodes, 0, 60, 1000, 1000, 1000, false, 1L)))
                .simulateData(n, false);

        DataSet ranked = Embedding.rankTransformToUnitInterval(data);
        double lo = -1.0 + 1.0 / n, hi = 1.0 - 1.0 / n;
        for (int j = 0; j < p; j++) {
            double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
            int argmin = -1, argmax = -1;
            for (int i = 0; i < n; i++) {
                double v = data.getDouble(i, j);
                if (v < min) { min = v; argmin = i; }
                if (v > max) { max = v; argmax = i; }
            }
            assertEquals(lo, ranked.getDouble(argmin, j), 1e-12);
            assertEquals(hi, ranked.getDouble(argmax, j), 1e-12);
        }

        // Ties must be averaged: a 0/1 column loaded as continuous stays two-valued, so its higher-order basis
        // columns are dropped as rank-deficient rather than turned into row-order noise.
        DataSet withBinary = data.copy();
        java.util.Random rnd = new java.util.Random(0);
        for (int i = 0; i < n; i++) withBinary.setDouble(i, 0, rnd.nextDouble() < 0.05 ? 1.0 : 0.0);
        DataSet rankedBinary = Embedding.rankTransformToUnitInterval(withBinary);
        java.util.Set<Double> distinct = new java.util.HashSet<>();
        for (int i = 0; i < n; i++) distinct.add(rankedBinary.getDouble(i, 0));
        assertEquals(2, distinct.size());
        assertEquals(1, new BasisFunctionBicScore(withBinary, 3, 0.0, false, true).embeddingBlockSizes()[0]);

        NullFit rank = new BasisFunctionBicScore(data, 3, 0.0, false, true)
                .fitNullsByPermutation(data, 1500, 0).get(9);
        assertEquals(1.0, rank.kappa(), 0.15);
        assertEquals(9.0, rank.nu(), 1.2);

        NullFit minmax = new BasisFunctionBicScore(data, 3, 0.0, false, false)
                .fitNullsByPermutation(data, 1500, 0).get(9);
        assertTrue("min-max embedding should show an inflated null: kappa=" + minmax.kappa(), minmax.kappa() > 1.5);
    }

    /**
     * The effect-size floor must invert minDetectablePartialCorrelation, grow with N where the FDR discount
     * shrinks, and cross it somewhere in the low thousands for r = 0.05 at p = 55.
     */
    @Test
    public void testEffectSizeFloor() {
        for (int n : new int[]{500, 1000, 20000}) {
            for (double r : new double[]{0.02, 0.05, 0.2}) {
                double c = PenaltyDiscountCalibration.penaltyDiscountForMinPartialCorrelation(r, n);
                assertEquals(r, SemBicScore.minDetectablePartialCorrelation(c, n), 1e-10);
            }
        }
        double prevEffect = 0, prevFdr = Double.MAX_VALUE;
        for (int n : new int[]{1000, 5000, 20000, 100000}) {
            double cEffect = PenaltyDiscountCalibration.penaltyDiscountForMinPartialCorrelation(0.05, n);
            double cFdr = SemBicScore.penaltyDiscountForFalseDiscoveryRate(55, n, 5.0, 0.01);
            assertTrue(cEffect > prevEffect);
            assertTrue(cFdr < prevFdr);
            prevEffect = cEffect;
            prevFdr = cFdr;
        }
        assertTrue(PenaltyDiscountCalibration.penaltyDiscountForMinPartialCorrelation(0.05, 1000)
                < SemBicScore.penaltyDiscountForFalseDiscoveryRate(55, 1000, 5.0, 0.01));
        assertTrue(PenaltyDiscountCalibration.penaltyDiscountForMinPartialCorrelation(0.05, 20000)
                > SemBicScore.penaltyDiscountForFalseDiscoveryRate(55, 20000, 5.0, 0.01));
    }

    /**
     * pairDofHistogram must count every unordered pair exactly once with df = size[x] * size[y].
     */
    @Test
    public void testPairDofHistogram() {
        Map<Integer, Long> hist = PenaltyDiscountCalibration.pairDofHistogram(new int[]{1, 2, 3});
        assertEquals(Long.valueOf(1), hist.get(2));  // (1,2)
        assertEquals(Long.valueOf(1), hist.get(3));  // (1,3)
        assertEquals(Long.valueOf(1), hist.get(6));  // (2,3)
        assertEquals(3, hist.size());
    }
}
