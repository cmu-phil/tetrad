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

package edu.cmu.tetradapp.model.datamanip;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Parameters;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Regression tests for the Data Subset box not re-subsetting after an upstream change.
 * <p>
 * Before the fix, {@code DataSubsetModel} ignored its parent entirely and reinstalled the {@code DataSet} that the
 * editor had baked into the parameters. So after removing a variable upstream and propagating, the subset still
 * contained the removed variable (and the old rows). These tests build the parameters exactly as the editor writes
 * them - both the spec and the baked subset - then hand the model a *different* parent and check that the result
 * follows the parent, not the baked copy.
 */
public class TestDataSubsetModelRecompute {

    private static DataSet continuous(String[] names, double[][] rows) {
        List<Node> vars = new ArrayList<>();
        for (String n : names) vars.add(new ContinuousVariable(n));
        DataSet d = new BoxDataSet(new DoubleDataBox(rows.length, names.length), vars);
        for (int i = 0; i < rows.length; i++)
            for (int j = 0; j < names.length; j++)
                d.setDouble(i, j, rows[i][j]);
        return d;
    }

    private static DataSet original() {
        return continuous(new String[]{"X1", "X2", "X3", "X4"}, new double[][]{
                {1, 10, 100, 1000},
                {2, 20, 200, 2000},
                {3, 30, 300, 3000},
                {4, 40, 400, 4000},
                {5, 50, 500, 5000},
        });
    }

    /**
     * The same data with X3 removed and one fewer row, as if the user re-loaded upstream.
     */
    private static DataSet withX3Removed() {
        return continuous(new String[]{"X1", "X2", "X4"}, new double[][]{
                {1, 10, 1000},
                {2, 20, 2000},
                {3, 30, 3000},
                {4, 40, 4000},
        });
    }

    /**
     * Writes parameters the way DataSubsetParamsEditor.finalizeEdit() does: the spec, plus the baked subset.
     */
    private static Parameters editorParams(DataSet editedAgainst, DataSubsetter.Spec spec) {
        Parameters params = new Parameters();
        spec.storeIn(params);
        params.set(DataSubsetter.KEY_LEGACY_SUBSET, DataSubsetter.subset(editedAgainst, spec));
        return params;
    }

    @Test
    public void testRemovedVariableIsDroppedOnRecompute() {
        DataSubsetter.Spec spec = new DataSubsetter.Spec(Arrays.asList("X1", "X3", "X4"), "", "",
                DataSubsetter.SamplingMode.USE_AS_IS, null, "");
        Parameters params = editorParams(original(), spec);

        // Sanity: the baked subset still has X3.
        assertNotNull(((DataSet) params.get(DataSubsetter.KEY_LEGACY_SUBSET)).getVariable("X3"));

        DataSet subset = DataSubsetModel.computeSubset(withX3Removed(), params);

        // The bug: previously this returned the baked subset with X3 and 5 rows.
        assertNull("X3 was removed upstream and must not reappear", subset.getVariable("X3"));
        assertEquals(Arrays.asList("X1", "X4"), subset.getVariableNames());
        assertEquals(4, subset.getNumRows());
        assertEquals(4000.0, subset.getDouble(3, 1), 0.0);
    }

    @Test
    public void testNewRowsUpstreamAreReflected() {
        DataSubsetter.Spec spec = new DataSubsetter.Spec(null, "1-3", "X1 > 1",
                DataSubsetter.SamplingMode.USE_AS_IS, null, "");
        Parameters params = editorParams(original(), spec);

        DataSet reloaded = continuous(new String[]{"X1", "X2", "X3", "X4"}, new double[][]{
                {7, 10, 100, 1000},
                {8, 20, 200, 2000},
                {9, 30, 300, 3000},
        });

        DataSet subset = DataSubsetModel.computeSubset(reloaded, params);
        assertEquals(3, subset.getNumRows());
        assertEquals(7.0, subset.getDouble(0, 0), 0.0);
    }

    @Test
    public void testConditionOnRemovedVariableFailsLoudly() {
        DataSubsetter.Spec spec = new DataSubsetter.Spec(null, "", "X3 > 150",
                DataSubsetter.SamplingMode.USE_AS_IS, null, "");
        Parameters params = editorParams(original(), spec);

        try {
            DataSubsetModel.computeSubset(withX3Removed(), params);
            fail("A condition on a variable that no longer exists must not be silently dropped");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("X3"));
        }
    }

    @Test
    public void testAllSelectedVariablesGoneFailsLoudly() {
        DataSubsetter.Spec spec = new DataSubsetter.Spec(List.of("X3"), "", "",
                DataSubsetter.SamplingMode.USE_AS_IS, null, "");
        Parameters params = editorParams(original(), spec);

        try {
            DataSubsetModel.computeSubset(withX3Removed(), params);
            fail("Must not silently fall back to all variables");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("X3"));
        }
    }

    @Test
    public void testLegacyParamsWithoutSpecStillWork() {
        Parameters params = new Parameters();
        DataSet baked = DataSubsetter.subset(original(),
                new DataSubsetter.Spec(List.of("X2"), "", "", null, null, null));
        params.set(DataSubsetter.KEY_LEGACY_SUBSET, baked);

        DataSet subset = DataSubsetModel.computeSubset(withX3Removed(), params);
        assertSame(baked, subset);
    }

    @Test
    public void testNoSpecAndNoLegacyThrows() {
        try {
            DataSubsetModel.computeSubset(original(), new Parameters());
            fail();
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    @Test
    public void testSeedMakesBootstrapReproducible() {
        DataSubsetter.Spec seeded = new DataSubsetter.Spec(null, "", "",
                DataSubsetter.SamplingMode.BOOTSTRAP, 20, "12345");

        DataSet a = DataSubsetter.subset(original(), seeded);
        DataSet b = DataSubsetter.subset(original(), seeded);

        assertEquals(20, a.getNumRows());
        for (int i = 0; i < 20; i++) {
            assertEquals(a.getDouble(i, 0), b.getDouble(i, 0), 0.0);
        }

        DataSubsetter.Spec otherSeed = new DataSubsetter.Spec(null, "", "",
                DataSubsetter.SamplingMode.BOOTSTRAP, 20, "54321");
        DataSet c = DataSubsetter.subset(original(), otherSeed);
        boolean differs = false;
        for (int i = 0; i < 20 && !differs; i++) {
            differs = a.getDouble(i, 0) != c.getDouble(i, 0);
        }
        assertTrue("Different seeds should (almost surely) give different draws", differs);
    }

    @Test
    public void testSpecRoundTripsThroughParameters() {
        DataSubsetter.Spec spec = new DataSubsetter.Spec(Arrays.asList("X4", "X1"), "2-4", "X2 >= 20",
                DataSubsetter.SamplingMode.SUBSAMPLE, 2, "7");
        Parameters params = new Parameters();
        spec.storeIn(params);

        DataSubsetter.Spec back = DataSubsetter.Spec.fromParameters(params);
        assertNotNull(back);
        assertEquals(spec, back);

        assertNull(DataSubsetter.Spec.fromParameters(new Parameters()));
    }
}
