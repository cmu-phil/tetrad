package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.data.audit.AuditFinding;
import edu.cmu.tetrad.data.audit.DataAudit;
import edu.cmu.tetrad.data.audit.FindingCode;
import edu.cmu.tetrad.graph.Node;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Regression pins for the DETERMINISTIC_RELATION cell-determinism finding in {@link DataAudit}. The motivating case
 * is the Airfoil dataset, where a boundary-layer quantity is an exact nonlinear function of three experimental
 * settings jointly -- invisible to the linear (R-squared) and single-discrete (eta-squared) determinism checks -- and
 * its presence drives FCI to orient arrowheads into experimenter-set variables.
 */
public class TestDataAuditCellDeterminism {

    private static DataSet make(double[][] d, String... names) {
        List<Node> vars = new ArrayList<>();
        for (String nm : names) vars.add(new ContinuousVariable(nm));
        return new BoxDataSet(new DoubleDataBox(d), vars);
    }

    private static List<AuditFinding> detFindings(DataSet ds) {
        return new DataAudit(ds).getFindings(FindingCode.DETERMINISTIC_RELATION);
    }

    /**
     * A nonlinear function of two grid variables must be flagged, with the determined variable first and the minimal
     * determining pair following; the size-3 superset must not be additionally reported.
     */
    @Test
    public void testJointNonlinearDeterminismIsFlaggedMinimally() {
        Random rng = new Random(1);
        int n = 400;
        double[][] d = new double[n][4];
        for (int i = 0; i < n; i++) {
            double a = rng.nextInt(5);            // grid variable, 5 values
            double b = rng.nextInt(4);            // grid variable, 4 values
            d[i][0] = a;
            d[i][1] = b;
            d[i][2] = a * a - 3 * b + a * b;      // Y = f(A, B), nonlinear, exact
            d[i][3] = rng.nextGaussian();         // noise variable
        }
        DataSet ds = make(d, "A", "B", "Y", "C");

        List<AuditFinding> f = detFindings(ds);

        // The forward relation Y = f(A, B) must be reported, minimally.
        AuditFinding forward = null;
        for (AuditFinding finding : f) {
            if (finding.getVariables().get(0).equals("Y")) {
                assertTrue("Only one finding for Y", forward == null);
                forward = finding;
            }
        }
        assertTrue("Y | {A, B} reported", forward != null);
        assertEquals("Minimal determining set has two members", 3, forward.getVariables().size());
        assertTrue(forward.getVariables().containsAll(List.of("A", "B")));
        assertTrue("Coverage recorded", forward.getValues().get("coverage") > 0.5);

        // The INVERSE relation A = g(B, Y) is also true on this grid (a -> a^2 + a*b is injective for a >= 0),
        // and the audit deliberately reports true inverse determinisms too: conditioning on {B, Y} pins A, which
        // is the same faithfulness hazard in the other direction. This assertion pins that behavior as intended.
        boolean inverse = false;
        for (AuditFinding finding : f) {
            if (finding.getVariables().get(0).equals("A")
                    && finding.getVariables().containsAll(List.of("B", "Y"))) inverse = true;
        }
        assertTrue("Inverse determinism A | {B, Y} reported as a true data property", inverse);
        assertEquals("No findings beyond the forward and inverse relations", 2, f.size());
    }

    /**
     * The same function plus noise must NOT be flagged: near-cell-constancy beyond tolerance is not determinism.
     */
    @Test
    public void testNoisyFunctionIsNotFlagged() {
        Random rng = new Random(2);
        int n = 400;
        double[][] d = new double[n][3];
        for (int i = 0; i < n; i++) {
            double a = rng.nextInt(5);
            double b = rng.nextInt(4);
            d[i][0] = a;
            d[i][1] = b;
            d[i][2] = a * a - 3 * b + 0.1 * rng.nextGaussian();
        }
        DataSet ds = make(d, "A", "B", "Y");
        assertEquals(0, detFindings(ds).size());
    }

    /**
     * A single-variable function must be reported with the singleton set, and larger sets containing it must be
     * suppressed by minimality.
     */
    @Test
    public void testSingletonDeterminismIsMinimal() {
        Random rng = new Random(3);
        int n = 300;
        double[][] d = new double[n][3];
        for (int i = 0; i < n; i++) {
            double a = rng.nextInt(6);
            d[i][0] = a;
            d[i][1] = rng.nextInt(4);
            d[i][2] = Math.exp(a);                // Y = g(A) alone, nonlinear
        }
        DataSet ds = make(d, "A", "B", "Y");

        List<AuditFinding> f = detFindings(ds);
        // Y | {A} must be present; no finding for Y with a larger set.
        boolean singleton = false;
        for (AuditFinding finding : f) {
            if (finding.getVariables().get(0).equals("Y")) {
                assertEquals("Only the minimal set {A} for Y", 2, finding.getVariables().size());
                assertEquals("A", finding.getVariables().get(1));
                singleton = true;
            }
        }
        assertTrue(singleton);
    }

    /**
     * Floating-point noise at machine scale within cells must not defeat the tolerance: the relation still counts as
     * deterministic.
     */
    @Test
    public void testToleranceAbsorbsFloatNoise() {
        Random rng = new Random(4);
        int n = 400;
        double[][] d = new double[n][3];
        for (int i = 0; i < n; i++) {
            double a = rng.nextInt(5);
            double b = rng.nextInt(4);
            d[i][0] = a;
            d[i][1] = b;
            d[i][2] = (a * a - 3 * b) * (1 + 1e-15 * rng.nextGaussian());
        }
        DataSet ds = make(d, "A", "B", "Y");
        assertEquals(1, detFindings(ds).size());
    }

    /**
     * A fine grid whose cells are almost all singletons must not be flagged: single-row cells are vacuous, and the
     * coverage guard must reject the scan.
     */
    @Test
    public void testVacuousFineGridIsNotFlagged() {
        Random rng = new Random(5);
        int n = 100;
        double[][] d = new double[n][3];
        for (int i = 0; i < n; i++) {
            double a = rng.nextInt(25);
            double b = rng.nextInt(25);           // ~625 cells >> 100 rows: nearly all cells single-row
            d[i][0] = a;
            d[i][1] = b;
            d[i][2] = rng.nextGaussian();         // pure noise
        }
        DataSet ds = make(d, "A", "B", "Y");
        for (AuditFinding f : detFindings(ds)) {
            assertTrue("No vacuous determinism of the noise variable",
                    !f.getVariables().get(0).equals("Y"));
        }
    }
}
