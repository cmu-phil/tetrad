package edu.cmu.tetrad.sem;

import ai.djl.ndarray.NDArray;
import ai.djl.translate.TranslateException;
import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataTransforms;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.utils.MultiLayerPerceptronDjl;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.math3.distribution.RealDistribution;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Represents a Causal Perceptron Network (CPN) for generating synthetic data based on a directed acyclic graph (DAG),
 * simulated recursively.
 * <p>
 * The form of the model is Xi = fi(Pa(Xi), ei), ei _||_ Pa(Xi).
 * <p>
 * By default, the independent noise is assumed to be distributed as Beta(2, 5), though this can be adjusted. It is
 * assumed that the noise distribution is the same for all variables. In the future, this may be relaxed.
 * <p>
 * The activation function is assumed to be tanh, though this can be adjusted.
 * <p>
 * A good default for hidden dimension is 20; a good default for input scale it 5.0.
 * <p>
 * A good default for rescaling is to scale into the [-1, 1] interval, though rescaling can be turned off by setting the
 * min and max to be equal.
 * <p>
 * If is assumed that the random functions may be represented as shallow multi-layer perceptrons (MLPs).
 * <p>
 * See Zhang et al. (2015) for a reference discussion.
 * <p>
 * Goudet, O., Kalainathan, D., Caillou, P., Guyon, I., Lopez-Paz, D., &amp; Sebag, M. (2018). Learning functional
 * causal models with generative neural networks. Explainable and interpretable models in computer vision and machine
 * learning, 39-80.
 * <p>
 * Zhang, K., Wang, Z., Zhang, J., &amp; Schölkopf, B. (2015). On estimation of functional causal models: general
 * results and application to the post-nonlinear causal model. ACM Transactions on Intelligent Systems and Technology
 * (TIST), 7(2), 1-22.
 * <p>
 * Chu, T., Glymour, C., &amp; Ridgeway, G. (2008). Search for Additive Nonlinear Time Series Causal Models. Journal of
 * Machine Learning Research, 9(5).
 * <p>
 * Bühlmann, P., Peters, J., &amp; Ernest, J. (2014). "CAM: Causal Additive Models, high-dimensional order search and
 * penalized regression". The Annals of Statistics.
 * <p>
 * Peters, J., Mooij, J. M., Janzing, D., &amp; Schölkopf, B. (2014). "Causal Discovery with Continuous Additive Noise
 * Models". Journal of Machine Learning Research.
 * <p>
 * Zhang, K., &amp; Hyvarinen, A. (2012). On the identifiability of the post-nonlinear causal model. arXiv preprint
 * arXiv:1205.2599.
 * <p>
 * Hastie, T., &amp; Tibshirani, R. (1986). "Generalized Additive Models".
 * <p>
 * Hyvarinen, A., &amp; Pajunen, P. (1999). "Nonlinear Independent Component Analysis: Existence and Uniqueness
 * Results"
 */
public class CausalPerceptronNetworkDjl {
    /**
     * The directed acyclic graph (DAG) that defines the causal relationships among variables within the simulation.
     * This graph serves as the primary structure for defining causal interactions and dependencies between variables.
     * It must be acyclic for the simulation to be valid.
     * <p>
     * The `graph` is used to generate synthetic data under the assumption of additive noise models, where causal
     * mechanisms are modeled as functions of their parent variables in the graph, with noise added to capture
     * non-deterministic influences. The graph's structure is critical in determining these causal mechanisms and the
     * relationships among variables.
     */
    private final Graph graph;
    /**
     * Represents the number of samples to be generated in the additive noise simulation. This variable determines how
     * many synthetic data points will be created based on the causal relationships in the provided directed acyclic
     * graph (DAG).
     * <p>
     * Constraints: Must be a positive integer.
     */
    private final int numSamples;
    /**
     * Represents the noise distribution used in the additive simulation framework. This distribution is used to
     * introduce randomness into the simulated data, reflecting inherent noise in causal relationships. The noise is
     * applied during data generation, ensuring variability and realism in the synthetic dataset. Exogenous variables
     * are assumed to be independent and identically distributed (i.i.d) with the specified noise distribution.
     */
    private final RealDistribution noiseDistribution;
    /**
     * The lower bound used for rescaling data during the simulation process. This value is used to ensure that the
     * synthetic data is scaled within a specific range before further processing or transformations.
     */
    private final double rescaleMin;
    /**
     * The upper bound used for rescaling data during the simulation process. This value determines the maximum scale
     * applied to normalized data, ensuring it fits within the specified range during synthetic data generation.
     */
    private final double rescaleMax;
    /**
     * Represents the number of hidden neurons in a multilayer perceptron (MLP) function. This variable determines the
     * dimensionality of the hidden layer, which can affect the model's capacity to approximate complex functions in the
     * causal simulation.
     */
    private final List<Integer> hiddenDimensions;
    /**
     * A scaling factor applied to the input data in the simulation, used to introduce variability and adjust the
     * "bumpiness" of the generated causal relationships. This parameter determines how sensitive the inputs are when
     * passed through the data generation process.
     * <p>
     * It plays a critical role in shaping the nonlinearity and complexity of the causal mechanisms applied to the input
     * variables, influencing the statistical properties of the generated data.
     */
    private final float inputScale;
    /**
     * Represents the activation function used in the simulation process within the CGNN.
     * <p>
     * The activation function is applied to intermediate computations or transformations during the simulation,
     * providing a non-linear mapping that influences the resulting synthetic causal data. By default, the tangent
     * hyperbolic function (tanh) is used, though it can be customized through a setter method to support other
     * non-linear functions.
     */
    private Function<Double, Double> activationFunction = Math::tanh;

