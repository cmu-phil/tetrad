package edu.cmu.tetrad.sem;

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
 * AdditiveNoiseSimulation
 * <p>
 * Generates data from an additive-noise structural causal model (ANM):
 * <p>
 * X_j = f_j(Pa(X_j)) + N_j,   with independent noise terms N_j.
 * <p>
 * Each f_j is represented by a randomly initialized MLP (parents-only input). Root nodes are generated as pure noise
 * (optionally rescaled).
 * <p>
 * NOTE: The optional rescaling performed here is computed from the realized sample (min/max of the generated column),
 * which couples rows within a dataset. This is convenient for keeping values in a range but is not “SCM-pure” in the
 * sense of applying a fixed transformation independent of the sampled data.
 */
public class AdditiveNoiseSimulation {

    private final Graph graph;
    private final int numSamples;
    private final RealDistribution noiseDistribution;
    private final double rescaleMin, rescaleMax;
    private final int[] hiddenDimensions;
    private final double inputScale;
    private final Function<Double, Double> activationFunction;
    private final boolean useFastTanh;

    // Keep simple per-node seeding (still random overall)
    private final Random seeder = new Random();

    /**
     * Constructs a new AdditiveNoiseSimulation instance with the specified parameters.
     *
     * @param graph              The causal graph representing the structural relationships.
     * @param numSamples         The number of data samples to generate.
     * @param noiseDistribution  The distribution for additive noise.
     * @param rescaleMin         The minimum value for data rescaling.
     * @param rescaleMax         The maximum value for data rescaling.
     * @param hiddenDimensions   The dimensions of hidden layers in the MLP.
     * @param inputScale         The scaling factor for input data.
     * @param activationFunction The activation function for the MLP.
     */
    public AdditiveNoiseSimulation(Graph graph,
                                   int numSamples,
                                   RealDistribution noiseDistribution,
                                   double rescaleMin,
                                   double rescaleMax,
                                   int[] hiddenDimensions,
                                   double inputScale,
                                   Function<Double, Double> activationFunction) {
        if (!graph.paths().isAcyclic()) throw new IllegalArgumentException("Graph contains cycles.");
        if (numSamples < 1) throw new IllegalArgumentException("numSamples must be positive.");
        if (rescaleMin > rescaleMax) throw new IllegalArgumentException("rescaleMin > rescaleMax");
        Objects.requireNonNull(noiseDistribution, "noiseDistribution");
        Objects.requireNonNull(hiddenDimensions, "hiddenDimensions");
        Objects.requireNonNull(activationFunction, "activationFunction");

        for (int h : hiddenDimensions) if (h < 1) throw new IllegalArgumentException("Hidden dims must be >= 1");

        this.graph = graph;
        this.numSamples = numSamples;
        this.noiseDistribution = noiseDistribution;
        this.rescaleMin = rescaleMin;
        this.rescaleMax = rescaleMax;
        this.hiddenDimensions = hiddenDimensions.clone();
        this.inputScale = inputScale;
        this.activationFunction = activationFunction;

        // Robust-ish “is tanh” detection for fast path.
        this.useFastTanh = looksLikeTanh(activationFunction);
    }

    private static double quantileOfColumn(double[][] raw, int col, double q) {
        int n = raw.length;
        double[] tmp = new double[n];
        for (int i = 0; i < n; i++) tmp[i] = raw[i][col];
        Arrays.sort(tmp);

        if (n == 0) return Double.NaN;
        if (q <= 0.0) return tmp[0];
        if (q >= 1.0) return tmp[n - 1];

        double pos = q * (n - 1);
        int lo = (int) Math.floor(pos);
        int hi = (int) Math.ceil(pos);
        if (hi == lo) return tmp[lo];
        double w = pos - lo;
        return tmp[lo] * (1.0 - w) + tmp[hi] * w;
    }

