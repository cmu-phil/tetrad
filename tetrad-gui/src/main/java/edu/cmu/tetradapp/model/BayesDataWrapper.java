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

import edu.cmu.tetrad.bayes.BayesIm;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.sem.GeneralizedSemIm;
import edu.cmu.tetrad.sem.Simulator;
import edu.cmu.tetrad.session.SessionModel;
import edu.cmu.tetrad.session.SimulationParamsSource;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.rmi.MarshalledObject;
import java.util.HashMap;
import java.util.Map;

/**
 * Wraps a data model so that a random sample will automatically be drawn on
 * construction from a BayesIm.
 *
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 */
public class BayesDataWrapper extends DataWrapper implements SessionModel,
        SimulationParamsSource {
    static final long serialVersionUID = 23L;

    /**
     * @serial Can be null.
     */
    private String name;

    /**
     * The Bayes Im being sampled from.
     */
    private BayesIm bayesIm = null;
    private BayesDataParams params;

    private transient DataModelList dataModelList;

    private long seed;

    //============================CONSTRUCTORS=========================//

    public BayesDataWrapper(BayesImWrapper wrapper, BayesDataParams params) {
        BayesIm bayesIm = null;

        try {
            bayesIm = new MarshalledObject<>(wrapper.getBayesIm()).get();
        } catch (Exception e) {
            throw new RuntimeException("Could not clone the SEM IM.");
        }

        this.bayesIm = bayesIm;

        try {
            params = new MarshalledObject<>(params).get();
        } catch (Exception e) {
            throw new RuntimeException("Could not clone the SemDataParams.");
        }

        DataModelList list = simulateData(wrapper.getBayesIm(), params);
        setDataModel(list);
        setSourceGraph(wrapper.getBayesIm().getDag());
        this.bayesIm = wrapper.getBayesIm();
        setParams(params);
        setSeed();
        LogDataUtils.logDataModelList("Data simulated from a Bayes net.", getDataModelList());
    }


    public BayesDataWrapper(BayesPmWrapper wrapper) {
        setSourceGraph(wrapper.getBayesPm().getDag());
        setKnownVariables(wrapper.getBayesPm().getVariables());
    }

    public BayesDataWrapper(BayesEstimatorWrapper wrapper,
                            BayesDataParams params) {
        BayesIm bayesIm = null;

        try {
            bayesIm = new MarshalledObject<>(wrapper.getEstimatedBayesIm()).get();
        } catch (Exception e) {
            throw new RuntimeException("Could not clone the SEM IM.");
        }

        this.bayesIm = bayesIm;

        try {
            params = new MarshalledObject<>(params).get();
        } catch (Exception e) {
            throw new RuntimeException("Could not clone the SemDataParams.");
        }

        DataModelList list = simulateData(wrapper.getEstimatedBayesIm(), params);
        setDataModel(list);
        setSourceGraph(wrapper.getEstimatedBayesIm().getDag());
        this.bayesIm = wrapper.getEstimatedBayesIm();
        setParams(params);
        setSeed();
        LogDataUtils.logDataModelList("Data simulated from a Bayes net.", getDataModelList());
    }

    public BayesDataWrapper(DirichletEstimatorWrapper wrapper,
                            BayesDataParams params) {
        BayesIm bayesIm = null;

        try {
            bayesIm = new MarshalledObject<>(wrapper.getEstimatedBayesIm()).get();
        } catch (Exception e) {
            throw new RuntimeException("Could not clone the SEM IM.");
        }

        this.bayesIm = bayesIm;

        try {
            params = new MarshalledObject<>(params).get();
        } catch (Exception e) {
            throw new RuntimeException("Could not clone the SemDataParams.");
        }

        DataModelList list = simulateData(wrapper.getEstimatedBayesIm(), params);
        setDataModel(list);
        setSourceGraph(wrapper.getEstimatedBayesIm().getDag());
        this.bayesIm = wrapper.getEstimatedBayesIm();
        setParams(params);
        setSeed();
        LogDataUtils.logDataModelList("Data simulated from a Bayes net.", getDataModelList());
    }

    public BayesDataWrapper(CptInvariantUpdaterWrapper wrapper,
                            BayesDataParams params) {
        BayesIm bayesIm = null;

        try {
            bayesIm = new MarshalledObject<>(wrapper.getBayesUpdater().getManipulatedBayesIm()).get();
        } catch (Exception e) {
            throw new RuntimeException("Could not clone the SEM IM.");
        }

        this.bayesIm = bayesIm;

        try {
            params = new MarshalledObject<>(params).get();
        } catch (Exception e) {
            throw new RuntimeException("Could not clone the SemDataParams.");
        }

        DataModelList list = simulateData(wrapper.getBayesUpdater().getManipulatedBayesIm(), params);
        setDataModel(list);
        setSourceGraph(wrapper.getBayesUpdater().getUpdatedBayesIm().getDag());
        this.bayesIm = wrapper.getBayesUpdater().getUpdatedBayesIm();
        setParams(params);
        setSeed();
        LogDataUtils.logDataModelList("Data simulated from a Bayes net.", getDataModelList());
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static DataWrapper serializableInstance() {
        return new BayesDataWrapper(BayesPmWrapper.serializableInstance());
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Params getParams() {
        return super.getParams();
    }

    public BayesIm getBayesIm() {
        return this.bayesIm;
    }


    private DataModelList simulateData(Simulator simulator, BayesDataParams params) {
        if (this.dataModelList != null) {
            return this.dataModelList;
        }

        DataModelList list = new DataModelList();
        int sampleSize = params.getSampleSize();
        boolean latentDataSaved = params.isLatentDataSaved();

        for (int i = 0; i < params.getNumDataSets(); i++) {
            DataSet dataSet = simulator.simulateData(sampleSize, seed, latentDataSaved);
            list.add(dataSet);
        }

        this.dataModelList = list;

        return list;
    }

    /**
     * Sets the data model.
     */
    public void setDataModel(DataModel dataModel) {
//        if (dataModel == null) {
//            dataModel = new ColtDataSet(0, new LinkedList<Node>());
//        }
//
//        if (dataModel instanceof DataModelList) {
//            this.dataModelList = (DataModelList) dataModel;
//        } else {
//            this.dataModelList = new DataModelList();
//            this.dataModelList.add(dataModel);
//        }

        // These are generated from seeds.
    }

    /**
     * @return the list of models.
     */
    public DataModelList getDataModelList() {
        return simulateData(bayesIm, params);
//        return this.dataModelList;
    }

    public void setDataModelList(DataModelList dataModelList) {
//        this.dataModelList = dataModelList;
    }

    public void setParams(Params params) {
        this.params = (BayesDataParams) params;
    }

    private void setSeed() {
        this.seed = RandomUtil.getInstance().getSeed();
    }

    @Override
    public Map<String, String> getParamSettings() {
        Map<String, String> paramSettings = new HashMap<>();

        if (dataModelList == null) {
            System.out.println();
        }

        if (dataModelList.size() > 1) {
            paramSettings.put("# Datasets", Integer.toString(dataModelList.size()));
        } else {
            DataModel dataModel = dataModelList.get(0);

            if (!paramSettings.containsKey("# Nodes")) {
                paramSettings.put("# Vars", Integer.toString(((DataSet) dataModel).getNumColumns()));
            }
            paramSettings.put("N", Integer.toString(((DataSet) dataModel).getNumRows()));
        }

        return paramSettings;
    }
}





