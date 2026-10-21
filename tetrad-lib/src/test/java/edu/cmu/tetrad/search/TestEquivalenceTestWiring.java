package edu.cmu.tetrad.search;

import edu.cmu.tetrad.algcomparison.algorithm.oracle.pag.Ccd;
import edu.cmu.tetrad.algcomparison.independence.EquivalenceTest;
import edu.cmu.tetrad.algcomparison.independence.FisherZ;
import edu.cmu.tetrad.annotation.AnnotatedClass;
import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.annotation.TestOfIndependenceAnnotations;
import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.test.IndTestEquivalence;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.util.IndependenceTestDescriptions;
import edu.cmu.tetrad.util.ParamDescription;
import edu.cmu.tetrad.util.ParamDescriptions;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import org.junit.Test;

import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Wiring for the equivalence test as a user-selectable option: discovery by the annotation
 * scanner that populates the interface, resolution of its parameters from the HTML manual, and
 * the CCD algorithm's switch for applying it to the adjacency phase alone.
 */
public class TestEquivalenceTestWiring {

    private static DataSet continuousData(int n, long seed) {
        Random rng = new Random(seed);
        double[][] d = new double[n][4];

        for (int i = 0; i < n; i++) {
            double x1 = rng.nextGaussian();
            double x2 = 0.7 * x1 + rng.nextGaussian();
            double x3 = 0.6 * x2 + rng.nextGaussian();
            double x4 = 0.5 * x1 + 0.5 * x3 + rng.nextGaussian();
            d[i] = new double[]{x1, x2, x3, x4};
        }

        return new BoxDataSet(new DoubleDataBox(d), List.of(
                new ContinuousVariable("X1"), new ContinuousVariable("X2"),
                new ContinuousVariable("X3"), new ContinuousVariable("X4")));
    }

    /**
     * The interface's test list is built by scanning the algcomparison independence package for
     * the annotation, so the test is selectable only if the scanner finds it.
     */
    @Test
    public void theTestIsDiscoverableByTheAnnotationScanner() {
        boolean found = false;

        for (AnnotatedClass<TestOfIndependence> annotated
                : TestOfIndependenceAnnotations.getInstance().getAnnotatedClasses()) {
            if ("equivalence-test".equals(annotated.annotation().command())) {
                found = true;
                assertEquals("Equivalence (TOST)", annotated.annotation().name());
                assertEquals(EquivalenceTest.class, annotated.clazz());
            }
        }

        assertTrue("the equivalence test must be discoverable or it cannot be selected", found);
    }

    /**
     * Parameter descriptions are parsed from the HTML manual, and an unregistered name silently
     * resolves to a default of 0 - which the equivalence test would reject. These must come back
     * with their real defaults.
     */
    @Test
    public void theParametersResolveFromTheManual() {
        ParamDescription margin = ParamDescriptions.getInstance().get(Params.EQUIVALENCE_MARGIN);
        ParamDescription multiplier =
                ParamDescriptions.getInstance().get(Params.EQUIVALENCE_MARGIN_MULTIPLIER);
        ParamDescription use = ParamDescriptions.getInstance().get(Params.USE_EQUIVALENCE_REMOVAL);

        for (ParamDescription d : List.of(margin, multiplier, use)) {
            assertFalse("missing from the manual: " + d.getParamName(),
                    d.getShortDescription().startsWith("Please add"));
        }

        assertEquals(0.0, (Double) margin.getDefaultValue(), 1e-12);
        assertEquals(2.0, (Double) multiplier.getDefaultValue(), 1e-12);
        assertEquals(false, use.getDefaultValue());
    }

    /** A margin of 0 means "scale to the sample size"; an explicit margin is used as given. */
    @Test
    public void marginZeroScalesToTheSampleSizeAndAnExplicitMarginIsHonored() {
        DataSet data = continuousData(1000, 11L);
        Parameters parameters = new Parameters();
        parameters.set(Params.ALPHA, 0.05);
        parameters.set(Params.EQUIVALENCE_MARGIN, 0.0);
        parameters.set(Params.EQUIVALENCE_MARGIN_MULTIPLIER, 2.0);

        IndependenceTest auto = new EquivalenceTest().getTest(data, parameters);
        assertTrue(auto instanceof IndTestEquivalence);

        IndTestEquivalence scaled = (IndTestEquivalence) auto;
        double floor = scaled.smallestUsableMargin(1000, 0);
        assertEquals(2.0 * floor, scaled.getMargin(), 1e-9);

        parameters.set(Params.EQUIVALENCE_MARGIN, 0.123);
        IndTestEquivalence explicit =
                (IndTestEquivalence) new EquivalenceTest().getTest(data, parameters);
        assertEquals(0.123, explicit.getMargin(), 1e-12);
    }

