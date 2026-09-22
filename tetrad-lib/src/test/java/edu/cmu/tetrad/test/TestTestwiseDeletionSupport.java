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

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.data.missing.MissingDataSpec;
import edu.cmu.tetrad.data.missing.MissingDataUtils;
import edu.cmu.tetrad.data.missing.MissingValueSupport;
import edu.cmu.tetrad.data.missing.TestwiseCovariance;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.score.*;
import edu.cmu.tetrad.search.test.IndTestBasisFunctionBlocks;
import edu.cmu.tetrad.search.test.IndTestBasisFunctionLrt;
import edu.cmu.tetrad.search.test.IndTestDegenerateGaussianLrt;
import edu.cmu.tetrad.search.test.Kci;
import edu.cmu.tetrad.search.test.Rcit;
import edu.cmu.tetrad.search.utils.Embedding;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Tests the extension of the TESTWISE missing-data policy to the covariance-consuming scores (BGe, EBIC, GIC,
 * Poisson Prior), the embedding-based scores and tests (DG-BIC, DG-BGe, BF-BIC, BF-BGe, DG-LRT, BF-LRT), and the
 * kernel tests (KCI, RCIT), together with the missingness propagation in {@link Embedding} that the embedding tier
 * depends on. The central identity checked is that a local calculation whose variables are all complete is
 * unchanged by missing values elsewhere in the data, and that a local calculation involving a variable with missing
 * values equals the same calculation on the complete cases (for the components whose statistics are all family-local).
 * <p>
 * These tests fail on a jar built before this change: the embedding read a missing discrete value as the reference
 * category, the spec-taking constructors did not exist, and the wrappers rejected the 'testwise' policy.
 *
 * @author josephramsey
 */
public class TestTestwiseDeletionSupport {

    private static final int N = 400;

    /**
     * Constructs a new test.
     */
    public TestTestwiseDeletionSupport() {
    }

    /**
     * A mixed data set (three continuous, two discrete) in which only the LAST continuous column, C3, has missing
     * values, plus the same data with those entries filled in. Scores and tests not involving C3 must then agree
     * between the two.
     */
    private static DataSet[] mixedWithMissingC3(long seed, double missingRate) {
        Random rand = new Random(seed);
        List<Node> vars = new ArrayList<>();
        vars.add(new ContinuousVariable("C1"));
        vars.add(new ContinuousVariable("C2"));
        vars.add(new DiscreteVariable("D1", 3));
        vars.add(new DiscreteVariable("D2", 2));
        vars.add(new ContinuousVariable("C3"));

        DataSet full = new BoxDataSet(new MixedDataBox(vars, N), vars);

        for (int i = 0; i < N; i++) {
            double c1 = rand.nextGaussian();
            double c2 = 0.6 * c1 + rand.nextGaussian();
            int d1 = rand.nextInt(3);
            int d2 = c2 > 0 ? (rand.nextDouble() < 0.8 ? 1 : 0) : (rand.nextDouble() < 0.2 ? 1 : 0);
            double c3 = 0.5 * c2 + 0.4 * d1 + rand.nextGaussian();
            full.setDouble(i, 0, c1);
            full.setDouble(i, 1, c2);
            full.setInt(i, 2, d1);
            full.setInt(i, 3, d2);
            full.setDouble(i, 4, c3);
        }

        DataSet missing = full.copy();
        for (int i = 0; i < N; i++) {
            if (rand.nextDouble() < missingRate) missing.setDouble(i, 4, Double.NaN);
        }

        return new DataSet[]{full, missing};
    }

    /**
     * Continuous-only data with missing values confined to the last column.
     */
    private static DataSet[] continuousWithMissingLast(long seed, double missingRate) {
        Random rand = new Random(seed);
        List<Node> vars = new ArrayList<>();
        for (int j = 0; j < 4; j++) vars.add(new ContinuousVariable("X" + j));

        DataSet full = new BoxDataSet(new DoubleDataBox(N, 4), vars);
        for (int i = 0; i < N; i++) {
            double x0 = rand.nextGaussian();
            double x1 = 0.7 * x0 + rand.nextGaussian();
            double x2 = 0.5 * x1 + rand.nextGaussian();
            double x3 = 0.6 * x2 - 0.3 * x0 + rand.nextGaussian();
            full.setDouble(i, 0, x0);
            full.setDouble(i, 1, x1);
            full.setDouble(i, 2, x2);
            full.setDouble(i, 3, x3);
        }

        DataSet missing = full.copy();
        for (int i = 0; i < N; i++) {
            if (rand.nextDouble() < missingRate) missing.setDouble(i, 3, Double.NaN);
        }

        return new DataSet[]{full, missing};
    }

