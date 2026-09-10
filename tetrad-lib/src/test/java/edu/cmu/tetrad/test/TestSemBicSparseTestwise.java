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

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.*;

/**
 * Tests that {@link SemBicScore} under test-wise deletion returns NaN, rather than throwing, for a parent set on
 * which too few rows are jointly observed: with no more complete rows than variables the sample covariance is
 * undefined or rank deficient, and previously chooseInverse failed on NaN entries with an IllegalArgumentException
 * that killed the search. Parent sets with enough complete rows still score normally.
 *
 * @author josephramsey
 */
public class TestSemBicSparseTestwise {

    /**
     * Constructs a new test.
     */
    public TestSemBicSparseTestwise() {
    }

    /**
     * X and Y are never observed together; Z is observed with both.
     */
    private static DataSet sparseData() {
        List<Node> vars = new ArrayList<>();
        vars.add(new ContinuousVariable("X"));
        vars.add(new ContinuousVariable("Y"));
        vars.add(new ContinuousVariable("Z"));

        int n = 40;
        double[][] data = new double[n][3];
        Random random = new Random(0);

        for (int i = 0; i < n; i++) {
            double z = random.nextGaussian();
            data[i][2] = z;
            data[i][0] = i < n / 2 ? z + random.nextGaussian() : Double.NaN;
            data[i][1] = i < n / 2 ? Double.NaN : z + random.nextGaussian();
        }

        return new BoxDataSet(new DoubleDataBox(data), vars);
    }

    /**
     * Scoring Z given X alone works; scoring Z given X and Y, which share no complete rows, returns NaN instead of
     * throwing, and is counted.
     */
    @Test
    public void testDisjointParentsScoreNaN() {
        DataSet data = sparseData();
        SemBicScore score = new SemBicScore(data, true, MissingDataSpec.testwise());

        int x = 0, y = 1, z = 2;

        assertFalse(Double.isNaN(score.localScore(z, x)));
        assertFalse(Double.isNaN(score.localScore(z, y)));
        assertEquals(0, score.getNumSingularities());

        double joint = score.localScore(z, x, y);
        assertTrue("Expected NaN for a parent set with no jointly observed rows", Double.isNaN(joint));
        assertEquals(1, score.getNumSingularities());
    }

    /**
     * With as many complete rows as variables (here two rows for two variables) the covariance is rank deficient and
     * the score is NaN; with one more row it is defined.
     */
    @Test
    public void testTooFewRowsScoreNaN() {
        List<Node> vars = new ArrayList<>();
        vars.add(new ContinuousVariable("X"));
        vars.add(new ContinuousVariable("Y"));

        double[][] data = {
                {1.0, 2.0},
                {2.0, 1.5},
                {3.0, Double.NaN},
                {4.0, Double.NaN},
        };

        SemBicScore score = new SemBicScore(new BoxDataSet(new DoubleDataBox(data), vars), true,
                MissingDataSpec.testwise());
        assertTrue(Double.isNaN(score.localScore(0, 1)));

        data[2][1] = 2.5;
        score = new SemBicScore(new BoxDataSet(new DoubleDataBox(data), vars), true, MissingDataSpec.testwise());
        assertFalse(Double.isNaN(score.localScore(0, 1)));
    }
}
