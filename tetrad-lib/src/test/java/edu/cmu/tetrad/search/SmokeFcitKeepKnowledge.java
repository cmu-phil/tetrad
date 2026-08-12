package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import edu.cmu.tetrad.search.utils.MagToPag;
import edu.cmu.tetrad.search.utils.PagLegalityCheck;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.RandomUtil;

import java.util.HashSet;
import java.util.List;

/** Smoke: Fcit/FcitSl keep-knowledge variants across seeds with tiers; plus the 2-node scenario. Hand-run. */
public final class SmokeFcitKeepKnowledge {
    private SmokeFcitKeepKnowledge() {
    }

    public static void main(String[] args) throws Exception {
        int fails = 0;

        for (long seed : new long[]{7L, 42L, 1001L}) {
            RandomUtil.getInstance().setSeed(seed);
            Graph trueDag = RandomGraph.randomGraph(10, 2, 14, 6, 4, 4, false);
            DataSet data = new SemIm(new SemPm(trueDag)).simulateData(1500, false);

            List<Node> order = trueDag.paths().getValidOrder(trueDag.getNodes(), true);
            Knowledge tiers = new Knowledge();
            int i = 0;
            int numMeasured = 0;
            for (Node n : order) if (n.getNodeType() == NodeType.MEASURED) numMeasured++;
            for (Node n : order) {
                if (n.getNodeType() != NodeType.MEASURED) continue;
                tiers.addToTier(i++ < numMeasured / 2 ? 0 : 1, n.getName());
            }

            Graph f = runFcitKeep(data, tiers, true);
            boolean fLegal = refinedLegal(f);
            System.out.println("seed=" + seed + " FcitKeep   tiers exclSel=true refined-legal=" + fLegal
                    + " edges=" + f.getNumEdges());
            if (!fLegal) fails++;

            Graph s = runFcitSlKeep(data, tiers, true);
            boolean sLegal = refinedLegal(s);
            System.out.println("seed=" + seed + " FcitSlKeep tiers exclSel=true refined-legal=" + sLegal
                    + " edges=" + s.getNumEdges());
            if (!sLegal) fails++;
        }

        // The 2-node GUI scenario.
        RandomUtil.getInstance().setSeed(12345L);
        Node x1 = new GraphNode("X1");
        Node x2 = new GraphNode("X2");
        Graph twoNode = new EdgeListGraph(List.of(x1, x2));
        twoNode.addDirectedEdge(x1, x2);
        Knowledge twoTiers = new Knowledge();
        twoTiers.addToTier(0, "X1");
        twoTiers.addToTier(1, "X2");
        DataSet twoData = new SemIm(new SemPm(twoNode)).simulateData(1000, false);

        for (boolean excl : new boolean[]{false, true}) {
            System.out.println("2-node FcitKeep   exclSel=" + excl + " -> "
                    + runFcitKeep(twoData, twoTiers, excl).getEdges());
            System.out.println("2-node FcitSlKeep exclSel=" + excl + " -> "
                    + runFcitSlKeep(twoData, twoTiers, excl).getEdges());
        }

        System.out.println(fails == 0 ? "SMOKE OK" : fails + " SMOKE FAILURES");
        System.exit(fails == 0 ? 0 : 1);
    }

    private static boolean refinedLegal(Graph pag) {
        try {
            Graph mag = GraphTransforms.zhangMagFromPag(pag);
            if (!PagLegalityCheck.isLegalMag(mag, new HashSet<>()).isLegalMag()) return false;
            Graph canonical = new MagToPag(mag).convert(false, false);
            return PagLegalityCheck.isLegalPagQuiet(canonical, new HashSet<>());
        } catch (Exception e) {
            return false;
        }
    }

    private static Graph runFcitKeep(DataSet data, Knowledge k, boolean excl) throws InterruptedException {
        IndTestFisherZ test = new IndTestFisherZ(data, 0.01);
        SemBicScore score = new SemBicScore(new CovarianceMatrix(data));
        score.setPenaltyDiscount(2);
        FcitKeepKnowledgeOrientations fcit = new FcitKeepKnowledgeOrientations(test, score);
        fcit.setKnowledge(k.copy());
        fcit.setExcludeSelectionBias(excl);
        return fcit.search();
    }

    private static Graph runFcitSlKeep(DataSet data, Knowledge k, boolean excl) throws InterruptedException {
        IndTestFisherZ test = new IndTestFisherZ(data, 0.01);
        SemBicScore score = new SemBicScore(new CovarianceMatrix(data));
        score.setPenaltyDiscount(2);
        FcitSlKeepKnowledgeOrientations s = new FcitSlKeepKnowledgeOrientations(test, score);
        s.setKnowledge(k.copy());
        s.setExcludeSelectionBias(excl);
        return s.search();
    }
}
