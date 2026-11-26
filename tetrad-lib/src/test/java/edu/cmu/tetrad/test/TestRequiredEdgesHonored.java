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
import edu.cmu.tetrad.search.Grasp;
import edu.cmu.tetrad.search.PermutationSearch;
import edu.cmu.tetrad.search.PermutationSearchTopK;
import edu.cmu.tetrad.search.Sp;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.search.work_in_progress.GraspTol;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Required edges given as a chain, D before C before A before B, with the implied edges (D before A, and so on) not
 * listed, are all in the result of BOSS, BOSS Top-K, GRaSP and SP, and BOSS gives the same graph at one thread and
 * at four.
 * <p>
 * Before, each of these searches could start from an order that broke one of the required edges, because the repair
 * of the starting order made one pass only, and the edge could then be missing from the result: over sixty data sets
 * of ten variables, in 18 for BOSS and GRaSP.
 *
 * @author Claude
 */
public class TestRequiredEdgesHonored {

    private interface Search {
        Graph run(DataSet data, Knowledge knowledge) throws Exception;
    }

    private static SemBicScore score(DataSet data) {
        SemBicScore score = new SemBicScore(new CovarianceMatrix(data));
        score.setPenaltyDiscount(2);
        return score;
    }

    private static DataSet data(int numVariables, int rep) throws Exception {
        RandomUtil.getInstance().setSeed(3000L + rep);
        Graph truth = RandomGraph.randomGraph(numVariables, 0, 2 * numVariables, 100, 100, 100, false);
        DataSet data = new SemIm(new SemPm(truth)).simulateData(1000, false);
        List<Node> variables = new ArrayList<>(data.getVariables());
        Collections.shuffle(variables, new Random(50L + rep));
        return data.subsetColumns(variables);
    }

    /**
     * Four variables picked at random, required in a chain, with only the three links listed.
     */
    private static List<String[]> chain(DataSet data, int rep) {
        List<Node> pick = new ArrayList<>(data.getVariables());
        Collections.shuffle(pick, new Random(900L + rep));
        String a = pick.get(0).getName(), b = pick.get(1).getName(), c = pick.get(2).getName(),
                d = pick.get(3).getName();

        List<String[]> required = new ArrayList<>();
        required.add(new String[]{d, c});
        required.add(new String[]{c, a});
        required.add(new String[]{a, b});
        return required;
    }

    private static Knowledge knowledge(List<String[]> required) {
        Knowledge knowledge = new Knowledge();
        for (String[] edge : required) knowledge.setRequired(edge[0], edge[1]);
        return knowledge;
    }

    private static Graph boss(DataSet data, Knowledge knowledge, int numThreads) throws Exception {
        Boss boss = new Boss(score(data));
        boss.setNumThreads(numThreads);
        boss.setUseDataOrder(true);
        PermutationSearch search = new PermutationSearch(boss);
        search.setKnowledge(knowledge);
        return search.search(false);
    }

    private static void assertAllRequiredEdgesPresent(String name, Search search, int numVariables, int numDataSets)
            throws Exception {
        PrintStream out = System.out;
        System.setOut(new PrintStream(OutputStream.nullOutputStream()));

        try {
            for (int rep = 0; rep < numDataSets; rep++) {
                DataSet data = data(numVariables, rep);
                List<String[]> required = chain(data, rep);
                Graph graph = search.run(data, knowledge(required));

                for (String[] edge : required) {
                    assertTrue(name + ", data set " + rep + ": the required edge " + edge[0] + " --> " + edge[1]
                               + " is not in the result.",
                            graph.isParentOf(graph.getNode(edge[0]), graph.getNode(edge[1])));
                }
            }
        } finally {
            System.setOut(out);
        }
    }

