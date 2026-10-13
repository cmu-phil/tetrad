package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.search.test.IndTestFisherZ;
import edu.cmu.tetrad.search.utils.MagToPag;
import edu.cmu.tetrad.search.work_in_progress.LvBoss;
import edu.cmu.tetrad.search.work_in_progress.MagRicfBicScorer;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.RandomUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Finite-sample comparison of LV-BOSS against BFCI, GFCI, and FCIT on simulated latent-variable
 * data: same data to every algorithm, PAG compared to the true MAG's PAG.
 *
 * <p>Hand-run harness with a main (per the project's test-design convention); intended for Joe to
 * run at scale while watching the output. Arguments, all optional, in order:
 * {@code reps measures latents edges N numStarts penaltyDiscount alpha coefLow verbosePerRep}.
 * Defaults: 20 reps, 10 measures, 2 latents, 15 edges, N = 2000, 2 starts, penalty discount 1,
 * alpha 0.01, coefficients of either sign with magnitude in [0.3, 1.0], per-rep lines off.</p>
 *
 * <p>Metrics against the true PAG: adjacency precision, recall and F1; endpoint accuracy (over
 * adjacencies present in both graphs, the fraction of the four endpoint marks -- two per shared
 * edge -- matching the true PAG); and wall time. LV-BOSS output that is not a legal MAG (possible
 * when the score prefers a non-maximal graph) is counted in its own column, scored on adjacencies
 * only, and skipped for endpoint accuracy.</p>
 */
public class LvBossStudy {

    public static void main(String[] args) throws Exception {
        int reps = arg(args, 0, 20);
        int measures = arg(args, 1, 10);
        int latents = arg(args, 2, 2);
        int edges = arg(args, 3, 15);
        int n = arg(args, 4, 2000);
        int numStarts = arg(args, 5, 2);
        double penalty = darg(args, 6, 1.0);
        double alpha = darg(args, 7, 0.01);
        double coefLow = darg(args, 8, 0.3);
        boolean verbose = args.length > 9 && Boolean.parseBoolean(args[9]);

        System.out.println("LV-BOSS vs BFCI / GFCI / FCIT -- reps " + reps + ", " + measures + " measures, "
                           + latents + " latents, " + edges + " edges, N " + n + ", " + numStarts
                           + " starts, penalty " + penalty + ", alpha " + alpha + ", |coef| in ["
                           + coefLow + ", 1.0]");

        RandomUtil.getInstance().setSeed(20261013L);

        String[] algos = {"LV-BOSS", "BFCI", "GFCI", "FCIT"};
        Map<String, double[]> sums = new LinkedHashMap<>(); // adjP, adjR, adjF1, endpt, ms, endptCount
        for (String a : algos) sums.put(a, new double[6]);
        int lvBossNotMag = 0;
        int done = 0;

        for (int rep = 0; rep < reps; rep++) {
            Graph dagL = RandomGraph.randomGraph(measures, latents, edges, 100, 100, 100, false);
            Graph trueMag = GraphTransforms.dagToMag(dagL);
            if (trueMag.getNumEdges() == 0) continue;
            done++;

            Parameters params = new Parameters();
            params.set(Params.COEF_LOW, coefLow);
            params.set(Params.COEF_HIGH, 1.0);
            SemIm im = new SemIm(new SemPm(dagL), params);
            DataSet full = im.simulateData(n, false);

            List<Node> keep = new ArrayList<>();
            for (Node node : trueMag.getNodes()) keep.add(full.getVariable(node.getName()));
            DataSet data = full.subsetColumns(keep);
            ICovarianceMatrix cov = new CovarianceMatrix(data);

            Graph truePag = new MagToPag(GraphUtils.replaceNodes(trueMag, cov.getVariables())).convert(false, true);

            for (String algo : algos) {
                long t0 = System.currentTimeMillis();
                Graph out;
                boolean legalMag = true;

                switch (algo) {
                    case "LV-BOSS" -> {
                        MagRicfBicScorer scorer = new MagRicfBicScorer(cov);
                        scorer.setPenaltyDiscount(penalty);
                        LvBoss lvBoss = new LvBoss(scorer);
                        lvBoss.setNumStarts(numStarts);
                        lvBoss.setSeed(1000L + rep);
                        Graph mag = lvBoss.search();
                        legalMag = mag.paths().isLegalMag();
                        if (!legalMag) lvBossNotMag++;
                        out = legalMag ? new MagToPag(mag).convert(false, true) : mag;
                    }
                    case "BFCI" -> {
                        SemBicScore score = new SemBicScore(cov);
                        score.setPenaltyDiscount(penalty);
                        out = new Bfci(new IndTestFisherZ(cov, alpha), score).search();
                    }
                    case "GFCI" -> {
                        SemBicScore score = new SemBicScore(cov);
                        score.setPenaltyDiscount(penalty);
                        out = new Gfci(new IndTestFisherZ(cov, alpha), score).search();
                    }
                    case "FCIT" -> {
                        SemBicScore score = new SemBicScore(cov);
                        score.setPenaltyDiscount(penalty);
                        Fcit fcit = new Fcit(new IndTestFisherZ(cov, alpha), score);
                        fcit.setVerbose(false);
                        out = fcit.search();
                    }
                    default -> throw new IllegalStateException(algo);
                }

                long ms = System.currentTimeMillis() - t0;
                out = GraphUtils.replaceNodes(out, truePag.getNodes());

                double[] m = metrics(truePag, out, legalMag);
                double[] s = sums.get(algo);
                s[0] += m[0];
                s[1] += m[1];
                s[2] += m[2];
                if (legalMag) {
                    s[3] += m[3];
                    s[5] += 1;
                }
                s[4] += ms;

                if (verbose) {
                    System.out.printf("rep %d %-8s adjP %.3f adjR %.3f adjF1 %.3f endpt %s  %d ms%s%n",
                            rep, algo, m[0], m[1], m[2], legalMag ? String.format("%.3f", m[3]) : "  -- ",
                            ms, legalMag ? "" : "  [not a legal MAG]");
                }
            }
        }

        System.out.println();
        System.out.printf("%-8s %6s %6s %6s %6s %10s%n", "algo", "adjP", "adjR", "adjF1", "endpt", "ms/rep");
        for (String algo : algos) {
            double[] s = sums.get(algo);
            System.out.printf("%-8s %6.3f %6.3f %6.3f %6.3f %10.0f%n",
                    algo, s[0] / done, s[1] / done, s[2] / done,
                    s[5] > 0 ? s[3] / s[5] : Double.NaN, s[4] / done);
        }
        System.out.println("reps scored: " + done + "; LV-BOSS outputs that were not legal MAGs: "
                           + lvBossNotMag + " (adjacency-only in those reps)");
    }

    /**
     * {adjP, adjR, adjF1, endpointAccuracy}; endpoint accuracy over adjacencies in both graphs, two
     * marks per shared edge, and NaN when fewer than one shared edge or the output is not a PAG.
     */
    private static double[] metrics(Graph truePag, Graph out, boolean endpoints) {
        int tp = 0, fp = 0, fn = 0;
        int endpointMatches = 0, endpointTotal = 0;

        for (Edge e : out.getEdges()) {
            if (truePag.isAdjacentTo(truePag.getNode(e.getNode1().getName()),
                    truePag.getNode(e.getNode2().getName()))) {
                tp++;
            } else {
                fp++;
            }
        }

        for (Edge e : truePag.getEdges()) {
            Node a = out.getNode(e.getNode1().getName());
            Node b = out.getNode(e.getNode2().getName());

            if (!out.isAdjacentTo(a, b)) {
                fn++;
            } else if (endpoints) {
                Edge oe = out.getEdge(a, b);
                endpointTotal += 2;
                if (oe.getProximalEndpoint(a) == e.getProximalEndpoint(e.getNode1())) endpointMatches++;
                if (oe.getProximalEndpoint(b) == e.getProximalEndpoint(e.getNode2())) endpointMatches++;
            }
        }

        double adjP = tp + fp == 0 ? 1.0 : tp / (double) (tp + fp);
        double adjR = tp + fn == 0 ? 1.0 : tp / (double) (tp + fn);
        double adjF1 = adjP + adjR == 0 ? 0.0 : 2 * adjP * adjR / (adjP + adjR);
        double endpt = endpointTotal == 0 ? Double.NaN : endpointMatches / (double) endpointTotal;

        return new double[]{adjP, adjR, adjF1, endpt};
    }

    private static int arg(String[] args, int i, int dflt) {
        return args.length > i ? Integer.parseInt(args[i]) : dflt;
    }

    private static double darg(String[] args, int i, double dflt) {
        return args.length > i ? Double.parseDouble(args[i]) : dflt;
    }
}
