package edu.cmu.tetrad.test;

import edu.cmu.tetrad.algcomparison.algorithm.multi.Images;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.Boss;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.Pc;
import edu.cmu.tetrad.algcomparison.algorithm.oracle.pag.Gfci;
import edu.cmu.tetrad.algcomparison.independence.FisherZ;
import edu.cmu.tetrad.algcomparison.score.SemBicScore;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataModelList;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Pins the pooled-data-set path of AbstractBootstrapAlgorithm: passing a DataModelList to a score- or test-based
 * algorithm pools the data sets IMaGES-style. In particular BOSS + SEM-BIC on a DataModelList must reproduce the
 * Images wrapper edge for edge, pooling must help relative to a single small data set, the algorithm's wrappers must
 * be restored afterwards, and the pooled path must bootstrap.
 */
public class TestPooledDataSets {

    private static DataModelList fiveSmallDataSets(long seed) throws Exception {
        RandomUtil.getInstance().setSeed(seed);
        Graph dag = RandomGraph.randomDag(12, 0, 18, 100, 100, 100, false);
        SemPm pm = new SemPm(dag);
        DataModelList list = new DataModelList();
        for (int i = 0; i < 5; i++) {
            DataSet d = new SemIm(pm).simulateData(80, false);
            d.setName("subj" + i);
            list.add(d);
        }
        list.setName("five");
        Graph cpdag = GraphTransforms.dagToCpdag(dag);
        list.setKnowledge(new edu.cmu.tetrad.data.Knowledge()); // no knowledge
        TRUTH = cpdag;
        return list;
    }

    private static Graph TRUTH;

    private static Parameters params() {
        Parameters p = new Parameters();
        p.set(Params.PENALTY_DISCOUNT, 2);
        p.set(Params.ALPHA, 0.01);
        p.set(Params.SEED, 5);
        p.set(Params.VERBOSE, false);
        p.set(Params.NUMBER_RESAMPLING, 0);
        return p;
    }

    private static double adjacencyRecall(Graph truth, Graph est) {
        int tp = 0, fn = 0;
        for (Edge e : truth.getEdges()) {
            if (est.isAdjacentTo(est.getNode(e.getNode1().getName()), est.getNode(e.getNode2().getName()))) tp++;
            else fn++;
        }
        return tp / (double) (tp + fn);
    }

    @Test
    public void testBossPooledEqualsImagesWrapper() throws Exception {
        DataModelList list = fiveSmallDataSets(1);
        Parameters p = params();

        Graph fromImages = new Images(new SemBicScore()).search(new ArrayList<DataModel>(list), p);
        Boss boss = new Boss(new SemBicScore());
        Graph pooled = boss.search(list, p);

        assertEquals("BOSS + pooled score should reproduce the Images wrapper exactly", fromImages, pooled);
        assertTrue("Score wrapper must be restored after the search",
                boss.getScoreWrapper() instanceof SemBicScore);
    }

    @Test
    public void testPoolingHelpsScoreAndTestBased() throws Exception {
        DataModelList list = fiveSmallDataSets(1);
        Parameters p = params();

        Boss boss = new Boss(new SemBicScore());
        double single = adjacencyRecall(TRUTH, boss.search(list.getFirst(), p));
        double pooled = adjacencyRecall(TRUTH, boss.search(list, p));
        assertTrue("Pooling five n=80 data sets should raise BOSS adjacency recall: " + single + " -> " + pooled,
                pooled > single + 0.2);

        Pc pc = new Pc(new FisherZ());
        double singlePc = adjacencyRecall(TRUTH, pc.search(list.getFirst(), p));
        double pooledPc = adjacencyRecall(TRUTH, pc.search(list, p));
        assertTrue("Pooling (Fisher) should raise PC adjacency recall: " + singlePc + " -> " + pooledPc,
                pooledPc > singlePc + 0.2);
        assertTrue("Test wrapper must be restored after the search",
                pc.getIndependenceWrapper() instanceof FisherZ);

        // Score + test both pooled.
        Gfci gfci = new Gfci(new FisherZ(), new SemBicScore());
        Graph g = gfci.search(list, p);
        assertTrue(adjacencyRecall(TRUTH, g) > 0.6);
        assertTrue(gfci.getScoreWrapper() instanceof SemBicScore);
        assertTrue(gfci.getIndependenceWrapper() instanceof FisherZ);
    }

