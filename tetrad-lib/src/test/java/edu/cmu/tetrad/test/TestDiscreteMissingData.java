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
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.data.VerticalIntDataBox;
import edu.cmu.tetrad.data.missing.MissingDataSpec;
import edu.cmu.tetrad.data.missing.MissingValueSupport;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.BDeuScore;
import edu.cmu.tetrad.search.score.DiscreteBicScore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * Tests the discrete side of the Phase 1 missing-data refactor: BDeuScore's test-wise deletion routed (behavior
 * preserved) through TestwiseRows, and DiscreteBicScore's missing-value bug fix (previously, missing values flowed
 * the -99 sentinel into contingency-table indices, corrupting counts or throwing
 * ArrayIndexOutOfBoundsException).
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class TestDiscreteMissingData {

    /**
     * Constructs a new test.
     */
    public TestDiscreteMissingData() {
    }

    /**
     * An explicit TESTWISE spec must reproduce the legacy default exactly on missing data, including on repeated
     * (cached) queries.
     */
    @Test
    public void testBDeuExplicitTestwiseEqualsLegacyDefault() {
        DataSet ds = simulate(600, 5, 3, 0.1, new Random(21));

        BDeuScore legacy = new BDeuScore(ds);
        BDeuScore explicit = new BDeuScore(ds, MissingDataSpec.testwise());

        for (int rep = 0; rep < 2; rep++) {
            for (int i = 0; i < 5; i++) {
                for (int a = 0; a < 5; a++) {
                    if (a == i) continue;
                    assertEquals(legacy.localScore(i, new int[]{a}), explicit.localScore(i, new int[]{a}), 0.0);
                }
            }

            assertEquals(legacy.localScore(0, new int[]{1, 2, 3}), explicit.localScore(0, new int[]{1, 2, 3}), 0.0);
        }
    }

    /**
     * The semantic definition of test-wise deletion: the score on missing data must equal the score computed on the
     * dataset restricted to the rows complete on the scored columns. Checked for both scores. (For DiscreteBicScore
     * this also exercises the bug fix; the pre-fix code threw ArrayIndexOutOfBoundsException here.)
     */
    @Test
    public void testScoresEqualRestrictedCompleteRows() {
        DataSet ds = simulate(800, 5, 3, 0.08, new Random(22));

        int node = 0;
        int[] parents = {1, 3};

        // Restrict to rows complete on {node} union parents.
        List<Integer> keep = new ArrayList<>();

        K:
        for (int i = 0; i < ds.getNumRows(); i++) {
            if (ds.getInt(i, node) == DiscreteVariable.MISSING_VALUE) continue;
            for (int p : parents) {
                if (ds.getInt(i, p) == DiscreteVariable.MISSING_VALUE) continue K;
            }
            keep.add(i);
        }

        int[] rows = new int[keep.size()];
        for (int i = 0; i < rows.length; i++) rows[i] = keep.get(i);
        DataSet restricted = ds.subsetRows(rows);

        assertEquals(new BDeuScore(restricted).localScore(node, parents),
                new BDeuScore(ds).localScore(node, parents), 1e-10);

        DiscreteBicScore dbFull = new DiscreteBicScore(ds);
        dbFull.setPenaltyDiscount(1.0);
        DiscreteBicScore dbRestricted = new DiscreteBicScore(restricted);
        dbRestricted.setPenaltyDiscount(1.0);

        double full = dbFull.localScore(node, parents);
        assertFalse(Double.isNaN(full));
        assertEquals(dbRestricted.localScore(node, parents), full, 1e-10);
    }

    /**
     * Policy handling: FAIL throws on missing data; EM_COVARIANCE is rejected as continuous-only;
     * MULTIPLE_IMPUTATION is rejected at the single-score level; LISTWISE reduces to the complete rows.
     */
    @Test
    public void testPolicies() {
        DataSet ds = simulate(300, 4, 2, 0.1, new Random(23));

        try {
            new BDeuScore(ds, MissingDataSpec.fail());
            throw new AssertionError("Expected an IllegalArgumentException for FAIL on missing data.");
        } catch (IllegalArgumentException e) {
            // Expected.
        }

        try {
            new DiscreteBicScore(ds, MissingDataSpec.emCovariance());
            throw new AssertionError("Expected an IllegalArgumentException for EM_COVARIANCE on discrete data.");
        } catch (IllegalArgumentException e) {
            // Expected.
        }

        try {
            new BDeuScore(ds, MissingDataSpec.multipleImputation(5));
            throw new AssertionError("Expected an UnsupportedOperationException for MULTIPLE_IMPUTATION.");
        } catch (UnsupportedOperationException e) {
            // Expected.
        }

        int completeRows = 0;

        K:
        for (int i = 0; i < ds.getNumRows(); i++) {
            for (int j = 0; j < ds.getNumColumns(); j++) {
                if (ds.getInt(i, j) == DiscreteVariable.MISSING_VALUE) continue K;
            }
            completeRows++;
        }

        assertEquals(completeRows, new BDeuScore(ds, MissingDataSpec.listwise()).getSampleSize());
    }

    /**
     * Both scores must declare TESTWISE support.
     */
    @Test
    public void testCapabilityDeclarations() {
        DataSet ds = simulate(100, 3, 2, 0.0, new Random(24));

        assertEquals(MissingValueSupport.TESTWISE, new BDeuScore(ds).getMissingValueSupport());
        assertEquals(MissingValueSupport.TESTWISE, new DiscreteBicScore(ds).getMissingValueSupport());
    }

    private static DataSet simulate(int n, int p, int numCategories, double missingRate, Random rand) {
        List<Node> vars = new ArrayList<>();

        for (int j = 0; j < p; j++) {
            vars.add(new DiscreteVariable("D" + j, numCategories));
        }

        DataSet ds = new BoxDataSet(new VerticalIntDataBox(n, p), vars);

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < p; j++) {
                ds.setInt(i, j, rand.nextInt(numCategories));
            }
        }

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < p; j++) {
                if (rand.nextDouble() < missingRate) ds.setInt(i, j, DiscreteVariable.MISSING_VALUE);
            }
        }

        return ds;
    }
}
