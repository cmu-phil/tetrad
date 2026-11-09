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
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.Boss;
import edu.cmu.tetrad.algcomparison.score.BlocksBicScore;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.blocks.BlockSpec;
import edu.cmu.tetrad.search.blocks.BlocksUtil;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Parameters;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Pins the Search box's clusters-parent mode, added 2026-10-11: a {@link GeneralAlgorithmRunner} built over a
 * {@link LatentClustersRunner} parent runs the block search once and appends the measurement edges, exactly as the
 * legacy Latent Structure box did; foreign clusters are still rejected; and the new field is serialization-safe.
 */
public class TestSearchBoxWithClusters {

    /**
     * Two latents with three pure indicators each, simulated linearly; the true clusters are supplied as the
     * BlockSpec.
     */
    private static DataSet simulate(long seed) throws Exception {
        edu.cmu.tetrad.util.RandomUtil.getInstance().setSeed(seed);
        Graph g = new EdgeListGraph();
        Node l1 = new GraphNode("L1");
        Node l2 = new GraphNode("L2");
        l1.setNodeType(edu.cmu.tetrad.graph.NodeType.LATENT);
        l2.setNodeType(edu.cmu.tetrad.graph.NodeType.LATENT);
        g.addNode(l1);
        g.addNode(l2);
        for (int i = 1; i <= 6; i++) {
            Node x = new GraphNode("X" + i);
            g.addNode(x);
            g.addDirectedEdge(i <= 3 ? l1 : l2, x);
        }
        g.addDirectedEdge(l1, l2);
        SemPm pm = new SemPm(g);
        SemIm im = new SemIm(pm);
        return im.simulateData(500, false);
    }

    private static BlockSpec spec(DataSet data) {
        return BlocksUtil.toSpec(List.of(List.of(0, 1, 2), List.of(3, 4, 5)), data);
    }

    @Test
    public void clustersParentRunsOnceAndAppendsMeasurementEdges() throws Exception {
        DataSet data = simulate(38);
        DataWrapper dw = new DataWrapper(data);
        Parameters parameters = new Parameters();

        LatentClustersRunner clusters = new LatentClustersRunner(dw, parameters);
        clusters.setBlockSpec(spec(data));

        GeneralAlgorithmRunner runner = new GeneralAlgorithmRunner(dw, clusters, parameters);
        runner.setAlgorithm(new Boss(new BlocksBicScore()));
        runner.execute();

        Graph graph = runner.getGraph();
        assertNotNull(graph);
        assertEquals(1, runner.getGraphs().size());

        // The block variables and all the measured variables are in the result.
        BlockSpec spec = clusters.getBlockSpec();
        for (int i = 0; i < spec.blocks().size(); i++) {
            Node latent = graph.getNode(spec.blockVariables().get(i).getName());
            assertNotNull(latent);
            for (int j : spec.blocks().get(i)) {
                Node indicator = graph.getNode(data.getVariables().get(j).getName());
                assertNotNull(indicator);
                assertTrue("measurement edge " + latent + " -> " + indicator,
                        graph.isParentOf(latent, indicator));
            }
        }
    }

    @Test
    public void executeRefetchesTheSpecFromTheParent() throws Exception {
        DataSet data = simulate(39);
        DataWrapper dw = new DataWrapper(data);
        Parameters parameters = new Parameters();

        LatentClustersRunner clusters = new LatentClustersRunner(dw, parameters);
        clusters.setBlockSpec(spec(data));

        GeneralAlgorithmRunner runner = new GeneralAlgorithmRunner(dw, clusters, parameters);
        runner.setAlgorithm(new Boss(new BlocksBicScore()));

        // The clusters are re-run after this search box was built: execute() must use the new spec.
        BlockSpec edited = BlocksUtil.toSpec(List.of(List.of(0, 1, 2, 3), List.of(4, 5)), data);
        clusters.setBlockSpec(edited);
        runner.execute();

        assertSame(edited, runner.getBlockSpec());
        Graph graph = runner.getGraph();
        Node firstLatent = graph.getNode(edited.blockVariables().get(0).getName());
        assertNotNull(firstLatent);
        assertTrue(graph.isParentOf(firstLatent, graph.getNode(data.getVariables().get(3).getName())));
    }

    @Test
    public void foreignClustersAreRejected() throws Exception {
        DataSet data = simulate(40);
        DataSet other = simulate(41);
        // Rename a variable so the fallback name comparison fails too.
        other.getVariable("X6").setName("Y6");

        DataWrapper dw = new DataWrapper(data);
        Parameters parameters = new Parameters();

        LatentClustersRunner clusters = new LatentClustersRunner(new DataWrapper(other), parameters);
        clusters.setBlockSpec(spec(other));

        GeneralAlgorithmRunner runner = new GeneralAlgorithmRunner(dw, clusters, parameters);
        runner.setAlgorithm(new Boss(new BlocksBicScore()));

        try {
            runner.execute();
            fail("Foreign clusters should be rejected");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("latent clusters"));
        }
    }

    /**
     * The new field reads as null from streams written before it existed (simulated here by writing a runner built
     * without clusters), and a runner written with it still round-trips.
     */
    @Test
    public void serializationRoundTripsWithAndWithoutTheClustersField() throws Exception {
        DataSet data = simulate(42);
        DataWrapper dw = new DataWrapper(data);
        Parameters parameters = new Parameters();

        GeneralAlgorithmRunner plain = new GeneralAlgorithmRunner(dw, parameters);
        GeneralAlgorithmRunner plainBack = (GeneralAlgorithmRunner) roundTrip(plain);
        assertNotNull(plainBack);

        LatentClustersRunner clusters = new LatentClustersRunner(dw, parameters);
        clusters.setBlockSpec(spec(data));
        GeneralAlgorithmRunner withClusters = new GeneralAlgorithmRunner(dw, clusters, parameters);
        GeneralAlgorithmRunner back = (GeneralAlgorithmRunner) roundTrip(withClusters);
        assertNotNull(back.getBlockSpec());
    }

    private static Object roundTrip(Object o) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(o);
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return in.readObject();
        }
    }
}
