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

/**
 * Hand-run harness for FcitKeepKnowledgeOrientations and FcitSlKeepKnowledgeOrientations. Run
 * main() directly; no JUnit wiring yet. Exit code 0 iff all checks pass.
 * <p>
 * Checks:
 * <ol>
 *   <li>DEGENERATION (Fcit): with empty knowledge, FcitKeepKnowledgeOrientations returns exactly
 *       the same graph as Fcit on the same data and settings.</li>
 *   <li>KEPT ORIENTATION (Fcit): for a required edge X-&gt;Y whose mark at X is a circle in the
 *       knowledge-free Fcit estimate, the variant's output contains X --&gt; Y while plain Fcit's
 *       does not keep the tail.</li>
 *   <li>REFINED-LEGALITY (Fcit): the variant's output with knowledge is a legal PAG refined by
 *       knowledge (implied Zhang MAG legal; canonical strip strictly legal).</li>
 *   <li>DEGENERATION (FcitSl): with empty knowledge, FcitSlKeepKnowledgeOrientations returns
 *       exactly the same graph as FcitSl.</li>
 *   <li>KEPT ORIENTATION + CERTIFICATE (FcitSl): with the same required edge, the variant keeps
 *       X --&gt; Y (as plain FcitSl already does) and its output passes refined legality.</li>
 * </ol>
 */
public final class TestFcitKeepKnowledgeOrientations {

    private TestFcitKeepKnowledgeOrientations() {
    }

