package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.sem.RandomMlpSupport.RandomMlp;
import edu.cmu.tetrad.util.TMath;
import org.ejml.data.DMatrixRMaj;

import java.util.*;
import java.util.function.Function;

/**
 * <p><b>GeneralAdditiveModel</b></p>
 *
 * <p>
 * Simulates a genuine generalized additive structural equation model of the form
 * </p>
 *
 * <pre>
 *     X_j = sum_{k in Pa(j)} f_{jk}(X_k) + e_j
 * </pre>
 *
 * <p>
 * where each {@code f_{jk}} is a separate randomly initialized univariate neural
 * network (a 1-input MLP), and {@code e_j} is an independent noise term drawn
 * from the supplied {@link Sampler}.
 * </p>
 *
 * <p>
 * Root nodes are generated as pure noise. For non-root nodes, each parent
 * contributes through its own random subnet, preserving additivity
 * across parent effects.
 * </p>
 *
 * <p>
 * NOTE: When input standardization is enabled (the default), each parent column
 * is z-scored using the <em>realized sample's</em> mean and standard deviation
 * before entering its subnet. This couples rows within a dataset: strictly, the
 * effective structural functions depend (weakly, and vanishingly as the sample
 * size grows) on the sampled data, so the simulator is not "SCM-pure." Disable
 * standardization via {@link #setInputStandardize(boolean)} if this matters.
 * </p>
 *
 * <p>
 * This class is intended to align with the ANM/GNM simulators while preserving
 * the defining additive structure of a GAM.
 * </p>
 */
public final class GeneralAdditiveModel {

    private final Graph graph;
    private final int numSamples;
    private final Sampler sampler;

    private int[] hiddenDimensions = new int[]{8, 8};
    private double inputScale = 1.0;
    private boolean inputStandardize = true;
    private Function<Double, Double> activationFunction = TMath::tanh;
    private boolean useFastTanh = true;

    /**
     * Constructs a new generalized additive model simulator.
     *
     * @param graph      DAG over which data are simulated.
     * @param numSamples Number of rows to simulate.
     * @param sampler    Noise sampler used for the additive errors.
     */
    public GeneralAdditiveModel(Graph graph, int numSamples, Sampler sampler) {
        if (graph == null) throw new NullPointerException("graph");
        if (!graph.paths().isAcyclic()) {
            throw new IllegalArgumentException("Graph contains cycles; need a causal order to simulate.");
        }
        if (numSamples < 1) {
            throw new IllegalArgumentException("numSamples must be positive.");
        }
        if (sampler == null) throw new NullPointerException("sampler");

        this.graph = graph;
        this.numSamples = numSamples;
        this.sampler = sampler;
    }

    // --------------------------------------------------------------------
    // Configuration
    // --------------------------------------------------------------------

    /**
     * Sets the hidden dimensions for the model. Hidden dimensions represent the sizes
     * of the hidden layers used within the model. All dimensions must be greater than or equal to 1.
     *
     * @param hiddenDimensions the sizes of the hidden layers of the model, each must be >= 1
     * @return the updated GeneralAdditiveModel instance with the specified hidden dimensions
     * @throws NullPointerException     if the hiddenDimensions array is null
     * @throws IllegalArgumentException if any dimension in hiddenDimensions is less than 1
     */
    public GeneralAdditiveModel setHiddenDimensions(int... hiddenDimensions) {
        Objects.requireNonNull(hiddenDimensions, "hiddenDimensions");
        for (int h : hiddenDimensions) {
            if (h < 1) throw new IllegalArgumentException("Hidden dims must be >= 1.");
        }
        this.hiddenDimensions = hiddenDimensions.clone();
        return this;
    }

    /**
     * Sets the input scale for the model. The input scale must be a finite, positive value.
     *
     * @param inputScale the scaling factor to be applied to the input data, must be finite and greater than 0
     * @return the updated GeneralAdditiveModel instance with the specified input scale
     * @throws IllegalArgumentException if the input scale is not finite or less than or equal to 0
     */
    public GeneralAdditiveModel setInputScale(double inputScale) {
        if (!Double.isFinite(inputScale) || inputScale <= 0.0) {
            throw new IllegalArgumentException("inputScale must be finite and > 0.");
        }
        this.inputScale = inputScale;
        return this;
    }

