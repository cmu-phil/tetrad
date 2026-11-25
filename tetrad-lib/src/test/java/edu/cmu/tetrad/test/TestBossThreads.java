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
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.search.Boss;
import edu.cmu.tetrad.search.BossTopK;
import edu.cmu.tetrad.search.PermutationSearch;
import edu.cmu.tetrad.search.PermutationSearchTopK;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;

/**
 * BOSS makes its moves with one method when it has one thread and with another when it has several. The two are
 * meant to make the same moves, so the search should give the same graph whatever the number of threads. From
 * 2026-3-9 to 2026-11-25 the several-thread method put every rightward move one place short of the best place, and
 * the graphs differed more often than not.
 *
 * @author Claude
 */
public class TestBossThreads {

    /**
     * Simulates linear Gaussian data and puts its columns in an arbitrary order, as real data are. (Left in the
     * order the simulation makes them, the columns are already in causal order, and BOSS has almost nothing to do.)
     */
    private static DataSet data(int numVariables, long seed) throws Exception {
        RandomUtil.getInstance().setSeed(seed);
        Graph truth = RandomGraph.randomGraph(numVariables, 0, 2 * numVariables, 100, 100, 100, false);
        DataSet data = new SemIm(new SemPm(truth)).simulateData(1000, false);
        List<Node> variables = new ArrayList<>(data.getVariables());
        Collections.shuffle(variables, new Random(seed));
        return data.subsetColumns(variables);
    }

    private static SemBicScore score(DataSet data) {
        SemBicScore score = new SemBicScore(new CovarianceMatrix(data));
        score.setPenaltyDiscount(2);
        return score;
    }

    private static Graph boss(DataSet data, int numThreads, Knowledge knowledge, double[] bic)
            throws Exception {
        Boss boss = new Boss(score(data));
        boss.setNumThreads(numThreads);
        boss.setUseDataOrder(true);
        PermutationSearch search = new PermutationSearch(boss);

        if (knowledge != null) {
            search.setKnowledge(knowledge);
        }

        Graph graph = search.search(true);
        List<Double> bics = boss.getBics();
        bic[0] = bics.isEmpty() ? Double.NaN : bics.get(bics.size() - 1);
        return graph;
    }

    @Test
    public void testSameGraphWhateverTheNumberOfThreads() throws Exception {
        for (int rep = 0; rep < 8; rep++) {
            DataSet data = data(30, 4100L + rep);
            double[] bic1 = new double[1];
            double[] bic4 = new double[1];
            Graph one = boss(data, 1, null, bic1);
            Graph four = boss(data, 4, null, bic4);
            assertEquals("Data set " + rep + ": the score differs with the number of threads.", bic1[0], bic4[0], 1e-6);
            assertEquals("Data set " + rep + ": the graph differs with the number of threads.", one, four);
        }
    }

    /**
     * The same with knowledge, which limits where a node may be moved to, and which the two methods check in
     * their own ways.
     */
    @Test
    public void testSameGraphWithKnowledge() throws Exception {
        for (int rep = 0; rep < 6; rep++) {
            DataSet data = data(20, 5200L + rep);
            List<Node> variables = data.getVariables();
            Knowledge knowledge = new Knowledge();

            for (int i = 0; i < variables.size(); i++) {
                knowledge.addToTier(i % 3, variables.get(i).getName());
            }

            knowledge.setRequired(variables.get(0).getName(), variables.get(3).getName());
            knowledge.setForbidden(variables.get(1).getName(), variables.get(2).getName());

            double[] bic1 = new double[1];
            double[] bic4 = new double[1];
            Graph one = boss(data, 1, knowledge, bic1);
            Graph four = boss(data, 4, knowledge, bic4);
            assertEquals("Data set " + rep + ": the graph differs with the number of threads.", one, four);
        }
    }

