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
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteDiscretizationSpec;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.data.DiscretizationSpec;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.ConditioningSetType;
import edu.cmu.tetrad.sem.ScoreType;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetradapp.model.DataWrapper;
import edu.cmu.tetradapp.model.GraphWrapper;
import edu.cmu.tetradapp.model.MarkovCheckIndTestModel;
import edu.cmu.tetradapp.model.RegressionRunner;
import edu.cmu.tetradapp.model.SessionNodeWrapper;
import edu.cmu.tetradapp.model.SessionWrapper;
import edu.cmu.tetradapp.model.Simulation;
import edu.cmu.tetradapp.model.datamanip.DataSubsetter;
import edu.cmu.tetradapp.model.datamanip.DiscretizationWrapper;
import edu.cmu.tetradapp.model.datamanip.TimeSeriesWrapper;
import edu.cmu.tetradapp.session.Session;
import edu.cmu.tetradapp.session.SessionNode;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static edu.cmu.tetradapp.description.TestSessionDescription.DATE;
import static org.junit.Assert.*;

/**
 * Tests the description, and the return on reloading, of what boxes other than search boxes hold: settings that have
 * no registered default, and the choices of regression, discretization, Markov check and simulation boxes.
 *
 * @author Claude
 */
public class TestSessionDescriptionSettings {

    private static SessionNode node(SessionWrapper session, String name) {
        return ((SessionNodeWrapper) session.getNode(name)).getSessionNode();
    }

    private static Parameters bag(SessionNodeWrapper box, Class<?> modelClass) {
        return box.getSessionNode().getParam(modelClass);
    }

    /**
     * Builds a session of data handling and checking boxes around one data set, each with its settings made.
     */
    private static SessionWrapper buildSession() throws Exception {
        SessionWrapper session = new SessionWrapper(new Session("study.tet"));
        DataSet dataSet = TestSessionDescription.simulate();

        SessionNodeWrapper data = TestSessionDescription.addBox(session, "Data", "Data1", 100, 100);
        data.getSessionNode().createModel(DataWrapper.class, false);
        ((DataWrapper) data.getSessionNode().getModel()).setDataModel(dataSet);

        // Time lags: one whole number. The same box is also given the kinds of setting that take several lines,
        // under the names the subset box keeps its own under: a list of names, text of several lines, and a
        // parameter with several values. (The subset box itself cannot be made without the Tetrad window.)
        SessionNodeWrapper lag = TestSessionDescription.addBox(session, "Data", "Lagged", 100, 240);
        TestSessionDescription.connect(session, data, lag);
        Parameters lagBag = bag(lag, TimeSeriesWrapper.class);
        lagBag.set("numTimeLags", 2);
        lagBag.set(DataSubsetter.KEY_SELECTED_VAR_NAMES, (Object) new ArrayList<>(List.of("X1", "X3", "X4")));
        lagBag.set(DataSubsetter.KEY_ROW_SPEC, "1-150");
        lagBag.set("someNotes", "first line\n  second line, indented\nEnd text someNotes");
        lagBag.set("someChoices", "one", "two", "three");
        lag.getSessionNode().createModel(TimeSeriesWrapper.class, false);

        // Discretization: one variable, two categories.
        SessionNodeWrapper discretize = TestSessionDescription.addBox(session, "Data", "Discretized", 420, 240);
        TestSessionDescription.connect(session, data, discretize);
        Map<Node, DiscretizationSpec> specs = new HashMap<>();
        specs.put(dataSet.getVariable("X2"), new ContinuousDiscretizationSpec(new double[]{0.25},
                List.of("Low", "High"), ContinuousDiscretizationSpec.EVENLY_DISTRIBUTED_VALUES));
        bag(discretize, DiscretizationWrapper.class).set("discretizationSpecs", (Object) specs);
        discretize.getSessionNode().createModel(DiscretizationWrapper.class, false);

        // Regression: a target, regressors, and a registered parameter changed.
        SessionNodeWrapper regression = TestSessionDescription.addBox(session, "Regression", "Regression1", 580, 240);
        TestSessionDescription.connect(session, data, regression);
        bag(regression, RegressionRunner.class).set(Params.ALPHA, 0.05);
        regression.getSessionNode().createModel(RegressionRunner.class, false);
        RegressionRunner runner = (RegressionRunner) regression.getSessionNode().getModel();
        runner.setTargetName("X3");
        runner.setRegressorName(new ArrayList<>(List.of("X1", "X2")));

        // Markov check: a graph, the test by name, the conditioning sets as a named constant, a decimal number.
        SessionNodeWrapper graph = TestSessionDescription.addBox(session, "Graph", "Graph1", 100, 380);
        graph.getSessionNode().createModel(GraphWrapper.class, false);
        Graph g = new EdgeListGraph();

        for (Node variable : dataSet.getVariables()) {
            g.addNode(new GraphNode(variable.getName()));
        }

        g.addDirectedEdge(g.getNode("X1"), g.getNode("X2"));
        g.addDirectedEdge(g.getNode("X2"), g.getNode("X3"));
        ((GraphWrapper) graph.getSessionNode().getModel()).setGraph(g);

        SessionNodeWrapper markov = TestSessionDescription.addBox(session, "Compare", "Markov1", 260, 380);
        TestSessionDescription.connect(session, data, markov);
        TestSessionDescription.connect(session, graph, markov);
        markov.getSessionNode().createModel(MarkovCheckIndTestModel.class, false);
        MarkovCheckIndTestModel check = (MarkovCheckIndTestModel) markov.getSessionNode().getModel();
        check.setConditioningSetType(ConditioningSetType.MARKOV_BLANKET);
        Parameters markovBag = bag(markov, MarkovCheckIndTestModel.class);
        markovBag.set("independenceTestClassParam", "Fisher Z Test");
        markovBag.set("fractionSampleParam", 0.8);
        markovBag.set(Params.ALPHA, 0.001);

        // The interface's own bookkeeping, data, and an object no description can hold.
        markovBag.set("selectedTabParam", 1);
        markovBag.set("ui.search.algo", "PC");
        markovBag.set("__referenceSessionModel", "Graph1");
        markovBag.set("dataSet", (Object) dataSet);
        markovBag.set("someSpec", (Object) new HashMap<String, Integer>());

        // Simulation: the kind of simulation and a parameter changed.
        SessionNodeWrapper simulation = TestSessionDescription.addBox(session, "Simulation", "Simulation1", 580, 380);
        simulation.getSessionNode().createModel(Simulation.class, false);
        Simulation sim = (Simulation) simulation.getSessionNode().getModel();
        sim.setSimulation(new SemSimulation(new RandomForward()));
        sim.getParams().set(Params.SAMPLE_SIZE, 250);

        return session;
    }

