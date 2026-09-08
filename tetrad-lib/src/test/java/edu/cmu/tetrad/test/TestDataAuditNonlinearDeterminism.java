package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.data.SimpleDataLoader;
import edu.cmu.tetrad.data.audit.AuditFinding;
import edu.cmu.tetrad.data.audit.DataAudit;
import edu.cmu.tetrad.data.audit.FindingCode;
import edu.cmu.tetrad.graph.Node;
import edu.pitt.dbmi.data.reader.Delimiter;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Regression pins for the NEAR_DETERMINISM_NONLINEAR finding in {@link DataAudit}. The motivating case is the
 * Algerian forest fires dataset, where the FWI-system indices ISI and FWI are (up to hand-entry noise) exact
 * nonlinear functions -- exponentials of moisture codes and wind -- of other columns: their multiple linear
 * R-squared on the other continuous variables sits below the linear near-determinism threshold, their determiners
 * are many-valued (so the cell-inspection check cannot see them), and yet each is nearly a smooth function of two
 * other columns, which is precisely the near-faithfulness hazard the audit exists to surface.
 */
public class TestDataAuditNonlinearDeterminism {

    private static DataSet make(double[][] d, String... names) {
        List<Node> vars = new ArrayList<>();
        for (String nm : names) vars.add(new ContinuousVariable(nm));
        return new BoxDataSet(new DoubleDataBox(d), vars);
    }

    private static List<AuditFinding> nonlinearFindings(DataSet ds) {
        return new DataAudit(ds).getFindings(FindingCode.NEAR_DETERMINISM_NONLINEAR);
    }

    /** Returns the nonlinear finding whose determined variable (listed first) is the given name, or null. */
    private static AuditFinding findingFor(List<AuditFinding> findings, String target) {
        for (AuditFinding f : findings) {
            if (!f.getVariables().isEmpty() && f.getVariables().get(0).equals(target)) return f;
        }
        return null;
    }

    /**
     * A pure product Y = X1 * X2 of independent standard Gaussians is uncorrelated with every column, so the linear
     * near-determinism check is blind to it; the spline check must flag it, with the determined variable first and
     * both factors in the determining set, and the leave-one-out R-squared essentially one (the tensor basis
     * contains the product exactly).
     */
    @Test
    public void testSmoothNonlinearFunctionIsFlagged() {
        Random rng = new Random(1);
        int n = 400;
        double[][] d = new double[n][4];
        for (int i = 0; i < n; i++) {
            double x1 = rng.nextGaussian();
            double x2 = rng.nextGaussian();
            d[i][0] = x1;
            d[i][1] = x2;
            d[i][2] = x1 * x2;                    // Y = X1 * X2, exact, linearly invisible
            d[i][3] = rng.nextGaussian();         // noise variable
        }
        DataSet ds = make(d, "X1", "X2", "Y", "C");

        DataAudit audit = new DataAudit(ds);

        // The linear check must NOT have fired for Y; that blindness is what this finding exists for.
        for (AuditFinding f : audit.getFindings(FindingCode.NEAR_DETERMINISM_CONTINUOUS)) {
            assertTrue("Linear near-determinism should not fire for Y", !f.getVariables().get(0).equals("Y"));
        }

        AuditFinding f = findingFor(audit.getFindings(FindingCode.NEAR_DETERMINISM_NONLINEAR), "Y");
        assertTrue("Y = X1 * X2 must be flagged NEAR_DETERMINISM_NONLINEAR", f != null);
        assertTrue("Determining set must contain X1", f.getVariables().contains("X1"));
        assertTrue("Determining set must contain X2", f.getVariables().contains("X2"));
        assertEquals("Determined variable, then a two-variable determining set", 3, f.getVariables().size());
        assertTrue("LOO R^2 should be essentially 1 for an exact tensor-representable function, was "
                + f.getValues().get("looRSquared"), f.getValues().get("looRSquared") > 0.99);
        assertTrue("Linear LOO R^2 on the same set should be near zero, was "
                + f.getValues().get("linearLooRSquared"), f.getValues().get("linearLooRSquared") < 0.1);
    }

