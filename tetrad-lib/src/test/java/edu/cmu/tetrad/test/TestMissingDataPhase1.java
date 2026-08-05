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
import edu.cmu.tetrad.data.missing.MissingDataSpec;
import edu.cmu.tetrad.data.missing.MissingDataUtils;
import edu.cmu.tetrad.data.missing.MissingValueSupport;
import edu.cmu.tetrad.data.missing.TestwiseRows;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests the Phase 1 missing-data refactor: the explicit-policy constructors of SemBicScore and IndTestFisherZ, the
 * behavior-preservation of the (now shared and cached) test-wise row computation, listwise deletion, and the
 * capability declarations.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class TestMissingDataPhase1 {

    /**
     * Constructs a new test.
     */
    public TestMissingDataPhase1() {
    }

    /**
     * An explicit TESTWISE spec must reproduce the legacy default exactly, for both scores and Fisher Z p-values, on
     * data with missing values--including on repeated (cached) queries.
     */
    @Test
    public void testExplicitTestwiseEqualsLegacyDefault() throws InterruptedException {
        DataSet ds = simulateWithMissing(500, 6, 0.08, new Random(99));

        SemBicScore legacy = new SemBicScore(ds, true);
        SemBicScore explicit = new SemBicScore(ds, true, MissingDataSpec.testwise());

        for (int rep = 0; rep < 2; rep++) {
            for (int i = 0; i < 6; i++) {
                assertEquals(legacy.localScore(i), explicit.localScore(i), 0.0);

                for (int par = 0; par < 6; par++) {
                    if (par == i) continue;
                    assertEquals(legacy.localScore(i, par), explicit.localScore(i, par), 0.0);
                }
            }

            assertEquals(legacy.localScore(0, 1, 2, 3), explicit.localScore(0, 1, 2, 3), 0.0);
        }

        IndTestFisherZ legacyTest = new IndTestFisherZ(ds, 0.05);
        IndTestFisherZ explicitTest = new IndTestFisherZ(ds, 0.05, MissingDataSpec.testwise());
        List<Node> vars = ds.getVariables();

        assertEquals(legacyTest.checkIndependence(vars.get(0), vars.get(3), vars.get(1)).getPValue(),
                explicitTest.checkIndependence(vars.get(0), vars.get(3), vars.get(1)).getPValue(), 0.0);
    }

    /**
     * FAIL must throw on missing data; MULTIPLE_IMPUTATION must be rejected at the single-score level.
     */
    @Test
    public void testFailAndMiPolicies() {
        DataSet ds = simulateWithMissing(200, 4, 0.1, new Random(1));

        try {
            new SemBicScore(ds, true, MissingDataSpec.fail());
            throw new AssertionError("Expected an IllegalArgumentException for FAIL on missing data.");
        } catch (IllegalArgumentException e) {
            // Expected.
        }

        try {
            new IndTestFisherZ(ds, 0.05, MissingDataSpec.multipleImputation(5));
            throw new AssertionError("Expected an UnsupportedOperationException for MULTIPLE_IMPUTATION.");
        } catch (UnsupportedOperationException e) {
            // Expected.
        }

        // FAIL on complete data is fine.
        DataSet complete = simulateWithMissing(200, 4, 0.0, new Random(2));
        new SemBicScore(complete, true, MissingDataSpec.fail());
    }

    /**
     * LISTWISE must reduce the sample size to the number of complete rows, and listwiseDelete must retain exactly the
     * complete rows.
     */
    @Test
    public void testListwise() {
        DataSet ds = simulateWithMissing(300, 5, 0.1, new Random(3));

        DataSet complete = MissingDataUtils.listwiseDelete(ds);

        for (int i = 0; i < complete.getNumRows(); i++) {
            for (int j = 0; j < complete.getNumColumns(); j++) {
                assertFalse(Double.isNaN(complete.getDouble(i, j)));
            }
        }

        SemBicScore score = new SemBicScore(ds, true, MissingDataSpec.listwise());
        assertEquals(complete.getNumRows(), score.getSampleSize());
    }

    /**
     * EM_COVARIANCE must construct and produce finite scores and p-values on MAR data, where test-wise deletion's
     * MCAR assumption is violated.
     */
    @Test
    public void testEmCovariance() throws InterruptedException {
        Random rand = new Random(4);
        int n = 1000, p = 5;
        double[][] d = new double[n][p];

        for (int i = 0; i < n; i++) {
            d[i][0] = rand.nextGaussian();
            for (int j = 1; j < p; j++) d[i][j] = 0.6 * d[i][j - 1] + rand.nextGaussian();
        }

        for (int i = 0; i < n; i++) {
            if (d[i][1] > 0.3) d[i][2] = Double.NaN; // MAR: missingness in X3 depends on observed X2.
        }

        DataSet ds = dataSet(d, p);

        SemBicScore score = new SemBicScore(ds, true, MissingDataSpec.emCovariance());
        assertFalse(Double.isNaN(score.localScore(2, 1, 3)));

        IndTestFisherZ test = new IndTestFisherZ(ds, 0.05, MissingDataSpec.emCovariance());
        List<Node> vars = ds.getVariables();
        double pValue = test.checkIndependence(vars.get(0), vars.get(3), vars.get(1), vars.get(2)).getPValue();
        assertFalse(Double.isNaN(pValue));

        // ESS modes construct.
        new SemBicScore(ds, true, MissingDataSpec.emCovariance()
                .withEssMode(MissingDataSpec.EffectiveSampleSizeMode.MIN_PAIRWISE));
    }

    /**
     * TestwiseRows must agree with a brute-force row filter, cache full-dataset queries, and bypass the cache for
     * proper row subsets.
     */
    @Test
    public void testTestwiseRows() {
        DataSet ds = simulateWithMissing(200, 4, 0.15, new Random(5));
        TestwiseRows twr = TestwiseRows.forDataSet(ds);
        twr.clearCache();

        int[] cols = {0, 2, 3};
        List<Integer> rows = twr.validRows(cols);

        List<Integer> expected = new ArrayList<>();

        K:
        for (int k = 0; k < ds.getNumRows(); k++) {
            for (int c : cols) {
                if (Double.isNaN(ds.getDouble(k, c))) continue K;
            }
            expected.add(k);
        }

        assertEquals(expected, rows);
        assertEquals(1, twr.cacheSize());

        // Same columns in a different order hit the same cache entry.
        twr.validRows(new int[]{3, 0, 2});
        assertEquals(1, twr.cacheSize());

        // A proper row subset bypasses the cache.
        List<Integer> candidate = expected.subList(0, expected.size() / 2);
        twr.validRows(cols, candidate);
        assertEquals(1, twr.cacheSize());

        twr.clearCache();
        assertEquals(0, twr.cacheSize());
    }

    /**
     * The capability declarations must report TESTWISE for both patched components.
     */
    @Test
    public void testCapabilityDeclarations() {
        DataSet ds = simulateWithMissing(100, 3, 0.0, new Random(6));

        assertEquals(MissingValueSupport.TESTWISE, new SemBicScore(ds, true).getMissingValueSupport());
        assertEquals(MissingValueSupport.TESTWISE, new IndTestFisherZ(ds, 0.05).getMissingValueSupport());
    }

    private static DataSet simulateWithMissing(int n, int p, double missingRate, Random rand) {
        double[][] d = new double[n][p];

        for (int i = 0; i < n; i++) {
            d[i][0] = rand.nextGaussian();

            for (int j = 1; j < p; j++) {
                d[i][j] = 0.5 * d[i][j - 1] + rand.nextGaussian();
            }
        }

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < p; j++) {
                if (rand.nextDouble() < missingRate) d[i][j] = Double.NaN;
            }
        }

        return dataSet(d, p);
    }

    private static DataSet dataSet(double[][] d, int p) {
        List<Node> vars = new ArrayList<>();

        for (int j = 0; j < p; j++) {
            vars.add(new ContinuousVariable("X" + (j + 1)));
        }

        return new BoxDataSet(new DoubleDataBox(d), vars);
    }
}
