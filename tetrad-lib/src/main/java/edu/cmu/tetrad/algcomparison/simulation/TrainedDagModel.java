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
import edu.cmu.tetrad.algcomparison.utils.TakesData;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.sem.TrainedDagSimulatorGNM;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.io.Serial;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Trained DAG model.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class TrainedDagModel implements Simulation, TakesData {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The graph.
     */
    private final RandomGraph randomGraph;

    /**
     * The shocks.
     */
    private final DataSet data;

    /**
     * The data sets.
     */
    private List<DataSet> dataSets = new ArrayList<>();

    /**
     * The graphs.
     */
    private List<Graph> graphs = new ArrayList<>();

    /**
     * Constructs a TrainedDagModel using the given randomGraph and data set. The data set must contain all
     * nodes in the randomGraph.
     *
     * @param randomGraph  The randomGraph.
     * @param dataSet  The data set.
     */
    public TrainedDagModel(RandomGraph randomGraph, DataSet dataSet) {
        if (randomGraph == null) {
            throw new NullPointerException();
        }

        if (dataSet == null) {
            throw new NullPointerException();
        }

        this.randomGraph = randomGraph;
        this.data = dataSet;

        System.out.println("data variables = " + data.getVariables());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void createData(Parameters parameters, boolean newModel) {
        boolean saveLatentVars = parameters.getBoolean(Params.SAVE_LATENT_VARS);

        this.dataSets = new ArrayList<>();
        this.graphs = new ArrayList<>();

        int numRuns = parameters.getInt(Params.NUM_RUNS);

        for (int i = 0; i < numRuns; i++) {
            Graph graph = randomGraph.createGraph(parameters);

            graph = GraphUtils.replaceNodes(graph, data.getVariables());

            System.out.println("graph variables = " + graph.getNodes());
            System.out.println("data variables = " + data.getVariables());

            if (!(new HashSet<>(data.getVariables()).containsAll(new HashSet<>(graph.getNodes())))) {
                throw new IllegalArgumentException("Data set does not contain all nodes in graph.");
            }

            this.graphs.add(graph);

            int[] tiers = new int[graph.getNodes().size()];
            for (int j = 0; j < tiers.length; j++) {
                tiers[j] = j;
            }

            TrainedDagSimulatorGNM simulator = new TrainedDagSimulatorGNM(
                    data, graph, new TrainedDagSimulatorGNM.Params());
            simulator.fit();

            int anInt = parameters.getInt(Params.SAMPLE_SIZE);
            edu.cmu.tetrad.sem.TrainedDagSimulatorGNM.SimResult result = simulator.simulate(anInt);
            DataSet dataSet = result.toDataSet();

            if (parameters.getBoolean(Params.RANDOMIZE_COLUMNS)) {
                dataSet = DataTransforms.shuffleColumns(dataSet);
            }

            if (parameters.getDouble(Params.PROB_REMOVE_COLUMN) > 0) {
                double aDouble = parameters.getDouble(Params.PROB_REMOVE_COLUMN);
                dataSet = DataTransforms.removeRandomColumns(dataSet, aDouble);
            }

            dataSet = DataTransforms.restrictToMeasured(dataSet);

            this.dataSets.add(dataSet);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataModel getDataModel(int index) {
        return this.dataSets.get(index);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Graph getTrueGraph(int index) {
        return this.graphs.get(index);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Linear Fisher model simulation";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getShortName() {
        return "Linear Fisher Model";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>(randomGraph.getParameters());
        parameters.add(Params.NUM_RUNS);
        parameters.add(Params.SAMPLE_SIZE);
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
     * {@inheritDoc}
     */
    @Override
    public int getNumDataModels() {
        return this.dataSets.size();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataType getDataType() {
        return DataType.Mixed;
    }
}

