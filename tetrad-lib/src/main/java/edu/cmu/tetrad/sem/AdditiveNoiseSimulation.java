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
 * AdditiveNoiseSimulation
 * <p>
 * Generates data from an additive-noise structural causal model (ANM):
 * <p>
 * X_j = f_j(Pa(X_j)) + N_j,   with independent noise terms N_j.
 * <p>
 * Each f_j is represented by a randomly initialized MLP (parents-only input). Root nodes are generated as pure noise.
 * <p>
 * Parent values are passed through the activation function (bounding them) before entering each node's MLP, so
 * f_j is a bounded-input function by construction. No sample-dependent rescaling is performed.
 */
public class AdditiveNoiseSimulation {

    private final Graph graph;
    private final int numSamples;
    private final Sampler sampler;
    private final int[] hiddenDimensions;
    private final double inputScale;
    private final Function<Double, Double> activationFunction;
    private final boolean useFastTanh;

    /**
     * Constructs a new AdditiveNoiseSimulation instance with the specified parameters.
     *
     * @param graph              The causal graph representing the structural relationships.
     * @param numSamples         The number of data samples to generate.
     * @param sampler            The sampler for additive noise.
     * @param hiddenDimensions   The dimensions of hidden layers in the MLP.
     * @param inputScale         The scaling factor for input data.
     * @param activationFunction The activation function for the MLP.
     */
    public AdditiveNoiseSimulation(Graph graph,
                                   int numSamples,
                                   Sampler sampler,
                                   int[] hiddenDimensions,
                                   double inputScale,
                                   Function<Double, Double> activationFunction) {
        if (!graph.paths().isAcyclic()) throw new IllegalArgumentException("Graph contains cycle; need a causal order to simulate.");
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

        this.useFastTanh = RandomMlpSupport.isTanhLike(activationFunction);
    }

    /**
     * Generates a synthetic dataset by simulating data propagation through a graph with additive noise. The method
     * creates data for each node in the graph based on its topological order, parent relationships, and random
     * multilayer perceptron (MLP) evaluations, along with additive noise.
     * <p>
     * Note: the returned dataset's columns are in topological order, not
     * {@code graph.getNodes()} order.
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
        DMatrixRMaj A = new DMatrixRMaj(N, 1);   // parents-only input to MLP (will reshape)
        DMatrixRMaj S1 = new DMatrixRMaj(N, 1);  // scratch buffer 1
        DMatrixRMaj S2 = new DMatrixRMaj(N, 1);  // scratch buffer 2
        DMatrixRMaj Y = new DMatrixRMaj(N, 1);   // output (N x 1)

        final double[] noise = new double[N];

        for (int j = 0; j < P; j++) {
            final int[] pj = parentsIdx[j];
            final int Din = pj.length;          // <-- PARENTS ONLY (additive noise comes AFTER)
            final boolean isRoot = (Din == 0);

            double noiseScale = 0.5 * inputScale;

            for (int i = 0; i < N; i++) {
                double v = sampler.sample();
                noise[i] = noiseScale * v;
            }

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

                // Bound parent values before they enter the MLP.
                RandomMlpSupport.applyActivationInPlace(A, activationFunction, useFastTanh);

                // Random MLP for this node
                RandomMlp mlp = new RandomMlp(Din, hiddenDimensions, 1, inputScale,
                        0.0, activationFunction, useFastTanh);

                // signal = f_j(Pa)
                Y = mlp.forward(A, S1, S2, Y);

                // Additive noise: X_j = signal + noise
                for (int i = 0; i < N; i++) raw[i][j] = Y.data[i] + noise[i];
            }
        }

        return new BoxDataSet(new DoubleDataBox(raw), new ArrayList<>(topo));
    }
}
