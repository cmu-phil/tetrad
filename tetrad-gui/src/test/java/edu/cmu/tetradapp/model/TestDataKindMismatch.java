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
package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.continuous.dag.Fask;
import edu.cmu.tetrad.algcomparison.algorithm.multi.FaskPool;
import edu.cmu.tetrad.algcomparison.algorithm.multi.FciIod;
import edu.cmu.tetrad.algcomparison.algorithm.multi.Ion;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.Fges;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.Pc;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pag.Gfci;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pag.RfciBsc;
import edu.cmu.tetrad.algcomparison.algorithm.other.MimbuildBollen;
import edu.cmu.tetrad.algcomparison.algorithm.other.MimbuildPca;
import edu.cmu.tetrad.algcomparison.independence.ChiSquare;
import edu.cmu.tetrad.algcomparison.independence.FisherZ;
import edu.cmu.tetrad.algcomparison.score.BdeuScore;
import edu.cmu.tetrad.algcomparison.score.DegenerateGaussianBicScore;
import edu.cmu.tetrad.algcomparison.score.SemBicScore;
import edu.cmu.tetrad.data.ContinuousDiscretizationSpec;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscretizationSpec;
import edu.cmu.tetrad.data.Discretizer;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * When the Search box will not run a search because the data is of the wrong kind, it says which kind the data is,
 * whether the algorithm or its test or score takes another kind, and what to change. It used to say "The algorithm was
 * not expecting that type of data." in every case.
 *
 * @author Claude
 */
public class TestDataKindMismatch {

    private static DataSet continuous;
    private static DataSet discrete;
    private static DataSet mixed;

    @BeforeClass
    public static void makeData() throws Exception {
        RandomUtil.getInstance().setSeed(11L);
        Graph graph = RandomGraph.randomGraph(6, 0, 6, 100, 100, 100, false);
        continuous = new SemIm(new SemPm(graph)).simulateData(500, false);
        continuous.setName("study");

        Map<Node, DiscretizationSpec> all = new HashMap<>();
        Map<Node, DiscretizationSpec> firstTwo = new HashMap<>();
        List<Node> variables = continuous.getVariables();

        for (int c = 0; c < variables.size(); c++) {
            double[] column = new double[continuous.getNumRows()];
            for (int r = 0; r < column.length; r++) column[r] = continuous.getDouble(r, c);
            Arrays.sort(column);

            DiscretizationSpec atTheMedian
                    = new ContinuousDiscretizationSpec(new double[]{column[column.length / 2]}, List.of("0", "1"));
            all.put(variables.get(c), atTheMedian);
            if (c < 2) firstTwo.put(variables.get(c), atTheMedian);
        }

        Discretizer everything = new Discretizer(continuous, all);
        everything.setVariablesCopied(false);
        discrete = everything.discretize();
        discrete.setName("study");

        Discretizer some = new Discretizer(continuous, firstTwo);
        some.setVariablesCopied(true);
        mixed = some.discretize();
        mixed.setName("study");

        assertTrue(continuous.isContinuous());
        assertTrue(discrete.isDiscrete());
        assertTrue(mixed.isMixed());
    }

    @Test
    public void testAnAlgorithmOfItsOwnKindIsNamedWithBothKinds() {
        assertEquals("RFCI-BSC is for discrete data, and the data set 'study' is continuous.\n"
                     + "Choose an algorithm for continuous data in this Search box, or convert the data in a Data box"
                     + " first.",
                refusal(new RfciBsc(), continuous));
    }

    @Test
    public void testTheTestIsBlamedWhenItIsTheMismatch() {
        assertEquals("PC cannot be run on this data as it is set up.\n"
                     + "The data set 'study' is discrete, and the test chosen, Fisher Z, is for continuous data.\n"
                     + "Choose a test for discrete data in this Search box, or convert the data in a Data box first.",
                refusal(new Pc(new FisherZ()), discrete));

        String onContinuous = refusal(new Pc(new ChiSquare()), continuous);
        assertTrue(onContinuous, onContinuous.contains("is continuous, and the test chosen, Chi Square Test, is for"
                                                       + " discrete data."));

        String onMixed = refusal(new Pc(new FisherZ()), mixed);
        assertTrue(onMixed, onMixed.contains("is mixed (continuous and discrete columns), and the test chosen"));
        assertTrue(onMixed, onMixed.contains("Choose a test for mixed data"));
    }

