package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.score.ConditionalGaussianScore;
import edu.cmu.tetrad.search.test.IndTestConditionalGaussianLrt;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Hand-run harness for the CG SCORE (ConditionalGaussianScore / ConditionalGaussianLikelihood.getLikelihood),
 * companion to CgCalibrationHarness (which exercises the CG LRT test). Run once against the published jar
 * (old behavior) and once with the patched classes first on the classpath (new behavior), and compare.
 *
 * <p>S1: rare-parent false attachment under the null. X ~ N(0,1); D is an independent 6-category discrete
 * variable with rare cells (probs .4/.3/.15/.1/.03/.02). Reports the fraction of reps in which the local
 * BIC score prefers adding the parent, in both directions (D -> X candidate and X -> D candidate), for
 * discretize = true and false. Under a sane score this should be small (BIC-consistent, -> 0).</p>
 *
 * <p>S2: true-parent detection (power). Same D, but X | D = d has mean 0.4 * d, sd 1. Reports the fraction
 * of reps preferring the true parent; want high.</p>
 *
 * <p>S3: BOSS structure recovery on a true CG DAG (D1(3), D2(2) exogenous; D1 -> C1 -> C2 <- D2; C2 -> C3),
 * balanced vs rare-cell D1, n = 500 / 1500. Reports mean SHD to the true CPDAG plus mean extra / missing
 * adjacencies. Usage: java ... CgScoreHarness [R1 R3] (defaults 1000, 200).</p>
 */
public final class CgScoreHarness {

    private CgScoreHarness() {
    }

    /**
     * Entry point.
     *
     * @param args optional: R1 (reps for S1/S2), R3 (reps for S3)
     */
    public static void main(String[] args) {
        int r1 = args.length > 0 ? Integer.parseInt(args[0]) : 1000;
        int r3 = args.length > 1 ? Integer.parseInt(args[1]) : 200;

        System.out.println("=== S1: rare-parent FALSE ATTACHMENT under the null (R = " + r1 + "; want ~0) ===");
        for (int n : new int[]{200, 500}) {
            for (boolean disc : new boolean[]{true, false}) {
                s12(n, r1, disc, 0.0);
            }
        }

        System.out.println();
        System.out.println("=== S2: true-parent DETECTION, effect 0.4*d (R = " + r1 + "; want ~1) ===");
        for (int n : new int[]{200, 500}) {
            for (boolean disc : new boolean[]{true, false}) {
                s12(n, r1, disc, 0.4);
            }
        }

        System.out.println();
        System.out.println("=== S3: BOSS recovery of true CG DAG (R = " + r3 + ") ===");
        for (int n : new int[]{500, 1500}) {
            for (boolean rare : new boolean[]{false, true}) {
                s3(n, r3, rare);
            }
        }

        System.out.println();
        System.out.println("=== S4: parallel consistency under MISSING DATA (want 0 mismatches) ===");
        try {
            s4();
        } catch (Exception e) {
            System.out.println("S4 failed: " + e);
        }
    }

