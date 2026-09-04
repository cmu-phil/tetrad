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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Regression pins for the SENTINEL_VALUE finding in {@link DataAudit}. The motivating case is the Pima Indians
 * Diabetes dataset, in which five measurement columns write 0 for a measurement that was not taken -- 48.7% of the
 * insulin column and 29.6% of the triceps skinfold column. Arithmetic cannot distinguish the code from a real
 * measurement, so the shared zeros inflate the correlation between the two coded columns (0.44 against 0.19 on the
 * jointly observed rows) while attenuating the correlations between a coded column and an uncoded one, and the
 * resulting point mass costs conditional independence tests enough power that a graph can pass a Markov check
 * because nothing rejects.
 * <p>
 * The discriminating condition is the gap from the candidate value to its nearest observed neighbor, measured
 * against the median spacing among the remaining values: a code sits far outside the measured range, while a count
 * variable whose zero legitimately means "none" sits one ordinary step below its neighbors.
 */
public class TestDataAuditSentinelValue {

    private static DataSet make(double[][] d, String... names) {
        List<Node> vars = new ArrayList<>();
        for (String nm : names) vars.add(new ContinuousVariable(nm));
        return new BoxDataSet(new DoubleDataBox(d), vars);
    }

    private static List<AuditFinding> sentinelFindings(DataSet ds) {
        return new DataAudit(ds).getFindings(FindingCode.SENTINEL_VALUE);
    }

    private static AuditFinding findingFor(List<AuditFinding> findings, String variable) {
        for (AuditFinding f : findings) {
            if (f.getVariables().size() == 1 && f.getVariables().get(0).equals(variable)) return f;
        }
        return null;
    }

    /**
     * Builds a column of measurements on a coarse grid, then overwrites the first {@code numCoded} entries with the
     * given code. The grid spacing is 1, so the code's distance from the measured range controls the gap ratio.
     */
    private static void codedColumn(double[][] d, int col, int numCoded, double code, double low, Random rng) {
        for (int i = 0; i < d.length; i++) {
            d[i][col] = i < numCoded ? code : low + rng.nextInt(40);
        }
    }

    /**
     * A code far below the measured range, on half the rows, must be flagged: the Insulin case. The finding must
     * name the variable, carry the code as its "value", count the coded cells, and be a WARNING.
     */
    @Test
    public void testLowCodeIsFlagged() {
        Random rng = new Random(1);
        int n = 400;
        double[][] d = new double[n][2];
        codedColumn(d, 0, 200, 0.0, 50.0, rng);
        for (int i = 0; i < n; i++) d[i][1] = rng.nextGaussian();

        AuditFinding f = findingFor(sentinelFindings(make(d, "Coded", "Clean")), "Coded");

        assertNotNull("A code far below the measured range must be flagged.", f);
        assertEquals(AuditFinding.Severity.WARNING, f.getSeverity());
        assertEquals(0.0, f.getValues().get("value"), 0.0);
        assertEquals(200.0, f.getValues().get("count"), 0.0);
        assertEquals(1.0, f.getValues().get("atMinimum"), 0.0);
        assertEquals("0 is a conventional missing-data code.",
                1.0, f.getValues().get("commonMissingCode"), 0.0);
        assertTrue("The gap must exceed the default ratio of 3.", f.getValues().get("gapRatio") >= 3.0);
    }

    /**
     * A code far above the measured range must be flagged just as a low one is, with atMinimum false: files use 999
     * and 9999 as readily as 0 and -999.
     */
    @Test
    public void testHighCodeIsFlagged() {
        Random rng = new Random(2);
        int n = 400;
        double[][] d = new double[n][2];
        for (int i = 0; i < n; i++) {
            d[i][0] = i < 60 ? 999.0 : 20 + rng.nextInt(40);
            d[i][1] = rng.nextGaussian();
        }

        AuditFinding f = findingFor(sentinelFindings(make(d, "Coded", "Clean")), "Coded");

        assertNotNull("A code far above the measured range must be flagged.", f);
        assertEquals(999.0, f.getValues().get("value"), 0.0);
        assertEquals(0.0, f.getValues().get("atMinimum"), 0.0);
    }

