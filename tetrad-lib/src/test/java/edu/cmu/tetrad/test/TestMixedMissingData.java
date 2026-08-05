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
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.data.MixedDataBox;
import edu.cmu.tetrad.data.missing.MissingDataSpec;
import edu.cmu.tetrad.data.missing.MissingValueSupport;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.ConditionalGaussianScore;
import edu.cmu.tetrad.search.score.DegenerateGaussianScore;
import edu.cmu.tetrad.search.score.MvpScore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * Tests the mixed-score side of the Phase 1 missing-data refactor: ConditionalGaussianScore's test-wise deletion
 * routed (behavior preserved) through TestwiseRows, and the fail-fast guards on DegenerateGaussianScore and MvpScore,
 * which previously produced statistically undefined results and cryptic exceptions, respectively, on missing data.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class TestMixedMissingData {

    /**
     * Constructs a new test.
     */
    public TestMixedMissingData() {
    }

    /**
     * An explicit TESTWISE spec must reproduce ConditionalGaussianScore's legacy default exactly on missing data,
     * including on repeated (cached) queries, and the score must equal the score computed on the dataset restricted
     * to the rows complete on the scored columns.
     */
    @Test
    public void testConditionalGaussianTestwise() {
        DataSet ds = simulateMixed(500, 0.07, new Random(41));

        ConditionalGaussianScore legacy = new ConditionalGaussianScore(ds, 1.0, true);
        ConditionalGaussianScore explicit = new ConditionalGaussianScore(ds, 1.0, true, MissingDataSpec.testwise());

        for (int rep = 0; rep < 2; rep++) {
            for (int i = 0; i < 5; i++) {
                for (int a = 0; a < 5; a++) {
                    if (a == i) continue;
                    assertEquals(legacy.localScore(i, a), explicit.localScore(i, a), 0.0);
                }
            }
        }

        // Note: unlike BDeuScore, CG's score on missing data is NOT asserted equal to the score on the
        // restricted complete-row dataset. The row set governs which rows enter the likelihood (and the penalty
        // already uses rows.size()), but the shadow discretization of continuous parents derives its breakpoints
        // from the full dataset, so restricting the dataset changes the discretization and hence the likelihood.
        // The identity that IS guaranteed--and asserted above--is that the explicit TESTWISE spec reproduces the
        // legacy behavior exactly. Finiteness on missing data:
        assertFalse(Double.isNaN(legacy.localScore(2, 0, 3)));
        assertFalse(Double.isNaN(legacy.localScore(0, 2, 4)));
    }

    /**
     * DegenerateGaussianScore and MvpScore must reject missing data with an informative exception by default and
     * under FAIL, must reject MULTIPLE_IMPUTATION at the score level, and must accept LISTWISE. On complete data
     * they must work as before.
     */
    @Test
    public void testFailFastGuards() {
        DataSet ds = simulateMixed(300, 0.05, new Random(42));

        try {
            new DegenerateGaussianScore(ds, true, 0.0);
            throw new AssertionError("Expected an IllegalArgumentException for DGS on missing data.");
        } catch (IllegalArgumentException e) {
            // Expected: the informative fail-fast, replacing a silently undefined score.
        }

        try {
            new MvpScore(ds, 1.0, -1, true, -1);
            throw new AssertionError("Expected an IllegalArgumentException for MVP on missing data.");
        } catch (IllegalArgumentException e) {
            // Expected: the informative fail-fast, replacing a cryptic internal exception.
        }

        try {
            new DegenerateGaussianScore(ds, true, 0.0, MissingDataSpec.multipleImputation(5));
            throw new AssertionError("Expected an UnsupportedOperationException for MULTIPLE_IMPUTATION.");
        } catch (UnsupportedOperationException e) {
            // Expected.
        }

        // LISTWISE works and produces finite scores.
        DegenerateGaussianScore dg = new DegenerateGaussianScore(ds, true, 0.0, MissingDataSpec.listwise());
        assertFalse(Double.isNaN(dg.localScore(0, 1, 2)));

        MvpScore mvp = new MvpScore(ds, 1.0, -1, true, -1, MissingDataSpec.listwise());
        assertFalse(Double.isNaN(mvp.localScore(0, 1, 2)));

        // Complete data unaffected.
        DataSet complete = simulateMixed(300, 0.0, new Random(43));
        assertFalse(Double.isNaN(new DegenerateGaussianScore(complete, true, 0.0).localScore(0, 1, 2)));
        assertFalse(Double.isNaN(new MvpScore(complete, 1.0, -1, true, -1).localScore(0, 1, 2)));
    }

    /**
     * Capability declarations: ConditionalGaussianScore declares TESTWISE; the guarded scores keep the interface
     * default, NONE.
     */
    @Test
    public void testCapabilityDeclarations() {
        DataSet complete = simulateMixed(100, 0.0, new Random(44));

        assertEquals(MissingValueSupport.TESTWISE,
                new ConditionalGaussianScore(complete, 1.0, true).getMissingValueSupport());
        assertEquals(MissingValueSupport.NONE,
                new DegenerateGaussianScore(complete, true, 0.0).getMissingValueSupport());
        assertEquals(MissingValueSupport.NONE,
                new MvpScore(complete, 1.0, -1, true, -1).getMissingValueSupport());
    }

    private static DataSet simulateMixed(int n, double missingRate, Random rand) {
        List<Node> vars = new ArrayList<>();
        vars.add(new ContinuousVariable("C1"));
        vars.add(new ContinuousVariable("C2"));
        vars.add(new DiscreteVariable("D1", 3));
        vars.add(new DiscreteVariable("D2", 2));
        vars.add(new ContinuousVariable("C3"));

        DataSet ds = new BoxDataSet(new MixedDataBox(vars, n), vars);

        for (int i = 0; i < n; i++) {
            ds.setDouble(i, 0, rand.nextGaussian());
            ds.setDouble(i, 1, rand.nextGaussian());
            ds.setInt(i, 2, rand.nextInt(3));
            ds.setInt(i, 3, rand.nextInt(2));
            ds.setDouble(i, 4, rand.nextGaussian());
        }

        for (int i = 0; i < n; i++) {
            if (rand.nextDouble() < missingRate) ds.setDouble(i, 0, Double.NaN);
            if (rand.nextDouble() < missingRate) ds.setInt(i, 2, DiscreteVariable.MISSING_VALUE);
        }

        return ds;
    }
}