    private static void addBiasRowsInPlace(DMatrixRMaj A, double[] b) {
        final int n = A.numRows, m = A.numCols;
        int k = 0;
        for (int i = 0; i < n; i++) for (int j = 0; j < m; j++, k++) A.data[k] += b[j];
    }

    // ------------------ Tiny EJML MLP ------------------

    private static void applyActivationInPlace(DMatrixRMaj A,
                                               Function<Double, Double> f,
                                               boolean fastTanh) {
        final int n = A.getNumElements();
        if (fastTanh) {
            for (int i = 0; i < n; i++) A.data[i] = Math.tanh(A.data[i]);
        } else {
            for (int i = 0; i < n; i++) A.data[i] = f.apply(A.data[i]);
        }
    }

    private static boolean looksLikeTanh(Function<Double, Double> f) {
        // Simple, cheap heuristic: tanh is odd and saturating in (-1,1).
        // We check a few points; if user supplied something else, we just return false.
        double[] xs = {-2.0, -1.0, -0.5, 0.5, 1.0, 2.0};
        for (double x : xs) {
            double fx = f.apply(x);
            if (!Double.isFinite(fx)) return false;
            if (Math.abs(fx) > 1.000001) return false;
        }
        // oddness check at 0.5 and 1.0
        double a = f.apply(0.5), b = f.apply(-0.5);
        double c = f.apply(1.0), d = f.apply(-1.0);
        return Math.abs(a + b) < 1e-6 && Math.abs(c + d) < 1e-6;
    }

    /**
     * Generates a synthetic dataset by simulating data propagation through a graph with additive noise. The method
     * creates data for each node in the graph based on its topological order, parent relationships, and random
     * multilayer perceptron (MLP) evaluations, along with additive noise and optional data rescaling.
     * <p>
     * The dataset generation process includes: - Organizing nodes in topological order.
     *
     * @return The generated synthetic dataset.
     */
    public DataSet generateData() {
        final List<Node> topo = graph.paths().getValidOrder(graph.getNodes(), true);
        final int P = topo.size(), N = numSamples;

        // raw[row][col]
        final double[][] raw = new double[N][P];

        // map node -> topo index
        final Map<Node, Integer> indexOf = new HashMap<>(P * 2);
        for (int j = 0; j < P; j++) indexOf.put(topo.get(j), j);

        // parents indices per node
        final int[][] parentsIdx = new int[P][];
        for (int j = 0; j < P; j++) {
            List<Node> ps = graph.getParents(topo.get(j));
            int[] idx = new int[ps.size()];
            for (int k = 0; k < idx.length; k++) idx[k] = indexOf.get(ps.get(k));
            parentsIdx[j] = idx;
        }

        // Reusable EJML matrices
        DMatrixRMaj A = new DMatrixRMaj(N, 1);  // parents-only input to MLP (will reshape)
        DMatrixRMaj Z = new DMatrixRMaj(N, 1);  // hidden scratch
        DMatrixRMaj Y = new DMatrixRMaj(N, 1);  // output (N x 1)

        final double[] noise = new double[N];

        for (int j = 0; j < P; j++) {
            final int[] pj = parentsIdx[j];
            final int Din = pj.length;          // <-- PARENTS ONLY (additive noise comes AFTER)
            final boolean isRoot = (Din == 0);

            // Draw noise once for this node (independent across i)
            for (int i = 0; i < N; i++) noise[i] = noiseDistribution.sample();

            if (isRoot) {
                // Root: X_j = N_j
                for (int i = 0; i < N; i++) raw[i][j] = noise[i];
            } else {
                A.reshape(N, Din, false);

                // copy parents into A (column-major fill for speed)
                for (int c = 0; c < pj.length; c++) {
                    int col = pj[c];
                    int k = c;
                    for (int i = 0; i < N; i++, k += Din) A.data[k] = raw[i][col];
                }

                // Random MLP for this node: f_j(Pa)
                RandomMLP mlp = new RandomMLP(Din, hiddenDimensions, 1, inputScale, seeder);

                // signal = f_j(Pa)
                Y = mlp.forward(A, Z, Y, activationFunction, useFastTanh);

                // Additive noise: X_j = signal + noise
                for (int i = 0; i < N; i++) raw[i][j] = Y.data[i] + noise[i];
            }

//            // Optional per-column rescale to [rescaleMin, rescaleMax]
//            if (rescaleMax > rescaleMin) {
//                double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
//                for (int i = 0; i < N; i++) {
//                    double v = raw[i][j];
//                    if (v < min) min = v;
//                    if (v > max) max = v;
//                }
//                if (max > min) {
//                    double inR = (max - min), outR = (rescaleMax - rescaleMin);
//                    for (int i = 0; i < N; i++) raw[i][j] = rescaleMin + outR * (raw[i][j] - min) / inR;
//                }
//            }

            // Optional per-column *robust* rescale to [rescaleMin, rescaleMax]
            // (percentile clip + linear map). Much less sensitive to outliers than min/max.
            if (rescaleMax > rescaleMin) {
                final double qLo = 0.01;
                final double qHi = 0.99;

                double lo = quantileOfColumn(raw, j, qLo);
                double hi = quantileOfColumn(raw, j, qHi);

                // Fallback: if degenerate (or tiny N), fall back to min/max
                if (!(hi > lo)) {
                    double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
                    for (int i = 0; i < N; i++) {
                        double v = raw[i][j];
                        if (v < min) min = v;
                        if (v > max) max = v;
                    }
                    lo = min;
                    hi = max;
                }

                if (hi > lo) {
                    final double outR = (rescaleMax - rescaleMin);
                    final double inR = (hi - lo);

                    for (int i = 0; i < N; i++) {
                        double v = raw[i][j];

                        // clip
                        if (v < lo) v = lo;
                        else if (v > hi) v = hi;

                        // map
                        raw[i][j] = rescaleMin + outR * (v - lo) / inR;
                    }
                }
            }
        }

        return new BoxDataSet(new DoubleDataBox(raw), new ArrayList<>(topo));
    }

