/// ////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetradapp.description;

import edu.cmu.tetrad.algcomparison.graph.RandomForward;
import edu.cmu.tetrad.algcomparison.simulation.SemSimulation;
import edu.cmu.tetrad.data.ContinuousDiscretizationSpec;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataModelList;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.data.DiscretizationSpec;
import edu.cmu.tetrad.data.Discretizer;
import edu.cmu.tetrad.data.SplitCasesSpec;
import edu.cmu.tetrad.data.UnmixSpec;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetradapp.model.DataWrapper;
import edu.cmu.tetradapp.model.GridSearchModel;
import edu.cmu.tetradapp.model.SessionNodeWrapper;
import edu.cmu.tetradapp.model.SessionWrapper;
import edu.cmu.tetradapp.model.datamanip.CausalUnmix;
import edu.cmu.tetradapp.model.datamanip.SelectionWrapper;
import edu.cmu.tetradapp.model.datamanip.SelectionWrapper.SelectionSpec;
import edu.cmu.tetradapp.model.datamanip.SplitCasesWrapper;
import edu.cmu.tetradapp.session.Session;
import edu.cmu.tetradapp.session.SessionNode;
import edu.cmu.tetradapp.ui.model.AlgorithmModel;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static edu.cmu.tetradapp.description.TestSessionDescription.DATE;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests the description, and the return on reloading, of three boxes whose choices are held as objects: which rows
 * a selection box keeps, how a split-cases box divides the rows, and what a grid search box compares. Before, each
 * of these was a "Not described" line, and the values a grid search box compares were not in the description at
 * all.
 *
 * @author Claude
 */
public class TestSessionDescriptionSelections {

    private static SessionNode node(SessionWrapper session, String name) {
        return ((SessionNodeWrapper) session.getNode(name)).getSessionNode();
    }

    private static Parameters bag(SessionNodeWrapper box, Class<?> modelClass) {
        return box.getSessionNode().getParam(modelClass);
    }

    /**
     * The test data with X2 cut at zero into two categories, so that there is a discrete variable to select by.
     */
    private static DataSet mixedData() throws Exception {
        DataSet data = TestSessionDescription.simulate();
        Map<Node, DiscretizationSpec> specs = new HashMap<>();
        specs.put(data.getVariable("X2"), new ContinuousDiscretizationSpec(new double[]{0.0}, List.of("Low", "High")));
        Discretizer discretizer = new Discretizer(data, specs);
        discretizer.setVariablesCopied(true);
        DataSet mixed = discretizer.discretize();
        mixed.setName("study.csv");
        return mixed;
    }

    private static void fillGrid(GridSearchModel model) {
        Parameters parameters = model.getParameters();
        GridSearchModel.SimulationSpec simulation
                = new GridSearchModel.SimulationSpec("name", RandomForward.class, SemSimulation.class);
        model.addSimulationSpec(simulation);
        model.setSelectedSimulation(simulation);
        model.addAlgorithm(new GridSearchModel.AlgorithmSpec("FGES",
                new AlgorithmModel(PendingDescription.findAlgorithm("fges")), null,
                PendingDescription.findScore("sem-bic-score")));
        model.addAlgorithm(new GridSearchModel.AlgorithmSpec("PC",
                new AlgorithmModel(PendingDescription.findAlgorithm("pc")),
                PendingDescription.findTest("fisher-z-test"), null));
        parameters.set(Params.SAMPLE_SIZE, 500, 1000);
        parameters.set(Params.PENALTY_DISCOUNT, 1.0, 2.0, 4.0);
        parameters.set(Params.ALPHA, 0.01, 0.05);
        parameters.set(Params.NUM_MEASURES, 20);

        // The Markov checker's test, with an alpha of its own, and a parameter its test does not take.
        try {
            model.setMarkovCheckerIndependenceWrapper((edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper)
                    PendingDescription.findTest("fisher-z-test").clazz().getConstructor().newInstance());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }

        model.getMarkovCheckerParameters().set(Params.ALPHA, 0.2);
        model.getMarkovCheckerParameters().set(Params.PENALTY_DISCOUNT, 7.0);

        for (GridSearchModel.MyTableColumn column : model.getAllTableColumns()) {
            boolean statistic = column.getType() == GridSearchModel.MyTableColumn.ColumnType.STATISTIC;

            if (statistic ? Set.of("AP", "AR").contains(column.getColumnName())
                    : Set.of(Params.SAMPLE_SIZE, Params.PENALTY_DISCOUNT).contains(column.getParameter())) {
                model.addTableColumn(column);
            }
        }
    }

