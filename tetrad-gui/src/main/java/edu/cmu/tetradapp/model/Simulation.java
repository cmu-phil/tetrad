/// ////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
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
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataModelList;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.io.Serial;
import java.util.*;

/**
 * Wraps a Simulation object for the Tetrad interface. A Simulation object requires a RandomGraph and a choice of
 * Simulation style and can take a variety of parents, either standalone or with graphs, IM's or PM's as parents. It
 * essentially stores an ordered pair of [Graph, List[DataSet]]. It is edited by SimulationEditor.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class Simulation extends DataWrapper implements
        GraphSource, MultipleGraphSource {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The simulation.
     */
    private edu.cmu.tetrad.algcomparison.simulation.Simulation simulation;

    /**
     * The parameters.
     */
    private Parameters parameters;

    /**
     * The name.
     */
    private String name;

    /**
     * The fixed graph.
     */
    private boolean fixedGraph = true;

    /**
     * The fixed simulation.
     */
    private boolean fixedSimulation = true;

    /**
     * Constructs an instance of the Simulation class.
     * <p>
     * This constructor is marked as private to prevent external instantiation of the Simulation class. The Simulation
     * class follows the Singleton design pattern, where only one instance of the class can exist. To obtain the
     * instance of the Simulation class, use the getInstance() method.
     */
    private Simulation() {
    }

    /**
     * Initializes a new Simulation with the given Parameters.
     *
     * @param parameters The parameters for the simulation.
     */
    public Simulation(Parameters parameters) {
        if (this.simulation == null) {
            // By default, there shouldn't be a simulation until the users create one - Zhou
            //this.simulation = new BayesNetSimulation(new RandomForward());
            this.parameters = parameters;
            this.fixedGraph = false;
            this.fixedSimulation = false;
        }
    }

    /**
     * Creates a new Simulation object based on the provided GraphSource and Parameters.
     *
     * @param graphSource the source of the graph to be used in the simulation
     * @param parameters  the parameters to be used in the simulation
     */
    public Simulation(GraphSource graphSource, Parameters parameters) {
        if (graphSource instanceof Simulation _simulation) {
            this.simulation = _simulation.simulation;
            this.parameters = new Parameters(_simulation.parameters);
            this.name = _simulation.name + ".copy";
            this.fixedGraph = _simulation.fixedGraph;
            this.fixedSimulation = _simulation.fixedSimulation;
            createSimulation(); // The suggestion is that you shouldn't simulate before the user clicks 'simulate'
        } else {
            this.fixedGraph = true;
            this.parameters = parameters;
            this.fixedSimulation = false;
            setSourceGraph(graphSource.getGraph());

            if (parameters.getParametersNames().contains("simulationsDropdownPreference")) {
                String simulationType = String.valueOf(parameters.getValues("simulationsDropdownPreference")[0]);
                this.simulation = SimulationUtils.create(simulationType, new SingleGraph(graphSource.getGraph()));

                // Resimulation whenever graph source changed and "Execute" button is clicked.
                createSimulation();
            } else {
                this.simulation = new BayesNetSimulation(new SingleGraph(graphSource.getGraph()));
            }
        }
    }

    /**
     * Initializes a new Simulation instance with the provided BayesImWrapper and Parameters.
     *
     * @param wrapper    the BayesImWrapper instance used to create the simulation
     * @param parameters the Parameters instance used for configuring the simulation
     */
    public Simulation(BayesImWrapper wrapper, Parameters parameters) {
        this.simulation = new BayesNetSimulation(wrapper.getBayesIm());
        this.parameters = parameters;
        createSimulation();
    }

    /**
     * Initializes a new Simulation object with the given BayesImWrapperObs and Parameters.
     *
     * @param wrapper    the BayesImWrapperObs object to use for creating the simulation
     * @param parameters the Parameters object for the simulation
     */
    public Simulation(BayesImWrapperObs wrapper, Parameters parameters) {
        this.simulation = new BayesNetSimulation(wrapper.getBayesIm());
        this.parameters = parameters;
        createSimulation();
    }

    /**
     * Creates a Simulation object with the given BayesPmWrapper and Parameters.
     *
     * @param wrapper    The BayesPmWrapper object containing the Bayesian network model.
     * @param parameters The Parameters object containing the simulation parameters.
     */
    public Simulation(BayesPmWrapper wrapper, Parameters parameters) {
        this.simulation = new BayesNetSimulation(wrapper.getBayesPm());
        this.parameters = parameters;
        createSimulation();
    }

    /**
     * Creates a Simulation object with the given BayesEstimatorWrapper and Parameters.
     *
     * @param wrapper    The BayesEstimatorWrapper object.
     * @param parameters The Parameters object.
     */
    public Simulation(BayesEstimatorWrapper wrapper, Parameters parameters) {
        this.simulation = new BayesNetSimulation(wrapper.getEstimatedBayesIm());
        this.parameters = parameters;
        createSimulation();
    }

    /**
     * Initializes a simulation with the given wrapper and parameters.
     *
     * @param wrapper    The DirichletBayesImWrapper used by the simulation.
     * @param parameters The Parameters used for the simulation.
     */
    public Simulation(DirichletBayesImWrapper wrapper, Parameters parameters) {
        this.simulation = new BayesNetSimulation(wrapper.getDirichletBayesIm());
        this.parameters = parameters;
        createSimulation();
    }

    /**
     * Initializes a new instance of the Simulation class with the specified DirichletEstimatorWrapper and Parameters.
     *
     * @param wrapper    The DirichletEstimatorWrapper object used to estimate the Bayesian network.
     * @param parameters The Parameters object used for the simulation.
     */
    public Simulation(DirichletEstimatorWrapper wrapper, Parameters parameters) {
        this.simulation = new BayesNetSimulation(wrapper.getEstimatedBayesIm());
        this.parameters = parameters;
        createSimulation();
    }

    /**
     * Creates a Simulation object.
     *
     * @param wrapper    the CptInvariantUpdaterWrapper object used for initializing the simulation
     * @param parameters the Parameters object used for configuring the simulation
     */
    public Simulation(CptInvariantUpdaterWrapper wrapper, Parameters parameters) {
        this.simulation = new BayesNetSimulation(wrapper.getBayesUpdater().getManipulatedBayesIm());
        this.parameters = parameters;
        createSimulation();
    }

    /**
     * Constructs a Simulation object using the given SemPmWrapper and Parameters.
     *
     * @param wrapper    the SemPmWrapper object for accessing the SEM-PM functionality
     * @param parameters the Parameters object containing simulation parameters
     */
    public Simulation(SemPmWrapper wrapper, Parameters parameters) {
        this.simulation = new SemSimulation(wrapper.getSemPm());
        this.parameters = parameters;
        createSimulation();
    }

    /**
     * Constructs a Simulation object using the specified SemImWrapper and Parameters.
     *
     * @param wrapper    the SemImWrapper object used to initialize the simulation
     * @param parameters the Parameters object used to configure the simulation
     */
    public Simulation(SemImWrapper wrapper, Parameters parameters) {
        this.simulation = new SemSimulation(wrapper.getSemIm());
        this.parameters = parameters;
        createSimulation();
    }

    /**
     * Initializes a new Simulation object.
     *
     * @param wrapper    The StandardizedSemImWrapper object that encapsulates the standardized semantic image.
     * @param parameters The Parameters object that specifies the simulation parameters.
     */
    public Simulation(StandardizedSemImWrapper wrapper, Parameters parameters) {
        this.simulation = new StandardizedSemSimulation(wrapper.getStandardizedSemIm());
        this.parameters = parameters;
        createSimulation();
    }

    /**
     * Initializes a Simulation object with the specified wrapper and parameters.
     *
     * @param wrapper    the SemEstimatorWrapper containing the estimated SEM image
     * @param parameters the Parameters object containing the simulation parameters
     */
    public Simulation(SemEstimatorWrapper wrapper, Parameters parameters) {
        this.simulation = new SemSimulation(wrapper.getEstimatedSemIm());
        this.parameters = parameters;
        createSimulation();
    }

    /**
     * Constructs a new Simulation object.
     *
     * @param wrapper    the SemUpdaterWrapper object containing the SEM updater
     * @param parameters the Parameters object containing simulation parameters
     */
    public Simulation(SemUpdaterWrapper wrapper, Parameters parameters) {
        this.simulation = new SemSimulation(wrapper.getSemUpdater().getManipulatedSemIm());
        this.parameters = parameters;
        createSimulation();
    }

    /**
     * Initializes a new simulation using a provided wrapper and parameters.
     *
     * @param wrapper    the wrapper object that provides the necessary SEM-PM model for the simulation
     * @param parameters the parameters needed for running the simulation
     */
    public Simulation(GeneralizedSemPmWrapper wrapper, Parameters parameters) {
        this.simulation = new GeneralSemSimulation(wrapper.getSemPm());
        this.parameters = parameters;
        createSimulation();
    }

    /**
     * Creates a new Simulation object with the given wrapper and parameters.
     *
     * @param wrapper    the GeneralizedSemImWrapper object to be used for simulation
     * @param parameters the Parameters object containing simulation parameters
     * @throws IllegalArgumentException if the wrapper contains more than one SEM IM
     */
    public Simulation(GeneralizedSemImWrapper wrapper, Parameters parameters) {
        if (wrapper.getSemIms().size() != 1) {
            throw new IllegalArgumentException("I'm sorry; this editor can only edit a single generalized SEM IM.");
        }

        this.simulation = new GeneralSemSimulation(wrapper.getSemIms().getFirst());
        this.parameters = parameters;
        createSimulation();
    }

    /**
     * Initializes a simulation object with the given dataWrapper and parameters.
     *
     * @param dataWrapper the data wrapper object containing the required data model list
     * @param parameters  the parameters for the simulation
     */
    public Simulation(DataWrapper dataWrapper, Parameters parameters) {
        if (this.simulation == null) {
            this.simulation = new LinearFisherModel(new RandomForward(), dataWrapper.getDataModelList());
            this.parameters = parameters;
            this.fixedGraph = false;
            this.fixedSimulation = false;
        }
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetradapp.model.PcRunner} object
     * @see TetradSerializableUtils
     */
    public static PcRunner serializableInstance() {
        return PcRunner.serializableInstance();
    }

    /**
     * Returns the simulation used in the algorithm comparison.
     *
     * @return the simulation object used in the algorithm comparison
     */
    public edu.cmu.tetrad.algcomparison.simulation.Simulation getSimulation() {
        return this.simulation;
    }

    /**
     * Sets the simulation for the algorithm comparison.
     *
     * @param simulation the simulation to set
     */
    public void setSimulation(edu.cmu.tetrad.algcomparison.simulation.Simulation simulation) {
        this.simulation = simulation;
    }

    /**
     * Sets the simulation and parameters for this object.
     *
     * @param simulation The simulation to be set.
     * @param parameters The parameters to be set.
     */
    public void setSimulation(edu.cmu.tetrad.algcomparison.simulation.Simulation simulation, Parameters parameters) {
        this.simulation = simulation;
        this.parameters = parameters;
    }

    /**
     * Returns the name of this object.
     *
     * @return the name of this object
     */
    public String getName() {
        return this.name;
    }

    /**
     * Sets the name of the session model.
     *
     * @param name the name to be set for the session model
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the parameters of this method.
     *
     * @return the {@code Parameters} object containing the parameters of this method
     */
    public Parameters getParams() {
        return this.parameters;
    }

    /**
     * Sets the data model for the object.
     *
     * @param dataModel the data model to set
     */
    public void setDataModel(DataModel dataModel) {
    }

    /**
     * Retrieves a list of data models from the simulation.
     *
     * @return A DataModelList object containing the data models.
     */
    public DataModelList getDataModelList() {
        DataModelList list = new DataModelList();

        for (int i = 0; i < this.simulation.getNumDataModels(); i++) {
            list.add(this.simulation.getDataModel(i));
        }

        return list;
    }

    /**
     * Sets the data model list.
     *
     * @param dataModelList the data model list to set
     */
    public void setDataModelList(DataModelList dataModelList) {
        throw new UnsupportedOperationException();
    }

    /**
     * Retrieves a list of data models.
     *
     * @return a list of DataModel objects
     */
    public List<DataModel> getDataModels() {
        List<DataModel> list = new ArrayList<>();

        for (int i = 0; i < this.simulation.getNumDataModels(); i++) {
            list.add(this.simulation.getDataModel(i));
        }

        return list;
    }

    /**
     * Sets the parameters.
     *
     * @param parameters the parameters to set
     */
    public void setParameters(Parameters parameters) {
        this.parameters = parameters;
    }

    /**
     * Returns the parameter settings as a map.
     *
     * @return a map containing the parameter settings
     */
    @Override
    public Map<String, String> getParamSettings() {
        return new HashMap<>();
    }

    /**
     * Creates a simulation with new data.
     * <p>
     * This method is called when the user clicks the "Simulate" button. It creates new data for the simulation,
     * regardless of any previously created data.
     * </p>
     */
    public void createSimulation() {
        // Every time the users click the Simulate button, new data needs to be created
        // regardless of already created data - Zhou
        this.simulation.createData(this.parameters, false);
    }

    /**
     * Returns all the graphs in the simulation, in order.
     *
     * @return a {@link java.util.List} object
     */
    public List<Graph> getGraphs() {
        List<Graph> graphs = new ArrayList<>();

        for (int i = 0; i < this.simulation.getNumDataModels(); i++) {
            graphs.add(this.simulation.getTrueGraph(i));
        }

        return graphs;
    }

    /**
     * Checks if the simulation is fixed.
     *
     * @return true if the simulation is fixed, false otherwise.
     */
    public boolean isFixedSimulation() {
        return this.fixedSimulation;
    }

    /**
     * Sets whether the simulation is fixed or not.
     *
     * @param fixedSimulation true if the simulation should be fixed, false otherwise
     */
    public void setFixedSimulation(boolean fixedSimulation) {
        this.fixedSimulation = fixedSimulation;
    }

    /**
     * Checks if the graph is fixed.
     *
     * @return true if the graph is fixed, false otherwise.
     */
    public boolean isFixedGraph() {
        return this.fixedGraph;
    }

    /**
     * Sets the flag indicating whether the graph is fixed or not.
     *
     * @param fixedGraph true if the graph is fixed, false otherwise
     */
    public void setFixedGraph(boolean fixedGraph) {
        this.fixedGraph = fixedGraph;
    }

    /**
     * Retrieves the knowledge of the simulation. If the simulation implements the interface 'HasKnowledge', it returns
     * the knowledge obtained from the simulation. Otherwise, it returns a new instance of Knowledge.
     *
     * @return the knowledge obtained from the simulation, or a new instance of Knowledge if the simulation does not
     * implement HasKnowledge
     */
    public Knowledge getKnowledge() {
        if (this.simulation instanceof HasKnowledge) {
            return ((HasKnowledge) this.simulation).getKnowledge();
        } else {
            return new Knowledge();
        }
    }

    /**
     * Retrieves the graph associated with this object.
     *
     * @return the graph associated with this object.
     * @throws IllegalArgumentException if there is not exactly one graph associated with this object.
     */
    @Override
    public Graph getGraph() {
        Set<Graph> graphs = new HashSet<>(getGraphs());
        if (graphs.size() == 1) {
            return graphs.iterator().next();
        } else {
            throw new IllegalArgumentException("Expecting one graph.");
        }
    }
}
