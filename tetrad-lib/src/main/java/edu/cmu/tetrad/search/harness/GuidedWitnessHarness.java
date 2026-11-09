///////////////////////////////////////////////////////////////////////////////
// GuidedWitnessHarness.java                                                 //
//                                                                           //
// Oracle head-to-head for FCIT-SL candidate generators:                     //
//   BASE     the current default (class walk within-class generator)        //
//   GUIDED   the guided witness construction, legacy fallback ON            //
//   GUIDED0  the guided witness construction, fallback OFF (unaided reach)  //
//                                                                           //
// Per condition (numMeasures x avgDegree x numLatents), numRuns random DAGs //
// are drawn; each arm runs the REAL FcitSl against an MsepTest oracle and   //
// a GraphScore, starting from BOSS. The output is compared to the true PAG //
// (dagToPag). Each run is executed on a worker thread with a hard wall     //
// timeout; a timed-out run is recorded as such and its stats left blank.   //
//                                                                           //
// The question this harness answers: does the guided construction reach     //
// the same PAGs as the class walk while scaling past the walk's node-count  //
// wall -- and how often does its unaided reach (GUIDED0) suffice, i.e. how //
// often is the fallback actually consulted?                                 //
//                                                                           //
// Usage:                                                                    //
//   java -cp tetrad-current.jar \                                          //
//        edu.cmu.tetrad.search.harness.GuidedWitnessHarness                 //
//        [--numRuns 10] [--seed 42] [--timeoutSec 120] [--out results.tsv] //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.harness;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.search.FcitSl;
import edu.cmu.tetrad.search.FcitSl2;
import edu.cmu.tetrad.search.score.GraphScore;
import edu.cmu.tetrad.search.test.MsepTest;
import edu.cmu.tetrad.util.RandomUtil;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Oracle head-to-head for FCIT-SL candidate generators: the class-walk default versus the
 * guided witness construction, with and without the legacy fallback. See the file banner
 * for semantics.
 */
public final class GuidedWitnessHarness {

    private static final boolean EXCLUDE_SELECTION_BIAS = true;

    /** (numMeasures, avgDegree, numLatents) conditions, small to large. */
    private static final int[][] CONDITIONS = {
            {10, 3, 2},
            {15, 3, 3},
            {15, 4, 3},
            {20, 3, 4},
            {20, 4, 4},
            {25, 3, 5},
    };

    private GuidedWitnessHarness() {
    }

    /**
     * Entry point.
     *
     * @param args see the file banner.
     * @throws Exception if anything goes irrecoverably wrong.
     */
    public static void main(String[] args) throws Exception {
        int numRuns = 10;
        long seed = 42L;
        long timeoutSec = 120L;
        String out = null;
        int minNodes = 0;
        int maxNodes = Integer.MAX_VALUE;

        for (int i = 0; i < args.length - 1; i++) {
            switch (args[i]) {
                case "--numRuns" -> numRuns = Integer.parseInt(args[i + 1]);
                case "--seed" -> seed = Long.parseLong(args[i + 1]);
                case "--timeoutSec" -> timeoutSec = Long.parseLong(args[i + 1]);
                case "--out" -> out = args[i + 1];
                case "--minNodes" -> minNodes = Integer.parseInt(args[i + 1]);
                case "--maxNodes" -> maxNodes = Integer.parseInt(args[i + 1]);
            }
        }

        List<String> lines = new ArrayList<>();
        String header = String.join("\t", "nodes", "avgDeg", "latents", "run", "arm",
                "ms", "exact", "timedOut", "error");
        lines.add(header);
        System.out.println(header);

        ExecutorService exec = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "fcitsl-run");
            t.setDaemon(true);
            return t;
        });

        try {
            for (int[] cond : CONDITIONS) {
                int nodes = cond[0], avgDeg = cond[1], latents = cond[2];
                if (nodes < minNodes || nodes > maxNodes) continue;
                int edges = nodes * avgDeg / 2;

                int[] exact = new int[3];
                long[] totMs = new long[3];
                int[] done = new int[3];
                int[] tOut = new int[3];

                for (int run = 0; run < numRuns; run++) {
                    RandomUtil.getInstance().setSeed(seed + 1000L * run
                            + 100_000L * nodes + 17L * avgDeg);

                    Graph dag = RandomGraph.randomGraph(nodes, latents, edges,
                            100, 100, 100, false);
                    Graph truePag = GraphTransforms.dagToPag(dag, EXCLUDE_SELECTION_BIAS);

                    for (int arm = 0; arm < 3; arm++) {
                        String armName = switch (arm) {
                            case 0 -> "BASE";
                            case 1 -> "GUIDED";
                            default -> "GUIDED0";
                        };

                        long start = System.nanoTime();
                        String err = "";
                        boolean timedOut = false;
                        Graph est = null;

                        final int _arm = arm;
                        final Graph _dag = dag;
                        Future<Graph> fut = exec.submit(() -> runArm(_dag, _arm));

                        try {
                            est = fut.get(timeoutSec, TimeUnit.SECONDS);
                        } catch (TimeoutException te) {
                            fut.cancel(true);
                            timedOut = true;
                        } catch (ExecutionException ee) {
                            err = String.valueOf(ee.getCause());
                        }

                        long ms = (System.nanoTime() - start) / 1_000_000L;
                        boolean isExact = est != null && est.equals(truePag);

                        if (!timedOut && err.isEmpty()) {
                            done[arm]++;
                            totMs[arm] += ms;
                            if (isExact) exact[arm]++;
                        }
                        if (timedOut) tOut[arm]++;

                        String line = String.join("\t",
                                String.valueOf(nodes), String.valueOf(avgDeg),
                                String.valueOf(latents), String.valueOf(run), armName,
                                timedOut ? "" : String.valueOf(ms),
                                timedOut || !err.isEmpty() ? "" : (isExact ? "1" : "0"),
                                timedOut ? "1" : "0", err);
                        lines.add(line);
                        System.out.println(line);
                    }
                }

                for (int arm = 0; arm < 3; arm++) {
                    String armName = switch (arm) {
                        case 0 -> "BASE";
                        case 1 -> "GUIDED";
                        default -> "GUIDED0";
                    };
                    System.out.printf("## %d:%d+%d  %-8s exact %d/%d, timeouts %d, mean %.0f ms%n",
                            nodes, avgDeg, latents, armName, exact[arm], done[arm], tOut[arm],
                            done[arm] == 0 ? Double.NaN : totMs[arm] / (double) done[arm]);
                }
            }
        } finally {
            exec.shutdownNow();
        }

        if (out != null) {
            try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(Path.of(out)))) {
                lines.forEach(pw::println);
            }
            System.out.println("Wrote " + out);
        }
    }

    // ========================================================================
    // THE ONE PLACE THAT ENCODES THE FcitSl API -- verify against FcitSl.java.
    // ========================================================================
    private static Graph runArm(Graph dag, int arm) throws InterruptedException {
        MsepTest oracle = new MsepTest(dag);
        GraphScore score = new GraphScore(dag);

        FcitSl2 fcit = new FcitSl2(oracle, score);
        fcit.setKnowledge(new Knowledge());
        fcit.setCompleteRuleSetUsed(true);
        // MsepTest oracle => the constructor selects GRASP with GraphScore.
        fcit.setExcludeSelectionBias(EXCLUDE_SELECTION_BIAS);
        fcit.setVerbose(false);

        if (arm >= 1) fcit.setUseGuidedConstruction(true);
        if (arm == 2) fcit.setGuidedFallbackToLegacy(false);

        return fcit.search();
    }
}
