package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataTransforms;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.utils.MultiLayerPerceptronFunction1D;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.math3.distribution.RealDistribution;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Represents a nonlinear function of linear model for generating synthetic data based on a directed acyclic graph
 * (DAG). The error may be included additively or post-nonlinearly--that is, the nonlinear function may be taken either
 * before (additively) or after (post-nonlinearly) an independent draw from the noise distribution is added.
 * <p>
 * If additively, the model is Xi = fi(a1 Xi_1 + a2 Xi_2 + ... + ak Xi_k) + Ni.
 * <p>
 * If post-nonlinearly, the model is Xi = fi(a1 Xi_1 + a2 Xi_2 + ... + ak Xi_k + Ni).
 * <p>
 * Additively, the model is a special case of the nonlinear additive model in Hoyer et al. (2008),
 * <p>
 * xi = fi(Paj) + Ni
 * <p>
 * We have interpreted fi here as a nonlinear function over a linear combination of parents.
 * <p>
 * Post-nonlinearly, the error is added inside the fi function. In the bivariate case, this is a special case of the
 * post-nonlinear model in Zhang and Hyvarinen (2012); we are using the term 'post-nonlinear' in a more general sense
 * here, allowing for arbitrary smooth nonlinear functions.
 * <p>
 * In either case, we choose random functions fi using multilayer perceptrons from R to R with one hidden layer and tanh
 * activation functions. The number of hidden neurons is a parameter.
 * <p>
 * Hoyer, P., Janzing, D., Mooij, J. M., Peters, J., &amp; Schölkopf, B. (2008). Nonlinear causal discovery with
 * additive noise models. Advances in neural information processing systems, 21.
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
public class NonlinearFunctionOfLinear {
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
     * A mapping structure that establishes relationships between nodes in a directed acyclic graph (DAG) and their
     * corresponding parent nodes, associating each parent-child relationship with a functional transformation. This is
     * a two-level map where the top-level key represents a child node, the second-level map contains parent nodes of
     * the child, and the associated value defines the functional dependence of the child on the parent. The functions
     * encapsulate post-nonlinear relationships between nodes and are used in the generation of synthetic data based on
     * the specified causal graph.
     * <p>
     * This map is initialized and populated during the setup of the data generation process, ensuring that every
     * parent-child relationship in the graph is associated with the appropriate functional dependencies.
     */
    private final Map<Node, Map<Node, Function<Double, Double>>> parentFunctions = new HashMap<>();
    /**
     * Represents the dimensionality of the hidden layer used in the nonlinear transformation within the context of the
     * causal simulation. This value defines the number of latent variables or features used when applying
     * transformations or creating parent-child relationships in the data generation process.
     * <p>
     * The hidden dimension influences the complexity of the synthetic data being generated and affects the structure of
     * nonlinear functions derived during the simulation. It is typically set during the instantiation of the
     * `NonlinearFunctionOfLinear` object and remains immutable throughout the object's lifecycle.
     */
    private final int hiddenDimension;
    /**
     * Represents a scaling factor used to adjust the inputs of the nonlinear function. Determines the degree of scaling
     * applied to input values before processing through the modeled nonlinear function. Affects the magnitude of input
     * transformation operations, thereby influencing the behavior of the synthetic data generation.
     */
    private final double inputScale;
    /**
     * The lower bound for the random coefficient in the model.
     * <p>
     * This variable specifies the minimum value for the coefficient used in simulations involving random distortions or
     * transformations. It is used as part of the range to sample coefficients for defining nonlinear behaviors in the
     * post-nonlinear causal mechanisms.
     */
    private double coefLow = 0.2;
    /**
     * Represents the upper bound for the random coefficient sampling range used in generating synthetic data or
     * defining transformations in the post-nonlinear model. This value defines the maximum allowable magnitude for
     * coefficients when they are randomly generated or set within the model.
     * <p>
     * The specific role of this variable depends on its usage in the model's context, such as defining bounds for
     * transformation functions or scaling.
     */
    private double coefHigh = 1.0;
    /**
     * Indicates whether the coefficients in the model should be symmetric. Symmetry of coefficients may influence the
     * behavior and properties of the Taylor series and any transformations derived from it.
     * <p>
     * When set to true, the coefficients are adjusted to enforce symmetry, which may be useful for ensuring certain
     * theoretical or practical constraints in simulation or modeling tasks.
     * <p>
     * This field affects the generation process of the coefficients in the context of the model's operations, such as
     * polynomial approximations or transformations.
     */
    private boolean coefSymmetric = true;
    /**
     * The type of distortion applied to the data in the model. This parameter controls the nature of the distortions
     * applied to the synthetic data during the simulation process, affecting the behavior and properties of the
     * generated data.
     */
    private DistortionType distortionType = DistortionType.POST_NONLINEAR;

