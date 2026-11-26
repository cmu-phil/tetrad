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

import edu.cmu.tetrad.algcomparison.algorithm.MultiDataSetAlgorithm;
import edu.cmu.tetrad.annotation.AlgorithmAnnotations;
import edu.cmu.tetrad.annotation.AnnotatedClass;
import edu.cmu.tetradapp.model.DataWrapper;
import edu.cmu.tetradapp.model.SessionNodeWrapper;
import edu.cmu.tetradapp.model.SessionWrapper;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The py-tetrad script written from a session description: what it holds for the chain from data through knowledge
 * to a search, and what it says about the boxes it does not cover.
 * <p>
 * These tests read the script as text. That the scripts run in Python, and give the graphs the sessions gave, was
 * checked by hand when the writer was made; it cannot be checked here, since it needs py-tetrad.
 *
 * @author Claude (Anthropic)
 */
public class TestPyTetradScript {

    private static final String START = "Tetrad session description, format 1\n\n";
    private static final String END = "End of session description\n";

    private static PyTetradScript.Result script(String boxes) {
        return PyTetradScript.render(SessionDescriptionParser.parse(START + boxes + END).description());
    }

    /**
     * A data box as described when its file's settings were recorded, with the given settings in place of the
     * usual ones.
     */
    private static String dataBox(String name, String... changes) {
        Map<String, String> settings = new LinkedHashMap<>();
        settings.put("File", "study.csv");
        settings.put("First row variable names", "Yes");
        settings.put("Data file type", "Tabular data");
        settings.put("Data type", "Continuous");
        settings.put("Value delimiter", "Comma");
        settings.put("Case column to exclude/ignore", "None");
        settings.put("Comment marker", "//");
        settings.put("Quote character", "\"");
        settings.put("Missing value marker", "*");

        for (int i = 0; i < changes.length; i += 2) {
            if (changes[i + 1] == null) {
                settings.remove(changes[i]);
            } else {
                settings.put(changes[i], changes[i + 1]);
            }
        }

        StringBuilder box = new StringBuilder("[Box " + name + "]\nType: Data\nLocation: 100, 100\n"
                                              + "Data set: study.csv\n");
        settings.forEach((key, value) -> box.append(key).append(": ").append(value).append("\n"));
        return box.append("Shape: 200 rows, 4 columns, continuous\nMissing values: none\n\n").toString();
    }

    private static String searchBox(String name, String parents, String... lines) {
        StringBuilder box = new StringBuilder("[Box " + name + "]\nType: Search\nParents: " + parents
                                              + "\nLocation: 300, 100\n");

        for (String line : lines) {
            box.append(line).append("\n");
        }

        return box.append("\n").toString();
    }

    private static final String FGES = "Algorithm: FGES (fges)";
    private static final String SEM_BIC = "Score: Sem BIC Score (sem-bic-score)";

