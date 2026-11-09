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

    /**
     * With y = x^2 + w + e and y partly missing, a linear fit of y on x and w gives x no weight, so donors are
     * matched on w alone and imputed y is unrelated to x; with the predictor degree at 2, imputed y follows x^2.
     * (The third variable matters: with x the only predictor, any nonzero slope makes the fitted value a
     * one-to-one function of x, and matching on it is matching on x, which recovers the curve at degree 1.)
     * Degree 1 set explicitly is the default, value for value.
     */
    @Test
    public void testPredictorDegree() {
        DataSet ds = simulateSquare(2000, new Random(73), 1);

        MiceLiteImputer linear = new MiceLiteImputer();
        DataSet byDefault = linear.impute(ds, 1, 11).get(0);
        linear.setPredictorDegree(1);
        DataSet degreeOne = linear.impute(ds, 1, 11).get(0);

        for (int i = 0; i < ds.getNumRows(); i++) {
            assertEquals(byDefault.getDouble(i, 1), degreeOne.getDouble(i, 1), 0.0);
        }

        MiceLiteImputer quadratic = new MiceLiteImputer();
        quadratic.setPredictorDegree(2);
        DataSet degreeTwo = quadratic.impute(ds, 1, 11).get(0);

        double rLinear = squareCorrelation(ds, byDefault, 1);
        double rQuadratic = squareCorrelation(ds, degreeTwo, 1);

        assertTrue("Linear fit should lose the dependence: " + rLinear, Math.abs(rLinear) < 0.3);
        assertTrue("Quadratic fit should keep the dependence: " + rQuadratic, rQuadratic > 0.9);
    }

    /**
     * The other direction: with y = x^2 + w + e and x partly missing, the mean of x given y and w is zero, so
     * matching on the fitted mean of x alone picks donors on noise (how much of the dependence survives varies
     * from one dataset to the next, so nothing is asserted about it); matching on the fitted mean of x^2 as well
     * gives imputed x the right size for y - w. Degree 1 set explicitly is the default, value for value.
     */
    @Test
    public void testTargetDegree() {
        DataSet ds = simulateSquare(2000, new Random(73), 0);

        MiceLiteImputer mean = new MiceLiteImputer();
        DataSet byDefault = mean.impute(ds, 1, 11).get(0);
        mean.setTargetDegree(1);
        DataSet degreeOne = mean.impute(ds, 1, 11).get(0);

        for (int i = 0; i < ds.getNumRows(); i++) {
            assertEquals(byDefault.getDouble(i, 0), degreeOne.getDouble(i, 0), 0.0);
        }

        MiceLiteImputer moments = new MiceLiteImputer();
        moments.setTargetDegree(2);
        DataSet degreeTwo = moments.impute(ds, 1, 11).get(0);

        double r = squareCorrelation(ds, degreeTwo, 0);
        assertTrue("Matching on two moments should keep the dependence: " + r, r > 0.9);
    }

    /**
     * For data from simulateSquare, the correlation of x^2 with y - w in the completed data, over the rows where
     * the given column was missing in the original. About 0.98 in fully observed rows.
     */
    private static double squareCorrelation(DataSet original, DataSet completed, int missingColumn) {
        List<double[]> pairs = new ArrayList<>();

        for (int i = 0; i < original.getNumRows(); i++) {
            if (!MissingDataAudit.isMissing(original, i, missingColumn)) continue;
            double x = completed.getDouble(i, 0);
            pairs.add(new double[]{x * x, completed.getDouble(i, 1) - completed.getDouble(i, 2)});
        }

        double meanA = 0.0;
        double meanB = 0.0;

        for (double[] pair : pairs) {
            meanA += pair[0] / pairs.size();
            meanB += pair[1] / pairs.size();
        }

        double sab = 0.0;
        double saa = 0.0;
        double sbb = 0.0;

        for (double[] pair : pairs) {
            sab += (pair[0] - meanA) * (pair[1] - meanB);
            saa += (pair[0] - meanA) * (pair[0] - meanA);
            sbb += (pair[1] - meanB) * (pair[1] - meanB);
        }

        return sab / Math.sqrt(saa * sbb);
    }

    /**
     * X and W independent standard normal and Y = X^2 + W + 0.3 e (columns X, Y, W), with 30% of the given
     * column missing completely at random.
     */
    private static DataSet simulateSquare(int n, Random rand, int missingColumn) {
        List<Node> vars = new ArrayList<>();
        vars.add(new ContinuousVariable("X"));
        vars.add(new ContinuousVariable("Y"));
        vars.add(new ContinuousVariable("W"));

        DataSet ds = new BoxDataSet(new MixedDataBox(vars, n), vars);

        for (int i = 0; i < n; i++) {
            double x = rand.nextGaussian();
            double w = rand.nextGaussian();
            ds.setDouble(i, 0, x);
            ds.setDouble(i, 2, w);
            ds.setDouble(i, 1, x * x + w + 0.3 * rand.nextGaussian());
        }

        for (int i = 0; i < n; i++) {
            if (rand.nextDouble() < 0.3) ds.setDouble(i, missingColumn, Double.NaN);
        }

        return ds;
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