    /**
     * Sets whether the input data should be standardized for the model.
     * Standardizing inputs typically involves scaling them to have a mean of
     * zero and a standard deviation of one, which can improve model performance
     * and stability in certain cases. See the class javadoc for the
     * row-coupling caveat this introduces.
     *
     * @param inputStandardize a boolean indicating whether input standardization
     *                         should be applied (true for standardization, false otherwise)
     * @return the updated GeneralAdditiveModel instance with the specified
     * input standardization behavior
     */
    public GeneralAdditiveModel setInputStandardize(boolean inputStandardize) {
        this.inputStandardize = inputStandardize;
        return this;
    }

    /**
     * Sets the activation function for the generalized additive model. The activation function
     * transforms data through a specified mapping, typically used within neural networks or
     * simulation frameworks. If the specified activation function is exactly the hyperbolic
     * tangent function, an optimization flag is set to use a faster implementation.
     *
     * @param activationFunction the function to be used as the activation function, must be non-null
     * @return the updated GeneralAdditiveModel instance with the specified activation function
     * @throws NullPointerException if the activationFunction is null
     */
    public GeneralAdditiveModel setActivationFunction(Function<Double, Double> activationFunction) {
        Objects.requireNonNull(activationFunction, "activationFunction");
        this.activationFunction = activationFunction;
        this.useFastTanh = RandomMlpSupport.isTanhLike(activationFunction);
        return this;
    }

    // --------------------------------------------------------------------
    // Main generation
    // --------------------------------------------------------------------

    /**
     * Generates a simulated dataset according to the structure of the graph and the specified parameters
     * of the General Additive Model. The dataset is created by sampling noise, combining contributions
     * from parent nodes in the graph using subnet evaluations, and optionally applying input standardization
     * and activation functions.
     * <p>
     * Note: the returned dataset's columns are in topological order, not
     * {@code graph.getNodes()} order.
     *
     * @return a DataSet object containing the simulated data and associated graph node ordering
     */
    public DataSet generate() {
        final List<Node> topo = graph.paths().getValidOrder(graph.getNodes(), true);
        final int p = topo.size();
        final int n = numSamples;

        final double[][] raw = new double[n][p];

        final Map<Node, Integer> indexOf = new HashMap<>(2 * p);
        for (int j = 0; j < p; j++) {
            indexOf.put(topo.get(j), j);
        }

        final int[][] parentsIdx = new int[p][];
        for (int j = 0; j < p; j++) {
            List<Node> parents = graph.getParents(topo.get(j));
            int[] idx = new int[parents.size()];
            for (int k = 0; k < parents.size(); k++) {
                idx[k] = indexOf.get(parents.get(k));
            }
            parentsIdx[j] = idx;
        }

        // Reusable buffers for one-parent subnet evaluations.
        DMatrixRMaj xCol = new DMatrixRMaj(n, 1);
        DMatrixRMaj scratch1 = new DMatrixRMaj(n, 1);
        DMatrixRMaj scratch2 = new DMatrixRMaj(n, 1);
        DMatrixRMaj out = new DMatrixRMaj(n, 1);

        final double[] noise = new double[n];

        for (int j = 0; j < p; j++) {
            final int[] pj = parentsIdx[j];

            // draw additive error term e_j
            for (int i = 0; i < n; i++) {
                noise[i] = sampler.sample();
            }

            if (pj.length == 0) {
                // Root: X_j = e_j
                for (int i = 0; i < n; i++) {
                    raw[i][j] = noise[i];
                }
                continue;
            }

            // Start with additive noise.
            for (int i = 0; i < n; i++) {
                raw[i][j] = noise[i];
            }

            // Sum separate subnet contributions f_jk(X_k) over parents.
            for (int parentIndex : pj) {
                // Build 1-column input from this parent.
                for (int i = 0; i < n; i++) {
                    xCol.data[i] = raw[i][parentIndex];
                }

                if (inputStandardize) {
                    RandomMlpSupport.zScoreColumnsInPlace(xCol);
                }

                // Bound the (standardized) parent value before the subnet,
                // matching the ANM style.
                RandomMlpSupport.applyActivationInPlace(xCol, activationFunction, useFastTanh);

                RandomMlp subnet = new RandomMlp(1, hiddenDimensions, 1, inputScale,
                        0.0, activationFunction, useFastTanh);

                out = subnet.forward(xCol, scratch1, scratch2, out);

                for (int i = 0; i < n; i++) {
                    raw[i][j] += out.data[i];
                }
            }
        }

        return new BoxDataSet(new DoubleDataBox(raw), new ArrayList<>(topo));
    }
}
