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
import edu.cmu.tetrad.data.MissingnessInjector;
import edu.cmu.tetrad.data.missing.MissingDataAudit;
import edu.cmu.tetrad.data.missing.MissingDataSpec;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;

/**
 * Checks that SemBicScore applies the missing-data spec's effective-sample-size mode under test-wise deletion, as it
 * does under EM covariance, and that the full row count is kept for FULL_N and for a null spec.
 */
public class TestSemBicTestwiseEss {

    @Test
    public void minPairwiseModeIsApplied() {
        DataSet data = dataWithMissing();
        MissingDataSpec spec = MissingDataSpec.testwise()
                .withEssMode(MissingDataSpec.EffectiveSampleSizeMode.MIN_PAIRWISE);
        SemBicScore score = new SemBicScore(data, true, spec);

        int expected = new MissingDataAudit(data).getMinPairwiseCount();
        assertEquals(expected, score.getEffectiveSampleSize());

        // Same local scores as setting that sample size by hand.
        SemBicScore manual = new SemBicScore(data, true, MissingDataSpec.testwise());
        manual.setEffectiveSampleSize(expected);
        assertEquals(manual.localScore(2, 0, 1), score.localScore(2, 0, 1), 1e-9);
        assertEquals(manual.localScore(3, 2), score.localScore(3, 2), 1e-9);
    }

    @Test
    public void fullNAndNullSpecKeepTheRowCount() {
        DataSet data = dataWithMissing();
        assertEquals(data.getNumRows(), new SemBicScore(data, true, MissingDataSpec.testwise()
                .withEssMode(MissingDataSpec.EffectiveSampleSizeMode.FULL_N)).getEffectiveSampleSize());
        assertEquals(data.getNumRows(), new SemBicScore(data, true, null).getEffectiveSampleSize());
    }

    /** Four linear Gaussian variables in a chain, with 30% of each deleted completely at random. */
    private static DataSet dataWithMissing() {
        Random random = new Random(5);
        int n = 500;
        List<Node> vars = new ArrayList<>();
        for (int j = 0; j < 4; j++) vars.add(new ContinuousVariable("X" + j));
        double[][] x = new double[n][4];
        for (int i = 0; i < n; i++) {
            x[i][0] = random.nextGaussian();
            for (int j = 1; j < 4; j++) x[i][j] = 0.7 * x[i][j - 1] + random.nextGaussian();
        }
        RandomUtil.getInstance().setSeed(5L);
        return MissingnessInjector.inject(new BoxDataSet(new DoubleDataBox(x), vars),
                MissingnessInjector.Spec.uniform(4, 0.3, 0.0)).data();
    }
}
