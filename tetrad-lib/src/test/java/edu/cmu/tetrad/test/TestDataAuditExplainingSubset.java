package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.data.audit.AuditFinding;
import edu.cmu.tetrad.data.audit.DataAudit;
import edu.cmu.tetrad.data.audit.FindingCode;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Matrix;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Regression pins for the explaining-subset localization of the NEAR_DETERMINISM_CONTINUOUS finding in
 * {@link DataAudit}: the finding message must name a small predictor subset accounting for the dependence, rather
 * than only reporting that the variable is nearly a linear function of all of the others.
 */
public class TestDataAuditExplainingSubset {

    private static DataSet make(int n, int p, long seed, DataFiller filler) {
        Random rng = new Random(seed);
        List<Node> vars = new ArrayList<>();
        for (int j = 0; j < p; j++) vars.add(new ContinuousVariable("X" + (j + 1)));
        DataSet d = new BoxDataSet(new DoubleDataBox(n, p), vars);
        for (int i = 0; i < n; i++) filler.fill(d, i, rng);
        return d;
    }

    private interface DataFiller {
        void fill(DataSet d, int row, Random rng);
    }

    private static AuditFinding findingFor(DataSet d, String var) {
        for (AuditFinding f : new DataAudit(d).getFindings(FindingCode.NEAR_DETERMINISM_CONTINUOUS)) {
            if (f.getVariables().contains(var)) return f;
        }
        return null;
    }

    /**
     * X3 = X1 + X2 + small noise among five irrelevant variables: the finding for X3 must name X1 and X2 in the
     * subset, report a subset R^2 in the values map, and not name any irrelevant variable.
     */
    @Test
    public void testSubsetNamesTheDependentTriple() {
        DataSet d = make(500, 8, 42, (ds, i, rng) -> {
            double x1 = rng.nextGaussian(), x2 = rng.nextGaussian();
            ds.setDouble(i, 0, x1);
            ds.setDouble(i, 1, x2);
            ds.setDouble(i, 2, x1 + x2 + 0.05 * rng.nextGaussian());
            for (int j = 3; j < 8; j++) ds.setDouble(i, j, rng.nextGaussian());
        });

        AuditFinding f = findingFor(d, "X3");
        assertNotNull("X3 should be flagged as nearly linear in the others", f);

        String msg = f.getMessage();
        assertTrue("Message should localize to a subset: " + msg, msg.contains("on {"));
        assertTrue("Subset should include X1: " + msg, msg.contains("X1"));
        assertTrue("Subset should include X2: " + msg, msg.contains("X2"));
        for (int j = 4; j <= 8; j++) {
            assertFalse("Irrelevant X" + j + " should not be named: " + msg, msg.contains("X" + j));
        }

        assertTrue("Values map should carry the subset R^2", f.getValues().containsKey("subsetRSquared"));
        assertEquals(2, f.getValues().get("subsetSize"), 1e-9);
        assertTrue("Subset R^2 should be close to full R^2",
                f.getValues().get("subsetRSquared") >= 0.99 * f.getValues().get("rSquared"));
    }

    /**
     * The greedy helper on a known correlation matrix: X3 = X1 + X2 exactly (in correlation terms), with X4
     * independent. The helper must select exactly columns 0 and 1 for column 2 and achieve the full R^2.
     */
    @Test
    public void testExplainingSubsetHelperOnExactCorrelation() {
        // Corr of (X1, X2, X3 = (X1 + X2)/sqrt(2), X4) with X1, X2, X4 iid standard normal.
        double s = 1.0 / Math.sqrt(2.0);
        double[][] corr = {
                {1, 0, s, 0},
                {0, 1, s, 0},
                {s, s, 1, 0},
                {0, 0, 0, 1}};

        double[] achieved = new double[1];
        List<Integer> subset = DataAudit.explainingSubset(new Matrix(corr), 2, 1.0, achieved);

        assertEquals(2, subset.size());
        assertTrue(subset.contains(0));
        assertTrue(subset.contains(1));
        assertEquals(1.0, achieved[0], 1e-9);
    }

    /**
     * Diffuse dependence: X8 is an equal-weight sum of seven predictors with noise tuned so the full R^2 crosses the
     * default threshold but no five-predictor subset reaches 99 percent of it. The finding must still appear, report
     * the best subset found (capped at five predictors), and use the diffuse wording.
     */
    @Test
    public void testDiffuseDependenceReportsBestSubsetFound() {
        DataSet d = make(2000, 8, 7, (ds, i, rng) -> {
            double sum = 0;
            for (int j = 0; j < 7; j++) {
                double x = rng.nextGaussian();
                ds.setDouble(i, j, x);
                sum += x;
            }
            ds.setDouble(i, 7, sum + 0.05 * rng.nextGaussian());
        });

        AuditFinding f = findingFor(d, "X8");
        assertNotNull("X8 should be flagged as nearly linear in the others", f);

        String msg = f.getMessage();
        assertTrue("Message should localize to a subset: " + msg, msg.contains("on {"));
        assertTrue("Diffuse wording expected: " + msg, msg.contains("diffuse"));
        assertEquals(5, f.getValues().get("subsetSize"), 1e-9);
    }
}