    /** The builder used by both the wrapper and the CCD switch resolves the margin identically. */
    @Test
    public void theSharedBuilderResolvesTheMarginTheSameWay() {
        DataSet data = continuousData(2000, 13L);
        IndTestEquivalence auto = EquivalenceTest.build(
                new IndTestFisherZ(data, 0.05), 2000, 0.0, 2.0, 0.05);
        assertEquals(2.0 * auto.smallestUsableMargin(2000, 0), auto.getMargin(), 1e-9);

        IndTestEquivalence explicit = EquivalenceTest.build(
                new IndTestFisherZ(data, 0.05), 2000, 0.08, 2.0, 0.05);
        assertEquals(0.08, explicit.getMargin(), 1e-12);
    }

    /**
     * CCD must run from the algorithm wrapper without knowledge being set first. The wrapper's
     * knowledge field used to default to null and the search rejects null, so every run through
     * algcomparison failed with a NullPointerException.
     */
    @Test
    public void ccdRunsWithoutKnowledgeBeingSet() throws Exception {
        DataSet data = continuousData(500, 17L);
        Parameters parameters = new Parameters();
        parameters.set(Params.ALPHA, 0.05);
        parameters.set(Params.DEPTH, -1);
        parameters.set(Params.NUMBER_RESAMPLING, 0);

        Graph graph = new Ccd(new FisherZ()).search(new CovarianceMatrix(data), parameters);
        assertNotNull("CCD should run with no knowledge supplied", graph);
        assertEquals(4, graph.getNumNodes());
    }

    /** The switch is off by default, and turning it on still produces a graph over the variables. */
    @Test
    public void theCcdEquivalenceSwitchIsOffByDefaultAndWorksWhenOn() throws Exception {
        DataSet data = continuousData(1000, 19L);

        Parameters off = new Parameters();
        off.set(Params.ALPHA, 0.05);
        off.set(Params.DEPTH, -1);
        off.set(Params.NUMBER_RESAMPLING, 0);
        assertFalse("the switch must default to off",
                off.getBoolean(Params.USE_EQUIVALENCE_REMOVAL));

        Graph plain = new Ccd(new FisherZ()).search(new CovarianceMatrix(data), off);

        Parameters on = new Parameters();
        on.set(Params.ALPHA, 0.05);
        on.set(Params.DEPTH, -1);
        on.set(Params.NUMBER_RESAMPLING, 0);
        on.set(Params.USE_EQUIVALENCE_REMOVAL, true);

        Graph equivalence = new Ccd(new FisherZ()).search(new CovarianceMatrix(data), on);

        assertNotNull(plain);
        assertNotNull(equivalence);
        assertEquals(plain.getNumNodes(), equivalence.getNumNodes());
        assertTrue("the equivalence rule removes on positive evidence, so it should keep at least "
                        + "as many edges as the ordinary test here",
                equivalence.getNumEdges() >= plain.getNumEdges());
    }

    /** The CCD algorithm must offer the three parameters, or they cannot be set in the interface. */
    @Test
    public void theCcdAlgorithmOffersTheParameters() {
        List<String> parameters = new Ccd(new FisherZ()).getParameters();
        assertTrue(parameters.contains(Params.USE_EQUIVALENCE_REMOVAL));
        assertTrue(parameters.contains(Params.EQUIVALENCE_MARGIN));
        assertTrue(parameters.contains(Params.EQUIVALENCE_MARGIN_MULTIPLIER));
    }

    /** The wrapper must offer its own parameters too. */
    @Test
    public void theWrapperOffersItsParameters() {
        List<String> parameters = new EquivalenceTest().getParameters();
        assertTrue(parameters.contains(Params.ALPHA));
        assertTrue(parameters.contains(Params.EQUIVALENCE_MARGIN));
        assertTrue(parameters.contains(Params.EQUIVALENCE_MARGIN_MULTIPLIER));
    }

    /**
     * The description the interface shows for a test is read out of the HTML manual by the test's
     * command, so a missing entry is not a cosmetic problem: the interface displays "Please add a
     * description for equivalence-test." in place of the text.
     */
    @Test
    public void theManualCarriesADescriptionForTheTest() {
        String description = IndependenceTestDescriptions.getInstance().get("equivalence-test");

        assertFalse("the manual entry for equivalence-test is missing from index.html",
                description.startsWith("Please add"));
        assertTrue("the description should actually describe the test",
                description.contains("TOST") && description.contains("margin"));
    }

    /**
     * Every independence test selectable in the interface should have a manual entry, since the
     * lookup is by command and silently degrades to a placeholder.
     */
    @Test
    public void everySelectableTestHasAManualDescription() {
        List<String> missing = new java.util.ArrayList<>();

        for (AnnotatedClass<TestOfIndependence> annotated
                : TestOfIndependenceAnnotations.getInstance().getAnnotatedClasses()) {
            String command = annotated.annotation().command();
            if (IndependenceTestDescriptions.getInstance().get(command).startsWith("Please add")) {
                missing.add(command);
            }
        }

        assertTrue("tests with no description in index.html: " + missing, missing.isEmpty());
    }
}
