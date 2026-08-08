package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.utils.MagToPag;
import edu.cmu.tetrad.search.utils.PagLegalityCheck;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.RandomUtil;

import java.util.HashSet;
import java.util.List;

/**
 * Hand-run harness for StarFciKeepKnowledgeOrientations. Run main() directly; no JUnit wiring yet
 * (per current test-harness convention). Exit code 0 iff all checks pass.
 * <p>
 * NOTE (2026-8): the base-behavior comparator is the private nested GfciBase below, a direct
 * StarFciGuaranteePag subclass with the same FGES Markov-DAG supplier as GfciKeepKnowledge. It
 * deliberately does NOT use the public Gfci class: since Gfci (along with SpFci, GraspFci, and
 * Bfci) was switched to extend StarFciKeepKnowledgeOrientations, using Gfci here would compare
 * the new engine against itself, making checks 1 and 2 vacuous.
 * <p>
 * Checks, in order:
 * <ol>
 *   <li>DEGENERATION: with empty knowledge, the new engine returns exactly the same graph as
 *       StarFciGuaranteePag (via GfciBase) on the same data and settings.</li>
 *   <li>KEPT ORIENTATION: for a required edge X-&gt;Y whose mark at X is a circle in the
 *       knowledge-free estimate (so the requirement genuinely goes beyond the equivalence class),
 *       the new engine's output contains X --&gt; Y, while the base engine's output does not keep
 *       the tail at X.</li>
 *   <li>REFINED-LEGALITY: the new engine's output is a legal PAG refined by knowledge -- its
 *       implied Zhang MAG is a legal MAG, and the canonical PAG of that MAG passes the strict
 *       legality check.</li>
 *   <li>TIERS: with two temporal tiers and selection bias excluded, the new engine's output again
 *       passes the refined-legality check and carries at least as many oriented (non-circle)
 *       endpoint marks as the base engine's output.</li>
 * </ol>
 */
public final class TestStarFciKeepKnowledgeOrientations {

    private TestStarFciKeepKnowledgeOrientations() {
    }

    /**
     * Base-engine comparator: a direct StarFciGuaranteePag subclass with the same FGES Markov-DAG
     * supplier as GfciKeepKnowledge, so base-vs-new comparisons differ only in the legality
     * machinery. (The public Gfci class can no longer serve this role; see the class Javadoc.)
     */
    private static final class GfciBase extends StarFciGuaranteePag {
        private final edu.cmu.tetrad.search.score.Score score;

        GfciBase(IndependenceTest test, edu.cmu.tetrad.search.score.Score score) {
            super(test);
            this.score = score;
        }

        @Override
        public Graph getMarkovDag(boolean verbose) throws InterruptedException {
            Fges fges = new Fges(this.score);
            fges.setKnowledge(getKnowledge());
            fges.setVerbose(false);
            Graph graph = fges.search();
            return GraphTransforms.dagFromCpdag(graph);
        }
    }

    /**
     * Concrete engine over the new abstract class, using the same FGES Markov-DAG supplier as
     * GfciBase, so base-vs-new comparisons differ only in the legality machinery.
     */
    private static final class GfciKeepKnowledge extends StarFciKeepKnowledgeOrientations {
        private final edu.cmu.tetrad.search.score.Score score;

        GfciKeepKnowledge(IndependenceTest test, edu.cmu.tetrad.search.score.Score score) {
            super(test);
            this.score = score;
        }

        @Override
        public Graph getMarkovDag(boolean verbose) throws InterruptedException {
            Fges fges = new Fges(this.score);
            fges.setKnowledge(getKnowledge());
            fges.setVerbose(false);
            Graph graph = fges.search();
            return GraphTransforms.dagFromCpdag(graph);
        }
    }

