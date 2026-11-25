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

import edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.IsFges;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.Pcmci;
import edu.cmu.tetrad.algcomparison.graph.RandomSingleFactorMim;
import edu.cmu.tetrad.algcomparison.graph.RandomTwoFactorMim;
import edu.cmu.tetrad.algcomparison.independence.FisherZ;
import edu.cmu.tetrad.bayes.BayesPm;
import edu.cmu.tetrad.bayes.MlBayesIm;
import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.ParamDescriptions;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Places where code and the parameter definitions disagreed about a default in a way that changing a number in the
 * code did not settle.
 * <p>
 * The instance row of the instance-specific searches is registered as counting from 1, with 1 the default and the
 * least value allowed. The code counted from 0. So the interface, which fills in the registered default, searched
 * for the second row, and could not be asked for the first.
 * <p>
 * The number of children for each group of a measurement model was registered with a default of 0, with which the
 * graph cannot be made at all. And the registered number of structural edges, 5, was more than can be drawn among the
 * registered number of structural nodes, then 3.
 * <p>
 * The depth of PCMCI is registered with a default of -1, meaning no limit. In PCMCI a negative depth skipped the step
 * that selects conditioning sets instead of lifting its limit.
 * <p>
 * The range the error covariance of a bidirected edge is drawn from was registered as 0 to 0, with which such an edge
 * has no effect. The code drew from 0.1 to 0.2, and the definition now says so.
 *
 * @author Claude (Anthropic)
 */
public class TestRegisteredDefaultsHonored {

    private static DataSet discreteData() {
        RandomUtil.getInstance().setSeed(31L);
        Graph graph = RandomGraph.randomGraph(5, 0, 5, 100, 100, 100, false);
        BayesPm pm = new BayesPm(graph, 2, 3);
        return new MlBayesIm(pm, MlBayesIm.InitializationMethod.RANDOM).simulateData(300, false);
    }

    private static Graph search(DataSet data, Integer instanceRow) throws InterruptedException {
        Parameters parameters = new Parameters();

        if (instanceRow != null) {
            parameters.set(Params.INSTANCE_ROW, instanceRow);
        }

        return new IsFges().search(data, parameters);
    }

    /**
     * The instance row counts from 1: the first row is 1, the last is the number of rows, and 0 is refused with a
     * message that says what is allowed.
     */
    @Test
    public void testInstanceRowCountsFromOne() throws InterruptedException {
        DataSet data = discreteData();
        int rows = data.getNumRows();

        // Left alone, it is the registered default, the first row.
        assertEquals(search(data, 1), search(data, null));

        // The last row is allowed.
        search(data, rows);

        for (int refused : new int[]{0, rows + 1}) {
            try {
                search(data, refused);
                fail("An instance row of " + refused + " should be refused.");
            } catch (IllegalArgumentException e) {
                assertTrue(e.getMessage(),
                        e.getMessage().contains("Instance row out of range: " + refused + ". It must be from 1 to "
                                                + rows + "."));
            }
        }
    }

    /**
     * A measurement model graph can be made with nothing set, which takes the registered defaults.
     */
    @Test
    public void testMeasurementModelGraphsWithRegisteredDefaults() {
        assertEquals(3, ParamDescriptions.getInstance().get("mimNumChildrenPerGroup").getDefaultValue());
        assertEquals(1, ParamDescriptions.getInstance().get("mimNumChildrenPerGroup").getLowerBoundInt());
        assertEquals(5, ParamDescriptions.getInstance().get("mimNumStructuralNodes").getDefaultValue());
        assertEquals(5, ParamDescriptions.getInstance().get("mimNumStructuralEdges").getDefaultValue());

        for (Graph graph : new Graph[]{new RandomSingleFactorMim().createGraph(new Parameters()),
                new RandomTwoFactorMim().createGraph(new Parameters())}) {
            long measured = graph.getNodes().stream().filter(node -> node.getNodeType() == NodeType.MEASURED).count();
            long latent = graph.getNodes().stream().filter(node -> node.getNodeType() == NodeType.LATENT).count();

            assertTrue("The graph has no latent variables.", latent > 0);
            assertTrue("The graph has no measured variables.", measured > 0);
        }
    }

