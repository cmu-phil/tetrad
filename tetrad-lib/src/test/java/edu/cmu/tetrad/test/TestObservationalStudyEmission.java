package edu.cmu.tetrad.test;

import edu.cmu.tetrad.algcomparison.graph.RandomForward;
import edu.cmu.tetrad.algcomparison.simulation.ObservationalStudySimulation;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.ParamDescriptions;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Specifies the panel emission modes of ObservationalStudySimulation (osPanelEmitSubjectColumn,
 * osPanelEmitSubjectsAsDataSets) and the documentation coverage of its regrouped parameter
 * names. Fails on the unpatched jar because the two emission parameters do not exist there.
 * <p>
 * The stacking invariant tested here (per-subject data models, concatenated, reproduce the
 * stacked dataset row for row under the same seed) holds because emission happens after all
 * random draws are consumed, so the two modes share an identical RNG stream.
 *
 * @author josephramsey
 */
public class TestObservationalStudyEmission {

    private static final int NUM_SUBJECTS = 5;
    private static final int SAMPLE_SIZE = 500;
    private static final long SEED = 24601L;

    private static Parameters base() {
        Parameters p = new Parameters();
        p.set(Params.SEED, SEED);
        p.set(Params.NUM_RUNS, 1);
        p.set(Params.SAMPLE_SIZE, SAMPLE_SIZE);
        p.set(Params.OS_PANEL_NUM_SUBJECTS, NUM_SUBJECTS);
        p.set(Params.OS_FORM_INDEX_NOISE, 0.35);
        return p;
    }

    private static ObservationalStudySimulation run(Parameters p) {
        ObservationalStudySimulation sim = new ObservationalStudySimulation(new RandomForward());
        sim.createData(p, true);
        return sim;
    }

    private static Node subjectVar(DataSet d) {
        for (Node n : d.getVariables()) if (n.getName().equals("SUBJECT")) return n;
        return null;
    }

    @Test
    public void testDefaultIsStackedWithoutSubjectColumn() {
        ObservationalStudySimulation sim = run(base());
        assertEquals(1, sim.getNumDataModels());
        DataSet d = (DataSet) sim.getDataModel(0);
        assertEquals(SAMPLE_SIZE, d.getNumRows());
        assertNull("SUBJECT must not be emitted by default", subjectVar(d));
        assertEquals(NUM_SUBJECTS, sim.getSubjectStarts(0).length);
    }

    @Test
    public void testSubjectColumnMatchesSubjectStarts() {
        Parameters p = base();
        p.set(Params.OS_PANEL_EMIT_SUBJECT_COLUMN, true);
        ObservationalStudySimulation sim = run(p);

        assertEquals(1, sim.getNumDataModels());
        DataSet d = (DataSet) sim.getDataModel(0);
        Node subj = subjectVar(d);
        assertNotNull("SUBJECT column expected", subj);
        assertTrue(subj instanceof DiscreteVariable);
        assertEquals(NUM_SUBJECTS, ((DiscreteVariable) subj).getNumCategories());
        assertEquals("SUBJECT should be the last column",
                d.getNumColumns() - 1, d.getColumnIndex(subj));

        // Bookkeeping, not a system variable: absent from the true graph.
        assertNull(sim.getTrueGraph(0).getNode("SUBJECT"));

        int[] starts = sim.getSubjectStarts(0);
        int col = d.getColumnIndex(subj);
        for (int s = 0; s < starts.length; s++) {
            int to = s + 1 < starts.length ? starts[s + 1] : d.getNumRows();
            for (int r = starts[s]; r < to; r++) {
                assertEquals("row " + r, s, d.getInt(r, col));
            }
        }
    }

    @Test
    public void testSubjectColumnIgnoredForSingleSubject() {
        Parameters p = base();
        p.set(Params.OS_PANEL_NUM_SUBJECTS, 1);
        p.set(Params.OS_PANEL_EMIT_SUBJECT_COLUMN, true);
        DataSet d = (DataSet) run(p).getDataModel(0);
        assertNull(subjectVar(d));
    }

