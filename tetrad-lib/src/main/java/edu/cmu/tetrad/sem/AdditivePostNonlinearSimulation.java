package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphSaveLoadUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.util.RandomPiecewiseLinear;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TaylorSeries;
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
 * Class representing a simulation for generating synthetic data based on the Additive Post-Nonlinear (APNL) model. This
 * simulation utilizes a causal structure defined by a directed acyclic graph (DAG) and includes configurable
 * mechanisms, noise distributions, and rescaling options.
 * <p>
 * The AdditivePostNonlinearSimulation class is primarily used to model causal relationships between variables and
 * create synthetic data that adheres to these relationships. It applies post-nonlinear transformations combined with
 * random noise, making the generated data more closely resemble real-world processes.
 */
public class AdditivePostNonlinearSimulation {
    /**
     * Represents the graphical structure used to encode the causal relationships between variables in the context of
     * data generation. This variable holds the directed graph that defines the causal dependencies, which is central to
     * the operations of the PNLDataGenerator.
     * <p>
     * The graph is used to guide the generation of synthetic data by defining which variables directly influence others
     * within the structure. It is expected that the graph includes nodes representing variables and directed edges
     * representing causal relationships.
     * <p>
     * This variable is immutable and must be initialized through the constructor of the containing class to ensure
     * consistent usage throughout the data generation process.
     */
    private final Graph graph;
    /**
     * Represents the number of samples to be generated in the synthetic data generation process. This variable is used
     * to control the size of the dataset produced by the {@code generateData} method. It is a fixed parameter
     * initialized during the construction of the {@code PNLDataGenerator} instance.
     */
    private final int numSamples;
    /**
     * Represents the noise distribution used in the additive post-nonlinear simulation framework. This distribution is
     * utilized to introduce randomness into the simulated data, reflecting inherent noise in causal relationships. The
     * noise is applied during data generation, ensuring variability and realism in the synthetic dataset.
     */
    private final RealDistribution noiseDistribution;
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
    private Map<Node, Map<Node, Function<Double, Double>>> parentFunctions = new HashMap<>();
    /**
     * A map where each key is a node in the graph, and each value is a function representing the post-nonlinear
     * transformation applied to that node.
     * <p>
     * This map defines the post-nonlinear mechanisms for variables in the graph, allowing customization of how the
     * nonlinearity is applied to each node's value. The functions are expected to take a double value as input and
     * return a transformed double value as output.
     * <p>
     * It is used during the data generation process to apply specified post-nonlinear transformations to the synthetic
     * data after applying additive noise and parent functions. If not explicitly provided, default transformations may
     * be used.
     */
    private Map<Node, Function<Double, Double>> postNonlinearFunctions = new HashMap<>();
    /**
     * Represents the rescale-bound used to scale the generated data. This value is used to rescale the data to a
     * specified bound after the post-nonlinear transformations have been applied for each new variable simulated. The
     * default value is set to 1.0. If the value is set to 0, no rescaling is performed.
     */
    private double rescaleBound = 1.0;

    /**
     * Constructs an AdditivePostNonlinearSimulation with the specified graph, number of samples, noise distribution,
     * derivative bounds, coefficient bounds, and Taylor series degree. This simulation generates synthetic data based
     * on post-nonlinear causal mechanisms defined in the provided directed acyclic graph. The parent functions are
     * initialized randomly.
     *
     * @param graph              The directed acyclic graph (DAG) that defines the causal relationships among
     *                           variables.
     * @param numSamples         The number of samples to generate for the simulation. Must be a positive integer.
     * @param noiseDistribution  The real-valued noise distribution used for simulating additive noise in the causal
     *                           mechanisms.
     * @param derivMin           The minimum bound for the derivative of the causal functions. Must be less than or
     *                           equal to derivMax.
     * @param derivMax           The maximum bound for the derivative of the causal functions.
     * @param firstDerivMin      The minimum bound for f'(0) in the causal functions.
     * @param firstDerivMax      The maximum bound for f'(0) in the causal functions.
     * @param taylorSeriesDegree The degree of the Taylor series used to approximate the causal functions.
     * @throws IllegalArgumentException if the graph contains cycles, if derivMin is greater than derivMax, if
     *                                  firstDerivMin is greater than firstDerivMax, or if numSamples is less than 1.
     */
    public AdditivePostNonlinearSimulation(Graph graph, int numSamples, RealDistribution noiseDistribution,
                                           double derivMin, double derivMax, double firstDerivMin, double firstDerivMax,
                                           int taylorSeriesDegree) {
        this(graph, numSamples, noiseDistribution, derivMin, derivMax, firstDerivMin, firstDerivMax, taylorSeriesDegree,
                null, null);
    }

