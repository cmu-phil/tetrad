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

import edu.cmu.tetrad.algcomparison.independence.DegenerateGaussianLrt;
import edu.cmu.tetrad.data.*;
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
 * Checks the EM_COVARIANCE and PAIRWISE_COVARIANCE paths of DG-LRT as the interface builds it: the wrapper
 * accepts both policies on mixed data, the effective-sample-size mode is applied, a strong dependence is found,
 * a true conditional independence is kept, and row subsetting still works.
 */
public class TestDegenerateGaussianLrtEm {

    @Test
    public void emAndPairwiseRunThroughTheWrapper() throws InterruptedException {
        DataSet data = mixedChainWithMissing();
        for (String policy : new String[]{"em", "pairwise"}) {
            IndependenceTest test = new DegenerateGaussianLrt().getTest(data, params(policy, "minPairwise"));

            Node x = data.getVariable("X"), y = data.getVariable("Y"), z = data.getVariable("Z");
            double p = test.checkIndependence(x, y, Set.of()).getPValue();
            assertTrue(policy + " p=" + p, p < 1e-6);
            double q = test.checkIndependence(x, z, Set.of(y)).getPValue();
            assertTrue(policy + " q=" + q, q > 0.01);

            // MIN_PAIRWISE: X and Z are each 30% missing, independently, so about half the rows are complete on both.
            int nEff = ((EffectiveSampleSizeSettable) test).getEffectiveSampleSize();
            assertTrue(policy + " nEff " + nEff, nEff > 400 && nEff < 600);
        }
    }

    @Test
    public void fullNModeUsesTheRowCount() {
        DataSet data = mixedChainWithMissing();
        IndependenceTest test = new DegenerateGaussianLrt().getTest(data, params("em", "fullN"));
        assertEquals(data.getNumRows(), ((EffectiveSampleSizeSettable) test).getEffectiveSampleSize());
    }

    @Test
    public void rowSubsetRebuildsUnderEm() throws InterruptedException {
        DataSet data = mixedChainWithMissing();
        IndependenceTest test = new DegenerateGaussianLrt().getTest(data, params("em", "minPairwise"));
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
        p.set(Params.ALPHA, 0.01);
        p.set(Params.SINGULARITY_LAMBDA, 0.0);
        p.set(Params.EFFECTIVE_SAMPLE_SIZE, -1);
        p.set(Params.MISSING_DATA_POLICY, policy);
        p.set(Params.MISSING_ESS_MODE, essMode);
        return p;
    }

    /**
     * X -> Y -> Z with X continuous, Y a three-category discretization of a linear function of X, and Z
     * continuous depending on Y; 30% of X and of Z deleted completely at random.
     */
    private static DataSet mixedChainWithMissing() {
        Random random = new Random(11);
        int n = 1000;
        List<Node> vars = List.of(new ContinuousVariable("X"), new DiscreteVariable("Y", 3),
                new ContinuousVariable("Z"));
        DataSet data = new BoxDataSet(new MixedDataBox(vars, n), vars);
        for (int i = 0; i < n; i++) {
            double x = random.nextGaussian();
            double u = 0.9 * x + 0.4 * random.nextGaussian();
            int y = u < -0.6 ? 0 : (u < 0.6 ? 1 : 2);
            double z = 0.8 * (y - 1) + 0.5 * random.nextGaussian();
            data.setDouble(i, 0, x);
            data.setInt(i, 1, y);
            data.setDouble(i, 2, z);
        }
        RandomUtil.getInstance().setSeed(11L);
        return MissingnessInjector.inject(data, new MissingnessInjector.Spec(new double[]{0.3, 0.0, 0.3}, 0.0)).data();
    }
}