    @Test
    public void testTheScoreIsBlamedWhenItIsTheMismatch() {
        String message = refusal(new Fges(new BdeuScore()), continuous);

        assertTrue(message, message.startsWith("FGES cannot be run on this data as it is set up."));
        assertTrue(message, message.contains("the score chosen, BDeu Score, is for discrete data."));
        assertTrue(message, message.contains("Choose a score for continuous data"));
        assertFalse(message, message.contains("test"));
    }

    @Test
    public void testBothAreNamedWhenBothAreMismatches() {
        String message = refusal(new Gfci(new ChiSquare(), new BdeuScore()), continuous);

        assertTrue(message, message.contains("the test chosen, Chi Square Test, is for discrete data, and the score"
                                             + " chosen, BDeu Score, is for discrete data."));
        assertTrue(message, message.contains("Choose a test and a score for continuous data"));
    }

    @Test
    public void testADataSetWithNoNameIsNotQuoted() {
        DataSet unnamed = discrete.copy();
        unnamed.setName(" ");

        String message = DataKindMismatch.message(new Pc(new FisherZ()), unnamed);
        assertTrue(message, message.contains("\nThe data set is discrete, and the test chosen"));

        unnamed = continuous.copy();
        unnamed.setName("");

        message = DataKindMismatch.message(new RfciBsc(), unnamed);
        assertTrue(message, message.startsWith("RFCI-BSC is for discrete data, and the data set is continuous."));
    }

    /**
     * FASK is offered for mixed data, and handles it, but the Search box used to refuse to run it there, because FASK
     * said it took continuous data whatever its score.
     */
    @Test
    public void testFaskRunsOnMixedDataWithAScoreForMixedData() {
        assertNotNull(run(new Fask(new DegenerateGaussianBicScore()), mixed));
        assertNotNull(run(new Fask(new SemBicScore()), continuous));

        String message = refusal(new Fask(new SemBicScore()), mixed);
        assertTrue(message, message.startsWith("FASK cannot be run on this data as it is set up."));
        assertTrue(message, message.contains("the score chosen, Sem BIC Score, is for continuous data."));
    }

    /**
     * MIMBUILD requires a continuous data set, and used to say it took any kind, so that discrete data got as far as
     * the search before being refused with "Expecting a continuous dataset."
     */
    @Test
    public void testMimbuildIsRefusedDiscreteDataByKind() {
        assertEquals("Mimbuild (Bollen) is for continuous data, and the data set 'study' is discrete.\n"
                     + "Choose an algorithm for discrete data in this Search box, or convert the data in a Data box"
                     + " first.",
                refusal(new MimbuildBollen(), discrete));

        String message = refusal(new MimbuildPca(), mixed);
        assertTrue(message, message.startsWith("Mimbuild (PCA) is for continuous data, and the data set 'study' is"
                                               + " mixed"));
    }

    // ---- helpers ----

    private static Graph run(Algorithm algorithm, DataSet data) {
        PrintStream out = System.out;
        System.setOut(new PrintStream(OutputStream.nullOutputStream()));

        try {
            GeneralAlgorithmRunner runner = new GeneralAlgorithmRunner(new DataWrapper(data), new Parameters());
            runner.setAlgorithm(algorithm);
            runner.execute();
            return runner.getGraph();
        } catch (java.text.ParseException e) {
            throw new RuntimeException(e);
        } finally {
            System.setOut(out);
        }
    }

    private static String refusal(Algorithm algorithm, DataSet data) {
        try {
            run(algorithm, data);
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }

        fail("Expected the Search box to refuse the data.");
        return null;
    }

    // ---- several data sets searched together ----

    private static List<DataSet> three(DataSet first) {
        List<DataSet> dataSets = new java.util.ArrayList<>();

        for (int i = 1; i <= 3; i++) {
            DataSet copy = first.copy();
            copy.setName("run" + i);
            dataSets.add(copy);
        }

        return dataSets;
    }