    /**
     * The session the other description tests use, with the settings its data file was read with recorded, as
     * they are when a file is loaded in the interface.
     */
    @Test
    public void testTheChainFromDataToSearch() throws Exception {
        SessionWrapper session = TestSessionDescription.buildSession();
        DataWrapper data = (DataWrapper) ((SessionNodeWrapper) session.getNode("Data1")).getSessionNode().getModel();
        Map<String, String> settings = new LinkedHashMap<>();
        settings.put("File", "study.csv");
        settings.put("First row variable names", "Yes");
        settings.put("Data file type", "Tabular data");
        settings.put("Data type", "Continuous");
        settings.put("Value delimiter", "Comma");
        settings.put("Case column to exclude/ignore", "None");
        settings.put("Comment marker", "//");
        settings.put("Quote character", "\"");
        settings.put("Missing value marker", "*");
        data.setLoadSettings("study.csv", settings);

        SessionDescription description = new SessionDescriber().describe(session, TestSessionDescription.DATE);
        PyTetradScript.Result result = PyTetradScript.render(description);
        String script = result.script();
        System.out.println(script);

        // Java is started by py-tetrad, before any Java import.
        assertTrue(script.indexOf("import pytetrad.tools.translate") < script.indexOf("from edu.cmu.tetrad"));
        assertTrue(script.contains("from edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag import Fges\n"));
        assertTrue(script.contains("from edu.cmu.tetrad.algcomparison.score import SemBicScore\n"));

        // The data, read as the Load Data dialog read them.
        assertTrue(script.contains("# In the session these data were: 200 rows, 4 columns, continuous.\n"));
        assertTrue(script.contains("""
                data1 = read_tabular(
                    "study.csv",
                    delimiter=Delimiter.COMMA,
                    kind="continuous",
                    has_header=True,
                    comment_marker="//",
                    quote_character='"',
                    missing_marker="*",
                )
                """));

        // The knowledge, as text Tetrad's own reader takes.
        assertTrue(script.contains("knowledge1_text = \"\"\"\\\n/knowledge\naddtemporal\n"));
        assertTrue(script.contains("forbiddirect\nX4 X3\n"));
        assertTrue(script.contains("knowledge1 = SimpleDataLoader.loadKnowledge(StringReader(knowledge1_text), "
                                   + "DelimiterType.WHITESPACE, \"//\")\n"));

        // The search: only the parameters that were changed, each of its own kind; then score, knowledge, search.
        assertTrue(script.contains("""
                # Algorithm: FGES (fges). Score: Sem BIC Score (sem-bic-score).
                search1_parameters = Parameters()
                search1_parameters.set("maxDegree", JInt(5))  # default 1000
                search1_parameters.set("penaltyDiscount", 4.0)  # default 2.0
                search1_algorithm = Fges()
                search1_algorithm.setScoreWrapper(SemBicScore())
                search1_algorithm.setKnowledge(knowledge1)
                search1_graph = search1_algorithm.search(data1, search1_parameters)
                print('Box "Search1", FGES (fges):')
                print(search1_graph)
                save_graph(search1_graph, "search1_graph.txt")
                """));

        // The graph is saved as well as printed, in the form a Graph box loads, in a folder the user can set.
        assertTrue(script.contains("OUTPUT_DIR = \".\"\n"));
        assertTrue(script.contains("def save_graph(graph, file_name):\n"));
        assertTrue(script.contains("    GraphSaveLoadUtils.saveGraph(graph, File(path), False)\n"));
        assertTrue(script.contains("from edu.cmu.tetrad.graph import GraphSaveLoadUtils\n"));
        assertTrue(script.contains("from java.io import File"));
        assertTrue(script.indexOf("def save_graph(") < script.indexOf("save_graph(search1_graph"));
        assertTrue(result.preview("study").contains("Its graph is saved as search1_graph.txt."));
        assertFalse("A parameter of another algorithm is not set.", script.contains("\"alpha\""));
        assertFalse(script.contains("\"numStarts\""));
        assertFalse(script.contains("raise NotImplementedError"));

        // The note is there as a comment, and the boxes no search depends on are named and left out.
        assertTrue(script.contains("#     Second pass, after dropping the pilot rows.\n"));
        assertEquals(1, result.notes().size());
        assertTrue(result.notes().getFirst().startsWith("Left out, since no search that is written depends on "
                                                        + "them: "));
        assertTrue(result.notes().getFirst().contains("\"Result graph\" (Graph"));
        assertTrue(result.notes().getFirst().contains("\"Search2\" (Search)"));

        // Written from the description's text read back, as from a file, the script is the same.
        assertEquals(script, PyTetradScript.render(SessionDescriptionParser.parse(
                SessionDescriptionWriter.toText(description)).description()).script());
    }

    /**
     * Data whose file was never recorded cannot be loaded by the script. The script says so and stops there, and
     * the search after it is still written, on the variable the hand-written step is to leave.
     */
    @Test
    public void testDataWithNoRecordOfItsFile() throws Exception {
        SessionDescription description = new SessionDescriber().describe(TestSessionDescription.buildSession(),
                TestSessionDescription.DATE);
        PyTetradScript.Result result = PyTetradScript.render(description);
        String script = result.script();

        assertTrue(script.contains("# The description does not say what file these data came from, or how the "
                                   + "file was read.\n"));
        assertTrue(script.contains("# Write the step here, leaving its result in a Tetrad data set called data1,\n"));
        assertTrue(script.contains("raise NotImplementedError('The step for box \"Data1\" has not been written.')\n"));
        assertTrue(script.indexOf("raise NotImplementedError")
                   < script.indexOf("search1_graph = search1_algorithm.search(data1, search1_parameters)"));
        assertFalse(script.contains("def read_tabular"));
        assertTrue(result.notes().getFirst().startsWith("\"Data1\" (Data): to be written by hand."));

        // The remark is at the top of the script too.
        assertTrue(script.indexOf("# Not turned into code") < script.indexOf("import pytetrad"));
    }

