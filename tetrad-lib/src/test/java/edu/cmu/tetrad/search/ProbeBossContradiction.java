package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.RandomUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Adversarial: tiers REVERSED relative to the true chain X1->X2->X3 (knowledge says X3 before X1),
 * run both with full tiers (structural branch) and partial tiers (else-branch). The output must
 * never violate knowledge and must still explain the dependence as well as the constraint allows
 * (reversed chain X3->X2->X1 fits a Gaussian chain equally well, so BIC should find it).
 */
public final class ProbeBossContradiction {
    private ProbeBossContradiction() {
    }

    public static void main(String[] args) throws Exception {
        RandomUtil.getInstance().setSeed(555L);
        List<Node> nodes = new ArrayList<>();
        for (int i = 1; i <= 3; i++) nodes.add(new GraphNode("X" + i));
        Graph chain = new EdgeListGraph(nodes);
        chain.addDirectedEdge(nodes.get(0), nodes.get(1));
        chain.addDirectedEdge(nodes.get(1), nodes.get(2));
        DataSet data = new SemIm(new SemPm(chain)).simulateData(2000, false);

        int fails = 0;

        // Full reversed tiers: X3 tier 0, X2 tier 1, X1 tier 2.
        Knowledge reversed = new Knowledge();
        reversed.addToTier(0, "X3");
        reversed.addToTier(1, "X2");
        reversed.addToTier(2, "X1");

        Graph g1 = runBoss(data, reversed);
        boolean ok1 = !reversed.isViolatedBy(g1) && g1.getNumEdges() == 2;
        System.out.println("[1] reversed full tiers -> " + g1.getEdges() + " violated="
                + reversed.isViolatedBy(g1) + " : " + (ok1 ? "PASS" : "FAIL"));
        if (!ok1) fails++;

        // Partial reversed tiers: X3 tier 0, X1 tier 1, X2 in NO tier (else-branch).
        Knowledge partial = new Knowledge();
        partial.addToTier(0, "X3");
        partial.addToTier(1, "X1");

        int violations = 0, badFits = 0;
        for (int r = 0; r < 20; r++) {
            RandomUtil.getInstance().setSeed(3000L + r);
            Graph g = runBoss(data, partial);
            if (partial.isViolatedBy(g)) violations++;
            if (g.getNumEdges() != 2) badFits++;
        }
        boolean ok2 = violations == 0 && badFits == 0;
        System.out.println("[2] partial reversed tiers (else-branch), 20 restarts: violations=" + violations
                + " badFits=" + badFits + " : " + (ok2 ? "PASS" : "FAIL"));
        if (!ok2) fails++;

        System.out.println(fails == 0 ? "CONTRADICTION PROBE OK" : fails + " FAILURES");
        System.exit(fails == 0 ? 0 : 1);
    }

    private static Graph runBoss(DataSet data, Knowledge k) throws InterruptedException {
        SemBicScore score = new SemBicScore(new CovarianceMatrix(data));
        score.setPenaltyDiscount(2);
        Boss boss = new Boss(score);
        boss.setNumStarts(1);
        boss.setUseBes(false);
        PermutationSearch ps = new PermutationSearch(boss);
        ps.setKnowledge(k);
        return ps.search(true);
    }
}
