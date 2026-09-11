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

package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.data.missing.MissingDataSpec;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.SemBicScore;
import org.junit.Test;

import java.util.List;
import java.util.Random;

import static org.junit.Assert.*;

/**
 * Calibration of {@link SemBicScore} under test-wise deletion, where different parent sets are scored on different
 * row subsets. Y is fully observed on 250 rows; A is a candidate parent observed on the first 200 rows and B one
 * observed on the last 20, both equal to the same standard normal Z, with Y = r Z + noise.
 * <p>
 * Before the 2026-9-11 change, the residual variance of Y given a parent was estimated on the parent's rows while
 * the marginal variance of Y was estimated on all rows, and the ratio was scaled by the full n; the sampling noise
 * of the subset variance then dominated, and with r = 0 the 200-row parent A was accepted about a third of the
 * time (the complete-data BIC rate is 2 to 3%) and the 20-row parent B about half the time. The local score now
 * uses the within-subset R-squared and a penalty scaled up for small subsets, and these tests pin the resulting
 * rates. They use fixed seeds and are deterministic.
 *
 * @author josephramsey
 */
public class TestSemBicTestwiseCalibration {

    /**
     * Constructs a new test.
     */
    public TestSemBicTestwiseCalibration() {
    }

    private static DataSet data(double r, long seed, double scale) {
        List<Node> vars = List.of(new ContinuousVariable("Y"), new ContinuousVariable("A"),
                new ContinuousVariable("B"));
        int n = 250;
        double[][] d = new double[n][3];
        Random random = new Random(seed);

        for (int i = 0; i < n; i++) {
            double z = random.nextGaussian();
            d[i][0] = scale * (r * z + random.nextGaussian());
            d[i][1] = i < 200 ? z : Double.NaN;
            d[i][2] = i >= 230 ? z : Double.NaN;
        }

        return new BoxDataSet(new DoubleDataBox(d), vars);
    }

    /**
     * Counts, over replications, how often each parent is accepted (score with the parent exceeds score without).
     *
     * @return {A accepted, B accepted}.
     */
    private static int[] acceptances(double r, int reps) {
        int aAccepted = 0, bAccepted = 0;

        for (int rep = 0; rep < reps; rep++) {
            SemBicScore score = new SemBicScore(data(r, rep, 1.0), true, MissingDataSpec.testwise());
            double s0 = score.localScore(0);
            if (score.localScore(0, 1) > s0) aAccepted++;
            if (score.localScore(0, 2) > s0) bAccepted++;
        }

        return new int[]{aAccepted, bAccepted};
    }

    /**
     * With no true effect, false acceptance of the 200-row parent is near the complete-data BIC rate, and of the
     * 20-row parent is held down by the subset-scaled penalty. (Previously about 65 and 110 of 200.)
     */
    @Test
    public void testNullEffectFalseAcceptance() {
        int[] acc = acceptances(0.0, 200);
        assertTrue("200-row parent falsely accepted " + acc[0] + " of 200", acc[0] <= 12);
        assertTrue("20-row parent falsely accepted " + acc[1] + " of 200", acc[1] <= 40);
    }

    /**
     * With a real effect of r = 0.3, the 200-row parent is nearly always accepted and much more often than the
     * 20-row parent, which rests on far less evidence.
     */
    @Test
    public void testRealEffectPower() {
        int[] acc = acceptances(0.3, 200);
        assertTrue("200-row parent accepted only " + acc[0] + " of 200", acc[0] >= 180);
        assertTrue("20-row parent accepted " + acc[1] + " vs 200-row " + acc[0], acc[1] < acc[0] - 60);
    }

    /**
     * Score differences under test-wise deletion do not depend on the scale of the data.
     */
    @Test
    public void testScaleInvariance() {
        double[] bumps = new double[2];
        double[] scales = {1.0, 100.0};

        for (int s = 0; s < 2; s++) {
            SemBicScore score = new SemBicScore(data(0.3, 0, scales[s]), true, MissingDataSpec.testwise());
            bumps[s] = score.localScore(0, 2) - score.localScore(0);
        }

        assertEquals(bumps[0], bumps[1], 1e-8);
    }
}
