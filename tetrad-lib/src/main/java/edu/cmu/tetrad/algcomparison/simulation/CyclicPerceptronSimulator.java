package edu.cmu.tetrad.algcomparison.simulation;

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import org.apache.commons.math3.distribution.RealDistribution;
import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.CommonOps_DDRM;

import java.util.*;
import java.util.function.Function;

/**
 * Standalone generator that supports DAGs (single sweep) and cyclic graphs (fixed-point).
 * Does not modify or depend on sem.CausalPerceptronNetwork internals.
 */
public final class CyclicPerceptronSimulator {

    // Required
    private final Graph graph;
    private final int numSamples;
    private final RealDistribution noise;
    private final double rescaleMin, rescaleMax;
    private final int[] hidden;
    private final double inputScale;
    private final Function<Double, Double> activation;
    private final boolean fastTanh;

    // Optional
    private Long seed = null;
    private Random seeder = new Random();
    private boolean gaussSeidel = false;
    private double damping = 0.3;
    private double tol = 1e-6;
    private int maxIters = 200;

    // Stats
    private int lastIterations = 0;
    private double lastMaxDelta = Double.NaN;
    private boolean converged = true;

    // ----- Constructors -----

    public CyclicPerceptronSimulator(Graph graph,
                                     int numSamples,
                                     RealDistribution noiseDistribution,
                                     double rescaleMin,
                                     double rescaleMax,
                                     int[] hiddenDimensions,
                                     double inputScale,
                                     Function<Double, Double> activationFunction) {

        if (numSamples < 1) throw new IllegalArgumentException("numSamples must be positive.");
        if (rescaleMin > rescaleMax) throw new IllegalArgumentException("rescaleMin > rescaleMax");
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(noiseDistribution, "noiseDistribution");
        Objects.requireNonNull(hiddenDimensions, "hiddenDimensions");
        Objects.requireNonNull(activationFunction, "activationFunction");
        for (int h : hiddenDimensions) if (h < 1) throw new IllegalArgumentException("Hidden dims must be >= 1");

        this.graph = graph;
        this.numSamples = numSamples;
        this.noise = noiseDistribution;
        this.rescaleMin = rescaleMin;
        this.rescaleMax = rescaleMax;
        this.hidden = hiddenDimensions.clone();
        this.inputScale = inputScale;
        this.activation = activationFunction;

        @SuppressWarnings("unchecked")
        Function<Double, Double> tanhRef = (Function<Double, Double>) (Double x) -> Math.tanh(x);
        this.fastTanh = (activationFunction == tanhRef);
    }

    // ----- Tuners -----

    public CyclicPerceptronSimulator setSeed(long seed) { this.seed = seed; this.seeder = new Random(seed); return this; }
    public CyclicPerceptronSimulator setGaussSeidel(boolean on) { this.gaussSeidel = on; return this; }
    public CyclicPerceptronSimulator setDamping(double alpha) {
        if (!(alpha > 0 && alpha <= 1)) throw new IllegalArgumentException("damping must be in (0,1].");
        this.damping = alpha; return this;
    }
    public CyclicPerceptronSimulator setTolerance(double tol) {
        if (!(tol > 0)) throw new IllegalArgumentException("tol must be > 0.");
        this.tol = tol; return this;
    }
    public CyclicPerceptronSimulator setMaxIters(int maxIters) {
        if (maxIters < 1) throw new IllegalArgumentException("maxIters must be >= 1.");
        this.maxIters = maxIters; return this;
    }

    // Stats getters (useful for debugging)
    public int getLastIterations() { return lastIterations; }
    public double getLastMaxDelta() { return lastMaxDelta; }
    public boolean isConverged() { return converged; }

    // ----- Public API -----

    public DataSet generate() {
        // Reset RNG per run (honor late setSeed())
        this.seeder = (this.seed == null) ? new Random() : new Random(this.seed);

        final boolean isDag = graph.paths().isAcyclic();

        final List<Node> order = isDag
                ? graph.paths().getValidOrder(graph.getNodes(), true)
                : new ArrayList<>(graph.getNodes());

        final int P = order.size();
        final int N = numSamples;

        final Map<Node, Integer> indexOf = new HashMap<>(P * 2);
        for (int j = 0; j < P; j++) indexOf.put(order.get(j), j);

        final int[][] parentsIdx = new int[P][];
        for (int j = 0; j < P; j++) {
            List<Node> ps = graph.getParents(order.get(j));
            int[] idx = new int[ps.size()];
            for (int k = 0; k < idx.length; k++) idx[k] = indexOf.get(ps.get(k));
            parentsIdx[j] = idx;
        }

        final RandomMLP[] mlps = new RandomMLP[P];
        final int[] Din = new int[P];
        for (int j = 0; j < P; j++) {
            Din[j] = parentsIdx[j].length + 1;
            mlps[j] = new RandomMLP(Din[j], hidden, 1, inputScale, seeder);
        }

        return isDag
                ? generateDag(N, order, parentsIdx, Din, mlps)
                : generateCyclic(N, order, parentsIdx, Din, mlps);
    }

