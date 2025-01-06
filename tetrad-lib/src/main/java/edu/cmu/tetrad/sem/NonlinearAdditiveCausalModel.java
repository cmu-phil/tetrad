package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataTransforms;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TaylorSeries;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.math3.distribution.RealDistribution;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Represents a Nonlinear Additive Causal Model (NAC) (with some specific choices) for generating synthetic data based
 * on a directed acyclic graph (DAG). This is a nonlinear function for a linear combination of parent influences, plus
 * noise applied after the nonlinear function, where the non-linear function is represented as general Taylor series.
 * <p>
 * That is, the form of the model is Xi = fi(a1 Xi_1 + a2 Xi_2 + ... + ak Xi_k) + Ni, where g is a smooth nonlinear
 * function represented as a Taylor series.
 * <p>
 * The form specified in Peters et al. (2014) is as follows:
 * <p>
 * xi = fi(Paj) + Ni
 * <p>
 * We have interpreted f here as a nonlinear function of a linear combination of parents. Further work might be to use a
 * more general Taylor series for f, or to use a more general nonlinear function. For a more general nonlinear function,
 * consider using the Continuous Additive Noise model in Peters et al. (2014) with Gaussian process simulation.
 *
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
public class NonlinearAdditiveCausalModel {
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
     * The minimum bound for the derivative of the causal functions. This value determines the lower limit for the
     * derivative coefficients used in the Taylor series representation of the causal functions. It is used to constrain
     * the range of derivative values in the simulation process, ensuring that the functions are well-behaved and
     * realistic.
     * <p>
     * Constraints: Must be less than or equal to derivMax.
     */
    private final double derivMin;
    /**
     * The maximum bound for the derivative of the causal functions. This value determines the upper limit for the
     * derivative coefficients used in the Taylor series representation of the causal functions. It is used to constrain
     * the range of derivative values in the simulation process, ensuring that the functions are well-behaved and
     * realistic.
     */
    private final double derivMax;
    /**
     * The minimum bound for f'(0) in the causal functions. This value determines the lower limit for the first
     * derivative of the causal functions at the origin. It is used to constrain the range of first derivative values in
     * the simulation process, ensuring that the functions are well-behaved and realistic.
     * <p>
     * Constraints: Must be less than or equal to firstDerivMax.
     */
    private final double firstDerivMin;
    /**
     * The maximum bound for f'(0) in the causal functions. This value determines the upper limit for the first
     * derivative of the causal functions at the origin. It is used to constrain the range of first derivative values in
     * the simulation process, ensuring that the functions are well-behaved and realistic.
     */
    private final double firstDerivMax;
    /**
     * The degree of the Taylor series used to approximate the causal functions. This value defines the number of terms
     * included in the Taylor series representation of the causal functions, affecting the accuracy and complexity of
     * the model. Higher degrees result in more accurate approximations but may require more computational resources.
     * <p>
     * Constraints: Must be a positive integer.
     */
    private final int taylorSeriesDegree;
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
     * Constructs a additive model with the specified graph, number of samples, noise distribution, derivative bounds,
     * coefficient bounds, and Taylor series degree.
     * <p>
     * This is a private constructor that initializes the simulation with the specified parameters and parent
     * functions.
     *
     * @param graph              The directed acyclic graph (DAG) that defines the causal relationships among variables.
     *                           It must be acyclic, otherwise an IllegalArgumentException is thrown.
     * @param numSamples         The number of samples to generate for the simulation. Must be a positive integer.
     * @param noiseDistribution  The real-valued noise distribution used for simulating additive noise in the causal
     *                           mechanisms.
     * @param derivMin           The minimum bound for the derivative of the causal functions. Must be less than or
     *                           equal to derivMax.
     * @param derivMax           The maximum bound for the derivative of the causal functions.
     * @param firstDerivMin      The minimum bound for f'(0) in the causal functions. Must be less than or equal to
     *                           firstDerivMax.
     * @param firstDerivMax      The maximum bound for f'(0) in the causal functions.
     * @param taylorSeriesDegree The degree of the Taylor series used to approximate the causal functions. Must be a
     *                           positive integer.
     * @throws IllegalArgumentException if the graph contains cycles, if derivMin is greater than derivMax, if
     *                                  firstDerivMin is greater than firstDerivMax, if numSamples is less than 1, if
     *                                  taylorSeriesDegree is less than 1, or if parent functions are incomplete for the
     *                                  defined graph structure.
     */
    public NonlinearAdditiveCausalModel(Graph graph, int numSamples, RealDistribution noiseDistribution,
                                        double derivMin, double derivMax, double firstDerivMin, double firstDerivMax,
                                        int taylorSeriesDegree, double rescaleMin, double rescaleMax) {
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

        if (derivMin > derivMax) {
            throw new IllegalArgumentException("Derivative min must be less or equal to derivative max.");
        }

        if (firstDerivMin > firstDerivMax) {
            throw new IllegalArgumentException("Coefficient min must be less than or equal to coefficient max.");
        }

        if (taylorSeriesDegree < 1) {
            throw new IllegalArgumentException("Taylor series degree must be a positive integer.");
        }

        this.graph = graph;
        this.numSamples = numSamples;
        this.noiseDistribution = noiseDistribution;
        this.rescaleMin = rescaleMin;
        this.rescaleMax = rescaleMax;
        this.derivMin = derivMin;
        this.derivMax = derivMax;
        this.firstDerivMin = firstDerivMin;
        this.firstDerivMax = firstDerivMax;
        this.taylorSeriesDegree = taylorSeriesDegree;

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
     * Generates a Taylor series representation with random derivative coefficients within the specified bounds. The
     * Taylor series is defined by its degree and its derivatives, where the coefficients are sampled uniformly from the
     * given ranges.
     *
     * @param derivMin           The minimum bound for the derivative coefficients of the Taylor series (except for the
     *                           first derivative).
     * @param derivMax           The maximum bound for the derivative coefficients of the Taylor series (except for the
     *                           first derivative).
     * @param firstDerivMin      The minimum bound for f'(0) in the Taylor series.
     * @param firstDerivMax      The maximum bound for f'(0) in the Taylor series.
     * @param taylorSeriesDegree The degree of the Taylor series, defining the number of terms to include in the series.
     *                           Must be a non-negative integer.
     * @return A TaylorSeries instance with randomly generated coefficients for the specified degree and ranges.
     */
    private static @NotNull TaylorSeries getTaylorSeries(double derivMin, double derivMax, double firstDerivMin,
                                                         double firstDerivMax, int taylorSeriesDegree) {
        double[] derivatives = new double[taylorSeriesDegree + 1];
        for (int i1 = 2; i1 <= taylorSeriesDegree; i1++) {
            derivatives[i1] = RandomUtil.getInstance().nextUniform(derivMin, derivMax);
        }

        derivatives[1] = RandomUtil.getInstance().nextUniform(firstDerivMin, firstDerivMax);
        return TaylorSeries.get(derivatives, 0);
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
        Map<Node, Integer> nodeToIndex = IntStream.range(0, nodes.size()).boxed()
                .collect(Collectors.toMap(nodes::get, i -> i));

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
                        DataTransforms.scale(data, 0, 1, parent);
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
     * Sets the lower bound for the coefficient used in the model.
     *
     * @param coefLow The lower bound for the coefficient. This value determines the minimum limit for the range of
     *                coefficients in the model. Default is 0.2.
     */
    public void setCoefLow(double coefLow) {
        this.coefLow = coefLow;
    }

    /**
     * Sets the upper bound for the coefficient used in the model.
     *
     * @param coefHigh The upper bound for the coefficient. This value determines the maximum limit for the range of
     *                 coefficients in the model. Default is 1.0.
     */
    public void setCoefHigh(double coefHigh) {
        this.coefHigh = coefHigh;
    }

    /**
     * Sets whether the coefficient range in the model should be symmetric.
     *
     * @param coefSymmetric true if the range for coefficients should be symmetric about zero, false otherwise. Default
     *                      is true.
     */
    public void setCoefSymmetric(boolean coefSymmetric) {
        this.coefSymmetric = coefSymmetric;
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
        TaylorSeries taylor = getTaylorSeries(derivMin, derivMax, firstDerivMin, firstDerivMax, taylorSeriesDegree);
        Function<Double, Double> g = taylor::evaluate;

        for (int sample = 0; sample < numSamples; sample++) {
            Double apply = g.apply(data.getDouble(sample, nodeToIndex.get(node)));
            data.setDouble(sample, nodeToIndex.get(node), apply);
        }
    }

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