    /**
     * A column coded at both ends yields two findings, one per end, since -999 and 999 are commonly used together
     * for "not asked" and "refused".
     */
    @Test
    public void testBothEndsCodedYieldsTwoFindings() {
        Random rng = new Random(3);
        int n = 400;
        double[][] d = new double[n][2];
        for (int i = 0; i < n; i++) {
            if (i < 40) d[i][0] = -999.0;
            else if (i < 80) d[i][0] = 999.0;
            else d[i][0] = 20 + rng.nextInt(40);
            d[i][1] = rng.nextGaussian();
        }

        List<AuditFinding> findings = sentinelFindings(make(d, "Coded", "Clean"));
        int forCoded = 0;
        boolean sawLow = false, sawHigh = false;

        for (AuditFinding f : findings) {
            if (!f.getVariables().get(0).equals("Coded")) continue;
            forCoded++;
            if (f.getValues().get("value") == -999.0) sawLow = true;
            if (f.getValues().get("value") == 999.0) sawHigh = true;
        }

        assertEquals("One finding per coded end.", 2, forCoded);
        assertTrue(sawLow);
        assertTrue(sawHigh);
    }

    /**
     * The central negative control: a count variable whose zero legitimately means "none". Zero repeats heavily, so
     * the count and mass conditions hold, and only the gap condition separates this from a code. The gap here is one
     * ordinary step, so nothing may be flagged. Pima's Pregnancies column is this case, with 111 zeros.
     */
    @Test
    public void testLegitimateZeroOfACountVariableIsNotFlagged() {
        Random rng = new Random(4);
        int n = 400;
        double[][] d = new double[n][2];
        for (int i = 0; i < n; i++) {
            d[i][0] = i < 120 ? 0.0 : 1 + rng.nextInt(16);
            d[i][1] = rng.nextGaussian();
        }

        assertNull("A count variable's legitimate zero sits one step below its neighbors and must not be flagged.",
                findingFor(sentinelFindings(make(d, "Count", "Clean")), "Count"));
    }

    /**
     * A single extreme observation with a wide gap is an outlier, not a code, and is held back by the count floor.
     */
    @Test
    public void testIsolatedExtremeValueIsNotFlagged() {
        Random rng = new Random(5);
        int n = 400;
        double[][] d = new double[n][2];
        for (int i = 0; i < n; i++) {
            d[i][0] = i == 0 ? -5000.0 : 20 + rng.nextInt(40);
            d[i][1] = rng.nextGaussian();
        }

        assertNull("One extreme observation is an outlier, not a code.",
                findingFor(sentinelFindings(make(d, "Outlier", "Clean")), "Outlier"));
    }