    /**
     * Serial-versus-parallel consistency of localScore and checkIndependence on a dataset with ~8% testwise-deletable
     * missing values. localScore / checkIndependence historically routed the per-call row support through
     * likelihood.setRows - shared mutable state - so concurrent calls with differing supports (which is exactly what
     * testwise deletion produces under missing data) could silently mix supports. Every parallel result is compared
     * for exact equality with its serially computed value.
     */
    private static void s4() throws Exception {
        int n = 600;
        Random rng = new Random(4242);

        int[] d1 = new int[n];
        int[] d2 = new int[n];
        double[] c1 = new double[n];
        double[] c2 = new double[n];
        double[] c3 = new double[n];

        for (int i = 0; i < n; i++) {
            d1[i] = sample(new double[]{0.4, 0.35, 0.25}, rng);
            d2[i] = sample(new double[]{0.6, 0.4}, rng);
            c1[i] = 0.8 * d1[i] + rng.nextGaussian();
            c2[i] = 0.7 * c1[i] + 0.9 * d2[i] + rng.nextGaussian();
            c3[i] = 0.7 * c2[i] + rng.nextGaussian();
        }

        DataSet data = mixed(n, new String[]{"C1", "C2", "C3", "D1", "D2"},
                new double[][]{c1, c2, c3}, new int[][]{d1, d2}, new int[]{3, 2});

        // Punch ~8% missing values into every column, independently.
        for (int j = 0; j < 5; j++) {
            for (int i = 0; i < n; i++) {
                if (rng.nextDouble() < 0.08) {
                    if (j < 3) data.setDouble(i, j, Double.NaN);
                    else data.setInt(i, j, -99);
                }
            }
        }

        // --- Score side ---
        ConditionalGaussianScore score = new ConditionalGaussianScore(data, 1.0, true);

        List<int[]> configs = new ArrayList<>(); // {child, parent...}
        for (int i = 0; i < 5; i++) {
            configs.add(new int[]{i});
            for (int p = 0; p < 5; p++) {
                if (p != i) configs.add(new int[]{i, p});
            }
            for (int p = 0; p < 5; p++) {
                for (int q = p + 1; q < 5; q++) {
                    if (p != i && q != i) configs.add(new int[]{i, p, q});
                }
            }
        }

        double[] expectedScores = new double[configs.size()];
        for (int c = 0; c < configs.size(); c++) {
            expectedScores[c] = scoreOf(score, configs.get(c));
        }

        // --- Test side ---
        IndependenceTest test = new IndTestConditionalGaussianLrt(data, 0.05, true);
        List<Node> vars = test.getVariables();
        List<Node[]> facts = new ArrayList<>(); // {x, y, z...}
        for (int x = 0; x < 5; x++) {
            for (int y = x + 1; y < 5; y++) {
                facts.add(new Node[]{vars.get(x), vars.get(y)});
                for (int z = 0; z < 5; z++) {
                    if (z != x && z != y) facts.add(new Node[]{vars.get(x), vars.get(y), vars.get(z)});
                }
            }
        }

        double[] expectedPs = new double[facts.size()];
        for (int c = 0; c < facts.size(); c++) {
            expectedPs[c] = pOf(test, facts.get(c));
        }

        int threads = 8;
        int itersPerThread = 400;
        AtomicInteger scoreMismatches = new AtomicInteger();
        AtomicInteger pMismatches = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<?>> futures = new ArrayList<>();

        for (int t = 0; t < threads; t++) {
            long seed = 1000L + t;
            futures.add(pool.submit(() -> {
                Random local = new Random(seed);
                for (int it = 0; it < itersPerThread; it++) {
                    int c = local.nextInt(configs.size());
                    double s = scoreOf(score, configs.get(c));
                    if (Double.compare(s, expectedScores[c]) != 0) scoreMismatches.incrementAndGet();

                    int f = local.nextInt(facts.size());
                    double pv = pOf(test, facts.get(f));
                    if (Double.compare(pv, expectedPs[f]) != 0) pMismatches.incrementAndGet();
                }
            }));
        }

        for (Future<?> f : futures) f.get();
        pool.shutdown();

        int total = threads * itersPerThread;
        System.out.printf("score: %d / %d parallel evaluations differ from serial%n", scoreMismatches.get(), total);
        System.out.printf("test:  %d / %d parallel evaluations differ from serial%n", pMismatches.get(), total);
    }

    private static double scoreOf(ConditionalGaussianScore score, int[] config) {
        int child = config[0];
        int[] parents = new int[config.length - 1];
        System.arraycopy(config, 1, parents, 0, parents.length);
        return score.localScore(child, parents);
    }

