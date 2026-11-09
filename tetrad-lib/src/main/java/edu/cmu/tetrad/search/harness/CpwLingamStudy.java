/// ////////////////////////////////////////////////////////////////////////////
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
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.harness;

import edu.cmu.tetrad.algcomparison.algorithm.oracle.pag.Cpw;
import edu.cmu.tetrad.algcomparison.independence.FisherZ;
import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.search.DirectLingam;
import edu.cmu.tetrad.search.Fask;
import edu.cmu.tetrad.search.score.SemBicScore;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.RandomUtil;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Simulation study of CPW on linear non-Gaussian acyclic model (LiNGAM) data, with FASK and
 * DirectLiNGAM as references.
 *
 * <p>The true model is a random DAG on {@code NUM_NODES} nodes with {@code NUM_EDGES} edges and a
 * linear SEM over it, coefficients drawn from +/- U({@code COEF_LOW}, {@code COEF_HIGH}), with
 * exogenous noises drawn i.i.d. from one of six families. Each family is centered and scaled to
 * mean 0 and variance 1, so that the structural signal-to-noise ratio is the same across families
 * and only the SHAPE of the noise varies; the families form a ladder in skewness (1.99, 1.14,
 * 0.60, 0, 0, 0) with Normal as a negative control, since LiNGAM-style orientation is not
 * identifiable there.
 *
 * <p>Output graphs are scored against the true DAG rather than against its PAG, because CPW
 * assumes causal sufficiency and is designed to emit a fully oriented (though possibly cyclic)
 * graph; see {@link #score(Graph, Graph)} for the definitions. The headline measure is "exact":
 * the fraction of true edges that appear in the output as exactly that single directed edge.
 *
 * <p>Four experiments are run:
 * <ol>
 *   <li><b>A</b> - CPW vs FASK vs DirectLiNGAM over all noise families and sample sizes.</li>
 *   <li><b>B</b> - CPW's five pairwise left-right rules, on a skewed and a symmetric family.</li>
 *   <li><b>C</b> - CPW's two-cycle alpha and independence-test alpha, one family, one n.</li>
 *   <li><b>D</b> - two-cycle detection on vs off, over all families and sample sizes. The true
 *       graph is always acyclic, so every 2-cycle in an output graph is a false positive; the
 *       companion {@link FaskTwoCycleRuleHarness} isolates the test responsible.</li>
 * </ol>
 *
 * <p>Summary tables are printed to standard output. Given a path argument, the per-replicate
 * counts are also written there as CSV for re-analysis.
 *
 * @see Cpw
 * @see FaskTwoCycleRuleHarness
 */
public final class CpwLingamStudy {

    // -----------------------------------------------------------------------
    // Configuration
    // -----------------------------------------------------------------------

    private static final int NUM_NODES = 10;
    private static final int NUM_EDGES = 10;
    private static final double COEF_LOW = 0.2;
    private static final double COEF_HIGH = 0.9;

    /** Fisher Z alpha for CPW's independence test, except where experiment C varies it. */
    private static final double ALPHA = 0.01;

    /** SEM BIC penalty discount for the scores FASK and DirectLiNGAM use. */
    private static final double PENALTY_DISC = 2.0;

    /** CPW's default two-cycle alpha, as the HTML manual gives it. */
    private static final double TWO_CYCLE_ALPHA = 0.01;

    private static final int REPS = Integer.parseInt(System.getProperty("reps", "100"));
    private static final long BASE_SEED = Long.parseLong(System.getProperty("seed", "490217"));

    private static final int[] SAMPLE_SIZES = {200, 1000, 5000};

    /**
     * Noise families as {label, expression}, each centered and scaled to mean 0 and variance 1.
     * The moments were checked by simulation (500k draws):
     * <pre>
     *   label      skew   excess kurtosis
     *   Exp        1.99    5.90
     *   Gumbel     1.14    2.42
     *   Beta2-5    0.60   -0.12
     *   Uniform    0.00   -1.20
     *   Laplace    0.00    3.05
     *   Normal     0.00    0.00
     * </pre>
     * {@code Exp(1)} is the alias of {@code Exponential(1)}; both parse, and the alias is kept
     * here because the per-replicate seed is derived from the expression string, so changing it
     * would change every number this harness produces.
     */
    private static final String[][] NOISES = {
            {"Exp", "(Exp(1) - 1)"},
            {"Gumbel", "(Gumbel(0, 1) - 0.5772157) / 1.2825499"},
            {"Beta2-5", "(Beta(2, 5) - 0.2857143) / 0.1597191"},
            {"Uniform", "Uniform(-1, 1) * 1.7320508"},
            {"Laplace", "Laplace(0, 1) * 0.7071068"},
            {"Normal", "Normal(0, 1)"}
    };

    /** Index into {@link #NOISES} of the strongly right-skewed family experiment C uses. */
    private static final int SKEWED = 0;

    /** Index into {@link #NOISES} of the symmetric heavy-tailed family experiment B uses. */
    private static final int SYMMETRIC_HEAVY = 4;

    private static final String[] RULE_NAMES = {"FASK1", "FASK2", "RSkew", "Skew", "Tanh"};

    private CpwLingamStudy() {
    }

    // -----------------------------------------------------------------------
    // Entry point
    // -----------------------------------------------------------------------

    /**
     * Runs the four experiments, prints the summary tables, and optionally writes the
     * per-replicate counts as CSV.
     *
     * @param args optionally one path to write the per-replicate CSV to
     * @throws Exception if a search or the data simulation fails
     */
    public static void main(String[] args) throws Exception {
        List<Row> rows = new ArrayList<>();

        System.out.printf("CPW on linear non-Gaussian acyclic data: %d nodes, %d edges, "
                        + "coefficients +/- U(%.1f, %.1f), %d replicates per cell.%n",
                NUM_NODES, NUM_EDGES, COEF_LOW, COEF_HIGH, REPS);
        System.out.println("Scored against the true DAG. Noise families standardized to mean 0, variance 1.");

        runExperimentA(rows);
        runExperimentB(rows);
        runExperimentC(rows);
        runExperimentD(rows);

        printExperimentA(rows);
        printExperimentB(rows);
        printExperimentC(rows);
        printExperimentD(rows);

        if (args.length > 0) {
            writeCsv(rows, Path.of(args[0]));
            System.out.println();
            System.out.println("Per-replicate counts written to " + args[0]);
        }
    }

    // -----------------------------------------------------------------------
    // Experiments
    // -----------------------------------------------------------------------

    private static void runExperimentA(List<Row> rows) throws Exception {
        for (String[] noise : NOISES) {
            for (int n : SAMPLE_SIZES) {
                System.err.printf("A: noise=%s n=%d%n", noise[0], n);

                for (int rep = 0; rep < REPS; rep++) {
                    Trial trial = newTrial(noise[1], n, rep);

                    add(rows, "A", "CPW", noise[0], n, ALPHA, 2, TWO_CYCLE_ALPHA, rep, trial,
                            timed(() -> runCpw(trial.data(), ALPHA, 2, TWO_CYCLE_ALPHA)));
                    add(rows, "A", "FASK", noise[0], n, ALPHA, 2, TWO_CYCLE_ALPHA, rep, trial,
                            timed(() -> runFask(trial.data(), 2, TWO_CYCLE_ALPHA)));
                    add(rows, "A", "DirectLiNGAM", noise[0], n, ALPHA, 2, TWO_CYCLE_ALPHA, rep, trial,
                            timed(() -> runDirectLingam(trial.data())));
                }
            }
        }
    }

    private static void runExperimentB(List<Row> rows) throws Exception {
        for (int which : new int[]{SKEWED, SYMMETRIC_HEAVY}) {
            String[] noise = NOISES[which];
            System.err.printf("B: noise=%s%n", noise[0]);

            for (int rep = 0; rep < REPS; rep++) {
                Trial trial = newTrial(noise[1], 1000, rep);

                for (int rule = 1; rule <= 5; rule++) {
                    final int pwRule = rule;
                    add(rows, "B", "CPW", noise[0], 1000, ALPHA, rule, TWO_CYCLE_ALPHA, rep, trial,
                            timed(() -> runCpw(trial.data(), ALPHA, pwRule, TWO_CYCLE_ALPHA)));
                }
            }
        }
    }

    private static void runExperimentC(List<Row> rows) throws Exception {
        System.err.println("C: ablations");
        String[] noise = NOISES[SKEWED];

        for (int rep = 0; rep < REPS; rep++) {
            Trial trial = newTrial(noise[1], 1000, rep);

            for (double twoCycleAlpha : new double[]{0.0, 0.01, 0.05}) {
                final double tca = twoCycleAlpha;
                add(rows, "C-twocycle", "CPW", noise[0], 1000, ALPHA, 2, twoCycleAlpha, rep, trial,
                        timed(() -> runCpw(trial.data(), ALPHA, 2, tca)));
            }

            for (double alpha : new double[]{0.001, 0.01, 0.05, 0.1}) {
                final double a = alpha;
                add(rows, "C-alpha", "CPW", noise[0], 1000, alpha, 2, TWO_CYCLE_ALPHA, rep, trial,
                        timed(() -> runCpw(trial.data(), a, 2, TWO_CYCLE_ALPHA)));
            }
        }
    }

    private static void runExperimentD(List<Row> rows) throws Exception {
        for (String[] noise : NOISES) {
            for (int n : SAMPLE_SIZES) {
                System.err.printf("D: noise=%s n=%d%n", noise[0], n);

                for (int rep = 0; rep < REPS; rep++) {
                    Trial trial = newTrial(noise[1], n, rep);

                    for (double twoCycleAlpha : new double[]{0.0, TWO_CYCLE_ALPHA}) {
                        final double tca = twoCycleAlpha;
                        add(rows, "D", "CPW", noise[0], n, ALPHA, 2, twoCycleAlpha, rep, trial,
                                timed(() -> runCpw(trial.data(), ALPHA, 2, tca)));
                        add(rows, "D", "FASK", noise[0], n, ALPHA, 2, twoCycleAlpha, rep, trial,
                                timed(() -> runFask(trial.data(), 2, tca)));
                    }
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Trials
    // -----------------------------------------------------------------------

    private record Trial(Graph trueDag, DataSet data) {
    }

    /**
     * A fresh DAG, SEM and data set for one replicate. The seed is a function of the replicate,
     * the sample size and the noise expression, so that every algorithm and every parameter
     * setting in a cell sees exactly the same data, and so that the whole study is reproducible.
     */
    private static Trial newTrial(String noiseExpr, int n, int rep) throws Exception {
        RandomUtil.getInstance().setSeed(BASE_SEED + 1000L * rep + 7L * n + noiseExpr.hashCode());

        Graph dag = RandomGraph.randomGraph(NUM_NODES, 0, NUM_EDGES, 100, 100, 100, false);

        Parameters parameters = new Parameters();
        parameters.set(Params.COEF_LOW, COEF_LOW);
        parameters.set(Params.COEF_HIGH, COEF_HIGH);
        parameters.set(Params.COEF_SYMMETRIC, true);
        parameters.set(Params.CUSTOM_NOISE_OPTION, 2);
        parameters.set(Params.CUSTOM_NOISE_EXPRESSION, noiseExpr);

        SemIm im = new SemIm(new SemPm(dag), parameters);
        return new Trial(dag, im.simulateData(n, false));
    }

    // -----------------------------------------------------------------------
    // Algorithms
    // -----------------------------------------------------------------------

    /** Wall-clock milliseconds of the most recent search, recorded by {@link #timed}. */
    private static long lastMs;

    private interface Search {
        Graph run() throws Exception;
    }

    private static Graph timed(Search search) throws Exception {
        long t0 = System.nanoTime();
        Graph graph = search.run();
        CpwLingamStudy.lastMs = (System.nanoTime() - t0) / 1_000_000;
        return graph;
    }

    private static Graph runCpw(DataSet data, double alpha, int pwRule, double twoCycleAlpha)
            throws Exception {
        Parameters parameters = new Parameters();
        parameters.set(Params.ALPHA, alpha);
        parameters.set(Params.DEPTH, -1);
        parameters.set(Params.STABLE_FAS, true);
        parameters.set(Params.COLLIDER_ORIENTATION_STYLE, 3);   // Max-P, the manual's default
        parameters.set(Params.MAX_DISCRIMINATING_PATH_LENGTH, -1);
        parameters.set(Params.DO_POSSIBLE_DSEP, true);
        parameters.set(Params.COMPLETE_RULE_SET_USED, false);
        parameters.set(Params.FASK_LEFT_RIGHT_RULE, pwRule);
        parameters.set(Params.TWO_CYCLE_ALPHA, twoCycleAlpha);
        parameters.set(Params.FDR_Q, 0.0);
        parameters.set(Params.TIME_LAG, 0);
        parameters.set(Params.NUMBER_RESAMPLING, 0);
        parameters.set(Params.VERBOSE, false);

        return new Cpw(new FisherZ()).search(data, parameters);
    }

    private static Graph runFask(DataSet data, int pwRule, double twoCycleAlpha)
            throws InterruptedException {
        SemBicScore score = new SemBicScore(new CovarianceMatrix(data));
        score.setPenaltyDiscount(PENALTY_DISC);

        Fask fask = new Fask(data, score);
        fask.setLeftRight(toLeftRight(pwRule));
        fask.setDepth(-1);
        fask.setTwoCycleAlpha(twoCycleAlpha);
        return fask.search();
    }

    private static Graph runDirectLingam(DataSet data) {
        SemBicScore score = new SemBicScore(new CovarianceMatrix(data));
        score.setPenaltyDiscount(PENALTY_DISC);
        return new DirectLingam(data, score).search();
    }

    private static Fask.LeftRight toLeftRight(int pwRule) {
        return switch (pwRule) {
            case 1 -> Fask.LeftRight.FASK1;
            case 2 -> Fask.LeftRight.FASK2;
            case 3 -> Fask.LeftRight.RSKEW;
            case 4 -> Fask.LeftRight.SKEW;
            case 5 -> Fask.LeftRight.TANH;
            default -> throw new IllegalArgumentException("Unknown rule: " + pwRule);
        };
    }

    // -----------------------------------------------------------------------
    // Scoring
    // -----------------------------------------------------------------------

    private record Row(String exp, String algo, String noise, int n, double alpha, int rule,
                       double twoCycleAlpha, int rep, Counts counts, long ms) {
    }

    private static final class Counts {
        private int trueEdges;
        private int estEdges;
        private int est2cyc;
        private int adjTp;
        private int adjFp;
        private int adjFn;
        private int orCorrect;
        private int orReversed;
        private int orOther;
        private int orMissing;
        private int arrTp;
        private int arrFp;
        private int arrFn;
    }

    private static void add(List<Row> rows, String exp, String algo, String noise, int n,
                            double alpha, int rule, double twoCycleAlpha, int rep, Trial trial,
                            Graph est) {
        rows.add(new Row(exp, algo, noise, n, alpha, rule, twoCycleAlpha, rep,
                score(trial.trueDag(), est), CpwLingamStudy.lastMs));
    }

    /**
     * Scores an estimated graph against the true DAG, by name, so that node instance identity
     * need not be shared.
     *
     * <p><b>Adjacency</b> is counted over unordered pairs. <b>Orientation</b> is counted over the
     * true graph's singly-directed edges X -&gt; Y: correct if the output holds exactly X -&gt; Y,
     * reversed if it holds exactly Y -&gt; X, other if it holds anything else between them
     * (undirected, o-o, o-&gt;, &lt;-&gt;, or a 2-cycle), and missing if the adjacency itself is
     * absent. <b>Arrowheads</b> are counted over ordered pairs (X, Y) carrying an arrowhead at Y
     * on some X*-*Y edge; note that a 2-cycle therefore scores an arrowhead true positive in the
     * true edge's direction as well as a false positive in the reverse direction, which is why
     * arrowhead recall can stay high while the exact-orientation rate falls.
     *
     * @param trueDag the true DAG
     * @param est     the estimated graph, which may contain circles, bidirected edges or cycles
     * @return the counts for this replicate
     */
    private static Counts score(Graph trueDag, Graph est) {
        Counts counts = new Counts();

        List<Node> trueNodes = trueDag.getNodes();
        Map<String, Node> estByName = new HashMap<>();
        for (Node node : est.getNodes()) estByName.put(node.getName(), node);

        counts.trueEdges = trueDag.getNumEdges();
        counts.estEdges = est.getNumEdges();

        for (Edge edge : est.getEdges()) {
            if (est.getEdges(edge.getNode1(), edge.getNode2()).size() > 1) counts.est2cyc++;
        }
        counts.est2cyc /= 2; // each 2-cycle was counted once per edge

        for (int i = 0; i < trueNodes.size(); i++) {
            for (int j = i + 1; j < trueNodes.size(); j++) {
                Node tx = trueNodes.get(i);
                Node ty = trueNodes.get(j);
                Node ex = estByName.get(tx.getName());
                Node ey = estByName.get(ty.getName());

                boolean trueAdj = trueDag.isAdjacentTo(tx, ty);
                boolean estAdj = ex != null && ey != null && est.isAdjacentTo(ex, ey);

                if (trueAdj && estAdj) counts.adjTp++;
                else if (estAdj) counts.adjFp++;
                else if (trueAdj) counts.adjFn++;
            }
        }

        for (Edge trueEdge : trueDag.getEdges()) {
            Node tx = trueEdge.getNode1();
            Node ty = trueEdge.getNode2();

            boolean xToY = trueDag.isDirectedFromTo(tx, ty);
            boolean yToX = trueDag.isDirectedFromTo(ty, tx);
            if (xToY == yToX) continue; // not a singly-directed true edge

            Node from = estByName.get((xToY ? tx : ty).getName());
            Node to = estByName.get((xToY ? ty : tx).getName());

            if (from == null || to == null || !est.isAdjacentTo(from, to)) {
                counts.orMissing++;
            } else if (est.getEdges(from, to).size() != 1) {
                counts.orOther++;
            } else if (est.isDirectedFromTo(from, to)) {
                counts.orCorrect++;
            } else if (est.isDirectedFromTo(to, from)) {
                counts.orReversed++;
            } else {
                counts.orOther++;
            }
        }

        for (Node tx : trueNodes) {
            for (Node ty : trueNodes) {
                if (tx == ty) continue;

                boolean trueArrow = trueDag.isDirectedFromTo(tx, ty); // arrowhead at ty
                Node ex = estByName.get(tx.getName());
                Node ey = estByName.get(ty.getName());
                boolean estArrow = ex != null && ey != null && hasArrowAt(est, ex, ey);

                if (trueArrow && estArrow) counts.arrTp++;
                else if (estArrow) counts.arrFp++;
                else if (trueArrow) counts.arrFn++;
            }
        }

        return counts;
    }

    /** Whether some edge between the two nodes carries an arrowhead at {@code to}. */
    private static boolean hasArrowAt(Graph graph, Node from, Node to) {
        for (Edge edge : graph.getEdges(from, to)) {
            if (edge.getProximalEndpoint(to) == Endpoint.ARROW) return true;
        }
        return false;
    }

    // -----------------------------------------------------------------------
    // Aggregation
    // -----------------------------------------------------------------------

    /** Counts pooled over the replicates of one cell, with the rates derived from them. */
    private record Summary(int reps, double adjP, double adjR, double adjF1, double arrP,
                           double arrR, double orient, double reversed, double other,
                           double exact, double twoCycles, double edges, double ms) {
    }

    private static Summary summarize(List<Row> rows) {
        if (rows.isEmpty()) return null;

        Counts s = new Counts();
        long ms = 0;

        for (Row row : rows) {
            Counts c = row.counts();
            s.trueEdges += c.trueEdges;
            s.estEdges += c.estEdges;
            s.est2cyc += c.est2cyc;
            s.adjTp += c.adjTp;
            s.adjFp += c.adjFp;
            s.adjFn += c.adjFn;
            s.orCorrect += c.orCorrect;
            s.orReversed += c.orReversed;
            s.orOther += c.orOther;
            s.orMissing += c.orMissing;
            s.arrTp += c.arrTp;
            s.arrFp += c.arrFp;
            s.arrFn += c.arrFn;
            ms += row.ms();
        }

        int recovered = s.orCorrect + s.orReversed + s.orOther;
        double adjP = ratio(s.adjTp, s.adjTp + s.adjFp);
        double adjR = ratio(s.adjTp, s.adjTp + s.adjFn);

        return new Summary(rows.size(),
                adjP, adjR, ratio(2 * s.adjTp, 2 * s.adjTp + s.adjFp + s.adjFn),
                ratio(s.arrTp, s.arrTp + s.arrFp), ratio(s.arrTp, s.arrTp + s.arrFn),
                ratio(s.orCorrect, recovered), ratio(s.orReversed, recovered),
                ratio(s.orOther, recovered), ratio(s.orCorrect, s.trueEdges),
                ratio(s.est2cyc, rows.size()), ratio(s.estEdges, rows.size()),
                ratio(ms, rows.size()));
    }

    private static double ratio(double numerator, double denominator) {
        return denominator == 0.0 ? Double.NaN : numerator / denominator;
    }

    private static Summary cell(List<Row> rows, Predicate<Row> predicate) {
        List<Row> selected = new ArrayList<>();
        for (Row row : rows) if (predicate.test(row)) selected.add(row);
        return summarize(selected);
    }

    // -----------------------------------------------------------------------
    // Output
    // -----------------------------------------------------------------------

    private static void printExperimentA(List<Row> rows) {
        header("A", "CPW vs FASK vs DirectLiNGAM, by noise family and sample size");
        System.out.printf("%-9s %5s %-13s %6s %6s %6s %6s %6s %7s %6s %6s %6s %5s %5s%n",
                "noise", "n", "algo", "AdjP", "AdjR", "AdjF1", "ArrP", "ArrR",
                "Orient", "Rev", "Other", "Exact", "2cyc", "ms");
        System.out.println("-".repeat(107));

        for (String[] noise : NOISES) {
            for (int n : SAMPLE_SIZES) {
                for (String algo : new String[]{"CPW", "FASK", "DirectLiNGAM"}) {
                    final int sampleSize = n;
                    Summary s = cell(rows, r -> r.exp().equals("A") && r.noise().equals(noise[0])
                            && r.n() == sampleSize && r.algo().equals(algo));
                    if (s == null) continue;

                    System.out.printf("%-9s %5d %-13s %6.3f %6.3f %6.3f %6.3f %6.3f %7.3f "
                                    + "%6.3f %6.3f %6.3f %5.2f %5.0f%n",
                            noise[0], n, algo, s.adjP(), s.adjR(), s.adjF1(), s.arrP(), s.arrR(),
                            s.orient(), s.reversed(), s.other(), s.exact(), s.twoCycles(), s.ms());
                }
            }
            System.out.println();
        }
    }

    private static void printExperimentB(List<Row> rows) {
        header("B", "CPW pairwise left-right rule, n=1000");
        System.out.printf("%-9s %-8s %6s %6s %6s %6s %7s %6s %6s %6s%n",
                "noise", "rule", "AdjP", "AdjR", "ArrP", "ArrR", "Orient", "Rev", "Other", "Exact");
        System.out.println("-".repeat(73));

        for (int which : new int[]{SKEWED, SYMMETRIC_HEAVY}) {
            String[] noise = NOISES[which];
            for (int rule = 1; rule <= 5; rule++) {
                final int pwRule = rule;
                Summary s = cell(rows, r -> r.exp().equals("B") && r.noise().equals(noise[0])
                        && r.rule() == pwRule);
                if (s == null) continue;

                System.out.printf("%-9s %-8s %6.3f %6.3f %6.3f %6.3f %7.3f %6.3f %6.3f %6.3f%n",
                        noise[0], RULE_NAMES[rule - 1], s.adjP(), s.adjR(), s.arrP(), s.arrR(),
                        s.orient(), s.reversed(), s.other(), s.exact());
            }
            System.out.println();
        }
    }

    private static void printExperimentC(List<Row> rows) {
        header("C", "CPW ablations, " + NOISES[SKEWED][0] + " noise, n=1000");

        System.out.printf("%-14s %6s %6s %6s %6s %7s %6s %6s %6s %9s%n",
                "twoCycleAlpha", "AdjP", "AdjR", "ArrP", "ArrR", "Orient", "Rev", "Other",
                "Exact", "2cyc/rep");
        System.out.println("-".repeat(86));

        for (double twoCycleAlpha : new double[]{0.0, 0.01, 0.05}) {
            Summary s = cell(rows, r -> r.exp().equals("C-twocycle")
                    && r.twoCycleAlpha() == twoCycleAlpha);
            if (s == null) continue;

            System.out.printf("%-14s %6.3f %6.3f %6.3f %6.3f %7.3f %6.3f %6.3f %6.3f %9.2f%n",
                    String.valueOf(twoCycleAlpha), s.adjP(), s.adjR(), s.arrP(), s.arrR(),
                    s.orient(), s.reversed(), s.other(), s.exact(), s.twoCycles());
        }

        System.out.println();
        System.out.printf("%-14s %6s %6s %6s %6s %7s %6s %6s %6s %9s%n",
                "alpha", "AdjP", "AdjR", "ArrP", "ArrR", "Orient", "Rev", "Other",
                "Exact", "edges");
        System.out.println("-".repeat(86));

        for (double alpha : new double[]{0.001, 0.01, 0.05, 0.1}) {
            Summary s = cell(rows, r -> r.exp().equals("C-alpha") && r.alpha() == alpha);
            if (s == null) continue;

            System.out.printf("%-14s %6.3f %6.3f %6.3f %6.3f %7.3f %6.3f %6.3f %6.3f %9.2f%n",
                    String.valueOf(alpha), s.adjP(), s.adjR(), s.arrP(), s.arrR(),
                    s.orient(), s.reversed(), s.other(), s.exact(), s.edges());
        }
    }

    private static void printExperimentD(List<Row> rows) {
        header("D", "Two-cycle detection on (alpha=" + TWO_CYCLE_ALPHA + ") vs off (alpha=0). "
                + "The true graph is acyclic, so every 2-cycle is a false positive.");
        System.out.printf("%-9s %5s %-5s %-9s %6s %6s %7s %6s %6s %6s %9s%n",
                "noise", "n", "algo", "2cyc test", "ArrP", "ArrR", "Orient", "Rev", "Other",
                "Exact", "2cyc/rep");
        System.out.println("-".repeat(86));

        for (String[] noise : NOISES) {
            for (int n : SAMPLE_SIZES) {
                for (String algo : new String[]{"CPW", "FASK"}) {
                    for (double twoCycleAlpha : new double[]{0.0, TWO_CYCLE_ALPHA}) {
                        final int sampleSize = n;
                        Summary s = cell(rows, r -> r.exp().equals("D")
                                && r.noise().equals(noise[0]) && r.n() == sampleSize
                                && r.algo().equals(algo) && r.twoCycleAlpha() == twoCycleAlpha);
                        if (s == null) continue;

                        System.out.printf("%-9s %5d %-5s %-9s %6.3f %6.3f %7.3f %6.3f %6.3f "
                                        + "%6.3f %9.2f%n",
                                noise[0], n, algo, twoCycleAlpha == 0.0 ? "off" : "on",
                                s.arrP(), s.arrR(), s.orient(), s.reversed(), s.other(),
                                s.exact(), s.twoCycles());
                    }
                }
                System.out.println();
            }
        }
    }

    private static void header(String experiment, String description) {
        System.out.println();
        System.out.println("=".repeat(107));
        System.out.println("EXPERIMENT " + experiment + " - " + description);
        System.out.println("=".repeat(107));
    }

    private static void writeCsv(List<Row> rows, Path path) throws IOException {
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(path, StandardCharsets.UTF_8))) {
            writer.println("exp,algo,noise,n,alpha,rule,twoCycleAlpha,rep,trueEdges,estEdges,"
                    + "est2cyc,adjTp,adjFp,adjFn,orCorrect,orReversed,orOther,orMissing,"
                    + "arrTp,arrFp,arrFn,ms");

            for (Row row : rows) {
                Counts c = row.counts();
                writer.printf("%s,%s,%s,%d,%s,%d,%s,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d%n",
                        row.exp(), row.algo(), row.noise(), row.n(), row.alpha(), row.rule(),
                        row.twoCycleAlpha(), row.rep(),
                        c.trueEdges, c.estEdges, c.est2cyc, c.adjTp, c.adjFp, c.adjFn,
                        c.orCorrect, c.orReversed, c.orOther, c.orMissing,
                        c.arrTp, c.arrFp, c.arrFn, row.ms());
            }
        }
    }
}