    /**
     * An ordinary continuous column with no repeated extreme must produce no finding at all: the check must not fire
     * on well-behaved data.
     */
    @Test
    public void testCleanGaussianColumnsAreNotFlagged() {
        Random rng = new Random(6);
        int n = 400;
        double[][] d = new double[n][3];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < 3; j++) d[i][j] = rng.nextGaussian();
        }

        assertTrue("No finding may fire on clean Gaussian data.",
                sentinelFindings(make(d, "X", "Y", "Z")).isEmpty());
    }

    /**
     * The gap reference must be computed from the spacings among the uncoded values, not from the column's spread.
     * When the code carries most of the mass it dominates any spread statistic, so a spread-based reference grows
     * with the very contamination the check is looking for and the heaviest codings escape. This pins the case that
     * an interquartile-range reference fails: a code on 70% of the rows.
     */
    @Test
    public void testHeavilyCodedColumnIsStillFlagged() {
        Random rng = new Random(7);
        int n = 400;
        double[][] d = new double[n][2];
        codedColumn(d, 0, 280, 0.0, 30.0, rng);
        for (int i = 0; i < n; i++) d[i][1] = rng.nextGaussian();

        AuditFinding f = findingFor(sentinelFindings(make(d, "Coded", "Clean")), "Coded");

        assertNotNull("A code carrying 70% of the mass must still be flagged.", f);
        assertEquals(280.0, f.getValues().get("count"), 0.0);
    }

    /**
     * The thresholds must be reachable through the config, and tightening the gap ratio past the observed ratio must
     * suppress the finding: the check is a heuristic and its knobs have to be adjustable when it misfires.
     */
    @Test
    public void testGapRatioThresholdIsHonored() {
        Random rng = new Random(8);
        int n = 400;
        double[][] d = new double[n][2];
        codedColumn(d, 0, 100, 0.0, 50.0, rng);
        for (int i = 0; i < n; i++) d[i][1] = rng.nextGaussian();

        DataSet ds = make(d, "Coded", "Clean");

        AuditFinding f = findingFor(new DataAudit(ds).getFindings(FindingCode.SENTINEL_VALUE), "Coded");
        assertNotNull(f);
        double ratio = f.getValues().get("gapRatio");

        DataAudit tightened = new DataAudit(ds,
                new DataAudit.Config().withSentinelGapRatio(ratio * 2));

        assertTrue("A gap ratio threshold above the observed ratio must suppress the finding.",
                tightened.getFindings(FindingCode.SENTINEL_VALUE).isEmpty());
    }

    /**
     * A config customized for the sentinel check must survive an unrelated with-method. This pins the silent-reset
     * bug that a copy-through omission in Config would produce.
     */
    @Test
    public void testSentinelConfigSurvivesOtherWithMethods() {
        DataAudit.Config config = new DataAudit.Config()
                .withSentinelGapRatio(100.0)
                .withHighCorrelation(0.8)
                .withAdAlpha(0.05);

        Random rng = new Random(9);
        int n = 400;
        double[][] d = new double[n][2];
        codedColumn(d, 0, 100, 0.0, 50.0, rng);
        for (int i = 0; i < n; i++) d[i][1] = rng.nextGaussian();

        assertTrue("withHighCorrelation must not reset the sentinel gap ratio.",
                new DataAudit(make(d, "Coded", "Clean"), config)
                        .getFindings(FindingCode.SENTINEL_VALUE).isEmpty());
    }

    /**
     * Recoding a flagged code to missing must clear the finding, and the missingness audit must then see the cells.
     * This is the round trip the Data Audit dialog's recode button performs.
     */
    @Test
    public void testRecodeToMissingClearsTheFinding() {
        Random rng = new Random(10);
        int n = 400;
        double[][] d = new double[n][2];
        codedColumn(d, 0, 150, 0.0, 50.0, rng);
        for (int i = 0; i < n; i++) d[i][1] = rng.nextGaussian();

        DataSet ds = make(d, "Coded", "Clean");
        assertNotNull(findingFor(sentinelFindings(ds), "Coded"));

        assertEquals("Every coded cell must be recoded.", 150, DataAudit.recodeToMissing(ds, "Coded", 0.0));

        DataAudit after = new DataAudit(ds);
        assertTrue("The finding must clear once the code is missing.",
                after.getFindings(FindingCode.SENTINEL_VALUE).isEmpty());
        assertNotNull("The recoded cells must now be counted as missing.", after.getMissingDataAudit());
        assertTrue(after.hasFinding(FindingCode.MISSING_DATA));

        assertEquals("A second recode must find nothing left to do.",
                0, DataAudit.recodeToMissing(ds, "Coded", 0.0));
    }

    /**
     * The note must fire with the finding and not otherwise: it says only the codebook settles whether the flagged
     * value is a code, and that the audit's other continuous checks were computed with it treated as data.
     */
    @Test
    public void testNoteAccompaniesTheFinding() {
        Random rng = new Random(11);
        int n = 400;
        double[][] d = new double[n][2];
        codedColumn(d, 0, 100, 0.0, 50.0, rng);
        for (int i = 0; i < n; i++) d[i][1] = rng.nextGaussian();

        assertTrue(new DataAudit(make(d, "Coded", "Clean")).notes()
                .contains(DataAudit.SENTINEL_VALUE_NOTE));

        double[][] clean = new double[n][2];
        for (int i = 0; i < n; i++) {
            clean[i][0] = rng.nextGaussian();
            clean[i][1] = rng.nextGaussian();
        }

        assertFalse(new DataAudit(make(clean, "X", "Y")).notes()
                .contains(DataAudit.SENTINEL_VALUE_NOTE));
    }

    /**
     * Recoding a discrete variable must be refused rather than silently doing the wrong thing: a discrete sentinel
     * is a category, and the audit has no license to renumber the remaining ones.
     */
    @Test
    public void testRecodeRefusesDiscreteVariables() {
        List<Node> vars = new ArrayList<>();
        vars.add(new ContinuousVariable("X"));
        vars.add(new edu.cmu.tetrad.data.DiscreteVariable("D", 3));
        double[][] d = new double[50][2];
        Random rng = new Random(12);
        for (int i = 0; i < 50; i++) {
            d[i][0] = rng.nextGaussian();
            d[i][1] = rng.nextInt(3);
        }
        DataSet ds = new BoxDataSet(new DoubleDataBox(d), vars);

        try {
            DataAudit.recodeToMissing(ds, "D", 0.0);
            fail("Recoding a discrete variable must throw.");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }

        try {
            DataAudit.recodeToMissing(ds, "NoSuchVariable", 0.0);
            fail("Recoding an absent variable must throw.");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }
}
