package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.data.audit.DeterministicClusters;
import edu.cmu.tetrad.graph.Node;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests the audit-side deterministic-equation fitter. Data: X0, X1 iid standard normal; X2 = 1.5*X0 + 2*X1 + 3
 * exactly (the derived variable); X3 = X2 + noise; X4 = X0 + noise.
 */
public class TestDeterministicClusters {

    private static DataSet data() {
        int n = 500;
        Random rng = new Random(7);
        double[][] d = new double[n][5];
        for (int r = 0; r < n; r++) {
            double x0 = rng.nextGaussian();
            double x1 = rng.nextGaussian();
            double x2 = 1.5 * x0 + 2.0 * x1 + 3.0;     // derived, exactly, with intercept
            double x3 = x2 + rng.nextGaussian();
            double x4 = x0 + rng.nextGaussian();
            d[r][0] = x0;
            d[r][1] = x1;
            d[r][2] = x2;
            d[r][3] = x3;
            d[r][4] = x4;
        }
        List<Node> vars = new ArrayList<>();
        for (int j = 0; j < 5; j++) vars.add(new ContinuousVariable("X" + j));
        return new BoxDataSet(new DoubleDataBox(d), vars);
    }

    @Test
    public void testFindReportsTheOneConstraintWithItsCluster() {
        List<DeterministicClusters.Constraint> constraints = DeterministicClusters.find(data(), 1e-8);
        assertEquals("Expected exactly one deterministic constraint", 1, constraints.size());
        DeterministicClusters.Constraint c = constraints.get(0);
        List<String> cluster = c.cluster();
        assertTrue("Cluster should be {X0, X1, X2}, got " + cluster,
                cluster.size() == 3 && cluster.contains("X0") && cluster.contains("X1") && cluster.contains("X2"));
        assertTrue("Residual fraction should be at rounding scale", c.fractionResidual() < 1e-8);
    }

    @Test
    public void testConstraintForRecoversCoefficientsInterceptAndPrunesSupport() {
        DeterministicClusters.Constraint c = DeterministicClusters.constraintFor(data(), "X2", 1e-8);
        assertNotNull(c);
        assertTrue("Residual fraction should be at rounding scale, got " + c.fractionResidual(),
                c.fractionResidual() < 1e-8);
        // Support must prune to exactly {X0, X1}: X3 and X4 are not needed to stay exact.
        assertEquals("Support should prune to the two true determiners, got " + c.support(),
                2, c.support().size());
        assertTrue(c.support().contains("X0") && c.support().contains("X1"));
        for (int k = 0; k < c.support().size(); k++) {
            double expected = c.support().get(k).equals("X0") ? 1.5 : 2.0;
            assertEquals(expected, c.coefficients()[k], 1e-6);
        }
        assertEquals(3.0, c.intercept(), 1e-6);
        assertTrue("Equation should start with X2 =, got " + c.equation(), c.equation().startsWith("X2 ="));
    }

    @Test
    public void testWideDataWithManyDeterminismsIsFastAndExact() {
        // Rat-brain-shaped stress case: 100 base variables plus 24 aggregate variables, each an exact
        // positive-weight sum over a disjoint triple of base variables. The previous fitter (per-call covariance
        // rebuild, greedy backward elimination) took minutes here; the shared-precision fitter must do all 24
        // leave-one-out equations, the retained-form batch, and find() well under the bound.
        int n = 300, pBase = 100, k = 24;
        Random rng = new Random(11);
        double[][] d = new double[n][pBase + k];
        for (int r = 0; r < n; r++) {
            for (int j = 0; j < pBase; j++) d[r][j] = rng.nextGaussian();
        }
        int[][] triples = new int[k][3];
        double[][] weights = new double[k][3];
        for (int t = 0; t < k; t++) {
            for (int m = 0; m < 3; m++) {
                triples[t][m] = 3 * t + m;                       // disjoint triples
                weights[t][m] = 1.0 + 2.0 * rng.nextDouble();
            }
            for (int r = 0; r < n; r++) {
                double s = 0.0;
                for (int m = 0; m < 3; m++) s += weights[t][m] * d[r][triples[t][m]];
                d[r][pBase + t] = s;
            }
        }
        List<Node> vars = new ArrayList<>();
        for (int j = 0; j < pBase + k; j++) vars.add(new ContinuousVariable("X" + j));
        DataSet wide = new BoxDataSet(new DoubleDataBox(d), vars);

        long start = System.currentTimeMillis();

        DeterministicClusters.Fitter fitter = new DeterministicClusters.Fitter(wide, 1e-8);
        for (int t = 0; t < k; t++) {
            DeterministicClusters.Constraint c = fitter.leaveOneOut("X" + (pBase + t));
            assertNotNull(c);
            assertTrue("Aggregate " + t + " should be exact, frac " + c.fractionResidual(),
                    c.fractionResidual() < 1e-8);
            assertEquals("Aggregate " + t + " should keep exactly its three bases, got " + c.support(),
                    3, c.support().size());
            for (int m = 0; m < 3; m++) {
                int pos = c.support().indexOf("X" + triples[t][m]);
                assertTrue(pos >= 0);
                assertEquals(weights[t][m], c.coefficients()[pos], 1e-6);
            }
        }

        // Retained-form batch: all aggregates removed at once, written in terms of the base variables.
        List<String> targets = new ArrayList<>();
        for (int t = 0; t < k; t++) targets.add("X" + (pBase + t));
        List<String> retainedNames = new ArrayList<>();
        for (int j = 0; j < pBase; j++) retainedNames.add("X" + j);
        List<DeterministicClusters.Constraint> batch = fitter.onCommonSupport(targets, retainedNames);
        for (int t = 0; t < k; t++) {
            assertNotNull(batch.get(t));
            assertTrue(batch.get(t).fractionResidual() < 1e-8);
            assertEquals(3, batch.get(t).support().size());
        }

        // find() reports one constraint per independent deterministic relation.
        assertEquals(k, DeterministicClusters.find(wide, 1e-8).size());

        long elapsed = System.currentTimeMillis() - start;
        assertTrue("Wide case took " + elapsed + " ms; the shared-precision fitter should finish in seconds",
                elapsed < 20_000);
    }

    @Test
    public void testRetainedFormEquationWhenAnotherClusterMemberIsAlsoRemoved() {
        // Regressing X2 on a dataset from which X1 has been removed: the fit can no longer be exact, and the
        // equation must be labeled by its residual fraction rather than claimed exact. This is the
        // several-removals-at-once case of the audit flow.
        DataSet data = data();
        List<Node> keep = new ArrayList<>();
        for (Node node : data.getVariables()) {
            if (!node.getName().equals("X1")) keep.add(node);
        }
        DeterministicClusters.Constraint c =
                DeterministicClusters.constraintFor(data.subsetColumns(keep), "X2", 1e-8);
        assertNotNull(c);
        assertTrue("Without X1 the relation cannot be exact", c.fractionResidual() > 1e-8);
    }
}
