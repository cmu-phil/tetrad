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
 * Drop-in EJML generator for nonlinear DAG data:
 * - Batches each node’s column through a tiny random MLP
 * - Uses CommonOps_DDRM.multTransB (A * B^T) to avoid materializing transposes
 * - Detects tanh via a typed cast of the method reference (no “method reference not expected here”)
 * - No DJL / no parallelism
 */
public class CausalPerceptronNetworkDag {

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

    public CausalPerceptronNetworkDag(Graph graph,
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

        // IMPORTANT: give the method reference a target type to make == legal
        @SuppressWarnings("unchecked")
        Function<Double, Double> tanhRef = (Function<Double, Double>) (Double x) -> Math.tanh(x);
        this.useFastTanh = activationFunction == tanhRef;
    }

    /** Generate data (rows = samples, cols = nodes in topological order). */
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
        DMatrixRMaj Z = new DMatrixRMaj(N, 1);  // hidden scratch
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
            // draw noise once and place as last column
            for (int i = 0; i < N; i++) noise[i] = noiseDistribution.sample();
            int k = pj.length;
            for (int i = 0; i < N; i++, k += Din) A.data[k] = noise[i];

            // Random MLP for this node, supports H=[] (no hidden) too
            RandomMLP mlp = new RandomMLP(Din, hiddenDimensions, 1, inputScale, seeder);

            // Forward pass: Y = mlp(A)
            Y = mlp.forward(A, Z, Y, activationFunction, useFastTanh);

            // write column + rescale
            double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < N; i++) {
                double v = Y.data[i]; raw[i][j] = v;
                if (v < min) min = v; if (v > max) max = v;
            }
            if (rescaleMax > rescaleMin && max > min) {
                double inR = (max - min), outR = (rescaleMax - rescaleMin);
                for (int i = 0; i < N; i++) raw[i][j] = rescaleMin + outR * (raw[i][j] - min) / inR;
            }
        }

        return new BoxDataSet(new DoubleDataBox(raw), new ArrayList<>(topo));
    }

    // ------------------ Tiny EJML MLP ------------------

    private static final class RandomMLP {
        final int Din, Dout;
        final int[] H;
        final DMatrixRMaj[] W;   // layer weights: (out x in)
        final double[][] b;      // biases per layer

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

        /** Y = forward(X). Uses multTransB so we never materialize W^T. */
        /** Y = forward(X). Uses two scratch buffers so output != input for EJML. */
        DMatrixRMaj forward(DMatrixRMaj X,
                            DMatrixRMaj scratch1,
                            DMatrixRMaj out,
                            Function<Double, Double> act,
                            boolean fastTanh) {

            // Two ping-pong buffers for hidden activations
            DMatrixRMaj cur = X;
            DMatrixRMaj bufA = scratch1;
            DMatrixRMaj bufB = new DMatrixRMaj(1, 1); // will be reshaped

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

                // advance
                cur = dest;
            }

            // Output layer: write into 'out' (guaranteed != cur)
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
        if (fastTanh) {
            for (int i = 0; i < n; i++) A.data[i] = Math.tanh(A.data[i]);
        } else {
            for (int i = 0; i < n; i++) A.data[i] = f.apply(A.data[i]);
        }
    }
}