    @Test
    public void testEachWayOfReadingAFile() {
        String search = searchBox("Search1", "Data1", FGES, SEM_BIC);

        String mixed = script(dataBox("Data1", "Data type", "Mixed, max discrete categories 4",
                "Value delimiter", "Whitespace", "Case column to exclude/ignore", "Column labeled id",
                "Comment marker", "#", "Quote character", "None", "Missing value marker", "None") + search).script();
        assertTrue(mixed.contains("    delimiter=Delimiter.WHITESPACE,\n    kind=\"mixed\",\n    max_categories=4,\n"
                                  + "    has_header=True,\n    exclude_column=\"id\",\n    comment_marker=\"#\",\n"
                                  + "    quote_character=None,\n    missing_marker=\"\",\n"));

        String discrete = script(dataBox("Data1", "Data type", "Discrete", "First row variable names", "No",
                "Value delimiter", "Tab", "Case column to exclude/ignore", "First column",
                "Quote character", "'") + search).script();
        assertTrue(discrete.contains("    delimiter=Delimiter.TAB,\n    kind=\"discrete\",\n    has_header=False,\n"
                                     + "    exclude_first_column=True,\n    comment_marker=\"//\",\n"
                                     + "    quote_character=\"'\",\n"));

        String covariance = script(dataBox("Data1", "Data file type", "Covariance data (square)",
                "First row variable names", null, "Data type", null, "Case column to exclude/ignore", null,
                "Missing value marker", null, "Value delimiter", "Semicolon") + search).script();
        assertTrue(covariance.contains("data1 = read_covariance(\n    \"study.csv\",\n"
                                       + "    delimiter=Delimiter.SEMICOLON,\n    lower_triangle=False,\n"));
        assertTrue(covariance.contains("def read_covariance("));
        assertFalse(covariance.contains("def read_tabular("));

        // What the writer does not cover is left to be written by hand, with the reason.
        for (String[] change : new String[][]{{"Metadata JSON file", "meta.json"}, {"Value delimiter", "Bar"},
                {"Data type", "Ordinal"}, {"Data file type", "Spreadsheet"}}) {
            PyTetradScript.Result result = script(dataBox("Data1", change[0], change[1]) + search);
            assertTrue(change[0], result.script().contains("raise NotImplementedError("));
            assertTrue(change[0], result.notes().getFirst().contains("to be written by hand."));
        }

        // A case column left out by name needs the row of names.
        assertTrue(script(dataBox("Data1", "First row variable names", "No", "Case column to exclude/ignore",
                "Column labeled id") + search).script().contains("raise NotImplementedError("));
    }

    @Test
    public void testParametersOfEachKind() {
        String script = script(dataBox("Data1") + searchBox("Search1", "Data1", FGES, SEM_BIC,
                "Changed: maxDegree = 5 (default 1000)",
                "Changed: penaltyDiscount = 3.0 (default 2.0)",
                "Changed: faithfulnessAssumed = true (default false)",
                "Changed: seed = 42 (default -1)",
                "Changed: numberResampling = 10 (default 0)")).script();

        assertTrue(script.contains("search1_parameters.set(\"maxDegree\", JInt(5))  # default 1000\n"));
        assertTrue(script.contains("search1_parameters.set(\"penaltyDiscount\", 3.0)  # default 2.0\n"));
        assertTrue(script.contains("search1_parameters.set(\"faithfulnessAssumed\", True)  # default false\n"));
        assertTrue("A long is written bare; a bare Python whole number reaches Java as a long.",
                script.contains("search1_parameters.set(\"seed\", 42)  # default -1\n"));
        assertTrue(script.contains("from jpype import JArray, JInt\n"));

        // A value that cannot be read, and a parameter this Tetrad does not have: the search is not written.
        for (String changed : List.of("Changed: maxDegree = five (default 1000)",
                "Changed: noSuchParameter = 5 (default 1)")) {
            PyTetradScript.Result result = script(dataBox("Data1") + searchBox("Search1", "Data1", FGES, SEM_BIC,
                    changed));
            assertFalse(result.script().contains("search1_algorithm"));
            assertTrue(result.script().contains("print('Box \"Search1\" was not run: "));
            assertTrue(result.notes().getFirst().startsWith("\"Search1\" (Search): not run."));
        }

        // The seed left unfixed is remarked on, as the description remarks on it.
        assertTrue(script(dataBox("Data1") + searchBox("Search1", "Data1", FGES, SEM_BIC,
                "Seed: not fixed; results may vary between runs if the search uses random numbers")).script()
                .contains("# The seed was not fixed in the session"));
    }

    @Test
    public void testSearchesThatAreNotWritten() {
        String data = dataBox("Data1");

        // Another kind of box as input.
        PyTetradScript.Result graphInput = script(data + "[Box Graph1]\nType: Graph\nLocation: 1, 1\n\n"
                                                  + searchBox("Search1", "Data1, Graph1", FGES, SEM_BIC));
        assertTrue(graphInput.script().contains("# This search is not turned into code. It takes input from "
                                                + "\"Graph1\" (Graph),"));
        assertFalse(graphInput.script().contains("search1_algorithm"));
        assertFalse("Nothing else waits on it, so the script does not stop.",
                graphInput.script().contains("raise NotImplementedError"));

        // Two data boxes.
        assertTrue(script(data + dataBox("Data2") + searchBox("Search1", "Data1, Data2", FGES, SEM_BIC))
                .notes().getFirst().contains("It has 2 data boxes as input"));

        // An algorithm, a test and a score this Tetrad does not know.
        assertTrue(script(data + searchBox("Search1", "Data1", "Algorithm: Nonesuch (nonesuch)"))
                .notes().getFirst().contains("The algorithm \"Nonesuch (nonesuch)\" is not known"));
        assertTrue(script(data + searchBox("Search1", "Data1", "Algorithm: PC (pc)", "Test: Nonesuch (nonesuch)"))
                .notes().getFirst().contains("The test \"Nonesuch (nonesuch)\" is not known"));
        assertTrue(script(data + searchBox("Search1", "Data1", FGES, "Score: Nonesuch (nonesuch)"))
                .notes().getFirst().contains("The score \"Nonesuch (nonesuch)\" is not known"));

        // A score the algorithm needs, not named; and no algorithm chosen.
        assertTrue(script(data + searchBox("Search1", "Data1", FGES))
                .notes().getFirst().contains("The algorithm needs a score"));
        assertTrue(script(data + searchBox("Search1", "Data1", "Algorithm: none chosen yet"))
                .notes().getFirst().contains("No algorithm had been chosen"));

        // An algorithm that searches several data sets at once.
        for (AnnotatedClass<edu.cmu.tetrad.annotation.Algorithm> algorithm
                : AlgorithmAnnotations.getInstance().getAnnotatedClasses()) {
            if (MultiDataSetAlgorithm.class.isAssignableFrom(algorithm.clazz())) {
                String line = "Algorithm: " + algorithm.annotation().name() + " ("
                              + algorithm.annotation().command() + ")";
                assertTrue(line, script(data + searchBox("Search1", "Data1", line,
                        "Test: Fisher Z (fisher-z-test)", SEM_BIC)).notes().getFirst()
                        .contains("several data sets at once"));
            }
        }

        // A session with no search at all.
        assertEquals("The session has no search box with an algorithm chosen, so the script runs nothing.",
                script(data).notes().getFirst());
    }