    /**
     * The embedding must propagate a missing source entry to NaN in every derived column of that variable, and to
     * nothing else. Before this change, a missing discrete value produced all-zero indicators (the reference
     * category), and a missing continuous value under truncation above 1 poisoned the orthonormalization.
     */
    @Test
    public void testEmbeddingPropagatesMissingness() {
        DataSet[] pair = mixedWithMissingC3(1, 0.1);
        DataSet ds = pair[1].copy();

        // Also knock out some discrete entries.
        ds.setInt(3, 2, DiscreteVariable.MISSING_VALUE);
        ds.setInt(7, 2, DiscreteVariable.MISSING_VALUE);
        ds.setInt(11, 3, DiscreteVariable.MISSING_VALUE);

        for (int trunc : new int[]{1, 3}) {
            Embedding.EmbeddedData emb = Embedding.getEmbeddedData(ds, trunc, 1, 1);
            DataSet e = emb.embeddedData();
            Map<Integer, List<Integer>> map = emb.embedding();

            for (int i = 0; i < N; i++) {
                for (int v = 0; v < ds.getNumColumns(); v++) {
                    boolean missing = ds.getVariable(v) instanceof DiscreteVariable
                            ? ds.getInt(i, v) == DiscreteVariable.MISSING_VALUE
                            : Double.isNaN(ds.getDouble(i, v));

                    for (int col : map.get(v)) {
                        assertEquals("trunc " + trunc + " row " + i + " var " + v + " col " + col,
                                missing, Double.isNaN(e.getDouble(i, col)));
                    }
                }
            }

            // Every indicator column of D1 must be NaN at row 3 (not just one of them).
            for (int col : map.get(2)) assertTrue(Double.isNaN(e.getDouble(3, col)));
        }
    }

    /**
     * TestwiseCovariance: a family's covariance equals the covariance of the complete cases on that family, and its
     * n is their count.
     */
    @Test
    public void testTestwiseCovarianceMatchesCompleteCases() {
        DataSet[] pair = continuousWithMissingLast(2, 0.15);
        DataSet missing = pair[1];
        DataSet listwise = MissingDataUtils.listwiseDelete(missing);

        TestwiseCovariance tw = new TestwiseCovariance(missing.getDoubleData());

        // Family not touching X3: all rows.
        TestwiseCovariance.Family f01 = tw.family(new int[]{0, 1});
        assertEquals(N, f01.n());
        CovarianceMatrix all = new CovarianceMatrix(pair[0]);
        assertEquals(all.getValue(0, 1), f01.cov().get(0, 1), 1e-12);

        // Family touching X3: the complete cases.
        TestwiseCovariance.Family f23 = tw.family(new int[]{2, 3});
        assertEquals(listwise.getNumRows(), f23.n());
        CovarianceMatrix lw = new CovarianceMatrix(listwise);
        assertEquals(lw.getValue(2, 3), f23.cov().get(0, 1), 1e-12);
        assertEquals(lw.getValue(3, 3), f23.cov().get(1, 1), 1e-12);
    }

    /**
     * BGe under TESTWISE: families not involving the missing variable equal the complete-data score; families
     * involving it equal the score on the complete cases (BGe's family statistics are all family-local, so this
     * identity is exact). EM and LISTWISE produce finite scores; FAIL and the no-spec constructor throw.
     */
    @Test
    public void testBgeTestwise() {
        DataSet[] pair = continuousWithMissingLast(3, 0.15);
        DataSet full = pair[0];
        DataSet missing = pair[1];

        BgeScore complete = new BgeScore(full);
        BgeScore testwise = new BgeScore(missing, MissingDataSpec.testwise());
        BgeScore listwise = new BgeScore(MissingDataUtils.listwiseDelete(missing));

        assertEquals(MissingValueSupport.TESTWISE, testwise.getMissingValueSupport());

        // Not involving X3.
        assertEquals(complete.localScore(1, 0), testwise.localScore(1, 0), 1e-9);
        assertEquals(complete.localScore(2, 0, 1), testwise.localScore(2, 0, 1), 1e-9);
        assertEquals(complete.localScore(0), testwise.localScore(0), 1e-9);

        // Involving X3.
        assertEquals(listwise.localScore(3, 2, 0), testwise.localScore(3, 2, 0), 1e-9);
        assertEquals(listwise.localScore(2, 3), testwise.localScore(2, 3), 1e-9);
        assertEquals(listwise.localScore(3), testwise.localScore(3), 1e-9);

        BgeScore em = new BgeScore(missing, MissingDataSpec.emCovariance());
        assertFalse(Double.isNaN(em.localScore(3, 2, 0)));
        assertNotNull(em.getCovariances());

        try {
            new BgeScore(missing);
            fail("Expected a failure on missing data with no spec.");
        } catch (IllegalArgumentException e) {
            // Expected.
        }

        try {
            new BgeScore(missing, MissingDataSpec.fail());
            fail("Expected a failure under FAIL.");
        } catch (IllegalArgumentException e) {
            // Expected.
        }
    }

