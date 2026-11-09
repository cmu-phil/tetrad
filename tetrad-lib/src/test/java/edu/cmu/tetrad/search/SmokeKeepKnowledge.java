package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.utils.PagLegalityCheck;
import edu.cmu.tetrad.search.utils.MagToPag;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.RandomUtil;

import java.util.HashSet;

/** Multi-seed smoke test: gated + ungated paths, refined legality, conflict fallback. Hand-run. */
public final class SmokeKeepKnowledge {
    private SmokeKeepKnowledge() {
    }

    private static final class Keep extends StarFciKeepKnowledgeOrientations {
        private final edu.cmu.tetrad.search.score.Score score;

        Keep(IndependenceTest test, edu.cmu.tetrad.search.score.Score score) {
            super(test);
            this.score = score;
        }

        @Override
        public Graph getMarkovDag(boolean verbose) throws InterruptedException {
            Fges fges = new Fges(this.score);
            fges.setKnowledge(getKnowledge());
            fges.setVerbose(false);
            return GraphTransforms.dagFromCpdag(fges.search());
        }
    }

    public static void main(String[] args) throws Exception {
        int fails = 0;

        for (long seed : new long[]{1L, 7L, 42L, 1001L, 998877L}) {
            RandomUtil.getInstance().setSeed(seed);
            Graph trueDag = RandomGraph.randomGraph(10, 2, 14, 6, 4, 4, false);
            DataSet data = new SemIm(new SemPm(trueDag)).simulateData(1500, false);

            // Knowledge: require the first true direct edge between measured variables.
            Knowledge k = new Knowledge();
            for (Edge e : trueDag.getEdges()) {
                Node t = Edges.getDirectedEdgeTail(e), h = Edges.getDirectedEdgeHead(e);
                if (t.getNodeType() == NodeType.MEASURED && h.getNodeType() == NodeType.MEASURED) {
                    k.setRequired(t.getName(), h.getName());
                    break;
                }
            }

            for (boolean gating : new boolean[]{true, false}) {
                Graph pag = run(data, k, gating);
                boolean legal = refinedLegal(pag);
                System.out.println("seed=" + seed + " gating=" + gating + " refined-legal=" + legal
                        + " edges=" + pag.getNumEdges());
                if (!legal) fails++;
            }
        }

        // Conflict case: require an edge against an invariant arrowhead and confirm graceful,
        // legal fallback. Find X *-> Y in a knowledge-free run; require Y -> X.
        RandomUtil.getInstance().setSeed(38482838L);
        Graph trueDag = RandomGraph.randomGraph(12, 2, 18, 6, 4, 4, false);
        DataSet data = new SemIm(new SemPm(trueDag)).simulateData(2000, false);
        Graph free = run(data, new Knowledge(), true);

        Node cx = null, cy = null;
        for (Edge e : free.getEdges()) {
            if (e.getEndpoint2() == Endpoint.ARROW) {
                cx = e.getNode1();
                cy = e.getNode2();
                break;
            }
        }

        if (cx != null) {
            Knowledge conflict = new Knowledge();
            conflict.setRequired(cy.getName(), cx.getName());
            Graph pag = run(data, conflict, true);
            boolean legal = refinedLegal(pag);
            System.out.println("conflict case (" + free.getEdge(cx, cy) + ", required " + cy + " -> " + cx
                    + "): refined-legal=" + legal + ", result edge = " + pag.getEdge(
                    pag.getNode(cx.getName()), pag.getNode(cy.getName())));
            if (!legal) fails++;
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

    private static Graph run(DataSet data, Knowledge k, boolean gating) throws InterruptedException {
        IndependenceTest test = new IndTestFisherZ(data, 0.01);
        SemBicScore score = new SemBicScore(new CovarianceMatrix(data));
        score.setPenaltyDiscount(2);
        Keep keep = new Keep(test, score);
        keep.setKnowledge(k);
        keep.setDoLegalityGating(gating);
        keep.setVerbose(false);
        return keep.search();
    }
}
