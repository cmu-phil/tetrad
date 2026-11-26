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

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;

/**
 * BOSS Top-K with BES runs BES on the graph of the order its run arrived at.
 * <p>
 * Before, it ran BES on the graph of whichever order was last scored: the starting order on the first run, and the
 * previous run's result after that. The order was then rearranged to fit that graph, which undid part of the run.
 * With three starts on 25 variables, turning BES on lowered the score of the result in 14 of 30 data sets, by up to
 * 225 BIC points against BOSS with BES. BOSS itself had the same defect until it was given the same cure.
 *
 * @author Claude
 */
public class TestBossTopKBes {

    private static DataSet data(int numVariables, int rep) throws Exception {
        RandomUtil.getInstance().setSeed(9000L + rep);
        Graph truth = RandomGraph.randomGraph(numVariables, 0, 2 * numVariables, 100, 100, 100, false);
        DataSet data = new SemIm(new SemPm(truth)).simulateData(1000, false);
        List<Node> variables = new ArrayList<>(data.getVariables());
        Collections.shuffle(variables, new Random(50L + rep));
        return data.subsetColumns(variables);
    }

    private static SemBicScore score(DataSet data) {
        SemBicScore score = new SemBicScore(new CovarianceMatrix(data));
        score.setPenaltyDiscount(2);
        return score;
    }

    private static Graph topK(DataSet data, boolean useBes, int numStarts, int rep) throws Exception {
        RandomUtil.getInstance().setSeed(11L + rep);
        BossTopK boss = new BossTopK(score(data));
        boss.setUseDataOrder(true);
        boss.setUseBes(useBes);
        boss.setNumStarts(numStarts);
        return new PermutationSearchTopK(boss, 1, 0.0).search(false);
    }

    /**
     * From the data order with one start, keeping one model and not splitting, BOSS Top-K is BOSS, with BES as
     * without.
     */
    @Test
    public void testWithOneStartItGivesWhatBossWithBesGives() throws Exception {
        PrintStream out = System.out;
        System.setOut(new PrintStream(OutputStream.nullOutputStream()));

        try {
            for (int rep = 0; rep < 30; rep++) {
                DataSet data = data(12, rep);

                RandomUtil.getInstance().setSeed(11L + rep);
                Boss boss = new Boss(score(data));
                boss.setUseDataOrder(true);
                boss.setUseBes(true);

                assertEquals("Data set " + rep, new PermutationSearch(boss).search(false), topK(data, true, 1, rep));
            }
        } finally {
            System.setOut(out);
        }
    }

    /**
     * The same holds with several starts, with BES and without: the two make the same random starts. Before, BOSS
     * Top-K shuffled a fresh copy of the incoming order for each start, where BOSS shuffles the order its last start
     * ended at, and the two gave different graphs in about half of these data sets.
     */
    @Test
    public void testWithSeveralStartsItGivesWhatBossGives() throws Exception {
        PrintStream out = System.out;
        System.setOut(new PrintStream(OutputStream.nullOutputStream()));

        try {
            for (boolean useBes : new boolean[]{false, true}) {
                for (int rep = 0; rep < 15; rep++) {
                    DataSet data = data(12, rep);

                    RandomUtil.getInstance().setSeed(11L + rep);
                    Boss boss = new Boss(score(data));
                    boss.setUseDataOrder(true);
                    boss.setUseBes(useBes);
                    boss.setNumStarts(3);

                    assertEquals("Data set " + rep + ", BES " + useBes, new PermutationSearch(boss).search(false),
                            topK(data, useBes, 3, rep));
                }
            }
        } finally {
            System.setOut(out);
        }
    }
}