    /**
     * BOSS Top-K has the same two methods. With no splitting it should also give the same graph whatever the number
     * of threads.
     */
    @Test
    public void testTopKSameGraphWhateverTheNumberOfThreads() throws Exception {
        for (int rep = 0; rep < 6; rep++) {
            DataSet data = data(20, 6300L + rep);
            Graph[] graphs = new Graph[2];

            for (int i = 0; i < 2; i++) {
                BossTopK boss = new BossTopK(score(data));
                boss.setNumThreads(i == 0 ? 1 : 4);
                boss.setUseDataOrder(true);
                graphs[i] = new PermutationSearchTopK(boss, 5, 0.0).search(true);
            }

            assertEquals("Data set " + rep + ": the graph differs with the number of threads.", graphs[0], graphs[1]);
        }
    }
    /**
     * Runs BOSS Top-K with splitting and returns the models it kept, best first, each as its score and its
     * permutation. The scores are written out exactly, since the two thread settings should now agree to the last
     * bit.
     */
    private static List<String> topKModels(DataSet data, int numThreads, Knowledge knowledge, double delta,
                                           boolean byCpdag) throws Exception {
        BossTopK boss = new BossTopK(score(data));
        boss.setNumThreads(numThreads);
        boss.setUseDataOrder(true);
        PermutationSearchTopK search = new PermutationSearchTopK(boss, 5, delta);
        search.setMaxRuns(200);
        search.setDedupByCpdag(byCpdag);
        if (knowledge != null) {
            search.setKnowledge(knowledge);
        }
        search.search(true);

        List<String> models = new ArrayList<>();
        for (int i = 0; i < search.getNumModels(); i++) {
            models.add(Double.toHexString(search.getModelScore(i)) + " "
                       + java.util.Arrays.toString(search.getModelPermutation(i)));
        }
        return models;
    }

    /**
     * With splitting (a delta above zero), BOSS Top-K sets near-tied orderings aside and runs them later, best
     * first. Which orderings were set aside, and in what order they were run, used to depend on the number of
     * threads, for two reasons. The two move methods added up each slot's total in a different order, so the totals
     * differed in the last bit, and the split compares them exactly. And the one-thread method also set aside the
     * ordering it was moving away from. So the list of models kept differed with the number of threads.
     */
    @Test
    public void testTopKWithSplittingKeepsTheSameModelsWhateverTheNumberOfThreads() throws Exception {
        for (int numVariables : new int[]{12, 25}) {
            for (int rep = 0; rep < 6; rep++) {
                DataSet data = data(numVariables, 7400L + 31L * numVariables + rep);

                assertEquals(numVariables + " variables, data set " + rep
                             + ": the models kept differ with the number of threads.",
                        topKModels(data, 1, null, 2.0, false), topKModels(data, 4, null, 2.0, false));
            }
        }
    }

    /**
     * The same with models identified by their CPDAGs, and with a wider split.
     */
    @Test
    public void testTopKWithSplittingByCpdag() throws Exception {
        for (int rep = 0; rep < 6; rep++) {
            DataSet data = data(12, 8500L + rep);

            assertEquals("Data set " + rep + ": the models kept differ with the number of threads.",
                    topKModels(data, 1, null, 5.0, true), topKModels(data, 4, null, 5.0, true));
        }
    }

    /**
     * The same with required edges. A required edge rules out some of the places a node may be moved to. The
     * one-thread method never finished the totals for those places, and the several-thread method did and could set
     * such an ordering aside; neither looks at them now.
     */
    @Test
    public void testTopKWithSplittingAndRequiredEdges() throws Exception {
        for (int rep = 0; rep < 8; rep++) {
            DataSet data = data(16, 9600L + rep);
            List<Node> variables = data.getVariables();
            Knowledge knowledge = new Knowledge();
            knowledge.setRequired(variables.get(0).getName(), variables.get(3).getName());
            knowledge.setRequired(variables.get(5).getName(), variables.get(9).getName());
            knowledge.setRequired(variables.get(9).getName(), variables.get(12).getName());

            assertEquals("Data set " + rep + ": the models kept differ with the number of threads.",
                    topKModels(data, 1, knowledge, 2.0, false), topKModels(data, 4, knowledge, 2.0, false));
        }
    }
}