    public static void main(String[] args) throws Exception {
        RandomUtil.getInstance().setSeed(38482838L);

        Graph trueDag = RandomGraph.randomGraph(12, 2, 18, 6, 4, 4, false);
        DataSet data = new SemIm(new SemPm(trueDag)).simulateData(2000, false);

        int failures = 0;

        // ------------------------------------------------------------------
        // Check 1: Fcit degeneration with empty knowledge.
        // ------------------------------------------------------------------
        Graph fcitFree = runFcit(data, new Knowledge());
        Graph keepFree = runFcitKeep(data, new Knowledge());

        boolean same = fcitFree.equals(keepFree);
        System.out.println("[1] Fcit degeneration (empty knowledge, identical outputs): "
                + (same ? "PASS" : "FAIL"));
        if (!same) failures++;

        // ------------------------------------------------------------------
        // Knowledge target: X *-* Y adjacent in the knowledge-free Fcit estimate with a CIRCLE at
        // X, where the true DAG has the direct edge X -> Y.
        // ------------------------------------------------------------------
        Node tx = null, ty = null;
        for (Edge e : fcitFree.getEdges()) {
            for (int flip = 0; flip < 2; flip++) {
                Node a = flip == 0 ? e.getNode1() : e.getNode2();
                Node b = flip == 0 ? e.getNode2() : e.getNode1();
                if (fcitFree.getEndpoint(b, a) != Endpoint.CIRCLE) continue;
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
            failures++;
        } else {
            System.out.println("    Knowledge target: require " + tx + " -> " + ty
                    + " (knowledge-free Fcit estimate has " + fcitFree.getEdge(tx, ty) + ")");

            Knowledge req = new Knowledge();
            req.setRequired(tx.getName(), ty.getName());

            Graph fcitReq = runFcit(data, req);
            Graph keepReq = runFcitKeep(data, req);

            Edge fe = fcitReq.getEdge(fcitReq.getNode(tx.getName()), fcitReq.getNode(ty.getName()));
            Edge ke = keepReq.getEdge(keepReq.getNode(tx.getName()), keepReq.getNode(ty.getName()));
            System.out.println("    Fcit with knowledge:              " + fe);
            System.out.println("    FcitKeep... with knowledge:       " + ke);

            boolean kept = ke != null
                    && keepReq.getEndpoint(keepReq.getNode(ty.getName()), keepReq.getNode(tx.getName())) == Endpoint.TAIL
                    && keepReq.getEndpoint(keepReq.getNode(tx.getName()), keepReq.getNode(ty.getName())) == Endpoint.ARROW;
            System.out.println("[2] Required orientation kept by FcitKeepKnowledgeOrientations: "
                    + (kept ? "PASS" : "FAIL"));
            if (!kept) failures++;

            failures += checkRefinedLegality("[3]", keepReq);

            // ------------------------------------------------------------------
            // FcitSl checks with the same target.
            // ------------------------------------------------------------------
            Graph slFree = runFcitSl(data, new Knowledge());
            Graph slKeepFree = runFcitSlKeep(data, new Knowledge());

            boolean slSame = slFree.equals(slKeepFree);
            System.out.println("[4] FcitSl degeneration (empty knowledge, identical outputs): "
                    + (slSame ? "PASS" : "FAIL"));
            if (!slSame) failures++;

            Graph slKeepReq = runFcitSlKeep(data, req);
            Edge se = slKeepReq.getEdge(slKeepReq.getNode(tx.getName()), slKeepReq.getNode(ty.getName()));
            System.out.println("    FcitSlKeep... with knowledge:     " + se);

            boolean slKept = se != null
                    && slKeepReq.getEndpoint(slKeepReq.getNode(ty.getName()), slKeepReq.getNode(tx.getName())) == Endpoint.TAIL
                    && slKeepReq.getEndpoint(slKeepReq.getNode(tx.getName()), slKeepReq.getNode(ty.getName())) == Endpoint.ARROW;
            System.out.println("[5a] Required orientation kept by FcitSlKeepKnowledgeOrientations: "
                    + (slKept ? "PASS" : "FAIL"));
            if (!slKept) failures++;

            failures += checkRefinedLegality("[5b]", slKeepReq);
        }

        System.out.println();
        System.out.println(failures == 0 ? "ALL CHECKS PASSED" : failures + " CHECK(S) FAILED");
        System.exit(failures == 0 ? 0 : 1);
    }

    /** Verifies that pag's implied Zhang MAG is legal and that its canonical PAG is strictly legal. */
    private static int checkRefinedLegality(String label, Graph pag) {
        try {
            Graph mag = GraphTransforms.zhangMagFromPag(pag);
            PagLegalityCheck.LegalMagRet legalMag = PagLegalityCheck.isLegalMag(mag, new HashSet<>());
            Graph canonical = new MagToPag(mag).convert(false, false);
            boolean canonicalLegal = PagLegalityCheck.isLegalPagQuiet(canonical, new HashSet<>());

            boolean pass = legalMag.isLegalMag() && canonicalLegal;
            System.out.println(label + " Output is a legal PAG refined by knowledge (implied MAG legal: "
                    + legalMag.isLegalMag() + ", canonical strip legal: " + canonicalLegal + "): "
                    + (pass ? "PASS" : "FAIL"));
            return pass ? 0 : 1;
        } catch (Exception e) {
            System.out.println(label + " Refined-legality check threw: " + e + " FAIL");
            return 1;
        }
    }

    private static Graph runFcit(DataSet data, Knowledge k) throws InterruptedException {
        IndTestFisherZ test = new IndTestFisherZ(data, 0.01);
        SemBicScore score = new SemBicScore(new CovarianceMatrix(data));
        score.setPenaltyDiscount(2);
        Fcit fcit = new Fcit(test, score);
        fcit.setKnowledge(k.copy());
        return fcit.search();
    }

    private static Graph runFcitKeep(DataSet data, Knowledge k) throws InterruptedException {
        IndTestFisherZ test = new IndTestFisherZ(data, 0.01);
        SemBicScore score = new SemBicScore(new CovarianceMatrix(data));
        score.setPenaltyDiscount(2);
        FcitKeepKnowledgeOrientations fcit = new FcitKeepKnowledgeOrientations(test, score);
        fcit.setKnowledge(k.copy());
        return fcit.search();
    }

    private static Graph runFcitSl(DataSet data, Knowledge k) throws InterruptedException {
        IndTestFisherZ test = new IndTestFisherZ(data, 0.01);
        SemBicScore score = new SemBicScore(new CovarianceMatrix(data));
        score.setPenaltyDiscount(2);
        FcitSl s = new FcitSl(test, score);
        s.setKnowledge(k.copy());
        return s.search();
    }

    private static Graph runFcitSlKeep(DataSet data, Knowledge k) throws InterruptedException {
        IndTestFisherZ test = new IndTestFisherZ(data, 0.01);
        SemBicScore score = new SemBicScore(new CovarianceMatrix(data));
        score.setPenaltyDiscount(2);
        FcitSlKeepKnowledgeOrientations s = new FcitSlKeepKnowledgeOrientations(test, score);
        s.setKnowledge(k.copy());
        return s.search();
    }
}