    /**
     * Data; a selection of its rows by a continuous and a discrete variable; a split of its rows in three; and a
     * grid search box comparing two algorithms over a simulation.
     */
    private static SessionWrapper buildSession() throws Exception {
        SessionWrapper session = new SessionWrapper(new Session("study.tet"));
        DataSet dataSet = mixedData();

        SessionNodeWrapper data = TestSessionDescription.addBox(session, "Data", "Data1", 100, 100);
        data.getSessionNode().createModel(DataWrapper.class, false);
        ((DataWrapper) data.getSessionNode().getModel()).setDataModel(dataSet);

        SessionNodeWrapper selected = TestSessionDescription.addBox(session, "Data", "Selected", 100, 240);
        TestSessionDescription.connect(session, data, selected);
        Map<Node, SelectionSpec> specs = new HashMap<>();
        specs.put(dataSet.getVariable("X1"), SelectionSpec.continuous(List.of(new double[]{-0.5, 0.25},
                new double[]{1.0, Double.POSITIVE_INFINITY})));
        specs.put(dataSet.getVariable("X2"), SelectionSpec.discrete(Set.of(1)));
        bag(selected, SelectionWrapper.class).set("selectionSpecs", (Object) specs);
        bag(selected, SelectionWrapper.class).set("removeSelectionVariables", false);
        selected.getSessionNode().createModel(SelectionWrapper.class, false);

        SessionNodeWrapper split = TestSessionDescription.addBox(session, "Data", "Split", 260, 240);
        TestSessionDescription.connect(session, data, split);
        Parameters splitBag = bag(split, SplitCasesWrapper.class);
        splitBag.set("numSplits", 3);
        splitBag.set("dataShuffled", false);
        splitBag.set("splitCasesSpec", (Object) new SplitCasesSpec(200, new int[]{50, 120},
                new ArrayList<>(List.of("train", "tune", "test"))));
        split.getSessionNode().createModel(SplitCasesWrapper.class, false);

        SessionNodeWrapper grid = TestSessionDescription.addBox(session, "GridSearch", "Grid1", 420, 100);
        grid.getSessionNode().createModel(GridSearchModel.class, false);
        fillGrid((GridSearchModel) grid.getSessionNode().getModel());

        return session;
    }

    @Test
    public void testTheChoicesAreDescribed() throws Exception {
        String text = SessionDescriptionWriter.toText(new SessionDescriber().describe(buildSession(), DATE));
        System.out.println(text);

        assertTrue(text.contains("Select: X1; intervals: [-0.5, 0.25], [1.0, Infinity]\n"));
        assertTrue(text.contains("Select: X2; category numbers: 1; names: High\n"));
        assertTrue(text.contains("Setting: removeSelectionVariables = false\n"));

        assertTrue(text.contains("Split: 200 rows; break after rows: 50, 120; parts: train, tune, test\n"));
        assertTrue(text.contains("Changed: numSplits = 3 (default 2)\n"));
        assertTrue(text.contains("Changed: dataShuffled = false (default true)\n"));

        assertTrue(text.contains("Grid simulation: Linear Gaussian SEM simulation using Graph constructed by adding "
                                 + "random forward edges (edu.cmu.tetrad.algcomparison.simulation.SemSimulation on "
                                 + "edu.cmu.tetrad.algcomparison.graph.RandomForward)\n"));
        assertTrue(text.contains("Grid algorithm: FGES (fges); score: Sem BIC Score (sem-bic-score)\n"
                                 + "Grid algorithm: PC (pc); test: Fisher Z (fisher-z-test)\n"));
        assertTrue(text.contains("Grid column: penaltyDiscount (parameter)\n"));
        assertTrue(text.contains("Grid column: sampleSize (parameter)\n"));
        assertTrue(text.contains("Grid column: AP (statistic)\nGrid column: AR (statistic)\n"));

        // The values the box compares, which were not in a description before, and one parameter with one value.
        assertTrue(text.contains("Values: sampleSize = 500, 1000 (default 1000)\n"));
        assertTrue(text.contains("Values: penaltyDiscount = 1.0, 2.0, 4.0 (default 2.0)\n"));
        assertTrue(text.contains("Values: alpha = 0.01, 0.05 (default 0.01)\n"));
        assertTrue(text.contains("Changed: numMeasures = 20 (default 10)\n"));

        // The Markov checker's test and that test's own alpha, which is not the alpha of the algorithms. A
        // parameter the test does not take is not listed.
        assertTrue(text.contains("Grid Markov checker test: Fisher Z (fisher-z-test)\n"
                                 + "Grid Markov checker parameter: alpha = 0.2 (default 0.01)\n"));
        assertFalse(text.contains("Grid Markov checker parameter: penaltyDiscount"));

        // A setting the editor writes each time it opens and nothing reads is not passed off as a choice.
        SessionWrapper opened = buildSession();
        ((GridSearchModel) node(opened, "Grid1").getModel()).getParameters()
                .set("markovCheckerFactMode", "ORDERED_LOCAL_MARKOV");
        assertFalse(SessionDescriptionWriter.toText(new SessionDescriber().describe(opened, DATE))
                .contains("markovCheckerFactMode"));

        assertFalse(text, text.contains(StandardDescribers.NOT_DESCRIBED));
    }