    /**
     * Constructs a CausalPerceptronNetwork that operates on a directed acyclic graph (DAG) to model causal
     * relationships with post-nonlinear causal mechanisms and custom activation functions.
     *
     * @param graph              The directed acyclic graph (DAG) representing the causal structure.
     * @param numSamples         The number of synthetic data samples to generate.
     * @param noiseDistribution  The noise distribution used for simulating random noise in the causal relationships.
     * @param rescaleMin         The minimum value for rescaling the generated data.
     * @param rescaleMax         The maximum value for rescaling the generated data.
     * @param hiddenDimensions   An array specifying the number of units in each hidden layer of the perceptron
     *                           network.
     * @param inputScale         A scaling factor to adjust the input to the network.
     * @param activationFunction The activation function applied within the perceptron network for nonlinearity.
     * @throws IllegalArgumentException If the graph contains cycles, numSamples is less than 1, rescaleMin is greater
     *                                  than rescaleMax, or any value in hiddenDimensions is less than 1.
     */
    public CausalPerceptronNetworkDjl(Graph graph, int numSamples, RealDistribution noiseDistribution,
                                      double rescaleMin, double rescaleMax, List<Integer> hiddenDimensions, double inputScale,
                                      Function<Double, Double> activationFunction) {
        if (!graph.paths().isAcyclic()) {
            throw new IllegalArgumentException("Graph contains cycles.");
        }

        if (numSamples < 1) {
            throw new IllegalArgumentException("Number of samples must be positive.");
        }

        if (rescaleMin > rescaleMax) {
            throw new IllegalArgumentException("Rescale min must be less than or equal to rescale max.");
        }

        if (rescaleMin == rescaleMax) {
            TetradLogger.getInstance().log("Rescale min and rescale max are equal. No rescaling will be applied.");
        }

        for (int hiddenDimension : hiddenDimensions) {
            if (hiddenDimension < 1) {
                throw new IllegalArgumentException("Hidden dimensions must be positive integers.");
            }
        }

        this.graph = graph;
        this.numSamples = numSamples;
        this.noiseDistribution = noiseDistribution;
        this.rescaleMin = rescaleMin;
        this.rescaleMax = rescaleMax;
        this.activationFunction = activationFunction;
        this.hiddenDimensions = hiddenDimensions;
        this.inputScale = (float) inputScale;
    }

    /**
     * Generates synthetic data based on a directed acyclic graph (DAG) with causal relationships and post-nonlinear
     * causal mechanisms. The data generation process involves simulating parent-child relationships in the graph,
     * applying noise, rescaling, and applying random piecewise linear transformations.
     *
     * @return A DataSet object containing the generated synthetic data, with samples and variables defined by the
     * structure of the provided graph and simulation parameters.
     */
    public DataSet generateData() {
        DataSet data = new BoxDataSet(new DoubleDataBox(numSamples, graph.getNodes().size()), graph.getNodes());

        List<Node> nodes = graph.getNodes();
        Map<Node, Integer> nodeToIndex = IntStream.range(0, nodes.size()).boxed().collect(Collectors.toMap(nodes::get, i -> i));

        List<Node> validOrder = graph.paths().getValidOrder(graph.getNodes(), true);

        for (Node node : validOrder) {
            List<Node> parents = graph.getParents(node);

            MultiLayerPerceptronDjl randomFunction = new MultiLayerPerceptronDjl(
                    parents.size() + 1, // Input dimension (R^3 -> R)
                    hiddenDimensions, // Number of hidden neurons
//                    this.activationFunction, // Activation function
                    "continuous", // variable type.
                    this.inputScale // Input scale for bumpiness
//                    -1 // Random seed
            );

            for (int sample = 0; sample < numSamples; sample++) {
                int _sample = sample;

                List<Float> parentsList = new java.util.ArrayList<>(parents.stream().map(parent
                        -> (float) data.getDouble(_sample, nodeToIndex.get(parent))).toList());
                parentsList.add((float) noiseDistribution.sample());

//                float[] array = parents.stream().mapToDouble(parent -> data.getDouble(_sample, nodeToIndex.get(parent))).toArray();
//                float[] array2 = new double[array.length + 1];
//                System.arraycopy(array, 0, array2, 0, array.length);
//                array2[array.length] = noiseDistribution.sample();

                // Convert parentsList to float[] array.
                float[] array = new float[parentsList.size()];
                for (int i = 0; i < parentsList.size(); i++) {
                    array[i] = parentsList.get(i);
                }

                NDArray input = randomFunction.getManager().create(array);

                try {
                    data.setDouble(sample, nodeToIndex.get(node),
                            randomFunction.forward(randomFunction.getManager(),
                                    input).toFloatArray()[0]);
                } catch (TranslateException e) {
                    throw new RuntimeException(e);
                }
            }

            if (rescaleMin < rescaleMax) {
                DataTransforms.scale(data, rescaleMin, rescaleMax, node);
            }
        }

        return data;
    }
}