    /**
     * Constructs a NonlinearFunctionOfLinear instance, which generates synthetic data based on a directed acyclic graph
     * (DAG) using post-nonlinear causal relationships and associated modeling parameters.
     *
     * @param graph             The directed acyclic graph defining causal relationships between variables. Must not
     *                          contain cycles.
     * @param numSamples        The number of samples to be generated. Must be a positive integer.
     * @param noiseDistribution The distribution from which the random noise will be sampled.
     * @param rescaleMin        The minimum value for rescaling the generated data. Must be less than or equal to
     *                          rescaleMax.
     * @param rescaleMax        The maximum value for rescaling the generated data. Must be greater than or equal to
     *                          rescaleMin.
     * @param coefLow           The lower bound for the uniform sampling of coefficients.
     * @param coefHigh          The upper bound for the uniform sampling of coefficients.
     * @param coefSymmetric     Indicates whether coefficient sampling should be symmetric around zero.
     * @param hiddenDimension   The dimensionality of hidden variables in the model.
     * @param inputScale        A scaling factor applied to the inputs of the nonlinear functions.
     */
    public NonlinearFunctionOfLinear(Graph graph, int numSamples, RealDistribution noiseDistribution,
                                     double rescaleMin, double rescaleMax, double coefLow, double coefHigh, boolean coefSymmetric,
                                     int hiddenDimension, double inputScale) {
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
        this.coefLow = coefLow;
        this.coefHigh = coefHigh;
        this.coefSymmetric = coefSymmetric;
        this.hiddenDimension = hiddenDimension;
        this.inputScale = inputScale;

        for (Node child : this.graph.getNodes()) {
            Map<Node, Function<Double, Double>> parentFunctions1 = new HashMap<>();
            for (Node parent : this.graph.getParents(child)) {
                final double r = getCoef();
                parentFunctions1.put(parent, x -> r * x);
            }

            this.parentFunctions.put(child, parentFunctions1);
        }
    }

    /**
     * Generates a random coefficient uniformly sampled in the range (-1.0, 1.0) with an absolute value that is at least
     * 0.2. The method continues sampling randomly until the condition on the absolute value is satisfied.
     *
     * @return A random double value in the range (-1.0, 1.0), such that its absolute value is at least 0.2.
     */
    private double getCoef() {
        double r = RandomUtil.getInstance().nextUniform(coefLow, coefHigh);

        if (coefSymmetric) {
            r *= RandomUtil.getInstance().nextDouble() > 0.5 ? 1 : -1;
        }

        return r;
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
            Map<Node, Function<Double, Double>> nodeFunctionMap = parentFunctions.get(node);

            for (int sample = 0; sample < numSamples; sample++) {
                if (parents.isEmpty()) {
                    data.setDouble(sample, nodeToIndex.get(node), noiseDistribution.sample());
                } else {
                    double linearCombination = 0;
                    for (Node parent : parents) {
                        double aDouble = data.getDouble(sample, nodeToIndex.get(parent));
                        linearCombination += nodeFunctionMap.get(parent).apply(aDouble);
                    }
                    data.setDouble(sample, nodeToIndex.get(node), linearCombination);
                }
            }

            if (distortionType == DistortionType.PRE_NOISE) {
                distort(node, data, nodeToIndex);
            }

            for (int sample = 0; sample < numSamples; sample++) {
                data.setDouble(sample, nodeToIndex.get(node), data.getDouble(sample, nodeToIndex.get(node)) + noiseDistribution.sample());
            }

            if (distortionType == DistortionType.POST_NONLINEAR) {
                distort(node, data, nodeToIndex);
            }

            if (rescaleMin < rescaleMax) {
                DataTransforms.scale(data, rescaleMin, rescaleMax, node);
            }
        }

        return data;
    }

    /**
     * Applies distortion to the values of a specified node in the given dataset using a Taylor series approximation of
     * the transformation function. This method modifies the dataset in place.
     *
     * @param node        The node whose data values will be distorted. Represents a variable in the graph.
     * @param data        The dataset containing the samples and variables to be distorted.
     * @param nodeToIndex A mapping of nodes to their corresponding column indices in the dataset.
     */
    private void distort(Node node, DataSet data, Map<Node, Integer> nodeToIndex) {
        Function<Double, Double> g = new MultiLayerPerceptronFunction1D(
                this.hiddenDimension, // Number of hidden neurons
                this.inputScale, // Input scaling, affects bumpiness of function
                Math::tanh, // Activation function
                -1 // Random seed
        )::evaluate;

//        g = Math::tanh;
//        g = x -> Math.max(0, x);
//        g = x -> Math.max(0.01 * x, x);

        for (int sample = 0; sample < numSamples; sample++) {
            double y = g.apply(data.getDouble(sample, nodeToIndex.get(node))) / rescaleMax;
            data.setDouble(sample, nodeToIndex.get(node), y);
        }
    }

    /**
     * Sets the type of distortion to be applied in the nonlinear additive causal model.
     *
     * @param distortionType The DistortionType to set. Determines the distortion mechanism used to modify the data in
     *                       the causal model. Valid values are: - NONE: No distortion applied. - PRE_NOISE: Noise added
     *                       after the nonlinear distortion. - POST_NONLINEAR: Noise added before the nonlinear
     *                       distortion.
     */
    public void setDistortionType(DistortionType distortionType) {
        this.distortionType = distortionType;
    }

    /**
     * Represents the type of distortion applied in a nonlinear additive causal model. This enumeration defines the
     * available distortion mechanisms that can be used to simulate post-nonlinear causal relationships by modifying the
     * data.
     */
    public enum DistortionType {
        /**
         * No distortion applied to the data.
         */
        NONE,
        /**
         * Noise added after the nonlinear distortion.
         */
        PRE_NOISE,
        /**
         * Noise added before the nonlinear distortion.
         */
        POST_NONLINEAR
    }
}
