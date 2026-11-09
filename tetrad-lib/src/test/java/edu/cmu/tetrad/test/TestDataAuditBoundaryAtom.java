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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests of the BOUNDARY_ATOM check: a large point mass at the top or bottom of a continuous variable's observed
 * range, adjacent to the rest of the distribution -- a ceiling or floor -- as distinct from a SENTINEL_VALUE, which
 * is a point mass separated from the rest by a gap. "Adjacent" is scale-aware: a gap of at most a small fraction of
 * the observed range, or of at most one median grid step. The two checks run from one tally and an extreme value
 * is reported as one or the other, never both. The REAL CHEM grade columns are the motivating case: fractional
 * grades make the median spacing tiny, so the sentinel gap ratio read a two-point gap below 100 as a code.
 */
public class TestDataAuditBoundaryAtom {

    private static DataSet make(double[][] d, String... names) {
        List<Node> vars = new ArrayList<>();
        for (String nm : names) vars.add(new ContinuousVariable(nm));
        return new BoxDataSet(new DoubleDataBox(d), vars);
    }

    private static AuditFinding findingFor(List<AuditFinding> findings, String variable) {
        for (AuditFinding f : findings) {
            if (f.getVariables().size() == 1 && f.getVariables().get(0).equals(variable)) return f;
        }
        return null;
    }

    /**
     * A grade column with a large share at exactly 100 and the rest spread continuously just below it (the REAL
     * CHEM module checkpoints) must be flagged as a ceiling, not as a sentinel: there is no gap.
     */
    @Test
    public void testCeilingIsFlaggedAsBoundaryAtomNotSentinel() {
        Random rng = new Random(3);
        int n = 500;
        double[][] d = new double[n][2];
        for (int i = 0; i < n; i++) {
            d[i][0] = i < 300 ? 100.0 : 100.0 - 0.5 - rng.nextDouble() * 40.0;   // 60% at 100, rest in (59.5, 99.5)
            d[i][1] = rng.nextGaussian();
        }
        DataSet ds = make(d, "Grade", "Clean");
        DataAudit audit = new DataAudit(ds);

        AuditFinding atom = findingFor(audit.getFindings(FindingCode.BOUNDARY_ATOM), "Grade");
        assertNotNull("A 60% ceiling with no gap must be flagged as a boundary atom.", atom);
        assertEquals(AuditFinding.Severity.WARNING, atom.getSeverity());
        assertEquals(100.0, atom.getValues().get("value"), 0.0);
        assertEquals(300.0, atom.getValues().get("count"), 0.0);
        assertEquals(0.6, atom.getValues().get("mass"), 1e-12);
        assertEquals(0.0, atom.getValues().get("atMinimum"), 0.0);

        assertNull("A ceiling with no gap must not be reported as a sentinel code.",
                findingFor(audit.getFindings(FindingCode.SENTINEL_VALUE), "Grade"));
        assertNull("The clean column has no atom.",
                findingFor(audit.getFindings(FindingCode.BOUNDARY_ATOM), "Clean"));
    }

    /**
     * A floor at 0 with the rest of the distribution starting right above it is a floor, reported with atMinimum.
     */
    @Test
    public void testFloorIsFlaggedWithAtMinimum() {
        Random rng = new Random(4);
        int n = 500;
        double[][] d = new double[n][1];
        for (int i = 0; i < n; i++) d[i][0] = i < 100 ? 0.0 : 0.25 + rng.nextDouble() * 30.0;

        AuditFinding atom = findingFor(new DataAudit(make(d, "Count")).getFindings(FindingCode.BOUNDARY_ATOM),
                "Count");
        assertNotNull("A 20% floor with no gap must be flagged.", atom);
        assertEquals(0.0, atom.getValues().get("value"), 0.0);
        assertEquals(1.0, atom.getValues().get("atMinimum"), 0.0);
    }

