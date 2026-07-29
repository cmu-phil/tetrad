package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.search.Boss;
import edu.cmu.tetrad.search.PermutationSearch;
import edu.cmu.tetrad.search.score.ImagesScore;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests IMaGES-BOSS: BOSS run over an ImagesScore aggregating SemBicScores from
 * multiple datasets generated from a common DAG with dataset-specific SEM
 * parameters (the joint model the aggregate score corresponds to).
 * <p>
 * The main test uses 10 datasets with 50 nodes each and sample sizes varying by
 * a factor of 10, and checks structure recovery against the CPDAG of the true
 * DAG under both weighting schemes. A second test checks the documented
 * invariant that the two schemes agree exactly when all sample sizes are equal.
 *
 * @author josephramsey
 */
public class TestImagesBoss {

    private static final int NUM_NODES = 50;
    private static final int NUM_EDGES = 60;
    private static final int NUM_DATASETS = 10;

    /**
     * Simulates NUM_DATASETS datasets from a single random DAG, one SemIm
     * (fresh parameters) per dataset, and rewraps each dataset over a single
     * shared variable list so that the component scores have object-identical
     * variables, as ImagesScore requires.
     */
    private static List<DataSet> simulate(Graph dag, int[] sampleSizes) {
        SemPm pm = new SemPm(dag);
        List<DataSet> dataSets = new ArrayList<>();

        for (int sampleSize : sampleSizes) {
            SemIm im = new SemIm(pm);
            try {
                dataSets.add(im.simulateData(sampleSize, false));
            } catch (ParseException e) {
                throw new RuntimeException(e);
            }
        }

        // Rewrap all datasets over the variable list of the first, so that the
        // variables are object-identical across datasets.
        List<Node> vars = dataSets.get(0).getVariables();
        List<DataSet> rewrapped = new ArrayList<>();

        for (DataSet d : dataSets) {
            rewrapped.add(new BoxDataSet(new DoubleDataBox(d.getDoubleData().toArray()), vars));
        }

        return rewrapped;
    }

    private static Graph runImagesBoss(List<DataSet> dataSets, ImagesScore.WeightingScheme scheme)
            throws InterruptedException {
        List<Score> scores = new ArrayList<>();

        for (DataSet d : dataSets) {
            SemBicScore score = new SemBicScore(d, true);
            score.setPenaltyDiscount(2);
            scores.add(score);
        }

        ImagesScore imagesScore = new ImagesScore(scores, scheme);

        Boss boss = new Boss(imagesScore);
        boss.setUseBes(true);
        boss.setNumStarts(1);

        PermutationSearch search = new PermutationSearch(boss);
        return search.search();
    }

    /**
     * Returns {adjacency precision, adjacency recall, orientation accuracy},
     * where orientation accuracy is the fraction of commonly adjacent pairs
     * whose edge (including endpoints) is identical in the two graphs. Assumes
     * the graphs are over the same nodes (use GraphUtils.replaceNodes first).
     */
    private static double[] compare(Graph trueCpdag, Graph est) {
        int adjTp = 0;
        int adjFp = 0;
        int adjFn = 0;
        int oriCorrect = 0;
        int oriTotal = 0;

        for (Edge e : est.getEdges()) {
            if (trueCpdag.isAdjacentTo(e.getNode1(), e.getNode2())) {
                adjTp++;
                oriTotal++;

                Edge trueEdge = trueCpdag.getEdge(e.getNode1(), e.getNode2());

                if (trueEdge.equals(e)) {
                    oriCorrect++;
                }
            } else {
                adjFp++;
            }
        }

        for (Edge e : trueCpdag.getEdges()) {
            if (!est.isAdjacentTo(e.getNode1(), e.getNode2())) {
                adjFn++;
            }
        }

        double ap = adjTp + adjFp == 0 ? 1.0 : adjTp / (double) (adjTp + adjFp);
        double ar = adjTp + adjFn == 0 ? 1.0 : adjTp / (double) (adjTp + adjFn);
        double oa = oriTotal == 0 ? 1.0 : oriCorrect / (double) oriTotal;

        return new double[]{ap, ar, oa};
    }

    /**
     * 10 datasets, 50 nodes, sample sizes from 100 to 1000. Both weighting
     * schemes should recover the common structure well; the point of varying
     * the sample sizes by a factor of 10 is to exercise the rescaling path of
     * DATASET_WEIGHTED, under which the largest dataset does not dictate the
     * result.
     */
    @Test
    public void testImagesBossVaryingSampleSizes() throws InterruptedException {
        RandomUtil.getInstance().setSeed(38482838L);

        List<Node> nodes = new ArrayList<>();

        for (int i = 1; i <= NUM_NODES; i++) {
            nodes.add(new ContinuousVariable("X" + i));
        }

        Graph dag = RandomGraph.randomGraph(nodes, 0, NUM_EDGES, 100, 100, 100, false);
        Graph trueCpdag = GraphTransforms.dagToCpdag(dag);

        int[] sampleSizes = {100, 200, 300, 400, 500, 600, 700, 800, 900, 1000};
        assertEquals(NUM_DATASETS, sampleSizes.length);

        List<DataSet> dataSets = simulate(dag, sampleSizes);

        for (ImagesScore.WeightingScheme scheme : ImagesScore.WeightingScheme.values()) {
            Graph est = runImagesBoss(dataSets, scheme);
            est = GraphUtils.replaceNodes(est, trueCpdag.getNodes());

            double[] stats = compare(trueCpdag, est);

            System.out.println("Scheme = " + scheme
                    + ": AP = " + stats[0] + ", AR = " + stats[1] + ", OA = " + stats[2]);

            assertTrue("Adjacency precision too low for " + scheme + ": " + stats[0],
                    stats[0] >= 0.9);
            assertTrue("Adjacency recall too low for " + scheme + ": " + stats[1],
                    stats[1] >= 0.9);
            assertTrue("Orientation accuracy too low for " + scheme + ": " + stats[2],
                    stats[2] >= 0.8);
        }
    }

    /**
     * Documented invariant: when all sample sizes are equal, SAMPLE_UNIFORM and
     * DATASET_WEIGHTED (with the default uniform schedule) apply identical
     * effective weights, so the two searches maximize the same objective and
     * (with a single start) return the same graph.
     */
    @Test
    public void testSchemesAgreeForEqualSampleSizes() throws InterruptedException {
        RandomUtil.getInstance().setSeed(48291023L);

        List<Node> nodes = new ArrayList<>();

        for (int i = 1; i <= NUM_NODES; i++) {
            nodes.add(new ContinuousVariable("X" + i));
        }

        Graph dag = RandomGraph.randomGraph(nodes, 0, NUM_EDGES, 100, 100, 100, false);

        int[] sampleSizes = new int[NUM_DATASETS];

        for (int k = 0; k < NUM_DATASETS; k++) {
            sampleSizes[k] = 500;
        }

        List<DataSet> dataSets = simulate(dag, sampleSizes);

        Graph g1 = runImagesBoss(dataSets, ImagesScore.WeightingScheme.SAMPLE_UNIFORM);
        Graph g2 = runImagesBoss(dataSets, ImagesScore.WeightingScheme.DATASET_WEIGHTED);
        g2 = GraphUtils.replaceNodes(g2, g1.getNodes());

        assertEquals("Weighting schemes should agree when all sample sizes are equal", g1, g2);
    }
}