package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.score.GraphScore;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import edu.cmu.tetrad.search.test.MsepTest;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.RandomUtil;

import java.util.List;

/** Reproduces the 2-node X1->X2 tiered-knowledge scenario, oracle and data versions. */
public final class ReproTwoNode {
    private ReproTwoNode() {
    }

    /** GRaSP-based engine over StarFciKeepKnowledgeOrientations, mirroring GraspFci's getMarkovDag. */
    private static final class GraspFciKeep extends StarFciKeepKnowledgeOrientations {
        private final edu.cmu.tetrad.search.score.Score score;
        private final edu.cmu.tetrad.search.test.IndependenceTest test;

        GraspFciKeep(edu.cmu.tetrad.search.test.IndependenceTest test, edu.cmu.tetrad.search.score.Score score) {
            super(test);
            this.test = test;
            this.score = score;
        }

        @Override
        public Graph getMarkovDag(boolean verbose) throws InterruptedException {
            Grasp alg = new Grasp(this.test, this.score);
            alg.setNumStarts(1);
            alg.setVerbose(verbose);
            alg.setKnowledge(getKnowledge());
            alg.bestOrder(this.score.getVariables());
            return alg.getGraph(false);
        }
    }

    public static void main(String[] args) throws Exception {
        RandomUtil.getInstance().setSeed(12345L);

        Node x1 = new GraphNode("X1");
        Node x2 = new GraphNode("X2");
        Graph trueDag = new EdgeListGraph(List.of(x1, x2));
        trueDag.addDirectedEdge(x1, x2);

        Knowledge tiers = new Knowledge();
        tiers.addToTier(0, "X1");
        tiers.addToTier(1, "X2");

        // ---------- Oracle version ----------
        System.out.println("=== ORACLE (MsepTest + GraphScore) ===");
        for (boolean excl : new boolean[]{false, true}) {
            MsepTest msep = new MsepTest(trueDag);
            GraphScore gscore = new GraphScore(trueDag);

            GraspFciKeep keep = new GraspFciKeep(msep, gscore);
            keep.setKnowledge(tiers.copy());
            keep.setExcludeSelectionBias(excl);
            keep.setVerbose(true);
            Graph out = keep.search();
            System.out.println("KEEP  exclSel=" + excl + " -> [" + edges(out) + "]");

            MsepTest msep2 = new MsepTest(trueDag);
            GraphScore gscore2 = new GraphScore(trueDag);
            GraspFci base = new GraspFci(msep2, gscore2);
            base.setKnowledge(tiers.copy());
            base.setExcludeSelectionBias(excl);
            Graph outB = base.search();
            System.out.println("BASE  exclSel=" + excl + " -> [" + edges(outB) + "]");
        }

        // ---------- Data version ----------
        System.out.println("=== DATA (FisherZ + SemBicScore, n = 1000) ===");
        DataSet data = new SemIm(new SemPm(trueDag)).simulateData(1000, false);

        for (boolean excl : new boolean[]{false, true}) {
            IndTestFisherZ test = new IndTestFisherZ(data, 0.01);
            SemBicScore score = new SemBicScore(new CovarianceMatrix(data));
            score.setPenaltyDiscount(2);

            GraspFciKeep keep = new GraspFciKeep(test, score);
            keep.setKnowledge(tiers.copy());
            keep.setExcludeSelectionBias(excl);
            keep.setVerbose(true);
            Graph out = keep.search();
            System.out.println("KEEP  exclSel=" + excl + " -> [" + edges(out) + "]");

            IndTestFisherZ test2 = new IndTestFisherZ(data, 0.01);
            SemBicScore score2 = new SemBicScore(new CovarianceMatrix(data));
            score2.setPenaltyDiscount(2);
            GraspFci base = new GraspFci(test2, score2);
            base.setKnowledge(tiers.copy());
            base.setExcludeSelectionBias(excl);
            Graph outB = base.search();
            System.out.println("BASE  exclSel=" + excl + " -> [" + edges(outB) + "]");
        }
    }

    private static String edges(Graph g) {
        StringBuilder sb = new StringBuilder();
        for (Edge e : g.getEdges()) sb.append(e).append("; ");
        return sb.toString().trim();
    }
}
