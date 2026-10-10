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
import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.VerticalDoubleDataBox;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.blocks.BlockDiscoverers;
import edu.cmu.tetrad.search.blocks.BlockSpec;
import edu.cmu.tetrad.search.blocks.BlocksUtil;
import edu.cmu.tetrad.search.blocks.SingletonClusterPolicy;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Tests pooling several imputations of one data set into a single covariance matrix for latent clustering: the
 * averaged covariance of {@link BlocksUtil#pooledCovariance(List)} and the pooled-covariance constructors of the
 * block discoverers.
 *
 * @author josephramsey
 */
public class TestPooledBlockDiscovery {

    /**
     * Pooling identical copies of one data set reproduces that data set's covariance matrix exactly, at the shared
     * sample size.
     */
    @Test
    public void testPooledOfIdenticalCopiesIsTheSingleCovariance() {
        DataSet dataSet = simulateChainMim(500, 3, new Random(7), null);
        CovarianceMatrix single = new CovarianceMatrix(dataSet);
        CovarianceMatrix pooled = BlocksUtil.pooledCovariance(List.of(dataSet, dataSet, dataSet));

        assertEquals(single.getSampleSize(), pooled.getSampleSize());

        for (int i = 0; i < single.getDimension(); i++) {
            for (int j = 0; j < single.getDimension(); j++) {
                assertEquals(single.getMatrix().get(i, j), pooled.getMatrix().get(i, j), 1e-12);
            }
        }
    }

    /**
     * Pooling rejects data sets that differ in their variables, with a message saying pooling is for imputations of
     * one data set.
     */
    @Test
    public void testPooledRejectsDifferingVariables() {
        DataSet dataSet = simulateChainMim(200, 2, new Random(7), null);

        List<Node> otherVars = new ArrayList<>();
        for (int i = 1; i <= dataSet.getNumColumns(); i++) otherVars.add(new ContinuousVariable("Y" + i));
        DataSet other = new BoxDataSet(new VerticalDoubleDataBox(200, dataSet.getNumColumns()), otherVars);

        try {
            BlocksUtil.pooledCovariance(List.of(dataSet, other));
            fail("Expected an IllegalArgumentException for differing variables.");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("imputations"));
        }
    }

    /**
     * TSC run through the pooled-covariance discoverer over several stochastic imputations of a 3-latent chain MIM
     * with 20 percent of values missing recovers the three true clusters, and the resulting BlockSpec is anchored to
     * the representative data set.
     */
    @Test
    public void testPooledTscRecoversClusters() {
        int n = 2000, m = 4, numLatents = 3;
        Random rng = new Random(42);
        boolean[][] miss = new boolean[n][numLatents * m];
        DataSet complete = simulateChainMim(n, numLatents, rng, null);
        for (int t = 0; t < n; t++) {
            for (int c = 0; c < numLatents * m; c++) miss[t][c] = rng.nextDouble() < 0.20;
        }

        List<DataSet> imputations = new ArrayList<>();
        for (int k = 0; k < 5; k++) {
            imputations.add(imputeCrudely(complete, miss, new Random(1000 + k)));
        }

        DataSet representative = imputations.getFirst();
        CovarianceMatrix pooled = BlocksUtil.pooledCovariance(imputations);
        int autoEss = BlocksUtil.pooledEffectiveSampleSize(imputations);

        assertTrue("imputations disagree, so the effective sample size is below n", autoEss < n);
        assertTrue(autoEss >= 10);

        BlockSpec spec = BlockDiscoverers.tsc(representative, pooled, 0.01, autoEss, 1e-8, 2,
                SingletonClusterPolicy.EXCLUDE, 0, false).discover();

        assertSame(representative, spec.dataSet());

        Set<Set<Integer>> found = new HashSet<>();
        for (List<Integer> block : spec.blocks()) {
            if (block.size() > 1) found.add(new HashSet<>(block));
        }

        Set<Set<Integer>> truth = new HashSet<>();
        for (int l = 0; l < numLatents; l++) {
            Set<Integer> block = new HashSet<>();
            for (int j = 0; j < m; j++) block.add(l * m + j);
            truth.add(block);
        }

        assertEquals(truth, found);
    }

    /**
     * Pooling identical copies carries no between-imputation disagreement, so the estimated effective sample size is
     * the full n; fewer than two imputations are rejected.
     */
    @Test
    public void testEffectiveSampleSizeOfIdenticalCopiesIsN() {
        DataSet dataSet = simulateChainMim(500, 3, new Random(7), null);
        assertEquals(500, BlocksUtil.pooledEffectiveSampleSize(List.of(dataSet, dataSet, dataSet)));

        try {
            BlocksUtil.pooledEffectiveSampleSize(List.of(dataSet));
            fail("Expected an IllegalArgumentException for a single imputation.");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    /**
     * A pooled matrix whose variables do not match the representative data set is rejected by the discoverer.
     */
    @Test
    public void testDiscovererRejectsMismatchedPooledMatrix() {
        DataSet dataSet = simulateChainMim(200, 2, new Random(7), null);

        List<Node> otherVars = new ArrayList<>();
        for (int i = 1; i <= dataSet.getNumColumns(); i++) otherVars.add(new ContinuousVariable("Y" + i));
        DataSet other = new BoxDataSet(new VerticalDoubleDataBox(200, dataSet.getNumColumns()), otherVars);
        for (int t = 0; t < 200; t++) for (int c = 0; c < other.getNumColumns(); c++) other.setDouble(t, c, t + c);
        CovarianceMatrix mismatched = new CovarianceMatrix(other);

        try {
            BlockDiscoverers.tsc(dataSet, mismatched, 0.01, -1, 1e-8, 2, SingletonClusterPolicy.EXCLUDE, 0, false);
            fail("Expected an IllegalArgumentException for a mismatched pooled matrix.");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("same variables"));
        }
    }

    /**
     * Simulates a linear-Gaussian chain MIM, L1 -&gt; L2 -&gt; ... with 4 pure indicators per latent, indicator
     * variances about 1.
     */
    private static DataSet simulateChainMim(int n, int numLatents, Random rng, String ignored) {
        int m = 4, p = numLatents * m;
        double loading = 0.8, beta = 0.7;

        List<Node> vars = new ArrayList<>();
        for (int i = 1; i <= p; i++) vars.add(new ContinuousVariable("X" + i));

        VerticalDoubleDataBox box = new VerticalDoubleDataBox(n, p);

        for (int t = 0; t < n; t++) {
            double[] latents = new double[numLatents];
            latents[0] = rng.nextGaussian();
            for (int l = 1; l < numLatents; l++) {
                latents[l] = beta * latents[l - 1] + Math.sqrt(1 - beta * beta) * rng.nextGaussian();
            }
            for (int c = 0; c < p; c++) {
                box.set(t, c, loading * latents[c / m] + Math.sqrt(1 - loading * loading) * rng.nextGaussian());
            }
        }

        return new BoxDataSet(box, vars);
    }

    /**
     * A crude stochastic single imputation: each missing cell is replaced by a draw from a normal with that column's
     * observed mean and standard deviation. Deliberately noisy, so the imputations disagree.
     */
    private static DataSet imputeCrudely(DataSet complete, boolean[][] miss, Random rng) {
        int n = complete.getNumRows(), p = complete.getNumColumns();
        VerticalDoubleDataBox box = new VerticalDoubleDataBox(n, p);

        for (int c = 0; c < p; c++) {
            double sum = 0, sum2 = 0;
            int count = 0;
            for (int t = 0; t < n; t++) {
                if (!miss[t][c]) {
                    double v = complete.getDouble(t, c);
                    sum += v;
                    sum2 += v * v;
                    count++;
                }
            }
            double mean = sum / count;
            double sd = Math.sqrt(sum2 / count - mean * mean);

            for (int t = 0; t < n; t++) {
                box.set(t, c, miss[t][c] ? mean + sd * rng.nextGaussian() : complete.getDouble(t, c));
            }
        }

        return new BoxDataSet(box, complete.getVariables());
    }
}
