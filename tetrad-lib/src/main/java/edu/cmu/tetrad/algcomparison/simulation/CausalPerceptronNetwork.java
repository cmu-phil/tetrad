///////////////////////////////////////////////////////////////////////////////
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
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.algcomparison.simulation;

import edu.cmu.tetrad.algcomparison.graph.RandomGraph;
import edu.cmu.tetrad.algcomparison.graph.SingleGraph;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.LayoutUtil;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.MathUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.RandomUtil;
import org.apache.commons.math3.distribution.BetaDistribution;
import org.apache.commons.math3.util.FastMath;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * This class represents a Causal Perceptron Network.
 *
 * @author josephramsey
 */
public class CausalPerceptronNetwork implements Simulation {
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
    public CausalPerceptronNetwork(RandomGraph graph) {
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

    /**
     * Creates simulated data and associated graphs based on the given parameters. This method generates a specified
     * number of graphs and datasets using a random graph creation process, applies a layout to the graphs, simulates
     * data according to the graph structure, and performs post-processing on the data.
     *
     * @param parameters The parameters used to control the simulation process, including settings for seed, number of
     *                   runs, and other configurations.
     * @param newModel   A flag indicating whether a new model should be created for the simulation.
     */
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
        return "Causal Perceptron Network (CPN) using " + this.randomGraph.getDescription();
    }

    /**
     * Returns the short name of the simulation.
     *
     * @return The short name of the simulation.
     */
    public String getShortName() {
        return "CPN";
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

        parameters.add(Params.AM_RESCALE_MIN);
        parameters.add(Params.AM_RESCALE_MAX);
        parameters.add(Params.AM_BETA_ALPHA);
        parameters.add(Params.AM_BETA_BETA);
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

        return parameters;
    }

    /**
     * Returns the random graph class used in the simulation.
     *
     * @return The class of the random graph used in the simulation.
     */
    @Override
    public Class<? extends RandomGraph> getRandomGraphClass() {
        return randomGraph.getClass();
    }

    /**
     * Returns the class of the current simulation.
     *
     * @return The class of the simulation extending the Simulation interface.
     */
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
        return runModel(graph, parameters);
    }

    /**
     * Performs the simulation.
     *
     * @param graph the graph representing the causal relationships used in the simulation.
     * @return the generated synthetic dataset as a DataSet object.
     */
    private DataSet runModel(Graph graph, Parameters parameters) {
        String hiddenDimensionsString = parameters.getString(Params.HIDDEN_DIMENSIONS);
        String[] hiddenDimensionsSplit = hiddenDimensionsString.split(",");
        int[] hiddenDimensions = new int[hiddenDimensionsSplit.length];
        for (int i = 0; i < hiddenDimensionsSplit.length; i++) {
            hiddenDimensions[i] = Integer.parseInt(hiddenDimensionsSplit[i].trim());
        }

        Function<Double, Double> activation = Math::tanh;// x -> Math.max(0.1 * x, x);

//        edu.cmu.tetrad.sem.CausalPerceptronNetwork generator = new edu.cmu.tetrad.sem.CausalPerceptronNetwork(
//                graph, parameters.getInt(Params.SAMPLE_SIZE),
//                new BetaDistribution(parameters.getDouble(Params.AM_BETA_ALPHA), parameters.getDouble(Params.AM_BETA_BETA)),
//                parameters.getDouble(Params.AM_RESCALE_MIN), parameters.getDouble(Params.AM_RESCALE_MAX),
//                hiddenDimensions, parameters.getDouble(Params.INPUT_SCALE), activation);

        // Convert hidden dimsnions to List<Integer>
        List<Integer> hiddenDimensionsList = MathUtils.getInts(hiddenDimensions);

        edu.cmu.tetrad.sem.CausalPerceptronNetwork generator = new edu.cmu.tetrad.sem.CausalPerceptronNetwork(
                graph, parameters.getInt(Params.SAMPLE_SIZE),
                new BetaDistribution(parameters.getDouble(Params.AM_BETA_ALPHA), parameters.getDouble(Params.AM_BETA_BETA)),
                parameters.getDouble(Params.AM_RESCALE_MIN), parameters.getDouble(Params.AM_RESCALE_MAX),
                hiddenDimensions, parameters.getDouble(Params.INPUT_SCALE), activation);

        return generator.generateData();
    }
}

