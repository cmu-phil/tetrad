package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphSaveLoadUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TaylorSeries;
import org.apache.commons.math3.distribution.BetaDistribution;
import org.apache.commons.math3.distribution.RealDistribution;

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
 * A class for generating synthetic data using the Post-Nonlinear (PNL) data model. The data generation process is
 * conducted based on a given Directed Acyclic Graph (DAG) structure, defined causal mechanisms, and optional
 * post-nonlinear transformations.
 * <p>
 * This class supports the following functionalities: - Defining a DAG as the structure for data generation - Adding
 * custom causal mechanisms - Adding custom post-nonlinear transformations - Sampling data based on the PNL model that
 * applies causal mechanisms, noise, and post-nonlinear transformations
 *
 * @author josephramsey
 */
public class PNLDataGenerator {
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
     * A random number generator used for stochastic operations within the PNLDataGenerator class. This instance ensures
     * consistent and non-deterministic behavior for random sampling, coefficient initialization, or any other
     * operations requiring randomness.
     */
    private final RandomUtil random;
    /**
     * Represents the noise distribution used to generate random noise values in the data generation process. This
     * variable is an instance of {@code NormalDistribution}, which models a Gaussian distribution characterized by a
     * mean and standard deviation.
     * <p>
     * The noise is added to the data as part of simulating real-world imperfections and variations in observed values
     * during the data generation process.
     */
    private final RealDistribution noiseDistribution;
    /**
     * Represents a list of causal mechanisms modeled as mathematical functions. These functions are used to define the
     * causal relationships in a system, where each function maps a given input value to an output value.
     * <p>
     * This list contains default causal mechanisms: 1. Identity function: x -> x 2. Sine function: Math::sin 3.
     * Hyperbolic tangent function: Math::tanh 4. Log-transformed absolute value function: x -> Math.log1p(Math.abs(x))
     * <p>
     * The causal mechanisms are applied during data generation to simulate causal dependencies between variables in the
     * system.
     */
    private final List<Function<Double, Double>> postNonlinearTransformations = new ArrayList<>();
    /**
     * A mapping of coefficients used to represent causal relationships and dependencies between nodes in a graph
     * structure.
     * <p>
     * The outer map uses a {@link Node} as the key, which represents the source node in a causal relationship. The
     * corresponding value is another map.
     * <p>
     * The inner map represents the target nodes and the associated weights (coefficients) of the causal effects. The
     * keys in the inner map are {@link Node} objects that represent the target nodes in the relationship, and the
     * values are {@link Double} representing the strength or magnitude of the causal effect between the source and
     * target nodes.
     * <p>
     * This structure is used in generating simulated data based on a given causal graph, where each edge has a
     * coefficient defining the relationship strength between connected nodes.
     */
    private final Map<Node, Map<Node, Double>> coefficients = new HashMap<>();
    /**
     * Represents the number of post-nonlinear transformations to be applied to the data generation process. This
     * variable determines the number of possible transformations to be applied to the data after the main causal
     * mechanisms have been processed. The default value is set to 3. In each case, one function is selected and applied
     * to the data. The functions are random Taylor series of the specified degree, with f(0) = 0.
     */
    private int numPostNonlinearFunctions = 3;
    /**
     * Represents the degree of the Taylor series used in the post-nonlinear transformations. This is the maximum
     * polynomial degree of the Taylor series used in the post-processing stage of the data generation process. The
     * default value is set to 5.
     */
    private int taylorSeriesDegree = 5;
    /**
     * Represents the rescale-bound used to scale the generated data. This value is used to rescale the data to a
     * specified bound after the post-nonlinear transformations have been applied for each new variable simulated. The
     * default value is set to 1.0. If the value is set to 0, no rescaling is performed.
     */
    private double rescaleBound = 1.0;

