///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.algcomparison;

import edu.cmu.tetrad.algcomparison.algorithm.oracle.pag.Fcit;
import edu.cmu.tetrad.algcomparison.independence.MSeparationTest;
import edu.cmu.tetrad.algcomparison.score.MSepScore;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What do FCIT's two sweep caps cost in correctness, and what do they buy in time?
 *
 * <p>Oracle study. FCIT's separator sweep makes one recursive-blocking call per not-followed subset, on the live
 * view and then on up to sixteen mark-flip views, for every edge it fails to separate. That is finite but
 * exponential in the not-followed pool, and it is what makes a run on twenty-odd variables look hung. The two
 * caps cut it: {@link Params#FCIT_SWEEP_NF_MAX_SIZE} bounds the size of the not-followed subsets, and
 * {@link Params#FCIT_MAX_MARK_FLIPS} bounds the number of mark-flip views. Both shrink the candidate family, so
 * a capped search can miss a separator the full search finds; a capped configuration is not the certified one.
 *
 * <p>Each repetition draws a random DAG with latent confounders, takes its true PAG, and runs the FCIT wrapper
 * on an m-separation oracle for that DAG once per arm. The first arm is the uncapped search and is the
 * reference. Each run has a wall-clock limit; a run that passes it is interrupted and counted as timed out,
 * not as wrong. For each arm the summary gives how many runs finished, how many of those returned something
 * other than the true PAG, how many differ from the uncapped arm's answer where both finished, and the mean and
 * largest time of the finished runs.
 *
 * <p>An oracle result says nothing about data. On data a cap can also change which of several test-confirmed
 * separators is found first, and so change orientations, without any separator being missed.
 *
 * <p>Run {@link #main} from the IDE. Optional arguments, in order: repetitions, total nodes, latent confounders,
 * edges, base seed, limit per run in seconds, fcitCorrectness, useSaturatingStep (true or false), and the arms
 * as a comma-separated list of notFollowedCap:markFlipCap pairs, the uncapped arm first. A repetition is
 * reproduced by its seed, which is the base seed plus its index.
 *
 * @author josephramsey
 * @author Claude
 */
public final class FcitSweepCapStudy {

    /**
     * The default arms, as notFollowedCap:markFlipCap. The first is the uncapped search (-1 is unlimited, and 16
     * mark-flip views is FCIT's own default).
     */
    private static final String DEFAULT_ARMS = "-1:16,3:16,2:16,-1:4,3:4,2:2";

    private FcitSweepCapStudy() {
    }

    /**
     * Runs the study.
     *
     * @param args optional: repetitions (30), total nodes (16), latent confounders (4), edges (28), base seed
     *             (7000), limit per run in seconds (60), fcitCorrectness (3), useSaturatingStep (false), arms.
     * @throws InterruptedException if interrupted.
     */
    public static void main(String[] args) throws InterruptedException {
        int reps = arg(args, 0, 30);
        int numNodes = arg(args, 1, 16);
        int numLatents = arg(args, 2, 4);
        int numEdges = arg(args, 3, 28);
        int baseSeed = arg(args, 4, 7000);
        long limitMs = 1000L * arg(args, 5, 60);
        int correctness = arg(args, 6, 3);
        boolean saturatingStep = args.length > 7 && Boolean.parseBoolean(args[7]);

        List<Arm> arms = new ArrayList<>();
        for (String spec : (args.length > 8 ? args[8] : DEFAULT_ARMS).split(",")) {
            String[] parts = spec.trim().split(":");
            arms.add(new Arm(Integer.parseInt(parts[0]), Integer.parseInt(parts[1])));
        }

        System.out.println("FCIT sweep-cap study, m-separation oracle, BOSS start, fcitCorrectness " + correctness
                           + ", saturating step " + (saturatingStep ? "on" : "off") + ". " + reps + " random DAGs: "
                           + numNodes + " nodes of which " + numLatents + " latent, " + numEdges + " edges, seeds "
                           + baseSeed + " to " + (baseSeed + reps - 1) + ". Limit per run " + (limitMs / 1000)
                           + " s. Arms are not-followed cap : mark-flip cap; the first is the reference.");

        boolean wasLogging = TetradLogger.getInstance().isLogging();
        TetradLogger.getInstance().setLogging(false);

        try {
            for (int rep = 0; rep < reps; rep++) {
                int seed = baseSeed + rep;
                RandomUtil.getInstance().setSeed(seed);
                Graph dag = RandomGraph.randomDag(numNodes, numLatents, numEdges, 100, 100, 100, false);
                Graph truePag = GraphTransforms.dagToPag(dag, false);

                StringBuilder line = new StringBuilder("seed " + seed + ":");
                Graph reference = null;

                for (int i = 0; i < arms.size(); i++) {
                    Arm arm = arms.get(i);
                    long start = System.currentTimeMillis();
                    Graph est = runWithLimit(dag, arm, correctness, saturatingStep, limitMs);
                    long ms = System.currentTimeMillis() - start;
                    if (i == 0) reference = est;
                    arm.record(seed, truePag, est, reference, ms);
                    line.append("  [").append(arm.name()).append(" ").append(arm.lastVerdict).append("]");
                }

                System.out.println(line);

                if ((rep + 1) % 10 == 0 || rep == reps - 1) {
                    System.out.println("--- after " + (rep + 1) + " DAGs ---");
                    for (Arm arm : arms) System.out.println(arm.summary());
                }
            }
        } finally {
            TetradLogger.getInstance().setLogging(wasLogging);
        }

        for (Arm arm : arms) {
            if (!arm.wrongSeeds.isEmpty()) System.out.println("Wrong seeds, " + arm.name() + ": " + arm.wrongSeeds);
            if (!arm.differSeeds.isEmpty()) {
                System.out.println("Seeds differing from the reference, " + arm.name() + ": " + arm.differSeeds);
            }
        }

        // A run interrupted at the limit can leave pool tasks finishing in the background; do not wait for them.
        System.exit(0);
    }

    /**
     * Runs one arm on its own thread and interrupts it at the limit.
     *
     * @return the estimated PAG, or null if the run was stopped at the limit or failed.
     */
    private static Graph runWithLimit(Graph dag, Arm arm, int correctness, boolean saturatingStep, long limitMs)
            throws InterruptedException {
        Graph[] result = new Graph[1];

        Thread thread = new Thread(() -> {
            try {
                Parameters params = new Parameters();
                params.set(Params.FCIT_STARTS_WITH, 1);           // BOSS
                params.set(Params.USE_BES_DEFAULT_TRUE, true);    // needed for an exact oracle start
                params.set(Params.FCIT_CORRECTNESS, correctness);
                params.set(Params.USE_SATURATING_STEP, saturatingStep);
                params.set(Params.FCIT_SWEEP_NF_MAX_SIZE, arm.notFollowedCap);
                params.set(Params.FCIT_MAX_MARK_FLIPS, arm.markFlipCap);
                params.set(Params.VERBOSE, false);

                // As GeneralAlgorithmRunner does for a graph source: the graph on the wrappers, null data.
                MSeparationTest test = new MSeparationTest();
                test.setGraph(dag);
                MSepScore score = new MSepScore();
                score.setGraph(dag);

                result[0] = new Fcit(test, score).search(null, params);
            } catch (Throwable stoppedOrFailed) {
                // An interrupt at the limit arrives here, sometimes wrapped; either way there is no result.
                result[0] = null;
            }
        }, "fcit-sweep-cap-study");

        thread.setDaemon(true);
        thread.start();
        thread.join(limitMs);

        if (thread.isAlive()) {
            thread.interrupt();
            thread.join(30_000L);
            return null;
        }

        return result[0];
    }

    private static int arg(String[] args, int index, int fallback) {
        return args.length > index ? Integer.parseInt(args[index]) : fallback;
    }

    private static final class Arm {
        private final int notFollowedCap;
        private final int markFlipCap;
        private final List<Integer> wrongSeeds = new ArrayList<>();
        private final List<Integer> differSeeds = new ArrayList<>();
        private final List<Long> times = new ArrayList<>();
        private int runs = 0, timedOut = 0, comparedWithReference = 0;
        private String lastVerdict = "";

        private Arm(int notFollowedCap, int markFlipCap) {
            this.notFollowedCap = notFollowedCap;
            this.markFlipCap = markFlipCap;
        }

        private String name() {
            return notFollowedCap + ":" + markFlipCap;
        }

        private void record(int seed, Graph truePag, Graph est, Graph reference, long ms) {
            runs++;

            if (est == null) {
                timedOut++;
                lastVerdict = "timed out";
                return;
            }

            times.add(ms);
            boolean wrong = !truePag.equals(est);
            if (wrong) wrongSeeds.add(seed);

            boolean differs = false;
            if (reference != null) {
                comparedWithReference++;
                differs = !reference.equals(est);
                if (differs) differSeeds.add(seed);
            }

            lastVerdict = (wrong ? "WRONG" : "ok") + (differs ? ", differs" : "") + ", " + ms + " ms";
        }

        private String summary() {
            List<Long> sorted = new ArrayList<>(times);
            Collections.sort(sorted);
            long total = 0;
            for (long t : sorted) total += t;

            return String.format("%-7s finished %d of %d (timed out %d); wrong %d; differs from reference %d of %d; "
                                 + "mean %.0f ms, largest %d ms", name(), sorted.size(), runs, timedOut,
                    wrongSeeds.size(), differSeeds.size(), comparedWithReference,
                    sorted.isEmpty() ? 0.0 : (double) total / sorted.size(),
                    sorted.isEmpty() ? 0L : sorted.get(sorted.size() - 1));
        }
    }
}