    @Test
    public void testWhatEachBoxSays() throws Exception {
        SessionDescription description = new SessionDescriber().describe(buildSession(), DATE);
        System.out.println(SessionDescriptionWriter.toText(description));

        BoxDescription lagged = description.getBox("Lagged");
        assertEquals(List.of("dataSubsetRowSpec = \"1-150\"", "numTimeLags = 2"),
                lagged.getAll(StandardDescribers.SETTING));
        assertEquals(List.of("X1", "X3", "X4"),
                lagged.getBlock(StandardDescribers.LIST_BLOCK + "dataSubsetSelectedVarNames"));
        assertEquals(List.of("first line", "  second line, indented", "End text someNotes"),
                lagged.getBlock(StandardDescribers.TEXT_BLOCK + "someNotes"));
        assertEquals(List.of("one", "two", "three"),
                lagged.getBlock(StandardDescribers.VALUES_BLOCK + "someChoices"));

        BoxDescription discretize = description.getBox("Discretized");
        assertEquals(List.of("X2; categories: Low, High; breakpoints: 0.25; chosen by: Evenly Distributed Values"),
                discretize.getAll(StandardDescribers.DISCRETIZE));
        assertTrue(discretize.getAll(StandardDescribers.NOT_DESCRIBED).isEmpty());

        BoxDescription regression = description.getBox("Regression1");
        assertEquals("X3", regression.get(StandardDescribers.TARGET));
        assertEquals("X1, X2", regression.get(StandardDescribers.REGRESSORS));
        assertEquals(List.of(new BoxDescription.ChangedParameter("alpha", "0.05", "0.01")),
                regression.getChangedParameters());

        BoxDescription markov = description.getBox("Markov1");
        assertEquals("Markov Check", markov.get(BoxDescription.MODEL));
        assertEquals(List.of(
                        "conditioningSetTypeParam = MARKOV_BLANKET (enum edu.cmu.tetrad.search.ConditioningSetType)",
                        "fractionSampleParam = 0.8",
                        "independenceTestClassParam = \"Fisher Z Test\""),
                markov.getAll(StandardDescribers.SETTING));
        assertEquals(List.of(new BoxDescription.ChangedParameter("alpha", "0.001", "0.01")),
                markov.getChangedParameters());
        assertEquals("Only the object that is neither data nor bookkeeping is reported.", List.of("someSpec"),
                markov.getAll(StandardDescribers.NOT_DESCRIBED));

        BoxDescription simulation = description.getBox("Simulation1");
        assertNotNull(simulation.get(StandardDescribers.SIMULATION));
        assertTrue(simulation.getChangedParameters().contains(
                new BoxDescription.ChangedParameter("sampleSize", "250", "1000")));
        assertNull("Simulated data sets are results and are not listed.",
                simulation.get(StandardDescribers.DATA_SET));

        // It all reads back.
        String text = SessionDescriptionWriter.toText(description);
        SessionDescriptionParser.Result parsed = SessionDescriptionParser.parse(text);
        assertEquals(List.of(), parsed.warnings());
        assertEquals(description, parsed.description());
    }

