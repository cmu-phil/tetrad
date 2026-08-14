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
import edu.cmu.tetrad.algcomparison.independence.ConditionalGaussianLrt;
import edu.cmu.tetrad.algcomparison.independence.GSquare;
import edu.cmu.tetrad.algcomparison.independence.SemBicTest;
import edu.cmu.tetrad.algcomparison.score.EbicScore;
import edu.cmu.tetrad.algcomparison.score.GicScores;
import edu.cmu.tetrad.algcomparison.score.PoissonPriorScore;
import edu.cmu.tetrad.algcomparison.score.ZhangShenBoundScore;
import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.data.MixedDataBox;
import edu.cmu.tetrad.data.VerticalIntDataBox;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.test.ChiSquareTest;
import edu.cmu.tetrad.search.test.IndTestChiSquare;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

/**
 * Pins the Phase 2 extensions of missing-data support: the spec wired through SEM BIC Test, native test-wise
 * declarations for CG-LRT and the discrete tests (whose cell-table machinery performs test-wise row skipping), the
 * AD-tree guard on incomplete data, and the wrapper-level EM-covariance route for the covariance-consuming scores.
 *
 * @author josephramsey
 */
public class TestMissingDataPolicyPhase2 {

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

        for (int k = 0; k < 15; k++) {
            columns[rng.nextInt(3)][rng.nextInt(n)] = DiscreteVariable.MISSING_VALUE;
        }

        return new BoxDataSet(new VerticalIntDataBox(columns), vars);
    }

    private static DataSet mixedWithMissing() {
        int n = 300;
        List<Node> vars = new ArrayList<>();
        vars.add(new ContinuousVariable("C1"));
        vars.add(new ContinuousVariable("C2"));
        vars.add(new DiscreteVariable("D1", 3));

        Random rng = new Random(55511L);
        DataSet dataSet = new BoxDataSet(new MixedDataBox(vars, n), vars);

        for (int i = 0; i < n; i++) {
            dataSet.setDouble(i, 0, rng.nextGaussian());
            dataSet.setDouble(i, 1, rng.nextGaussian());
            dataSet.setInt(i, 2, rng.nextInt(3));
        }

        for (int k = 0; k < 10; k++) {
            dataSet.setDouble(rng.nextInt(n), rng.nextInt(2), Double.NaN);
            dataSet.setInt(rng.nextInt(n), 2, DiscreteVariable.MISSING_VALUE);
        }

        return dataSet;
    }

    private static Parameters policy(String policy) {
        Parameters parameters = new Parameters();
        parameters.set(Params.MISSING_DATA_POLICY, policy);
        return parameters;
    }

    /**
     * SEM BIC Test now passes the spec through to the underlying score: testwise and em succeed, default still
     * throws, mi still throws.
     */
    @Test
    public void testSemBicTestSpecWired() {
        DataSet cont = continuousWithMissing();

        assertNotNull(new SemBicTest().getTest(cont, policy("testwise")));
        assertNotNull(new SemBicTest().getTest(cont, policy("em")));
        assertNotNull(new SemBicTest().getTest(cont, policy("listwise")));
        assertThrows(IllegalArgumentException.class, () -> new SemBicTest().getTest(cont, new Parameters()));
        assertThrows(IllegalArgumentException.class, () -> new SemBicTest().getTest(cont, policy("mi")));
    }

    /**
     * Chi-Square and G-Square perform test-wise deletion natively (the count-sample cell table skips rows with the
     * missing code per conditional table): testwise now succeeds; em still throws (no covariance route for discrete
     * tests).
     */
    @Test
    public void testDiscreteTestsNativelyTestwise() {
        DataSet disc = discreteWithMissing();

        assertNotNull(new ChiSquare().getTest(disc, policy("testwise")));
        assertNotNull(new GSquare().getTest(disc, policy("testwise")));
        assertThrows(IllegalArgumentException.class, () -> new ChiSquare().getTest(disc, policy("em")));
        assertThrows(IllegalArgumentException.class, () -> new GSquare().getTest(disc, policy("em")));
    }

    /**
     * CG-LRT performs test-wise deletion natively (each likelihood-ratio calculation intersects its candidate rows
     * with the rows complete on the variables involved): testwise succeeds; em throws.
     */
    @Test
    public void testCgLrtNativelyTestwise() {
        DataSet mixed = mixedWithMissing();

        assertNotNull(new ConditionalGaussianLrt().getTest(mixed, policy("testwise")));
        assertNotNull(new ConditionalGaussianLrt().getTest(mixed, policy("listwise")));
        assertThrows(IllegalArgumentException.class,
                () -> new ConditionalGaussianLrt().getTest(mixed, policy("em")));
        assertThrows(IllegalArgumentException.class,
                () -> new ConditionalGaussianLrt().getTest(mixed, new Parameters()));
    }

    /**
     * The covariance-consuming scores accept the em policy via the wrapper-level EM-covariance route; testwise
     * still throws for them; default still throws.
     */
    @Test
    public void testCovarianceScoresEmRoute() {
        DataSet cont = continuousWithMissing();

        assertNotNull(new EbicScore().getScore(cont, policy("em")));
        assertNotNull(new GicScores().getScore(cont, policy("em")));
        assertNotNull(new PoissonPriorScore().getScore(cont, policy("em")));
        assertNotNull(new ZhangShenBoundScore().getScore(cont, policy("em")));

        assertThrows(IllegalArgumentException.class, () -> new EbicScore().getScore(cont, policy("testwise")));
        assertThrows(IllegalArgumentException.class, () -> new EbicScore().getScore(cont, new Parameters()));

        assertNotNull(new EbicScore().getScore(cont, policy("listwise")));
    }

    /**
     * The EM-route scores actually work: local scores are finite on the EM covariance.
     */
    @Test
    public void testEmRouteScoresAreUsable() {
        DataSet cont = continuousWithMissing();

        edu.cmu.tetrad.search.score.Score score = new EbicScore().getScore(cont, policy("em"));
        double s = score.localScore(0, 1, 2);
        org.junit.Assert.assertTrue(Double.isFinite(s));
    }

    /**
     * The AD-tree cell table does not skip the missing code, so requesting it on incomplete data falls back to the
     * count-sample table; on complete data the request is honored.
     */
    @Test
    public void testAdTreeGuardOnMissingData() {
        DataSet disc = discreteWithMissing();

        IndTestChiSquare test = new IndTestChiSquare(disc, 0.05);
        test.setCellTableType(ChiSquareTest.CellTableType.AD_TREE);
        // The guard is in ChiSquareTest; verify through a fresh ChiSquareTest directly.
        ChiSquareTest direct = new ChiSquareTest(disc, 0.05, ChiSquareTest.TestType.CHI_SQUARE, null);
        direct.setCellTableType(ChiSquareTest.CellTableType.AD_TREE);
        assertEquals(ChiSquareTest.CellTableType.COUNT_SAMPLE, direct.getCellTableType());

        DataSet complete = edu.cmu.tetrad.data.missing.MissingDataUtils.listwiseDelete(disc);
        ChiSquareTest completeCase = new ChiSquareTest(complete, 0.05, ChiSquareTest.TestType.CHI_SQUARE, null);
        completeCase.setCellTableType(ChiSquareTest.CellTableType.AD_TREE);
        assertEquals(ChiSquareTest.CellTableType.AD_TREE, completeCase.getCellTableType());
    }
}