    @Test
    public void testBoss() throws Exception {
        assertAllRequiredEdgesPresent("BOSS, one thread", (data, knowledge) -> boss(data, knowledge, 1), 10, 30);
        assertAllRequiredEdgesPresent("BOSS, four threads", (data, knowledge) -> boss(data, knowledge, 4), 10, 30);
    }

    @Test
    public void testBossGivesTheSameGraphWhateverTheNumberOfThreads() throws Exception {
        for (int rep = 0; rep < 30; rep++) {
            DataSet data = data(10, rep);
            Knowledge knowledge = knowledge(chain(data, rep));

            assertEquals("Data set " + rep + ": the graph differs with the number of threads.",
                    boss(data, knowledge, 1), boss(data, knowledge, 4));
        }
    }

    @Test
    public void testBossTopK() throws Exception {
        assertAllRequiredEdgesPresent("BOSS Top-K", (data, knowledge) -> {
            BossTopK boss = new BossTopK(score(data));
            boss.setUseDataOrder(true);
            PermutationSearchTopK search = new PermutationSearchTopK(boss, 5, 0.0);
            search.setKnowledge(knowledge);
            return search.search(false);
        }, 10, 30);
    }

    /**
     * With BES the order is remade from the graph BES leaves. The required edges are still in the result, at one
     * thread and at four, and with one start and with three.
     */
    @Test
    public void testBossWithBes() throws Exception {
        for (int numThreads : new int[]{1, 4}) {
            for (int numStarts : new int[]{1, 3}) {
                assertAllRequiredEdgesPresent("BOSS with BES, threads " + numThreads + ", starts " + numStarts,
                        (data, knowledge) -> {
                            Boss boss = new Boss(score(data));
                            boss.setNumThreads(numThreads);
                            boss.setUseDataOrder(true);
                            boss.setUseBes(true);
                            boss.setNumStarts(numStarts);
                            PermutationSearch search = new PermutationSearch(boss);
                            search.setKnowledge(knowledge);
                            return search.search(false);
                        }, 10, 15);
            }
        }
    }

    @Test
    public void testBossTopKWithBes() throws Exception {
        for (int numStarts : new int[]{1, 3}) {
            assertAllRequiredEdgesPresent("BOSS Top-K with BES, starts " + numStarts, (data, knowledge) -> {
                BossTopK boss = new BossTopK(score(data));
                boss.setUseDataOrder(true);
                boss.setUseBes(true);
                boss.setNumStarts(numStarts);
                PermutationSearchTopK search = new PermutationSearchTopK(boss, 5, 0.0);
                search.setKnowledge(knowledge);
                return search.search(false);
            }, 10, 15);
        }
    }

    /**
     * GraspTol checked its moves against forbidden edges only, and left a refused move in place. A required edge was
     * missing from its result in 60 of 60 searches with this chain, and in 40 of 60 with one required edge.
     */
    @Test
    public void testGraspTol() throws Exception {
        assertAllRequiredEdgesPresent("GraspTol", (data, knowledge) -> {
            GraspTol grasp = new GraspTol(score(data));
            grasp.setKnowledge(knowledge);
            grasp.setUseDataOrder(true);
            grasp.bestOrder(new ArrayList<>(data.getVariables()));
            return grasp.getGraph(false);
        }, 10, 30);
    }

    @Test
    public void testGrasp() throws Exception {
        assertAllRequiredEdgesPresent("GRaSP", (data, knowledge) -> {
            Grasp grasp = new Grasp(score(data));
            grasp.setKnowledge(knowledge);
            grasp.setUseDataOrder(true);
            grasp.bestOrder(new ArrayList<>(data.getVariables()));
            return grasp.getGraph(false);
        }, 10, 30);
    }

    @Test
    public void testSp() throws Exception {
        assertAllRequiredEdgesPresent("SP", (data, knowledge) -> {
            PermutationSearch search = new PermutationSearch(new Sp(score(data)));
            search.setKnowledge(knowledge);
            return search.search(false);
        }, 7, 20);
    }
}