    /**
     * A data box that takes its data from another data box, as described when it holds one data set.
     */
    private static String stepBox(String name, String model, String parents, String... lines) {
        StringBuilder box = new StringBuilder("[Box " + name + "]\nType: Data\nModel: " + model + "\nParents: "
                                              + parents + "\nLocation: 1, 1\n");

        for (String line : lines) {
            box.append(line).append("\n");
        }

        return box.append("Data set: study.csv\nShape: 200 rows, 4 columns, continuous\nMissing values: none\n\n")
                .toString();
    }

    /**
     * A box that changes the data is written as the box's own class, made from the data of the box above and the
     * settings the box had, and the search on its result is run on what that gives. A simulation is still left
     * to be written by hand, and stops the script, since the searches after it would otherwise run on other data.
     */
    @Test
    public void testABoxThatChangesTheData() {
        PyTetradScript.Result result = script(dataBox("Data1")
                + stepBox("Discretized", "Discretize Dataset", "Data1",
                "Discretize: X2; categories: Low, High; breakpoints: 0.25; chosen by: Evenly Distributed Values",
                "Discretize: X3; categories: a, b, c; breakpoints: -1.0, 1.0; chosen by: Evenly Distributed Values")
                + "[Box Simulation1]\nType: Simulation\nLocation: 1, 1\n\n"
                + searchBox("Search1", "Discretized", FGES, SEM_BIC)
                + searchBox("Search2", "Simulation1", FGES, SEM_BIC));
        String script = result.script();

        assertTrue(script.contains("""
                # ---- Box "Discretized" (Data: Discretize Dataset), from Data1 ----
                # The box's own step, Discretize Dataset, run on the data from "Data1" with the settings the
                # box had.
                discretized_parameters = Parameters()
                discretized_parameters.set("discretizationSpecs", DescribedChoices.discretization(\"""\\
                X2; categories: Low, High; breakpoints: 0.25; chosen by: Evenly Distributed Values
                X3; categories: a, b, c; breakpoints: -1.0, 1.0; chosen by: Evenly Distributed Values
                \"""))
                discretized = DiscretizationWrapper(data_box(data1), discretized_parameters).getSelectedDataModel()
                """));
        assertTrue(script.contains("from edu.cmu.tetradapp.description import DescribedChoices\n"));
        assertTrue(script.contains("from edu.cmu.tetradapp.model import DataWrapper\n"));
        assertTrue(script.contains("from edu.cmu.tetradapp.model.datamanip import DiscretizationWrapper\n"));
        assertTrue(script.contains("""
                def data_box(data):
                    \"""A Tetrad data box holding the given data, for a box's own step to be run on.\"""
                    box = DataWrapper(Parameters())
                    box.setDataModel(data)
                    return box
                """));
        assertTrue(script.contains("search1_graph = search1_algorithm.search(discretized, search1_parameters)\n"));

        assertTrue(script.contains("# ---- Box \"Simulation1\" (Simulation) ----\n"
                                   + "# A simulation draws new data each time it is run, so the data the session "
                                   + "had cannot be\n# made again from the box's settings.\n"));
        assertTrue(script.contains("raise NotImplementedError('The step for box \"Simulation1\" has not been "
                                   + "written.')\n"));
        assertTrue(script.contains("search2_graph = search2_algorithm.search(simulation1, search2_parameters)\n"));
        assertEquals(1, result.notes().size());

        // A script with no such step does not import the box classes or define the helper.
        String plain = script(dataBox("Data1") + searchBox("Search1", "Data1", FGES, SEM_BIC)).script();
        assertFalse(plain.contains("tetradapp"));
        assertFalse(plain.contains("data_box"));
    }

