package edu.cmu.tetrad.test;

import edu.cmu.tetrad.algcomparison.algorithm.multi.Images;
import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Pins the common basis-column decision for the BF-BIC score under multi-data-set
 * (common-model) use: with adaptive basis selection enabled, the wrapper's
 * {@code getScores(List, Parameters)} must give every data set the SAME embedding - the
 * union of the per-data-set adaptive decisions - so that a common-model algorithm such as
 * IMaGES sums scores of the identical parameterization. Previously each data set pruned its
 * own columns from its own correlation matrix, so the summed score compared models whose
 * effective embeddings differed across data sets.
 */
public class TestBfCommonEmbeddingAcrossDatasets {

    /**
     * Data set in which Y depends on X cubically (higher-order basis columns of X are
     * informative) - the adaptive screen should keep them.
     */
    private static DataSet cubicData(long seed, int n) {
        Random rng = new Random(seed);
        double[][] d = new double[n][2];
        for (int i = 0; i < n; i++) {
            double x = rng.nextGaussian();
            d[i][0] = x;
            d[i][1] = x * x * x + 0.3 * rng.nextGaussian();
        }
        return mk(d);
    }

    /**
     * Data set in which X and Y are independent Gaussians - higher-order columns are
     * uninformative and the adaptive screen should prune them.
     */
    private static DataSet noiseData(long seed, int n) {
        Random rng = new Random(seed);
        double[][] d = new double[n][2];
        for (int i = 0; i < n; i++) {
            d[i][0] = rng.nextGaussian();
            d[i][1] = rng.nextGaussian();
        }
        return mk(d);
    }

    private static DataSet mk(double[][] d) {
        List<Node> vars = new ArrayList<>();
        vars.add(new ContinuousVariable("X"));
        vars.add(new ContinuousVariable("Y"));
        return new BoxDataSet(new DoubleDataBox(d), vars);
    }

    private static Set<Integer> allKept(Map<Integer, List<Integer>> embedding) {
        Set<Integer> kept = new HashSet<>();
        for (List<Integer> cols : embedding.values()) kept.addAll(cols);
        return kept;
    }

    /**
     * Precondition for the fix mattering at all: the two data sets' individual adaptive
     * decisions differ (the cubic data keeps higher-order columns the noise data prunes).
     */
    @Test
    public void testPerDatasetDecisionsDiffer() {
        int trunc = 3;
        Map<Integer, List<Integer>> a =
                edu.cmu.tetrad.search.score.BasisFunctionBicScore.adaptivePrunedEmbedding(cubicData(1L, 500), trunc);
        Map<Integer, List<Integer>> b =
                edu.cmu.tetrad.search.score.BasisFunctionBicScore.adaptivePrunedEmbedding(noiseData(2L, 500), trunc);
        assertFalse("Expected the cubic and noise data sets to make different basis decisions; "
                + "otherwise this test exercises nothing.", allKept(a).equals(allKept(b)));
        assertTrue("The cubic data should keep at least as many columns.",
                allKept(a).size() > allKept(b).size());
    }

    /**
     * The wrapper's joint construction must give both data sets the identical embedding,
     * equal to the union of the individual decisions.
     */
    @Test
    public void testCommonEmbeddingIsUnionAndShared() {
        int trunc = 3;
        DataSet a = cubicData(1L, 500);
        DataSet b = noiseData(2L, 500);

        Parameters parameters = new Parameters();
        parameters.set(Params.TRUNCATION_LIMIT, trunc);
        parameters.set(Params.ADAPTIVE_BASIS_SELECTION, true);

        edu.cmu.tetrad.algcomparison.score.BasisFunctionBicScore wrapper =
                new edu.cmu.tetrad.algcomparison.score.BasisFunctionBicScore();
        List<DataModel> dataModels = new ArrayList<>();
        dataModels.add(a);
        dataModels.add(b);
        List<Score> scores = wrapper.getScores(dataModels, parameters);
        assertEquals(2, scores.size());

        Map<Integer, List<Integer>> ea =
                ((edu.cmu.tetrad.search.score.BasisFunctionBicScore) scores.get(0)).getEmbedding();
        Map<Integer, List<Integer>> eb =
                ((edu.cmu.tetrad.search.score.BasisFunctionBicScore) scores.get(1)).getEmbedding();

        assertEquals("Both data sets must score against the identical embedding.", ea, eb);

        Set<Integer> union = new HashSet<>();
        union.addAll(allKept(edu.cmu.tetrad.search.score.BasisFunctionBicScore.adaptivePrunedEmbedding(a, trunc)));
        union.addAll(allKept(edu.cmu.tetrad.search.score.BasisFunctionBicScore.adaptivePrunedEmbedding(b, trunc)));
        assertEquals("The common embedding must be the union of the per-data-set decisions.",
                union, allKept(ea));
    }

    /**
     * With adaptive selection off, the joint construction must reduce to the per-data-set
     * path (full embeddings, identical anyway).
     */
    @Test
    public void testAdaptiveOffUnchanged() {
        Parameters parameters = new Parameters();
        parameters.set(Params.TRUNCATION_LIMIT, 3);
        parameters.set(Params.ADAPTIVE_BASIS_SELECTION, false);

        edu.cmu.tetrad.algcomparison.score.BasisFunctionBicScore wrapper =
                new edu.cmu.tetrad.algcomparison.score.BasisFunctionBicScore();
        List<DataModel> dataModels = new ArrayList<>();
        dataModels.add(cubicData(1L, 500));
        dataModels.add(noiseData(2L, 500));
        List<Score> scores = wrapper.getScores(dataModels, parameters);

        Map<Integer, List<Integer>> ea =
                ((edu.cmu.tetrad.search.score.BasisFunctionBicScore) scores.get(0)).getEmbedding();
        Map<Integer, List<Integer>> eb =
                ((edu.cmu.tetrad.search.score.BasisFunctionBicScore) scores.get(1)).getEmbedding();
        assertEquals(ea, eb);
    }

    /**
     * End-to-end: IMaGES with the BF wrapper and adaptive selection runs through the joint
     * construction and returns a graph.
     */
    @Test
    public void testImagesEndToEnd() throws InterruptedException {
        Parameters parameters = new Parameters();
        parameters.set(Params.TRUNCATION_LIMIT, 3);
        parameters.set(Params.ADAPTIVE_BASIS_SELECTION, true);
        parameters.set(Params.PENALTY_DISCOUNT, 1.0);
        parameters.set(Params.SEED, 42L);
        parameters.set(Params.NUMBER_RESAMPLING, 0);
        parameters.set(Params.TIME_LAG, 0);

        Images images = new Images();
        images.setScoreWrapper(new edu.cmu.tetrad.algcomparison.score.BasisFunctionBicScore());

        List<DataModel> dataModels = new ArrayList<>();
        dataModels.add(cubicData(1L, 500));
        dataModels.add(noiseData(2L, 500));

        Graph graph = images.search(dataModels, parameters);
        assertNotNull(graph);
        assertEquals(2, graph.getNumNodes());
    }
}
