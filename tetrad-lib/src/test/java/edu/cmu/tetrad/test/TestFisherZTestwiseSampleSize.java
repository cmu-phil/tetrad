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
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import edu.cmu.tetrad.search.test.IndependenceResult;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Tests that {@link IndTestFisherZ} under test-wise deletion uses, as its sample size, the number of rows complete on
 * x, y and the conditioning set -- the rows the partial correlation is computed from -- rather than the full row
 * count. Previously the df was N - 3 - |z| with N all rows, crediting each test with rows it never saw.
 *
 * @author josephramsey
 */
public class TestFisherZTestwiseSampleSize {

    /**
     * Constructs a new test.
     */
    public TestFisherZTestwiseSampleSize() {
    }

    private static List<Node> vars() {
        List<Node> vars = new ArrayList<>();
        vars.add(new ContinuousVariable("X"));
        vars.add(new ContinuousVariable("Y"));
        vars.add(new ContinuousVariable("Z"));
        return vars;
    }

    /**
     * N = 400 rows; X is observed only on the first 100, Y and Z everywhere. X -> Z -> Y.
     */
    private static double[][] raw() {
        int n = 400;
        double[][] data = new double[n][3];
        Random random = new Random(0);

        for (int i = 0; i < n; i++) {
            double x = random.nextGaussian();
            double z = 0.8 * x + random.nextGaussian();
            double y = 0.8 * z + random.nextGaussian();
            data[i][0] = i < 100 ? x : Double.NaN;
            data[i][1] = y;
            data[i][2] = z;
        }

        return data;
    }

    /**
     * The df of X _||_ Y | Z is m - 3 - 1 with m = 100 complete rows, and the result (r, p, df) matches Fisher Z run
     * on the 100 complete rows alone.
     */
    @Test
    public void dfUsesCompleteRowCount() {
        double[][] data = raw();
        DataSet full = new BoxDataSet(new DoubleDataBox(data), vars());

        double[][] complete = new double[100][];
        System.arraycopy(data, 0, complete, 0, 100);
        DataSet cc = new BoxDataSet(new DoubleDataBox(complete), vars());

        IndTestFisherZ testwise = new IndTestFisherZ(full, 0.05, MissingDataSpec.testwise());
        IndTestFisherZ reference = new IndTestFisherZ(cc, 0.05);

        Node x = full.getVariable("X"), y = full.getVariable("Y"), z = full.getVariable("Z");
        IndTestFisherZ.Result a = testwise.getResult(x, y, Set.of(z));
        IndTestFisherZ.Result b = reference.getResult(cc.getVariable("X"), cc.getVariable("Y"),
                Set.of(cc.getVariable("Z")));

        assertEquals(96.0, a.df(), 0.0);
        assertEquals(b.df(), a.df(), 0.0);
        assertEquals(b.r(), a.r(), 1e-10);
        assertEquals(b.pValue(), a.pValue(), 1e-10);

        // Y _||_ Z, both fully observed: all 400 rows, df = 400 - 3.
        assertEquals(397.0, testwise.getResult(y, z, Set.of()).df(), 0.0);
    }

    /**
     * X and Y never observed together: the test reports dependence (keeping the adjacency) instead of throwing.
     */
    @Test
    public void tooFewCompleteRowsIsDependentNotAnError() {
        int n = 40;
        double[][] data = new double[n][3];
        Random random = new Random(1);

        for (int i = 0; i < n; i++) {
            double z = random.nextGaussian();
            data[i][2] = z;
            data[i][0] = i < n / 2 ? z + random.nextGaussian() : Double.NaN;
            data[i][1] = i < n / 2 ? Double.NaN : z + random.nextGaussian();
        }

        DataSet dataSet = new BoxDataSet(new DoubleDataBox(data), vars());
        IndTestFisherZ test = new IndTestFisherZ(dataSet, 0.05, MissingDataSpec.testwise());

        IndependenceResult result = test.checkIndependence(dataSet.getVariable("X"), dataSet.getVariable("Y"),
                Set.of(dataSet.getVariable("Z")));
        assertFalse(result.isIndependent());
    }
}