    // ----- DAG mode -----

    private DataSet generateDag(int N,
                                List<Node> order,
                                int[][] parentsIdx,
                                int[] Din,
                                RandomMLP[] mlps) {
        final int P = order.size();
        final double[][] raw = new double[N][P];

        DMatrixRMaj A = new DMatrixRMaj(N, 1);
        DMatrixRMaj Z = new DMatrixRMaj(N, 1);
        DMatrixRMaj Y = new DMatrixRMaj(N, 1);

        final double[] noiseCol = new double[N];

        for (int j = 0; j < P; j++) {
            int[] pj = parentsIdx[j];
            int din = Din[j];

            A.reshape(N, din, false);

            // parents
            for (int c = 0; c < pj.length; c++) {
                int col = pj[c];
                int k = c;
                for (int i = 0; i < N; i++, k += din) A.data[k] = raw[i][col];
            }
            // noise
            for (int i = 0; i < N; i++) noiseCol[i] = noise.sample();
            int k = pj.length;
            for (int i = 0; i < N; i++, k += din) A.data[k] = noiseCol[i];

            // forward
            Y = mlps[j].forward(A, Z, Y, activation, fastTanh);

            // write + rescale
            double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < N; i++) {
                double v = Y.data[i];
                raw[i][j] = v;
                if (v < min) min = v;
                if (v > max) max = v;
            }
            if (rescaleMax > rescaleMin && max > min) {
                double inR = (max - min), outR = (rescaleMax - rescaleMin);
                for (int i = 0; i < N; i++) raw[i][j] = rescaleMin + outR * (raw[i][j] - min) / inR;
            }
        }

