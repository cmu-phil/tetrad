///////////////////////////////////////////////////////////////////////////////
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

import edu.cmu.tetrad.algcomparison.algorithm.multi.FciIod;
import edu.cmu.tetrad.algcomparison.graph.RandomForward;
import edu.cmu.tetrad.algcomparison.independence.FisherZ;
import edu.cmu.tetrad.algcomparison.simulation.GeneralSemSimulation;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import java.util.Set;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertEquals;

/**
 * Tests the FciIod algorithm.
 *
 * @author josephramsey
 */
public class TestFciIod {

    @Test
    public void testFciIod() throws InterruptedException {
        Parameters parameters = new Parameters();

        // Simulation parameters
        parameters.set(Params.NUM_RUNS, 5);
        parameters.set(Params.NUM_MEASURES, 10);
        parameters.set(Params.NUM_LATENTS, 1);
        parameters.set(Params.AVG_DEGREE, 2);
        parameters.set(Params.MAX_DEGREE, 3);
        parameters.set(Params.MAX_INDEGREE, 2);
        parameters.set(Params.MAX_OUTDEGREE, 2);
        parameters.set(Params.CONNECTED, false);
        parameters.set(Params.SAMPLE_SIZE, 50);
        parameters.set(Params.COEF_LOW, 0.4);
        parameters.set(Params.COEF_HIGH, 0.9);
        parameters.set(Params.VAR_LOW, 0.1);
        parameters.set(Params.VAR_HIGH, 1.0);
        parameters.set(Params.VERBOSE, false);
        parameters.set(Params.SEED, 42L);

        // Algorithm parameters
        parameters.set(Params.ALPHA, 0.001);
        parameters.set(Params.DEPTH, -1);
        parameters.set(Params.STABLE_FAS, true);
        parameters.set(Params.COLLIDER_ORIENTATION_STYLE, 1);
        parameters.set(Params.MAX_DISCRIMINATING_PATH_LENGTH, -1);
        parameters.set(Params.DO_POSSIBLE_DSEP, true);
        parameters.set(Params.COMPLETE_RULE_SET_USED, true);
        parameters.set(Params.EXCLUDE_SELECTION_BIAS, true);
        parameters.set(Params.GUARANTEE_PAG, false);

        GeneralSemSimulation simulation = new GeneralSemSimulation(new RandomForward());
        simulation.createData(parameters, true);

        List<DataModel> dataSets = new ArrayList<>();
        // We need at least two datasets for IOD to be interesting, 
        // but it works with one as well. Simulation by default creates 
        // numRuns datasets? No, getNumDataModels() return the count.
        // Let's check how many it created.
        for (int i = 0; i < simulation.getNumDataModels(); i++) {
            dataSets.add(simulation.getDataModel(i));
        }
        
        // If we want multiple datasets from one simulation run, 
        // we might need to adjust numRuns or just call createData again.
        // For now, let's try with what we have.
        
        parameters.set(Params.NUM_RUNS, 5);
        simulation.createData(parameters, true);
        dataSets.clear();
        for (int i = 0; i < simulation.getNumDataModels(); i++) {
            dataSets.add(simulation.getDataModel(i));
        }

        FciIod fciIod = new FciIod(new FisherZ());
        Graph result = fciIod.search(dataSets, parameters);

        assertNotNull(result);
        System.out.println("Resulting graph nodes: " + result.getNodeNames());
        System.out.println("Resulting graph edges: " + result.getEdges());
    }