    /**
     * A description reads back, and each box, given its model, is what it was: the same rows are selected, the same
     * rows are in each part, and the grid search box compares the same things.
     */
    @Test
    public void testTheChoicesComeBack() throws Exception {
        SessionWrapper session = buildSession();
        SessionDescription description = new SessionDescriber().describe(session, DATE);
        SessionRebuilder.Result result = SessionRebuilder.rebuild(
                SessionDescriptionParser.parse(SessionDescriptionWriter.toText(description)).description(),
                "study.tet");
        assertEquals(List.of(), result.problems());
        SessionWrapper rebuilt = result.session();

        node(rebuilt, "Data1").createModel(DataWrapper.class, false);
        ((DataWrapper) node(rebuilt, "Data1").getModel()).setDataModel(mixedData());

        for (String name : List.of("Selected", "Split", "Grid1")) {
            SessionNode node = node(rebuilt, name);
            node.createModel(PendingDescription.modelClass(node), false);
            assertEquals(name, List.of(), PendingDescription.apply(node));
        }

        DataSet was = (DataSet) ((DataWrapper) node(session, "Selected").getModel()).getSelectedDataModel();
        DataSet now = (DataSet) ((DataWrapper) node(rebuilt, "Selected").getModel()).getSelectedDataModel();
        assertTrue(was.getNumRows() > 0 && was.getNumRows() < 200);
        assertEquals(was.getNumRows(), now.getNumRows());
        assertEquals(was.getDouble(0, 0), now.getDouble(0, 0), 0.0);

        DataModelList partsWere = ((DataWrapper) node(session, "Split").getModel()).getDataModelList();
        DataModelList partsNow = ((DataWrapper) node(rebuilt, "Split").getModel()).getDataModelList();
        assertEquals(3, partsNow.size());

        for (int i = 0; i < 3; i++) {
            assertEquals(partsWere.get(i).getName(), partsNow.get(i).getName());
            assertEquals(((DataSet) partsWere.get(i)).getNumRows(), ((DataSet) partsNow.get(i)).getNumRows());
        }

        assertEquals(List.of(50, 70, 80), List.of(((DataSet) partsNow.get(0)).getNumRows(),
                ((DataSet) partsNow.get(1)).getNumRows(), ((DataSet) partsNow.get(2)).getNumRows()));

        GridSearchModel grid = (GridSearchModel) node(rebuilt, "Grid1").getModel();
        assertEquals(2, grid.getSelectedAlgorithms().size());
        assertEquals("fges", grid.getSelectedAlgorithms().get(0).getAlgorithm().getAlgorithm().annotation().command());
        assertEquals("sem-bic-score", grid.getSelectedAlgorithms().get(0).getScore().annotation().command());
        assertEquals("fisher-z-test", grid.getSelectedAlgorithms().get(1).getTest().annotation().command());
        assertEquals(SemSimulation.class, grid.getSelectedSimulation().getSimulationClass());
        assertEquals(RandomForward.class, grid.getSelectedSimulation().getGraphClass());
        assertEquals(4, grid.getSelectedTableColumns().size());

        // The values compared are whole numbers and decimal numbers as they were, which the box depends on.
        assertArrayEquals(new Object[]{500, 1000}, grid.getParameters().getValues(Params.SAMPLE_SIZE));
        assertArrayEquals(new Object[]{1.0, 2.0, 4.0}, grid.getParameters().getValues(Params.PENALTY_DISCOUNT));
        assertEquals(20, grid.getParameters().getInt(Params.NUM_MEASURES));

        // The Markov checker's test and its alpha, kept apart from the alpha values the box compares.
        assertEquals("fisher-z-test", grid.getMarkovCheckerIndependenceWrapper().getClass()
                .getAnnotation(edu.cmu.tetrad.annotation.TestOfIndependence.class).command());
        assertEquals(0.2, grid.getMarkovCheckerParameters().getDouble(Params.ALPHA), 0.0);
        assertArrayEquals(new Object[]{0.01, 0.05}, grid.getParameters().getValues(Params.ALPHA));

        SessionDescription again = new SessionDescriber().describe(rebuilt, DATE);

        for (String name : List.of("Data1", "Selected", "Split", "Grid1")) {
            assertEquals(name, description.getBox(name), again.getBox(name));
        }
    }

