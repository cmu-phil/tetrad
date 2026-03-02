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
 * General-noise simulator: X_j = f_j(Pa(X_j), e_j)
 * <p>
 * Each node's causal mechanism f_j is a randomly initialized MLP. Noise enters
 * as an extra input column, allowing nonlinear interaction between parents and noise.
 * <p>
 * To produce data resembling real scientific datasets:
 * <ul>
 *   <li>Each node draws its noise from a randomly chosen distribution (normal,
 *       skewed, or near-uniform), giving varied marginal error character.</li>
 *   <li>Each node's MLP depth is drawn randomly from 0..maxDepth, so some
 *       relationships are near-linear and others are strongly nonlinear.</li>
 *   <li>Each node's output is rescaled by a random per-node factor, giving
 *       variables different effective variances rather than all being
 *       tanh-compressed to a similar range.</li>
 * </ul>
 */
public class GeneralNoiseSimulation {

    private final Graph graph;
    private final int numSamples;
    private final RealDistribution noiseDistribution;
    private final int[] hiddenDimensions;   // max hidden layer spec; actual depth varies per node
    private final double inputScale;
    private final Function<Double, Double> activationFunction;
    private final boolean isTanh;           // reliable check, not reference equality

    private final double interceptSd;
    private final long interceptSeed;

    // Shared seeder — advances continuously across nodes, giving each node
    // a distinct but reproducible random weight matrix.
    private final Random seeder = new Random();

    public GeneralNoiseSimulation(Graph graph,
                                  int numSamples,
                                  RealDistribution noiseDistribution,
                                  int[] hiddenDimensions,
                                  double inputScale,
                                  Function<Double, Double> activationFunction) {
        this(graph, numSamples, noiseDistribution, hiddenDimensions, inputScale,
                activationFunction, 0.0, 123456789L);
    }

    public GeneralNoiseSimulation(Graph graph,
                                  int numSamples,
                                  RealDistribution noiseDistribution,
                                  int[] hiddenDimensions,
                                  double inputScale,
                                  Function<Double, Double> activationFunction,
                                  double interceptSd,
                                  long interceptSeed) {
        if (!graph.paths().isAcyclic()) throw new IllegalArgumentException("Graph contains cycles.");
        if (numSamples < 1) throw new IllegalArgumentException("numSamples must be positive.");
        Objects.requireNonNull(noiseDistribution, "noiseDistribution");
        Objects.requireNonNull(hiddenDimensions, "hiddenDimensions");
        Objects.requireNonNull(activationFunction, "activationFunction");
        for (int h : hiddenDimensions) if (h < 1) throw new IllegalArgumentException("Hidden dims must be >= 1");

        this.graph = graph;
        this.numSamples = numSamples;
        this.noiseDistribution = noiseDistribution;
        this.hiddenDimensions = hiddenDimensions.clone();
        this.inputScale = inputScale;
        this.activationFunction = activationFunction;
        this.interceptSd = Math.max(0.0, interceptSd);
        this.interceptSeed = interceptSeed;

        // Functional check for tanh: avoids the broken reference-equality trick.
        this.isTanh = (Math.abs(activationFunction.apply(1.0) - Math.tanh(1.0)) < 1e-12)
                && (Math.abs(activationFunction.apply(-0.7) - Math.tanh(-0.7)) < 1e-12);
    }

    public DataSet generateData() {
        final List<Node> topo = graph.paths().getValidOrder(graph.getNodes(), true);
        final int P = topo.size(), N = numSamples;
        final double[][] raw = new double[N][P];

        final Map<Node, Integer> indexOf = new HashMap<>(P * 2);
        for (int j = 0; j < P; j++) indexOf.put(topo.get(j), j);

        final int[][] parentsIdx = new int[P][];
        for (int j = 0; j < P; j++) {
            List<Node> ps = graph.getParents(topo.get(j));
            int[] idx = new int[ps.size()];
            for (int k = 0; k < idx.length; k++) idx[k] = indexOf.get(ps.get(k));
            parentsIdx[j] = idx;
        }

        // Per-node intercepts (only used if interceptSd > 0).
        final double[] nodeIntercept = new double[P];
        if (interceptSd > 0.0) {
            Random irng = new Random(interceptSeed);
            for (int j = 0; j < P; j++) nodeIntercept[j] = irng.nextGaussian() * interceptSd;
        }

        // Per-node variety RNG — seeded separately from weight RNG so they don't interfere.
        final Random varietyRng = new Random(seeder.nextLong());

        DMatrixRMaj A = new DMatrixRMaj(N, 1);
        DMatrixRMaj Z = new DMatrixRMaj(N, 1);
        DMatrixRMaj Y = new DMatrixRMaj(N, 1);
        final double[] noise = new double[N];

        for (int j = 0; j < P; j++) {
            final int[] pj = parentsIdx[j];
            final int Din = pj.length + 1;
            A.reshape(N, Din, false);

            // Copy parent values into input matrix.
            for (int c = 0; c < pj.length; c++) {
                int col = pj[c];
                int k = c;
                for (int i = 0; i < N; i++, k += Din) A.data[k] = raw[i][col];
            }

            // Draw noise from a per-node randomly chosen distribution.
            // This creates marginal variety: some nodes get Gaussian error,
            // some skewed, some near-uniform — as in real scientific variables.
            drawNodeNoise(noise, N, varietyRng);
            int k = pj.length;
            for (int i = 0; i < N; i++, k += Din) A.data[k] = noise[i];

            // Random depth for this node: 0..maxDepth layers.
            // Depth 0 = linear model; depth > 0 = nonlinear.
            // This means some relationships will be near-linear, others complex.
            int[] nodeDims = randomDepth(hiddenDimensions, varietyRng);

            RandomMLP mlp = new RandomMLP(Din, nodeDims, 1, inputScale, seeder);
            Y = mlp.forward(A, Z, Y, activationFunction, isTanh);

            // Per-node output scaling: gives variables different effective variances.
            // Real scientific variables rarely all have the same scale.
            double outScale = drawOutputScale(varietyRng);
            for (int i = 0; i < N; i++) Y.data[i] *= outScale;

            if (interceptSd > 0.0) {
                final double bj = nodeIntercept[j];
                for (int i = 0; i < N; i++) Y.data[i] += bj;
            }

            for (int i = 0; i < N; i++) raw[i][j] = Y.data[i];
        }

        return new BoxDataSet(new DoubleDataBox(raw), new ArrayList<>(topo));
    }

