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
import edu.cmu.tetrad.data.missing.ImputationSearch;
import edu.cmu.tetrad.data.missing.MiceLiteImputer;
import edu.cmu.tetrad.data.missing.MissingDataAudit;
import edu.cmu.tetrad.data.missing.MissingDataSpec;
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
 * Tests Phase 3b: the chained-PMM imputer for mixed data--fills all missing entries with plausible values (valid
 * category codes for discrete variables), preserves observed entries, varies across imputations, reproduces under
 * a fixed seed--and its use as the default imputer for mixed data in ImputationSearch.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class TestMiceLiteImputer {

    /**
     * Constructs a new test.
     */
    public TestMiceLiteImputer() {
    }

    /**
     * The imputer's basic contract on mixed data.
     */
    @Test
    public void testContract() {
        DataSet ds = simulateMixedMar(600, new Random(71));
        List<DataSet> imp = new MiceLiteImputer().impute(ds, 5, 7);
        assertEquals(5, imp.size());

        boolean differ = false;

        for (int i = 0; i < ds.getNumRows(); i++) {
            for (int j = 0; j < ds.getNumColumns(); j++) {
                boolean wasMissing = MissingDataAudit.isMissing(ds, i, j);
                boolean disc = ds.getVariables().get(j) instanceof DiscreteVariable;

                for (DataSet c : imp) {
                    assertFalse(MissingDataAudit.isMissing(c, i, j));

                    if (disc) {
                        int v = c.getInt(i, j);
                        assertTrue(v >= 0 && v < ((DiscreteVariable) ds.getVariables().get(j)).getNumCategories());
                        if (!wasMissing) assertEquals(ds.getInt(i, j), v);
                    } else if (!wasMissing) {
                        assertEquals(ds.getDouble(i, j), c.getDouble(i, j), 0.0);
                    }
                }

                if (wasMissing && !valuesEqual(imp.get(0), imp.get(1), i, j, disc)) differ = true;
            }
        }

        assertTrue("Distinct imputations should differ on some imputed entries.", differ);

        List<DataSet> imp2 = new MiceLiteImputer().impute(ds, 5, 7);
        for (int i = 0; i < ds.getNumRows(); i++) {
            for (int j = 0; j < ds.getNumColumns(); j++) {
                boolean disc = ds.getVariables().get(j) instanceof DiscreteVariable;
                assertTrue(valuesEqual(imp.get(0), imp2.get(0), i, j, disc));
            }
        }
    }

    /**
     * ImputationSearch on mixed data with no imputer given uses MiceLiteImputer and pools a graph.
     */
    @Test
    public void testDefaultInImputationSearch() throws InterruptedException {
        DataSet ds = simulateMixedMar(600, new Random(72));

        var algorithm = new edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.Fges(
                new edu.cmu.tetrad.algcomparison.score.ConditionalGaussianBicScore());
        ImputationSearch.Result result = ImputationSearch.search(ds, algorithm, new Parameters(), null,
                MissingDataSpec.multipleImputation(5).withSeed(7));

        assertEquals(5, result.imputationGraphs.size());
        assertEquals(ds.getNumColumns(), result.pooledGraph.getNumNodes());
    }

    private static boolean valuesEqual(DataSet a, DataSet b, int i, int j, boolean disc) {
        return disc ? a.getInt(i, j) == b.getInt(i, j) : a.getDouble(i, j) == b.getDouble(i, j);
    }

    private static DataSet simulateMixedMar(int n, Random rand) {
        List<Node> vars = new ArrayList<>();
        vars.add(new ContinuousVariable("C1"));
        vars.add(new ContinuousVariable("C2"));
        vars.add(new DiscreteVariable("D1", 3));
        vars.add(new DiscreteVariable("D2", 2));
        vars.add(new ContinuousVariable("C3"));

        DataSet ds = new BoxDataSet(new MixedDataBox(vars, n), vars);

        for (int i = 0; i < n; i++) {
            double c1 = rand.nextGaussian();
            ds.setDouble(i, 0, c1);
            ds.setDouble(i, 1, 0.7 * c1 + rand.nextGaussian());
            ds.setInt(i, 2, c1 > 0 ? (rand.nextDouble() < 0.7 ? 2 : rand.nextInt(2)) : rand.nextInt(3));
            ds.setInt(i, 3, rand.nextInt(2));
            ds.setDouble(i, 4, 0.7 * ds.getDouble(i, 1) + rand.nextGaussian());
        }

        for (int i = 0; i < n; i++) {

            // MAR: missingness in C2 and D1 depends on observed C1.
            if (ds.getDouble(i, 0) > 0.5) {
                if (rand.nextDouble() < 0.6) ds.setDouble(i, 1, Double.NaN);
                if (rand.nextDouble() < 0.6) ds.setInt(i, 2, DiscreteVariable.MISSING_VALUE);
            }
        }

        return ds;
    }
}
