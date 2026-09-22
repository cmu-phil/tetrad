package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.data.MixedDataBox;
import edu.cmu.tetrad.data.missing.MissingDataAudit;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.utils.MissingnessIndicatorAdder;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests {@link MissingnessIndicatorAdder}.
 *
 * @author josephramsey
 */
public class TestMissingnessIndicatorAdder {

    private static DataSet fixture() {
        Random random = new Random(38482L);
        int n = 400;

        List<Node> vars = new ArrayList<>();
        vars.add(new ContinuousVariable("X1"));   // about 25% missing
        vars.add(new ContinuousVariable("X2"));   // one missing case, below the floor
        vars.add(new DiscreteVariable("D1", List.of("a", "b", "c")));  // about 40% missing
        vars.add(new ContinuousVariable("X3"));   // no missing
        vars.add(new ContinuousVariable("X4"));   // entirely missing

        DataSet data = new BoxDataSet(new MixedDataBox(vars, n), vars);

        for (int i = 0; i < n; i++) {
            data.setDouble(i, 0, random.nextDouble() < 0.25 ? Double.NaN : random.nextGaussian());
            data.setDouble(i, 1, i == 0 ? Double.NaN : random.nextGaussian());
            data.setInt(i, 2, random.nextDouble() < 0.40 ? DiscreteVariable.MISSING_VALUE : random.nextInt(3));
            data.setDouble(i, 3, random.nextGaussian());
            data.setDouble(i, 4, Double.NaN);
        }

        return data;
    }

    /**
     * Only variables inside the rate window get indicators. A fully observed variable, a fully missing one and one
     * below the floor are all skipped, since each would give a constant column.
     */
    @Test
    public void testSelection() {
        DataSet data = fixture();
        MissingnessIndicatorAdder.Result result =
                MissingnessIndicatorAdder.add(data, MissingnessIndicatorAdder.Spec.defaults());

        assertEquals(List.of("X1_missing", "D1_missing"), result.indicatorNames());
        assertEquals(7, result.data().getNumColumns());
        assertEquals(data.getNumRows(), result.data().getNumRows());
    }

    /**
     * The indicator is 1 exactly when the source value is absent, for both continuous and discrete sources, and the
     * original columns come through unchanged.
     */
    @Test
    public void testPolarityAndPreservation() {
        DataSet data = fixture();
        DataSet out = MissingnessIndicatorAdder.add(data, MissingnessIndicatorAdder.Spec.defaults()).data();

        for (String name : List.of("X1", "D1")) {
            int source = data.getColumnIndex(data.getVariable(name));
            int indicator = out.getColumnIndex(out.getVariable(name + "_missing"));

            for (int i = 0; i < data.getNumRows(); i++) {
                boolean missing = MissingDataAudit.isMissing(data, i, source);
                assertEquals("Row " + i + " of " + name, missing ? 1 : 0, out.getInt(i, indicator));
            }
        }

        for (int i = 0; i < data.getNumRows(); i++) {
            assertEquals(data.getInt(i, 2), out.getInt(i, 2));

            if (!Double.isNaN(data.getDouble(i, 0))) {
                assertEquals(data.getDouble(i, 0), out.getDouble(i, 0), 0.0);
            }
        }
    }

    /**
     * Indicators may not cause substantive variables, and a variable may not cause its own indicator.
     */
    @Test
    public void testKnowledge() {
        DataSet data = fixture();
        DataSet out = MissingnessIndicatorAdder.add(data, MissingnessIndicatorAdder.Spec.defaults()).data();

        Knowledge knowledge = out.getKnowledge();
        assertNotNull(knowledge);

        assertTrue(knowledge.isForbidden("X1_missing", "X1"));
        assertTrue(knowledge.isForbidden("D1_missing", "X3"));
        assertTrue(knowledge.isForbidden("X1", "X1_missing"));
        assertFalse(knowledge.isForbidden("X1", "D1_missing"));
        assertFalse(knowledge.isForbidden("X1_missing", "D1_missing"));
    }

    /**
     * With knowledge switched off, nothing is attached; with self-masking allowed, only the tier rule applies.
     */
    @Test
    public void testKnowledgeSwitches() {
        DataSet data = fixture();

        DataSet none = MissingnessIndicatorAdder.add(data,
                MissingnessIndicatorAdder.Spec.defaults().withKnowledge(false, false)).data();
        assertTrue(none.getKnowledge().isEmpty());

        DataSet allowed = MissingnessIndicatorAdder.add(data,
                MissingnessIndicatorAdder.Spec.defaults().withKnowledge(true, false)).data();
        assertFalse(allowed.getKnowledge().isForbidden("X1", "X1_missing"));
        assertTrue(allowed.getKnowledge().isForbidden("X1_missing", "X1"));
    }

    /**
     * Continuous indicators carry the same 0/1 coding.
     */
    @Test
    public void testContinuousIndicators() {
        DataSet data = fixture();
        DataSet out = MissingnessIndicatorAdder.add(data,
                MissingnessIndicatorAdder.Spec.defaults().withDiscreteIndicators(false)).data();

        Node indicator = out.getVariable("X1_missing");
        assertTrue(indicator instanceof ContinuousVariable);

        int column = out.getColumnIndex(indicator);
        int source = data.getColumnIndex(data.getVariable("X1"));

        for (int i = 0; i < data.getNumRows(); i++) {
            double expected = MissingDataAudit.isMissing(data, i, source) ? 1.0 : 0.0;
            assertEquals(expected, out.getDouble(i, column), 0.0);
        }
    }
}