    /**
     * Draw noise for one node from a randomly chosen distribution.
     * The mix of types produces varied marginal error character across nodes:
     *   ~50% standard normal
     *   ~25% skewed (chi-squared-like, via sum of squared normals)
     *   ~25% near-uniform (average of two uniforms, giving a triangular shape)
     *
     * All are re-centered to zero mean so the causal interpretation is clean.
     */
    private static void drawNodeNoise(double[] noise, int N, Random rng) {
        int type = rng.nextInt(4); // 0,1 = normal; 2 = skewed; 3 = near-uniform

        if (type <= 1) {
            // Standard normal.
            for (int i = 0; i < N; i++) noise[i] = rng.nextGaussian();
        } else if (type == 2) {
            // Skewed: difference of two exponentials with different rates.
            // This gives a zero-mean asymmetric distribution.
            double mean = 0.0;
            for (int i = 0; i < N; i++) {
                double e1 = -Math.log(1.0 - rng.nextDouble());      // Exp(1)
                double e2 = -0.5 * Math.log(1.0 - rng.nextDouble()); // Exp(0.5)
                noise[i] = e1 - e2;
                mean += noise[i];
            }
            mean /= N;
            for (int i = 0; i < N; i++) noise[i] -= mean;
        } else {
            // Near-uniform: average of two uniforms (triangular distribution).
            double mean = 0.0;
            for (int i = 0; i < N; i++) {
                noise[i] = (rng.nextDouble() + rng.nextDouble()) - 1.0; // center at 0
                mean += noise[i];
            }
            mean /= N;
            for (int i = 0; i < N; i++) noise[i] -= mean;
        }
    }

    /**
     * Choose a random depth from 0 to hiddenDimensions.length.
     * Depth 0 returns an empty array (linear model for this node).
     * Otherwise returns the first d entries of hiddenDimensions.
     * <p>
     * Weighting: depths are drawn uniformly so all levels of nonlinearity
     * are represented equally. Adjust the weights here if you want to bias
     * toward more or less nonlinearity.
     */
    private static int[] randomDepth(int[] hiddenDimensions, Random rng) {
        return hiddenDimensions;
//        int maxDepth = hiddenDimensions.length;
//        if (maxDepth == 0) return hiddenDimensions;
//        int depth = rng.nextInt(maxDepth) + 1; // 1..maxDepth inclusive
//        return Arrays.copyOf(hiddenDimensions, depth);
    }

    /**
     * Draw a per-node output scale factor.
     * Uses a log-normal distribution so scales vary over roughly an order of
     * magnitude but are always positive. Real scientific variables measured in
     * different units or with different effect sizes will have this kind of spread.
     */
    private static double drawOutputScale(Random rng) {
        // Log-normal with mean ~1, SD ~0.6 on the log scale.
        // 90th percentile range is roughly 0.4 to 2.5.
        return Math.exp(rng.nextGaussian() * 0.5);
    }

    // ------------------- MLP (unchanged from original) -------------------

    private static final class RandomMLP {
        final int[] H;
        final DMatrixRMaj[] W;
        final double[][] b;
        final int Dout;

        RandomMLP(int Din, int[] hidden, int Dout, double inputScale, Random r) {
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
        if (fastTanh) {
            for (int i = 0; i < n; i++) A.data[i] = Math.tanh(A.data[i]);
        } else {
            for (int i = 0; i < n; i++) A.data[i] = f.apply(A.data[i]);
        }
    }
}