    private static GeneralAlgorithmRunner several(Algorithm algorithm, List<DataSet> dataSets, Graph attached)
            throws java.text.ParseException {
        DataWrapper wrapper = new DataWrapper(dataSets.getFirst());
        edu.cmu.tetrad.data.DataModelList list = new edu.cmu.tetrad.data.DataModelList();
        list.addAll(dataSets);
        wrapper.setDataModelList(list);

        GeneralAlgorithmRunner runner = attached == null
                ? new GeneralAlgorithmRunner(wrapper, new Parameters())
                : new GeneralAlgorithmRunner(wrapper, new GraphWrapper(attached), new Parameters());
        runner.setAlgorithm(algorithm);
        return runner;
    }

    private static String refusalForSeveral(Algorithm algorithm, List<DataSet> dataSets) {
        PrintStream out = System.out;
        System.setOut(new PrintStream(OutputStream.nullOutputStream()));

        try {
            several(algorithm, dataSets, null).execute();
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        } catch (java.text.ParseException e) {
            throw new RuntimeException(e);
        } finally {
            System.setOut(out);
        }

        fail("Expected the Search box to refuse the data.");
        return null;
    }

    /**
     * A search over several data sets together had no check on the kind of data, and a test or score for another
     * kind surfaced as "Data set must be continuous." or the like from inside the search. It is now named, with the
     * data set, as for a search over one data set.
     */
    @Test
    public void testASearchOverSeveralDataSetsNamesTheTestOrScore() {
        String message = refusalForSeveral(new FciIod(new FisherZ()), three(discrete));
        assertTrue(message, message.startsWith("IOD cannot be run on this data as it is set up.\nThe data set 'run"));
        assertTrue(message, message.contains("is discrete, and the test chosen, Fisher Z, is for continuous data."));

        message = refusalForSeveral(new FciIod(new ChiSquare()), three(continuous));
        assertTrue(message, message.contains("is continuous, and the test chosen, Chi Square Test, is for discrete"
                                             + " data."));

        message = refusalForSeveral(new Ion(new FisherZ(), new BdeuScore()), three(continuous));
        assertTrue(message, message.startsWith("ION cannot be run on this data as it is set up."));
        assertTrue(message, message.contains("the score chosen, BDeu Score, is for discrete data."));
        assertFalse(message, message.contains("the test chosen"));
    }

    /**
     * IOD and ION say they take every kind of data, whatever test and score they hold. That is not read as taking
     * none, and a matching test and score run.
     */
    @Test
    public void testASearchOverSeveralDataSetsOfTheRightKindRuns() throws Exception {
        assertFalse(DataKindMismatch.refuses(new FciIod(new FisherZ()), continuous));
        assertFalse(DataKindMismatch.refuses(new FciIod(new ChiSquare()), discrete));
        assertTrue(DataKindMismatch.refuses(new FciIod(new FisherZ()), discrete));
        assertFalse(DataKindMismatch.refuses(new FaskPool(new DegenerateGaussianBicScore()), mixed));
        assertTrue(DataKindMismatch.refuses(new FaskPool(new SemBicScore()), mixed));

        PrintStream out = System.out;
        System.setOut(new PrintStream(OutputStream.nullOutputStream()));

        try {
            GeneralAlgorithmRunner runner = several(new FciIod(new FisherZ()), three(continuous), null);
            runner.execute();
            assertNotNull(runner.getGraph());
        } finally {
            System.setOut(out);
        }
    }

    /**
     * With a graph attached the check is not made. FASK-Pool, given its adjacencies, has no use for its score, and
     * runs on mixed data with a score for continuous data, as it did before.
     */
    @Test
    public void testFaskPoolWithAGraphAttachedIsNotRefusedForItsScore() throws Exception {
        List<DataSet> dataSets = three(mixed);
        List<Node> v = dataSets.getFirst().getVariables();
        Graph given = new edu.cmu.tetrad.graph.EdgeListGraph(v);
        given.addUndirectedEdge(v.get(2), v.get(3));
        given.addUndirectedEdge(v.get(3), v.get(4));

        PrintStream out = System.out;
        System.setOut(new PrintStream(OutputStream.nullOutputStream()));

        try {
            GeneralAlgorithmRunner runner = several(new FaskPool(new SemBicScore()), dataSets, given);
            runner.execute();
            assertEquals(2, runner.getGraph().getNumEdges());
        } finally {
            System.setOut(out);
        }
    }
}
