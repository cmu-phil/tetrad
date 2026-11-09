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
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.BasisFunctionBicScore;
import edu.cmu.tetrad.search.test.IndTestBasisFunctionBlocks;
import edu.cmu.tetrad.search.utils.Embedding;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Verifies the 2026-8 rank-revealing basis embedding in Embedding.getEmbeddedData. For a
 * continuous variable whose basis block is numerically rank-deficient (e.g., a variable with
 * only a few distinct values, where higher-order polynomial columns are exactly linearly
 * dependent on lower-order ones given an intercept), the previous Householder-QR
 * orthonormalization filled the trailing columns with numerically arbitrary orthonormal vectors
 * unrelated to the data. The embedding now drops such columns: a variable with c distinct values
 * keeps at most c - 1 basis columns, and the first (linear) column is always kept.
 * <p>
 * The block-size tests fail against the pre-fix class (which always emitted truncation-limit
 * columns). The remaining tests guard that full-rank behavior, downstream score equivalence, and
 * downstream test behavior are unchanged.
 */
public class TestEmbeddingRank {

    private static final int LEGENDRE = 1;

    /**
     * A binary-coded (0/1) variable stored as continuous spans only {1, x}: with truncation 3,
     * exactly one basis column should survive. Fails against the pre-2026-8 class, which
     * emitted three columns (two of them numerical junk).
     */
    @Test
    public void testBinaryCodedVariableKeepsOneColumn() {
        Random rng = new Random(62);
        int n = 500;
        double[][] d = new double[n][1];
        for (int i = 0; i < n; i++) d[i][0] = rng.nextDouble() < 0.4 ? 1.0 : 0.0;

        Embedding.EmbeddedData e = Embedding.getEmbeddedData(makeData(d, "X"), 3, LEGENDRE, 1);
        int size = e.embedding().get(0).size();
        System.out.printf("Binary-coded variable, truncation 3: %d embedded column(s)%n", size);
        assertEquals("A two-valued variable should keep exactly one basis column", 1, size);
    }

    /**
     * A three-valued variable (0/1/2) supports polynomials up to degree 2 beyond the intercept:
     * with truncation 4, exactly two basis columns should survive. Fails against the pre-2026-8
     * class, which emitted four.
     */
    @Test
    public void testThreeValuedVariableKeepsTwoColumns() {
        Random rng = new Random(63);
        int n = 600;
        double[][] d = new double[n][1];
        for (int i = 0; i < n; i++) d[i][0] = rng.nextInt(3);

        Embedding.EmbeddedData e = Embedding.getEmbeddedData(makeData(d, "X"), 4, LEGENDRE, 1);
        int size = e.embedding().get(0).size();
        System.out.printf("Three-valued variable, truncation 4: %d embedded column(s)%n", size);
        assertEquals("A three-valued variable should keep exactly two basis columns", 2, size);
    }

    /**
     * A genuinely continuous variable is full rank: all truncation-limit columns are kept, the
     * kept columns are orthonormal, and the first column is proportional to the (scaled) linear
     * term.
     */
    @Test
    public void testContinuousVariableKeepsAllColumns() {
        Random rng = new Random(64);
        int n = 500;
        double[][] d = new double[n][1];
        for (int i = 0; i < n; i++) d[i][0] = rng.nextGaussian();

        DataSet raw = makeData(d, "X");
        Embedding.EmbeddedData e = Embedding.getEmbeddedData(raw, 4, LEGENDRE, 1);
        List<Integer> block = e.embedding().get(0);
        System.out.printf("Continuous variable, truncation 4: %d embedded column(s)%n", block.size());
        assertEquals("A continuous variable should keep all basis columns", 4, block.size());

        DataSet emb = e.embeddedData();

        // Orthonormality of the kept columns.
        for (int a = 0; a < block.size(); a++) {
            for (int b = a; b < block.size(); b++) {
                double dot = 0.0;
                for (int i = 0; i < n; i++) {
                    dot += emb.getDouble(i, block.get(a)) * emb.getDouble(i, block.get(b));
                }
                double expected = (a == b) ? 1.0 : 0.0;
                assertTrue("Embedded columns should be orthonormal; <q" + a + ", q" + b + "> = " + dot,
                        Math.abs(dot - expected) < 1e-8);
            }
        }

        // First column proportional to the scaled linear term: |corr| with the raw variable = 1.
        double[] q1 = new double[n];
        double[] x = new double[n];
        for (int i = 0; i < n; i++) {
            q1[i] = emb.getDouble(i, block.get(0));
            x[i] = raw.getDouble(i, 0);
        }
        double corr = corr(q1, x);
        System.out.printf("|corr(first embedded column, x)| = %.6f%n", Math.abs(corr));
        assertTrue("First embedded column should span the linear term; |corr| = " + Math.abs(corr),
                Math.abs(Math.abs(corr) - 1.0) < 1e-8);
    }

    /**
     * Kept-column naming preserves the original basis orders, so dropped orders are visible as
     * gaps: a binary-coded variable at truncation 3 keeps only "X.P(1)".
     */
    @Test
    public void testKeptColumnNamingPreservesOrders() {
        Random rng = new Random(65);
        int n = 400;
        double[][] d = new double[n][1];
        for (int i = 0; i < n; i++) d[i][0] = rng.nextDouble() < 0.5 ? 1.0 : 0.0;

        Embedding.EmbeddedData e = Embedding.getEmbeddedData(makeData(d, "X"), 3, LEGENDRE, 1);
        List<Integer> block = e.embedding().get(0);
        String name = e.embeddedData().getVariables().get(block.get(0)).getName();
        System.out.printf("Kept column name: %s%n", name);
        assertEquals("The surviving column of a binary-coded variable should be the linear term",
                "X.P(1)", name);
    }

