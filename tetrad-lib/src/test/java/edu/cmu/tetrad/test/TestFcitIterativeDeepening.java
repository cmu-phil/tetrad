package edu.cmu.tetrad.test;

import edu.cmu.tetrad.algcomparison.statistic.*;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataTransforms;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.search.Fcit;
import edu.cmu.tetrad.search.Fcit2;
import edu.cmu.tetrad.search.FcitZm;
import edu.cmu.tetrad.search.FcitZm2;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Parameters;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Empirical comparison of the single-pass main loop against the iterative-deepening
 * schedule, for {@link Fcit} and {@link FcitZm}.
 *
 * <p>Each replication generates a random DAG with latent confounders, simulates
 * linear-Gaussian data, restricts to the measured variables, and runs the same
 * configuration twice -- deepening off, then deepening on -- against the same data
 * and the same true PAG. Everything but the schedule is held fixed, so a difference
 * in the reported statistics is attributable to the schedule alone.</p>
 *
 * <p>Reported per arm: adjacency precision/recall, arrowhead precision/recall,
 * whether the output is a legal PAG, elapsed wall time, and the number of edges in
 * the estimated graph. The summary prints paired means and the win/loss/tie counts
 * over replications, which is the comparison that matters -- the arms see identical
 * data, so the paired difference has far less variance than the marginal means.</p>
 *
 * <p>Run {@link #main(String[])} directly.</p>
 */
public class TestFcitIterativeDeepening {

    /**
     * Number of measured variables.
     */
    private static final int NUM_MEASURES = 20;
    /**
     * Number of latent confounders.
     */
    private static final int NUM_LATENTS = 4;
    /**
     * Average degree of the generating DAG.
     */
    private static final int AVG_DEGREE = 4;
    /**
     * Sample size.
     */
    private static final int SAMPLE_SIZE = 1000;
    /**
     * Number of replications.
     */
    private static final int NUM_REPS = 10;
    /**
     * Alpha for the Fisher Z test.
     */
    private static final double ALPHA = 0.01;
    /**
     * Penalty discount for the SEM BIC score.
     */
    private static final double PENALTY_DISCOUNT = 2.0;
    /**
     * Conditioning-set cap. The deepening arm ramps 0..DEPTH; the single-pass arm
     * runs once at DEPTH. A finite cap is used so both arms have the same ceiling
     * and the comparison is not confounded by the unlimited-ramp heuristic.
     */
    private static final int DEPTH = 5;

    /**
     * Runs the comparison and prints a per-replication table and a paired summary.
     *
     * @param args ignored
     * @throws Exception if a simulation or search fails
     */
    public static void main(String[] args) throws Exception {
        System.out.printf("FCIT iterative-deepening comparison%n");
        System.out.printf("%d+%d:%d:%d, alpha=%s, penalty=%s, depth=%d, %d reps%n%n",
                NUM_MEASURES, NUM_LATENTS, AVG_DEGREE, SAMPLE_SIZE,
                ALPHA, PENALTY_DISCOUNT, DEPTH, NUM_REPS);

        runArmPair("Fcit", false);
        System.out.println();
        runArmPair("FcitZm", true);
    }

    private static void runArmPair(String label, boolean zm) throws Exception {
        List<Result> off = new ArrayList<>();
        List<Result> on = new ArrayList<>();

        System.out.println("=== " + label + " ===");
        System.out.printf("%-4s %-8s %6s %6s %6s %6s %6s %8s%n",
                "rep", "arm", "adjP", "adjR", "ahP", "ahR", "legal", "ms");

        for (int rep = 1; rep <= NUM_REPS; rep++) {
            long seed = 838837L + 17L * rep;

            Graph trueDag = RandomGraph.randomGraph(
                    NUM_MEASURES + NUM_LATENTS, NUM_LATENTS,
                    AVG_DEGREE * (NUM_MEASURES + NUM_LATENTS) / 2,
                    100, 100, 100, false, seed);

            SemPm pm = new SemPm(trueDag);
            SemIm im = new SemIm(pm);
            DataSet full = im.simulateData(SAMPLE_SIZE, false);
            DataSet data = DataTransforms.restrictToMeasured(full);

            Graph truePag = GraphTransforms.dagToPag(trueDag, false);

            Result r0 = runOne(zm, data, false);
            Result r1 = runOne(zm, data, true);

            r0.score(trueDag, truePag, data);
            r1.score(trueDag, truePag, data);

            off.add(r0);
            on.add(r1);

            print(rep, "single", r0);
            print(rep, "deepen", r1);
        }

        summarize(label, off, on);
    }

    private static Result runOne(boolean zm, DataSet data, boolean deepening) throws Exception {
        IndTestFisherZ test = new IndTestFisherZ(data, ALPHA);
        SemBicScore score = new SemBicScore(data, true);
        score.setPenaltyDiscount(PENALTY_DISCOUNT);

        long start = System.currentTimeMillis();
        Graph est;

        if (zm) {
            FcitZm2 search = new FcitZm2(test, score);
            search.setDepth(DEPTH);
            search.setVerbose(false);
            search.setIterativeDeepening(deepening);
            est = search.search();
        } else {
            Fcit2 search = new Fcit2(test, score);
            search.setDepth(DEPTH);
            search.setVerbose(false);
            search.setIterativeDeepening(deepening);
            est = search.search();
        }

        long elapsed = System.currentTimeMillis() - start;
        return new Result(est, elapsed);
    }

    private static void print(int rep, String arm, Result r) {
        System.out.printf(Locale.US, "%-4d %-8s %6.3f %6.3f %6.3f %6.3f %6.0f %8d%n",
                rep, arm, r.adjP, r.adjR, r.ahP, r.ahR, r.legal, r.ms);
    }

    private static void summarize(String label, List<Result> off, List<Result> on) {
        System.out.printf("%n--- %s paired summary over %d reps ---%n", label, off.size());
        System.out.printf("%-8s %8s %8s %9s %6s %6s %6s%n",
                "stat", "single", "deepen", "diff", "win", "loss", "tie");

        report("adjP", off, on, r -> r.adjP);
        report("adjR", off, on, r -> r.adjR);
        report("ahP", off, on, r -> r.ahP);
        report("ahR", off, on, r -> r.ahR);
        report("legal", off, on, r -> r.legal);
        report("ms", off, on, r -> (double) r.ms);
    }

    private static void report(String name, List<Result> off, List<Result> on,
                               java.util.function.ToDoubleFunction<Result> f) {
        double sumOff = 0.0, sumOn = 0.0;
        int win = 0, loss = 0, tie = 0;

        for (int i = 0; i < off.size(); i++) {
            double a = f.applyAsDouble(off.get(i));
            double b = f.applyAsDouble(on.get(i));
            sumOff += a;
            sumOn += b;

            if (b > a + 1e-9) win++;
            else if (b < a - 1e-9) loss++;
            else tie++;
        }

        int n = off.size();
        System.out.printf(Locale.US, "%-8s %8.3f %8.3f %+9.3f %6d %6d %6d%n",
                name, sumOff / n, sumOn / n, (sumOn - sumOff) / n, win, loss, tie);
    }

    /**
     * One arm's estimated graph plus its scored statistics.
     */
    private static final class Result {
        private final Graph est;
        private final long ms;
        private double adjP, adjR, ahP, ahR, legal;

        private Result(Graph est, long ms) {
            this.est = est;
            this.ms = ms;
        }

        private void score(Graph trueDag, Graph truePag, DataSet data) {
            Parameters params = new Parameters();

            // The estimated graph carries the data's Node objects; the true PAG carries
            // the generating DAG's. The confusion classes key on node identity, so
            // without this every adjacency reads as a miss.
            Graph e = edu.cmu.tetrad.graph.GraphUtils.replaceNodes(est, truePag.getNodes());

            this.adjP = new AdjacencyPrecision().getValue(trueDag, truePag, e, data, params);
            this.adjR = new AdjacencyRecall().getValue(trueDag, truePag, e, data, params);
            this.ahP = new ArrowheadPrecision().getValue(trueDag, truePag, e, data, params);
            this.ahR = new ArrowheadRecall().getValue(trueDag, truePag, e, data, params);
            this.legal = new LegalPag().getValue(trueDag, truePag, e, data, params);
        }
    }
}
