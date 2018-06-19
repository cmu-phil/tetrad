///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.algcomparison.graph.RandomForward;
import edu.cmu.tetrad.algcomparison.graph.SingleGraph;
import edu.cmu.tetrad.algcomparison.simulation.*;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.session.SessionModel;
import edu.cmu.tetrad.session.SimulationParamsSource;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradSerializableUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Wraps a Simulation object for the Tetrad interface. A Simulation object
 * requires a RandomGraph and a choice of Simulation style and can take
 * a variety of parents, either standalone or with graphs, IM's or PM's
 * as parents. It essentially stores an ordered pair of <Graph, List<DataSet>>.
 * It is edited by SimulationEditor.
 *
 * @author jdramsey
 */
public class Simulation extends DataWrapper implements SessionModel,
        SimulationParamsSource, MultipleGraphSource, MultipleDataSource {
    static final long serialVersionUID = 23L;

    private edu.cmu.tetrad.algcomparison.simulation.Simulation simulation;
    private Parameters parameters;
    private String name;
    private boolean fixedGraph = true;
    private boolean fixedSimulation = true;
    private List<DataModel> inputDataModelList;

    //============================CONSTRUCTORS=========================//

    private Simulation() {
    }

    public Simulation(Parameters parameters) {
        if (simulation == null) {
            // By default there shouldn't be a simulation until the users create one - Zhou
            //this.simulation = new BayesNetSimulation(new RandomForward());
            this.parameters = parameters;
            this.fixedGraph = false;
            this.fixedSimulation = false;
        }
    }

//    public Simulation(Simulation simulation) {
//        this.simulation = simulation.simulation;
//        this.parameters = new Parameters(simulation.parameters);
//        this.name = simulation.name + ".copy";
//        this.fixedGraph = simulation.fixedGraph;
//        this.fixedSimulation = simulation.fixedSimulation;
//        createSimulation();
//    }

    public Simulation(GraphSource graphSource, Parameters parameters) {
        if (graphSource instanceof Simulation) {
            Simulation simulation = (Simulation) graphSource;
            this.simulation = simulation.simulation;
            this.parameters = new Parameters(simulation.parameters);
            this.name = simulation.name + ".copy";
            this.fixedGraph = simulation.fixedGraph;
            this.fixedSimulation = simulation.fixedSimulation;
//            createSimulation(); // The suggestion is that you should't actually simulate before the user clicks 'simulate'
        } else {
            simulation = new BayesNetSimulation(new SingleGraph(graphSource.getGraph()));
            this.fixedGraph = true;
            this.parameters = parameters;
            this.fixedSimulation = false;
            setSourceGraph(graphSource.getGraph());
        }
    }

    public Simulation(BayesImWrapper wrapper, Parameters parameters) {
        simulation = new BayesNetSimulation(wrapper.getBayesIm());
        this.parameters = parameters;
        createSimulation();
    }

    public Simulation(BayesImWrapperObs wrapper, Parameters parameters) {
        simulation = new BayesNetSimulation(wrapper.getBayesIm());
        this.parameters = parameters;
//        createSimulation();
    }

    public Simulation(BayesPmWrapper wrapper, Parameters parameters) {
        simulation = new BayesNetSimulation(wrapper.getBayesPm());
        this.parameters = parameters;
//        createSimulation();
    }

    public Simulation(BayesEstimatorWrapper wrapper, Parameters parameters) {
        simulation = new BayesNetSimulation(wrapper.getEstimatedBayesIm());
        this.parameters = parameters;
//        createSimulation();
    }

    public Simulation(DirichletBayesImWrapper wrapper, Parameters parameters) {
        simulation = new BayesNetSimulation(wrapper.getDirichletBayesIm());
        this.parameters = parameters;
//        createSimulation();
    }

    public Simulation(DirichletEstimatorWrapper wrapper, Parameters parameters) {
        simulation = new BayesNetSimulation(wrapper.getEstimatedBayesIm());
        this.parameters = parameters;
//        createSimulation();
    }

    public Simulation(CptInvariantUpdaterWrapper wrapper, Parameters parameters) {
        simulation = new BayesNetSimulation(wrapper.getBayesUpdater().getManipulatedBayesIm());
        this.parameters = parameters;
//        createSimulation();
    }

    public Simulation(SemPmWrapper wrapper, Parameters parameters) {
        simulation = new SemSimulation(wrapper.getSemPm());
        this.parameters = parameters;
//        createSimulation();
    }

    public Simulation(SemImWrapper wrapper, Parameters parameters) {
        simulation = new SemSimulation(wrapper.getSemIm());
        this.parameters = parameters;
//        createSimulation();
    }

    public Simulation(StandardizedSemImWrapper wrapper, Parameters parameters) {
        simulation = new StandardizedSemSimulation(wrapper.getStandardizedSemIm());
        this.parameters = parameters;
//        createSimulation();
    }

    public Simulation(SemEstimatorWrapper wrapper, Parameters parameters) {
        simulation = new SemSimulation(wrapper.getEstimatedSemIm());
        this.parameters = parameters;
//        createSimulation();
    }

    public Simulation(SemUpdaterWrapper wrapper, Parameters parameters) {
        simulation = new SemSimulation(wrapper.getSemUpdater().getManipulatedSemIm());
        this.parameters = parameters;
//        createSimulation();
    }

    public Simulation(GeneralizedSemPmWrapper wrapper, Parameters parameters) {
        simulation = new GeneralSemSimulation(wrapper.getSemPm());
        this.parameters = parameters;
//        createSimulation();
    }

    public Simulation(GeneralizedSemImWrapper wrapper, Parameters parameters) {
        if (wrapper.getSemIms().size() != 1) {
            throw new IllegalArgumentException("I'm sorry; this editor can only edit a single generalized SEM IM.");
        }

        simulation = new GeneralSemSimulation(wrapper.getSemIms().get(0));
        this.parameters = parameters;
        createSimulation();
    }

    public Simulation(DataWrapper dataWrapper, Parameters parameters) {
        if (simulation == null) {
            this.simulation = new LinearFisherModel(new RandomForward(), dataWrapper.getDataModelList());
            this.inputDataModelList = dataWrapper.getDataModelList();
            this.parameters = parameters;
            this.fixedGraph = false;
            this.fixedSimulation = false;
        }
    }

    public edu.cmu.tetrad.algcomparison.simulation.Simulation getSimulation() {
        return this.simulation;
    }

    public void setSimulation(edu.cmu.tetrad.algcomparison.simulation.Simulation simulation, Parameters parameters) {
        this.simulation = simulation;
        this.parameters = parameters;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static PcRunner serializableInstance() {
        return PcRunner.serializableInstance();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Parameters getParams() {
        return parameters;
    }

    /**
     * Sets the data model.
     */
    public void setDataModel(DataModel dataModel) {
    }

    /**
     * @return the list of models.
     */
    public DataModelList getDataModelList() {
        DataModelList list = new DataModelList();

        for (int i = 0; i < simulation.getNumDataModels(); i++) {
            list.add(simulation.getDataModel(i));
        }

        return list;
    }

    /**
     * @return the list of models.
     */
    public List<DataModel> getDataModels() {
        List<DataModel> list = new ArrayList<>();

        for (int i = 0; i < simulation.getNumDataModels(); i++) {
            list.add(simulation.getDataModel(i));
        }

        return list;
    }

    public void setDataModelList(DataModelList dataModelList) {
        throw new UnsupportedOperationException();
    }

    public void setParameters(Parameters parameters) {
        this.parameters = parameters;
    }

    @Override
    public Map<String, String> getParamSettings() {
        return new HashMap<>();
    }

    public void createSimulation() {
        // Every time the users click the Simulate button, new data needs to be created
        // regardless of already created data - Zhou
        //if (simulation.getNumDataModels() == 0) {
            simulation.createData(parameters);
        //}
    }

    @Override
    /**
     * Returns all of the graphs in the simulation, in order.
     */
    public List<Graph> getGraphs() {
        List<Graph> graphs = new ArrayList<>();

        for (int i = 0; i < simulation.getNumDataModels(); i++) {
            graphs.add(simulation.getTrueGraph(i));
        }

        return graphs;
    }

    public boolean isFixedSimulation() {
        return fixedSimulation;
    }

    public void setFixedSimulation(boolean fixedSimulation) {
        this.fixedSimulation = fixedSimulation;
    }

    public boolean isFixedGraph() {
        return fixedGraph;
    }

    public void setFixedGraph(boolean fixedGraph) {
        this.fixedGraph = fixedGraph;
    }

    public IKnowledge getKnowledge() {
        if (simulation instanceof HasKnowledge) {
            return ((HasKnowledge) simulation).getKnowledge();
        } else {
            return new Knowledge2();
        }
    }

    public void setSimulation(edu.cmu.tetrad.algcomparison.simulation.Simulation simulation) {
        this.simulation = simulation;
    }

    public List<DataModel> getInputDataModelList() {
        return inputDataModelList;
    }
}





