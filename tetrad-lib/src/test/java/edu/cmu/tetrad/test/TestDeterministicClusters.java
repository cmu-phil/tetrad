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