    /**
     * A time series in which each variable depends on its own last value and on the last values of about a quarter
     * of the others.
     */
    private static DataSet series(long seed, int numVariables, int numRows) {
        Random random = new Random(seed);
        double[][] weights = new double[numVariables][numVariables];

        for (int i = 0; i < numVariables; i++) {
            weights[i][i] = 0.4;

            for (int j = 0; j < numVariables; j++) {
                if (i != j && random.nextDouble() < 0.25) {
                    weights[i][j] = (random.nextBoolean() ? 1 : -1) * 0.3;
                }
            }
        }

        double[][] values = new double[numRows][numVariables];

        for (int t = 1; t < numRows; t++) {
            for (int i = 0; i < numVariables; i++) {
                double value = random.nextGaussian();

                for (int j = 0; j < numVariables; j++) {
                    value += weights[i][j] * values[t - 1][j];
                }

                values[t][i] = value;
            }
        }

        List<Node> variables = new ArrayList<>();

        for (int j = 0; j < numVariables; j++) {
            variables.add(new ContinuousVariable("X" + (j + 1)));
        }

        return new BoxDataSet(new DoubleDataBox(values), variables);
    }

    private static Set<String> pcmci(DataSet data, Integer depth) throws InterruptedException {
        Parameters parameters = new Parameters();
        parameters.set(Params.TIME_LAG, 2);

        if (depth != null) {
            parameters.set(Params.DEPTH, depth);
        }

        Set<String> edges = new TreeSet<>();

        for (Edge edge : new Pcmci(new FisherZ()).search(data, parameters).getEdges()) {
            edges.add(edge.toString());
        }

        return edges;
    }

    /**
     * In PCMCI a depth of -1 means no limit: it gives what a limit too large to matter gives, and it is what a depth
     * left alone gives. On the first series below a depth of -1 used to give 35 edges where a depth of 100 gave 29,
     * since it skipped the step that selects conditioning sets.
     */
    @Test
    public void testPcmciDepthOfMinusOneMeansNoLimit() throws InterruptedException {
        for (long seed : new long[]{1L, 3L}) {
            DataSet data = series(seed, 10, 200);

            assertEquals(pcmci(data, 100), pcmci(data, -1));
            assertEquals(pcmci(data, -1), pcmci(data, null));
        }

        // On the second series a limit of 3 gives another graph, so a depth left alone is not a limit of 3.
        assertNotEquals(pcmci(series(3L, 10, 200), 3), pcmci(series(3L, 10, 200), null));
    }

    /**
     * A bidirected edge has an effect in a model made with nothing set: the error covariance it stands for is
     * drawn from the registered range, 0.1 to 0.2, with either sign.
     */
    @Test
    public void testBidirectedEdgeHasAnEffectByDefault() {
        ParamDescriptions registry = ParamDescriptions.getInstance();
        assertEquals(0.1, registry.get("covLow").getDefaultValue());
        assertEquals(0.2, registry.get("covHigh").getDefaultValue());

        Graph graph = new EdgeListGraph(List.of(new GraphNode("X"), new GraphNode("Y")));
        graph.addBidirectedEdge(graph.getNode("X"), graph.getNode("Y"));

        for (int i = 0; i < 20; i++) {
            double covariance = Math.abs(new SemIm(new SemPm(graph)).getImplCovar(true).get(0, 1));
            assertTrue("The covariance is " + covariance, covariance >= 0.1 && covariance <= 0.2);

            covariance = Math.abs(new SemIm(new SemPm(graph), new Parameters()).getImplCovar(true).get(0, 1));
            assertTrue("The covariance is " + covariance, covariance >= 0.1 && covariance <= 0.2);
        }
    }

    /**
     * The two deltas of a scale-free graph are real numbers, and are registered as such.
     */
    @Test
    public void testScaleFreeDeltasAreRealNumbers() {
        for (String name : new String[]{"scaleFreeDeltaIn", "scaleFreeDeltaOut"}) {
            assertEquals(3.0, ParamDescriptions.getInstance().get(name).getDefaultValue());
            assertEquals(0.0, ParamDescriptions.getInstance().get(name).getLowerBoundDouble(), 0.0);
        }
    }
}
