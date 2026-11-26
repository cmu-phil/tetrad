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

import edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.BossTopK;
import edu.cmu.tetrad.algcomparison.score.SemBicScore;
import edu.cmu.tetrad.data.DataModelList;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The BOSS Top-K algorithm hands back the models it kept, not only the best graph. Before, they were printed to the
 * console and could not be reached from code that called the algorithm, such as py-tetrad's.
 *
 * @author Claude
 */
public class TestBossTopKModels {

    private static DataSet data(long seed) throws Exception {
        RandomUtil.getInstance().setSeed(seed);
        Graph truth = RandomGraph.randomGraph(12, 0, 24, 100, 100, 100, false);
        DataSet data = new SemIm(new SemPm(truth)).simulateData(1000, false);
        List<Node> variables = new ArrayList<>(data.getVariables());
        Collections.shuffle(variables, new Random(50L));
        return data.subsetColumns(variables);
    }

    private static Parameters split() {
        Parameters parameters = new Parameters();
        parameters.set(Params.TOP_K, 5);
        parameters.set(Params.SPLIT_DELTA, 2.0);
        parameters.set(Params.MAX_RUNS, 200);
        return parameters;
    }

    private static <T> T quietly(Callable<T> call) throws Exception {
        PrintStream out = System.out;
        System.setOut(new PrintStream(OutputStream.nullOutputStream()));

        try {
            return call.call();
        } finally {
            System.setOut(out);
        }
    }

    @Test
    public void testTheModelsKeptAreHandedBackBestFirst() throws Exception {
        DataSet data = data(9000L);
        BossTopK algorithm = new BossTopK(new SemBicScore());

        assertTrue(algorithm.getTopGraphs().isEmpty());
        assertTrue(algorithm.getTopScores().isEmpty());
        assertTrue(algorithm.getTopOrders().isEmpty());

        Graph best = quietly(() -> algorithm.search(data, split()));

        List<Graph> graphs = algorithm.getTopGraphs();
        List<Double> scores = algorithm.getTopScores();
        List<List<Node>> orders = algorithm.getTopOrders();

        assertTrue("More than one model was expected with a split delta of 2.", graphs.size() > 1);
        assertTrue(graphs.size() <= 5);
        assertEquals(graphs.size(), scores.size());
        assertEquals(graphs.size(), orders.size());

        // The first is the graph the search returned.
        assertEquals(best.getEdges(), graphs.getFirst().getEdges());

        for (int i = 1; i < scores.size(); i++) {
            assertTrue("Scores are not best first: " + scores, scores.get(i) <= scores.get(i - 1));
        }

        for (List<Node> order : orders) {
            assertEquals(data.getNumColumns(), order.size());
            assertEquals(new HashSet<>(data.getVariables()), new HashSet<>(order));
        }

        // A CPDAG by default, and each model a different one.
        assertEquals(graphs.size(), new HashSet<>(graphs.stream().map(Graph::getEdges).toList()).size());
    }

    /**
     * Without splitting and with one start there is one model, the one returned.
     */
    @Test
    public void testWithoutSplittingThereIsOneModel() throws Exception {
        DataSet data = data(9001L);
        BossTopK algorithm = new BossTopK(new SemBicScore());
        Graph best = quietly(() -> algorithm.search(data, new Parameters()));

        assertEquals(1, algorithm.getTopGraphs().size());
        assertEquals(best.getEdges(), algorithm.getTopGraphs().getFirst().getEdges());
    }

    /**
     * With bootstrapping the search is run on each resample. The models of one resample are not the models of the
     * data, so none are given.
     */
    @Test
    public void testWithBootstrappingThereAreNoneToGive() throws Exception {
        DataSet data = data(9002L);
        BossTopK algorithm = new BossTopK(new SemBicScore());

        quietly(() -> algorithm.search(data, split()));
        assertFalse(algorithm.getTopGraphs().isEmpty());

        Parameters parameters = split();
        parameters.set(Params.NUMBER_RESAMPLING, 3);
        parameters.set(Params.SEED, 7L);
        parameters.set(Params.SAVE_BOOTSTRAP_GRAPHS, true);
        quietly(() -> algorithm.search(data, parameters));

        assertTrue(algorithm.getTopGraphs().isEmpty());
        assertTrue(algorithm.getTopScores().isEmpty());
        assertTrue(algorithm.getTopOrders().isEmpty());
        assertEquals(3, algorithm.getBootstrapGraphs().size());
    }

    /**
     * Several data sets pooled into one search are one search, and its models are given.
     */
    @Test
    public void testPooledDataSetsGiveTheirModels() throws Exception {
        DataModelList pooled = new DataModelList();
        RandomUtil.getInstance().setSeed(9003L);
        SemIm im = new SemIm(new SemPm(RandomGraph.randomGraph(8, 0, 12, 100, 100, 100, false)));
        pooled.add(im.simulateData(500, false));
        pooled.add(im.simulateData(500, false));

        BossTopK algorithm = new BossTopK(new SemBicScore());
        Graph best = quietly(() -> algorithm.search(pooled, split()));

        assertFalse(algorithm.getTopGraphs().isEmpty());
        assertEquals(best.getEdges(), algorithm.getTopGraphs().getFirst().getEdges());
    }

    /**
     * The models are not saved with a session. An algorithm read back has none until it is run again.
     */
    @Test
    public void testAnAlgorithmReadBackFromASessionHasNoneUntilItIsRun() throws Exception {
        DataSet data = data(9004L);
        BossTopK algorithm = new BossTopK(new SemBicScore());
        quietly(() -> algorithm.search(data, split()));
        assertFalse(algorithm.getTopGraphs().isEmpty());

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(algorithm);
        }

        BossTopK readBack;
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            readBack = (BossTopK) in.readObject();
        }

        assertTrue(readBack.getTopGraphs().isEmpty());
        assertTrue(readBack.getTopScores().isEmpty());
        assertTrue(readBack.getTopOrders().isEmpty());

        quietly(() -> readBack.search(data, split()));
        assertEquals(algorithm.getTopScores(), readBack.getTopScores());
    }
}