    /**
     * The embedded scores under TESTWISE: constructed on data whose only missing column is C3, every local score
     * not involving C3 equals the complete-data local score, and every local score is finite. DG-BGe and BF-BGe are
     * family-local, so their C3 families also equal the complete-case scores.
     */
    @Test
    public void testEmbeddedScoresTestwise() throws InterruptedException {
        DataSet[] pair = mixedWithMissingC3(4, 0.12);
        DataSet full = pair[0];
        DataSet missing = pair[1];
        DataSet listwise = MissingDataUtils.listwiseDelete(missing);
        MissingDataSpec tw = MissingDataSpec.testwise();

        // SemBicScore's penalty discount now defaults to 1 in every constructor (it was 0 on complete data and 1
        // on missing data), so no explicit setPenaltyDiscount is needed for these to agree.
        Score[][] triples = new Score[][]{
                {new DegenerateGaussianScore(full, true, 0.0),
                        new DegenerateGaussianScore(missing, true, 0.0, tw),
                        new DegenerateGaussianScore(listwise, true, 0.0)},
                {new DegenerateGaussianBgeScore(full),
                        new DegenerateGaussianBgeScore(missing, tw),
                        new DegenerateGaussianBgeScore(listwise)},
                {new BasisFunctionBicScore(full, 3, 0.0, false, false),
                        new BasisFunctionBicScore(missing, 3, 0.0, false, false, tw),
                        new BasisFunctionBicScore(listwise, 3, 0.0, false, false)},
                {new BasisFunctionBgeScore(full, 3, false),
                        new BasisFunctionBgeScore(missing, 3, false, tw),
                        new BasisFunctionBgeScore(listwise, 3, false)},
        };

        for (Score[] t : triples) {
            String name = t[0].getClass().getSimpleName();
            assertEquals(name, MissingValueSupport.TESTWISE, t[1].getMissingValueSupport());

            // Families without C3 (index 4): identical to complete data. BF-BIC under test-wise scores the raw
            // embedded columns rather than the correlation matrix, so compare score DIFFERENCES for it (the
            // per-node scale constant cancels).
            boolean bfBic = t[0] instanceof BasisFunctionBicScore;
            if (!bfBic) {
                assertEquals(name, t[0].localScore(1, 0), t[1].localScore(1, 0), 1e-8);
                assertEquals(name, t[0].localScore(3, 1, 2), t[1].localScore(3, 1, 2), 1e-8);
                assertEquals(name, t[0].localScore(2, 0), t[1].localScore(2, 0), 1e-8);
            }
            assertEquals(name, t[0].localScoreDiff(0, 1, new int[]{2}), t[1].localScoreDiff(0, 1, new int[]{2}), 1e-8);
            assertEquals(name, t[0].localScoreDiff(2, 3, new int[]{}), t[1].localScoreDiff(2, 3, new int[]{}), 1e-8);

            // Families with C3: finite.
            assertFalse(name, Double.isNaN(t[1].localScore(4, 1, 2)));
            assertFalse(name, Double.isNaN(t[1].localScore(1, 4)));

            // DG-BGe is family-local with a fixed (identity) embedding, so its C3 families equal the complete-case
            // scores exactly. BF-BGe at truncation 3 does NOT satisfy this identity, and is not expected to: each
            // continuous block's orthonormal basis is built over that variable's observed rows (400 for C1 and C2
            // here, 350 in the listwise instance), and BGe's diagonal prior over the derived columns is not
            // invariant to a change of basis within a block. BF-BIC, being basis-invariant, does satisfy the
            // corresponding localScoreDiff identity above.
            if (t[0] instanceof DegenerateGaussianBgeScore) {
                assertEquals(name, t[2].localScore(4, 1, 2), t[1].localScore(4, 1, 2), 1e-8);
                assertEquals(name, t[2].localScore(1, 4), t[1].localScore(1, 4), 1e-8);
            }
        }
    }