    /**
     * Constructor for the PNLDataGenerator class.
     *
     * @param graph      the directed acyclic graph (DAG) representing the causal structure on which the data generation
     *                   process is based.
     * @param numSamples the number of data samples to be generated.
     * @throws IllegalArgumentException if the provided graph contains cycles.
     */
    public PNLDataGenerator(Graph graph, int numSamples, RealDistribution noiseDistribution) {
        if (!graph.paths().isAcyclic()) {
            throw new IllegalArgumentException("Graph contains cycles.");
        }

        this.graph = graph;
        this.numSamples = numSamples;
        this.random = RandomUtil.getInstance();
        this.noiseDistribution = noiseDistribution;
        initializeCoefficients();

        for (int i = 0; i < numPostNonlinearFunctions; i++) {
            double[] derivatives = new double[taylorSeriesDegree + 1];
            for (int i1 = 1; i1 <= taylorSeriesDegree; i1++) {
                derivatives[i1] = RandomUtil.getInstance().nextUniform(-1, 1);
            }

            // We want the function to be 0 at 0, since the causal theory we are using assumes the data is centered.
            derivatives[0] = 0;

            TaylorSeries taylor = TaylorSeries.get(derivatives, 0);
            addPostNonlinearTransformation(taylor::evaluate);
        }
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
        PNLDataGenerator generator = new PNLDataGenerator(graph, 1000, new BetaDistribution(2, 5));
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
     * Adds a post-nonlinear transformation to the list of transformations used in the data generation process. These
     * transformations are applied after the main causal mechanisms, allowing for additional modifications to the
     * generated data.
     *
     * @param transformation the function representing a post-nonlinear transformation. It takes a single input of type
     *                       Double and returns a value of type Double. This transformation modifies the generated data
     *                       in the post-processing stage.
     */
    public void addPostNonlinearTransformation(Function<Double, Double> transformation) {
        postNonlinearTransformations.add(transformation);
    }

    /**
     * Generates a dataset based on the causal relationships defined in a directed acyclic graph (DAG). The dataset is
     * populated using specified causal mechanisms, post-nonlinear transformations, and noise distribution. The method
     * constructs the data by iterating through nodes in a valid causal ordering and computes values using parent nodes'
     * data, causal mechanisms, transformations, and noise.
     *
     * @return a DataSet object containing the generated synthetic data, structured according to the graph's topology.
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

            for (int sample = 0; sample < numSamples; sample++) {
                if (parents.isEmpty()) {
                    data.setDouble(sample, nodeToIndex.get(node), noiseDistribution.sample());
                } else {
                    // Compute a linear combination of the parents
                    double linearCombination = 0;
                    for (Node parent : parents) {
                        linearCombination += coefficients.get(node).get(parent) * data.getDouble(sample, nodeToIndex.get(parent));
                    }

                    // Add noise
                    double noisyOutput = linearCombination + noiseDistribution.sample();

                    // Apply a random post-nonlinear transformation
                    var g = getRandomElement(postNonlinearTransformations);

                    data.setDouble(sample, nodeToIndex.get(node), g.apply(noisyOutput));
                }
            }

            if (rescaleBound > 0) {
                DataTransforms.scale(data, rescaleBound, node);
            }
        }

        return data;
    }

    /**
     * Selects and returns a random element from the provided list.
     *
     * @param <T>  the type of elements contained in the list.
     * @param list the list from which a random element will be selected. The list must not be null or empty.
     * @return a randomly selected element from the list.
     * @throws IllegalArgumentException if the provided list is null or empty.
     */
    private <T> T getRandomElement(List<T> list) {
        return list.get(random.nextInt(list.size()));
    }

    /**
     * Initializes the coefficients for the edges in the directed acyclic graph (DAG).
     * <p>
     * For each child node in the graph, this method assigns a randomly generated coefficient to each of its parent
     * nodes. The coefficients are stored in a nested map, where the outer map associates each child node with its
     * respective parent-coefficients map, and the inner map associates each parent node with its corresponding
     * coefficient value.
     * <p>
     * The coefficients are generated as random double values in the range [0.1, 1.0).
     */
    private void initializeCoefficients() {
        for (Node child : graph.getNodes()) {
            Map<Node, Double> parentCoefficients = new HashMap<>();
            for (Node parent : graph.getParents(child)) {
                parentCoefficients.put(parent, random.nextDouble() * .6 + 0.1);
            }
            coefficients.put(child, parentCoefficients);
        }
    }

    /**
     * Sets the degree of the Taylor series to be used in the data generation process. The Taylor series degree
     * determines the complexity of the approximations applied in generating synthetic data. The default value is 5.
     *
     * @param taylorSeriesDegree the degree of the Taylor series. It should be a positive integer that specifies the
     *                           order of terms to be included in the series during approximations.
     */
    public void setTaylorSeriesDegree(int taylorSeriesDegree) {
        if (taylorSeriesDegree < 1) {
            throw new IllegalArgumentException("Taylor series degree must be positive.");
        }

        this.taylorSeriesDegree = taylorSeriesDegree;
    }

    /**
     * Represents the number of post-nonlinear transformations to be applied to the data generation process. This
     * variable determines the number of possible transformations to be applied to the data after the main causal
     * mechanisms have been processed. In each case, one function is selected and applied to the data. The functions are
     * random Taylor series of the specified degree, with f(0) = 0.
     *
     * @param numPostNonlinearFunctions The number of post-nonlinear transformations to be applied. The default value is
     *                                  set to 3.
     */
    public void setNumPostNonlinearFunctions(int numPostNonlinearFunctions) {
        if (numPostNonlinearFunctions < 1) {
            throw new IllegalArgumentException("Number of post-nonlinear functions must be positive.");
        }

        this.numPostNonlinearFunctions = numPostNonlinearFunctions;
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
