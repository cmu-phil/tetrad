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

import edu.cmu.tetrad.data.DataModelList;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.LogDataUtils;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.session.SessionModel;
import edu.cmu.tetrad.util.TetradSerializableUtils;

/**
 * Wraps a data model so that a random sample will automatically be drawn on
 * construction from a SemIm.
 *
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 */
public class SemDataWrapper extends DataWrapper implements SessionModel {
    static final long serialVersionUID = 23L;
    private SemIm semIm = null;

    //==============================CONSTRUCTORS=============================//

    public SemDataWrapper(SemImWrapper wrapper, SemDataParams params) {
        int sampleSize = params.getSampleSize();
        boolean latentDataSaved = params.isIncludeLatents();
        SemIm semIm = wrapper.getSemIm();
        semIm.setSimulatedPositiveDataOnly(params.isPositiveDataOnly());

        DataModelList list = new DataModelList();

        for (int i = 0; i < params.getNumDataSets(); i++) {
            DataSet dataSet = semIm.simulateData(sampleSize, latentDataSaved);
            list.add(dataSet);
        }

        this.setDataModel(list);
        this.setSourceGraph(semIm.getSemPm().getGraph());
        setParams(params);
        this.semIm = semIm;
        LogDataUtils.logDataModelList("Data simulated from a linear structural equation model.", getDataModelList());
    }

    public SemDataWrapper(SemImWrapper wrapper, DataWrapper initialValues, SemDataParams params) {
//        int sampleSize = params.getSampleSize();
        boolean latentDataSaved = params.isIncludeLatents();
        SemIm semIm = wrapper.getSemIm();
        semIm.setSimulatedPositiveDataOnly(params.isPositiveDataOnly());

        DataModelList list = new DataModelList();

        for (int i = 0; i < params.getNumDataSets(); i++) {
            DataSet _dataSet = (DataSet) initialValues.getSelectedDataModel();
            DataSet dataSet = semIm.simulateDataRecursive(_dataSet, latentDataSaved);
            list.add(dataSet);
        }

        this.setDataModel(list);
        this.setSourceGraph(semIm.getSemPm().getGraph());
        setParams(params);
        this.semIm = semIm;
        LogDataUtils.logDataModelList("Data simulated from a linear structural equation model.", getDataModelList());
    }

    public SemDataWrapper(SemEstimatorWrapper wrapper, SemDataParams params) {
        int sampleSize = params.getSampleSize();
        boolean latentDataSaved = params.isIncludeLatents();

        DataModelList list = new DataModelList();

        for (int i = 0; i < params.getNumDataSets(); i++) {
            DataSet dataSet = wrapper.getSemEstimator().getEstimatedSem().simulateData(sampleSize, latentDataSaved);
            list.add(dataSet);
        }

        setDataModel(list);
        setSourceGraph(wrapper.getSemEstimator().getEstimatedSem().getSemPm().getGraph());
        setParams(params);
        this.semIm = wrapper.getEstimatedSemIm();
        LogDataUtils.logDataModelList("Data simulated from a linear structural equation model.", getDataModelList());
    }

    public SemDataWrapper(SemUpdaterWrapper wrapper, SemDataParams params) {
        int sampleSize = params.getSampleSize();
        boolean latentDataSaved = params.isIncludeLatents();

        DataModelList list = new DataModelList();

        for (int i = 0; i < params.getNumDataSets(); i++) {
            DataSet dataSet = wrapper.getSemUpdater().getUpdatedSemIm().simulateData(sampleSize, latentDataSaved);
            list.add(dataSet);
        }

        setDataModel(list);
        setSourceGraph(wrapper.getSemUpdater().getUpdatedSemIm().getSemPm().getGraph());
        setParams(params);
        this.semIm = wrapper.getSemUpdater().getUpdatedSemIm();
        LogDataUtils.logDataModelList("Data simulated from a linear structural equation model.", getDataModelList());
    }

    public SemDataWrapper(StandardizedSemImWrapper wrapper, SemDataParams params) {
        int sampleSize = params.getSampleSize();
        boolean latentDataSaved = params.isIncludeLatents();

        DataModelList list = new DataModelList();

        for (int i = 0; i < params.getNumDataSets(); i++) {
            DataSet columnDataModel =
                    wrapper.getStandardizedSemIm().simulateData(sampleSize, latentDataSaved);
            list.add(columnDataModel);
        }

        setDataModel(list);
        setSourceGraph(wrapper.getStandardizedSemIm().getSemPm().getGraph());
        setParams(params);
        LogDataUtils.logDataModelList("Data simulated from a linear structural equation model.", getDataModelList());
    }

    public SemIm getSemIm() {
        return this.semIm;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see edu.cmu.TestSerialization
     * @see TetradSerializableUtils
     */
    public static DataWrapper serializableInstance() {
        return new SemDataWrapper(SemImWrapper.serializableInstance(),
                SemDataParams.serializableInstance());
    }
}





