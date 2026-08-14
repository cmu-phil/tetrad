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

/**
 * Hand-run harness for FcitZmKeepKnowledgeOrientations. Run main() directly; no JUnit wiring
 * yet. Exit code 0 iff all checks pass. Both commit routes are exercised, since the MAG route
 * is where the knowledge marks were being dropped by the knowledge-free MagToPag conversion.
 * <p>
 * Checks (per commit route):
 * <ol>
 *   <li>DEGENERATION: with empty knowledge, the variant returns exactly the same graph as the
 *       repaired FcitZm on the same data and settings.</li>
 *   <li>KEPT ORIENTATION: for a required edge X-&gt;Y whose mark at X is a circle in the
 *       knowledge-free estimate, the variant's output contains X --&gt; Y while plain FcitZm's
 *       does not keep the tail.</li>
 *   <li>MAG LEGALITY: the variant's output with knowledge still implies a legal MAG (FcitZm's
 *       own output contract), and its canonical strip is a strictly legal PAG.</li>
 *   <li>TIERS / 2-NODE: the X1-&gt;X2 tier scenario gives o-&gt; in both selection-bias
 *       settings; forbidden-edge (tier) enforcement is no longer gated on
 *       excludeSelectionBias (see FciOrient.fciOrientbk).</li>
 * </ol>
 */
public final class TestFcitZmKeepKnowledgeOrientations {

    private TestFcitZmKeepKnowledgeOrientations() {
    }