    /**
     * Downstream guard: with a binary-coded variable in the dataset, the BF-BIC score must
     * remain score-equivalent (X -> Y vs Y -> X) and finite. Junk columns previously entered
     * these regressions as noise predictors.
     */
    @Test
    public void testScoreEquivalenceWithDegenerateVariable() {
        Random rng = new Random(66);
        int n = 1200;
        double[][] d = new double[n][2];
        for (int i = 0; i < n; i++) {
            double x = rng.nextDouble() < 0.5 ? 1.0 : 0.0; // binary, coded continuous
            d[i][0] = x;
            d[i][1] = 1.5 * x + rng.nextGaussian();
        }
        DataSet data = makeData(d, "X", "Y");

        BasisFunctionBicScore score = new BasisFunctionBicScore(data, 3, 0.0);
        score.setPenaltyDiscount(2);

        double xToY = score.localScore(0) + score.localScore(1, 0);
        double yToX = score.localScore(1) + score.localScore(0, 1);
        double relDiff = Math.abs(xToY - yToX) / Math.max(1.0, Math.abs(xToY));

        System.out.printf("Degenerate-variable equivalence: score(X->Y) = %.6f, score(Y->X) = %.6f, "
                + "relative difference = %.3g%n", xToY, yToX, relDiff);
        assertTrue("Scores should be finite", Double.isFinite(xToY) && Double.isFinite(yToX));
        assertTrue("Score equivalence should hold with a degenerate variable present; "
                + "relative difference = " + relDiff, relDiff < 1e-6);
    }

    /**
     * Downstream guard: the Blocks test with a binary-coded continuous variable behaves
     * sensibly - dependence on the linear term is detected, and an independent pair is not
     * rejected wildly above nominal.
     */
    @Test
    public void testBlocksTestWithDegenerateVariable() {
        Random rng = new Random(67);
        int n = 1000;

        // Dependent case.
        double[][] d1 = new double[n][2];
        for (int i = 0; i < n; i++) {
            double x = rng.nextDouble() < 0.5 ? 1.0 : 0.0;
            d1[i][0] = x;
            d1[i][1] = 1.5 * x + rng.nextGaussian();
        }
        IndTestBasisFunctionBlocks dep = new IndTestBasisFunctionBlocks(makeData(d1, "X", "Y"), 3, LEGENDRE);
        double pDep = pOf(dep);
        System.out.printf("Degenerate-variable dependence: p = %.4g%n", pDep);
        assertTrue("Dependence on a binary-coded variable should be detected, p = " + pDep,
                pDep < 0.01);

        // Null level.
        int reps = 200, rejects = 0;
        for (int r = 0; r < reps; r++) {
            double[][] d0 = new double[500][2];
            for (int i = 0; i < 500; i++) {
                d0[i][0] = rng.nextDouble() < 0.5 ? 1.0 : 0.0;
                d0[i][1] = rng.nextGaussian();
            }
            IndTestBasisFunctionBlocks nul = new IndTestBasisFunctionBlocks(makeData(d0, "X", "Y"), 3, LEGENDRE);
            if (pOf(nul) < 0.05) rejects++;
        }
        double rate = rejects / (double) reps;
        System.out.printf("Degenerate-variable null: reject@.05 = %.3f%n", rate);
        assertTrue("Null rejection rate should be near nominal, got " + rate,
                rate >= 0.005 && rate <= 0.15);
    }

    // ------------------------------------------------------------------------------------------

    private static DataSet makeData(double[][] d, String... names) {
        List<Node> vars = new ArrayList<>();
        for (String name : names) vars.add(new ContinuousVariable(name));
        return new BoxDataSet(new DoubleDataBox(d), vars);
    }

    private static double pOf(IndTestBasisFunctionBlocks test) {
        DataSet data = (DataSet) test.getData();
        try {
            return test.checkIndependence(data.getVariable("X"), data.getVariable("Y"),
                    new java.util.HashSet<>()).getPValue();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static double corr(double[] a, double[] b) {
        int n = a.length;
        double ma = 0, mb = 0;
        for (int i = 0; i < n; i++) { ma += a[i]; mb += b[i]; }
        ma /= n; mb /= n;
        double sab = 0, sa = 0, sb = 0;
        for (int i = 0; i < n; i++) {
            sab += (a[i] - ma) * (b[i] - mb);
            sa += (a[i] - ma) * (a[i] - ma);
            sb += (b[i] - mb) * (b[i] - mb);
        }
        return sab / Math.sqrt(sa * sb);
    }

    /**
     * Manual runner (the harness is not yet wired into the build).
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        TestEmbeddingRank t = new TestEmbeddingRank();
        t.testBinaryCodedVariableKeepsOneColumn();
        t.testThreeValuedVariableKeepsTwoColumns();
        t.testContinuousVariableKeepsAllColumns();
        t.testKeptColumnNamingPreservesOrders();
        t.testScoreEquivalenceWithDegenerateVariable();
        t.testBlocksTestWithDegenerateVariable();
        System.out.println("ALL TESTS PASSED");
    }
}
