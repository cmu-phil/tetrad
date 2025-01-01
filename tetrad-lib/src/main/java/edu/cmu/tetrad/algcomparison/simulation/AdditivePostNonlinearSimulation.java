package edu.cmu.tetrad.algcomparison.simulation;

import edu.cmu.tetrad.algcomparison.graph.RandomGraph;
import edu.cmu.tetrad.algcomparison.graph.SingleGraph;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.LayoutUtil;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.RandomUtil;
import org.apache.commons.math3.distribution.BetaDistribution;
import org.apache.commons.math3.util.FastMath;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * This class represents a random additive post-nonlinear SEM simulation.
 *
 * @author josephramsey
 */
public class AdditivePostNonlinearSimulation implements Simulation {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The random graph generator.
     */
    private final RandomGraph randomGraph;

    /**
     * The data sets.
     */
    private List<DataSet> dataSets = new ArrayList<>();

    /**
     * The graphs.
     */
    private List<Graph> graphs = new ArrayList<>();

    /**
     * Constructs a SemSimulation object with the given RandomGraph object.
     *
     * @param graph the RandomGraph object used for simulation.
     * @throws NullPointerException if graph is null.
     */
    public AdditivePostNonlinearSimulation(RandomGraph graph) {
        if (graph == null) throw new NullPointerException("Graph is null.");
        this.randomGraph = graph;
    }

    /**
     * Performs post-processing on a given dataset based on the provided parameters.
     *
     * @param parameters The parameters used for post-processing.
     * @param dataSet    The dataset to be post-processed.
     * @return The post-processed dataset.
     */
    private static DataSet postProcess(Parameters parameters, DataSet dataSet) {
        if (parameters.getBoolean(Params.STANDARDIZE)) {
            dataSet = DataTransforms.standardizeData(dataSet);
        }

        double variance = parameters.getDouble(Params.MEASUREMENT_VARIANCE);

        if (variance > 0) {
            for (int k = 0; k < dataSet.getNumRows(); k++) {
                for (int j = 0; j < dataSet.getNumColumns(); j++) {
                    double d = dataSet.getDouble(k, j);
                    double norm = RandomUtil.getInstance().nextGaussian(0, FastMath.sqrt(variance));
                    dataSet.setDouble(k, j, d + norm);
                }
            }
        }

        if (parameters.getBoolean(Params.RANDOMIZE_COLUMNS)) {
            dataSet = DataTransforms.shuffleColumns(dataSet);
        }

        if (parameters.getDouble(Params.PROB_REMOVE_COLUMN) > 0) {
            double aDouble = parameters.getDouble(Params.PROB_REMOVE_COLUMN);
            dataSet = DataTransforms.removeRandomColumns(dataSet, aDouble);
        }

        dataSet = DataTransforms.restrictToMeasured(dataSet);

        return dataSet;
    }

    @Override
    public void createData(Parameters parameters, boolean newModel) {
        if (parameters.getLong(Params.SEED) != -1L) {
            RandomUtil.getInstance().setSeed(parameters.getLong(Params.SEED));
        }

        this.dataSets = new ArrayList<>();
        this.graphs = new ArrayList<>();

        for (int i = 0; i < parameters.getInt(Params.NUM_RUNS); i++) {
            Graph graph = this.randomGraph.createGraph(parameters);

            List<Node> continuousVars = new ArrayList<>();

            for (Node node : graph.getNodes()) {
                ContinuousVariable var = new ContinuousVariable(node.getName());
                var.setNodeType(node.getNodeType());
                continuousVars.add(var);
            }

            graph = GraphUtils.replaceNodes(graph, continuousVars);
            LayoutUtil.defaultLayout(graph);

            DataSet dataSet = simulate(graph, parameters);

            dataSet = postProcess(parameters, dataSet);

            graphs.add(graph);
            dataSets.add(dataSet);
        }
    }

    /**
     * Returns the true graph at the specified index.
     *
     * @param index The index of the desired true graph.
     * @return The true graph at the specified index.
     */
    @Override
    public Graph getTrueGraph(int index) {
        return this.graphs.get(index);
    }

    /**
     * Returns the number of data models.
     *
     * @return The number of data sets to simulate.
     */
    @Override
    public int getNumDataModels() {
        return this.dataSets.size();
    }