    @Test
    public void testPooledBootstrap() throws Exception {
        DataModelList list = fiveSmallDataSets(2);
        Parameters p = params();
        p.set(Params.NUMBER_RESAMPLING, 15);
        p.set(Params.PERCENT_RESAMPLE_SIZE, 100);
        p.set(Params.RESAMPLING_WITH_REPLACEMENT, true);
        p.set(Params.ADD_ORIGINAL_DATASET, false);

        Boss boss = new Boss(new SemBicScore());
        Graph g = boss.search(list, p);
        assertNotNull("Bootstrapped pooled search must attach a sampling graph",
                ((EdgeListGraph) g).getAncillaryGraph("samplingGraph"));
        assertTrue(adjacencyRecall(TRUTH, g) > 0.6);
        assertTrue(boss.getScoreWrapper() instanceof SemBicScore);
    }

    /**
     * With a time lag, BOSS on a DataModelList must lag each data set separately and then pool, reproducing the
     * Images wrapper (which lags internally) exactly, with the wrapper's knowledge left as base knowledge.
     */
    @Test
    public void testPooledTimeLagEqualsImagesWrapper() throws Exception {
        java.util.Random rnd = new java.util.Random(7);
        DataModelList list = new DataModelList();
        for (int k = 0; k < 2; k++) {
            java.util.List<edu.cmu.tetrad.graph.Node> vars = java.util.List.of(
                    new edu.cmu.tetrad.data.ContinuousVariable("X"), new edu.cmu.tetrad.data.ContinuousVariable("Y"));
            DataSet d = new edu.cmu.tetrad.data.BoxDataSet(new edu.cmu.tetrad.data.DoubleDataBox(150, 2), vars);
            double x = 0, y = 0;
            for (int t = 0; t < 150; t++) {
                double nx = 0.5 * x + rnd.nextGaussian();
                double ny = 0.5 * y + 0.8 * x + rnd.nextGaussian();
                x = nx;
                y = ny;
                d.setDouble(t, 0, x);
                d.setDouble(t, 1, y);
            }
            d.setName("region" + k);
            list.add(d);
        }
        Parameters p = params();
        p.set(Params.TIME_LAG, 1);

        Graph fromImages = new Images(new SemBicScore()).search(new ArrayList<DataModel>(list), p);
        Boss boss = new Boss(new SemBicScore());
        Graph pooled = boss.search(list, p);

        assertEquals("Lagged pooled BOSS should reproduce lagged Images", fromImages, pooled);
        assertTrue(pooled.getNode("X:1") != null);
        for (String name : boss.getKnowledge().getVariables()) {
            assertTrue("Knowledge should be restored to base: " + name, !name.contains(":"));
        }
    }

    /**
     * The pooled test honors pooledTestMethod: the two methods must both dispatch and both recover the structure,
     * and the parameter must be registered with its allowed values.
     */
    @Test
    public void testPooledTestMethodParameter() throws Exception {
        assertEquals("fisher", new Parameters().getString(Params.POOLED_TEST_METHOD));
        assertEquals(java.util.List.of("fisher", "tippett"),
                edu.cmu.tetrad.util.ParamDescriptions.getInstance().get(Params.POOLED_TEST_METHOD).getAllowedValues());

        DataModelList list = fiveSmallDataSets(4);
        for (String method : new String[]{"fisher", "tippett"}) {
            Parameters p = params();
            p.set(Params.POOLED_TEST_METHOD, method);
            Pc pc = new Pc(new FisherZ());
            Graph g = pc.search(list, p);
            assertTrue(method + ": expected good adjacency recall, got " + adjacencyRecall(TRUTH, g),
                    adjacencyRecall(TRUTH, g) > 0.7);
        }
    }

    @Test
    public void testSingletonListIsOrdinarySearch() throws Exception {
        DataModelList list = fiveSmallDataSets(3);
        DataModelList one = new DataModelList();
        one.add(list.getFirst());
        Parameters p = params();
        Boss boss = new Boss(new SemBicScore());
        assertEquals(boss.search(list.getFirst(), p), boss.search(one, p));
    }
}
