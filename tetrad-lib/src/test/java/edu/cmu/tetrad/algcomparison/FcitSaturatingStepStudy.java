package edu.cmu.tetrad.algcomparison;

import edu.cmu.tetrad.algcomparison.algorithm.oracle.pag.Fcit;
import edu.cmu.tetrad.algcomparison.independence.MSeparationTest;
import edu.cmu.tetrad.algcomparison.score.MSepScore;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.ArrayList;
import java.util.List;

/**
 * How often does FCIT return the wrong PAG when it runs with no correctness gate and no saturating step?
 *
 * <p>Oracle study. Each repetition draws a random DAG with latent confounders, takes its true PAG, and runs the
 * FCIT wrapper on an m-separation oracle for that DAG under three settings of the correctness parameters:
 * <ol>
 * <li>fcitCorrectness = 3 (Neither), useSaturatingStep = true. The configuration proved correct in the oracle,
 * so this arm is the control: any miss here is a bug or a wrong comparison, not a finding.</li>
 * <li>fcitCorrectness = 3 (Neither), useSaturatingStep = false. The arm in question: the single-edge fixpoint
 * alone, with no warrant.</li>
 * <li>fcitCorrectness = 1 (Guarantee I-map). The wrapper default, which never runs the saturating step; its
 * no-stall property is conjectured, not proved. Switch off with {@link #INCLUDE_IMAP_ARM}.</li>
 * </ol>
 * A miss is classified by its skeleton: extra adjacencies (edges the search failed to remove, which is what a
 * missing saturating step is expected to produce), missing adjacencies, or orientation only.
 *
 * <p>An oracle result says nothing about data. It bounds from below how often the step is needed: on data a
 * removal can also be blocked by a wrong test verdict.
 *
 * <p>Run {@link #main} from the IDE. Optional arguments, in order: repetitions, total nodes, latent confounders,
 * edges, base seed. A repetition is reproduced by its seed, which is the base seed plus its index.
 *
 * @author josephramsey
 * @author Claude
 */
public final class FcitSaturatingStepStudy {

    /**
     * Whether to run the I-map gate arm as well. It is the slowest of the three.
     */
    private static final boolean INCLUDE_IMAP_ARM = true;

    private FcitSaturatingStepStudy() {
    }

    /**
     * Runs the study.
     *
     * @param args optional: repetitions (200), total nodes (12), latent confounders (3), edges (18), base seed
     *             (7000).
     * @throws InterruptedException if interrupted.
     */
    public static void main(String[] args) throws InterruptedException {
        int reps = arg(args, 0, 200);
        int numNodes = arg(args, 1, 12);
        int numLatents = arg(args, 2, 3);
        int numEdges = arg(args, 3, 18);
        int baseSeed = arg(args, 4, 7000);

        List<Arm> arms = new ArrayList<>();
        arms.add(new Arm("neither, saturating step ON (control)", 3, true));
        arms.add(new Arm("neither, saturating step OFF", 3, false));
        if (INCLUDE_IMAP_ARM) arms.add(new Arm("I-map gate (wrapper default)", 1, true));

        System.out.println("FCIT saturating-step study, m-separation oracle, BOSS start. " + reps
                           + " random DAGs: " + numNodes + " nodes of which " + numLatents + " latent, "
                           + numEdges + " edges, seeds " + baseSeed + " to " + (baseSeed + reps - 1) + ".");

        for (int rep = 0; rep < reps; rep++) {
            int seed = baseSeed + rep;
            RandomUtil.getInstance().setSeed(seed);
            Graph dag = RandomGraph.randomDag(numNodes, numLatents, numEdges, 100, 100, 100, false);
            Graph truePag = GraphTransforms.dagToPag(dag, false);

            StringBuilder line = new StringBuilder("seed " + seed + ":");

            for (Arm arm : arms) {
                long start = System.currentTimeMillis();
                Graph est = run(dag, arm);
                long ms = System.currentTimeMillis() - start;
                arm.record(seed, truePag, est, ms);
                line.append("  [").append(arm.lastVerdict).append(", ").append(ms).append(" ms]");
            }

            System.out.println(line);

            if ((rep + 1) % 25 == 0 || rep == reps - 1) {
                System.out.println("--- after " + (rep + 1) + " DAGs ---");
                for (Arm arm : arms) System.out.println(arm.summary());
            }
        }

        for (Arm arm : arms) {
            if (!arm.wrongSeeds.isEmpty()) System.out.println("Wrong seeds, " + arm.name + ": " + arm.wrongSeeds);
        }
    }