    /**
     * The settings of a step are written as the parameter object is given them: a registered parameter that was
     * changed as a value of its own kind, with its default remarked; a setting with no registered default as the
     * kind of value its line says it is; and steps follow one another.
     */
    @Test
    public void testTheSettingsOfAStep() {
        String script = script(dataBox("Data1")
                + stepBox("Thinned", "Thin Rows", "Data1", "Changed: thinRowsK = 3 (default 2)",
                "Changed: thinRowsOffset = 1 (default 0)")
                + stepBox("Logged", "Logarithmic Transform", "Thinned", "Changed: a = 2.5 (default 10.0)",
                "Changed: unlog = true (default false)")
                + stepBox("Chosen", "Selection Subset", "Logged", "Select: X1; intervals: [-0.5, 0.25]",
                "Setting: removeSelectionVariables = false", "Setting: someCount = 4", "Setting: someSeed = 9L",
                "Setting: someRate = 0.5", "Setting: someLabel = \"it's \\\"so\\\"\"")
                + searchBox("Search1", "Chosen", FGES, SEM_BIC)).script();

        assertTrue(script.contains("""
                thinned_parameters = Parameters()
                thinned_parameters.set("thinRowsK", JInt(3))  # default 2
                thinned_parameters.set("thinRowsOffset", JInt(1))  # default 0
                thinned = ThinRowsWrapper(data_box(data1), thinned_parameters).getSelectedDataModel()
                """));
        assertTrue(script.contains("""
                logged_parameters = Parameters()
                logged_parameters.set("a", 2.5)  # default 10.0
                logged_parameters.set("unlog", True)  # default false
                logged = LogData(data_box(thinned), logged_parameters).getSelectedDataModel()
                """));
        assertTrue(script.contains("""
                chosen_parameters = Parameters()
                chosen_parameters.set("removeSelectionVariables", False)
                chosen_parameters.set("someCount", JInt(4))
                chosen_parameters.set("someSeed", 9)
                chosen_parameters.set("someRate", 0.5)
                chosen_parameters.set("someLabel", "it's \\"so\\"")
                chosen_parameters.set("selectionSpecs", DescribedChoices.selection(\"""\\
                X1; intervals: [-0.5, 0.25]
                \"""))
                chosen = SelectionWrapper(data_box(logged), chosen_parameters).getSelectedDataModel()
                """));
        assertTrue(script.contains("from jpype import JArray, JInt\n"));
        assertTrue(script.contains("search1_graph = search1_algorithm.search(chosen, search1_parameters)\n"));
    }

    /**
     * A step that draws random numbers is written, and says that its data will not be the session's. A step is
     * left to be written by hand, with the reason, where the box needs more than the data of one box and its
     * settings, or where its result is not one data set.
     */
    @Test
    public void testStepsThatAreNotTheSessions() {
        String data = dataBox("Data1");
        PyTetradScript.Result random = script(data + stepBox("Shuffled", "Permute Rows", "Data1")
                                              + searchBox("Search1", "Shuffled", FGES, SEM_BIC));
        assertTrue(random.script().contains("# This step draws random numbers, so the data it gives here are not "
                                            + "the data the session\n# had.\n"));
        assertTrue(random.script().contains("shuffled = PermuteRowsWrapper(data_box(data1), shuffled_parameters)"
                                            + ".getSelectedDataModel()\n"));
        assertEquals(List.of(), random.notes());
        assertTrue(random.preview(null).contains("  - \"Shuffled\" (Data: Permute Rows): Runs the box's own step "
                                                 + "on the data from \"Data1\". It draws random numbers, so its "
                                                 + "data will not be the data the session had.\n"));

        Map<String, String> reasons = new LinkedHashMap<>();
        reasons.put(stepBox("Step", "Copy Selected Variables", "Data1"), "works from what was selected by hand");
        reasons.put(stepBox("Step", "Causal Unmix", "Data1", "Unmix: components: 2"), "behind a progress window");
        reasons.put(stepBox("Step", "Concatenate Datasets", "Data1, Data2"), "It takes input from 2 boxes");
        reasons.put(stepBox("Step", "NTad Explorer", "Data1"), "This kind of box is not turned into code.");
        reasons.put(stepBox("Step", "No Such Model", "Data1"), "does not say what kind of data box this is");
        reasons.put(stepBox("Step", "Thin Rows", "Data1", "Changed: thinRowsK = many (default 2)"),
                "Its setting \"thinRowsK = many (default 2)\" could not be read");
        reasons.put(stepBox("Step", "Thin Rows", "Data1", "Not described: someObject"),
                "Its setting \"someObject\" is of a kind a description cannot hold.");
        reasons.put(stepBox("Step", "Thin Rows", "Data1", "Setting: mode = FAST (enum java.lang.Thread$State)"),
                "is of a kind that is not written as code.");
        reasons.put(stepBox("Step", "Thin Rows", "Data1", "Values: thinRowsK = 2, 3 (default 2)"),
                "is of a kind that is not written as code.");
        reasons.put("[Box Step]\nType: Data\nModel: Impute Missing Data\nParents: Data1\nLocation: 1, 1\n"
                    + "Data set: a\nShape: 9 rows, 4 columns, continuous\nMissing values: none\n"
                    + "Data set: b\nShape: 9 rows, 4 columns, continuous\nMissing values: none\n\n",
                "This box holds 2 data sets");
        reasons.put("[Box Step]\nType: Data\nModel: Thin Rows\nParents: Data1\nLocation: 1, 1\n\n",
                "This box held no data when the session was described.");
        reasons.put("[Box Graph1]\nType: Graph\nLocation: 1, 1\n\n[Box Step]\nType: Data\nModel: Thin Rows\n"
                    + "Parents: Graph1\nLocation: 1, 1\nData set: a\nShape: 9 rows, 4 columns, continuous\n\n",
                "It takes input from \"Graph1\" (Graph), which is not a data box.");

        for (Map.Entry<String, String> entry : reasons.entrySet()) {
            PyTetradScript.Result result = script(data + dataBox("Data2") + entry.getKey()
                                                  + searchBox("Search1", "Step", FGES, SEM_BIC));
            assertEquals(entry.getKey(), 1, result.count(PyTetradScript.Fate.BY_HAND));
            assertTrue(entry.getKey() + result.notes(), result.notes().getFirst().contains(entry.getValue()));
            assertTrue(result.script().contains("raise NotImplementedError('The step for box \"Step\" has not "
                                                + "been written.')\n"));
            assertTrue(result.script().contains("search1_graph = search1_algorithm.search(step, search1_parameters)"));
        }
    }

