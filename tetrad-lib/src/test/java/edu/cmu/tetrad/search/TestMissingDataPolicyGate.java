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

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.algcomparison.independence.ChiSquare;
import edu.cmu.tetrad.algcomparison.independence.FisherZ;
import edu.cmu.tetrad.algcomparison.score.SemBicScore;
import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.data.VerticalIntDataBox;
import edu.cmu.tetrad.data.missing.MissingDataUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Pins the behavior of the wrapper-level missing-data gate (MissingDataUtils.gate): with missing values present, the
 * "default" policy throws (the user must make an explicit choice), "fail" throws, "listwise" works for every test and
 * score, native policies pass through only for spec-aware wrappers, and complete data pass through untouched under
 * any policy.
 *
 * @author josephramsey
 */
public class TestMissingDataPolicyGate {

    private static DataSet continuousWithMissing() {
        int n = 200;
        List<Node> vars = new ArrayList<>();
        for (int j = 0; j < 4; j++) vars.add(new ContinuousVariable("X" + (j + 1)));

        Random rng = new Random(38472L);
        double[][] data = new double[n][4];

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < 4; j++) {
                data[i][j] = rng.nextGaussian();
            }
        }

        // Scatter missing values in 20 rows.
        for (int k = 0; k < 20; k++) {
            data[rng.nextInt(n)][rng.nextInt(4)] = Double.NaN;
        }

        return new BoxDataSet(new DoubleDataBox(data), vars);
    }

    private static DataSet discreteWithMissing() {
        int n = 200;
        List<Node> vars = new ArrayList<>();
        for (int j = 0; j < 3; j++) vars.add(new DiscreteVariable("D" + (j + 1), 3));

        Random rng = new Random(91827L);
        int[][] columns = new int[3][n];

        for (int j = 0; j < 3; j++) {
            for (int i = 0; i < n; i++) {
                columns[j][i] = rng.nextInt(3);
            }
        }

        // Scatter missing values in 15 cells.
        for (int k = 0; k < 15; k++) {
            columns[rng.nextInt(3)][rng.nextInt(n)] = DiscreteVariable.MISSING_VALUE;
        }

        return new BoxDataSet(new VerticalIntDataBox(columns), vars);
    }

    private static Parameters policy(String policy) {
        Parameters parameters = new Parameters();
        parameters.set(Params.MISSING_DATA_POLICY, policy);
        return parameters;
    }

    /**
     * With missing values and no chosen policy, every wrapper throws, asking the user to choose.
     */
    @Test
    public void testDefaultPolicyThrowsOnMissingData() {
        DataSet cont = continuousWithMissing();
        DataSet disc = discreteWithMissing();

        assertThrows(IllegalArgumentException.class, () -> new FisherZ().getTest(cont, new Parameters()));
        assertThrows(IllegalArgumentException.class, () -> new SemBicScore().getScore(cont, new Parameters()));
        assertThrows(IllegalArgumentException.class, () -> new ChiSquare().getTest(disc, new Parameters()));
    }

    /**
     * The "fail" policy throws by contract.
     */
    @Test
    public void testFailPolicyThrows() {
        DataSet cont = continuousWithMissing();
        assertThrows(IllegalArgumentException.class, () -> new FisherZ().getTest(cont, policy("fail")));
    }

    /**
     * "listwise" works for every wrapper, native or not, and the constructed component sees only complete rows.
     */
    @Test
    public void testListwiseWorksUniversally() {
        DataSet cont = continuousWithMissing();
        DataSet disc = discreteWithMissing();

        IndependenceTest fisherZ = new FisherZ().getTest(cont, policy("listwise"));
        assertNotNull(fisherZ);

        IndependenceTest chiSquare = new ChiSquare().getTest(disc, policy("listwise"));
        assertNotNull(chiSquare);

        assertNotNull(new SemBicScore().getScore(cont, policy("listwise")));

        // The gate itself returns the complete-case dataset.
        DataSet gated = (DataSet) MissingDataUtils.gate(cont, policy("listwise"), false, "test");
        assertTrue(gated.getNumRows() < cont.getNumRows());
        assertTrue(!gated.existsMissingValue());
    }

    /**
     * Native policies pass through for spec-aware wrappers and throw for wrappers without native support.
     */
    @Test
    public void testNativePoliciesRespectSpecAwareness() {
        DataSet cont = continuousWithMissing();
        DataSet disc = discreteWithMissing();

        // Fisher Z and SEM BIC handle testwise and em natively.
        assertNotNull(new FisherZ().getTest(cont, policy("testwise")));
        assertNotNull(new FisherZ().getTest(cont, policy("em")));
        assertNotNull(new SemBicScore().getScore(cont, policy("testwise")));

        // As of Phase 2, Chi Square is natively test-wise (the count-sample cell table skips rows with the missing
        // code per conditional table), but it has no EM-covariance route, so em still throws with a pointer to
        // listwise.
        assertNotNull(new ChiSquare().getTest(disc, policy("testwise")));
        assertThrows(IllegalArgumentException.class, () -> new ChiSquare().getTest(disc, policy("em")));
    }

    /**
     * "mi" is a search-level policy and throws at the wrapper level even for spec-aware wrappers.
     */
    @Test
    public void testMultipleImputationThrowsAtWrapperLevel() {
        DataSet cont = continuousWithMissing();
        assertThrows(IllegalArgumentException.class, () -> new FisherZ().getTest(cont, policy("mi")));
    }

    /**
     * Complete data pass through the gate untouched (same object), regardless of policy, so complete-data users see
     * no change in behavior.
     */
    @Test
    public void testCompleteDataUnaffected() {
        DataSet cont = continuousWithMissing();
        DataSet complete = MissingDataUtils.listwiseDelete(cont);

        assertSame(complete, MissingDataUtils.gate(complete, new Parameters(), false, "test"));
        assertSame(complete, MissingDataUtils.gate(complete, policy("fail"), false, "test"));
        assertSame(complete, MissingDataUtils.gate(complete, policy("testwise"), false, "test"));

        assertNotNull(new FisherZ().getTest(complete, new Parameters()));
    }

    /**
     * An unrecognized policy string throws with the list of legal values.
     */
    @Test
    public void testUnrecognizedPolicyThrows() {
        DataSet cont = continuousWithMissing();
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new FisherZ().getTest(cont, policy("bogus")));
        assertTrue(e.getMessage().contains("listwise"));
    }

    /**
     * The listwise row count matches a direct computation, as a sanity check on the deletion itself.
     */
    @Test
    public void testListwiseRowCount() {
        DataSet cont = continuousWithMissing();

        int complete = 0;
        K:
        for (int i = 0; i < cont.getNumRows(); i++) {
            for (int j = 0; j < cont.getNumColumns(); j++) {
                if (Double.isNaN(cont.getDouble(i, j))) continue K;
            }
            complete++;
        }

        DataSet gated = (DataSet) MissingDataUtils.gate(cont, policy("listwise"), false, "test");
        assertEquals(complete, gated.getNumRows());
    }
}
