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

import edu.cmu.tetrad.algcomparison.algorithm.oracle.cpdag.BossTopK;
import edu.cmu.tetrad.algcomparison.score.SemBicScore;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The BOSS Top-K algorithm says how many runs it made and whether its cap on runs cut it short. Before, a search that
 * reached the cap gave its models with nothing to say that they were the best of only part of what was set aside.
 *
 * @author Claude
 */
public class TestBossTopKRuns {

    private static final String CUT_SHORT = "The cap on runs (maxRuns) stopped the search";

    private static DataSet data(long seed) throws Exception {
        RandomUtil.getInstance().setSeed(seed);
        Graph truth = RandomGraph.randomGraph(12, 0, 24, 100, 100, 100, false);
        DataSet data = new SemIm(new SemPm(truth)).simulateData(1000, false);
        List<Node> variables = new ArrayList<>(data.getVariables());
        Collections.shuffle(variables, new Random(50L));
        return data.subsetColumns(variables);
    }

    /**
     * Splitting with ties left out, which runs out of placements by itself after a modest number of runs.
     */
    private static Parameters split(int maxRuns) {
        Parameters parameters = new Parameters();
        parameters.set(Params.TOP_K, 5);
        parameters.set(Params.SPLIT_DELTA, 2.0);
        parameters.set(Params.SPLIT_DEFER_TIES, false);
        parameters.set(Params.MAX_RUNS, maxRuns);
        return parameters;
    }

    /**
     * Runs the search and returns what it printed.
     */
    private static String run(BossTopK algorithm, DataSet data, Parameters parameters) throws Exception {
        PrintStream out = System.out;
        ByteArrayOutputStream printed = new ByteArrayOutputStream();
        System.setOut(new PrintStream(printed));

        try {
            algorithm.search(data, parameters);
        } finally {
            System.setOut(out);
        }

        return printed.toString();
    }

    @Test
    public void testOneStartWithoutSplittingIsOneRun() throws Exception {
        BossTopK algorithm = new BossTopK(new SemBicScore());

        assertEquals(0, algorithm.getNumRuns());
        assertFalse(algorithm.isStoppedByMaxRuns());

        String printed = run(algorithm, data(9100L), new Parameters());

        assertEquals(1, algorithm.getNumRuns());
        assertFalse(algorithm.isStoppedByMaxRuns());
        assertTrue(printed.contains("runs made = 1"));
        assertFalse(printed.contains(CUT_SHORT));
    }

    /**
     * Every start is run, whatever the cap, and with no splitting the cap leaves nothing undone.
     */
    @Test
    public void testStartsAreNotCutShortByTheCap() throws Exception {
        Parameters parameters = new Parameters();
        parameters.set(Params.NUM_STARTS, 5);
        parameters.set(Params.MAX_RUNS, 2);

        BossTopK algorithm = new BossTopK(new SemBicScore());
        run(algorithm, data(9100L), parameters);

        assertEquals(5, algorithm.getNumRuns());
        assertFalse(algorithm.isStoppedByMaxRuns());
    }

    /**
     * A search that finishes below its cap was not cut short. Given exactly the runs it needs, it is not cut short
     * either, and gives the same models. Given one fewer, it is.
     */
    @Test
    public void testCutShortOnlyWhenSomethingSetAsideIsLeft() throws Exception {
        DataSet data = data(9100L);

        BossTopK free = new BossTopK(new SemBicScore());
        run(free, data, split(100000));
        int needed = free.getNumRuns();

        assertTrue("The search was expected to split; it made " + needed + " run(s).", needed > 2);
        assertFalse(free.isStoppedByMaxRuns());

        BossTopK exact = new BossTopK(new SemBicScore());
        String printedExact = run(exact, data, split(needed));

        assertEquals(needed, exact.getNumRuns());
        assertFalse(exact.isStoppedByMaxRuns());
        assertFalse(printedExact.contains(CUT_SHORT));
        assertEquals(free.getTopScores(), exact.getTopScores());

        BossTopK short1 = new BossTopK(new SemBicScore());
        String printedShort = run(short1, data, split(needed - 1));

        assertEquals(needed - 1, short1.getNumRuns());
        assertTrue(short1.isStoppedByMaxRuns());
        assertTrue(printedShort.contains("runs made = " + (needed - 1)));
        assertTrue(printedShort.contains(CUT_SHORT));

        // With a cap of one run nothing is ever set aside, and the search still says that something was left.
        BossTopK one = new BossTopK(new SemBicScore());
        run(one, data, split(1));

        assertEquals(1, one.getNumRuns());
        assertTrue(one.isStoppedByMaxRuns());
    }

    /**
     * With bootstrapping there are no models to give, and no counts either.
     */
    @Test
    public void testNothingIsReportedWithBootstrapping() throws Exception {
        Parameters parameters = split(3);
        parameters.set(Params.NUMBER_RESAMPLING, 3);

        BossTopK algorithm = new BossTopK(new SemBicScore());
        run(algorithm, data(9100L), parameters);

        assertTrue(algorithm.getTopGraphs().isEmpty());
        assertEquals(0, algorithm.getNumRuns());
        assertFalse(algorithm.isStoppedByMaxRuns());
    }

    /**
     * With every variable in a tier the tiers are searched one after another, and the runs are those of all of them.
     */
    @Test
    public void testRunsAreSummedOverTiers() throws Exception {
        DataSet data = data(9100L);
        Knowledge knowledge = new Knowledge();

        for (int i = 0; i < data.getNumColumns(); i++) {
            knowledge.addToTier(i < 6 ? 0 : 1, data.getVariable(i).getName());
        }

        BossTopK algorithm = new BossTopK(new SemBicScore());
        algorithm.setKnowledge(knowledge);
        run(algorithm, data, new Parameters());

        assertEquals(2, algorithm.getNumRuns());
        assertFalse(algorithm.isStoppedByMaxRuns());
    }
}