    /**
     * On reloading, each setting is back in its box's parameters as the kind of value it was, so that the box's
     * model finds it when it is made.
     */
    @Test
    public void testSettingsAreBackAsTheKindOfValueTheyWere() throws Exception {
        SessionDescription description = new SessionDescriber().describe(buildSession(), DATE);
        SessionRebuilder.Result result = SessionRebuilder.rebuild(
                SessionDescriptionParser.parse(SessionDescriptionWriter.toText(description)).description(),
                "study.tet");
        SessionWrapper rebuilt = result.session();

        assertEquals(List.of("Markov1: the setting \"someSpec\" is of a kind a description cannot hold; set it "
                             + "again in the box."), result.problems());

        Parameters lag = node(rebuilt, "Lagged").getParam(TimeSeriesWrapper.class);
        assertEquals(Integer.valueOf(2), lag.getValues("numTimeLags")[0]);
        assertEquals(new ArrayList<>(List.of("X1", "X3", "X4")),
                lag.getValues(DataSubsetter.KEY_SELECTED_VAR_NAMES)[0]);
        assertEquals("1-150", lag.getValues(DataSubsetter.KEY_ROW_SPEC)[0]);
        assertEquals("first line\n  second line, indented\nEnd text someNotes", lag.getValues("someNotes")[0]);
        assertArrayEquals(new Object[]{"one", "two", "three"}, lag.getValues("someChoices"));

        Parameters markov = node(rebuilt, "Markov1").getParam(MarkovCheckIndTestModel.class);
        assertEquals(ConditioningSetType.MARKOV_BLANKET, markov.getValues("conditioningSetTypeParam")[0]);
        assertEquals(Double.valueOf(0.8), markov.getValues("fractionSampleParam")[0]);
        assertEquals("Fisher Z Test", markov.getValues("independenceTestClassParam")[0]);
        assertEquals(0.001, markov.getDouble(Params.ALPHA), 0.0);

        @SuppressWarnings("unchecked")
        Map<Node, DiscretizationSpec> specs = (Map<Node, DiscretizationSpec>)
                node(rebuilt, "Discretized").getParam(DiscretizationWrapper.class).getValues("discretizationSpecs")[0];
        ContinuousDiscretizationSpec spec = (ContinuousDiscretizationSpec) specs.get(new ContinuousVariable("X2"));
        assertArrayEquals(new double[]{0.25}, spec.getBreakpoints(), 0.0);
        assertEquals(List.of("Low", "High"), spec.getCategories());
        assertEquals(ContinuousDiscretizationSpec.EVENLY_DISTRIBUTED_VALUES, spec.getMethod());
    }

    /**
     * With the data loaded again and the boxes given their models, the session describes itself as it did.
     */
    @Test
    public void testSessionComesBackWhenBoxesGetModels() throws Exception {
        SessionDescription description = new SessionDescriber().describe(buildSession(), DATE);
        SessionWrapper rebuilt = SessionRebuilder.rebuild(
                SessionDescriptionParser.parse(SessionDescriptionWriter.toText(description)).description(),
                "study.tet").session();

        node(rebuilt, "Data1").createModel(DataWrapper.class, false);
        ((DataWrapper) node(rebuilt, "Data1").getModel()).setDataModel(TestSessionDescription.simulate());

        for (String name : List.of("Lagged", "Discretized", "Regression1", "Markov1")) {
            SessionNode node = node(rebuilt, name);
            node.createModel(PendingDescription.modelClass(node), false);
            assertEquals(name, List.of(), PendingDescription.apply(node));
        }

        RegressionRunner runner = (RegressionRunner) node(rebuilt, "Regression1").getModel();
        assertEquals("X3", runner.getTargetName());
        assertEquals(List.of("X1", "X2"), runner.getRegressorNames());

        MarkovCheckIndTestModel check = (MarkovCheckIndTestModel) node(rebuilt, "Markov1").getModel();
        assertEquals(ConditioningSetType.MARKOV_BLANKET, check.getConditioningSetType());

        // The discretized data have X2 in two categories.
        DataSet discretized = (DataSet) ((DataWrapper) node(rebuilt, "Discretized").getModel()).getSelectedDataModel();
        assertEquals(List.of("Low", "High"), ((DiscreteVariable) discretized.getVariable("X2")).getCategories());

        // The lagged data have two lags of each of the four variables besides the variables themselves.
        DataSet lagged = (DataSet) ((DataWrapper) node(rebuilt, "Lagged").getModel()).getSelectedDataModel();
        assertEquals(12, lagged.getNumColumns());

        SessionDescription now = new SessionDescriber().describe(rebuilt, DATE);

        for (String name : List.of("Data1", "Lagged", "Discretized", "Regression1", "Graph1")) {
            assertEquals(name, description.getBox(name), now.getBox(name));
        }

        // The Markov check box is as it was but for the one object the description could not hold, which the
        // reloaded box does not have.
        List<BoxDescription.Item> was = new ArrayList<>(description.getBox("Markov1").getItems());
        assertTrue(was.remove(new BoxDescription.Field(StandardDescribers.NOT_DESCRIBED, "someSpec")));
        assertEquals(was, now.getBox("Markov1").getItems());
    }