    private static double pOf(IndependenceTest test, Node[] fact) {
        java.util.Set<Node> z = new java.util.HashSet<>();
        for (int i = 2; i < fact.length; i++) z.add(fact[i]);
        try {
            return test.checkIndependence(fact[0], fact[1], z).getPValue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    private static void s12(int n, int reps, boolean discretize, double effect) {
        Random rng = new Random(31L + n + (discretize ? 1 : 0) + (long) (effect * 100));
        double[] probs = {0.4, 0.3, 0.15, 0.1, 0.03, 0.02};

        int preferDtoX = 0;   // score(X | {D}) > score(X | {})
        int preferXtoD = 0;   // score(D | {X}) > score(D | {})
        int bad = 0;

        for (int rep = 0; rep < reps; rep++) {
            int[] d = new int[n];
            double[] x = new double[n];
            for (int i = 0; i < n; i++) {
                d[i] = sample(probs, rng);
                x[i] = effect * d[i] + rng.nextGaussian();
            }

            DataSet data = mixed(n, new String[]{"X", "D"}, new double[][]{x}, new int[][]{d}, new int[]{6});
            ConditionalGaussianScore score = new ConditionalGaussianScore(data, 1.0, discretize);

            int xi = data.getColumnIndex(data.getVariable("X"));
            int di = data.getColumnIndex(data.getVariable("D"));

            double sX0 = score.localScore(xi);
            double sX1 = score.localScore(xi, di);
            double sD0 = score.localScore(di);
            double sD1 = score.localScore(di, xi);

            if (Double.isNaN(sX0) || Double.isNaN(sX1) || Double.isNaN(sD0) || Double.isNaN(sD1)) {
                bad++;
                continue;
            }

            if (sX1 > sX0) preferDtoX++;
            if (sD1 > sD0) preferXtoD++;
        }

        int ok = reps - bad;
        System.out.printf("n=%-5d discretize=%-5s  prefer D->X: %.3f   prefer X->D: %.3f   (bad=%d)%n",
                n, discretize, preferDtoX / (double) ok, preferXtoD / (double) ok, bad);
    }

    private static void s3(int n, int reps, boolean rareCells) {
        Random rng = new Random(97L + n + (rareCells ? 1 : 0));

        double[] p1 = rareCells
                ? new double[]{0.55, 0.41, 0.04}
                : new double[]{0.4, 0.35, 0.25};
        double[] p2 = {0.6, 0.4};

        Graph trueDag = trueDag();
        Graph trueCpdag = GraphTransforms.dagToCpdag(trueDag);

        double sumShd = 0;
        double sumExtra = 0;
        double sumMissing = 0;
        int failed = 0;

        for (int rep = 0; rep < reps; rep++) {
            int[] d1 = new int[n];
            int[] d2 = new int[n];
            double[] c1 = new double[n];
            double[] c2 = new double[n];
            double[] c3 = new double[n];

            for (int i = 0; i < n; i++) {
                d1[i] = sample(p1, rng);
                d2[i] = sample(p2, rng);
                c1[i] = 0.8 * d1[i] + rng.nextGaussian();
                c2[i] = 0.7 * c1[i] + 0.9 * d2[i] + rng.nextGaussian();
                c3[i] = 0.7 * c2[i] + rng.nextGaussian();
            }

            DataSet data = mixed(n, new String[]{"C1", "C2", "C3", "D1", "D2"},
                    new double[][]{c1, c2, c3}, new int[][]{d1, d2}, new int[]{3, 2});

            try {
                ConditionalGaussianScore score = new ConditionalGaussianScore(data, 1.0, true);
                Boss boss = new Boss(score);
                PermutationSearch search = new PermutationSearch(boss);
                Graph est = search.search();
                est = GraphUtils.replaceNodes(est, trueCpdag.getNodes());

                sumShd += shd(trueCpdag, est);
                sumExtra += extraAdjacencies(trueCpdag, est);
                sumMissing += extraAdjacencies(est, trueCpdag);
            } catch (Exception e) {
                failed++;
            }
        }

        int ok = reps - failed;
        System.out.printf("n=%-5d rareCells=%-5s  mean SHD: %.3f   extra adj: %.3f   missing adj: %.3f   (failed=%d)%n",
                n, rareCells, sumShd / ok, sumExtra / ok, sumMissing / ok, failed);
    }

    private static Graph trueDag() {
        Node c1 = new GraphNode("C1");
        Node c2 = new GraphNode("C2");
        Node c3 = new GraphNode("C3");
        Node d1 = new GraphNode("D1");
        Node d2 = new GraphNode("D2");
        Graph g = new EdgeListGraph(List.of(c1, c2, c3, d1, d2));
        g.addDirectedEdge(d1, c1);
        g.addDirectedEdge(c1, c2);
        g.addDirectedEdge(d2, c2);
        g.addDirectedEdge(c2, c3);
        return g;
    }

    /** Structural Hamming distance between two CPDAGs over the same nodes. */
    private static int shd(Graph g1, Graph g2) {
        int d = 0;
        List<Node> nodes = g1.getNodes();

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                Node a = nodes.get(i);
                Node b = nodes.get(j);
                Edge e1 = g1.getEdge(a, b);
                Edge e2 = g2.getEdge(a, b);

                if (e1 == null && e2 == null) continue;
                if (e1 == null || e2 == null) {
                    d++;
                    continue;
                }
                if (!sameOrientation(e1, e2, a, b)) d++;
            }
        }

        return d;
    }

    private static boolean sameOrientation(Edge e1, Edge e2, Node a, Node b) {
        return e1.getProximalEndpoint(a) == e2.getProximalEndpoint(a)
               && e1.getProximalEndpoint(b) == e2.getProximalEndpoint(b);
    }

    /** Number of adjacencies in g2 that are not in g1. */
    private static int extraAdjacencies(Graph g1, Graph g2) {
        int extra = 0;
        for (Edge e : g2.getEdges()) {
            if (!g1.isAdjacentTo(e.getNode1(), e.getNode2())) extra++;
        }
        return extra;
    }

    private static int sample(double[] probs, Random rng) {
        double u = rng.nextDouble();
        double cum = 0;
        for (int c = 0; c < probs.length; c++) {
            cum += probs[c];
            if (u < cum) return c;
        }
        return probs.length - 1;
    }

    private static DataSet mixed(int n, String[] names, double[][] cont, int[][] disc, int[] numCats) {
        List<Node> vars = new ArrayList<>();
        for (int j = 0; j < cont.length; j++) vars.add(new ContinuousVariable(names[j]));
        for (int j = 0; j < disc.length; j++) {
            List<String> cats = new ArrayList<>();
            for (int c = 0; c < numCats[j]; c++) cats.add("c" + c);
            vars.add(new DiscreteVariable(names[cont.length + j], cats));
        }
        DataSet d = new BoxDataSet(new MixedDataBox(vars, n), vars);
        for (int j = 0; j < cont.length; j++)
            for (int i = 0; i < n; i++) d.setDouble(i, j, cont[j][i]);
        for (int j = 0; j < disc.length; j++)
            for (int i = 0; i < n; i++) d.setInt(i, cont.length + j, disc[j][i]);
        return d;
    }
}