    @Test
    public void testSubjectsAsDataSetsReproduceStackedRows() {
        ObservationalStudySimulation stacked = run(base());
        DataSet all = (DataSet) stacked.getDataModel(0);
        int[] starts = stacked.getSubjectStarts(0);

        Parameters p = base();
        p.set(Params.OS_PANEL_EMIT_SUBJECTS_AS_DATA_SETS, true);
        ObservationalStudySimulation split = run(p);

        assertEquals(NUM_SUBJECTS, split.getNumDataModels());
        Graph g0 = split.getTrueGraph(0);

        for (int s = 0; s < NUM_SUBJECTS; s++) {
            DataSet d = (DataSet) split.getDataModel(s);
            assertNull("no SUBJECT column in per-subject mode", subjectVar(d));
            assertArrayEquals(new int[]{0}, split.getSubjectStarts(s));
            assertSame("all subjects share the run's true graph", g0, split.getTrueGraph(s));
            assertEquals(all.getVariableNames(), d.getVariableNames());

            int to = s + 1 < NUM_SUBJECTS ? starts[s + 1] : all.getNumRows();
            assertEquals(to - starts[s], d.getNumRows());

            for (int r = 0; r < d.getNumRows(); r++) {
                for (int j = 0; j < d.getNumColumns(); j++) {
                    Object a = all.getObject(starts[s] + r, j);
                    Object b = d.getObject(r, j);
                    assertEquals("subject " + s + " row " + r + " col " + j, a, b);
                }
            }
        }
    }

    @Test
    public void testSubjectsAsDataSetsScalesWithRuns() {
        Parameters p = base();
        p.set(Params.NUM_RUNS, 3);
        p.set(Params.OS_PANEL_EMIT_SUBJECTS_AS_DATA_SETS, true);
        ObservationalStudySimulation sim = run(p);
        assertEquals(3 * NUM_SUBJECTS, sim.getNumDataModels());
        // Within a run, one graph object; across runs, distinct objects.
        assertSame(sim.getTrueGraph(0), sim.getTrueGraph(NUM_SUBJECTS - 1));
        assertNotSame(sim.getTrueGraph(0), sim.getTrueGraph(NUM_SUBJECTS));
    }

    @Test
    public void testAllParametersDocumentedUnderRegroupedNames() {
        List<String> params = new ObservationalStudySimulation(new RandomForward()).getParameters();
        assertTrue(params.contains(Params.OS_PANEL_EMIT_SUBJECT_COLUMN));
        assertTrue(params.contains(Params.OS_PANEL_EMIT_SUBJECTS_AS_DATA_SETS));

        ParamDescriptions desc = ParamDescriptions.getInstance();
        for (String param : params) {
            if (!param.startsWith("os")) continue;
            String[] groups = {"osGraph", "osType", "osForm", "osSerial", "osPanel", "osDegrade"};
            assertTrue("parameter not in a sort group: " + param,
                    Arrays.stream(groups).anyMatch(param::startsWith));
            String shortDesc = desc.get(param).getShortDescription();
            assertFalse("undocumented: " + param,
                    shortDesc.startsWith("Please add a description"));
        }
    }

    /**
     * Entry point for running the checks outside JUnit.
     *
     * @param args ignored.
     */
    public static void main(String[] args) {
        TestObservationalStudyEmission t = new TestObservationalStudyEmission();
        t.testDefaultIsStackedWithoutSubjectColumn();
        t.testSubjectColumnMatchesSubjectStarts();
        t.testSubjectColumnIgnoredForSingleSubject();
        t.testSubjectsAsDataSetsReproduceStackedRows();
        t.testSubjectsAsDataSetsScalesWithRuns();
        t.testAllParametersDocumentedUnderRegroupedNames();
        System.out.println("All observational-study emission checks passed.");
    }
}
