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
import edu.cmu.tetrad.algcomparison.simulation.BayesNetSimulation;
import edu.cmu.tetrad.algcomparison.simulation.GeneralSemSimulation;
import edu.cmu.tetrad.algcomparison.simulation.SemSimulation;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.session.SessionModel;
import edu.cmu.tetrad.session.SimulationParamsSource;
import edu.cmu.tetrad.algcomparison.utils.Parameters;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Wraps a data model so that a random sample will automatically be drawn on
 * construction from a BayesIm.
 *
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 */
public class Simulation extends DataWrapper implements SessionModel,
        SimulationParamsSource {
    static final long serialVersionUID = 23L;

    private edu.cmu.tetrad.algcomparison.simulation.Simulation simulation;
    private Parameters parameters;
    private String name;
    private boolean fixSimulation = false;

    //============================CONSTRUCTORS=========================//

    public Simulation(Parameters parameters) {
        this.simulation = null;
        this.parameters = parameters;
    }

    public Simulation(edu.cmu.tetrad.algcomparison.simulation.Simulation simulation, Parameters parameters) {
        this.simulation = simulation;
        this.parameters = parameters;
    }

    public Simulation(BayesImWrapper wrapper, Parameters parameters) {
        simulation = new BayesNetSimulation(new SingleGraph(wrapper.getGraph()));
        fixSimulation = true;
        this.parameters = parameters;
        createSimulation();
        LogDataUtils.logDataModelList("Data simulated from a Bayes net.", getDataModelList());
    }

    public Simulation(BayesPmWrapper wrapper, Parameters parameters) {
        simulation = new BayesNetSimulation(wrapper.getBayesPm());
        fixSimulation = true;
        this.parameters = parameters;
        createSimulation();
        LogDataUtils.logDataModelList("Data simulated from a Bayes net.", getDataModelList());
    }

    public Simulation(BayesEstimatorWrapper wrapper, Parameters parameters) {
        simulation = new BayesNetSimulation(wrapper.getEstimatedBayesIm());
        fixSimulation = true;
        this.parameters = parameters;
        createSimulation();
        LogDataUtils.logDataModelList("Data simulated from a Bayes net.", getDataModelList());
    }

    public Simulation(DirichletBayesImWrapper wrapper, Parameters parameters) {
        simulation = new BayesNetSimulation(wrapper.getDirichletBayesIm());
        fixSimulation = true;
        this.parameters = parameters;
        createSimulation();
        LogDataUtils.logDataModelList("Data simulated from a Bayes net.", getDataModelList());
    }

    public Simulation(DirichletEstimatorWrapper wrapper, Parameters parameters) {
        simulation = new BayesNetSimulation(wrapper.getEstimatedBayesIm());
        fixSimulation = true;
        this.parameters = parameters;
        createSimulation();
        LogDataUtils.logDataModelList("Data simulated from a Bayes net.", getDataModelList());
    }

    public Simulation(CptInvariantUpdaterWrapper wrapper, Parameters parameters) {
        simulation = new BayesNetSimulation(wrapper.getBayesUpdater().getManipulatedBayesIm());
        fixSimulation = true;
        this.parameters = parameters;
        createSimulation();
        LogDataUtils.logDataModelList("Data simulated from a Bayes net.", getDataModelList());
    }

    public Simulation(SemPmWrapper wrapper, Parameters parameters) {
        simulation = new SemSimulation(wrapper.getSemPm());
        fixSimulation = true;
        this.parameters = parameters;
        createSimulation();
        LogDataUtils.logDataModelList("Data simulated from a Bayes net.", getDataModelList());
    }

    public Simulation(SemImWrapper wrapper, Parameters parameters) {
        simulation = new SemSimulation(wrapper.getSemIm());
        fixSimulation = true;
        this.parameters = parameters;
        createSimulation();
        LogDataUtils.logDataModelList("Data simulated from a Bayes net.", getDataModelList());
    }

    public Simulation(SemEstimatorWrapper wrapper, Parameters parameters) {
        simulation = new SemSimulation(wrapper.getEstimatedSemIm());
        fixSimulation = true;
        this.parameters = parameters;
        createSimulation();
        LogDataUtils.logDataModelList("Data simulated from a Bayes net.", getDataModelList());
    }

    public Simulation(SemUpdaterWrapper wrapper, Parameters parameters) {
        simulation = new SemSimulation(wrapper.getSemUpdater().getManipulatedSemIm());
        fixSimulation = true;
        this.parameters = parameters;
        createSimulation();
        LogDataUtils.logDataModelList("Data simulated from a Bayes net.", getDataModelList());
    }

    public Simulation(StandardizedSemImWrapper wrapper, Parameters parameters) {
        simulation = new SemSimulation(wrapper.getStandardizedSemIm());
        fixSimulation = true;
        this.parameters = parameters;
        createSimulation();
        LogDataUtils.logDataModelList("Data simulated from a Bayes net.", getDataModelList());
    }

    public Simulation(GeneralizedSemPmWrapper wrapper, Parameters parameters) {
        simulation = new GeneralSemSimulation(wrapper.getSemPm());
        fixSimulation = true;
        this.parameters = parameters;
        createSimulation();
        LogDataUtils.logDataModelList("Data simulated from a Bayes net.", getDataModelList());
    }

    public Simulation(GeneralizedSemImWrapper wrapper, Parameters parameters) {
        simulation = new GeneralSemSimulation(wrapper.getSemIm());
        fixSimulation = true;
        this.parameters = parameters;
        createSimulation();
        LogDataUtils.logDataModelList("Data simulated from a Bayes net.", getDataModelList());
    }

    public edu.cmu.tetrad.algcomparison.simulation.Simulation getSimulation() {
        return this.simulation;
    }

    public void setSimulation(edu.cmu.tetrad.algcomparison.simulation.Simulation simulation) {
        this.simulation = simulation;
        createSimulation();
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static DataWrapper serializableInstance() {
        return new Simulation(new BayesNetSimulation(new RandomForward()), new Parameters());
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

        for (int i = 0; i < simulation.getNumDataSets(); i++) {
            list.add(simulation.getDataSet(i));
        }

        return list;
    }

    public void setDataModelList(DataModelList dataModelList) {
    }

    public void setParams(Parameters parameters) {
        this.parameters = parameters;
    }

    @Override
    public Map<String, String> getParamSettings() {
        Map<String, String> paramSettings = new HashMap<>();

//        if (dataModelList == null) {
//            System.out.println();
//        }
//
//        if (dataModelList.size() > 1) {
//            paramSettings.put("# Datasets", Integer.toString(dataModelList.size()));
//        } else {
//            DataModel dataModel = dataModelList.get(0);
//
//            if (!paramSettings.containsKey("# Nodes")) {
//                paramSettings.put("# Vars", Integer.toString(((DataSet) dataModel).getNumColumns()));
//            }
//            paramSettings.put("N", Integer.toString(((DataSet) dataModel).getNumRows()));
//        }

        return paramSettings;
    }

    public void createSimulation() {
        simulation.createData(parameters);
    }

    public boolean isFixSimulation() {
        return fixSimulation;
    }
}





