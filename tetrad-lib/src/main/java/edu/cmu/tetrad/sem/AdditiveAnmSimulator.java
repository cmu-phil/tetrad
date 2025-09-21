package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import org.apache.commons.math3.distribution.RealDistribution;

import java.util.*;

/**
 * Additive Nonlinear SEM generator (ANM style):
 *
 *   X_j = sum_{k in pa(j)} f_{jk}(X_k) + eps_j
 *
 * - Noise is added ONLY at the output layer (after summing the parent functions).
 * - Each edge (k->j) gets an independent randomized univariate function f_{jk}.
 * - Supports multiple function families: RBF bumps, tanh units, or simple polynomials.
 * - Expects an acyclic graph and generates in a single topological sweep (fast).
 *
 * Usage:
 *   AdditiveAnmSimulator gen = new AdditiveAnmSimulator(graph, N, noise, -3, 3)
 *       .setFunctionFamily(AdditiveAnmSimulator.Family.RBF)
 *       .setNumUnitsPerEdge(6)
 *       .setInputStandardize(true)
 *       .setEdgeScale(1.0)
 *       .setSeed(1234L);
 *   DataSet ds = gen.generate();
 */
public class AdditiveAnmSimulator {

    // ---------------- configuration ----------------
    public enum Family { RBF, TANH, POLY }

    private final Graph graph;
    private final int numSamples;
    private final RealDistribution noise;
    private final double rescaleMin;
    private final double rescaleMax;

    private Family family = Family.RBF;
    private int numUnitsPerEdge = 6;      // K: #basis per edge
    private boolean inputStandardize = true; // z-score inputs before f(x)
    private double edgeScale = 1.0;       // global multiplier for sum_k f_{jk}(x_k)
    private long seed = System.nanoTime();

    // internal
    private Random rng;

    // ---------------- ctor ----------------
    public AdditiveAnmSimulator(Graph graph,
                                int numSamples,
                                RealDistribution noiseDistribution,
                                double rescaleMin,
                                double rescaleMax) {
        if (!graph.paths().isAcyclic()) {
            throw new IllegalArgumentException("Graph must be acyclic for this generator.");
        }
        if (numSamples < 1) throw new IllegalArgumentException("numSamples must be >= 1");
        if (rescaleMin > rescaleMax) throw new IllegalArgumentException("rescaleMin > rescaleMax");

        this.graph = Objects.requireNonNull(graph, "graph");
        this.numSamples = numSamples;
        this.noise = Objects.requireNonNull(noiseDistribution, "noiseDistribution");
        this.rescaleMin = rescaleMin;
        this.rescaleMax = rescaleMax;
        this.rng = new Random(seed);
    }

    // ---------------- fluent setters ----------------
    public AdditiveAnmSimulator setFunctionFamily(Family fam) {
        this.family = Objects.requireNonNull(fam);
        return this;
    }

    /** # of basis units per edge (K). For POLY, this is the polynomial degree (>=1). */
    public AdditiveAnmSimulator setNumUnitsPerEdge(int k) {
        this.numUnitsPerEdge = Math.max(1, k);
        return this;
    }

    /** Standardize each parent input (z-score) before applying f(x). */
    public AdditiveAnmSimulator setInputStandardize(boolean on) {
        this.inputStandardize = on;
        return this;
    }

    /** Global scale for the deterministic part (sum of edge functions). */
    public AdditiveAnmSimulator setEdgeScale(double s) {
        this.edgeScale = s;
        return this;
    }

    public AdditiveAnmSimulator setSeed(long seed) {
        this.seed = seed;
        this.rng = new Random(seed);
        return this;
    }

