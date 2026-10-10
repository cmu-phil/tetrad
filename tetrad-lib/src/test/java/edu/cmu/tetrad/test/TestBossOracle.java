package edu.cmu.tetrad.test;

import edu.cmu.tetrad.algcomparison.score.MSepScore;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.search.Boss;
import edu.cmu.tetrad.search.PermutationSearch;
import edu.cmu.tetrad.search.score.GraphScore;
import edu.cmu.tetrad.search.utils.BesPermutation;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests BOSS in oracle mode, where the score is a GraphScore answering d-separation queries on a true DAG.
 */
public class TestBossOracle {

    private static Graph oracleBoss(Graph dag, List<Node> start, boolean useBes, int numThreads, boolean cpdag)
            throws InterruptedException {
        Boss boss = new Boss(new GraphScore(dag));
        boss.setUseBes(useBes);
        boss.setNumThreads(numThreads);
        PermutationSearch search = new PermutationSearch(boss);
        search.setOrder(start);
        return search.search(cpdag);
    }

    /**
     * With the BES step, oracle BOSS returns the CPDAG of the true DAG from any starting order, single or
     * multithreaded.
     */
    @Test
    public void testCpdagFromOracle() throws InterruptedException {
        RandomUtil.getInstance().setSeed(3829483L);

        for (int i = 0; i < 25; i++) {
            Graph dag = RandomGraph.randomGraph(10, 0, 20, 100, 100, 100, false);
            Graph cpdag = GraphTransforms.dagToCpdag(dag);

            List<Node> start = new ArrayList<>(dag.getNodes());
            RandomUtil.shuffle(start);

            assertEquals(cpdag, oracleBoss(dag, start, true, 1, true));
            assertEquals(cpdag, oracleBoss(dag, start, true, 4, true));
        }
    }

    /**
     * With the BES step, the DAG returned by oracle BOSS is a DAG in the Markov equivalence class of the true DAG.
     */
    @Test
    public void testDagFromOracle() throws InterruptedException {
        RandomUtil.getInstance().setSeed(3829483L);

        for (int i = 0; i < 25; i++) {
            Graph dag = RandomGraph.randomGraph(10, 0, 20, 100, 100, 100, false);

            List<Node> start = new ArrayList<>(dag.getNodes());
            RandomUtil.shuffle(start);

            Graph out = oracleBoss(dag, start, true, 1, false);

            assertTrue(out.paths().isLegalDag());
            assertEquals(GraphTransforms.dagToCpdag(dag), GraphTransforms.dagToCpdag(out));
        }
    }

    /**
     * Without the BES step, the DAG returned for the final order is still an I-map of the true DAG that contains its
     * adjacencies; it may have extra edges.
     */
    @Test
    public void testNoBesGivesSupergraph() throws InterruptedException {
        RandomUtil.getInstance().setSeed(3829483L);

        for (int i = 0; i < 25; i++) {
            Graph dag = RandomGraph.randomGraph(10, 0, 20, 100, 100, 100, false);

            List<Node> start = new ArrayList<>(dag.getNodes());
            RandomUtil.shuffle(start);

            Graph out = oracleBoss(dag, start, false, 1, false);

            assertTrue(out.paths().isLegalDag());

            for (Node x : dag.getNodes()) {
                for (Node y : dag.getAdjacentNodes(x)) {
                    assertTrue(out.isAdjacentTo(x, y));
                }
            }
        }
    }

    /**
     * BES started at the true CPDAG must not change it, whatever order the variables are listed in. This fails if
     * BesPermutation indexes the score by position in the order instead of by the score's variable list.
     */
    @Test
    public void testBesLeavesTrueCpdagAlone() throws InterruptedException {
        RandomUtil.getInstance().setSeed(3829483L);

        for (int i = 0; i < 25; i++) {
            Graph dag = RandomGraph.randomGraph(10, 0, 20, 100, 100, 100, false);
            Graph cpdag = GraphTransforms.dagToCpdag(dag);
            Graph graph = new EdgeListGraph(cpdag);

            List<Node> order = new ArrayList<>(dag.getNodes());
            RandomUtil.shuffle(order);

            BesPermutation bes = new BesPermutation(new GraphScore(dag));
            bes.setVerbose(false);
            bes.bes(graph, order, new ArrayList<>(order));

            assertEquals(cpdag, graph);
        }
    }

    /**
     * The algcomparison wrapper runs from a graph, using the m-separation score, with no data.
     */
    @Test
    public void testWrapper() throws InterruptedException {
        RandomUtil.getInstance().setSeed(3829483L);

        Graph dag = RandomGraph.randomGraph(10, 0, 20, 100, 100, 100, false);

        Parameters parameters = new Parameters();
        parameters.set(Params.USE_BES, true);
        parameters.set(Params.USE_DATA_ORDER, false);

        edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.Boss boss
                = new edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.Boss(new MSepScore(dag));

        assertEquals(GraphTransforms.dagToCpdag(dag), boss.search(null, parameters));
    }
}
