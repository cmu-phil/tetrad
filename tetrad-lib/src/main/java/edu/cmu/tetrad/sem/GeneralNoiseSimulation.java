package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TMath;
import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.CommonOps_DDRM;

import java.util.*;
import java.util.function.Function;

import static edu.cmu.tetrad.util.TMath.abs;

/**
 * General-noise simulator: X_j = f_j(Pa(X_j), e_j)
 * <p>
 * Noise enters as an extra input column, allowing nonlinear interaction between parents and noise.
 * This variant enforces "nature-like" positive noise clipped to a tanh-friendly interval [0, 2].
 */
public class GeneralNoiseSimulation {
    private final Graph graph;
    private final int numSamples;
    private final Sampler sampler;
    private final int[] hiddenDimensions;
    private final double inputScale;
    private final Function<Double, Double> activationFunction;
    private final boolean useFastTanh;
    private final boolean reportSaturation;
    private final double saturationAbsActivationThreshold;

    /**
     * Constructs a GeneralNoiseSimulation instance based on the provided parameters.
     *
     * @param graph The graph structure representing the network or model to be simulated.
     * @param numSamples The number of data samples to generate during the simulation.
     * @param sampler The sampler instance used to generate noise or random data.
     * @param hiddenDimensions An array defining the number of hidden units in each layer of the network.
     * @param inputScale A scaling factor applied to the input data.
     * @param activationFunction The activation function applied to the network's nodes.
     */
    public GeneralNoiseSimulation(Graph graph,
                                  int numSamples,
                                  Sampler sampler,
                                  int[] hiddenDimensions,
                                  double inputScale,
                                  Function<Double, Double> activationFunction) {
        this(graph, numSamples, sampler, hiddenDimensions, inputScale, activationFunction,
                false, 0.95);
    }

    /**
     * Constructs a GeneralNoiseSimulation instance based on the provided parameters.
     *
     * @param graph The graph structure representing the network or model to be simulated.
     *              The graph must be acyclic; otherwise, an exception will be thrown.
     * @param numSamples The number of data samples to generate during the simulation.
     *                   Must be a positive integer.
     * @param sampler The sampler instance used to generate noise or random data.
     *                Cannot be null.
     * @param hiddenDimensions An array defining the number of hidden units in each layer of the network.
     *                         Each value must be a positive integer. Cannot be null.
     * @param inputScale A scaling factor applied to the input data.
     * @param activationFunction The activation function applied to the network's nodes.
     *                           Typically used to introduce non-linearity. Cannot be null.
     * @param reportSaturation A boolean flag indicating whether to report saturation statistics
     *                         during the simulation.
     * @param saturationAbsActivationThreshold The absolute activation threshold used to determine
     *                                         activation saturation. Only applicable if
     *                                         {@code reportSaturation} is set to {@code true}.
     *                                         Must be a non-negative value.
     * @throws IllegalArgumentException If the graph contains cycles, if {@code numSamples} is less than 1,
     *                                  if any element in {@code hiddenDimensions} is less than 1, or if
     *                                  {@code saturationAbsActivationThreshold} is negative.
     * @throws NullPointerException If {@code sampler}, {@code hiddenDimensions}, or {@code activationFunction} is null.
     */
    public GeneralNoiseSimulation(Graph graph,
                                  int numSamples,
                                  Sampler sampler,
                                  int[] hiddenDimensions,
                                  double inputScale,
                                  Function<Double, Double> activationFunction,
                                  boolean reportSaturation,
                                  double saturationAbsActivationThreshold) {
        if (!graph.paths().isAcyclic()) throw new IllegalArgumentException("Graph contains cycles; need a causal order to simulate.");
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
        this.reportSaturation = reportSaturation;
        this.saturationAbsActivationThreshold = saturationAbsActivationThreshold;

        this.useFastTanh = isTanhLike(activationFunction);
    }