    /**
     * The choices a script hands to a step as text give the step what the session's box had: the same variables
     * are cut at the same places, and the same rows are kept. A line that cannot be read is refused by name.
     */
    @Test
    public void testChoicesReadAsAScriptReadsThem() throws Exception {
        edu.cmu.tetrad.data.DataSet data = TestSessionDescription.simulate();

        Map<edu.cmu.tetrad.graph.Node, edu.cmu.tetrad.data.DiscretizationSpec> cuts = new java.util.HashMap<>();
        cuts.put(data.getVariable("X2"), new edu.cmu.tetrad.data.ContinuousDiscretizationSpec(
                new double[]{-0.3, 0.6}, List.of("Low", "Mid", "High")));
        cuts.put(data.getVariable("X4"), new edu.cmu.tetrad.data.ContinuousDiscretizationSpec(
                new double[]{0.1}, List.of("No", "Yes")));
        StringBuilder cutLines = new StringBuilder();

        for (Map.Entry<edu.cmu.tetrad.graph.Node, edu.cmu.tetrad.data.DiscretizationSpec> entry : cuts.entrySet()) {
            cutLines.append(StandardDescribers.discretizeLine(entry.getKey().getName(), entry.getValue()))
                    .append("\n");
        }

        edu.cmu.tetrad.util.Parameters inSession = new edu.cmu.tetrad.util.Parameters();
        inSession.set("discretizationSpecs", (Object) cuts);
        edu.cmu.tetrad.util.Parameters inScript = new edu.cmu.tetrad.util.Parameters();
        inScript.set("discretizationSpecs", (Object) DescribedChoices.discretization(cutLines.toString()));
        assertEquals(new edu.cmu.tetradapp.model.datamanip.DiscretizationWrapper(new DataWrapper(data), inSession)
                        .getSelectedDataModel(),
                new edu.cmu.tetradapp.model.datamanip.DiscretizationWrapper(new DataWrapper(data), inScript)
                        .getSelectedDataModel());

        Map<edu.cmu.tetrad.graph.Node, edu.cmu.tetradapp.model.datamanip.SelectionWrapper.SelectionSpec> rows
                = new java.util.HashMap<>();
        rows.put(data.getVariable("X1"), edu.cmu.tetradapp.model.datamanip.SelectionWrapper.SelectionSpec
                .continuous(List.of(new double[]{-0.5, 0.25}, new double[]{1.0, Double.POSITIVE_INFINITY})));
        String rowLines = StandardDescribers.selectLine(data.getVariable("X1"), rows.get(data.getVariable("X1")));

        inSession = new edu.cmu.tetrad.util.Parameters();
        inSession.set("selectionSpecs", (Object) rows);
        inScript = new edu.cmu.tetrad.util.Parameters();
        inScript.set("selectionSpecs", (Object) DescribedChoices.selection(rowLines + "\n"));
        edu.cmu.tetrad.data.DataSet kept = (edu.cmu.tetrad.data.DataSet) new edu.cmu.tetradapp.model.datamanip
                .SelectionWrapper(new DataWrapper(data), inSession).getSelectedDataModel();
        assertTrue(kept.getNumRows() > 0 && kept.getNumRows() < data.getNumRows());
        assertEquals(kept, new edu.cmu.tetradapp.model.datamanip.SelectionWrapper(new DataWrapper(data), inScript)
                .getSelectedDataModel());

        try {
            DescribedChoices.selection("X1; intervals: [2.0, 1.0]\n");
            throw new AssertionError("A backward interval should not be read.");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().startsWith("The selection \"X1; intervals: [2.0, 1.0]\" was "
                                                                 + "not read, since "));
        }

