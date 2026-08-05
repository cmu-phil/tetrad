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
import edu.cmu.tetrad.data.missing.ImputationSearch;
import edu.cmu.tetrad.data.missing.MissingDataSpec;
import edu.cmu.tetrad.data.missing.MvnImputer;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Parameters;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests Phase 3: MvnImputer (fills all missing entries, preserves observed entries, varies across imputations,
 * reproduces under a fixed seed) and ImputationSearch (pools per-imputation graphs and recovers structure on MAR
 * data).
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class TestMultipleImputation {

    /**
     * Constructs a new test.
     */
    public TestMultipleImputation() {
    }

    /**
     * The imputer's basic contract.
     */
    @Test
    public void testMvnImputer() {
        DataSet ds = simulateMar(800, 5, new Random(61));
        MissingDataSpec spec = MissingDataSpec.multipleImputation(5).withSeed(7);

        List<DataSet> imp = new MvnImputer(spec).impute(ds, 5, 7);
        assertEquals(5, imp.size());

        boolean differ = false;

        for (int i = 0; i < ds.getNumRows(); i++) {
            for (int j = 0; j < ds.getNumColumns(); j++) {
                double orig = ds.getDouble(i, j);

                for (DataSet c : imp) {
                    assertFalse(Double.isNaN(c.getDouble(i, j)));
                    if (!Double.isNaN(orig)) assertEquals(orig, c.getDouble(i, j), 0.0);
                }

                if (Double.isNaN(orig) && imp.get(0).getDouble(i, j) != imp.get(1).getDouble(i, j)) differ = true;
            }
        }

        assertTrue("Distinct imputations should differ on imputed entries.", differ);

        List<DataSet> imp2 = new MvnImputer(spec).impute(ds, 5, 7);
        assertEquals(imp.get(0).getDouble(3, 2), imp2.get(0).getDouble(3, 2), 0.0);
    }

    /**
     * The pooled search runs FGES over the imputations and recovers the chain skeleton on MAR data, including the
     * adjacency involving the heavily missing variable.
     */
    @Test
    public void testImputationSearch() throws InterruptedException {
        DataSet ds = simulateMar(1200, 5, new Random(62));
        MissingDataSpec spec = MissingDataSpec.multipleImputation(10).withSeed(7);

        var algorithm = new edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.Fges(
                new edu.cmu.tetrad.algcomparison.score.SemBicScore());
        ImputationSearch.Result result = ImputationSearch.search(ds, algorithm, new Parameters(), null, spec);

        assertEquals(10, result.imputationGraphs.size());
        assertEquals(5, result.pooledGraph.getNumNodes());
        assertTrue(result.pooledGraph.isAdjacentTo(result.pooledGraph.getNode("X2"),
                result.pooledGraph.getNode("X3")));
    }

    /**
     * Guard rails: a non-MI spec is rejected; complete data short-circuits to a single run.
     */
    @Test
    public void testGuards() throws InterruptedException {
        DataSet ds = simulateMar(300, 4, new Random(63));
        var algorithm = new edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.Fges(
                new edu.cmu.tetrad.algcomparison.score.SemBicScore());

        try {
            ImputationSearch.search(ds, algorithm, new Parameters(), null, MissingDataSpec.testwise());
            throw new AssertionError("Expected an IllegalArgumentException for a non-MI spec.");
        } catch (IllegalArgumentException e) {
            // Expected.
        }

        DataSet complete = simulateMar(300, 4, new Random(64));
        for (int i = 0; i < complete.getNumRows(); i++) {
            for (int j = 0; j < complete.getNumColumns(); j++) {
                if (Double.isNaN(complete.getDouble(i, j))) complete.setDouble(i, j, 0.0);
            }
        }

        ImputationSearch.Result single = ImputationSearch.search(complete, algorithm, new Parameters(), null, null);
        assertEquals(1, single.imputationGraphs.size());
    }

    private static DataSet simulateMar(int n, int p, Random rand) {
        double[][] d = new double[n][p];

        for (int i = 0; i < n; i++) {
            d[i][0] = rand.nextGaussian();
            for (int j = 1; j < p; j++) d[i][j] = 0.7 * d[i][j - 1] + rand.nextGaussian();
        }

        for (int i = 0; i < n; i++) {
            if (d[i][1] > 0.2) d[i][2] = Double.NaN;
        }

        List<Node> vars = new ArrayList<>();
        for (int j = 0; j < p; j++) vars.add(new ContinuousVariable("X" + (j + 1)));
        return new BoxDataSet(new DoubleDataBox(d), vars);
    }
}
