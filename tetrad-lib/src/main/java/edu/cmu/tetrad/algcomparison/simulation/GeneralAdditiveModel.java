/// ////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.algcomparison.simulation;

import edu.cmu.tetrad.algcomparison.graph.RandomGraph;
import edu.cmu.tetrad.algcomparison.graph.SingleGraph;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.LayoutUtil;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.sem.ExpressionSampler;
import edu.cmu.tetrad.sem.Sampler;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TMath;

import java.io.Serial;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Wrapper for the neural-net Generalized Additive Model (GAM) simulator.
 *
 * <p>The underlying SEM has the form</p>
 *
 * <pre>
 *     X_j = sum_{k in Pa(j)} f_{jk}(X_k) + e_j
 * </pre>
 *
 * <p>
 * where each {@code f_{jk}} is a separate random univariate MLP and {@code e_j}
 * is additive noise drawn from the configured noise sampler.
 * </p>
 *
 * <p>
 * This wrapper intentionally aligns its user-facing controls with the deep-net
 * additive-noise simulator where possible:
 * </p>
 *
 * <ul>
 *   <li>{@link Params#NOISE_EXPRESSION}</li>
 *   <li>{@link Params#HIDDEN_DIMENSIONS}</li>
 *   <li>{@link Params#INPUT_SCALE}</li>
 *   <li>{@link Params#SAMPLE_SIZE}</li>
 *   <li>{@link Params#STANDARDIZE}</li>
 *   <li>{@link Params#SEED}</li>
 * </ul>
 *
 * <p>
 * In this wrapper, {@link Params#STANDARDIZE} is used only for post-processing
 * of the final dataset, matching the behavior of other algcomparison wrappers.
 * Parent-input standardization inside the simulator is enabled by default for
 * numerical stability.
 * </p>
 */
public class GeneralAdditiveModel implements Simulation {

    @Serial
    private static final long serialVersionUID = 24L;

    /**
     * Represents the random graph used within the General Additive Model (GAM).
     * This graph serves as the foundation for data generation, simulations, and
     * various dependent operations in the model.
     * <p>
     * The associated {@link RandomGraph} instance dictates the structure and
     * characteristics of the graph, and its description and parameters are
     * utilized in configuring the model.
     * <p>
     * This variable is immutable and must be initialized at the time of object
     * construction, ensuring consistency and preventing unintended modifications
     * to the underlying graph.
     */
    private final RandomGraph randomGraph;
    /**
     * A collection of data models used within the General Additive Model (GAM).
     * <p>
     * This field stores a list of {@code DataSet} objects, which represent the
     * datasets generated or used during the execution of the model. The list is
     * initialized as an empty {@code ArrayList} and is populated through data
     * generation, simulation, or external interaction with the class.
     */
    private List<DataSet> dataSets = new ArrayList<>();
    /**
     * A list of Graph objects used within the General Additive Model.
     * <p>
     * This list serves as a central collection of graphs, where each graph
     * represents a distinct structure used for data modeling or simulations.
     * Graphs stored in this list are typically generated or modified through
     * various operations within the model.
     */
    private List<Graph> graphs = new ArrayList<>();

    /**
     * Constructs a GeneralAdditiveModel instance with the specified random graph.
     *
     * @param graph The random graph to be used in the model. Must not be null.
     * @throws NullPointerException if the provided graph is null.
     */
    public GeneralAdditiveModel(RandomGraph graph) {
        if (graph == null) throw new NullPointerException("Graph is null.");
        this.randomGraph = graph;
    }

    /**
     * Applies a series of post-processing transformations to the given dataset based on
     * the specified parameters. These transformations include standardization, variance-based
     * noise addition, column randomization, and column removal, followed by restricting the
     * dataset to measured values.
     *
     * @param parameters Contains various configuration options for post-processing the dataset.
     *                   Relevant keys include:
     *                   - Params.STANDARDIZE: Boolean flag indicating whether to standardize the data.
     *                   - Params.MEASUREMENT_VARIANCE: Double value representing the variance of
     *                   the Gaussian noise to add to the data. Default is 0.0.
     *                   - Params.RANDOMIZE_COLUMNS: Boolean flag specifying whether to shuffle columns.
     *                   - Params.PROB_REMOVE_COLUMN: Double value representing the probability of
     *                   removing random columns. Default is 0.0.
     * @param dataSet    The dataset to be post-processed.
     * @return A new DataSet instance with the applied transformations.
     */
    private static DataSet postProcess(Parameters parameters, DataSet dataSet) {
        if (parameters.getBoolean(Params.STANDARDIZE)) {
            dataSet = DataTransforms.standardizeData(dataSet);
        }

        double variance = parameters.getDouble(Params.MEASUREMENT_VARIANCE, 0.0);

        if (variance > 0.0) {
            for (int r = 0; r < dataSet.getNumRows(); r++) {
                for (int c = 0; c < dataSet.getNumColumns(); c++) {
                    double d = dataSet.getDouble(r, c);
                    double n = RandomUtil.getInstance().nextGaussian(0.0, TMath.sqrt(variance));
                    dataSet.setDouble(r, c, d + n);
                }
            }
        }

        if (parameters.getBoolean(Params.RANDOMIZE_COLUMNS, false)) {
            dataSet = DataTransforms.shuffleColumns(dataSet);
        }

        double pRemove = parameters.getDouble(Params.PROB_REMOVE_COLUMN, 0.0);
        if (pRemove > 0.0) {
            dataSet = DataTransforms.removeRandomColumns(dataSet, pRemove);
        }

        return DataTransforms.restrictToMeasured(dataSet);
    }

    /**
     * Parses a comma-separated hidden-dimension string such as "8,8" or "16".
     */
    private static int[] parseHiddenDimensions(String spec) {
        if (spec == null || spec.trim().isEmpty()) {
            return new int[]{8, 8};
        }

        String[] parts = spec.split(",");
        int[] dims = new int[parts.length];

        for (int i = 0; i < parts.length; i++) {
            String s = parts[i].trim();
            if (s.isEmpty()) {
                throw new IllegalArgumentException("Malformed hidden-dimensions string: \"" + spec + "\"");
            }

            int h = Integer.parseInt(s);
            if (h < 1) {
                throw new IllegalArgumentException("Hidden dimensions must be >= 1: \"" + spec + "\"");
            }
            dims[i] = h;
        }

        return dims;
    }

    /**
     * Creates data models and their corresponding graphs based on the provided parameters.
     * This method initializes a series of data sets and graphs by repeatedly generating
     * random graphs, modifying them, and simulating data. The number of simulations
     * executed is determined by the number of runs specified in the parameters.
     * <p>
     * If a seed is provided in the parameters, it will be used to initialize the random
     * number generator, ensuring reproducibility.
     *
     * @param parameters An object that encapsulates various configuration settings for
     *                   data generation, including:
     *                   - Params.NUM_RUNS: The number of data models to generate.
     *                   - Params.SEED: A long value specifying the random seed for
     *                   reproducibility (optional).
     * @param newModel   A boolean flag indicating whether to generate a new model. If set
     *                   to true, additional operations might be performed based on the
     *                   application's logic.
     */
    @Override
    public void createData(Parameters parameters, boolean newModel) {
        if (parameters.getLong(Params.SEED, -1L) != -1L) {
            RandomUtil.getInstance().setSeed(parameters.getLong(Params.SEED));
        }

        this.dataSets = new ArrayList<>();
        this.graphs = new ArrayList<>();

        for (int run = 0; run < parameters.getInt(Params.NUM_RUNS); run++) {
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

            this.graphs.add(graph);
            this.dataSets.add(dataSet);
        }
    }

    /**
     * Retrieves the true graph specified by the given index from the list of graphs.
     *
     * @param index The index of the graph to retrieve. Must be within the bounds of the graph list.
     * @return The graph at the specified index.
     * @throws IndexOutOfBoundsException if the index is out of range.
     */
    @Override
    public Graph getTrueGraph(int index) {
        return this.graphs.get(index);
    }

    /**
     * Retrieves the number of data models currently stored in the class.
     *
     * @return The total number of data models present, determined by the size
     * of the internal dataSets list.
     */
    @Override
    public int getNumDataModels() {
        return this.dataSets.size();
    }

    /**
     * Retrieves the data model located at the specified index within the collection of data models.
     *
     * @param index The index of the data model to retrieve. Must be within the bounds of the dataSets list.
     * @return The data model at the specified index.
     * @throws IndexOutOfBoundsException if the index is out of range.
     */
    @Override
    public DataModel getDataModel(int index) {
        return this.dataSets.get(index);
    }

    /**
     * Returns the data type associated with this model. The data type specifies
     * whether the data set consists of continuous, discrete, mixed variables, or
     * other possible types defined in the {@link DataType} enumeration.
     *
     * @return The {@link DataType} value, indicating that the model's data type is continuous.
     */
    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    /**
     * Provides a description of the Generalized Additive Model (GAM) with its associated
     * random graph. The description includes details about the underlying random graph
     * utilized in the model.
     *
     * @return A string containing a description of the Generalized Additive Model,
     * including the description of the associated random graph.
     */
    public String getDescription() {
        return "Generalized Additive Model (Deep Net) using " + this.randomGraph.getDescription();
    }

    /**
     * Retrieves the short name associated with the General Additive Model (GAM).
     *
     * @return A string representing the short name of the model, specifically "GAM".
     */
    public String getShortName() {
        return "GAM";
    }

    /**
     * Retrieves a list of configuration parameters used by the model, including both
     * parameters from the associated random graph (if applicable) and those specific
     * to this implementation.
     * <p>
     * The parameters include various configuration options such as noise expression,
     * dimensional settings, and simulation controls. If the random graph associated
     * with the model is not an instance of {@code SingleGraph}, its parameters are
     * also included.
     *
     * @return A list of parameter names as {@code String}, containing both graph-specific
     * parameters and additional parameters specific to the General Additive Model.
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();

        if (!(this.randomGraph instanceof SingleGraph)) {
            parameters.addAll(this.randomGraph.getParameters());
        }

        parameters.add(Params.NOISE_EXPRESSION);
        parameters.add(Params.HIDDEN_DIMENSIONS);
        parameters.add(Params.INPUT_SCALE);

        parameters.add(Params.NUM_RUNS);
        parameters.add(Params.PROB_REMOVE_COLUMN);
        parameters.add(Params.DIFFERENT_GRAPHS);
        parameters.add(Params.RANDOMIZE_COLUMNS);
        parameters.add(Params.SAMPLE_SIZE);
        parameters.add(Params.SAVE_LATENT_VARS);
        parameters.add(Params.STANDARDIZE);
        parameters.add(Params.SEED);
        parameters.add(Params.MEASUREMENT_VARIANCE);

        return parameters;
    }

    /**
     * Retrieves the class type of the random graph associated with the model.
     *
     * @return A {@code Class} object representing the type of the random graph
     * associated with the model, which extends {@code RandomGraph}.
     */
    @Override
    public Class<? extends RandomGraph> getRandomGraphClass() {
        return randomGraph.getClass();
    }

    /**
     * Retrieves the class type of the simulation associated with the General Additive Model.
     *
     * @return A {@code Class} object representing the type of the simulation, which extends {@code Simulation}.
     */
    @Override
    public Class<? extends Simulation> getSimulationClass() {
        return getClass();
    }

    private DataSet simulate(Graph graph, Parameters parameters) {
        return runModel(graph, parameters);
    }

    /**
     * Runs the neural-net GAM simulator.
     */
    private DataSet runModel(Graph graph, Parameters parameters) {
        int[] hiddenDimensions = parseHiddenDimensions(parameters.getString(Params.HIDDEN_DIMENSIONS));

        double inputScale = parameters.getDouble(Params.INPUT_SCALE, 1.0);
        int sampleSize = parameters.getInt(Params.SAMPLE_SIZE);

        // Keep the same default activation as the additive-noise simulator.
        Function<Double, Double> activation = TMath::tanh;

        try {
            Sampler sampler = new ExpressionSampler(parameters.getString(Params.NOISE_EXPRESSION));

            edu.cmu.tetrad.sem.GeneralAdditiveModel generator =
                    new edu.cmu.tetrad.sem.GeneralAdditiveModel(graph, sampleSize, sampler)
                            .setHiddenDimensions(hiddenDimensions)
                            .setInputScale(inputScale)
                            .setInputStandardize(true)
                            .setActivationFunction(activation);

            return generator.generate();
        } catch (ParseException e) {
            throw new RuntimeException("Could not parse noise expression: "
                    + parameters.getString(Params.NOISE_EXPRESSION), e);
        }
    }
}