    /**
     * The embedded LRT tests and the kernel tests under TESTWISE: tests not involving the missing variable agree
     * exactly with the complete-data test; tests involving it agree with the complete-case test (each test's
     * statistic is computed entirely from the rows it uses).
     */
    @Test
    public void testTestsTestwise() throws InterruptedException {
        DataSet[] pair = mixedWithMissingC3(5, 0.12);
        DataSet full = pair[0];
        DataSet missing = pair[1];
        DataSet listwise = MissingDataUtils.listwiseDelete(missing);
        MissingDataSpec tw = MissingDataSpec.testwise();

        List<Node> v = full.getVariables();

        IndependenceTest[][] triples = new IndependenceTest[][]{
                {new IndTestDegenerateGaussianLrt(full),
                        new IndTestDegenerateGaussianLrt(missing, tw),
                        new IndTestDegenerateGaussianLrt(listwise)},
                {new IndTestBasisFunctionLrt(full, 3, 0.0),
                        new IndTestBasisFunctionLrt(missing, 3, 0.0, tw),
                        new IndTestBasisFunctionLrt(listwise, 3, 0.0)},
                {new IndTestBasisFunctionBlocks(full, 3, 1, false),
                        new IndTestBasisFunctionBlocks(missing, 3, 1, false, tw),
                        new IndTestBasisFunctionBlocks(listwise, 3, 1, false)},
        };

        for (IndependenceTest[] t : triples) {
            String name = t[0].getClass().getSimpleName();
            assertEquals(name, MissingValueSupport.TESTWISE, t[1].getMissingValueSupport());

            // The blocks test is invariant to the within-block basis (which differs between the missing-data
            // and listwise instances for the complete variables) only up to rounding; the covariance LRTs are
            // exact.
            double tol = t[0] instanceof IndTestBasisFunctionBlocks ? 1e-6 : 1e-9;

            double p0 = t[0].checkIndependence(v.get(0), v.get(2), Set.of(v.get(1))).getPValue();
            double p1 = t[1].checkIndependence(v.get(0), v.get(2), Set.of(v.get(1))).getPValue();
            assertEquals(name, p0, p1, tol);

            double q2 = t[2].checkIndependence(v.get(4), v.get(0), Set.of(v.get(1), v.get(2))).getPValue();
            double q1 = t[1].checkIndependence(v.get(4), v.get(0), Set.of(v.get(1), v.get(2))).getPValue();
            assertEquals(name, q2, q1, tol);
        }

        // Kernel tests on the continuous columns only. Both are deterministic (KCI's bandwidth subsample is
        // evenly spaced rather than random; RCIT's random-feature seed no longer depends on the row set), so the
        // identities are exact, both against the complete data and against the listwise subset.
        DataSet[] cpair = continuousWithMissingLast(6, 0.12);
        DataSet cfull = cpair[0];
        DataSet cmissing = cpair[1];
        DataSet clistwise = MissingDataUtils.listwiseDelete(cmissing);
        List<Node> cv = cfull.getVariables();

        List<Integer> completeRows = new ArrayList<>();
        DataSet cfilled = cmissing.copy();
        for (int i = 0; i < cmissing.getNumRows(); i++) {
            if (Double.isNaN(cmissing.getDouble(i, 3))) cfilled.setDouble(i, 3, 0.0);
            else completeRows.add(i);
        }

        Kci kTw = new Kci(cmissing, tw);
        assertEquals(MissingValueSupport.TESTWISE, kTw.getMissingValueSupport());

        double kp0 = new Kci(cfull).checkIndependence(cv.get(0), cv.get(2), Set.of(cv.get(1))).getPValue();
        double kp1 = kTw.checkIndependence(cv.get(0), cv.get(2), Set.of(cv.get(1))).getPValue();
        assertEquals(kp0, kp1, 1e-9);

        double kq2 = new Kci(clistwise).checkIndependence(cv.get(3), cv.get(0), Set.of(cv.get(2))).getPValue();
        double kq1 = kTw.checkIndependence(cv.get(3), cv.get(0), Set.of(cv.get(2))).getPValue();
        assertEquals(kq2, kq1, 1e-9);

        // KCI is deterministic: repeated calls agree exactly.
        assertEquals(kp0, new Kci(cfull).checkIndependence(cv.get(0), cv.get(2), Set.of(cv.get(1))).getPValue(), 0.0);

        // KCI on missing data with no spec throws (previously: silent NaN-to-0 imputation).
        try {
            new Kci(cmissing);
            fail("Expected a failure for KCI on missing data with no spec.");
        } catch (IllegalArgumentException e) {
            // Expected.
        }

        Rcit rFull = new Rcit(cfull);
        Rcit rTw = new Rcit(cmissing, new Parameters(), tw);
        Rcit rLw = new Rcit(clistwise);
        Rcit rRows = new Rcit(cfilled);
        rRows.setRows(completeRows);

        assertEquals(MissingValueSupport.TESTWISE, rTw.getMissingValueSupport());
        assertEquals(rFull.checkIndependence(cv.get(0), cv.get(2), Set.of(cv.get(1))).getPValue(),
                rTw.checkIndependence(cv.get(0), cv.get(2), Set.of(cv.get(1))).getPValue(), 1e-9);
        assertEquals(rRows.checkIndependence(cv.get(3), cv.get(0), Set.of(cv.get(2))).getPValue(),
                rTw.checkIndependence(cv.get(3), cv.get(0), Set.of(cv.get(2))).getPValue(), 1e-9);
        assertEquals(rLw.checkIndependence(cv.get(3), cv.get(0), Set.of(cv.get(2))).getPValue(),
                rTw.checkIndependence(cv.get(3), cv.get(0), Set.of(cv.get(2))).getPValue(), 1e-9);

        // After a test-wise call, the RCIT instance is back on its full active row set.
        assertEquals(rFull.checkIndependence(cv.get(1), cv.get(2), Set.of()).getPValue(),
                rTw.checkIndependence(cv.get(1), cv.get(2), Set.of()).getPValue(), 1e-9);
    }

