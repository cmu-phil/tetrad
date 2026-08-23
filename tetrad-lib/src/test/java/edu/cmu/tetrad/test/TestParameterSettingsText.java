package edu.cmu.tetrad.test;

import edu.cmu.tetrad.algcomparison.algorithm.multi.Images;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.Boss;
import edu.cmu.tetrad.algcomparison.score.SemBicScore;
import edu.cmu.tetrad.algcomparison.utils.ParameterSettingsText;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Pins the plain-text rendering of effective parameter settings used by the parameter
 * panel's "Settings as Text..." button: one {@code name = value} line per parameter, grouped
 * into annotated sections, with user-set values taking precedence over registered defaults.
 */
public class TestParameterSettingsText {

    @Test
    public void testUserSetValuesAndSections() {
        Boss algorithm = new Boss(new SemBicScore());
        Parameters parameters = new Parameters();
        parameters.set(Params.PENALTY_DISCOUNT, 2.5);
        parameters.set(Params.TIME_LAG, 3);
        parameters.set(Params.TIME_LAG_REPLICATING_GRAPH, true);

        String text = ParameterSettingsText.render(algorithm, parameters, false);

        assertTrue("Expected the user-set penalty:\n" + text,
                text.contains("penaltyDiscount = 2.5"));
        assertTrue("Expected the user-set time lag:\n" + text,
                text.contains("timeLag = 3"));
        assertTrue("Expected the user-set replicating flag:\n" + text,
                text.contains("timeLagReplicatingGraph = true"));
        assertTrue("Expected a bootstrapping section:\n" + text,
                text.contains("numberResampling = "));
        assertTrue("Expected name = value lines with defaults for unset parameters:\n" + text,
                text.contains(" = "));
    }

    @Test
    public void testSourceGraphSuppressesBootstrapping() {
        Boss algorithm = new Boss(new SemBicScore());
        String text = ParameterSettingsText.render(algorithm, new Parameters(), true);
        assertTrue("Expected the source-graph bootstrapping note:\n" + text,
                text.contains("unavailable"));
        assertFalse("Bootstrapping parameters should not be listed with a source graph:\n" + text,
                text.contains("numberResampling = "));
    }

    @Test
    public void testMultiDataSetAlgorithmRenders() {
        Images algorithm = new Images();
        algorithm.setScoreWrapper(new SemBicScore());
        Parameters parameters = new Parameters();
        parameters.set(Params.TIME_LAG, 4);

        String text = ParameterSettingsText.render(algorithm, parameters, false);

        assertTrue("Expected the algorithm section:\n" + text, text.contains("IMaGES"));
        assertTrue("Expected the time lag line:\n" + text, text.contains("timeLag = 4"));
    }
}
