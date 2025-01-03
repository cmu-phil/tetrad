package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphSaveLoadUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.util.RandomPiecewiseLinearBijective;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TaylorSeries;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.math3.distribution.BetaDistribution;
import org.apache.commons.math3.distribution.RealDistribution;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Represents an Additive Model for generating synthetic data based on a directed acyclic graph (DAG) as a sum of smooth
 * nonlinear influences from parents of a node to the node. Optionally, a distortion function may be specified for each
 * node to mask the additivity of the node, to be applied after the sum of the parent influences and before the addition
 * of noise. With or without a distortion function, independent noise is added to each node as it is generated, so that
 * the function will be an additive error model in each case. That is, each node is a nonlinear function of the parents
 * plus noise).
 * <p>
 * This simulation without the distortion implements the Causal Additive Model as given in Buhlmann et al. (2014) and
 * Chu et al. (2008). Zhang and Hyvarinen (2012) add a post-nonlinear distortion to the model after the error has been
 * added; this is not included here, though perhaps we may in the future allow it.
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
public class NonlinearAdditiveModel {
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
     * A flag indicating whether distortions should be applied to the data points before the introduction of error noise
     * in the model.
     * <p>
     * When set to true, the pre-error distortion functions are used to modify the values of nodes before any noise is
     * added. This setting is relevant to the simulation process and affects how the synthetic data is generated in the
     * model.
     * <p>
     * By default, this flag is set to false, meaning no distortions are applied before the addition of noise.
     */
    private boolean distortPreError = false;
    /**
     * Indicates whether post-error distortion should be applied to the data in the model. When set to true, additional
     * distortions are applied after the error terms are introduced to simulate post-nonlinear mechanisms. This
     * parameter works in conjunction with other distortion settings to control the nature of synthetic data generation
     * and causal simulation.
     * <p>
     * This is the "post-nonlinear" distortion.
     */
    private boolean distortPostError = false;

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
    public NonlinearAdditiveModel(Graph graph, int numSamples, RealDistribution noiseDistribution,
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

        this.graph = graph;
        this.numSamples = numSamples;
        this.noiseDistribution = noiseDistribution;
        this.rescaleMin = rescaleMin;
        this.rescaleMax = rescaleMax;

        if (derivMin > derivMax) {
            throw new IllegalArgumentException("Derivative min must be less or equal to derivative max.");
        }

        if (firstDerivMin > firstDerivMax) {
            throw new IllegalArgumentException("Coefficient min must be less than or equal to coefficient max.");
        }

        if (taylorSeriesDegree < 1) {
            throw new IllegalArgumentException("Taylor series degree must be a positive integer.");
        }

        for (Node child : this.graph.getNodes()) {
            Map<Node, Function<Double, Double>> parentFunctions1 = new HashMap<>();
            for (Node parent : this.graph.getParents(child)) {
                TaylorSeries taylor = getTaylorSeries(firstDerivMin, firstDerivMax, derivMin, derivMax, taylorSeriesDegree);
                parentFunctions1.put(parent, taylor::evaluate);
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
        for (int i1 = 0; i1 <= taylorSeriesDegree; i1++) {
            derivatives[i1] = RandomUtil.getInstance().nextUniform(derivMin, derivMax);
        }

        derivatives[1] = RandomUtil.getInstance().nextUniform(firstDerivMin, firstDerivMax);
        return TaylorSeries.get(derivatives, 0);
    }

    /**
     * The main method demonstrates the generation of synthetic data based on a random graph, saving the data to a file,
     * and printing the dataset and graph structure.
     *
     * @param args the command-line arguments, not used in this implementation.
     */
    public static void main(String[] args) {

        int numNodes = 5;

        List<Node> nodes = new ArrayList<>();

        for (int i = 0; i < numNodes; i++) {
            nodes.add(new ContinuousVariable("X" + i));
        }

        Graph graph = RandomGraph.randomGraph(nodes, 0, 5, 100,
                100, 100, false);

        // Generate data
        NonlinearAdditiveModel generator = new NonlinearAdditiveModel(graph, 1000,
                new BetaDistribution(2, 5), -1, 1,
                0.1, 1, 5, -1, 1);
        DataSet data = generator.generateData();

        // Save the data to a file.
        try {
            File file = new File("data_am.txt");
            FileWriter writer = new FileWriter(file);
            writer.write(data.toString());
            writer.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Save the graph to a file.
        GraphSaveLoadUtils.saveGraph(graph, new File("graph_am.txt"), false);
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
        Map<Node, Integer> nodeToIndex = IntStream.range(0, nodes.size())
                .boxed()
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
                        linearCombination += nodeFunctionMap.get(parent).apply(data.getDouble(sample, nodeToIndex.get(parent)));
                    }
                    data.setDouble(sample, nodeToIndex.get(node), linearCombination);
                }
            }

            if (distortPreError) {
                distort(node, data, nodeToIndex);
            }

            for (int sample = 0; sample < numSamples; sample++) {
                data.setDouble(sample, nodeToIndex.get(node), data.getDouble(sample, nodeToIndex.get(node)) + noiseDistribution.sample());
            }

            if (rescaleMin < rescaleMax) {
                DataTransforms.scale(data, rescaleMin, rescaleMax, node);
            }

            if (distortPostError) {
                distort(node, data, nodeToIndex);
            }
        }

        return data;
    }

    private void distort(Node node, DataSet data, Map<Node, Integer> nodeToIndex) {
        RandomPiecewiseLinearBijective rpl = new RandomPiecewiseLinearBijective(50, RandomUtil.getInstance().nextLong());
        Function<Double, Double> g;

        // Find the min and max of the column the node in the data.
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;

        for (int sample = 0; sample < numSamples; sample++) {
            double value = data.getDouble(sample, nodeToIndex.get(node));
            if (value < min) {
                min = value;
            }
            if (value > max) {
                max = value;
            }
        }

        rpl.setScale(min, max, rescaleMin, rescaleMax);
        g = rpl::evaluate;

        for (int sample = 0; sample < numSamples; sample++) {
            data.setDouble(sample, nodeToIndex.get(node), g.apply(data.getDouble(sample, nodeToIndex.get(node))));
        }
    }

    /**
     * A flag indicating whether distortions should be applied to the data points before the introduction of error noise
     * in the model.
     * <p>
     * When set to true, the pre-error distortion functions are used to modify the values of nodes before any noise is
     * added. This setting is relevant to the simulation process and affects how the synthetic data is generated in the
     * model.
     * <p>
     * By default, this flag is set to false, meaning no distortions are applied before the addition of noise.
     *
     * @param distortPreError true if pre-error distortions should be applied, false otherwise.
     */
    public void setDistortPreError(boolean distortPreError) {
        this.distortPreError = distortPreError;
    }

    /**
     * Indicates whether post-error distortion should be applied to the data in the model. When set to true, additional
     * distortions are applied after the error terms are introduced to simulate post-nonlinear mechanisms. This
     * parameter works in conjunction with other distortion settings to control the nature of synthetic data generation
     * and causal simulation.
     * <p>
     * This is the "post-nonlinear" distortion.
     *
     * @param distortPostError true if post-error distortions should be applied, false otherwise.
     */
    public void setDistortPostError(boolean distortPostError) {
        this.distortPostError = distortPostError;
    }
}
