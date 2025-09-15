package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataTransforms;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.utils.MultiLayerPerceptronFunction1D;
import edu.cmu.tetrad.search.utils.MultiLayerPerceptronFunctionND;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.math3.distribution.RealDistribution;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Represents a Post-nonlinear Causal Model (Zhang and Hyvarinen, 2009, 2012).
 * <p>
 * The form of the recursive model is Xi = f2i(f1i(Pa(Xi)) + Ni)
 * <p>
 * Zhang, K., &amp; Hyvarinen, A. (2012). On the identifiability of the post-nonlinear causal model. arXiv preprint
 * arXiv:1205.2599.
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
 * Hastie, T., &amp; Tibshirani, R. (1986). "Generalized Additive Models".
 * <p>
 * Hyvarinen, A., &amp; Pajunen, P. (1999). "Nonlinear Independent Component Analysis: Existence and Uniqueness
 * Results"
 */
public class PostnonlinearCausalModel {
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
    private final int hiddenDimension;
    /**
     * A scaling factor applied to the input data in the simulation, used to introduce variability and adjust the
     * "bumpiness" of the generated causal relationships. This parameter determines how sensitive the inputs are when
     * passed through the data generation process.
     * <p>
     * It plays a critical role in shaping the nonlinearity and complexity of the causal mechanisms applied to the input
     * variables, influencing the statistical properties of the generated data.
     */
    private final double inputScale;
    /**
     * The activation function used in the post-nonlinear causal model to introduce nonlinearity to the relationships
     * between variables. This typically applies a mathematical transformation to the data, and by default, it is set to
     * the hyperbolic tangent function (Math::tanh).
     * <p>
     * This function is utilized in the data generation process, where the causal dependencies among variables are
     * influenced by the nonlinear transformation applied by this function.
     * <p>
     * Users can customize the activation function to implement alternative nonlinearities by providing their own
     * implementation through the provided setter method.
     */
    private Function<Double, Double> activationFunction = Math::tanh;
    private double coefLow = -1;
    private double coefHigh = 1;
    private boolean coefSymmetric = false;

    /**
     * Constructs a PostnonlinearCausalModel object. This model generates synthetic data based on a directed acyclic
     * graph (DAG) with causal relationships, utilizing post-nonlinear causal mechanisms. The model allows for various
     * parameter configurations to control noise, rescaling, dimensionality, and coefficient properties.
     *
     * @param graph             The directed acyclic graph (DAG) containing the causal structure for the model. Must be
     *                          acyclic; otherwise, an exception will be thrown.
     * @param numSamples        The number of samples to generate for the synthetic data. Must be positive.
     * @param noiseDistribution The distribution from which noise values are generated. Often a standard distribution,
     *                          such as Gaussian, but can be user-defined.
     * @param rescaleMin        The minimum value for rescaling the data. Must be less than or equal to rescaleMax.
     * @param rescaleMax        The maximum value for rescaling the data. Must be greater than or equal to rescaleMin.
     * @param hiddenDimension   The dimensionality of the hidden variables affecting the model's behavior.
     * @param inputScale        A scaling factor applied to the input variables before applying post-nonlinear
     *                          operations.
     * @param coefLow           The lower bound for randomly selected coefficients used in the model.
     * @param coefHigh          The upper bound for randomly selected coefficients used in the model.
     * @param coefSymmetric     A boolean flag indicating whether the randomly selected coefficients should be symmetric
     *                          around zero.
     * @throws IllegalArgumentException If the provided graph is not acyclic, the number of samples is less than one, or
     *                                  rescaleMin is greater than rescaleMax.
     */
    public PostnonlinearCausalModel(Graph graph, int numSamples, RealDistribution noiseDistribution,
                                    double rescaleMin, double rescaleMax,
                                    int hiddenDimension, double inputScale,
                                    double coefLow, double coefHigh, boolean coefSymmetric
    ) {
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

        this.graph = graph;
        this.numSamples = numSamples;
        this.noiseDistribution = noiseDistribution;
        this.rescaleMin = rescaleMin;
        this.rescaleMax = rescaleMax;
        this.hiddenDimension = hiddenDimension;
        this.inputScale = inputScale;
        this.coefLow = coefLow;
        this.coefHigh = coefHigh;
        this.coefSymmetric = coefSymmetric;
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

        // Generate data for each node in the valid order. This ensures that parents are generated before children.
        // If rescaling is selected, the data is rescaled to the specified range after each node is generated,
        // effectively enforcing a range constraint on the f1 functions.
        for (Node node : validOrder) {
            List<Node> parents = graph.getParents(node);

            // A random function from R^N -> R
            Function<double[], Double> f1 = new MultiLayerPerceptronFunctionND(
                    parents.size(), // Input dimension (R^N -> R)
                    this.hiddenDimension, // Number of hidden neurons
                    this.activationFunction, // Activation function
                    this.inputScale, // Input scale for bumpiness
                    -1 // Random seed
            )::evaluateAdjusted;

//            Function<double[], Double> f1 = new LinearFunctionND(
//                    parents.size(), // Input dimension
//                    coefLow, // CoefLow
//                    coefHigh, // CoefHigh
//                    coefSymmetric, // CoefSymmetric
//                    -1 // Random seed
//            )::evaluateAdjusted;

            for (int sample = 0; sample < numSamples; sample++) {
                int _sample = sample;
                double[] array = parents.stream().mapToDouble(parent -> data.getDouble(_sample, nodeToIndex.get(parent))).toArray();
                double value = f1.apply(array) + noiseDistribution.sample();
                data.setDouble(sample, nodeToIndex.get(node), value);
            }

            if (rescaleMin < rescaleMax) {
                DataTransforms.scale(data, rescaleMin, rescaleMax, node);
            }
        }

        // Apply invertible post-nonlinear distortion. This does not affect scaling.
        for (Node node : validOrder) {

            var func = new MultiLayerPerceptronFunction1D(
                    hiddenDimension, // Number of hidden neurons
                    inputScale, // Input scale for bumpiness
                    activationFunction, // Activation function
                    -1 // Random seed
            );
            Function<Double, Double> f2 = func::evaluate;

            for (int sample = 0; sample < numSamples; sample++) {
                double value = data.getDouble(sample, nodeToIndex.get(node));
                value = f2.apply(value);
                data.setDouble(sample, nodeToIndex.get(node), value);
            }
        }

        return data;
    }

    /**
     * Sets the activation function used in the model. The activation function is a mathematical function that
     * transforms the input and can influence the relationships and behavior within the causal model.
     *
     * @param activationFunction The function to be used as the activation function. It must be a mapping from a Double
     *                           input to a Double output, representing the non-linear transformation applied within the
     *                           model.
     */
    public void setActivationFunction(Function<Double, Double> activationFunction) {
        this.activationFunction = activationFunction;
    }
}