    @Test
    public void testFciIodSingleDataset() throws InterruptedException {
        Parameters parameters = new Parameters();

        // Simulation parameters
        parameters.set(Params.NUM_RUNS, 5);
        parameters.set(Params.NUM_MEASURES, 10);
        parameters.set(Params.NUM_LATENTS, 1);
        parameters.set(Params.AVG_DEGREE, 2);
        parameters.set(Params.MAX_DEGREE, 3);
        parameters.set(Params.MAX_INDEGREE, 2);
        parameters.set(Params.MAX_OUTDEGREE, 2);
        parameters.set(Params.CONNECTED, false);
        parameters.set(Params.SAMPLE_SIZE, 50);
        parameters.set(Params.COEF_LOW, 0.4);
        parameters.set(Params.COEF_HIGH, 0.9);
        parameters.set(Params.VAR_LOW, 0.1);
        parameters.set(Params.VAR_HIGH, 1.0);
        parameters.set(Params.VERBOSE, false);
        parameters.set(Params.SEED, 42L);

        // Algorithm parameters
        parameters.set(Params.ALPHA, 0.001);
        parameters.set(Params.DEPTH, -1);
        parameters.set(Params.STABLE_FAS, true);
        parameters.set(Params.COLLIDER_ORIENTATION_STYLE, 1);
        parameters.set(Params.MAX_DISCRIMINATING_PATH_LENGTH, -1);
        parameters.set(Params.DO_POSSIBLE_DSEP, true);
        parameters.set(Params.COMPLETE_RULE_SET_USED, true);
        parameters.set(Params.EXCLUDE_SELECTION_BIAS, true);
        parameters.set(Params.GUARANTEE_PAG, false);

        GeneralSemSimulation simulation = new GeneralSemSimulation(new RandomForward());
        simulation.createData(parameters, true);

        DataModel dataSet = simulation.getDataModel(0);

        FciIod fciIod = new FciIod(new FisherZ());
        Graph result = fciIod.search(dataSet, parameters);

        assertNotNull(result);
        System.out.println("Resulting graph nodes: " + result.getNodeNames());
        System.out.println("Resulting graph edges: " + result.getEdges());
    }

    /**
     * Tests the convention for pairs of variables that are never jointly measured in any dataset. Data are simulated
     * from the chain X1 -&gt; X2 -&gt; X3 -&gt; X4 -&gt; X5; dataset A measures X1, X2, X3 and dataset B measures X3,
     * X4, X5, so the pairs in {X1, X2} x {X4, X5} are never jointly measured. Since no dataset can rule such an edge
     * out, the pooled test correctly leaves those pairs adjacent; but by the same token no dataset supports any
     * endpoint orientation on those edges, so both endpoints must be circles.
     */
    @Test
    public void testNeverJointlyMeasuredPairsGetCircleEndpoints() throws Exception {
        RandomUtil.getInstance().setSeed(77L);

        Graph dag = new EdgeListGraph();
        for (int i = 1; i <= 5; i++) dag.addNode(new GraphNode("X" + i));
        for (int i = 1; i <= 4; i++) dag.addDirectedEdge(dag.getNode("X" + i), dag.getNode("X" + (i + 1)));

        SemPm pm = new SemPm(dag);
        SemIm im = new SemIm(pm);
        DataSet data = im.simulateData(2000, false);

        DataSet dA = data.subsetColumns(List.of(data.getVariable("X1"), data.getVariable("X2"),
                data.getVariable("X3")));
        dA.setName("A");
        DataSet dB = data.subsetColumns(List.of(data.getVariable("X3"), data.getVariable("X4"),
                data.getVariable("X5")));
        dB.setName("B");

        FciIod fciIod = new FciIod(new FisherZ());
        Graph result = fciIod.search(List.of((DataModel) dA, dB), new Parameters());

        assertNotNull(result);

        Set<String> neverJoint = Set.of("X1|X4", "X1|X5", "X2|X4", "X2|X5");

        for (Edge edge : result.getEdges()) {
            String n1 = edge.getNode1().getName();
            String n2 = edge.getNode2().getName();
            String key = n1.compareTo(n2) <= 0 ? n1 + "|" + n2 : n2 + "|" + n1;

            if (neverJoint.contains(key)) {
                assertEquals("Endpoint at " + n1 + " on never-jointly-measured edge " + edge
                             + " should be a circle", Endpoint.CIRCLE, edge.getEndpoint1());
                assertEquals("Endpoint at " + n2 + " on never-jointly-measured edge " + edge
                             + " should be a circle", Endpoint.CIRCLE, edge.getEndpoint2());
            }
        }

        // The pairs cannot be ruled out by any dataset, so at least one such edge should in fact be present
        // for the endpoint assertion above to be meaningful.
        boolean anyNeverJointEdge = false;
        for (Edge edge : result.getEdges()) {
            String n1 = edge.getNode1().getName();
            String n2 = edge.getNode2().getName();
            String key = n1.compareTo(n2) <= 0 ? n1 + "|" + n2 : n2 + "|" + n1;
            if (neverJoint.contains(key)) anyNeverJointEdge = true;
        }
        org.junit.Assert.assertTrue("Expected at least one edge between never-jointly-measured pairs",
                anyNeverJointEdge);
    }
}
