package edu.cmu.tetrad.test;

import edu.cmu.tetrad.algcomparison.graph.RandomForward;
import edu.cmu.tetrad.algcomparison.simulation.ObservationalStudySimulation;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.TimeLagGraph;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Specifies osTypePropSystemDiscrete: a fixed count of system variables is genuinely discrete,
 * inside the system (with parents and children), disjoint from ordinalization, and the option
 * composes with serial, panel, degradation, and discrete-outcome modes. Fails on the unpatched
 * jar, where the parameter does not exist and no system variable is ever discrete.
 *
 * @author josephramsey
 */
public class TestObservationalStudyDiscreteSystem {

    private static final int NUM_SYSTEM = 6;

    private static Parameters base(long seed) {
        Parameters p = new Parameters();
        p.set(Params.SEED, seed);
        p.set(Params.NUM_RUNS, 1);
        p.set(Params.SAMPLE_SIZE, 800);
        p.set(Params.OS_GRAPH_NUM_SYSTEM, NUM_SYSTEM);
        p.set(Params.OS_FORM_INDEX_NOISE, 0.35);
        return p;
    }

    private static ObservationalStudySimulation run(Parameters p) {
        ObservationalStudySimulation sim = new ObservationalStudySimulation(new RandomForward());
        sim.createData(p, true);
        return sim;
    }

    private static List<Node> discreteSystemNodes(DataSet d) {
        List<Node> out = new ArrayList<>();
        for (Node n : d.getVariables()) {
            if (n.getName().startsWith("S") && n instanceof DiscreteVariable) out.add(n);
        }
        return out;
    }

    @Test
    public void testDefaultHasNoDiscreteSystemVariables() {
        DataSet d = (DataSet) run(base(1L)).getDataModel(0);
        assertTrue(discreteSystemNodes(d).isEmpty());
    }

    @Test
    public void testFixedCountOfDiscreteSystemVariables() {
        for (long seed = 1; seed <= 5; seed++) {
            Parameters p = base(seed);
            p.set(Params.OS_TYPE_PROP_SYSTEM_DISCRETE, 0.5);
            p.set(Params.OS_TYPE_NUM_CATEGORIES, 3);
            ObservationalStudySimulation sim = run(p);
            DataSet d = (DataSet) sim.getDataModel(0);
            Graph g = sim.getTrueGraph(0);

            List<Node> disc = discreteSystemNodes(d);
            assertEquals("seed " + seed, 3, disc.size());
            for (Node n : disc) {
                assertEquals(3, ((DiscreteVariable) n).getNumCategories());
                assertTrue("graph node should be discrete too",
                        g.getNode(n.getName()) instanceof DiscreteVariable);
                // Values are valid category indices.
                int col = d.getColumnIndex(n);
                for (int r = 0; r < d.getNumRows(); r++) {
                    int v = d.getInt(r, col);
                    assertTrue(v >= 0 && v < 3);
                }
            }
        }
    }

    @Test
    public void testDiscreteSystemVariableCanBeInterior() {
        // Over several seeds, at least one discrete system variable must have both a parent and
        // a child in the true graph: the interior case this option exists to produce.
        boolean interior = false;
        for (long seed = 1; seed <= 10 && !interior; seed++) {
            Parameters p = base(seed);
            p.set(Params.OS_TYPE_PROP_SYSTEM_DISCRETE, 0.5);
            ObservationalStudySimulation sim = run(p);
            Graph g = sim.getTrueGraph(0);
            for (Node n : discreteSystemNodes((DataSet) sim.getDataModel(0))) {
                Node gn = g.getNode(n.getName());
                if (!g.getParents(gn).isEmpty() && !g.getChildren(gn).isEmpty()) interior = true;
            }
        }
        assertTrue(interior);
    }

    @Test
    public void testDisjointFromOrdinalization() {
        Parameters p = base(3L);
        p.set(Params.OS_TYPE_PROP_SYSTEM_DISCRETE, 0.5);
        p.set(Params.OS_DEGRADE_ORDINALIZE_PROP, 0.5);
        DataSet d = (DataSet) run(p).getDataModel(0);
        // 3 discrete + 3 ordinalized = all 6 recorded discrete, no double-counting, no crash.
        assertEquals(NUM_SYSTEM, discreteSystemNodes(d).size());

        // Ordinalization capped at the remaining continuous count: asking for all six to be
        // ordinalized while three are discrete must not throw.
        p.set(Params.OS_DEGRADE_ORDINALIZE_PROP, 1.0);
        d = (DataSet) run(p).getDataModel(0);
        assertEquals(NUM_SYSTEM, discreteSystemNodes(d).size());
    }

    @Test
    public void testComposesWithSerialPanelDegradationAndDiscreteOutcome() {
        Parameters p = base(7L);
        p.set(Params.OS_TYPE_PROP_SYSTEM_DISCRETE, 0.5);
        p.set(Params.OS_SERIAL_MAX_LAG, 2);
        p.set(Params.OS_PANEL_NUM_SUBJECTS, 4);
        p.set(Params.OS_TYPE_DISCRETE_OUTCOME, true);
        p.set(Params.OS_DEGRADE_CENSOR_PROP, 0.5);
        p.set(Params.OS_DEGRADE_MISSING_MECHANISM, "mnar");
        p.set(Params.OS_DEGRADE_MISSING_PROP, 0.1);
        ObservationalStudySimulation sim = run(p);
        DataSet d = (DataSet) sim.getDataModel(0);
        assertTrue(sim.getTrueGraph(0) instanceof TimeLagGraph);
        assertEquals(3, discreteSystemNodes(d).size());
        assertEquals(4, sim.getSubjectStarts(0).length);

        // In serial mode a discrete system variable is sticky: consecutive equal values within
        // a subject should be far more common than the 1/3 an i.i.d. draw would give.
        Node s = discreteSystemNodes(d).get(0);
        int col = d.getColumnIndex(s);
        int[] starts = sim.getSubjectStarts(0);
        int same = 0, pairs = 0;
        for (int b = 0; b < starts.length; b++) {
            int to = b + 1 < starts.length ? starts[b + 1] : d.getNumRows();
            for (int r = starts[b] + 1; r < to; r++) {
                int a = d.getInt(r - 1, col), c = d.getInt(r, col);
                if (a < 0 || c < 0) continue; // missing
                pairs++;
                if (a == c) same++;
            }
        }
        assertTrue("sticky self-lag expected", same > 0.45 * pairs);
    }

    /**
     * Entry point for running the checks outside JUnit.
     *
     * @param args ignored.
     */
    public static void main(String[] args) {
        TestObservationalStudyDiscreteSystem t = new TestObservationalStudyDiscreteSystem();
        t.testDefaultHasNoDiscreteSystemVariables();
        t.testFixedCountOfDiscreteSystemVariables();
        t.testDiscreteSystemVariableCanBeInterior();
        t.testDisjointFromOrdinalization();
        t.testComposesWithSerialPanelDegradationAndDiscreteOutcome();
        System.out.println("All discrete-system-variable checks passed.");
    }
}