    /**
     * The lines read back, and what does not make sense is refused with the reason.
     */
    @Test
    public void testLinesReadBack() {
        Map<Node, SelectionSpec> specs = new HashMap<>();
        StandardDescribers.readSelectLine("Income; intervals: [-Infinity, 20000.0], [50000.0, 60000.5]", specs);
        StandardDescribers.readSelectLine("Region; category numbers: 0, 2; names: north, west", specs);
        StandardDescribers.readSelectLine("Wave; category numbers: 3", specs);
        assertEquals(3, specs.size());

        for (Map.Entry<Node, SelectionSpec> entry : specs.entrySet()) {
            switch (entry.getKey().getName()) {
                case "Income" -> {
                    assertTrue(entry.getKey() instanceof ContinuousVariable);
                    assertTrue(entry.getValue().acceptsContinuous(-1e9));
                    assertTrue(entry.getValue().acceptsContinuous(55000));
                    assertFalse(entry.getValue().acceptsContinuous(30000));
                    assertEquals("Income; intervals: [-Infinity, 20000.0], [50000.0, 60000.5]",
                            StandardDescribers.selectLine(entry.getKey(), entry.getValue()));
                }
                case "Region" -> {
                    assertTrue(entry.getKey() instanceof DiscreteVariable);
                    assertEquals(Set.of(0, 2), entry.getValue().getAcceptedCategories());
                }
                case "Wave" -> assertEquals(Set.of(3), entry.getValue().getAcceptedCategories());
                default -> fail(entry.getKey().getName());
            }
        }

        for (String bad : List.of("X1", "X1; intervals: ", "X1; intervals: [2.0, 1.0]", "X1; intervals: [a, b]",
                "X2; category numbers: ", "X2; category numbers: -1", "X2; category numbers: one")) {
            try {
                StandardDescribers.readSelectLine(bad, new HashMap<>());
                fail(bad + " should not be read.");
            } catch (IllegalArgumentException expected) {
                // As it should be.
            }
        }

        SplitCasesSpec one = StandardDescribers.readSplitLine("80 rows; break after rows: none; parts: same_data");
        assertEquals(80, one.getSampleSize());
        assertEquals(0, one.getBreakpoints().length);
        assertEquals(List.of("same_data"), one.getSplitNames());
        assertEquals("80 rows; break after rows: none; parts: same_data", StandardDescribers.splitLine(one));

        for (String bad : List.of("80 rows", "80 rows; break after rows: 40; parts: a",
                "80 rows; break after rows: 90; parts: a, b", "80 rows; break after rows: 60, 40; parts: a, b, c",
                "many rows; break after rows: 40; parts: a, b")) {
            try {
                StandardDescribers.readSplitLine(bad);
                fail(bad + " should not be read.");
            } catch (IllegalArgumentException expected) {
                // As it should be.
            }
        }
    }

