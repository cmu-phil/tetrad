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

import edu.cmu.tetrad.bayes.BayesIm;
import edu.cmu.tetrad.bayes.BayesPm;
import edu.cmu.tetrad.bayes.MlBayesEstimator;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataModelList;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializableUtils;
import edu.cmu.tetradapp.session.SessionModel;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * Wraps a Bayes Pm for use in the Tetrad application.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class BayesEstimatorWrapper implements SessionModel {

    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The data wrapper.
     */
    private final DataWrapper dataWrapper;

    /**
     * ' The estimated Bayes IM.
     */
    private final List<BayesIm> bayesIms = new ArrayList<>();

    /**
     * The name of the Bayes Pm.
     */
    private String name;

    /**
     * The estimated Bayes IM.
     */
    private BayesIm bayesIm;

    /**
     * The data set.
     */
    private DataSet dataSet;

    /**
     * The number of models.
     */
    private int numModels;

    /**
     * The model index.
     */
    private int modelIndex;

    //=================================CONSTRUCTORS========================//

    /**
     * <p>Constructor for BayesEstimatorWrapper.</p>
     *
     * @param dataWrapper    a {@link edu.cmu.tetradapp.model.DataWrapper} object
     * @param bayesPmWrapper a {@link edu.cmu.tetradapp.model.BayesPmWrapper} object
     */
    public BayesEstimatorWrapper(DataWrapper dataWrapper,
                                 BayesPmWrapper bayesPmWrapper) {

        if (dataWrapper == null) {
            throw new NullPointerException(
                    "BayesDataWrapper must not be null.");
        }

        this.dataWrapper = dataWrapper;

        if (bayesPmWrapper == null) {
            throw new NullPointerException("BayesPmWrapper must not be null");
        }

        DataModelList dataModel = dataWrapper.getDataModelList();

        if (dataModel != null) {
            for (int i = 0; i < dataWrapper.getDataModelList().size(); i++) {
                DataModel model = dataWrapper.getDataModelList().get(i);
                DataSet dataSet = (DataSet) model;
                bayesPmWrapper.setModelIndex(i);
                BayesPm bayesPm = bayesPmWrapper.getBayesPm();

                estimate(dataSet, bayesPm);
                this.bayesIms.add(this.bayesIm);
            }

            this.bayesIm = this.bayesIms.get(0);
            log(this.bayesIm);

        } else {
            throw new IllegalArgumentException("Data must consist of discrete data sets.");
        }

        this.name = bayesPmWrapper.getName();
        this.numModels = this.bayesIms.size();
        this.modelIndex = 0;
        this.bayesIm = this.bayesIms.get(this.modelIndex);
        DataModel model = dataModel.get(this.modelIndex);
        this.dataSet = (DataSet) model;
    }

    /**
     * <p>Constructor for BayesEstimatorWrapper.</p>
     *
     * @param dataWrapper    a {@link edu.cmu.tetradapp.model.DataWrapper} object
     * @param bayesImWrapper a {@link edu.cmu.tetradapp.model.BayesImWrapper} object
     */
    public BayesEstimatorWrapper(DataWrapper dataWrapper,
                                 BayesImWrapper bayesImWrapper) {
        this(dataWrapper, new BayesPmWrapper(bayesImWrapper));
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

    //==============================PUBLIC METHODS========================//

    /**
     * <p>getEstimatedBayesIm.</p>
     *
     * @return a {@link edu.cmu.tetrad.bayes.BayesIm} object
     */
    public BayesIm getEstimatedBayesIm() {
        return this.bayesIm;
    }

    /**
     * <p>Setter for the field <code>bayesIm</code>.</p>
     *
     * @param bayesIm a {@link edu.cmu.tetrad.bayes.BayesIm} object
     */
    public void setBayesIm(BayesIm bayesIm) {
        this.bayesIms.clear();
        this.bayesIms.add(bayesIm);
    }

    /**
     * <p>Getter for the field <code>dataSet</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public DataSet getDataSet() {
        return this.dataSet;
    }

    /**
     * <p>getGraph.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public Graph getGraph() {
        return this.bayesIm.getBayesPm().getDag();
    }

    /**
     * <p>Getter for the field <code>name</code>.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getName() {
        return this.name;
    }

    /**
     * {@inheritDoc}
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the number of models.
     *
     * @return the number of models.
     */
    public int getNumModels() {
        return this.numModels;
    }

    /**
     * Sets the number of models.
     *
     * @param numModels the number of models to be set.
     */
    public void setNumModels(int numModels) {
        this.numModels = numModels;
    }

    /**
     * Retrieves the model index.
     *
     * @return the model index
     */
    public int getModelIndex() {
        return this.modelIndex;
    }

    /**
     * Sets the model index.
     *
     * @param modelIndex the index of the model to be set.
     */
    public void setModelIndex(int modelIndex) {
        this.modelIndex = modelIndex;
        this.bayesIm = this.bayesIms.get(modelIndex);

        DataModelList dataModel = this.dataWrapper.getDataModelList();

        this.dataSet = (DataSet) dataModel.get(modelIndex);

    }

    //======================== Private Methods ======================//

    /**
     * Adds semantic checks to the default deserialization method. This method must have the standard signature for a
     * readObject method, and the body of the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from version to version. A readObject method of
     * this form may be added to any class, even if Tetrad sessions were previously saved out using a version of the
     * class that didn't include it. (That's what the "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for
     * help.)
     *
     * @param s a {@link java.io.ObjectInputStream} object
     * @throws IOException            If any.
     * @throws ClassNotFoundException If any.
     */
    @Serial
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        if (this.bayesIm == null) {
            throw new NullPointerException();
        }
    }

    private void log(BayesIm im) {
        TetradLogger.getInstance().forceLogMessage("ML estimated Bayes IM.");
        String message = im.toString();
        TetradLogger.getInstance().forceLogMessage(message);
    }

    private void estimate(DataSet dataSet, BayesPm bayesPm) {
        Graph graph = bayesPm.getDag();

        for (Node node : graph.getNodes()) {
            if (node.getNodeType() == NodeType.LATENT) {
                throw new IllegalArgumentException("Estimation of Bayes IMs "
                        + "with latents is not supported.");
            }
        }

        if (DataUtils.containsMissingValue(dataSet)) {
            throw new IllegalArgumentException("Please remove or impute missing values.");
        }

        try {
            MlBayesEstimator estimator = new MlBayesEstimator();
            this.bayesIm = estimator.estimate(bayesPm, dataSet);
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new RuntimeException("Value assignments between Bayes PM "
                    + "and discrete data set do not match.");
        }
    }

}
