package edu.cmu.tetrad.hybridcg;

import edu.cmu.tetrad.algcomparison.score.ConditionalGaussianBicScore;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.search.Boss;
import edu.cmu.tetrad.search.PermutationSearch;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import java.util.*;

public class TestHybridCgModelModel {

    public static HybridCgModel.HybridCgIm randomIm(HybridCgModel.HybridCgPm pm, Random rng) {
        HybridCgModel.HybridCgIm im = new HybridCgModel.HybridCgIm(pm);
        int n = pm.getNodes().length;

        for (int y = 0; y < n; y++) {
            int rows = pm.getNumRows(y);

            if (pm.isDiscrete(y)) {
                int K = pm.getCardinality(y);
                for (int r = 0; r < rows; r++) {
                    double[] probs = new double[K];
                    double sum = 0.0;
                    for (int k = 0; k < K; k++) {
                        probs[k] = rng.nextDouble();
                        sum += probs[k];
                    }
                    for (int k = 0; k < K; k++) {
                        im.setProbability(y, r, k, probs[k] / sum);
                    }
                }
            } else {
                int m = pm.getContinuousParents(y).length;
                for (int r = 0; r < rows; r++) {
                    im.setIntercept(y, r, rng.nextGaussian()); // random mean offset
                    for (int j = 0; j < m; j++) {
                        im.setCoefficient(y, r, j, rng.nextGaussian() * 0.5); // random slope
                    }
                    im.setVariance(y, r, 1.0 + rng.nextDouble()); // variance > 0
                }
            }
        }
        return im;
    }

    @Test
    public void test() {

        List<Node> nodes = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            if (RandomUtil.getInstance().nextDouble() < 0.5) {
                nodes.add(new ContinuousVariable("X" + (i + 1)));
            } else {
                nodes.add(new DiscreteVariable("Y" + i, 3));
            }
        }

        Graph g = RandomGraph.randomGraph(nodes, 0, 10, 100, 100, 100, false);
        List<Node> nodeOrder = g.getNodes(); // or any fixed order you like

        System.out.println("Nodes =  " + nodeOrder);
        System.out.println("Graph = " + g);

        // Tell the PM which variables are discrete and their categories (order matters!)
        Map<Node, Boolean> isDisc = new HashMap<>();
        Map<Node, List<String>> cats = new HashMap<>();

        for (Node v : nodeOrder) {
            boolean discrete = v instanceof DiscreteVariable;
            isDisc.put(v, discrete);
            if (discrete) cats.put(v, ((DiscreteVariable) v).getCategories());
        }

        HybridCgModel.HybridCgPm pm = new HybridCgModel.HybridCgPm(g, nodeOrder, isDisc, cats);

        for (Node child : nodeOrder) {
            int y = pm.indexOf(child);
            if (!pm.isDiscrete(y)) continue;
            int[] cParents = pm.getContinuousParents(y);
            if (cParents.length == 0) continue;

            Map<Node, double[]> cutpoints = new HashMap<>();
            for (int idx : cParents) {
                Node cp = pm.getNodes()[idx];
                // choose your policy:
                //  - fixed edges (domain knowledge)
                //  - equal-width or equal-frequency from data
                //  - your ConditionalGaussianLikelihood helper (if it exposes binning)
                cutpoints.put(cp, new double[]{-0.5, 0.5}); // example: 3 bins
            }
            pm.setContParentCutpointsForDiscreteChild(child, cutpoints);
        }

        for (Node child : nodeOrder) {
            int y = pm.indexOf(child);
            if (!pm.isDiscrete(y)) continue;
            int[] cParents = pm.getContinuousParents(y);
            if (cParents.length == 0) continue;

            Map<Node, double[]> cutpoints = new HashMap<>();
            for (int idx : cParents) {
                Node cp = pm.getNodes()[idx];
                // choose your policy:
                //  - fixed edges (domain knowledge)
                //  - equal-width or equal-frequency from data
                //  - your ConditionalGaussianLikelihood helper (if it exposes binning)
                cutpoints.put(cp, new double[]{-0.5, 0.5}); // example: 3 bins
            }
            pm.setContParentCutpointsForDiscreteChild(child, cutpoints);
        }

        HybridCgModel.HybridCgIm im = randomIm(pm, new Random());

        int n = 5000;
        HybridCgModel.HybridCgIm.Sample draw = im.sample(n, new Random(42));

        // Convert to a Tetrad DataSet (choose any node order you want in the output)
        List<Node> outOrder = Arrays.asList(pm.getNodes()); // same order as PM
        DataSet simulated = im.toDataSet(draw, outOrder);

//        System.out.println(simulated);

        // Dirichlet alpha = 1.0 for CPT smoothing; shareVarianceAcrossRows = false (change if strata are thin)
        HybridCgModel.HybridCgIm.HybridEstimator est = new HybridCgModel.HybridCgIm.HybridEstimator(1.0, false);
        HybridCgModel.HybridCgIm im2 = est.mle(pm, simulated);

        System.out.println(im2);

        ConditionalGaussianBicScore score = new ConditionalGaussianBicScore();
        edu.cmu.tetrad.search.score.Score _score = score.getScore(simulated, new Parameters());

        try {
            Graph cpdag = new PermutationSearch(new Boss(_score)).search();

            System.out.println("True CPDAG = " + GraphTransforms.dagToCpdag(g));
            System.out.println("BOSS result = " + cpdag);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

    }
}
