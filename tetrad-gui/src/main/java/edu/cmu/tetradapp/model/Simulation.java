///////////////////////////////////////////////////////////////////////////////
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
     * The input data model list.
     */
    private List<DataModel> inputDataModelList;

    //============================CONSTRUCTORS=========================//
    private Simulation() {
    }

    /**
     * <p>Constructor for Simulation.</p>
     *
     * @param parameters a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public Simulation(Parameters parameters) {
        if (this.simulation == null) {
            // By default there shouldn't be a simulation until the users create one - Zhou
            //this.simulation = new BayesNetSimulation(new RandomForward());
            this.parameters = parameters;
            this.fixedGraph = false;
            this.fixedSimulation = false;
        }
    }

    /**
     * <p>Constructor for Simulation.</p>
     *
     * @param graphSource a {@link edu.cmu.tetradapp.model.GraphSource} object
     * @param parameters a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public Simulation(GraphSource graphSource, Parameters parameters) {
        if (graphSource instanceof Simulation) {
            Simulation simulation = (Simulation) graphSource;
            this.simulation = simulation.simulation;
            this.parameters = new Parameters(simulation.parameters);
            this.name = simulation.name + ".copy";
            this.fixedGraph = simulation.fixedGraph;
            this.fixedSimulation = simulation.fixedSimulation;
            createSimulation(); // The suggestion is that you should't actually simulate before the user clicks 'simulate'
        } else {
            this.fixedGraph = true;
            this.parameters = parameters;
            this.fixedSimulation = false;
            setSourceGraph(graphSource.getGraph());

            if (parameters.getParametersNames().contains("simulationsDropdownPreference")) {
                String simulationType = String.valueOf(parameters.getValues("simulationsDropdownPreference")[0]);
                this.simulation = SimulationUtils.create(simulationType, new SingleGraph(graphSource.getGraph()));

                // Re-simuation whenever graph source changed and "Execute" button is clicked.
                createSimulation();
            } else {
                this.simulation = new BayesNetSimulation(new SingleGraph(graphSource.getGraph()));
            }
        }
    }

    /**
     * <p>Constructor for Simulation.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.BayesImWrapper} object
     * @param parameters a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public Simulation(BayesImWrapper wrapper, Parameters parameters) {
        this.simulation = new BayesNetSimulation(wrapper.getBayesIm());
        this.parameters = parameters;
        createSimulation();
    }

    /**
     * <p>Constructor for Simulation.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.BayesImWrapperObs} object
     * @param parameters a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public Simulation(BayesImWrapperObs wrapper, Parameters parameters) {
        this.simulation = new BayesNetSimulation(wrapper.getBayesIm());
        this.parameters = parameters;
        createSimulation();
    }

    /**
     * <p>Constructor for Simulation.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.BayesPmWrapper} object
     * @param parameters a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public Simulation(BayesPmWrapper wrapper, Parameters parameters) {
        this.simulation = new BayesNetSimulation(wrapper.getBayesPm());
        this.parameters = parameters;
        createSimulation();
    }

    /**
     * <p>Constructor for Simulation.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.BayesEstimatorWrapper} object
     * @param parameters a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public Simulation(BayesEstimatorWrapper wrapper, Parameters parameters) {
        this.simulation = new BayesNetSimulation(wrapper.getEstimatedBayesIm());
        this.parameters = parameters;
        createSimulation();
    }

    /**
     * <p>Constructor for Simulation.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.DirichletBayesImWrapper} object
     * @param parameters a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public Simulation(DirichletBayesImWrapper wrapper, Parameters parameters) {
        this.simulation = new BayesNetSimulation(wrapper.getDirichletBayesIm());
        this.parameters = parameters;
        createSimulation();
    }

    /**
     * <p>Constructor for Simulation.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.DirichletEstimatorWrapper} object
     * @param parameters a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public Simulation(DirichletEstimatorWrapper wrapper, Parameters parameters) {
        this.simulation = new BayesNetSimulation(wrapper.getEstimatedBayesIm());
        this.parameters = parameters;
        createSimulation();
    }

    /**
     * <p>Constructor for Simulation.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.CptInvariantUpdaterWrapper} object
     * @param parameters a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public Simulation(CptInvariantUpdaterWrapper wrapper, Parameters parameters) {
        this.simulation = new BayesNetSimulation(wrapper.getBayesUpdater().getManipulatedBayesIm());
        this.parameters = parameters;
        createSimulation();
    }

    /**
     * <p>Constructor for Simulation.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.SemPmWrapper} object
     * @param parameters a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public Simulation(SemPmWrapper wrapper, Parameters parameters) {
        this.simulation = new SemSimulation(wrapper.getSemPm());
        this.parameters = parameters;
        createSimulation();
    }

    /**
     * <p>Constructor for Simulation.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.SemImWrapper} object
     * @param parameters a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public Simulation(SemImWrapper wrapper, Parameters parameters) {
        this.simulation = new SemSimulation(wrapper.getSemIm());
        this.parameters = parameters;
        createSimulation();
    }

    /**
     * <p>Constructor for Simulation.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.StandardizedSemImWrapper} object
     * @param parameters a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public Simulation(StandardizedSemImWrapper wrapper, Parameters parameters) {
        this.simulation = new StandardizedSemSimulation(wrapper.getStandardizedSemIm());
        this.parameters = parameters;
        createSimulation();
    }

    /**
     * <p>Constructor for Simulation.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.SemEstimatorWrapper} object
     * @param parameters a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public Simulation(SemEstimatorWrapper wrapper, Parameters parameters) {
        this.simulation = new SemSimulation(wrapper.getEstimatedSemIm());
        this.parameters = parameters;
        createSimulation();
    }

    /**
     * <p>Constructor for Simulation.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.SemUpdaterWrapper} object
     * @param parameters a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public Simulation(SemUpdaterWrapper wrapper, Parameters parameters) {
        this.simulation = new SemSimulation(wrapper.getSemUpdater().getManipulatedSemIm());
        this.parameters = parameters;
        createSimulation();
    }

    /**
     * <p>Constructor for Simulation.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.GeneralizedSemPmWrapper} object
     * @param parameters a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public Simulation(GeneralizedSemPmWrapper wrapper, Parameters parameters) {
        this.simulation = new GeneralSemSimulation(wrapper.getSemPm());
        this.parameters = parameters;
        createSimulation();
    }

    /**
     * <p>Constructor for Simulation.</p>
     *
     * @param wrapper a {@link edu.cmu.tetradapp.model.GeneralizedSemImWrapper} object
     * @param parameters a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public Simulation(GeneralizedSemImWrapper wrapper, Parameters parameters) {
        if (wrapper.getSemIms().size() != 1) {
            throw new IllegalArgumentException("I'm sorry; this editor can only edit a single generalized SEM IM.");
        }

        this.simulation = new GeneralSemSimulation(wrapper.getSemIms().get(0));
        this.parameters = parameters;
        createSimulation();
    }

    /**
     * <p>Constructor for Simulation.</p>
     *
     * @param dataWrapper a {@link edu.cmu.tetradapp.model.DataWrapper} object
     * @param parameters a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public Simulation(DataWrapper dataWrapper, Parameters parameters) {
        if (this.simulation == null) {
            this.simulation = new LinearFisherModel(new RandomForward(), dataWrapper.getDataModelList());
            this.inputDataModelList = dataWrapper.getDataModelList();
            this.parameters = parameters;
            this.fixedGraph = false;
            this.fixedSimulation = false;
        }
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     * @return a {@link edu.cmu.tetradapp.model.PcRunner} object
     */
    public static PcRunner serializableInstance() {
        return PcRunner.serializableInstance();
    }

    /**
     * <p>Getter for the field <code>simulation</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.algcomparison.simulation.Simulation} object
     */
    public edu.cmu.tetrad.algcomparison.simulation.Simulation getSimulation() {
        return this.simulation;
    }

    /**
     * <p>Setter for the field <code>simulation</code>.</p>
     *
     * @param simulation a {@link edu.cmu.tetrad.algcomparison.simulation.Simulation} object
     */
    public void setSimulation(edu.cmu.tetrad.algcomparison.simulation.Simulation simulation) {
        this.simulation = simulation;
    }

    /**
     * <p>Setter for the field <code>simulation</code>.</p>
     *
     * @param simulation a {@link edu.cmu.tetrad.algcomparison.simulation.Simulation} object
     * @param parameters a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public void setSimulation(edu.cmu.tetrad.algcomparison.simulation.Simulation simulation, Parameters parameters) {
        this.simulation = simulation;
        this.parameters = parameters;
    }

    /**
     * <p>Getter for the field <code>name</code>.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getName() {
        return this.name;
    }

    /** {@inheritDoc} */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * <p>getParams.</p>
     *
     * @return a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public Parameters getParams() {
        return this.parameters;
    }

    /**
     * {@inheritDoc}
     *
     * Sets the data model.
     */
    public void setDataModel(DataModel dataModel) {
    }

    /**
     * <p>getDataModelList.</p>
     *
     * @return the list of models.
     */
    public DataModelList getDataModelList() {
        DataModelList list = new DataModelList();

        for (int i = 0; i < this.simulation.getNumDataModels(); i++) {
            list.add(this.simulation.getDataModel(i));
        }

        return list;
    }

    /** {@inheritDoc} */
    public void setDataModelList(DataModelList dataModelList) {
        throw new UnsupportedOperationException();
    }

    /**
     * <p>getDataModels.</p>
     *
     * @return the list of models.
     */
    public List<DataModel> getDataModels() {
        List<DataModel> list = new ArrayList<>();

        for (int i = 0; i < this.simulation.getNumDataModels(); i++) {
            list.add(this.simulation.getDataModel(i));
        }

        return list;
    }

    /** {@inheritDoc} */
    public void setParameters(Parameters parameters) {
        this.parameters = parameters;
    }

    /** {@inheritDoc} */
    @Override
    public Map<String, String> getParamSettings() {
        return new HashMap<>();
    }

    /**
     * <p>createSimulation.</p>
     */
    public void createSimulation() {
        // Every time the users click the Simulate button, new data needs to be created
        // regardless of already created data - Zhou
        //if (simulation.getNumDataModels() == 0) {
        this.simulation.createData(this.parameters, false);
        //}
    }

    /**
     * Returns all of the graphs in the simulation, in order.
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
     * <p>isFixedSimulation.</p>
     *
     * @return a boolean
     */
    public boolean isFixedSimulation() {
        return this.fixedSimulation;
    }

    /**
     * <p>Setter for the field <code>fixedSimulation</code>.</p>
     *
     * @param fixedSimulation a boolean
     */
    public void setFixedSimulation(boolean fixedSimulation) {
        this.fixedSimulation = fixedSimulation;
    }

    /**
     * <p>isFixedGraph.</p>
     *
     * @return a boolean
     */
    public boolean isFixedGraph() {
        return this.fixedGraph;
    }

    /**
     * <p>Setter for the field <code>fixedGraph</code>.</p>
     *
     * @param fixedGraph a boolean
     */
    public void setFixedGraph(boolean fixedGraph) {
        this.fixedGraph = fixedGraph;
    }

    /**
     * <p>getKnowledge.</p>
     *
     * @return a {@link edu.cmu.tetrad.data.Knowledge} object
     */
    public Knowledge getKnowledge() {
        if (this.simulation instanceof HasKnowledge) {
            return ((HasKnowledge) this.simulation).getKnowledge();
        } else {
            return new Knowledge();
        }
    }

    /**
     * <p>Getter for the field <code>inputDataModelList</code>.</p>
     *
     * @return a {@link java.util.List} object
     */
    public List<DataModel> getInputDataModelList() {
        return this.inputDataModelList;
    }

    /** {@inheritDoc} */
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
