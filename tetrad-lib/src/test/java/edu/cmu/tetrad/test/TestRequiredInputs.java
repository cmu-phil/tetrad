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
package edu.cmu.tetrad.test;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.Cstar;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.FgesMb;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.PcMb;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.RestrictedBoss;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.SingleGraphAlg;
import edu.cmu.tetrad.algcomparison.algorithm.pairwise.FaskPw;
import edu.cmu.tetrad.algcomparison.algorithm.pairwise.R1;
import edu.cmu.tetrad.algcomparison.algorithm.pairwise.R2;
import edu.cmu.tetrad.algcomparison.algorithm.pairwise.R3;
import edu.cmu.tetrad.algcomparison.algorithm.pairwise.Rskew;
import edu.cmu.tetrad.algcomparison.algorithm.pairwise.RskewE;
import edu.cmu.tetrad.algcomparison.algorithm.pairwise.Skew;
import edu.cmu.tetrad.algcomparison.algorithm.pairwise.SkewE;
import edu.cmu.tetrad.algcomparison.algorithm.pairwise.Tanh;
import edu.cmu.tetrad.algcomparison.independence.FisherZ;
import edu.cmu.tetrad.algcomparison.score.SemBicScore;
import edu.cmu.tetrad.algcomparison.utils.RequiredInputs;
import edu.cmu.tetrad.algcomparison.utils.TakesExternalGraph;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * An algorithm that cannot run without target variables, or without a graph to orient, says so when the input is
 * missing: which algorithm, what it needs, and where to give it. Before, a forgotten input ended in a null-pointer or
 * array-index error from inside the search, or, for Restricted-BOSS and PC-MB, in an answer to a different question.
 *
 * @author Claude
 */
public class TestRequiredInputs {

    private static DataSet data;

    @BeforeClass
    public static void makeData() throws Exception {
        RandomUtil.getInstance().setSeed(11L);
        Graph graph = RandomGraph.randomGraph(6, 0, 6, 100, 100, 100, false);
        data = new SemIm(new SemPm(graph)).simulateData(500, false);
    }

    // ---- target names ----

    @Test
    public void testTargetNamesAreReadTheSameWayHoweverSeparated() {
        List<Node> variables = data.getVariables();
        List<Node> expected = List.of(data.getVariable("X1"), data.getVariable("X2"));

        for (String given : new String[]{"X1,X2", "X1, X2", "X1 X2", "  X1   X2 ", "X1 ,X2,", "X1\tX2", "X1, X2, X1"}) {
            assertEquals("'" + given + "'", expected, RequiredInputs.targets("Some search", given, variables));
        }
    }

    @Test
    public void testACommaSeparatedNameMayContainSpaces() {
        List<Node> variables = List.of(new ContinuousVariable("blood pressure"), new ContinuousVariable("age"));

        assertEquals(variables, RequiredInputs.targets("Some search", "blood pressure, age", variables));
    }

    @Test
    public void testNoTargetIsRefusedWithWhatToSetAndWhatIsAvailable() {
        for (String given : new String[]{null, "", "   ", ",", " , "}) {
            String message = refusal(() -> RequiredInputs.targets("Some search", given, data.getVariables()));

            assertTrue(message, message.startsWith("Some search needs at least one target variable"));
            assertTrue(message, message.contains("'Target names' parameter (" + Params.TARGETS + ")"));
            assertTrue(message, message.contains("X1, X2, X3, X4, X5, X6."));
        }
    }

    @Test
    public void testAnUnknownTargetIsNamed() {
        String one = refusal(() -> RequiredInputs.targets("Some search", "X1 X9", data.getVariables()));
        assertTrue(one, one.startsWith("Some search was given a target that is not a variable in the data: 'X9'."));
        assertFalse(one, one.contains("'X1'"));
        assertTrue(one, one.contains("X1, X2, X3, X4, X5, X6."));

        String two = refusal(() -> RequiredInputs.targets("Some search", "x1, X2, Y", data.getVariables()));
        assertTrue(two, two.startsWith("Some search was given targets that are not variables in the data: 'x1', 'Y'."));
    }

    @Test
    public void testALongListOfVariablesIsCutShort() {
        List<Node> variables = new ArrayList<>();
        for (int i = 1; i <= 40; i++) variables.add(new ContinuousVariable("V" + i));

        String message = refusal(() -> RequiredInputs.targets("Some search", "", variables));

        assertTrue(message, message.contains("V11, V12, and 28 more."));
        assertFalse(message, message.contains("V13"));
    }

    @Test
    public void testEveryTargetTakingSearchRefusesMissingAndUnknownTargets() {
        Map<String, Supplier<Algorithm>> searches = new LinkedHashMap<>();
        searches.put("Restricted-BOSS", () -> new RestrictedBoss(new SemBicScore()));
        searches.put("FGES-MB", () -> new FgesMb(new SemBicScore()));
        searches.put("PC-MB", () -> new PcMb(new FisherZ()));
        searches.put("CStaR", () -> new Cstar(new FisherZ(), new SemBicScore()));

        for (Map.Entry<String, Supplier<Algorithm>> search : searches.entrySet()) {
            String name = search.getKey();

            // Nothing set: the registered default of the targets parameter is blank.
            String missing = refusal(() -> run(search.getValue().get(), new Parameters()));
            assertTrue(missing, missing.startsWith(name + " needs at least one target variable"));

            Parameters blank = new Parameters();
            blank.set(Params.TARGETS, "  ");
            String blankMessage = refusal(() -> run(search.getValue().get(), blank));
            assertTrue(blankMessage, blankMessage.startsWith(name + " needs at least one target variable"));

            Parameters misspelled = new Parameters();
            misspelled.set(Params.TARGETS, "X1 X9");
            String unknown = refusal(() -> run(search.getValue().get(), misspelled));
            assertTrue(unknown, unknown.startsWith(name + " was given a target that is not a variable in the data: 'X9'."));
        }
    }

