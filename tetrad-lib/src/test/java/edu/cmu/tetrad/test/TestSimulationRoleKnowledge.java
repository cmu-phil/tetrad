package edu.cmu.tetrad.test;

import edu.cmu.tetrad.algcomparison.graph.RandomForward;
import edu.cmu.tetrad.algcomparison.simulation.DesignedExperimentSimulation;
import edu.cmu.tetrad.algcomparison.simulation.ObservationalStudySimulation;
import edu.cmu.tetrad.algcomparison.utils.ProvidesKnowledge;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Specifies the design-implied knowledge of the observational-study and designed-experiment
 * simulations (ProvidesKnowledge): role tiers over the emitted variables, bookkeeping columns in
 * a leading tier of their own, hidden variables absent, and - the property that makes the
 * knowledge legitimate rather than cheating - the true graph is always consistent with it, since
 * every true edge runs forward or within a tier. Fails on the unpatched jar, where neither
 * simulation implements ProvidesKnowledge.
 *
 * @author josephramsey
 */
public class TestSimulationRoleKnowledge {

    private static int tierOf(Knowledge k, String name) {
        for (int t = 0; t < k.getNumTiers(); t++) {
            if (k.getTier(t).contains(name)) return t;
        }
        return -1;
    }

    private static void assertGraphConsistent(Graph g, Knowledge k) {
        for (Edge e : g.getEdges()) {
            String from = e.getNode1().getName(), to = e.getNode2().getName();
            if (e.pointsTowards(e.getNode1())) {
                String tmp = from; from = to; to = tmp;
            }
            int tf = tierOf(k, from), tt = tierOf(k, to);
            if (tf < 0 || tt < 0) continue; // hidden variables have no tier
            assertTrue(from + " -> " + to + " runs backward across tiers", tf <= tt);
            assertFalse(from + " -> " + to + " is forbidden", k.isForbidden(from, to));
        }
    }

    @Test
    public void testObservationalStudyTiers() {
        Parameters p = new Parameters();
        p.set(Params.SEED, 5L);
        p.set(Params.NUM_RUNS, 1);
        p.set(Params.SAMPLE_SIZE, 300);
        p.set(Params.OS_GRAPH_NUM_HIDDEN_CONTEXT, 1);
        ObservationalStudySimulation sim = new ObservationalStudySimulation(new RandomForward());
        sim.createData(p, true);
        assertTrue(sim instanceof ProvidesKnowledge);

        Knowledge k = sim.getKnowledge(0);
        assertEquals(4, k.getNumTiers());
        assertEquals(0, tierOf(k, "C1"));
        assertEquals(1, tierOf(k, "S1"));
        assertEquals(2, tierOf(k, "I1"));
        assertEquals(3, tierOf(k, "Y1"));
        assertFalse("hidden context is not in the data and must not be in the knowledge",
                k.getVariables().contains("H1"));
        assertEquals(((DataSet) sim.getDataModel(0)).getVariableNames().size(),
                k.getVariables().size());
        assertGraphConsistent(sim.getTrueGraph(0), k);
    }

    @Test
    public void testObservationalStudySubjectColumnLeadsAndPerSubjectMode() {
        Parameters p = new Parameters();
        p.set(Params.SEED, 6L);
        p.set(Params.NUM_RUNS, 1);
        p.set(Params.SAMPLE_SIZE, 400);
        p.set(Params.OS_PANEL_NUM_SUBJECTS, 4);
        p.set(Params.OS_PANEL_EMIT_SUBJECT_COLUMN, true);
        ObservationalStudySimulation sim = new ObservationalStudySimulation(new RandomForward());
        sim.createData(p, true);
        Knowledge k = sim.getKnowledge(0);
        assertEquals(5, k.getNumTiers());
        assertEquals(0, tierOf(k, "SUBJECT"));
        assertEquals(1, tierOf(k, "C1"));
        assertEquals(2, tierOf(k, "S1")); // "SUBJECT" must not be swept into the S tier

        p.set(Params.OS_PANEL_EMIT_SUBJECT_COLUMN, false);
        p.set(Params.OS_PANEL_EMIT_SUBJECTS_AS_DATA_SETS, true);
        sim = new ObservationalStudySimulation(new RandomForward());
        sim.createData(p, true);
        assertEquals(4, sim.getNumDataModels());
        for (int i = 0; i < 4; i++) {
            Knowledge ki = sim.getKnowledge(i);
            assertEquals(4, ki.getNumTiers());
            assertFalse(ki.getVariables().contains("SUBJECT"));
            assertGraphConsistent(sim.getTrueGraph(i), ki);
        }
    }

    @Test
    public void testDesignedExperimentTiers() {
        Parameters p = new Parameters();
        p.set(Params.SEED, 7L);
        p.set(Params.NUM_RUNS, 1);
        p.set(Params.SAMPLE_SIZE, 300);
        p.set(Params.DE_SORT_BY_CONFIGURATION, true);
        p.set(Params.DE_EMIT_CONFIG_COLUMN, true);
        DesignedExperimentSimulation sim = new DesignedExperimentSimulation(new RandomForward());
        sim.createData(p, true);
        assertTrue(sim instanceof ProvidesKnowledge);

        Knowledge k = sim.getKnowledge(0);
        assertEquals(4, k.getNumTiers());
        assertEquals(0, tierOf(k, "CONFIG"));
        assertEquals(1, tierOf(k, "F1"));
        assertEquals(2, tierOf(k, "D1"));
        assertEquals(3, tierOf(k, "R1"));
        assertGraphConsistent(sim.getTrueGraph(0), k);

        p.set(Params.DE_EMIT_CONFIG_COLUMN, false);
        sim = new DesignedExperimentSimulation(new RandomForward());
        sim.createData(p, true);
        k = sim.getKnowledge(0);
        assertEquals("empty bookkeeping tier is dropped", 3, k.getNumTiers());
        assertEquals(0, tierOf(k, "F1"));
    }

    @Test
    public void testKnowledgeIsFreshEachCall() {
        Parameters p = new Parameters();
        p.set(Params.SEED, 8L);
        p.set(Params.NUM_RUNS, 1);
        p.set(Params.SAMPLE_SIZE, 200);
        ObservationalStudySimulation sim = new ObservationalStudySimulation(new RandomForward());
        sim.createData(p, true);
        Knowledge a = sim.getKnowledge(0);
        a.setForbidden("C1", "S1");
        Knowledge b = sim.getKnowledge(0);
        assertFalse("modifying a returned knowledge must not affect the simulation",
                b.isForbidden("C1", "S1"));
        List<String> names = ((DataSet) sim.getDataModel(0)).getVariableNames();
        assertEquals(names.size(), b.getVariables().size());
    }

    public static void main(String[] args) {
        TestSimulationRoleKnowledge t = new TestSimulationRoleKnowledge();
        t.testObservationalStudyTiers();
        t.testObservationalStudySubjectColumnLeadsAndPerSubjectMode();
        t.testDesignedExperimentTiers();
        t.testKnowledgeIsFreshEachCall();
        System.out.println("All simulation role-knowledge checks passed.");
    }
}
