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
 * Probes BOSS (via PermutationSearch) for the GRaSP-style knowledge blind spot. Hand-run.
 * <p>
 * Scenarios:
 * <ol>
 *   <li>2-node X1-&gt;X2 with full tiers: expect the edge present, no violation.</li>
 *   <li>Chain X1-&gt;...-&gt;X5, full tiers ({X1,X2} tier 0, {X3,X4,X5} tier 1): expect
 *       chain recovered, no violation. This exercises the structural tier-by-tier branch.</li>
 *   <li>Same chain, PARTIAL tiers (X2 in no tier): this falls into PermutationSearch's
 *       else-branch where NOTHING constrains the order by tiers -- the candidate blind
 *       spot. 20 shuffled restarts; check every output for knowledge violations, edge
 *       count stability, and Markov adequacy proxy (edge count vs the full-tier run).</li>
 *   <li>Required-edge-only knowledge (no tiers) on the chain: 20 shuffled restarts;
 *       check required edge present and no violations.</li>
 * </ol>
 */
public final class ProbeBossKnowledge {
    private ProbeBossKnowledge() {
    }

    public static void main(String[] args) throws Exception {
        int fails = 0;

        // ---------------- Scenario 1: 2-node, full tiers ----------------
        RandomUtil.getInstance().setSeed(12345L);
        Node x1 = new GraphNode("X1");
        Node x2 = new GraphNode("X2");
        Graph twoNode = new EdgeListGraph(List.of(x1, x2));
        twoNode.addDirectedEdge(x1, x2);
        DataSet twoData = new SemIm(new SemPm(twoNode)).simulateData(1000, false);

        Knowledge twoTiers = new Knowledge();
        twoTiers.addToTier(0, "X1");
        twoTiers.addToTier(1, "X2");

        Graph g1 = runBoss(twoData, twoTiers, null);
        boolean ok1 = g1.getNumEdges() == 1 && !twoTiers.isViolatedBy(g1);
        System.out.println("[1] 2-node full tiers -> " + g1.getEdges() + " : " + (ok1 ? "PASS" : "FAIL"));
        if (!ok1) fails++;

        // ---------------- Chain setup ----------------
        RandomUtil.getInstance().setSeed(777L);
        List<Node> chainNodes = new ArrayList<>();
        for (int i = 1; i <= 5; i++) chainNodes.add(new GraphNode("X" + i));
        Graph chain = new EdgeListGraph(chainNodes);
        for (int i = 0; i < 4; i++) chain.addDirectedEdge(chainNodes.get(i), chainNodes.get(i + 1));
        DataSet chainData = new SemIm(new SemPm(chain)).simulateData(2000, false);

        // ---------------- Scenario 2: chain, full tiers ----------------
        Knowledge fullTiers = new Knowledge();
        fullTiers.addToTier(0, "X1");
        fullTiers.addToTier(0, "X2");
        fullTiers.addToTier(1, "X3");
        fullTiers.addToTier(1, "X4");
        fullTiers.addToTier(1, "X5");

        Graph g2 = runBoss(chainData, fullTiers, null);
        boolean ok2 = !fullTiers.isViolatedBy(g2) && g2.getNumEdges() == 4;
        System.out.println("[2] chain full tiers -> edges=" + g2.getNumEdges() + " violated="
                + fullTiers.isViolatedBy(g2) + " : " + (ok2 ? "PASS" : "FAIL"));
        if (!ok2) fails++;

        // ---------------- Scenario 3: chain, PARTIAL tiers (X2 in no tier) ----------------
        Knowledge partialTiers = new Knowledge();
        partialTiers.addToTier(0, "X1");
        partialTiers.addToTier(1, "X3");
        partialTiers.addToTier(1, "X4");
        partialTiers.addToTier(1, "X5");
        // X2 deliberately in no tier -> PermutationSearch else-branch.

        int violations3 = 0, edgeMismatches3 = 0;
        for (int r = 0; r < 20; r++) {
            List<Node> shuffled = null; // runBoss shuffles internally per seed
            RandomUtil.getInstance().setSeed(1000L + r);
            Graph g = runBoss(chainData, partialTiers, shuffledOrder(chainData, 1000L + r));
            if (partialTiers.isViolatedBy(g)) violations3++;
            if (g.getNumEdges() != 4) edgeMismatches3++;
        }
        boolean ok3 = violations3 == 0 && edgeMismatches3 == 0;
        System.out.println("[3] chain partial tiers (else-branch), 20 shuffled restarts: violations="
                + violations3 + " edgeMismatches=" + edgeMismatches3 + " : " + (ok3 ? "PASS" : "FAIL"));
        if (!ok3) fails++;

        // ---------------- Scenario 4: required-edge-only knowledge ----------------
        Knowledge reqOnly = new Knowledge();
        reqOnly.setRequired("X2", "X3");

        int violations4 = 0, missingReq4 = 0;
        for (int r = 0; r < 20; r++) {
            RandomUtil.getInstance().setSeed(2000L + r);
            Graph g = runBoss(chainData, reqOnly, shuffledOrder(chainData, 2000L + r));
            if (reqOnly.isViolatedBy(g)) violations4++;
            Node a = g.getNode("X2"), b = g.getNode("X3");
            Edge e = g.getEdge(a, b);
            boolean directed = e != null && g.getEndpoint(a, b) == Endpoint.ARROW
                    && g.getEndpoint(b, a) == Endpoint.TAIL;
            if (!directed) missingReq4++;
        }
        boolean ok4 = violations4 == 0 && missingReq4 == 0;
        System.out.println("[4] required-only knowledge, 20 shuffled restarts: violations=" + violations4
                + " requiredEdgeMissingOrUndirected=" + missingReq4 + " : " + (ok4 ? "PASS" : "FAIL"));
        if (!ok4) fails++;

        System.out.println();
        System.out.println(fails == 0 ? "BOSS PROBE OK" : fails + " BOSS PROBE FAILURES");
        System.exit(fails == 0 ? 0 : 1);
    }

    private static List<Node> shuffledOrder(DataSet data, long seed) {
        SemBicScore score = new SemBicScore(new CovarianceMatrix(data));
        List<Node> order = new ArrayList<>(score.getVariables());
        RandomUtil.getInstance().setSeed(seed);
        RandomUtil.shuffle(order);
        return order;
    }

    private static Graph runBoss(DataSet data, Knowledge k, List<Node> order) throws InterruptedException {
        SemBicScore score = new SemBicScore(new CovarianceMatrix(data));
        score.setPenaltyDiscount(2);
        Boss boss = new Boss(score);
        boss.setNumStarts(1);
        boss.setUseBes(false);
        PermutationSearch ps = new PermutationSearch(boss);
        ps.setKnowledge(k);
        if (order != null) ps.setOrder(order);
        return ps.search(true);
    }
}
