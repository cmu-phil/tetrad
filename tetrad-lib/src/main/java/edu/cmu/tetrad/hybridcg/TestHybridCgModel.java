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

/**
 * Tests for HybridCgModel.
 */
public class TestHybridCgModel {

    /**
     * Default constructor for the TestHybridCgModel class.
     * This constructor initializes an instance of the TestHybridCgModel class.
     */
    public TestHybridCgModel() {

    }

    /**
     * Generates a random HybridCgIm model.
     *
     * @param pm the HybridCgPm
     * @param rng the random number generator
     * @return the generated HybridCgIm model
     */
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
                    im.setIntercept(y, r, rng.nextGaussian());         // random mean offset
                    for (int j = 0; j < m; j++) {
                        im.setCoefficient(y, r, j, rng.nextGaussian() * 0.5); // random slope
                    }
                    im.setVariance(y, r, 1.0 + rng.nextDouble());      // variance > 0
                }
            }
        }
        return im;
    }

    /**
     * Tests the HybridCgModel with a random mixed node list.
     */
    @Test
    public void test() {

        // ----- Build a random mixed node list
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

        // ----- Tell the PM which variables are discrete and their categories
        Map<Node, Boolean> isDisc = new HashMap<>();
        Map<Node, List<String>> cats = new HashMap<>();

        for (Node v : nodeOrder) {
            boolean discrete = v instanceof DiscreteVariable;
            isDisc.put(v, discrete);
            if (discrete) cats.put(v, ((DiscreteVariable) v).getCategories());
        }

        HybridCgModel.HybridCgPm pm = new HybridCgModel.HybridCgPm(g, nodeOrder, isDisc, cats);

        // ----- Install simple fixed cutpoints for any discrete child with continuous parents (Path A needs this)
        for (Node child : nodeOrder) {
            int y = pm.indexOf(child);
            if (!pm.isDiscrete(y)) continue;
            int[] cParents = pm.getContinuousParents(y);
            if (cParents.length == 0) continue;

            Map<Node, double[]> cutpoints = new HashMap<>();
            for (int idx : cParents) {
                Node cp = pm.getNodes()[idx];
                // Example: three bins via two cutpoints
                cutpoints.put(cp, new double[]{-0.5, 0.5});
            }
            pm.setContParentCutpointsForDiscreteChild(child, cutpoints);
        }

        // ----- Simulate from a random IM (deterministic seeds)
        HybridCgModel.HybridCgIm im = randomIm(pm, new Random(12345));
        int n = 5000;
        HybridCgModel.HybridCgIm.Sample draw = im.sample(n, new Random(42));

        // Convert to a Tetrad DataSet
        DataSet simulated = im.toDataSet(draw);

        // ----- Path A: PM already has cutpoints (binPolicy="none") -> direct MLE
        HybridCgModel.HybridCgIm.HybridEstimator est = new HybridCgModel.HybridCgIm.HybridEstimator(1.0, false);
        HybridCgModel.HybridCgIm im2 = est.mle(pm, simulated);
        System.out.println("=== MLE with pre-set PM cutpoints (binPolicy=none) ===");
        System.out.println(im2);

        // ----- Path B: Have the estimator compute cutpoints from data (equal_frequency)
        Parameters p = new Parameters();
        p.set("hybridcg.alpha", 1.0);
        p.set("hybridcg.shareVariance", false);
        p.set("hybridcg.binPolicy", "equal_frequency"); // auto cutpoints
        p.set("hybridcg.bins", 3);

        HybridCgModel.HybridCgIm imAuto = HybridCgEstimator.estimate(pm, simulated, p);
        System.out.println("=== MLE with auto cutpoints (binPolicy=equal_frequency) ===");
        System.out.println(imAuto);

        // ----- Score and run a small search (as before)
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