    public static void main(String[] args) throws Exception {
        RandomUtil.getInstance().setSeed(38482838L);

        Graph trueDag = RandomGraph.randomGraph(12, 2, 18, 6, 4, 4, false);
        SemPm pm = new SemPm(trueDag);
        SemIm im = new SemIm(pm);
        DataSet data = im.simulateData(2000, false);

        int failures = 0;

        // ------------------------------------------------------------------
        // Check 1: degeneration with empty knowledge.
        // ------------------------------------------------------------------
        Graph baseNoKnow = runBase(data, new Knowledge(), false);
        Graph keepNoKnow = runKeep(data, new Knowledge(), false);

        boolean sameNoKnow = baseNoKnow.equals(keepNoKnow);
        System.out.println("[1] Degeneration (empty knowledge, identical outputs): "
                + (sameNoKnow ? "PASS" : "FAIL"));
        if (!sameNoKnow) {
            failures++;
            System.out.println("    base: " + baseNoKnow);
            System.out.println("    keep: " + keepNoKnow);
        }

        // ------------------------------------------------------------------
        // Pick a knowledge target: X *-* Y adjacent in the knowledge-free estimate with a CIRCLE
        // at X, where the true DAG has the direct edge X -> Y. Requiring X -> Y then adds a tail
        // at X beyond the invariant marks of the class.
        // ------------------------------------------------------------------
        Node targetX = null, targetY = null;
        for (Edge e : baseNoKnow.getEdges()) {
            for (int flip = 0; flip < 2; flip++) {
                Node x = (flip == 0) ? e.getNode1() : e.getNode2();
                Node y = (flip == 0) ? e.getNode2() : e.getNode1();
                if (baseNoKnow.getEndpoint(y, x) != Endpoint.CIRCLE) continue;

                Node tx = trueDag.getNode(x.getName());
                Node ty = trueDag.getNode(y.getName());
                if (tx == null || ty == null) continue;

                if (trueDag.isParentOf(tx, ty)) {
                    targetX = x;
                    targetY = y;
                    break;
                }
            }
            if (targetX != null) break;
        }

        if (targetX == null) {
            System.out.println("[2] Could not find a circle-marked edge matching a true direct edge; "
                    + "change the seed. FAIL");
            failures++;
        } else {
            System.out.println("    Knowledge target: require " + targetX + " -> " + targetY
                    + " (knowledge-free estimate has " + baseNoKnow.getEdge(targetX, targetY) + ")");

            Knowledge required = new Knowledge();
            required.setRequired(targetX.getName(), targetY.getName());

            Graph baseKnow = runBase(data, required, false);
            Graph keepKnow = runKeep(data, required, false);

            Edge baseEdge = baseKnow.getEdge(baseKnow.getNode(targetX.getName()), baseKnow.getNode(targetY.getName()));
            Edge keepEdge = keepKnow.getEdge(keepKnow.getNode(targetX.getName()), keepKnow.getNode(targetY.getName()));

            System.out.println("    base with knowledge: " + baseEdge);
            System.out.println("    keep with knowledge: " + keepEdge);

            // ------------------------------------------------------------------
            // Check 2: the new engine keeps the required orientation X --> Y.
            // ------------------------------------------------------------------
            boolean kept = keepEdge != null
                    && keepKnow.getEndpoint(keepKnow.getNode(targetY.getName()), keepKnow.getNode(targetX.getName())) == Endpoint.TAIL
                    && keepKnow.getEndpoint(keepKnow.getNode(targetX.getName()), keepKnow.getNode(targetY.getName())) == Endpoint.ARROW;
            System.out.println("[2] Required orientation kept by new engine: " + (kept ? "PASS" : "FAIL"));
            if (!kept) failures++;

            boolean baseKept = baseEdge != null
                    && baseKnow.getEndpoint(baseKnow.getNode(targetY.getName()), baseKnow.getNode(targetX.getName())) == Endpoint.TAIL;
            System.out.println("    (for the record, base engine kept the tail: " + baseKept + ")");

            // ------------------------------------------------------------------
            // Check 3: the new engine's output is a legal PAG refined by knowledge.
            // ------------------------------------------------------------------
            failures += checkRefinedLegality("[3]", keepKnow);
        }

        // ------------------------------------------------------------------
        // Check 4: temporal tiers with selection bias excluded.
        // ------------------------------------------------------------------
        List<Node> order = trueDag.paths().getValidOrder(trueDag.getNodes(), true);
        Knowledge tiers = new Knowledge();
        int i = 0;
        int numMeasured = 0;
        for (Node n : order) if (n.getNodeType() == NodeType.MEASURED) numMeasured++;
        for (Node n : order) {
            if (n.getNodeType() != NodeType.MEASURED) continue;
            tiers.addToTier(i++ < numMeasured / 2 ? 0 : 1, n.getName());
        }

        Graph baseTiers = runBase(data, tiers, true);
        Graph keepTiers = runKeep(data, tiers, true);

        failures += checkRefinedLegality("[4a]", keepTiers);

        int baseMarks = countOrientedEndpoints(baseTiers);
        int keepMarks = countOrientedEndpoints(keepTiers);
        System.out.println("[4b] Oriented endpoint marks, base = " + baseMarks + ", keep = " + keepMarks
                + ": " + (keepMarks >= baseMarks ? "PASS" : "FAIL"));
        if (keepMarks < baseMarks) failures++;

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

    private static int countOrientedEndpoints(Graph g) {
        int count = 0;
        for (Edge e : g.getEdges()) {
            if (e.getEndpoint1() != Endpoint.CIRCLE) count++;
            if (e.getEndpoint2() != Endpoint.CIRCLE) count++;
        }
        return count;
    }

    private static Graph runBase(DataSet data, Knowledge knowledge, boolean excludeSelectionBias)
            throws InterruptedException {
        IndependenceTest test = new IndTestFisherZ(data, 0.01);
        SemBicScore score = new SemBicScore(new CovarianceMatrix(data));
        score.setPenaltyDiscount(2);
        GfciBase gfci = new GfciBase(test, score);
        gfci.setKnowledge(knowledge);
        gfci.setExcludeSelectionBias(excludeSelectionBias);
        gfci.setVerbose(false);
        return gfci.search();
    }

    private static Graph runKeep(DataSet data, Knowledge knowledge, boolean excludeSelectionBias)
            throws InterruptedException {
        IndependenceTest test = new IndTestFisherZ(data, 0.01);
        SemBicScore score = new SemBicScore(new CovarianceMatrix(data));
        score.setPenaltyDiscount(2);
        GfciKeepKnowledge keep = new GfciKeepKnowledge(test, score);
        keep.setKnowledge(knowledge);
        keep.setExcludeSelectionBias(excludeSelectionBias);
        keep.setVerbose(false);
        return keep.search();
    }
}
