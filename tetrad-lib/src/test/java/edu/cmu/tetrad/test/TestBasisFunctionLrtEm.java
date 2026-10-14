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

import edu.cmu.tetrad.algcomparison.independence.BasisFunctionLrt;
import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.data.MissingnessInjector;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.test.RowsSettable;
import edu.cmu.tetrad.util.EffectiveSampleSizeSettable;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Checks the EM_COVARIANCE path of BF-LRT as the interface builds it: the wrapper accepts the policy, the
 * effective-sample-size mode is applied, a strong dependence is found, and row subsetting still works.
 */
public class TestBasisFunctionLrtEm {

    @Test
    public void emPolicyRunsThroughTheWrapper() throws InterruptedException {
        DataSet data = chainWithMissing();
        IndependenceTest test = new BasisFunctionLrt().getTest(data, params("em", "minPairwise"));

        Node x = data.getVariable("X"), y = data.getVariable("Y"), z = data.getVariable("Z");
        double p = test.checkIndependence(x, y, Set.of()).getPValue();
        assertTrue(p < 1e-6);
        double q = test.checkIndependence(x, z, Set.of(y)).getPValue();
        assertTrue(q >= 0.0 && q <= 1.0);

        // MIN_PAIRWISE: X and Z are each 30% missing, independently, so about half the rows are complete on both.
        int nEff = ((EffectiveSampleSizeSettable) test).getEffectiveSampleSize();
        assertTrue("nEff " + nEff, nEff > 400 && nEff < 600);
    }

    @Test
    public void fullNModeUsesTheRowCount() {
        DataSet data = chainWithMissing();
        IndependenceTest test = new BasisFunctionLrt().getTest(data, params("em", "fullN"));
        assertEquals(data.getNumRows(), ((EffectiveSampleSizeSettable) test).getEffectiveSampleSize());
    }

    @Test
    public void rowSubsetRebuildsUnderEm() throws InterruptedException {
        DataSet data = chainWithMissing();
        IndependenceTest test = new BasisFunctionLrt().getTest(data, params("em", "minPairwise"));
        int full = ((EffectiveSampleSizeSettable) test).getEffectiveSampleSize();

        List<Integer> rows = new ArrayList<>();
        for (int i = 0; i < data.getNumRows(); i += 2) rows.add(i);
        ((RowsSettable) test).setRows(rows);

        assertEquals(Math.round(full / 2.0), ((EffectiveSampleSizeSettable) test).getEffectiveSampleSize(), 1);
        double p = test.checkIndependence(data.getVariable("X"), data.getVariable("Y"), Set.of()).getPValue();
        assertTrue(p < 1e-6);
    }

    private static Parameters params(String policy, String essMode) {
        Parameters p = new Parameters();
        p.set(Params.TRUNCATION_LIMIT, 3);
        p.set(Params.ADAPTIVE_BASIS_SELECTION, false);
        p.set(Params.ALPHA, 0.01);
        p.set(Params.EFFECTIVE_SAMPLE_SIZE, -1);
        p.set(Params.MISSING_DATA_POLICY, policy);
        p.set(Params.MISSING_ESS_MODE, essMode);
        return p;
    }

    /** X -> Y -> Z, linear Gaussian, with 30% of X and of Z deleted completely at random. */
    private static DataSet chainWithMissing() {
        Random random = new Random(11);
        int n = 1000;
        List<Node> vars = List.of(new ContinuousVariable("X"), new ContinuousVariable("Y"),
                new ContinuousVariable("Z"));
        double[][] x = new double[n][3];
        for (int i = 0; i < n; i++) {
            x[i][0] = random.nextGaussian();
            x[i][1] = 0.7 * x[i][0] + 0.5 * random.nextGaussian();
            x[i][2] = 0.8 * x[i][1] + 0.5 * random.nextGaussian();
        }
        RandomUtil.getInstance().setSeed(11L);
        return MissingnessInjector.inject(new BoxDataSet(new DoubleDataBox(x), vars),
                new MissingnessInjector.Spec(new double[]{0.3, 0.0, 0.3}, 0.0)).data();
    }
}
