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
import edu.cmu.tetrad.data.DataModelList;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.LogDataUtils;
import edu.cmu.tetrad.session.SessionModel;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.TetradSerializableUtils;

/**
 * Wraps a data model so that a random sample will automatically be drawn on
 * construction from a BayesIm.
 *
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 */
public class BayesDataWrapper extends DataWrapper implements SessionModel {
    static final long serialVersionUID = 23L;

    /**
     * @serial Can be null.
     */
    private String name;

    /**
     * The Bayes Im being sampled from.
     */
    private BayesIm bayesIm = null;

    //============================CONSTRUCTORS=========================//

    public BayesDataWrapper(BayesImWrapper wrapper, BayesDataParams params) {
        int sampleSize = params.getSampleSize();
        boolean latentDataSaved = params.isLatentDataSaved();

        DataModelList list = new DataModelList();

        for (int i = 0; i < params.getNumDataSets(); i++) {
            DataSet dataSet = wrapper.getBayesIm().simulateData(sampleSize, latentDataSaved);
            list.add(dataSet);
        }

//        DataSet dataSet = wrapper.getBayesIm().simulateData(sampleSize, latentDataSaved);
        setDataModel(list);
        setSourceGraph(wrapper.getBayesIm().getDag());
        this.bayesIm = wrapper.getBayesIm();
        setParams(params);
        LogDataUtils.logDataModelList("Data simulated from a Bayes net.", getDataModelList());
    }

    public BayesDataWrapper(BayesPmWrapper wrapper) {
        setSourceGraph(wrapper.getBayesPm().getDag());
        setKnownVariables(wrapper.getBayesPm().getVariables());
        LogDataUtils.logDataModelList("Data simulated from a Bayes net.", getDataModelList());
    }

    public BayesDataWrapper(BayesEstimatorWrapper wrapper,
                            BayesDataParams params) {
        int sampleSize = params.getSampleSize();
        boolean latentDataSaved = params.isLatentDataSaved();
        setDataModel(wrapper.getEstimatedBayesIm().simulateData(sampleSize, latentDataSaved));
        setSourceGraph(wrapper.getEstimatedBayesIm().getDag());
        this.bayesIm = wrapper.getEstimatedBayesIm();
        setParams(params);
        LogDataUtils.logDataModelList("Data simulated from a Bayes net.", getDataModelList());
    }

    public BayesDataWrapper(DirichletEstimatorWrapper wrapper,
                            BayesDataParams params) {
        int sampleSize = params.getSampleSize();
        boolean latentDataSaved = params.isLatentDataSaved();
        setDataModel(wrapper.getEstimatedBayesIm().simulateData(sampleSize, latentDataSaved));
        setSourceGraph(wrapper.getEstimatedBayesIm().getDag());
        this.bayesIm = wrapper.getEstimatedBayesIm();
        setParams(params);
        LogDataUtils.logDataModelList("Data simulated from a Bayes net.", getDataModelList());
    }

    public BayesDataWrapper(CptInvariantUpdaterWrapper wrapper,
                            BayesDataParams params) {
        int sampleSize = params.getSampleSize();
        boolean latentDataSaved = params.isLatentDataSaved();
        setDataModel(wrapper.getBayesUpdater().getUpdatedBayesIm()
                .simulateData(sampleSize, latentDataSaved));
        setSourceGraph(wrapper.getBayesUpdater().getUpdatedBayesIm().getDag());
        this.bayesIm = wrapper.getBayesUpdater().getUpdatedBayesIm();
        setParams(params);
        LogDataUtils.logDataModelList("Data simulated from a Bayes net.", getDataModelList());
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see edu.cmu.TestSerialization
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

}





