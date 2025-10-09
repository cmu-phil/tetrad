package edu.cmu.tetrad.search;

import java.util.*;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.sem.*;
import edu.cmu.tetrad.search.utils.PagLegalityCheck;
import edu.cmu.tetrad.search.utils.PagLegalityCheck.LegalPagRet;
import edu.cmu.tetrad.util.RandomUtil;

public class LvliteRepro {
    public static void main(String[] args) {
        long seed = 12345L;
        RandomUtil.getInstance().setSeed(seed);
        System.setProperty("java.util.concurrent.ForkJoinPool.common.parallelism", "1");

        // If your grid uses selection nodes, populate this accordingly.
        // For a pure repro, keep empty unless your grid truly sets selection.
        Set<Node> selection = Collections.emptySet();

        for (int i = 0; i < 10; i++) {
            // Try to mirror your dense grid: scale these up to your real values
            Graph trueDag = RandomGraph.randomGraph(
                    /*measured=*/50,
                    /*avgDeg=*/10,
                    /*numLatents=*/20,
                    /*maxIn=*/100, /*maxOut=*/100, /*maxErr=*/100,
                    false,
                    /*seed=*/seed + i);

            // Optional: if grid uses selection, mark selection nodes here and
            // set `selection` to those nodes’ measured proxies if applicable.

            RandomUtil.getInstance().setSeed(seed + 1);

            SemPm pm = new SemPm(trueDag);
            SemIm im = new SemIm(pm);
            DataSet ds = im.simulateData(5000, /*latentToMeasuredOnly=*/false);

            // Score/penalty: mirror your grid’s exact params
            double penaltyMult = 2.0; // <- replace with grid value
            Score score = new SemBicScore(new CovarianceMatrix(ds), penaltyMult);

            // LV-Lite with exact grid flags
            LvDumb lv = new LvDumb(score);
            lv.setVerbose(false);
//            lv.setDeterministic(true);   // if grid uses internal randomness, set the same here
//            lv.setParallel(false);       // if grid runs in parallel, set true to match

            // Mirror ALL toggles your grid sets:
            // lv.setUseR4(true/false);
            // lv.setMaxBlockingLen(Lb);
            // lv.setMaxDiscPathLen(Ld);
            // lv.setDepthBound(d);
            // lv.setPropagateToFixpoint(true/false);
            // lv.setRestoreMaximality(true/false);
            // lv.setFixAlmostCycles(true/false);
            // lv.setTimeoutMillis(...);

//            printParams(lv, penaltyMult, selection);

            Graph pag = null;
            try {
                pag = lv.search();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            // Ensure you run the SAME post-passes as the grid in the SAME order:
            // pag = PostPasses.propagateToFixpoint(pag);
            // pag = PostPasses.restoreMaximality(pag);
            // pag = PostPasses.fixAlmostCycles(pag);

            // Cheap invariants: catch issues even if the validator differs
            assertNoDirectedCycles(pag);
            assertEndpointConsistency(pag);

            LegalPagRet ret = PagLegalityCheck.isLegalPag(pag, selection);
            System.out.println(ret.isLegalPag() + " :: " + ret.getReason());

            if (!ret.isLegalPag()) {
                System.out.println("First illegal PAG at i=" + i);
                System.out.println(pag);
                break;
            }
        }
    }

    private static void printParams(LvDumb lv, double penaltyMult, Set<Node> selection) {
        System.out.println("=== LV-Lite Param Fingerprint ===");
        System.out.println("penaltyMult=" + penaltyMult);
        System.out.println("deterministic=" + true /* or lv.getDeterministic() if available */);
        System.out.println("parallel=" + false       /* or lv.getParallel() */);
        // System.out.println("useR4=" + lv.getUseR4());
        // System.out.println("Lb=" + lv.getMaxBlockingLen());
        // System.out.println("Ld=" + lv.getMaxDiscPathLen());
        // System.out.println("depthBound=" + lv.getDepthBound());
        // System.out.println("propagate=" + lv.getPropagateToFixpoint());
        // System.out.println("restoreMax=" + lv.getRestoreMaximality());
        // System.out.println("fixAlmostCycles=" + lv.getFixAlmostCycles());
        System.out.println("selectionSize=" + selection.size());
        System.out.println("=================================");
    }

    private static void assertNoDirectedCycles(Graph g) {
        if (g.paths().existsDirectedCycle()) {
            throw new IllegalStateException("Directed cycle detected.");
        }
    }
    private static void assertEndpointConsistency(Graph g) {
        for (Edge e : g.getEdges()) {
            Endpoint a = e.getEndpoint1();
            Endpoint b = e.getEndpoint2();
            if (a == null || b == null) throw new IllegalStateException("Null endpoint.");
            // Add any internal endpoint sanity you enforce in the grid's checker.
        }
    }
}