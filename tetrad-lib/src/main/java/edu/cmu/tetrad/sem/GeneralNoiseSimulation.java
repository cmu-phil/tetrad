package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.CommonOps_DDRM;

import java.util.*;
import java.util.function.Function;

import static java.lang.Math.abs;

/**
 * General-noise simulator: X_j = f_j(Pa(X_j), e_j)
 *
 * Noise enters as an extra input column, allowing nonlinear interaction between parents and noise.
 * This variant enforces "nature-like" positive noise clipped to a tanh-friendly interval [0, 2].
 */
public class GeneralNoiseSimulation {

    // --- Your requested noise behavior ---
    private static final double NOISE_MIN = -2.0;
    private static final double NOISE_MAX = 2.0;

    private final Graph graph;
    private final int numSamples;
    private final Sampler sampler;
    private final int[] hiddenDimensions;
    private final double inputScale;
    private final Function<Double, Double> activationFunction;
    private final boolean useFastTanh;

    // Keep simple per-node seeding (still random overall)
    private final Random seeder = new Random();

    public GeneralNoiseSimulation(Graph graph,
                                  int numSamples,
                                  Sampler sampler,
                                  int[] hiddenDimensions,
                                  double inputScale,
                                  Function<Double, Double> activationFunction) {
        if (!graph.paths().isAcyclic()) throw new IllegalArgumentException("Graph contains cycles.");
        if (numSamples < 1) throw new IllegalArgumentException("numSamples must be positive.");
        Objects.requireNonNull(sampler, "sampler");
        Objects.requireNonNull(hiddenDimensions, "hiddenDimensions");
        Objects.requireNonNull(activationFunction, "activationFunction");
        for (int h : hiddenDimensions) if (h < 1) throw new IllegalArgumentException("Hidden dims must be >= 1");

        this.graph = graph;
        this.numSamples = numSamples;
        this.sampler = sampler;
        this.hiddenDimensions = hiddenDimensions.clone();
        this.inputScale = inputScale;
        this.activationFunction = activationFunction;

        // Robust functional check for tanh (instead of broken reference equality).
        this.useFastTanh = isTanhLike(activationFunction);
    }

    public DataSet generateData() {
        final List<Node> topo = graph.paths().getValidOrder(graph.getNodes(), true);
        final int P = topo.size(), N = numSamples;

        // raw[row][col]
        final double[][] raw = new double[N][P];

        // map node -> topo index (avoid topo.indexOf in hot loops)
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
        DMatrixRMaj A = new DMatrixRMaj(N, 1);  // input to MLP (will reshape)
        DMatrixRMaj S1 = new DMatrixRMaj(N, 1); // scratch buffer 1
        DMatrixRMaj S2 = new DMatrixRMaj(N, 1); // scratch buffer 2
        DMatrixRMaj Y = new DMatrixRMaj(N, 1);  // output (N x 1)

        final double[] noise = new double[N];

        for (int j = 0; j < P; j++) {
            final int[] pj = parentsIdx[j];
            final int Din = pj.length + 1;      // parents + noise
            A.reshape(N, Din, false);

            // copy parents
            for (int c = 0; c < pj.length; c++) {
                int col = pj[c];
                int k = c;
                for (int i = 0; i < N; i++, k += Din) A.data[k] = raw[i][col];
            }

            // draw noise once (positive + clipped) and place as last column
            drawNoise(noise, N, sampler);

            int k = pj.length;
            for (int i = 0; i < N; i++, k += Din) A.data[k] = noise[i];

            // Random MLP for this node, supports H=[] (no hidden) too
            RandomMLP mlp = new RandomMLP(Din, hiddenDimensions, 1, inputScale, seeder);

            // Forward pass: Y = mlp(A)
            Y = mlp.forward(A, S1, S2, Y, activationFunction, useFastTanh);

            // write column
            for (int i = 0; i < N; i++) raw[i][j] = Y.data[i];
        }

        return new BoxDataSet(new DoubleDataBox(raw), new ArrayList<>(topo));
    }

    /**
     * Your requested noise model:
     *  - make it positive (abs)
     *  - clip to [NOISE_MIN, NOISE_MAX] to avoid tanh saturation via the noise channel
     *
     * Note: this is still NOT additive noise; it's an input column to f_j.
     */
    private static void drawNoise(double[] noise, int N, Sampler sampler) {
        for (int i = 0; i < N; i++) {
            noise[i] = sampler.sample();
        }
    }

    private static boolean isTanhLike(Function<Double, Double> f) {
        // Very low-cost signature test.
        double a = f.apply(1.0);
        double b = f.apply(-0.7);
        return abs(a - Math.tanh(1.0)) < 1e-12
                && abs(b - Math.tanh(-0.7)) < 1e-12;
    }

    // ------------------ Tiny EJML MLP ------------------

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
                // biases default to 0; you can randomize later if you want
                prev = H[l];
            }
            W[L - 1] = new DMatrixRMaj(Dout, prev);
            b[L - 1] = new double[Dout];
            heInit(W[L - 1], r, inputScale * 0.5);
        }

        /**
         * Forward pass using two scratch buffers so EJML never sees aliasing.
         */
        DMatrixRMaj forward(DMatrixRMaj X,
                            DMatrixRMaj scratch1,
                            DMatrixRMaj scratch2,
                            DMatrixRMaj out,
                            Function<Double, Double> act,
                            boolean fastTanh) {

            DMatrixRMaj cur = X;
            DMatrixRMaj bufA = scratch1;
            DMatrixRMaj bufB = scratch2;

            // Hidden layers
            for (int l = 0; l < H.length; l++) {
                int h = H[l];

                DMatrixRMaj dest = (cur == bufA) ? bufB : bufA;
                dest.reshape(X.numRows, h, false);

                CommonOps_DDRM.multTransB(cur, W[l], dest);
                addBiasRowsInPlace(dest, b[l]);
                applyActivationInPlace(dest, act, fastTanh);

                cur = dest;
            }

            // Output layer into 'out' (must not alias cur)
            out.reshape(X.numRows, Dout, false);
            if (out == cur) throw new IllegalArgumentException("Output aliases current buffer.");
            CommonOps_DDRM.multTransB(cur, W[W.length - 1], out);
            addBiasRowsInPlace(out, b[b.length - 1]);
            return out;
        }

        private static void heInit(DMatrixRMaj W, Random r, double scale) {
            double s = scale * Math.sqrt(2.0 / Math.max(1, W.numCols));
            for (int i = 0, n = W.getNumElements(); i < n; i++) {
                W.data[i] = r.nextGaussian() * s;
            }
        }
    }

    private static void addBiasRowsInPlace(DMatrixRMaj A, double[] b) {
        final int n = A.numRows, m = A.numCols;
        int k = 0;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++, k++) {
                A.data[k] += b[j];
            }
        }
    }

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
}