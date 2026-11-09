package edu.cmu.tetrad.test;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.search.Boss;
import edu.cmu.tetrad.search.PermutationSearch;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.TetradLogger;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;

import static org.junit.Assert.assertFalse;

/**
 * Regression test: 1-based tier numbering leaves tier 0 empty. PermutationSearch
 * must skip empty tiers rather than passing an empty suborder to the suborder
 * search, and Boss must not log a spurious "hit max iterations" warning when a
 * suborder search has in fact converged. Fails on unpatched code (warning is
 * logged on every run); passes with the empty-tier skip and the improved-only
 * warning condition.
 */
public class TestBossEmptyTierWarning {

    @Test
    public void testNoSpuriousMaxIterationsWarningWithOneBasedTiers() throws InterruptedException, java.text.ParseException {
        Graph g = RandomGraph.randomGraph(6, 0, 6, 100, 100, 100, false);
        SemPm pm = new SemPm(g);
        SemIm im = new SemIm(pm);
        DataSet data = im.simulateData(200, false);
        List<Node> vars = data.getVariables();

        // 1-based tiers: tier 0 is left empty, as a user numbering tiers from 1 would.
        Knowledge knowledge = new Knowledge();
        knowledge.addToTier(1, vars.get(0).getName());
        knowledge.addToTier(1, vars.get(1).getName());
        knowledge.addToTier(1, vars.get(2).getName());
        knowledge.addToTier(2, vars.get(3).getName());
        knowledge.addToTier(2, vars.get(4).getName());
        knowledge.addToTier(3, vars.get(5).getName());
        knowledge.setTierForbiddenWithin(1, true);

        SemBicScore score = new SemBicScore(data, true);
        Boss boss = new Boss(score);
        boss.setNumStarts(1);
        boss.setNumThreads(1);
        PermutationSearch search = new PermutationSearch(boss);
        search.setKnowledge(knowledge);

        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        PrintStream stream = new PrintStream(captured);
        TetradLogger.getInstance().addOutputStream(stream);

        try {
            search.search();
        } finally {
            TetradLogger.getInstance().removeOutputStream(stream);
        }

        assertFalse("Spurious iteration-cap warning logged for a converged search with an empty tier 0: "
                        + captured,
                captured.toString().contains("max iterations")
                        || captured.toString().contains("iteration cap"));
    }
}
