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
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.algcomparison.simulation;

import edu.cmu.tetrad.algcomparison.graph.RandomGraph;
import edu.cmu.tetrad.algcomparison.graph.SingleGraph;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataTransforms;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.hybridcg.HybridCgModel;
import edu.cmu.tetrad.hybridcg.HybridCgVars;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.RandomUtil;
import org.apache.commons.math3.util.FastMath;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * This class represents a Simulation using Structural Equation Modeling (SEM).
 */
public class HybridCgSimulation implements Simulation {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The random graph.
     */
    private final RandomGraph randomGraph;

    /**
     * The SEM PM.
     */
    private HybridCgModel.HybridCgPm pm;

    /**
     * Represents a SemIm object used for simulation.
     */
    private HybridCgModel.HybridCgIm im;

    /**
     * The data sets.
     */
    private List<DataSet> dataSets = new ArrayList<>();

    /**
     * The graphs.
     */
    private List<Graph> graphs = new ArrayList<>();

    /**
     * The SEM IMs.
     */
    private List<HybridCgModel.HybridCgIm> ims = new ArrayList<>();

    /**
     * Constructs a SemSimulation object with the given RandomGraph object.
     *
     * @param graph the RandomGraph object used for simulation.
     * @throws NullPointerException if graph is null.
     */
    public HybridCgSimulation(RandomGraph graph) {
        if (graph == null) throw new NullPointerException("Graph is null.");
        this.randomGraph = graph;
    }

    /**
     * Initializes a SemSimulation with the given SemPm object.
     *
     * @param pm the SemPm object used for simulation.
     * @throws NullPointerException if pm is null.
     */
    public HybridCgSimulation(HybridCgModel.HybridCgPm pm) {
        if (pm == null) throw new NullPointerException("PM is null.");
        Graph graph = pm.getGraph();
        this.randomGraph = new SingleGraph(graph);
        this.pm = pm;
        this.im = null;
    }

    /**
     * Constructs a GeneralSemSimulation object with the given SemIm object.
     *
     * @param im the SemIm object used for simulation
     */
    public HybridCgSimulation(HybridCgModel.HybridCgIm im) {
        if (im == null) throw new NullPointerException("IM is null.");
        Graph graph = im.getPm().getGraph();
        this.randomGraph = new SingleGraph(graph);
        this.pm = im.getPm();
        this.im = im;
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
     * Creates data sets for simulation based on the given parameters and model reuse preference.
     *
     * @param parameters The parameters to use in the simulation.
     * @param newModel   If true, a new model is created. If false, the model is reused.
     */
    @Override
    public void createData(Parameters parameters, boolean newModel) {
        if (parameters.getLong(Params.SEED) != -1L) {
            RandomUtil.getInstance().setSeed(parameters.getLong(Params.SEED));
        }

        Graph graph = this.randomGraph.createGraph(parameters);

        this.dataSets = new ArrayList<>();
        this.graphs = new ArrayList<>();
        this.ims = new ArrayList<>();

        for (int i = 0; i < parameters.getInt(Params.NUM_RUNS); i++) {
            if (parameters.getBoolean(Params.DIFFERENT_GRAPHS) && i > 0) {
                graph = this.randomGraph.createGraph(parameters);
            }

//            SemPm pm = this.pm;
            HybridCgModel.HybridCgIm im = this.im;


            HybridCgModel.HybridCgIm.Sample sample = im.sample(parameters.getInt(Params.SAMPLE_SIZE), new Random());

            List<Node> nodes = HybridCgVars.materializeDataVariables(im.getPm());

            DataSet dataSet = im.toDataSet(sample);

            System.out.println(dataSet);

            dataSet = postProcess(parameters, dataSet);
            dataSet.setName("Run" + (i + 1));

            this.graphs.add(graph);
            this.ims.add(im);
            this.dataSets.add(DataTransforms.restrictToMeasured(dataSet));
        }
    }

    /**
     * Retrieves the list of SemIm objects used for simulation.
     *
     * @return The list of SemIm objects.
     */
    public List<HybridCgModel.HybridCgIm> getIms() {
        return this.ims;
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
        return "Linear SEM simulation using " + this.randomGraph.getDescription();
    }

    /**
     * Returns the short name of the simulation.
     *
     * @return The short name of the simulation.
     */
    public String getShortName() {
        return "Linear SEM Simulation";
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

        if (this.im == null) {
            parameters.addAll(SemIm.getParameterNames());
        }

        parameters.add(Params.MEASUREMENT_VARIANCE);
        parameters.add(Params.NUM_RUNS);
        parameters.add(Params.PROB_REMOVE_COLUMN);
        parameters.add(Params.DIFFERENT_GRAPHS);
        parameters.add(Params.RANDOMIZE_COLUMNS);
        parameters.add(Params.SAMPLE_SIZE);
        parameters.add(Params.SAVE_LATENT_VARS);
        parameters.add(Params.STANDARDIZE);
        parameters.add(Params.SIMULATION_ERROR_TYPE);
        parameters.add(Params.SIMULATION_PARAM1);
        parameters.add(Params.SIMULATION_PARAM2);
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
     * @param im         the SemIm object used for simulation
     * @param parameters the parameters to use in the simulation
     * @return a DataSet object representing the simulated data
     */
    private DataSet simulate(SemIm im, Parameters parameters) {
        return im.simulateData(parameters.getInt(Params.SAMPLE_SIZE), true);
    }
}