    /**
     * The algcomparison wrappers must accept the 'testwise' policy (and BGe additionally 'em') on data with
     * missing values, where before they threw. The DG-BIC wrapper is the one py-tetrad and the GUI use for mixed
     * data.
     */
    @Test
    public void testWrappersAcceptTestwise() throws InterruptedException {
        DataSet mixed = mixedWithMissingC3(7, 0.1)[1];
        DataSet cont = continuousWithMissingLast(8, 0.1)[1];

        Parameters testwise = new Parameters();
        testwise.set(Params.MISSING_DATA_POLICY, "testwise");

        Parameters em = new Parameters();
        em.set(Params.MISSING_DATA_POLICY, "em");

        assertFalse(Double.isNaN(new edu.cmu.tetrad.algcomparison.score.BgeScore()
                .getScore(cont, testwise).localScore(3, 2)));
        assertFalse(Double.isNaN(new edu.cmu.tetrad.algcomparison.score.BgeScore()
                .getScore(cont, em).localScore(3, 2)));
        assertFalse(Double.isNaN(new edu.cmu.tetrad.algcomparison.score.EbicScore()
                .getScore(cont, testwise).localScore(3, 2)));
        assertFalse(Double.isNaN(new edu.cmu.tetrad.algcomparison.score.GicScores()
                .getScore(cont, testwise).localScore(3, 2)));
        assertFalse(Double.isNaN(new edu.cmu.tetrad.algcomparison.score.PoissonPriorScore()
                .getScore(cont, testwise).localScore(3, 2)));
        assertFalse(Double.isNaN(new edu.cmu.tetrad.algcomparison.score.DegenerateGaussianBicScore()
                .getScore(mixed, testwise).localScore(4, 1, 2)));
        assertFalse(Double.isNaN(new edu.cmu.tetrad.algcomparison.score.DegenerateGaussianBgeScore()
                .getScore(mixed, testwise).localScore(4, 1, 2)));
        assertFalse(Double.isNaN(new edu.cmu.tetrad.algcomparison.score.BasisFunctionBicScore()
                .getScore(mixed, testwise).localScore(4, 1, 2)));
        assertFalse(Double.isNaN(new edu.cmu.tetrad.algcomparison.score.BasisFunctionBgeScore()
                .getScore(mixed, testwise).localScore(4, 1, 2)));

        List<Node> mv = mixed.getVariables();
        List<Node> cvars = cont.getVariables();
        assertNotNull(new edu.cmu.tetrad.algcomparison.independence.DegenerateGaussianLrt()
                .getTest(mixed, testwise).checkIndependence(mv.get(4), mv.get(0), Set.of(mv.get(1))));
        assertNotNull(new edu.cmu.tetrad.algcomparison.independence.BasisFunctionLrt()
                .getTest(mixed, testwise).checkIndependence(mv.get(4), mv.get(0), Set.of(mv.get(1))));
        assertNotNull(new edu.cmu.tetrad.algcomparison.independence.Kci()
                .getTest(cont, testwise));
        assertNotNull(new edu.cmu.tetrad.algcomparison.independence.Rcit()
                .getTest(cont, testwise));
        assertNotNull(new edu.cmu.tetrad.algcomparison.independence.CciTest()
                .getTest(cont, testwise));
        assertNotNull(new edu.cmu.tetrad.algcomparison.independence.Gcm()
                .getTest(cont, testwise));
        assertNotNull(cvars);
    }
}