    // ---------------- main API ----------------
    public DataSet generate() {
        final List<Node> topo = graph.paths().getValidOrder(graph.getNodes(), true);
        final int P = topo.size();
        final int N = numSamples;
        final Map<Node, Integer> idxOf = new HashMap<>(P * 2);
        for (int j = 0; j < P; j++) idxOf.put(topo.get(j), j);

        // parent indices per node
        final int[][] parents = new int[P][];
        for (int j = 0; j < P; j++) {
            List<Node> ps = graph.getParents(topo.get(j));
            int[] arr = new int[ps.size()];
            for (int k = 0; k < arr.length; k++) arr[k] = idxOf.get(ps.get(k));
            parents[j] = arr;
        }

        // pre-draw noise
        final double[][] eps = new double[N][P];
        for (int j = 0; j < P; j++) for (int i = 0; i < N; i++) eps[i][j] = noise.sample();

        // prebuild per-edge univariate functions
        final EdgeFunction[][] funcs = new EdgeFunction[P][];
        for (int j = 0; j < P; j++) {
            int[] pj = parents[j];
            funcs[j] = new EdgeFunction[pj.length];
            for (int t = 0; t < pj.length; t++) {
                funcs[j][t] = randomEdgeFunction();
            }
        }

        // storage
        final double[][] X = new double[N][P];

        // generate in topological order
        for (int j = 0; j < P; j++) {
            int[] pj = parents[j];

            if (pj.length == 0) {
                // no parents: just noise (centered by default of the distribution)
                for (int i = 0; i < N; i++) X[i][j] = eps[i][j];
            } else {
                // build parent columns, optionally z-score each parent for this child
                double[][] parentCols = new double[pj.length][N];
                for (int t = 0; t < pj.length; t++) {
                    int col = pj[t];
                    for (int i = 0; i < N; i++) parentCols[t][i] = X[i][col];
                    if (inputStandardize) zscoreInPlace(parentCols[t]);
                }

                // apply univariate f_{jk} to each parent and sum
                for (int i = 0; i < N; i++) {
                    double s = 0.0;
                    for (int t = 0; t < pj.length; t++) {
                        s += funcs[j][t].eval(parentCols[t][i]);
                    }
                    X[i][j] = edgeScale * s + eps[i][j];
                }
            }

            // optional per-column rescale
            if (rescaleMax > rescaleMin) {
                double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
                for (int i = 0; i < N; i++) {
                    double v = X[i][j]; if (v < min) min = v; if (v > max) max = v;
                }
                if (max > min) {
                    double inR = max - min, outR = rescaleMax - rescaleMin;
                    for (int i = 0; i < N; i++) X[i][j] = rescaleMin + outR * (X[i][j] - min) / inR;
                }
            }
        }

        return new BoxDataSet(new DoubleDataBox(X), new ArrayList<>(topo));
    }

    // ---------------- helpers ----------------

    private static void zscoreInPlace(double[] x) {
        int n = x.length;
        double m = 0.0;
        for (double v : x) m += v;
        m /= n;
        double v = 0.0;
        for (double a : x) { double d = a - m; v += d * d; }
        double sd = Math.sqrt(Math.max(v / Math.max(1, n - 1), 1e-12));
        for (int i = 0; i < n; i++) x[i] = (x[i] - m) / sd;
    }

    // ---------------- randomized univariate functions per edge ----------------

    private EdgeFunction randomEdgeFunction() {
        return switch (family) {
            case RBF -> randomRbf();
            case TANH -> randomTanh();
            case POLY -> randomPoly();
        };
    }

    // sum_{u=1..K} a_u * exp(-(x - c_u)^2 / (2*s_u^2))
    private EdgeFunction randomRbf() {
        int K = numUnitsPerEdge;
        double[] a = new double[K];
        double[] c = new double[K];
        double[] s = new double[K];

        for (int u = 0; u < K; u++) {
            a[u] = rng.nextGaussian() / Math.sqrt(K);     // variance-normalized
            c[u] = rng.nextGaussian() * 0.75;             // centers near 0
            s[u] = 0.4 + Math.abs(rng.nextGaussian()) * 0.6; // widths in [~0.1, ~1.5]
        }
        return x -> {
            double sum = 0.0;
            for (int u = 0; u < K; u++) {
                double d = (x - c[u]) / s[u];
                sum += a[u] * Math.exp(-0.5 * d * d);
            }
            return sum;
        };
    }

    // sum_{u=1..K} a_u * tanh(w_u * x + b_u)
    private EdgeFunction randomTanh() {
        int K = numUnitsPerEdge;
        double[] a = new double[K];
        double[] w = new double[K];
        double[] b = new double[K];

        for (int u = 0; u < K; u++) {
            a[u] = rng.nextGaussian() / Math.sqrt(K);
            w[u] = rng.nextGaussian();
            b[u] = rng.nextGaussian();
        }
        return x -> {
            double sum = 0.0;
            for (int u = 0; u < K; u++) {
                sum += a[u] * Math.tanh(w[u] * x + b[u]);
            }
            return sum;
        };
    }

    // sum_{r=1..K} a_r * x^r   (K is degree; omit intercept to keep f(0)=0 on average)
    private EdgeFunction randomPoly() {
        int deg = Math.max(1, numUnitsPerEdge);
        double[] a = new double[deg + 1]; // a[0] unused to avoid constant shift
        for (int r = 1; r <= deg; r++) a[r] = rng.nextGaussian() / Math.pow(2.0, r); // damp high degrees
        return x -> {
            double xr = x, sum = 0.0;
            for (int r = 1; r <= deg; r++) {
                sum += a[r] * xr;
                xr *= x;
            }
            return sum;
        };
    }

    // simple functional interface
    private interface EdgeFunction { double eval(double x); }

    // ---------------- getters for reproducibility/debug ----------------

    public Family getFunctionFamily() { return family; }
    public int getNumUnitsPerEdge() { return numUnitsPerEdge; }
    public boolean isInputStandardize() { return inputStandardize; }
    public double getEdgeScale() { return edgeScale; }
    public long getSeed() { return seed; }
}