    @Test
    public void testASpaceAfterTheCommaDoesNotChangeTheSearch() {
        Map<String, Supplier<Algorithm>> searches = new LinkedHashMap<>();
        searches.put("Restricted-BOSS", () -> new RestrictedBoss(new SemBicScore()));
        searches.put("FGES-MB", () -> new FgesMb(new SemBicScore()));
        searches.put("PC-MB", () -> new PcMb(new FisherZ()));

        for (Map.Entry<String, Supplier<Algorithm>> search : searches.entrySet()) {
            Parameters tight = new Parameters();
            tight.set(Params.TARGETS, "X1,X2");
            Parameters spaced = new Parameters();
            spaced.set(Params.TARGETS, "X1, X2");

            Graph expected = run(search.getValue().get(), tight);
            Graph actual = run(search.getValue().get(), spaced);

            assertTrue(search.getKey(), expected.containsNode(expected.getNode("X2")));
            assertEquals(search.getKey(), edges(expected), edges(actual));
            assertEquals(search.getKey(), names(expected), names(actual));
        }
    }

    // ---- a graph to orient ----

    @Test
    public void testEveryPairwiseSearchRefusesAMissingGraph() {
        Map<String, Supplier<Algorithm>> searches = new LinkedHashMap<>();
        searches.put("Skew", Skew::new);
        searches.put("SkewE", SkewE::new);
        searches.put("RSkew", Rskew::new);
        searches.put("RSkewE", RskewE::new);
        searches.put("R1", R1::new);
        searches.put("R2", R2::new);
        searches.put("R3", R3::new);
        searches.put("Tanh", () -> new Tanh(null));
        searches.put("FASK-PW", FaskPw::new);

        for (Map.Entry<String, Supplier<Algorithm>> search : searches.entrySet()) {
            String name = search.getKey();
            String expected = RequiredInputs.graphSourceMessage(name);

            assertTrue(expected, expected.startsWith(name + " needs a graph as well as data"));
            assertTrue(expected, expected.contains("Graph box"));
            assertTrue(expected, expected.contains("setExternalGraph"));

            assertEquals(name, expected, refusal(() -> run(search.getValue().get(), new Parameters())));

            // The interface asks for this message, when the algorithm is picked, by handing over no graph source.
            assertEquals(name, expected,
                    refusal(() -> ((TakesExternalGraph) search.getValue().get()).setExternalGraph(null)));
        }
    }

    @Test
    public void testPairwiseSearchesRunWhenGivenAGraph() {
        List<Node> v = data.getVariables();
        Graph given = new EdgeListGraph(v);
        given.addUndirectedEdge(v.get(0), v.get(1));
        given.addUndirectedEdge(v.get(2), v.get(3));

        for (Algorithm search : new Algorithm[]{new Skew(new SingleGraphAlg(given)), new R3(new SingleGraphAlg(given)),
                new Rskew(new SingleGraphAlg(given)), new FaskPw(new SingleGraphAlg(given))}) {
            assertEquals(search.getDescription(), Set.of("X1-X2", "X3-X4"), adjacencies(run(search, new Parameters())));
        }
    }

    @Test
    public void testFaskPwOrientsTheGraphItWasLastGiven() {
        List<Node> v = data.getVariables();
        Graph first = new EdgeListGraph(v);
        first.addUndirectedEdge(v.get(0), v.get(1));
        first.addUndirectedEdge(v.get(2), v.get(3));
        Graph second = new EdgeListGraph(v);
        second.addUndirectedEdge(v.get(4), v.get(5));

        FaskPw search = new FaskPw(new SingleGraphAlg(first));
        assertEquals(Set.of("X1-X2", "X3-X4"), adjacencies(run(search, new Parameters())));

        // It used to keep the first graph.
        search.setExternalGraph(new SingleGraphAlg(second));
        assertEquals(Set.of("X5-X6"), adjacencies(run(search, new Parameters())));
    }

    @Test
    public void testFaskPwIsDescribedAsItself() {
        assertTrue(new FaskPw().getDescription().startsWith("FASK-PW"));
    }

    // ---- helpers ----

    private static Graph run(Algorithm algorithm, Parameters parameters) {
        PrintStream out = System.out;
        System.setOut(new PrintStream(OutputStream.nullOutputStream()));

        try {
            return algorithm.search(data, parameters);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            System.setOut(out);
        }
    }

    /**
     * Runs something that must be refused with an IllegalArgumentException, and returns the message.
     */
    private static String refusal(Runnable runnable) {
        try {
            runnable.run();
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }

        fail("Expected an IllegalArgumentException.");
        return null;
    }

    private static Set<String> adjacencies(Graph graph) {
        Set<String> pairs = new TreeSet<>();

        for (Edge edge : graph.getEdges()) {
            String a = edge.getNode1().getName();
            String b = edge.getNode2().getName();
            pairs.add(a.compareTo(b) < 0 ? a + "-" + b : b + "-" + a);
        }

        return pairs;
    }

    private static Set<String> edges(Graph graph) {
        Set<String> edges = new TreeSet<>();
        for (Edge edge : graph.getEdges()) edges.add(edge.toString());
        return edges;
    }

    private static Set<String> names(Graph graph) {
        Set<String> names = new TreeSet<>();
        for (Node node : graph.getNodes()) names.add(node.getName());
        return names;
    }
}