    /**
     * Fills the provided noise array with sampled values using the specified sampler.
     *
     * @param noise The array to be populated with sampled values.
     * @param N The number of samples to generate and store in the noise array.
     * @param sampler The sampling strategy used to generate the noise values.
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
        return abs(a - TMath.tanh(1.0)) < 1e-12
                && abs(b - TMath.tanh(-0.7)) < 1e-12;
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

    // ------------------ Tiny EJML MLP ------------------

    private static void applyActivationInPlace(DMatrixRMaj A,
                                               Function<Double, Double> f,
                                               boolean fastTanh) {
        final int n = A.getNumElements();
        if (fastTanh) {
            for (int i = 0; i < n; i++) A.data[i] = TMath.tanh(A.data[i]);
        } else {
            for (int i = 0; i < n; i++) A.data[i] = f.apply(A.data[i]);
        }
    }

    private static void printSaturationStats(String nodeName,
                                             int layerIndex,
                                             DMatrixRMaj activations,
                                             double absThreshold) {
        int total = activations.getNumElements();
        int sat = 0;

        for (int i = 0; i < total; i++) {
            if (TMath.abs(activations.data[i]) >= absThreshold) sat++;
        }

        double pct = 100.0 * sat / TMath.max(1, total);

        System.out.printf(
                Locale.US,
                "GeneralNoiseSimulation saturation: node=%s layer=%d threshold=|a|>=%.3f saturated=%d/%d (%.2f%%)%n",
                nodeName, layerIndex, absThreshold, sat, total, pct
        );
    }

    /**
     * Generates a dataset based on the current graph structure, incorporating
     * random noise and a multi-layer perceptron (MLP) for each node in the graph.
     * The method uses topological sorting to determine the order of computation,
     * propagates values through the graph, and applies noise to ensure variability.
     *
     * @return A DataSet containing the computed values for all nodes in the graph
     *         and their corresponding topological order.
     */
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
            RandomMLP mlp = new RandomMLP(Din, hiddenDimensions, 1, inputScale);

            // Forward pass: Y = mlp(A)
//            Y = mlp.forward(A, S1, S2, Y, activationFunction, useFastTanh);

            Y = mlp.forward(
                    A, S1, S2, Y,
                    activationFunction, useFastTanh,
                    reportSaturation,
                    saturationAbsActivationThreshold,
                    topo.get(j).getName()
            );

            // write column
            for (int i = 0; i < N; i++) raw[i][j] = Y.data[i];
        }

        return new BoxDataSet(new DoubleDataBox(raw), new ArrayList<>(topo));
    }

    private static final class RandomMLP {
        final int Din, Dout;
        final int[] H;
        final DMatrixRMaj[] W;   // layer weights: (out x in)
        final double[][] b;      // biases per layer

        RandomMLP(int Din, int[] hidden, int Dout, double inputScale) {
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
                xavierInit(W[l], inputScale);
                // biases default to 0; you can randomize later if you want
                prev = H[l];
            }
            W[L - 1] = new DMatrixRMaj(Dout, prev);
            b[L - 1] = new double[Dout];
            xavierInit(W[L - 1], inputScale * 0.5);
        }

        private static void heInit(DMatrixRMaj W,  double scale) {
            double s = scale * TMath.sqrt(2.0 / TMath.max(1, W.numCols));
            for (int i = 0, n = W.getNumElements(); i < n; i++) {
                W.data[i] = RandomUtil.getInstance().nextGaussian() * s;
            }
        }

        private static void xavierInit(DMatrixRMaj W, double scale) {
            int fanIn = TMath.max(1, W.numCols);
            int fanOut = TMath.max(1, W.numRows);

            double std = scale * TMath.sqrt(2.0 / (fanIn + fanOut));

            for (int i = 0, n = W.getNumElements(); i < n; i++) {
                W.data[i] = RandomUtil.getInstance().nextGaussian() * std;
            }
        }

        /**
         * Forward pass using two scratch buffers so EJML never sees aliasing.
         */
        DMatrixRMaj forward(DMatrixRMaj X,
                            DMatrixRMaj scratch1,
                            DMatrixRMaj scratch2,
                            DMatrixRMaj out,
                            Function<Double, Double> act,
                            boolean fastTanh,
                            boolean reportSaturation,
                            double saturationAbsActivationThreshold,
                            String nodeName) {

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

                if (reportSaturation) {
                    printSaturationStats(nodeName, l + 1, dest, saturationAbsActivationThreshold);
                }

                cur = dest;
            }

            // Output layer into 'out' (must not alias cur)
            out.reshape(X.numRows, Dout, false);
            if (out == cur) throw new IllegalArgumentException("Output aliases current buffer.");
            CommonOps_DDRM.multTransB(cur, W[W.length - 1], out);
            addBiasRowsInPlace(out, b[b.length - 1]);
            return out;
        }
    }
}