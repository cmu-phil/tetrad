package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.sem.RandomMlpSupport.RandomMlp;
import org.ejml.data.DMatrixRMaj;

import java.util.*;
import java.util.function.Function;

/**
 * General-noise simulator: X_j = f_j(Pa(X_j), e_j)
 * <p>
 * Noise enters as an extra input column, allowing nonlinear interaction between parents and noise.
 * The noise distribution is determined entirely by the supplied {@link Sampler}; no clipping or
 * positivity constraint is imposed here.
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
     * @param graph              The graph structure representing the network or model to be simulated.
     * @param numSamples         The number of data samples to generate during the simulation.
     * @param sampler            The sampler instance used to generate noise or random data.
     * @param hiddenDimensions   An array defining the number of hidden units in each layer of the network.
     * @param inputScale         A scaling factor applied to the input data.
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
     * @param graph                            The graph structure representing the network or model to be simulated.
     *                                         The graph must be acyclic; otherwise, an exception will be thrown.
     * @param numSamples                       The number of data samples to generate during the simulation.
     *                                         Must be a positive integer.
     * @param sampler                          The sampler instance used to generate noise or random data.
     *                                         Cannot be null.
     * @param hiddenDimensions                 An array defining the number of hidden units in each layer of the network.
     *                                         Each value must be a positive integer. Cannot be null.
     * @param inputScale                       A scaling factor applied to the input data.
     * @param activationFunction               The activation function applied to the network's nodes.
     *                                         Typically used to introduce non-linearity. Cannot be null.
     * @param reportSaturation                 A boolean flag indicating whether to report saturation statistics
     *                                         during the simulation.
     * @param saturationAbsActivationThreshold The absolute activation threshold used to determine
     *                                         activation saturation. Only applicable if
     *                                         {@code reportSaturation} is set to {@code true}.
     *                                         Must be a non-negative value.
     * @throws IllegalArgumentException If the graph contains cycles, if {@code numSamples} is less than 1,
     *                                  if any element in {@code hiddenDimensions} is less than 1, or if
     *                                  {@code saturationAbsActivationThreshold} is negative.
     * @throws NullPointerException     If {@code sampler}, {@code hiddenDimensions}, or {@code activationFunction} is null.
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
        if (saturationAbsActivationThreshold < 0) throw new IllegalArgumentException("saturationAbsActivationThreshold must be non-negative.");

        this.graph = graph;
        this.numSamples = numSamples;
        this.sampler = sampler;
        this.hiddenDimensions = hiddenDimensions.clone();
        this.inputScale = inputScale;
        this.activationFunction = activationFunction;
        this.reportSaturation = reportSaturation;
        this.saturationAbsActivationThreshold = saturationAbsActivationThreshold;

        this.useFastTanh = RandomMlpSupport.isTanhLike(activationFunction);
    }

    /**
     * Fills the provided noise array with sampled values using the specified sampler.
     *
     * @param noise   The array to be populated with sampled values.
     * @param n       The number of samples to generate and store in the noise array.
     * @param sampler The sampling strategy used to generate the noise values.
     */
    private static void drawNoise(double[] noise, int n, Sampler sampler) {
        for (int i = 0; i < n; i++) {
            noise[i] = sampler.sample();
        }
    }

    /**
     * Generates a dataset based on the current graph structure, incorporating
     * random noise and a multi-layer perceptron (MLP) for each node in the graph.
     * The method uses topological sorting to determine the order of computation,
     * propagates values through the graph, and applies noise to ensure variability.
     * <p>
     * Note: the returned dataset's columns are in topological order, not
     * {@code graph.getNodes()} order.
     *
     * @return A DataSet containing the computed values for all nodes in the graph
     * and their corresponding topological order.
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

            // draw noise once and place as last column
            drawNoise(noise, N, sampler);

            int k = pj.length;
            for (int i = 0; i < N; i++, k += Din) A.data[k] = noise[i];

            // Random MLP for this node, supports H=[] (no hidden) too
            RandomMlp mlp = new RandomMlp(Din, hiddenDimensions, 1, inputScale,
                    0.0, activationFunction, useFastTanh);

            // Forward pass: Y = mlp(A)
            Y = mlp.forward(A, S1, S2, Y,
                    reportSaturation, saturationAbsActivationThreshold,
                    topo.get(j).getName());

            // write column
            for (int i = 0; i < N; i++) raw[i][j] = Y.data[i];
        }

        return new BoxDataSet(new DoubleDataBox(raw), new ArrayList<>(topo));
    }
}
