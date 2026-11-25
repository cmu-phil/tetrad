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
                """));
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
     * A box that changes the data stops the script until its step is written, since the searches after it would
     * otherwise run on other data. The search on its result is written, on the variable the step is to leave.
     */
    @Test
    public void testABoxThatChangesTheData() {
        PyTetradScript.Result result = script(dataBox("Data1")
                + "[Box Discretized]\nType: Data\nModel: Discretize Data\nParents: Data1\nLocation: 1, 1\n"
                + "Discretize: X2; categories: Low, High; breakpoints: 0.25; chosen by: Evenly Distributed Values\n\n"
                + "[Box Simulation1]\nType: Simulation\nLocation: 1, 1\n\n"
                + searchBox("Search1", "Discretized", FGES, SEM_BIC)
                + searchBox("Search2", "Simulation1", FGES, SEM_BIC));
        String script = result.script();

        assertTrue(script.contains("# ---- Box \"Discretized\" (Data: Discretize Data), from Data1 ----\n"
                                   + "# This kind of box is not turned into code.\n"
                                   + "# What the session description says of it:\n"
                                   + "#     Discretize: X2; categories: Low, High; breakpoints: 0.25; chosen by: "
                                   + "Evenly\n#         Distributed Values\n"));
        assertTrue(script.contains("raise NotImplementedError('The step for box \"Discretized\" has not been "
                                   + "written.')\n"));
        assertTrue(script.contains("search1_graph = search1_algorithm.search(discretized, search1_parameters)\n"));
        assertTrue(script.contains("search2_graph = search2_algorithm.search(simulation1, search2_parameters)\n"));
        assertEquals(2, result.notes().size());
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