    /**
     * A causal unmix box says how many components it divides the rows into and which settings of the unmixing were
     * changed, and a description of one gives the box the same specification back. The box is not run here, since
     * running it opens a progress window.
     */
    @Test
    public void testUnmixIsDescribedAndComesBack() throws Exception {
        UnmixSpec byDefault = new UnmixSpec();
        assertEquals(List.of("components: chosen by BIC from 1 to 4"), StandardDescribers.unmixLines(byDefault));

        UnmixSpec spec = new UnmixSpec().setAutoSelectK(false).setK(3).setSupersetTopM(20)
                .setSupersetScore(UnmixSpec.SupersetScore.SPEARMAN).setRobustScaleResiduals(false)
                .setCovarianceMode(UnmixSpec.CovarianceMode.DIAGONAL).setRidge(0.01).setSaveDiagnostics(true);
        spec.setLogIntermediate(true);
        List<String> lines = StandardDescribers.unmixLines(spec);
        assertEquals(List.of("components: 3", "supersetTopM = 20 (default 12)",
                "supersetScore = SPEARMAN (default KENDALL)", "robustScaleResiduals = false (default true)",
                "covarianceMode = DIAGONAL (default AUTO)", "ridge = 0.01 (default 0.001)",
                "saveDiagnostics = true (default false)"), lines);

        // The describer writes those lines for the box, ahead of what a data box says, and the specification is
        // not left as a setting that could not be described.
        SessionWrapper session = new SessionWrapper(new Session("study.tet"));
        SessionNodeWrapper box = TestSessionDescription.addBox(session, "Data", "Unmixed", 100, 100);
        box.getSessionNode().createModel(DataWrapper.class, false);
        ((DataWrapper) box.getSessionNode().getModel()).setDataModel(mixedData());
        bag(box, DataWrapper.class).set("unmixSpec", (Object) spec);
        BoxDescription.Builder out = new BoxDescription.Builder("Unmixed");
        StandardDescribers.describeUnmix(box.getSessionNode().getModel(), box.getSessionNode(), out);
        BoxDescription described = out.build();
        assertEquals(lines, described.getAll(StandardDescribers.UNMIX));
        assertTrue(described.getAll(StandardDescribers.NOT_DESCRIBED).isEmpty());
        assertEquals("study.csv", described.get(StandardDescribers.DATA_SET));

        // A description of a causal unmix box, read back, gives the box that specification.
        BoxDescription.Builder data = new BoxDescription.Builder("Data1").field(BoxDescription.TYPE, "Data")
                .field(BoxDescription.MODEL, "Data");
        BoxDescription.Builder unmix = new BoxDescription.Builder("Unmixed").field(BoxDescription.TYPE, "Data")
                .field(BoxDescription.MODEL, "Causal Unmix").field(BoxDescription.PARENTS, "Data1");

        for (String line : lines) {
            unmix.field(StandardDescribers.UNMIX, line);
        }

        SessionDescription description = new SessionDescription(
                new SessionDescriber().describe(session, DATE).getHeader(), List.of(data.build(), unmix.build()));
        SessionRebuilder.Result result = SessionRebuilder.rebuild(
                SessionDescriptionParser.parse(SessionDescriptionWriter.toText(description)).description(),
                "study.tet");
        assertEquals(List.of(), result.problems());
        assertEquals(CausalUnmix.class, PendingDescription.modelClass(node(result.session(), "Unmixed")));

        Object back = node(result.session(), "Unmixed").getParam(CausalUnmix.class).getValues("unmixSpec")[0];
        assertTrue(back instanceof UnmixSpec);
        assertEquals(lines, StandardDescribers.unmixLines((UnmixSpec) back));
        assertFalse(((UnmixSpec) back).isAutoSelectK());
        assertEquals(3, ((UnmixSpec) back).getK());
        assertTrue(((UnmixSpec) back).isLogIntermediate());

        // A range of components comes back as a range.
        UnmixSpec range = new UnmixSpec();
        StandardDescribers.readUnmixLine("components: chosen by BIC from 2 to 6", range);
        assertTrue(range.isAutoSelectK());
        assertEquals(2, range.getKmin());
        assertEquals(6, range.getKmax());

        for (String bad : List.of("components: several", "components: chosen by BIC from 4 to 2",
                "components: chosen by BIC from 2", "components: 0", "supersetTopM = many (default 12)",
                "supersetScore = PEARSON (default KENDALL)", "robustScaleResiduals = perhaps (default true)",
                "randomSeed = 5 (default 13)", "no setting at all")) {
            try {
                StandardDescribers.readUnmixLine(bad, new UnmixSpec());
                fail(bad + " should not be read.");
            } catch (IllegalArgumentException expected) {
                // As it should be.
            }
        }
    }

    /**
     * A split set up for one number of rows is not applied to data with another: the box says so, where it used to
     * give parts of the wrong sizes or fail with an index out of range.
     */
    @Test
    public void testASplitForOtherDataIsRefused() throws Exception {
        DataWrapper data = new DataWrapper(mixedData());
        Parameters parameters = new Parameters();
        parameters.set("numSplits", 2);
        parameters.set("dataShuffled", false);
        parameters.set("splitCasesSpec", (Object) new SplitCasesSpec(150, new int[]{75},
                new ArrayList<>(List.of("train", "test"))));

        try {
            new SplitCasesWrapper(data, parameters);
            fail("A split for 150 rows should not be applied to 200.");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("set up for 150 rows"));
            assertTrue(e.getMessage(), e.getMessage().contains("the data have 200 rows"));
        }

        Parameters none = new Parameters();
        none.set("numSplits", 2);

        try {
            new SplitCasesWrapper(data, none);
            fail("A box with no split set up should say so.");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().startsWith("No split has been set up for this box."));
        }
    }
}
