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
import edu.cmu.tetrad.algcomparison.algorithm.MultiDataSetAlgorithm;
import edu.cmu.tetrad.algcomparison.algorithm.continuous.dag.Fask;
import edu.cmu.tetrad.algcomparison.algorithm.multi.FaskPool;
import edu.cmu.tetrad.algcomparison.algorithm.multi.FaskVote;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.Cstar;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.FgesMb;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.PcMb;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.RestrictedBoss;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.SingleGraphAlg;
import edu.cmu.tetrad.algcomparison.algorithm.other.MimbuildBollen;
import edu.cmu.tetrad.algcomparison.algorithm.other.MimbuildPca;
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
import edu.cmu.tetrad.algcomparison.score.DegenerateGaussianBicScore;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.algcomparison.score.SemBicScore;
import edu.cmu.tetrad.algcomparison.utils.RequiredInputs;
import edu.cmu.tetrad.algcomparison.utils.TakesExternalGraph;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.TakesScoreWrapper;
import edu.cmu.tetrad.annotation.AlgorithmAnnotations;
import edu.cmu.tetrad.annotation.AnnotatedClass;
import edu.cmu.tetrad.data.ContinuousDiscretizationSpec;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.data.DiscretizationSpec;
import edu.cmu.tetrad.data.Discretizer;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.search.blocks.BlockSpec;
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
import java.util.Arrays;
import java.util.HashMap;
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
 * An algorithm that cannot run without target variables, a graph to orient, or clusters says so when the input is
 * missing: which algorithm, what it needs, and where to give it. Before, a forgotten input ended in a null-pointer or
 * array-index error from inside the search, or, for Restricted-BOSS and PC-MB, in an answer to a different question.
 * <p>
 * Also here: an algorithm is offered only for the kinds of data it says it takes.
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

    /**
     * The search class behind PC-MB, called directly, refuses no targets and a null target with a message. A null
     * target used to end in a null-pointer error from inside the search, and no targets in an empty graph.
     */
    @Test
    public void testTheSearchClassBehindPcMbRefusesMissingAndNullTargets() {
        edu.cmu.tetrad.search.test.IndTestFisherZ test = new edu.cmu.tetrad.search.test.IndTestFisherZ(data, 0.01);

        String none = refusal(() -> quietly(() -> new edu.cmu.tetrad.search.PcMb(test, 3).search(new ArrayList<>())));
        assertTrue(none, none.startsWith("PC-MB needs at least one target variable"));

        List<Node> withNull = new ArrayList<>();
        withNull.add(data.getVariable("X1"));
        withNull.add(test.getVariable("X9"));
        String missing = refusal(() -> quietly(() -> new edu.cmu.tetrad.search.PcMb(test, 3).search(withNull)));
        assertTrue(missing, missing.startsWith("PC-MB was given a target that is null"));

        Graph found = quietly(() -> new edu.cmu.tetrad.search.PcMb(test, 3).search(List.of(test.getVariable("X1"))));
        assertTrue(found.containsNode(found.getNode("X1")));
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
            assertTrue(expected, expected.contains("--external-graph"));
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

    // ---- clusters ----

    @Test
    public void testMimbuildRefusesMissingClusters() {
        Map<String, Supplier<Algorithm>> searches = new LinkedHashMap<>();
        searches.put("Mimbuild (Bollen)", MimbuildBollen::new);
        searches.put("Mimbuild (PCA)", MimbuildPca::new);

        for (Map.Entry<String, Supplier<Algorithm>> search : searches.entrySet()) {
            String name = search.getKey();
            String expected = RequiredInputs.blockSpecMessage(name);

            assertTrue(expected, expected.startsWith(name + " needs clusters as well as data"));
            assertTrue(expected, expected.contains("Latent Clusters box"));
            assertTrue(expected, expected.contains("setBlockSpec"));

            assertEquals(name, expected, refusal(() -> run(search.getValue().get(), new Parameters())));
        }
    }

    @Test
    public void testMimbuildRefusesClustersMadeForOtherData() {
        DataSet other = data.subsetColumns(new int[]{0, 1, 2, 3});

        MimbuildBollen bollen = new MimbuildBollen();
        bollen.setBlockSpec(twoClusters(other, 2));
        String message = refusal(() -> run(bollen, new Parameters()));
        assertTrue(message, message.startsWith("The clusters given to Mimbuild (Bollen) were made for a different data set"));

        MimbuildPca pca = new MimbuildPca();
        pca.setBlockSpec(twoClusters(other, 2));
        message = refusal(() -> run(pca, new Parameters()));
        assertTrue(message, message.startsWith("The clusters given to Mimbuild (PCA) were made for a different data set"));
    }

    @Test
    public void testMimbuildRunsWhenGivenClusters() {
        MimbuildBollen bollen = new MimbuildBollen();
        bollen.setBlockSpec(twoClusters(data, 3));
        assertEquals(Set.of("L1", "L2"), names(run(bollen, new Parameters())));

        MimbuildPca pca = new MimbuildPca();
        pca.setBlockSpec(twoClusters(data, 3));
        assertEquals(Set.of("L1", "L2"), names(run(pca, new Parameters())));
    }

    private static BlockSpec twoClusters(DataSet dataSet, int size) {
        List<List<Integer>> blocks = new ArrayList<>();
        List<Node> latents = new ArrayList<>();

        for (int b = 0; b < 2; b++) {
            List<Integer> block = new ArrayList<>();
            for (int i = 0; i < size; i++) block.add(b * size + i);
            blocks.add(block);

            ContinuousVariable latent = new ContinuousVariable("L" + (b + 1));
            latent.setNodeType(NodeType.LATENT);
            latents.add(latent);
        }

        return new BlockSpec(dataSet, blocks, latents);
    }

    // ---- the kind of data ----

    /**
     * The chooser offers an algorithm for the kinds of data in its annotation, and the Search box then refuses to run
     * it on data its getDataType() does not take. For an algorithm whose kind is its own, and not that of a test or
     * score chosen for it, the two must agree, or the algorithm is offered and then refused. PAG-Sampling-RFCI and
     * Two-Step declared no kind, so were offered for every kind.
     */
    @Test
    public void testAnAlgorithmIsOfferedOnlyForDataItTakes() {
        List<String> offeredButRefused = new ArrayList<>();
        int checked = 0;

        for (AnnotatedClass<edu.cmu.tetrad.annotation.Algorithm> annotated
                : AlgorithmAnnotations.getInstance().getAnnotatedClasses()) {
            Class<?> c = annotated.clazz();

            if (MultiDataSetAlgorithm.class.isAssignableFrom(c) || TakesIndependenceWrapper.class.isAssignableFrom(c)
                    || TakesScoreWrapper.class.isAssignableFrom(c)) {
                continue;
            }

            DataType takes;

            try {
                takes = ((Algorithm) c.getDeclaredConstructor().newInstance()).getDataType();
            } catch (ReflectiveOperationException | RuntimeException e) {
                continue; // The kind comes from something that is not set on a bare instance.
            }

            checked++;
            List<DataType> declared = Arrays.asList(annotated.annotation().dataType());

            for (DataType kind : new DataType[]{DataType.Continuous, DataType.Discrete, DataType.Mixed}) {
                boolean offered = declared.contains(DataType.All) || declared.contains(kind);
                boolean taken = takes == DataType.Mixed || takes == kind;

                if (offered && !taken) {
                    offeredButRefused.add(annotated.annotation().command() + " for " + kind + " data (it takes "
                            + takes + ")");
                }
            }
        }

        assertTrue("Too few algorithms were checked: " + checked, checked >= 10);
        assertTrue("Offered for data they do not take: " + offeredButRefused, offeredButRefused.isEmpty());
    }

    @Test
    public void testFaskTakesTheKindOfDataItsScoreTakes() {
        assertEquals(DataType.Continuous, new Fask(new SemBicScore()).getDataType());
        assertEquals(DataType.Mixed, new Fask(new DegenerateGaussianBicScore()).getDataType());
        assertEquals(DataType.Continuous, new Fask().getDataType());
    }

    @Test
    public void testMimbuildIsForContinuousData() {
        assertEquals(DataType.Continuous, new MimbuildBollen().getDataType());
        assertEquals(DataType.Continuous, new MimbuildPca().getDataType());
    }

    @Test
    public void testFaskPoolAndFaskVoteTakeTheKindOfDataTheirScoreTakes() {
        assertEquals(DataType.Continuous, new FaskPool(new SemBicScore()).getDataType());
        assertEquals(DataType.Mixed, new FaskPool(new DegenerateGaussianBicScore()).getDataType());
        assertEquals(DataType.Continuous, new FaskPool().getDataType());

        assertEquals(DataType.Continuous, new FaskVote(new SemBicScore()).getDataType());
        assertEquals(DataType.Mixed, new FaskVote(new DegenerateGaussianBicScore()).getDataType());
        assertEquals(DataType.Continuous, new FaskVote().getDataType());
    }

    /**
     * FASK-Pool and FASK-Vote take data sets with discrete variables when the adjacency search is IMaGES and the score
     * is one for mixed data. An adjacency with a discrete variable is then left undirected. With any other adjacency
     * search, including the default, or with a score for continuous data, they say what to change.
     */
    @Test
    public void testFaskPoolAndFaskVoteOnMixedData() throws Exception {
        List<DataModel> mixed = threeMixedDataSets();

        for (String which : new String[]{"FASK-Pool", "FASK-Vote"}) {
            Parameters images = new Parameters();
            images.set(Params.FASK_POOL_ADJACENCY, 1);

            Graph graph = quietly(() -> pooled(which, new DegenerateGaussianBicScore()).search(mixed, images));
            assertTrue(which, graph.getNumEdges() > 0);

            for (Edge edge : graph.getEdges()) {
                boolean discrete = mixed.getFirst().getVariable(edge.getNode1().getName()) instanceof DiscreteVariable
                        || mixed.getFirst().getVariable(edge.getNode2().getName()) instanceof DiscreteVariable;
                if (discrete) assertFalse(which + ": " + edge, edge.isDirected());
            }

            // The default adjacency search is not IMaGES.
            String byDefault = refusal(() -> quietly(() -> pooled(which, new DegenerateGaussianBicScore())
                    .search(mixed, new Parameters())));
            assertTrue(byDefault, byDefault.contains("adjacency method is for continuous data"));
            assertTrue(byDefault, byDefault.contains("(" + Params.FASK_POOL_ADJACENCY + ") to 1, IMaGES"));

            String wrongScore = refusal(() -> quietly(() -> pooled(which, new SemBicScore()).search(mixed, images)));
            assertTrue(wrongScore, wrongScore.startsWith("These data sets have discrete variables, and the score chosen,"));
            assertTrue(wrongScore, wrongScore.contains("is for continuous data."));
            assertTrue(wrongScore, wrongScore.contains("hoose a score for mixed data"));

            // With the defaults both are wrong, and one message says so.
            String bothWrong = refusal(() -> quietly(() -> pooled(which, new SemBicScore())
                    .search(mixed, new Parameters())));
            assertTrue(bothWrong, bothWrong.contains("hoose a score for mixed data"));
            assertTrue(bothWrong, bothWrong.contains("(" + Params.FASK_POOL_ADJACENCY + ") to 1, IMaGES"));
        }
    }

    /**
     * The other route for mixed data is to supply the adjacencies as an external graph. FASK-Pool then has no use for
     * its score. FASK-Vote still runs FASK on each data set with the score, so it needs one for mixed data either way.
     */
    @Test
    public void testFaskPoolAndFaskVoteOnMixedDataWithAnExternalGraph() throws Exception {
        List<DataModel> mixed = threeMixedDataSets();
        List<Node> v = mixed.getFirst().getVariables();
        Graph given = new EdgeListGraph(v);
        given.addUndirectedEdge(v.get(0), v.get(5));
        given.addUndirectedEdge(v.get(2), v.get(3));
        given.addUndirectedEdge(v.get(3), v.get(4));

        FaskPool pool = new FaskPool(new SemBicScore());
        pool.setExternalGraph(new SingleGraphAlg(given));
        assertEquals(Set.of("X1-X6", "X3-X4", "X4-X5"), adjacencies(quietly(() -> pool.search(mixed, new Parameters()))));

        FaskVote vote = new FaskVote(new DegenerateGaussianBicScore());
        vote.setExternalGraph(new SingleGraphAlg(given));
        assertEquals(Set.of("X1-X6", "X3-X4", "X4-X5"), adjacencies(quietly(() -> vote.search(mixed, new Parameters()))));

        FaskVote wrongScore = new FaskVote(new SemBicScore());
        wrongScore.setExternalGraph(new SingleGraphAlg(given));
        String message = refusal(() -> quietly(() -> wrongScore.search(mixed, new Parameters())));
        assertTrue(message, message.startsWith("These data sets have discrete variables, and the score chosen,"));
        assertTrue(message, message.contains("FASK-Vote runs FASK on each data set with this score"));
    }

    private static MultiDataSetAlgorithm pooled(String which, ScoreWrapper score) {
        return which.equals("FASK-Pool") ? new FaskPool(score) : new FaskVote(score);
    }

    /**
     * Three samples from one model, with the first two variables cut at the median into two categories.
     */
    private static List<DataModel> threeMixedDataSets() throws Exception {
        RandomUtil.getInstance().setSeed(11L);
        Graph graph = RandomGraph.randomGraph(6, 0, 6, 100, 100, 100, false);
        SemIm im = new SemIm(new SemPm(graph));
        List<DataModel> dataSets = new ArrayList<>();

        for (int k = 0; k < 3; k++) {
            DataSet sample = im.simulateData(500, false);
            Map<Node, DiscretizationSpec> specs = new HashMap<>();

            for (int c = 0; c < 2; c++) {
                double[] column = new double[sample.getNumRows()];
                for (int r = 0; r < column.length; r++) column[r] = sample.getDouble(r, c);
                Arrays.sort(column);
                specs.put(sample.getVariable(c),
                        new ContinuousDiscretizationSpec(new double[]{column[column.length / 2]}, List.of("0", "1")));
            }

            Discretizer discretizer = new Discretizer(sample, specs);
            discretizer.setVariablesCopied(true);
            DataSet mixed = discretizer.discretize();
            assertTrue(mixed.isMixed());
            dataSets.add(mixed);
        }

        return dataSets;
    }

    private interface Search {
        Graph run() throws InterruptedException;
    }

    private static Graph quietly(Search search) {
        PrintStream out = System.out;
        System.setOut(new PrintStream(OutputStream.nullOutputStream()));

        try {
            return search.run();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            System.setOut(out);
        }
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