    /**
     * The same mass at an extreme value, but separated from the rest by a wide gap, is the sentinel case and must
     * be reported as SENTINEL_VALUE only.
     */
    @Test
    public void testGappedAtomIsSentinelNotBoundary() {
        Random rng = new Random(5);
        int n = 500;
        double[][] d = new double[n][1];
        for (int i = 0; i < n; i++) d[i][0] = i < 150 ? 0.0 : 50.0 + rng.nextInt(40);   // integer grid, gap 50

        DataAudit audit = new DataAudit(make(d, "Coded"));
        assertNotNull("A gapped atom is a sentinel.",
                findingFor(audit.getFindings(FindingCode.SENTINEL_VALUE), "Coded"));
        assertNull("A gapped atom must not also be a boundary atom.",
                findingFor(audit.getFindings(FindingCode.BOUNDARY_ATOM), "Coded"));
    }

    /**
     * A count variable's zero, one grid step below its neighbors, with a large share of the rows on it (Pima's
     * Pregnancies): not a sentinel, as the sentinel tests already pin, and now a BOUNDARY_ATOM floor. This is a
     * stated choice: the atom attenuates every correlation the variable takes part in whether or not the zero is a
     * censored value, and the finding says floor, not code.
     */
    @Test
    public void testCountVariableZeroIsAFloorNotASentinel() {
        Random rng = new Random(7);
        int n = 400;
        double[][] d = new double[n][1];
        for (int i = 0; i < n; i++) d[i][0] = i < 120 ? 0.0 : 1 + rng.nextInt(16);

        DataAudit audit = new DataAudit(make(d, "Count"));
        AuditFinding atom = findingFor(audit.getFindings(FindingCode.BOUNDARY_ATOM), "Count");
        assertNotNull("A 30% count-variable zero one step below its neighbors is a floor atom.", atom);
        assertEquals(1.0, atom.getValues().get("atMinimum"), 0.0);
        assertNull(findingFor(audit.getFindings(FindingCode.SENTINEL_VALUE), "Count"));
    }

    /**
     * A ceiling of fractional grades with a one-point gap below 100 -- the REAL CHEM geometry, where the old
     * sentinel rule fired -- must be a boundary atom and not a sentinel, and the small-gap fraction is configurable:
     * shrinking it to nothing hands the column back to the sentinel rule.
     */
    @Test
    public void testFractionalGradeCeilingIsNotASentinelAndFractionIsConfigurable() {
        Random rng = new Random(8);
        int n = 2000;
        double[][] d = new double[n][1];
        for (int i = 0; i < n; i++) d[i][0] = i < 500 ? 100.0 : Math.round((50.0 + rng.nextDouble() * 49.0) * 100.0) / 100.0;

        DataSet ds = make(d, "MC");
        DataAudit audit = new DataAudit(ds);
        assertNotNull(findingFor(audit.getFindings(FindingCode.BOUNDARY_ATOM), "MC"));
        assertNull("A one-point gap below 100 in a column of fractional grades is not a code.",
                findingFor(audit.getFindings(FindingCode.SENTINEL_VALUE), "MC"));

        DataAudit strict = new DataAudit(ds, new DataAudit.Config().withBoundaryAtomMaxGapFraction(0.0));
        assertNull("With the small-gap fraction at zero the gap is no longer small.",
                findingFor(strict.getFindings(FindingCode.BOUNDARY_ATOM), "MC"));
        assertNotNull("...and the sentinel rule, scale-blind, fires on it as before.",
                findingFor(strict.getFindings(FindingCode.SENTINEL_VALUE), "MC"));
    }

    /**
     * Below the mass threshold nothing fires; the threshold is configurable.
     */
    @Test
    public void testMassThresholdIsRespectedAndConfigurable() {
        Random rng = new Random(6);
        int n = 500;
        double[][] d = new double[n][1];
        for (int i = 0; i < n; i++) d[i][0] = i < 25 ? 100.0 : 100.0 - 0.5 - rng.nextDouble() * 40.0;  // 5% at 100

        assertNull("5% at the ceiling is under the default 10% threshold.",
                findingFor(new DataAudit(make(d, "Grade")).getFindings(FindingCode.BOUNDARY_ATOM), "Grade"));

        DataAudit lowered = new DataAudit(make(d, "Grade"), new DataAudit.Config().withBoundaryAtomMinMass(0.04));
        AuditFinding atom = findingFor(lowered.getFindings(FindingCode.BOUNDARY_ATOM), "Grade");
        assertNotNull("At a 4% threshold the same column fires.", atom);
        assertTrue(atom.getValues().get("mass") >= 0.04);
    }
}