    public static void main(String[] args) throws Exception {
        int failures = 0;

        for (FcitZm.COMMIT_ROUTE route : FcitZm.COMMIT_ROUTE.values()) {
            System.out.println("\n===== commit route: " + route + " =====");
            failures += runRoute(route);
        }

        // ------------------------------------------------------------------
        // The 2-node tier scenario, both selection-bias settings.
        // ------------------------------------------------------------------
        RandomUtil.getInstance().setSeed(12345L);
        Node x1 = new GraphNode("X1");
        Node x2 = new GraphNode("X2");
        Graph twoNode = new EdgeListGraph(List.of(x1, x2));
        twoNode.addDirectedEdge(x1, x2);
        DataSet twoData = new SemIm(new SemPm(twoNode)).simulateData(1000, false);

        Knowledge tiers = new Knowledge();
        tiers.addToTier(0, "X1");
        tiers.addToTier(1, "X2");

        System.out.println("\n===== 2-node tier scenario =====");
        for (boolean excl : new boolean[]{false, true}) {
            Graph g = runKeep(twoData, tiers, FcitZm.COMMIT_ROUTE.MAG, excl);
            String edges = g.getEdges().toString();
            boolean ok = edges.contains("o->"); // tiers enforced in both selection-bias settings
            System.out.println("  excludeSelectionBias=" + excl + " -> " + edges
                    + " : " + (ok ? "PASS" : "FAIL"));
            if (!ok) failures++;
        }

        System.out.println();
        System.out.println(failures == 0 ? "ALL CHECKS PASSED" : failures + " CHECK(S) FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }

    private static int runRoute(FcitZm.COMMIT_ROUTE route) throws Exception {
        int failures = 0;

        RandomUtil.getInstance().setSeed(38482838L);
        Graph trueDag = RandomGraph.randomGraph(12, 2, 18, 6, 4, 4, false);
        DataSet data = new SemIm(new SemPm(trueDag)).simulateData(2000, false);

        Graph baseFree = runBase(data, new Knowledge(), route, false);
        Graph keepFree = runKeep(data, new Knowledge(), route, false);

        boolean same = baseFree.equals(keepFree);
        System.out.println("[1] Degeneration (empty knowledge, identical outputs): "
                + (same ? "PASS" : "FAIL"));
        if (!same) failures++;

        Node tx = null, ty = null;
        for (Edge e : baseFree.getEdges()) {
            for (int flip = 0; flip < 2; flip++) {
                Node a = flip == 0 ? e.getNode1() : e.getNode2();
                Node b = flip == 0 ? e.getNode2() : e.getNode1();
                if (baseFree.getEndpoint(b, a) != Endpoint.CIRCLE) continue;
                Node ta = trueDag.getNode(a.getName()), tb = trueDag.getNode(b.getName());
                if (ta != null && tb != null && trueDag.isParentOf(ta, tb)) {
                    tx = a;
                    ty = b;
                    break;
                }
            }
            if (tx != null) break;
        }

        if (tx == null) {
            System.out.println("[2] No circle-marked edge matching a true direct edge; change the seed. FAIL");
            return failures + 1;
        }

        System.out.println("    Knowledge target: require " + tx + " -> " + ty
                + " (knowledge-free estimate has " + baseFree.getEdge(tx, ty) + ")");

        Knowledge req = new Knowledge();
        req.setRequired(tx.getName(), ty.getName());

        Graph baseReq = runBase(data, req, route, false);
        Graph keepReq = runKeep(data, req, route, false);

        System.out.println("    FcitZm with knowledge:      "
                + baseReq.getEdge(baseReq.getNode(tx.getName()), baseReq.getNode(ty.getName())));
        System.out.println("    FcitZmKeep... w/ knowledge: "
                + keepReq.getEdge(keepReq.getNode(tx.getName()), keepReq.getNode(ty.getName())));

        boolean kept = keepReq.getEndpoint(keepReq.getNode(ty.getName()), keepReq.getNode(tx.getName())) == Endpoint.TAIL
                && keepReq.getEndpoint(keepReq.getNode(tx.getName()), keepReq.getNode(ty.getName())) == Endpoint.ARROW;
        System.out.println("[2] Required orientation kept: " + (kept ? "PASS" : "FAIL"));
        if (!kept) failures++;

        failures += checkLegality("[3]", keepReq);
        return failures;
    }

    /** FcitZm's own contract: the output implies a legal MAG; its canonical strip is a legal PAG. */
    private static int checkLegality(String label, Graph pag) {
        try {
            Graph mag = GraphTransforms.zhangMagFromPag(pag);
            boolean magLegal = PagLegalityCheck.isLegalMag(mag, new HashSet<>()).isLegalMag();
            Graph canonical = new MagToPag(mag).convert(false, false);
            boolean canonicalLegal = PagLegalityCheck.isLegalPagQuiet(canonical, new HashSet<>());

            boolean pass = magLegal && canonicalLegal;
            System.out.println(label + " Output implies a legal MAG (" + magLegal
                    + ") with a strictly legal canonical strip (" + canonicalLegal + "): "
                    + (pass ? "PASS" : "FAIL"));
            return pass ? 0 : 1;
        } catch (Exception e) {
            System.out.println(label + " Legality check threw: " + e + " FAIL");
            return 1;
        }
    }

    private static Graph runBase(DataSet data, Knowledge k, FcitZm.COMMIT_ROUTE route, boolean excl)
            throws InterruptedException {
        IndTestFisherZ test = new IndTestFisherZ(data, 0.01);
        SemBicScore score = new SemBicScore(new CovarianceMatrix(data));
        score.setPenaltyDiscount(2);
        FcitZm s = new FcitZm(test, score);
        s.setKnowledge(k.copy());
        s.setCommitRoute(route);
        s.setExcludeSelectionBias(excl);
        return s.search();
    }

    private static Graph runKeep(DataSet data, Knowledge k, FcitZm.COMMIT_ROUTE route, boolean excl)
            throws InterruptedException {
        IndTestFisherZ test = new IndTestFisherZ(data, 0.01);
        SemBicScore score = new SemBicScore(new CovarianceMatrix(data));
        score.setPenaltyDiscount(2);
        FcitZmKeepKnowledgeOrientations s = new FcitZmKeepKnowledgeOrientations(test, score);
        s.setKnowledge(k.copy());
        s.setCommitRoute(FcitZmKeepKnowledgeOrientations.COMMIT_ROUTE.valueOf(route.name()));
        s.setExcludeSelectionBias(excl);
        return s.search();
    }
}
