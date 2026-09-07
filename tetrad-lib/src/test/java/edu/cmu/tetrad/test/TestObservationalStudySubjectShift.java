package edu.cmu.tetrad.test;

import edu.cmu.tetrad.algcomparison.graph.RandomForward;
import edu.cmu.tetrad.algcomparison.simulation.ObservationalStudySimulation;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.util.ParamDescriptions;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests the osPanelSubjectShiftSd parameter of ObservationalStudySimulation.
 *
 * <p>The subject random intercept on a system variable shows up in pooled data as
 * between-subject variance of that variable's subject means. Its share of total variance -- the
 * intraclass correlation -- is near 1 / rowsPerSubject with no shift and approaches
 * sd^2 / (sd^2 + 1) with a shift of sd on a unit-scale variable. The test checks both ends.</p>
 *
 * <p>The parameter name is written as a string literal so the test compiles against a jar that
 * predates the parameter; on such a jar the setting is ignored and the no-shift case fails.</p>
 */
public class TestObservationalStudySubjectShift {

    private static final String SHIFT_SD = "osPanelSubjectShiftSd";
    private static final int NUM_SUBJECTS = 40;
    private static final int ROWS_PER_SUBJECT = 100;

    private static Parameters panel(double shiftSd, long seed) {
        Parameters p = new Parameters();
        p.set(Params.SEED, seed);
        p.set(Params.NUM_RUNS, 1);
        p.set(Params.SAMPLE_SIZE, NUM_SUBJECTS * ROWS_PER_SUBJECT);
        p.set(Params.OS_GRAPH_NUM_CONTEXT, 1);
        p.set(Params.OS_GRAPH_NUM_HIDDEN_CONTEXT, 0);
        p.set(Params.OS_GRAPH_NUM_SYSTEM, 4);
        p.set(Params.OS_GRAPH_NUM_INDICES, 0);
        p.set(Params.OS_GRAPH_NUM_OUTCOMES, 0);
        p.set(Params.OS_PANEL_NUM_SUBJECTS, NUM_SUBJECTS);
        p.set(Params.OS_PANEL_EMIT_SUBJECT_COLUMN, false);
        p.set(Params.OS_PANEL_EMIT_SUBJECTS_AS_DATA_SETS, false);
        p.set(Params.OS_TYPE_PROP_CONTEXT_DISCRETE, 0.0);
        p.set(Params.OS_TYPE_PROP_SYSTEM_DISCRETE, 0.0);
        p.set(Params.OS_TYPE_DISCRETE_OUTCOME, false);
        p.set(Params.OS_DEGRADE_ORDINALIZE_PROP, 0.0);
        p.set(Params.OS_DEGRADE_MISSING_PROP, 0.0);
        p.set(Params.OS_DEGRADE_CENSOR_PROP, 0.0);
        p.set(Params.OS_SERIAL_MAX_LAG, 0);
        p.set(Params.OS_FORM_NONLINEARITY, 0.0);
        p.set(Params.OS_FORM_INTERACTION, 0.0);
        p.set(SHIFT_SD, shiftSd);
        return p;
    }

    /**
     * Intraclass correlation of the named column: between-subject variance of subject means over
     * total variance, using the simulation's own subject boundaries.
     */
    private static double icc(ObservationalStudySimulation sim, String column) {
        DataSet data = (DataSet) sim.getDataModel(0);
        int col = data.getColumnIndex(column);
        int[] starts = sim.getSubjectStarts(0);
        int n = data.getNumRows();

        double grand = 0;
        for (int i = 0; i < n; i++) grand += data.getDouble(i, col);
        grand /= n;

        double between = 0, total = 0;
        for (int s = 0; s < starts.length; s++) {
            int from = starts[s], to = s + 1 < starts.length ? starts[s + 1] : n;
            double m = 0;
            for (int i = from; i < to; i++) m += data.getDouble(i, col);
            m /= (to - from);
            between += (to - from) * (m - grand) * (m - grand);
            for (int i = from; i < to; i++) {
                double d = data.getDouble(i, col) - grand;
                total += d * d;
            }
        }
        return between / total;
    }

    private static ObservationalStudySimulation simulate(double shiftSd, long seed) {
        ObservationalStudySimulation sim = new ObservationalStudySimulation(new RandomForward());
        sim.createData(panel(shiftSd, seed), true);
        return sim;
    }

    /**
     * With no shift, subjects are exact replicates and the ICC of a system variable is at the
     * chance level of about 1 / rowsPerSubject. S1 has no system parents so its only variation is
     * its own noise plus context, which is drawn fresh each row.
     */
    @Test
    public void testZeroShiftGivesReplicateSubjects() {
        double icc = icc(simulate(0.0, 11L), "S1");
        assertTrue("ICC with no shift should be near chance, was " + icc, icc < 0.06);
    }

    /**
     * With a shift of 2 on a unit-scale variable, roughly 4/5 of S1's variance is between
     * subjects. The bound is loose because S1's within-subject variance also includes context
     * contributions and non-unit noise.
     */
    @Test
    public void testLargeShiftGivesHighIcc() {
        double icc = icc(simulate(2.0, 11L), "S1");
        assertTrue("ICC with shift sd 2 should be high, was " + icc, icc > 0.5);
    }

    /**
     * The ICC must rise monotonically with the shift, on a common seed.
     */
    @Test
    public void testIccIncreasesWithShift() {
        double a = icc(simulate(0.0, 23L), "S1");
        double b = icc(simulate(0.5, 23L), "S1");
        double c = icc(simulate(1.0, 23L), "S1");
        assertTrue("ICC should increase with shift: " + a + " < " + b + " < " + c,
                a < b && b < c);
    }

    /**
     * The parameter is declared, defaults to 0.5, and is listed by the simulation.
     */
    @Test
    public void testParameterIsDeclared() {
        assertEquals(SHIFT_SD, Params.OS_PANEL_SUBJECT_SHIFT_SD);
        assertEquals(0.5, (Double) ParamDescriptions.getInstance().get(SHIFT_SD).getDefaultValue(), 0.0);
        assertTrue(new ObservationalStudySimulation(new RandomForward()).getParameters().contains(SHIFT_SD));
    }
}