    /**
     * Each kind of value is written so that its kind can be read back.
     */
    @Test
    public void testKindsOfValue() {
        Object[] values = {"MICE", "", "2", "true", "say \"hi\"", "a\\b", "x = y (default z)", 0, 3, -7, 42L, -1L,
                2.0, 0.0001, -0.5, 1.0E20, Double.NaN, Double.POSITIVE_INFINITY, true, false, ScoreType.Fgls,
                ConditioningSetType.MARKOV_BLANKET};

        for (Object value : values) {
            String text = SettingLiteral.write(value);
            assertNotNull(String.valueOf(value), text);
            Object back = SettingLiteral.read(text);
            assertEquals(text, value, back);
            assertEquals(text, value.getClass(), back.getClass());
        }

        assertEquals("\"2\"", SettingLiteral.write("2"));
        assertEquals("2", SettingLiteral.write(2));
        assertEquals("2L", SettingLiteral.write(2L));
        assertEquals("2.0", SettingLiteral.write(2.0));

        // What cannot be written on one line and read back is refused, not guessed at.
        assertNull(SettingLiteral.write("two\nlines"));
        assertNull(SettingLiteral.write(List.of("a")));
        assertNull(SettingLiteral.write(1.5f));

        for (String bad : List.of("MICE", "1.2.3", "Fgls (enum no.such.Type)", "x (enum java.lang.String)")) {
            try {
                SettingLiteral.read(bad);
                fail(bad + " should not be read.");
            } catch (IllegalArgumentException expected) {
                // As it should be.
            }
        }
    }

    /**
     * How a variable is discretized reads back, and what does not make sense is refused with the reason.
     */
    @Test
    public void testDiscretizationLines() {
        Map<Node, DiscretizationSpec> specs = new HashMap<>();
        String continuous = StandardDescribers.discretizeLine("age", new ContinuousDiscretizationSpec(
                new double[]{30.0, 50.5}, List.of("Young", "Middle", "Old"),
                ContinuousDiscretizationSpec.EVENLY_DISTRIBUTED_INTERVALS));
        assertEquals("age; categories: Young, Middle, Old; breakpoints: 30.0, 50.5; chosen by: "
                     + "Evenly Distributed Intervals", continuous);
        StandardDescribers.readDiscretizeLine(continuous, specs);
        assertEquals(continuous, StandardDescribers.discretizeLine("age", specs.get(new ContinuousVariable("age"))));

        String discrete = StandardDescribers.discretizeLine("grade", new DiscreteDiscretizationSpec(
                new int[]{0, 0, 1}, List.of("Fail", "Pass")));
        assertEquals("grade; categories: Fail, Pass; old categories become: 0, 0, 1", discrete);
        Map<Node, DiscretizationSpec> more = new HashMap<>();
        StandardDescribers.readDiscretizeLine(discrete, more);
        assertEquals(discrete, StandardDescribers.discretizeLine("grade", more.values().iterator().next()));

        assertNull("A variable left as it is has no line.", StandardDescribers.discretizeLine("x",
                new ContinuousDiscretizationSpec(new double[]{0.0}, List.of("a", "b"),
                        ContinuousDiscretizationSpec.NONE)));

        for (String bad : List.of(
                "age",
                "age; categories: Young, Old; breakpoints: 30.0, 50.5; chosen by: Evenly Distributed Values",
                "age; categories: Young, Old; breakpoints: thirty; chosen by: Evenly Distributed Values",
                "age; categories: Young, Old; breakpoints: 30.0; chosen by: Guesswork",
                "grade; categories: Fail, Pass; old categories become: 0, 2")) {
            try {
                StandardDescribers.readDiscretizeLine(bad, new HashMap<>());
                fail(bad + " should not be read.");
            } catch (IllegalArgumentException expected) {
                // As it should be.
            }
        }
    }
}
