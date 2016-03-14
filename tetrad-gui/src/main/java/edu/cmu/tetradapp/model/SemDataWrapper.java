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

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.Simulator;
import edu.cmu.tetrad.session.SessionModel;
import edu.cmu.tetrad.session.SimulationParamsSource;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.rmi.MarshalledObject;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Wraps a data model so that a random sample will automatically be drawn on
 * construction from a SemIm.
 *
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 */
public class SemDataWrapper extends DataWrapper implements SessionModel,
        SimulationParamsSource {
    static final long serialVersionUID = 23L;
    private SemDataParams params;
    private Simulator semIm = null;
    //    private DataModelList dataModelList;
    private long seed;
    private transient DataModelList dataModelList;
    private LinkedHashMap<String, String> allParamSettings;


    //==============================CONSTRUCTORS=============================//

    public SemDataWrapper(SemImWrapper wrapper, SemDataParams params) {
        SemIm semIm = null;

        try {
            semIm = new MarshalledObject<>(wrapper.getSemIm()).get();
        } catch (Exception e) {
            throw new RuntimeException("Could not clone the SEM IM.");
        }

        this.semIm = semIm;

        try {
            params = new MarshalledObject<>(params).get();
        } catch (Exception e) {
            throw new RuntimeException("Could not clone the SemDataParams.");
        }

        setParams(params);

        setSeed();

//        DataModelList dataModelList = simulateData((SemDataParams) getParams(), semIm);
//        setDataModel(dataModelList);
        setSourceGraph(semIm.getSemPm().getGraph());
        LogDataUtils.logDataModelList("Data simulated from a linear structural equation model.", getDataModelList());
    }

    public SemDataWrapper(SemEstimatorWrapper wrapper, SemDataParams params) {
        SemIm semIm = null;
        try {
            semIm = new MarshalledObject<>(wrapper.getEstimatedSemIm()).get();
        } catch (Exception e) {
            throw new RuntimeException("Could not clone the SEM IM.");
        }

        this.semIm = semIm;

        try {
            params = new MarshalledObject<>(params).get();
        } catch (Exception e) {
            throw new RuntimeException("Could not clone the SemDataParams.");
        }

        setParams(params);

        setSeed();

//        DataModelList dataModelList = simulateData((SemDataParams) getParams(), semIm);
//        setDataModel(dataModelList);
        setSourceGraph(wrapper.getSemEstimator().getEstimatedSem().getSemPm().getGraph());
        setParams(params);
        LogDataUtils.logDataModelList("Data simulated from a linear structural equation model.", getDataModelList());
    }

    public SemDataWrapper(SemUpdaterWrapper wrapper, SemDataParams params) {
        SemIm semIm = null;
        try {
            semIm = new MarshalledObject<>(wrapper.getSemUpdater().getManipulatedSemIm()).get();
        } catch (Exception e) {
            throw new RuntimeException("Could not clone the SEM IM.");
        }

        this.semIm = semIm;

        try {
            params = new MarshalledObject<>(params).get();
        } catch (Exception e) {
            throw new RuntimeException("Could not clone the SemDataParams.");
        }

        setParams(params);

        setSeed();

        setSourceGraph(wrapper.getSemUpdater().getUpdatedSemIm().getSemPm().getGraph());
        LogDataUtils.logDataModelList("Data simulated from a linear structural equation model.", getDataModelList());
    }

    public SemDataWrapper(StandardizedSemImWrapper wrapper, SemDataParams params) {
        Simulator semIm = null;
        try {
            semIm = new MarshalledObject<>(wrapper.getStandardizedSemIm()).get();
        } catch (Exception e) {
            throw new RuntimeException("Could not clone the SEM IM.");
        }

        this.semIm = semIm;

        try {
            params = new MarshalledObject<>(params).get();
        } catch (Exception e) {
            throw new RuntimeException("Could not clone the SemDataParams.");
        }

        setParams(params);

        setSeed();

        setSourceGraph(wrapper.getStandardizedSemIm().getSemPm().getGraph());
        setParams(params);
        LogDataUtils.logDataModelList("Data simulated from a linear structural equation model.", getDataModelList());
    }

    private void setSeed() {
        this.seed = RandomUtil.getInstance().getSeed();
    }

    private DataModelList simulateData(Simulator simulator) {
        if (this.dataModelList != null) {
            return this.dataModelList;
        }

        DataModelList dataModelList = new DataModelList();
        int sampleSize = params.getSampleSize();
        boolean latentDataSaved = params.isLatentDataSaved();

        for (int i = 0; i < params.getNumDataSets(); i++) {
            DataSet dataSet = simulator.simulateData(sampleSize, seed, latentDataSaved);
            dataSet.setName("data" + (i + 1));
            dataModelList.add(dataSet);
        }

        this.dataModelList = dataModelList;
        return dataModelList;
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

    public Simulator getSemIm() {
        return this.semIm;
    }

    /**
     * @return the list of models.
     */
    public DataModelList getDataModelList() {
        return simulateData(semIm);
//        return this.dataModelList;
    }

    public void setDataModelList(DataModelList dataModelList) {
//        this.dataModelList = dataModelList;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static DataWrapper serializableInstance() {
        return new SemDataWrapper(SemImWrapper.serializableInstance(),
                SemDataParams.serializableInstance());
    }

    public void setParams(Params params) {
        this.params = (SemDataParams) params;
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

            if (dataModel instanceof CovarianceMatrix) {
                if (!paramSettings.containsKey("# Nodes")) {
                    paramSettings.put("# Vars", Integer.toString(((CovarianceMatrix) dataModel).getDimension()));
                }
                paramSettings.put("N", Integer.toString(((CovarianceMatrix) dataModel).getSampleSize()));
            } else {
                if (!paramSettings.containsKey("# Nodes")) {
                    paramSettings.put("# Vars", Integer.toString(((DataSet) dataModel).getNumColumns()));
                }
                paramSettings.put("N", Integer.toString(((DataSet) dataModel).getNumRows()));
            }
        }

        return paramSettings;
    }

    @Override
    public void setAllParamSettings(Map<String, String> paramSettings) {
        this.allParamSettings = new LinkedHashMap<>(paramSettings);
    }
}





