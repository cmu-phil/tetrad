package edu.cmu.tetrad.test;

import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.search.Fges;
import edu.cmu.tetrad.search.score.GraphScore;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Tests FGES in oracle mode, where the score is a GraphScore answering d-separation queries on a true DAG. FGES
 * should return the CPDAG of the true DAG.
 */
public class TestFgesOracle {

    private static Graph oracleFges(Graph dag) throws InterruptedException {
        Fges fges = new Fges(new GraphScore(dag));
        fges.setVerbose(false);
        return fges.search();
    }

    /**
     * A five-node DAG for which FGES used to drop the adjacency X2 --&gt; X5. The Insert of X2 --&gt; X5 with T empty
     * scored the same as the Insert with T = {X1}; the first was chosen, it was invalid when dequeued, and the second,
     * which was valid, was never tried.
     */
    @Test
    public void testFiveNodeExample() throws InterruptedException {
        List<Node> nodes = new ArrayList<>();
        for (int i = 1; i <= 5; i++) nodes.add(new GraphNode("X" + i));

        Graph dag = new EdgeListGraph(nodes);
        int[][] edges = {{1, 3}, {1, 5}, {2, 4}, {2, 5}, {3, 4}};

        for (int[] edge : edges) {
            dag.addDirectedEdge(nodes.get(edge[0] - 1), nodes.get(edge[1] - 1));
        }

        assertEquals(GraphTransforms.dagToCpdag(dag), oracleFges(dag));
    }

    /**
     * Sparse random DAGs, where the failure was most frequent (about 1 in 20 at 12 nodes and 12 edges).
     */
    @Test
    public void testSparseRandomDags() throws InterruptedException {
        RandomUtil.getInstance().setSeed(492834L);

        for (int i = 0; i < 300; i++) {
            Graph dag = RandomGraph.randomGraph(12, 0, 12, 100, 100, 100, false);
            assertEquals(GraphTransforms.dagToCpdag(dag), oracleFges(dag));
        }
    }

    /**
     * Denser random DAGs.
     */
    @Test
    public void testDenserRandomDags() throws InterruptedException {
        RandomUtil.getInstance().setSeed(492834L);

        for (int i = 0; i < 100; i++) {
            Graph dag = RandomGraph.randomGraph(10, 0, 20, 100, 100, 100, false);
            assertEquals(GraphTransforms.dagToCpdag(dag), oracleFges(dag));
        }
    }
}