    private static Graph run(Graph dag, Arm arm) throws InterruptedException {
        Parameters params = new Parameters();
        params.set(Params.FCIT_STARTS_WITH, 1);           // BOSS
        params.set(Params.USE_BES_DEFAULT_TRUE, true);    // needed for an exact oracle start
        params.set(Params.FCIT_CORRECTNESS, arm.correctness);
        params.set(Params.USE_SATURATING_STEP, arm.saturatingStep);
        params.set(Params.VERBOSE, false);

        // As GeneralAlgorithmRunner does for a graph source: the graph on the test and score wrappers, null data.
        MSeparationTest test = new MSeparationTest();
        test.setGraph(dag);
        MSepScore score = new MSepScore();
        score.setGraph(dag);

        // FCIT reports its gate summaries and timings through the logger even when not verbose; keep them out of
        // the study's output.
        boolean wasLogging = TetradLogger.getInstance().isLogging();
        TetradLogger.getInstance().setLogging(false);
        try {
            return new Fcit(test, score).search(null, params);
        } finally {
            TetradLogger.getInstance().setLogging(wasLogging);
        }
    }

    private static int adjacenciesNotIn(Graph from, Graph other) {
        int count = 0;
        for (Edge e : from.getEdges()) {
            Node a = other.getNode(e.getNode1().getName());
            Node b = other.getNode(e.getNode2().getName());
            if (a == null || b == null || !other.isAdjacentTo(a, b)) count++;
        }
        return count;
    }

    private static int arg(String[] args, int index, int fallback) {
        return args.length > index ? Integer.parseInt(args[index]) : fallback;
    }

    private static final class Arm {
        private final String name;
        private final int correctness;
        private final boolean saturatingStep;
        private final List<Integer> wrongSeeds = new ArrayList<>();
        private int runs = 0, extraAdjacency = 0, missingAdjacency = 0, orientationOnly = 0;
        private long totalMs = 0;
        private String lastVerdict = "";

        private Arm(String name, int correctness, boolean saturatingStep) {
            this.name = name;
            this.correctness = correctness;
            this.saturatingStep = saturatingStep;
        }

        private void record(int seed, Graph truePag, Graph est, long ms) {
            runs++;
            totalMs += ms;

            if (truePag.equals(est)) {
                lastVerdict = "ok";
                return;
            }

            wrongSeeds.add(seed);
            int extra = adjacenciesNotIn(est, truePag);
            int missing = adjacenciesNotIn(truePag, est);

            // A graph with both extra and missing adjacencies is counted under each.
            if (extra > 0) extraAdjacency++;
            if (missing > 0) missingAdjacency++;
            if (extra == 0 && missing == 0) orientationOnly++;
            lastVerdict = "WRONG +" + extra + " adj, -" + missing + " adj";
        }

        private String summary() {
            int wrong = wrongSeeds.size();
            return String.format("%-40s wrong %d of %d (%.1f%%): extra adjacencies in %d, missing in %d, "
                                 + "orientation only in %d; mean %.0f ms", name, wrong, runs,
                    runs == 0 ? 0.0 : 100.0 * wrong / runs, extraAdjacency, missingAdjacency, orientationOnly,
                    runs == 0 ? 0.0 : (double) totalMs / runs);
        }
    }
}
