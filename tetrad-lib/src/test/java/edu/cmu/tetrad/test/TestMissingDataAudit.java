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
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.data.missing.MissingDataAudit;
import edu.cmu.tetrad.data.missing.MissingDataPolicy;
import edu.cmu.tetrad.data.missing.MissingDataSpec;
import edu.cmu.tetrad.data.missing.MissingValueSupport;
import edu.cmu.tetrad.graph.Node;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests the Phase 0 missing-data classes: the audit's descriptive statistics, Little's MCAR test (should not reject
 * under MCAR, should reject under MAR), the spec's validation, and the default capability declaration on scores.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class TestMissingDataAudit {

    /**
     * Constructs a new test.
     */
    public TestMissingDataAudit() {
    }

    /**
     * The audit's counts should be exactly right on a small dataset with known missingness.
     */
    @Test
    public void testDescriptiveStatistics() {
        double nan = Double.NaN;

        double[][] data = {
                {1.0, 2.0, 3.0},
                {1.0, nan, 3.0},
                {nan, nan, 3.0},
                {1.0, 2.0, 3.0}
        };

        MissingDataAudit audit = new MissingDataAudit(continuousDataSet(data, 3));

        assertTrue(audit.anyMissing());
        assertEquals(1, audit.getMissingCount(0));
        assertEquals(2, audit.getMissingCount(1));
        assertEquals(0, audit.getMissingCount(2));
        assertEquals(2, audit.getNumCompleteRows());
        assertEquals(3, audit.getNumPatterns());
        assertEquals(3.0 / 12.0, audit.getOverallMissingRate(), 1e-12);

        int[][] pairwise = audit.getPairwiseCompleteCounts();
        assertEquals(2, pairwise[0][1]); // rows 0 and 3
        assertEquals(3, pairwise[0][2]); // rows 0, 1, 3
        assertEquals(2, pairwise[1][2]); // rows 0 and 3
        assertEquals(2, audit.getMinPairwiseCount());
    }

    /**
     * Under MCAR, Little's test should not reject at alpha = 0.001; under strong MAR, it should reject decisively.
     * (The alpha is deliberately loose so this test is stable across seeds.)
     */
    @Test
    public void testLittlesMcarTest() {
        Random rand = new Random(42);
        int n = 2000, p = 4;

        double[][] complete = new double[n][p];

        for (int i = 0; i < n; i++) {
            complete[i][0] = rand.nextGaussian();

            for (int j = 1; j < p; j++) {
                complete[i][j] = 0.6 * complete[i][j - 1] + rand.nextGaussian();
            }
        }

        double[][] mcar = deepCopy(complete);

        for (int i = 0; i < n; i++) {
            for (int j = 2; j < p; j++) {
                if (rand.nextDouble() < 0.15) mcar[i][j] = Double.NaN;
            }
        }

        double[][] mar = deepCopy(complete);

        for (int i = 0; i < n; i++) {
            if (mar[i][1] > 0.3) mar[i][2] = Double.NaN;
        }

        MissingDataAudit.LittleResult mcarResult
                = new MissingDataAudit(continuousDataSet(mcar, p)).littlesMcarTest();
        MissingDataAudit.LittleResult marResult
                = new MissingDataAudit(continuousDataSet(mar, p)).littlesMcarTest();

        assertTrue("MCAR data should not be rejected as MCAR: p = " + mcarResult.pValue,
                mcarResult.pValue > 0.001);
        assertTrue("MAR data should be rejected as MCAR: p = " + marResult.pValue,
                marResult.pValue < 0.001);
    }

    /**
     * The static isMissing helper should use NaN for continuous variables and the sentinel for discrete variables.
     */
    @Test
    public void testIsMissingConventions() {
        List<Node> vars = new ArrayList<>();
        vars.add(new ContinuousVariable("X1"));

        double[][] data = {{Double.NaN}, {1.0}};
        DataSet continuous = new BoxDataSet(new DoubleDataBox(data), vars);

        assertTrue(MissingDataAudit.isMissing(continuous, 0, 0));
        assertFalse(MissingDataAudit.isMissing(continuous, 1, 0));

        List<Node> dVars = new ArrayList<>();
        dVars.add(new DiscreteVariable("D1", 3));

        DataSet discrete = new BoxDataSet(new DoubleDataBox(new double[][]{{1}, {1}}), dVars);
        discrete.setInt(0, 0, DiscreteVariable.MISSING_VALUE);
        discrete.setInt(1, 0, 1);

        assertTrue(MissingDataAudit.isMissing(discrete, 0, 0));
        assertFalse(MissingDataAudit.isMissing(discrete, 1, 0));
    }

    /**
     * The spec should validate its parameters and carry them through withers.
     */
    @Test
    public void testSpecValidationAndWithers() {
        MissingDataSpec spec = MissingDataSpec.emCovariance()
                .withEmRidge(1e-4)
                .withEssMode(MissingDataSpec.EffectiveSampleSizeMode.MIN_PAIRWISE);

        assertEquals(MissingDataPolicy.EM_COVARIANCE, spec.getPolicy());
        assertEquals(1e-4, spec.getEmRidge(), 0.0);
        assertEquals(MissingDataSpec.EffectiveSampleSizeMode.MIN_PAIRWISE, spec.getEssMode());

        assertEquals(20, MissingDataSpec.multipleImputation(20).getNumImputations());

        try {
            MissingDataSpec.multipleImputation(1);
            throw new AssertionError("Expected an IllegalArgumentException for m = 1.");
        } catch (IllegalArgumentException e) {
            // Expected.
        }

        try {
            MissingDataSpec.emCovariance().withEmRidge(-1.0);
            throw new AssertionError("Expected an IllegalArgumentException for a negative ridge.");
        } catch (IllegalArgumentException e) {
            // Expected.
        }
    }

    /**
     * Scores that have not declared missing-value support should report NONE by default. (Note: this must be tested
     * with a score that does not override getMissingValueSupport(); SemBicScore, used here originally, declares
     * TESTWISE as of the Phase 1 refactor and is covered by TestMissingDataPhase1.testCapabilityDeclarations.)
     */
    @Test
    public void testDefaultMissingValueSupport() {
        edu.cmu.tetrad.search.score.Score undeclared = new edu.cmu.tetrad.search.score.Score() {
            @Override
            public double localScore(int node, int... parents) {
                return 0.0;
            }

            @Override
            public List<Node> getVariables() {
                return new ArrayList<>();
            }

            @Override
            public int getSampleSize() {
                return 0;
            }
        };

        assertEquals(MissingValueSupport.NONE, undeclared.getMissingValueSupport());
    }

    private static double[][] deepCopy(double[][] a) {
        double[][] b = new double[a.length][];
        for (int i = 0; i < a.length; i++) b[i] = a[i].clone();
        return b;
    }

    private static DataSet continuousDataSet(double[][] data, int p) {
        List<Node> vars = new ArrayList<>();

        for (int j = 0; j < p; j++) {
            vars.add(new ContinuousVariable("X" + (j + 1)));
        }

        return new BoxDataSet(new DoubleDataBox(data), vars);
    }
}