        try {
            DescribedChoices.discretization("X2\n");
            throw new AssertionError("A line with no categories should not be read.");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().startsWith("The discretization \"X2\" was not read, since "));
        }
    }

    /**
     * Each kind of box whose step is written is a model the Data box offers, that can be imported by name and is
     * made from one data box and a parameter object; so that a class renamed or moved is noticed here and not when
     * a script fails.
     */
    @Test
    public void testTheStepsWrittenAreDataBoxModels() throws Exception {
        java.util.Set<String> offered = new java.util.HashSet<>();

        for (Class<?> model : edu.cmu.tetradapp.app.TetradApplicationConfig.getInstance()
                .getSessionNodeConfig("Data").getModels()) {
            offered.add(model.getName());
        }

        java.util.Set<String> named = new java.util.TreeSet<>(PyTetradScript.STEPS_WRITTEN);
        named.addAll(PyTetradScript.STEPS_BY_SELECTION);
        named.addAll(PyTetradScript.STEPS_BEHIND_A_WINDOW);

        for (String name : named) {
            assertTrue(name + " is not a model of the Data box.", offered.contains(name));
        }

        for (String name : PyTetradScript.STEPS_WRITTEN) {
            Class<?> model = Class.forName(name);
            assertTrue(name, java.lang.reflect.Modifier.isPublic(model.getModifiers()));
            assertTrue(name, model.getEnclosingClass() == null);
            model.getConstructor(DataWrapper.class, edu.cmu.tetrad.util.Parameters.class);
        }

        assertTrue(PyTetradScript.STEPS_WRITTEN.containsAll(PyTetradScript.STEPS_RANDOM));
        assertEquals(34, PyTetradScript.STEPS_WRITTEN.size());
    }

    /**
     * Empty knowledge is given to no search. Knowledge is given to an algorithm that takes it, and a remark is
     * made where the algorithm does not.
     */
    @Test
    public void testKnowledge() {
        String data = dataBox("Data1");
        String knowledge = "[Box Knowledge1]\nType: Knowledge\nParents: Data1\nLocation: 1, 1\n"
                           + "Begin knowledge\n    /knowledge\n    addtemporal\n\n    0  X1\n    1  X2\n\n"
                           + "    forbiddirect\n\n    requiredirect\nEnd knowledge\n\n";
        String empty = "[Box Knowledge1]\nType: Knowledge\nParents: Data1\nLocation: 1, 1\nKnowledge: empty\n\n";

        String given = script(data + knowledge + searchBox("Search1", "Data1, Knowledge1", FGES, SEM_BIC)).script();
        assertTrue(given.contains("knowledge1_text = \"\"\"\\\n/knowledge\naddtemporal\n\n0  X1\n1  X2\n"));
        assertTrue(given.contains("search1_algorithm.setKnowledge(knowledge1)\n"));

        String none = script(data + empty + searchBox("Search1", "Data1, Knowledge1", FGES, SEM_BIC)).script();
        assertTrue(none.contains("# The knowledge in this box was empty, so the searches below are given none.\n"));
        assertFalse(none.contains("setKnowledge"));
        assertFalse(none.contains("loadKnowledge"));
    }

    /**
     * What the user is shown before a script is saved: what becomes of each box, and what a script of this kind
     * is in general.
     */
    @Test
    public void testWhatTheUserIsShownBeforeSaving() {
        PyTetradScript.Result result = script(dataBox("Data1")
                + stepBox("Discretized", "Discretize Dataset", "Data1",
                "Discretize: X2; categories: Low, High; breakpoints: 0.25; chosen by: Evenly Distributed Values")
                + "[Box Simulation1]\nType: Simulation\nLocation: 1, 1\n\n"
                + "[Box Graph1]\nType: Graph\nLocation: 1, 1\n\n"
                + "[Box Estimator1]\nType: Estimator\nParents: Data1\nLocation: 1, 1\n\n"
                + "[Box Note1]\nType: Note\nLocation: 1, 1\nBegin note\n    Second pass.\nEnd note\n\n"
                + searchBox("Plain", "Data1", FGES, SEM_BIC)
                + searchBox("On discretized", "Discretized", "Algorithm: PC (pc)", "Test: Fisher Z (fisher-z-test)")
                + searchBox("On simulated", "Simulation1", FGES, SEM_BIC)
                + searchBox("With graph", "Data1, Graph1", FGES, SEM_BIC));

        // Every box of the session is accounted for, once.
        assertEquals(10, result.boxes().size());
        assertEquals(6, result.count(PyTetradScript.Fate.WRITTEN));
        assertEquals(1, result.count(PyTetradScript.Fate.BY_HAND));
        assertEquals(1, result.count(PyTetradScript.Fate.NOT_RUN));
        assertEquals(2, result.count(PyTetradScript.Fate.LEFT_OUT));

        String preview = result.preview("study.tet");
        System.out.println(preview);

        assertTrue(preview.startsWith("A py-tetrad script is a Python program that reruns this session's searches "
                                      + "outside Tetrad.\n"
                                      + "For session \"study.tet\" the script would hold 3 of the session's 4 "
                                      + "searches.\n"
                                      + "It would not run to the end as saved: 1 step is left for you to write in "
                                      + "Python.\n"));
        assertTrue(preview.contains("""

                WRITTEN AS CODE
                  - "Data1" (Data): Reads the file "study.csv".
                  - "Discretized" (Data: Discretize Dataset): Runs the box's own step on the data from "Data1".
                  - "Note1" (Note): Its text, as a comment.
                  - "Plain" (Search): Runs FGES (fges) with Sem BIC Score (sem-bic-score) on "Data1". Its graph \
                is saved as plain_graph.txt.
                  - "On discretized" (Search): Runs PC (pc) with Fisher Z (fisher-z-test) on "Discretized". Its \
                graph is saved as on_discretized_graph.txt.
                  - "On simulated" (Search): Runs FGES (fges) with Sem BIC Score (sem-bic-score) on \
                "Simulation1". Its graph is saved as on_simulated_graph.txt.

                LEFT FOR YOU TO WRITE BY HAND. The script stops at each of these until you write the step.
                  - "Simulation1" (Simulation): A simulation draws new data each time it is run, so the data \
                the session had cannot be made again from the box's settings.

                SEARCHES THE SCRIPT WILL NOT RUN
                  - "With graph" (Search): It takes input from "Graph1" (Graph), and a script is written only \
                for a search on one data box, with or without a knowledge box.

                LEFT OUT, since no search in the script depends on them
                  - "Graph1" (Graph): A search takes it as input, and that search is not written.
                  - "Estimator1" (Estimator)

                WHAT A SCRIPT OF THIS KIND IS, FOR ANY SESSION
                """));

        for (String limit : PyTetradScript.LIMITS) {
            assertTrue(preview.contains("  - " + limit + "\n"));
        }

        // A session the script covers whole: only what is written, and the general remarks.
        String whole = script(dataBox("Data1") + searchBox("Search1", "Data1", FGES, SEM_BIC)).preview(null);
        assertTrue(whole.contains("For session (unnamed) the script would hold 1 of the session's 1 search.\n"));
        assertFalse(whole.contains("It would not run to the end"));
        assertFalse(whole.contains("BY HAND"));
        assertFalse(whole.contains("WILL NOT RUN"));
        assertFalse(whole.contains("LEFT OUT"));

        // A session with nothing to run.
        assertTrue(script(dataBox("Data1")).preview("x").contains("would hold 0 of the session's 0 searches.\n"
                                                                + "So there is nothing in it to run.\n"));
    }

    /**
     * Each box gets a Python name of its own, whatever it is called in the session.
     */
    @Test
    public void testPythonNames() {
        String script = script(dataBox("1st data") + dataBox("class") + dataBox("Wave 2") + dataBox("Wave-2")
                               + dataBox("Étude") + dataBox("***")
                               + searchBox("a", "1st data", FGES, SEM_BIC) + searchBox("b", "class", FGES, SEM_BIC)
                               + searchBox("c", "Wave 2", FGES, SEM_BIC) + searchBox("d", "Wave-2", FGES, SEM_BIC)
                               + searchBox("e", "Étude", FGES, SEM_BIC) + searchBox("f", "***", FGES, SEM_BIC))
                .script();

        assertTrue(script.contains("\nbox_1st_data = read_tabular("));
        assertTrue(script.contains("\nclass_box = read_tabular("));
        assertTrue(script.contains("\nwave_2 = read_tabular("));
        assertTrue(script.contains("\nwave_2_2 = read_tabular("));
        assertTrue(script.contains("\ntude = read_tabular("));
        assertTrue(script.contains("\nbox_ = read_tabular("));
        assertTrue(script.contains("d_graph = d_algorithm.search(wave_2_2, d_parameters)\n"));
    }

    @Test
    public void testPythonText() {
        assertEquals("\"plain\"", PyTetradScript.python("plain"));
        assertEquals("'say \"so\"'", PyTetradScript.python("say \"so\""));
        assertEquals("\"it's \\\"so\\\"\"", PyTetradScript.python("it's \"so\""));
        assertEquals("\"a\\\\b\\tc\\nd\"", PyTetradScript.python("a\\b\tc\nd"));
        assertEquals("\"\\x07\"", PyTetradScript.python("\u0007"));
        assertEquals("\"\"", PyTetradScript.python(null));
    }
}