    /**
     * Returns the data model at the specified index.
     *
     * @param index The index of the desired simulated data set.
     * @return The data model at the specified index.
     */
    @Override
    public DataModel getDataModel(int index) {
        return this.dataSets.get(index);
    }

    /**
     * Returns the data type of the data set.
     *
     * @return The type of the data set--continuous if all continuous variables, discrete if all discrete variables;
     * otherwise, mixed.
     */
    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    /**
     * Returns the description of the simulation.
     *
     * @return a short, one-line description of the simulation.
     */
    public String getDescription() {
        return "Additive post-nonlinear SEM simulation using " + this.randomGraph.getDescription();
    }

    /**
     * Returns the short name of the simulation.
     *
     * @return The short name of the simulation.
     */
    public String getShortName() {
        return "Additive post-nonlinear SEM Simulation";
    }

    /**
     * Retrieves the parameters required for the simulation.
     *
     * @return A list of String names representing the parameters.
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();

        if (!(this.randomGraph instanceof SingleGraph)) {
            parameters.addAll(this.randomGraph.getParameters());
        }

        parameters.add(Params.PNL_TAYLOR_SERIES_DEGREE);
        parameters.add(Params.PNL_RESCALE_BOUND);
        parameters.add(Params.PNL_BETA_ALPHA);
        parameters.add(Params.PNL_BETA_BETA);
        parameters.add(Params.PNL_DERIVATIVE_MIN);
        parameters.add(Params.PNL_DERIVATIVE_MAX);
        parameters.add(Params.PNL_FIRST_DERIVATIVE_MIN);
        parameters.add(Params.PNL_FIRST_DERIVATIVE_MAX);
        parameters.add(Params.NUM_RUNS);
        parameters.add(Params.PROB_REMOVE_COLUMN);
        parameters.add(Params.DIFFERENT_GRAPHS);
        parameters.add(Params.RANDOMIZE_COLUMNS);
        parameters.add(Params.SAMPLE_SIZE);
        parameters.add(Params.SAVE_LATENT_VARS);
        parameters.add(Params.STANDARDIZE);
        parameters.add(Params.SEED);

        return parameters;
    }

    @Override
    public Class<? extends RandomGraph> getRandomGraphClass() {
        return randomGraph.getClass();
    }

    @Override
    public Class<? extends Simulation> getSimulationClass() {
        return getClass();
    }

    /**
     * Simulates a data set based on the given SemIm and Parameters.
     *
     * @param graph      the graph to use in the simulation
     * @param parameters the parameters to use in the simulation
     * @return a DataSet object representing the simulated data
     */
    private DataSet simulate(Graph graph, Parameters parameters) {

        // Run the simulation
        return runAdditivePostnonlinearSimulation(graph, parameters);
    }

    /**
     * Executes a post-nonlinear simulation to generate a synthetic dataset based on the provided graph, number of
     * samples, and noise standard deviation using the default configuration of the PNLDataGenerator.
     *
     * @param graph      the graph representing the causal relationships used in the simulation.
     * @return the generated synthetic dataset as a DataSet object.
     */
    private DataSet runAdditivePostnonlinearSimulation(Graph graph, Parameters parameters) {
        // Use the default PNLDataGenerator configuration
        edu.cmu.tetrad.sem.AdditivePostNonlinearSimulation generator = new edu.cmu.tetrad.sem.AdditivePostNonlinearSimulation(graph, parameters.getInt(Params.SAMPLE_SIZE),
                new BetaDistribution(parameters.getDouble(Params.PNL_BETA_ALPHA), parameters.getDouble(Params.PNL_BETA_BETA)),
                parameters.getDouble(Params.PNL_DERIVATIVE_MIN), parameters.getDouble(Params.PNL_DERIVATIVE_MAX),
                parameters.getDouble(Params.PNL_FIRST_DERIVATIVE_MIN), parameters.getDouble(Params.PNL_FIRST_DERIVATIVE_MAX),
                parameters.getInt(Params.PNL_TAYLOR_SERIES_DEGREE));
        generator.setRescaleBound(parameters.getDouble(Params.PNL_RESCALE_BOUND));

        // Generate the synthetic dataset
        return generator.generateData();
    }
}