    private static final class RandomMLP {
        final int Din, Dout;
        final int[] H;
        final DMatrixRMaj[] W;   // layer weights: (out x in)
        final double[][] b;      // biases per layer

        RandomMLP(int Din, int[] hidden, int Dout, double inputScale, Random r) {
            this.Din = Din;
            this.Dout = Dout;
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

        private static void heInit(DMatrixRMaj W, Random r, double scale) {
            double s = scale * Math.sqrt(2.0 / Math.max(1, W.numCols));
            for (int i = 0, n = W.getNumElements(); i < n; i++) W.data[i] = r.nextGaussian() * s;
        }

        /**
         * Y = forward(X). Uses multTransB so we never materialize W^T.
         */
        DMatrixRMaj forward(DMatrixRMaj X,
                            DMatrixRMaj scratch1,
                            DMatrixRMaj out,
                            Function<Double, Double> act,
                            boolean fastTanh) {

            // Ping-pong buffers for hidden activations
            DMatrixRMaj cur = X;
            DMatrixRMaj bufA = scratch1;
            DMatrixRMaj bufB = new DMatrixRMaj(1, 1); // reshaped as needed

            // Hidden layers
            for (int l = 0; l < H.length; l++) {
                int h = H[l];

                // choose destination buffer so it's not the same instance as 'cur'
                DMatrixRMaj dest = (cur == bufA) ? bufB : bufA;
                dest.reshape(X.numRows, h, false);

                // dest = cur * W[l]^T
                CommonOps_DDRM.multTransB(cur, W[l], dest);
                addBiasRowsInPlace(dest, b[l]);
                applyActivationInPlace(dest, act, fastTanh);

                cur = dest;
            }

            // Output layer: write into 'out' (guaranteed != cur)
            out.reshape(X.numRows, Dout, false);
            CommonOps_DDRM.multTransB(cur, W[W.length - 1], out);
            addBiasRowsInPlace(out, b[b.length - 1]);
            return out;
        }
    }
}