    /**
     * Constructs an AdditivePostNonlinearSimulation with the specified graph, number of samples, noise distribution,
     * parent functions, and post-nonlinear functions. This simulation generates synthetic data based on post-nonlinear
     * causal mechanisms defined in the provided directed acyclic graph (DAG).
     *
     * @param graph               The directed acyclic graph (DAG) that defines the causal relationships among variables.
     *                            The graph must be acyclic for the simulation to work.
     * @param numSamples          The number of samples to generate for the simulation. Must be a positive integer.
     * @param noiseDistribution   The real-valued noise distribution used for simulating additive noise in the causal
     *                            mechanisms.
     * @param parentFunctions     A map specifying functions representing the relationships between parent nodes and
     *                            their corresponding child node in the graph. The keys are nodes, and the values are
     *                            maps where keys are parent nodes, and values are functions defining the relationship.
     *                            If null, parent functions are initialized randomly.
     * @param postNonlinearFunctions A map specifying post-nonlinear transformation functions for each node in the
     *                               graph. For each node, the function provides a transformation to be applied after
     *                               simulating the relationships. If null, default functions are applied.
     */
    public AdditivePostNonlinearSimulation(Graph graph, int numSamples, RealDistribution noiseDistribution,
                                           Map<Node, Map<Node, Function<Double, Double>>> parentFunctions,
                                           Map<Node, Function<Double, Double>> postNonlinearFunctions) {
        this(graph, numSamples, noiseDistribution, -1, -1, -1, -1, -1,
                parentFunctions, postNonlinearFunctions);
    }

    /**
     * Constructs an AdditivePostNonlinearSimulation with the specified graph, number of samples, noise distribution,
     * derivative bounds, coefficient bounds, and Taylor series degree. This simulation generates synthetic data based
     * on post-nonlinear causal mechanisms defined in the provided directed acyclic graph.
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
     * @param parentFunctions    A map specifying the parent functions for relationships between nodes. For a node, all
     *                           its parent nodes in the graph must have defined functions. If null, the parent
     *                           functions are initialized randomly.
     * @throws IllegalArgumentException if the graph contains cycles, if derivMin is greater than derivMax, if
     *                                  firstDerivMin is greater than firstDerivMax, if numSamples is less than 1, if
     *                                  taylorSeriesDegree is less than 1, or if parent functions are incomplete for the
     *                                  defined graph structure.
     */
    private AdditivePostNonlinearSimulation(Graph graph, int numSamples, RealDistribution noiseDistribution,
                                            double derivMin, double derivMax, double firstDerivMin, double firstDerivMax,
                                            int taylorSeriesDegree, Map<Node, Map<Node, Function<Double, Double>>> parentFunctions,
                                            Map<Node, Function<Double, Double>> postNonlinearFunctions) {
        if (!graph.paths().isAcyclic()) {
            throw new IllegalArgumentException("Graph contains cycles.");
        }

        if (numSamples < 1) {
            throw new IllegalArgumentException("Number of samples must be positive.");
        }

        this.graph = graph;
        this.numSamples = numSamples;
        this.noiseDistribution = noiseDistribution;

        if (parentFunctions != null) {
            // Check to make sure all nodes in the graph have parent functions for all parents.
            for (Node node : graph.getNodes()) {
                if (!parentFunctions.containsKey(node)) {
                    throw new IllegalArgumentException("Parent functions must be provided for all nodes in the graph.");
                }
                for (Node parent : graph.getParents(node)) {
                    if (!parentFunctions.get(node).containsKey(parent)) {
                        throw new IllegalArgumentException("Parent functions must be provided for all parents of each node.");
                    }
                }
            }

            this.parentFunctions = parentFunctions;
        } else {
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

        if (postNonlinearFunctions != null) {
            for (Node node : graph.getNodes()) {
                if (!postNonlinearFunctions.containsKey(node)) {
                    throw new IllegalArgumentException("Post-nonlinear functions must be provided for all nodes in the graph.");
                }
            }
            this.postNonlinearFunctions = postNonlinearFunctions;
        } else {
            for (Node node : this.graph.getNodes()) {
                RandomPiecewiseLinear randomPiecewiseLinear = RandomPiecewiseLinear.get(-1, 1, 20);
                Function<Double, Double> g = randomPiecewiseLinear::evaluate;
                this.postNonlinearFunctions.put(node, g);
            }
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
        AdditivePostNonlinearSimulation generator = new AdditivePostNonlinearSimulation(graph, 1000,
                new BetaDistribution(2, 5), -1, 1,
                0.1, 1, 5);
        DataSet data = generator.generateData();

        // Save the data to a file.
        try {
            File file = new File("data_pnl.txt");
            FileWriter writer = new FileWriter(file);
            writer.write(data.toString());
            writer.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Save the graph to a file.
        GraphSaveLoadUtils.saveGraph(graph, new File("graph_pnl.txt"), false);
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
                    // Compute a linear combination of the parents
                    double linearCombination = 0;
                    for (Node parent : parents) {
                        linearCombination += nodeFunctionMap.get(parent).apply(data.getDouble(sample, nodeToIndex.get(parent)));
                    }

                    // Add noise
                    double noisyOutput = linearCombination + noiseDistribution.sample();
                    data.setDouble(sample, nodeToIndex.get(node), noisyOutput);
                }
            }

            if (rescaleBound > 0) {
                DataTransforms.scale(data, rescaleBound, node);
            }

            var g = postNonlinearFunctions.get(node);

            for (int sample = 0; sample < numSamples; sample++) {
                data.setDouble(sample, nodeToIndex.get(node), g.apply(data.getDouble(sample, nodeToIndex.get(node))));
            }
        }

        return data;
    }

    /**
     * Represents the rescale-bound used to scale the generated data. This value is used to rescale the data to a
     * specified bound after the post-nonlinear transformations have been applied for each new variable simulated. The
     * default value is set to 1.0. If the value is set to 0, no rescaling is performed.
     */
    public void setRescaleBound(double rescaleBound) {
        if (rescaleBound < 0) {
            throw new IllegalArgumentException("Rescale bound must be non-negative.");
        }

        this.rescaleBound = rescaleBound;
    }
}