        return new BoxDataSet(new DoubleDataBox(raw), new ArrayList<>(order));
    }

    // ----- Cyclic mode (fixed-point) -----

    private DataSet generateCyclic(int N,
                                   List<Node> order,
                                   int[][] parentsIdx,
                                   int[] Din,
                                   RandomMLP[] mlps) {
        final int P = order.size();

        final double[][] X = new double[N][P];
        final double[][] Xnext = new double[N][P];
        final double[][] eps = new double[N][P];
        for (int j = 0; j < P; j++) for (int i = 0; i < N; i++) eps[i][j] = noise.sample();

        // init
        for (int j = 0; j < P; j++) Arrays.fill(Xnext[j], 0.0);

        DMatrixRMaj A = new DMatrixRMaj(N, 1);
        DMatrixRMaj Z = new DMatrixRMaj(N, 1);
        DMatrixRMaj Y = new DMatrixRMaj(N, 1);

        this.lastIterations = 0;
        this.lastMaxDelta = Double.NaN;
        this.converged = false;

        for (int iter = 0; iter < maxIters; iter++) {
            double maxDelta = 0.0;
            final double alpha = damping;

            if (!gaussSeidel) {
                // Jacobi: compute into Xnext, then damp
                for (int j = 0; j < P; j++) {
                    int[] pj = parentsIdx[j];
                    int din = Din[j];
                    A.reshape(N, din, false);

                    for (int c = 0; c < pj.length; c++) {
                        int col = pj[c];
                        int k = c;
                        for (int i = 0; i < N; i++, k += din) A.data[k] = X[i][col];
                    }
                    int k = pj.length;
                    for (int i = 0; i < N; i++, k += din) A.data[k] = eps[i][j];

                    Y = mlps[j].forward(A, Z, Y, activation, fastTanh);
                    for (int i = 0; i < N; i++) Xnext[i][j] = Y.data[i];
                }
                for (int j = 0; j < P; j++) {
                    for (int i = 0; i < N; i++) {
                        double v = (1 - alpha) * X[i][j] + alpha * Xnext[i][j];
                        double d = Math.abs(v - X[i][j]);
                        if (d > maxDelta) maxDelta = d;
                        X[i][j] = v;
                    }
                }
            } else {
                // Gauss–Seidel: in-place damped updates using freshest parents
                for (int j = 0; j < P; j++) {
                    int[] pj = parentsIdx[j];
                    int din = Din[j];
                    A.reshape(N, din, false);

                    for (int c = 0; c < pj.length; c++) {
                        int col = pj[c];
                        int k = c;
                        for (int i = 0; i < N; i++, k += din) A.data[k] = X[i][col];
                    }
                    int k = pj.length;
                    for (int i = 0; i < N; i++, k += din) A.data[k] = eps[i][j];

                    Y = mlps[j].forward(A, Z, Y, activation, fastTanh);
                    for (int i = 0; i < N; i++) {
                        double v = (1 - alpha) * X[i][j] + alpha * Y.data[i];
                        double d = Math.abs(v - X[i][j]);
                        if (d > maxDelta) maxDelta = d;
                        X[i][j] = v;
                    }
                }
            }

            this.lastIterations = iter + 1;
            this.lastMaxDelta = maxDelta;
            if (maxDelta < tol) { this.converged = true; break; }
        }

        // Final rescale
        if (rescaleMax > rescaleMin) {
            for (int j = 0; j < P; j++) {
                double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
                for (int i = 0; i < N; i++) {
                    double v = X[i][j];
                    if (v < min) min = v;
                    if (v > max) max = v;
                }
                if (max > min) {
                    double inR = (max - min), outR = (rescaleMax - rescaleMin);
                    for (int i = 0; i < N; i++) X[i][j] = rescaleMin + outR * (X[i][j] - min) / inR;
                }
            }
        }

        return new BoxDataSet(new DoubleDataBox(X), new ArrayList<>(order));
    }

    // ----- Tiny EJML MLP -----

    private static final class RandomMLP {
        final int Din, Dout;
        final int[] H;
        final DMatrixRMaj[] W;
        final double[][] b;

        RandomMLP(int Din, int[] hidden, int Dout, double inputScale, Random r) {
            this.Din = Din; this.Dout = Dout;
            this.H = hidden == null ? new int[0] : hidden.clone();
            int L = H.length + 1;
            this.W = new DMatrixRMaj[L];
            this.b = new double[L][];

            int prev = Din;
            for (int l = 0; l < H.length; l++) {
                W[l] = new DMatrixRMaj(H[l], prev);
                b[l] = new double[H[l]];
                heInit(W[l], r, inputScale);
                prev = H[l];
            }
            W[L - 1] = new DMatrixRMaj(Dout, prev);
            b[L - 1] = new double[Dout];
            heInit(W[L - 1], r, inputScale * 0.5);
        }

        DMatrixRMaj forward(DMatrixRMaj X,
                            DMatrixRMaj scratch1,
                            DMatrixRMaj out,
                            Function<Double, Double> act,
                            boolean fastTanh) {
            DMatrixRMaj cur = X;
            DMatrixRMaj bufA = scratch1;
            DMatrixRMaj bufB = new DMatrixRMaj(1, 1);

            for (int l = 0; l < H.length; l++) {
                int h = H[l];
                DMatrixRMaj dest = (cur == bufA) ? bufB : bufA;
                dest.reshape(X.numRows, h, false);
                CommonOps_DDRM.multTransB(cur, W[l], dest);
                addBiasRowsInPlace(dest, b[l]);
                applyActivationInPlace(dest, act, fastTanh);
                cur = dest;
            }

            out.reshape(X.numRows, Dout, false);
            CommonOps_DDRM.multTransB(cur, W[W.length - 1], out);
            addBiasRowsInPlace(out, b[b.length - 1]);
            return out;
        }

        private static void heInit(DMatrixRMaj W, Random r, double scale) {
            double s = scale * Math.sqrt(2.0 / Math.max(1, W.numCols));
            for (int i = 0, n = W.getNumElements(); i < n; i++) W.data[i] = r.nextGaussian() * s;
        }
    }

    private static void addBiasRowsInPlace(DMatrixRMaj A, double[] b) {
        final int n = A.numRows, m = A.numCols;
        int k = 0;
        for (int i = 0; i < n; i++) for (int j = 0; j < m; j++, k++) A.data[k] += b[j];
    }

    private static void applyActivationInPlace(DMatrixRMaj A,
                                               Function<Double, Double> f,
                                               boolean fastTanh) {
        final int n = A.getNumElements();
        if (fastTanh) { for (int i = 0; i < n; i++) A.data[i] = Math.tanh(A.data[i]); }
        else { for (int i = 0; i < n; i++) A.data[i] = f.apply(A.data[i]); }
    }
}