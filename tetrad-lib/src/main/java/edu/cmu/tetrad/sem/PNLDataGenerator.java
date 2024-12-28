package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.data.BoxDataSet;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DoubleDataBox;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphSaveLoadUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.RandomGraph;
import edu.cmu.tetrad.util.RandomUtil;
import org.apache.commons.math3.distribution.NormalDistribution;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
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
    private final NormalDistribution noiseDistribution;
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
    private final List<Function<Double, Double>> causalMechanisms = new ArrayList<>(Arrays.asList(
            x -> x,
            Math::sin,
            Math::tanh,
            x -> Math.log1p(Math.abs(x))
    ));
    /**
     * A list of nonlinear transformation functions applied after the causal mechanisms in the Post-Nonlinear Structural
     * Equation Model (PNL-SEM) data generation process. Each function in the list corresponds to a different type of
     * transformation that can be applied to model nonlinear effects, such as identity, exponential, square, or
     * hyperbolic tangent.
     * <p>
     * This list is pre-populated with a set of commonly used nonlinear transformations: - Identity transformation: `x
     * -> x` - Exponential transformation: `Math::exp` - Quadratic transformation: `x -> x * x` - Hyperbolic tangent
     * transformation: `Math::tanh`
     * <p>
     * Additional transformations can be added to this list using the appropriate class methods to customize the data
     * generation process.
     */
    private final List<Function<Double, Double>> postNonlinearTransformations = new ArrayList<>(Arrays.asList(
            x -> x,
            Math::exp,
            x -> x * x,
            Math::tanh
    ));
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
     * This structure is utilized in generating simulated data based on a given causal graph, where each edge has a
     * coefficient defining the relationship strength between connected nodes.
     */
    private final Map<Node, Map<Node, Double>> coefficients = new HashMap<>();

    /**
     * Constructor for the PNLDataGenerator class.
     *
     * @param graph      the directed acyclic graph (DAG) representing the causal structure on which the data generation
     *                   process is based.
     * @param numSamples the number of data samples to be generated.
     * @param noiseStd   the standard deviation of the noise added to the generated data. It must be a positive value.
     * @throws IllegalArgumentException if the provided graph contains cycles.
     */
    public PNLDataGenerator(Graph graph, int numSamples, double noiseStd) {
        if (!graph.paths().isAcyclic()) {
            throw new IllegalArgumentException("Graph contains cycles.");
        }

        this.graph = graph;
        this.numSamples = numSamples;
        this.random = RandomUtil.getInstance();
        this.noiseDistribution = new NormalDistribution(0, noiseStd);
        initializeCoefficients();
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
        PNLDataGenerator generator = new PNLDataGenerator(graph, 1000, .5);
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
     * Adds a causal mechanism to the list of mechanisms used in the data generation process.
     *
     * @param mechanism the function representing a causal mechanism. It takes a single input of type Double and returns
     *                  a value of type Double. This mechanism defines the transformation applied to generate data in
     *                  accordance with the causal structure.
     */
    public void addCausalMechanism(Function<Double, Double> mechanism) {
        causalMechanisms.add(mechanism);
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

                    // Apply a random causal mechanism
                    Function<Double, Double> f = getRandomElement(causalMechanisms);
                    double fOutput = f.apply(linearCombination);

                    // Add noise
                    double noisyOutput = fOutput + noiseDistribution.sample();

                    // Apply a random post-nonlinear transformation
                    Function<Double, Double> g = getRandomElement(postNonlinearTransformations);

                    data.setDouble(sample, nodeToIndex.get(node), g.apply(noisyOutput));
                }
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
                parentCoefficients.put(parent, random.nextDouble() * .9 + 0.1);
            }
            coefficients.put(child, parentCoefficients);
        }
    }

}
