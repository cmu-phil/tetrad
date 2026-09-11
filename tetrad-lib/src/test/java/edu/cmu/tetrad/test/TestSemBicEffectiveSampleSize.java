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
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.SemBicScore;
import org.junit.Test;

import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;

/**
 * Tests that an effective sample size set after construction of {@link SemBicScore} is used by the BIC penalty as
 * well as by the likelihood. Previously the log-n term was fixed at construction, so the algcomparison wrappers'
 * effectiveSampleSize parameter changed the likelihood term only.
 *
 * @author josephramsey
 */
public class TestSemBicEffectiveSampleSize {

    /**
     * Constructs a new test.
     */
    public TestSemBicEffectiveSampleSize() {
    }

    /**
     * On complete data, the bump for adding a parent equals twice the likelihood gain minus the penalty discount
     * times log of the effective sample size, both before and after the effective sample size is changed.
     */
    @Test
    public void testPenaltyTracksEffectiveSampleSize() {
        List<Node> vars = List.of(new ContinuousVariable("Y"), new ContinuousVariable("X"));
        int n = 200;
        double[][] d = new double[n][2];
        Random random = new Random(0);

        for (int i = 0; i < n; i++) {
            double x = random.nextGaussian();
            d[i][1] = x;
            d[i][0] = 0.5 * x + random.nextGaussian();
        }

        DataSet data = new BoxDataSet(new DoubleDataBox(d), vars);
        SemBicScore score = new SemBicScore(data, true);
        score.setPenaltyDiscount(2.0);

        for (int nEff : new int[]{n, 50, 1000}) {
            score.setEffectiveSampleSize(nEff);
            assertEquals(nEff, score.getEffectiveSampleSize());

            double bump = score.localScore(0, 1) - score.localScore(0);
            double expected = 2 * (score.getLikelihood(0, new int[]{1}) - score.getLikelihood(0, new int[]{}))
                    - 2.0 * Math.log(nEff);

            assertEquals("Penalty should use log(" + nEff + ")", expected, bump, 1e-8);
        }
    }
}
