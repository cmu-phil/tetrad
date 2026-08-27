///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2026 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
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

import edu.cmu.tetrad.bayes.BayesPm;
import edu.cmu.tetrad.bayes.MlBayesIm;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.search.Boss;
import edu.cmu.tetrad.search.PermutationSearch;
import edu.cmu.tetrad.search.score.BDeuScore;
import edu.cmu.tetrad.search.score.BasisFunctionBicScore;
import edu.cmu.tetrad.search.score.DegenerateGaussianBgeScore;
import edu.cmu.tetrad.search.score.DegenerateGaussianScore;
import edu.cmu.tetrad.search.score.DiscreteBicScore;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.util.RandomUtil;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Hand-run harness: BOSS adjacency precision/recall on purely discrete data for BDeu, discrete BIC, DG-BIC, DG-BGe at
 * several alpha_mu, and BF-BIC, with recall split by whether the true child of each edge has one parent or more than
 * one. If the indicator-based scores lose mostly on multi-parent children, the deficit is the additive (main-effects)
 * parent model rather than the penalty; if they lose uniformly, it is the penalty.
 * <p>
 * Usage: {@code java ... edu.cmu.tetrad.test.DiscreteScorePowerHarness [numVars] [avgDegree] [reps] [n1,n2,...]}
 */
public class DiscreteScorePowerHarness {

    public static void main(String[] args) throws Exception {
        int numVars = args.length > 0 ? Integer.parseInt(args[0]) : 15;
        int avgDegree = args.length > 1 ? Integer.parseInt(args[1]) : 3;
        int reps = args.length > 2 ? Integer.parseInt(args[2]) : 10;
        String[] ns = args.length > 3 ? args[3].split(",") : new String[]{"200", "500", "1000", "2000"};

        Map<String, Function<DataSet, Score>> scores = new LinkedHashMap<>();
        scores.put("BDeu ess=1", d -> {
            BDeuScore s = new BDeuScore(d);
            s.setPriorEquivalentSampleSize(1);
            return s;
        });
        scores.put("BDeu ess=10", d -> {
            BDeuScore s = new BDeuScore(d);
            s.setPriorEquivalentSampleSize(10);
            return s;
        });
        scores.put("Disc-BIC pd=1", d -> {
            DiscreteBicScore s = new DiscreteBicScore(d);
            s.setPenaltyDiscount(1);
            return s;
        });
        scores.put("DG-BIC pd=2", d -> {
            DegenerateGaussianScore s = new DegenerateGaussianScore(d, true, 0.0);
            s.setPenaltyDiscount(2);
            return s;
        });
        scores.put("DG-BIC pd=1", d -> {
            DegenerateGaussianScore s = new DegenerateGaussianScore(d, true, 0.0);
            s.setPenaltyDiscount(1);
            return s;
        });
        scores.put("DG-BGe mu=0.1", d -> {
            DegenerateGaussianBgeScore s = new DegenerateGaussianBgeScore(d);
            s.setAlphaMu(0.1);
            return s;
        });
        scores.put("DG-BGe mu=1", d -> new DegenerateGaussianBgeScore(d));
        scores.put("DG-BGe mu=10", d -> {
            DegenerateGaussianBgeScore s = new DegenerateGaussianBgeScore(d);
            s.setAlphaMu(10);
            return s;
        });
        scores.put("BF-BIC pd=2", d -> {
            BasisFunctionBicScore s = new BasisFunctionBicScore(d, 3, 0.0);
            s.setPenaltyDiscount(2);
            return s;
        });

        System.out.printf("%d vars, avg degree %d, %d reps; recall split: R1 = edges into single-parent children, "
                + "R2+ = edges into children with >= 2 parents%n%n", numVars, avgDegree, reps);
        System.out.printf("%-6s %-16s %8s %8s %8s %8s %8s%n", "N", "score", "prec", "recall", "R1", "R2+", "#edges");

        for (String nStr : ns) {
            int n = Integer.parseInt(nStr);
            Map<String, double[]> acc = new LinkedHashMap<>();
            for (String k : scores.keySet()) acc.put(k, new double[6]); // tp fp fn tp1 tot1 tp2 (tot2 = fn-based)
            Map<String, Double> tot1 = new LinkedHashMap<>(), tot2 = new LinkedHashMap<>();
            for (String k : scores.keySet()) {
                tot1.put(k, 0.0);
                tot2.put(k, 0.0);
            }

            for (int rep = 0; rep < reps; rep++) {
                RandomUtil.getInstance().setSeed(1000L * n + rep);
                Graph dag = RandomGraph.randomGraph(numVars, 0, numVars * avgDegree / 2, 100, 100, 100, false);
                BayesPm pm = new BayesPm(dag, 2, 4);
                MlBayesIm im = new MlBayesIm(pm, MlBayesIm.InitializationMethod.RANDOM);
                DataSet data = im.simulateData(n, false);
                Graph truth = GraphTransforms.dagToCpdag(dag);

                for (Map.Entry<String, Function<DataSet, Score>> e : scores.entrySet()) {
                    Score score = e.getValue().apply(data);
                    Boss boss = new Boss(score);
                    boss.setNumStarts(1);
                    boss.setUseBes(true);
                    Graph est = new PermutationSearch(boss).search();

                    double[] a = acc.get(e.getKey());
                    for (Edge ed : est.getEdges()) {
                        boolean hit = truth.isAdjacentTo(truth.getNode(ed.getNode1().getName()),
                                truth.getNode(ed.getNode2().getName()));
                        if (hit) a[0]++;
                        else a[1]++;
                    }
                    for (Edge ed : dag.getEdges()) {
                        Node x = ed.getNode1(), y = ed.getNode2();
                        if (!dag.isParentOf(x, y)) {
                            Node t = x;
                            x = y;
                            y = t;
                        }
                        boolean multi = dag.getParents(y).size() >= 2;
                        boolean found = est.isAdjacentTo(est.getNode(x.getName()), est.getNode(y.getName()));
                        if (multi) {
                            tot2.put(e.getKey(), tot2.get(e.getKey()) + 1);
                            if (found) a[5]++;
                        } else {
                            tot1.put(e.getKey(), tot1.get(e.getKey()) + 1);
                            if (found) a[3]++;
                        }
                        if (!found) a[2]++;
                    }
                }
            }

            for (String k : scores.keySet()) {
                double[] a = acc.get(k);
                double prec = a[0] / Math.max(1, a[0] + a[1]);
                double rec = a[0] / Math.max(1, a[0] + a[2]);
                double r1 = a[3] / Math.max(1, tot1.get(k));
                double r2 = a[5] / Math.max(1, tot2.get(k));
                System.out.printf("%-6d %-16s %8.3f %8.3f %8.3f %8.3f %8.1f%n", n, k, prec, rec, r1, r2,
                        (a[0] + a[1]) / reps);
            }
            System.out.println();
        }
    }
}