    /**
     * A bounded monotone transform W = tanh(2 X3) of a single column must be flagged from the one-variable spline
     * basis alone (a curved single-determiner relation, invisible to the linear whole-matrix check when the
     * correlation stays below its threshold, and invisible to the cell check because X3 is many-valued).
     */
    @Test
    public void testMonotoneSingleDeterminerIsFlagged() {
        Random rng = new Random(2);
        int n = 400;
        double[][] d = new double[n][3];
        for (int i = 0; i < n; i++) {
            double x3 = rng.nextGaussian();
            d[i][0] = x3;
            d[i][1] = Math.tanh(2.0 * x3);        // W = tanh(2 X3), exact, curved
            d[i][2] = rng.nextGaussian();
        }
        DataSet ds = make(d, "X3", "W", "C");

        List<AuditFinding> findings = nonlinearFindings(ds);
        AuditFinding f = findingFor(findings, "W");
        assertTrue("W = tanh(2 X3) must be flagged NEAR_DETERMINISM_NONLINEAR", f != null);
        assertTrue("Determining set must contain X3", f.getVariables().contains("X3"));
        assertTrue("LOO R^2 should be very high for a smooth single-variable function, was "
                + f.getValues().get("looRSquared"), f.getValues().get("looRSquared") > 0.98);
    }

    /**
     * Independent Gaussian columns must yield no nonlinear near-determinism findings: leave-one-out
     * cross-validation must hold the false-positive rate at bay even though the greedy selection examines every
     * candidate subset expansion. This is the calibration side of the check.
     */
    @Test
    public void testIndependentColumnsNotFlagged() {
        Random rng = new Random(42);
        int n = 244;
        int p = 6;
        double[][] d = new double[n][p];
        for (int i = 0; i < n; i++) for (int j = 0; j < p; j++) d[i][j] = rng.nextGaussian();
        String[] names = new String[p];
        for (int j = 0; j < p; j++) names[j] = "V" + j;
        DataSet ds = make(d, names);

        List<AuditFinding> findings = nonlinearFindings(ds);
        assertTrue("Independent columns must not be flagged; got " + findings, findings.isEmpty());
    }

    /**
     * Setting the maximum determining-set size to zero must disable the check entirely.
     */
    @Test
    public void testZeroMaxSetSizeDisablesCheck() {
        Random rng = new Random(1);
        int n = 400;
        double[][] d = new double[n][3];
        for (int i = 0; i < n; i++) {
            double x1 = rng.nextGaussian();
            double x2 = rng.nextGaussian();
            d[i][0] = x1;
            d[i][1] = x2;
            d[i][2] = x1 * x2;
        }
        DataSet ds = make(d, "X1", "X2", "Y");

        DataAudit audit = new DataAudit(ds,
                new DataAudit.Config().withNonlinearDeterminismMaxSetSize(0));
        assertTrue(audit.getFindings(FindingCode.NEAR_DETERMINISM_NONLINEAR).isEmpty());
    }

    /**
     * The Algerian forest fires regression: with default settings, the audit must flag ISI as nearly a smooth
     * function of {FFMC, Ws} and FWI as nearly a smooth function of {ISI, BUI} -- these are the FWI-system
     * definitions, nonlinear (exponential) in form, whose determiners are many-valued, and whose recorded values
     * carry enough hand-entry noise to sit below the linear check's threshold. The test is skipped when the
     * ancillary data file is not present (for example, in a partial checkout).
     */
    @Test
    public void testAlgerianIsiAndFwiAreFlagged() throws Exception {
        File file = new File("../ancillary_files/algerian-forest-fires.mixed.maximum.2.txt");
        if (!file.exists()) file = new File("ancillary_files/algerian-forest-fires.mixed.maximum.2.txt");
        Assume.assumeTrue("Algerian ancillary data file not found; skipping", file.exists());

        DataSet ds = SimpleDataLoader.loadMixedData(file, "//", '"', "*", true, 2, Delimiter.TAB, false);
        DataAudit audit = new DataAudit(ds);
        List<AuditFinding> findings = audit.getFindings(FindingCode.NEAR_DETERMINISM_NONLINEAR);

        AuditFinding isi = findingFor(findings, "ISI");
        assertTrue("ISI must be flagged NEAR_DETERMINISM_NONLINEAR; findings were " + findings, isi != null);
        assertTrue("ISI determining set must contain FFMC", isi.getVariables().contains("FFMC"));
        assertTrue("ISI determining set must contain Ws", isi.getVariables().contains("Ws"));

        AuditFinding fwi = findingFor(findings, "FWI");
        assertTrue("FWI must be flagged NEAR_DETERMINISM_NONLINEAR; findings were " + findings, fwi != null);
        assertTrue("FWI determining set must contain ISI", fwi.getVariables().contains("ISI"));
        assertTrue("FWI determining set must contain BUI", fwi.getVariables().contains("BUI"));

        // The linear check must not have claimed these targets; the partition between the two findings is part of
        // the contract.
        for (AuditFinding f : audit.getFindings(FindingCode.NEAR_DETERMINISM_CONTINUOUS)) {
            String target = f.getVariables().get(0);
            assertTrue("Linear and nonlinear findings must not overlap on " + target,
                    !target.equals("ISI") && !target.equals("FWI"));
        }
    }
}
