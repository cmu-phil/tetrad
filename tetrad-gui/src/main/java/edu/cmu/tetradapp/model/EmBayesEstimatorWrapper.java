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
import edu.cmu.tetrad.bayes.EmBayesEstimator;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializableUtils;
import edu.cmu.tetradapp.session.SessionModel;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;

/**
 * Wraps a Bayes Pm for use in the Tetrad application.
 *
 * @author josephramsey
 * @author Frank Wimberly adapted for EM Bayes estimator and structural EM Bayes estimator
 * @version $Id: $Id
 */
public class EmBayesEstimatorWrapper implements SessionModel, GraphSource {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The name of the model.
     */
    private String name;

    /**
     * The data model.
     */
    private DataSet dataSet;

    /**
     * Contains the estimated BayesIm, or null if it hasn't been estimated yet.
     */
    private BayesIm estimateBayesIm;

    //============================CONSTRUCTORS==========================//

    /**
     * <p>Constructor for EmBayesEstimatorWrapper.</p>
     *
     * @param dataWrapper    a {@link edu.cmu.tetradapp.model.DataWrapper} object
     * @param bayesPmWrapper a {@link edu.cmu.tetradapp.model.BayesPmWrapper} object
     * @param params         a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public EmBayesEstimatorWrapper(DataWrapper dataWrapper,
                                   BayesPmWrapper bayesPmWrapper, Parameters params) {
        if (dataWrapper == null) {
            throw new NullPointerException();
        }

        if (bayesPmWrapper == null) {
            throw new NullPointerException();
        }

        if (params == null) {
            throw new NullPointerException();
        }

        DataSet dataSet =
                (DataSet) dataWrapper.getSelectedDataModel();
        BayesPm bayesPm = bayesPmWrapper.getBayesPm();

        EmBayesEstimator estimator = new EmBayesEstimator(bayesPm, dataSet);
        this.dataSet = estimator.getMixedDataSet();

        try {
            estimator.maximization(params.getDouble("tolerance", 0.0001));
            this.estimateBayesIm = estimator.getEstimatedIm();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            throw new RuntimeException(
                    "Please specify the search tolerance first.");
        }
        TetradLogger.getInstance().log("info", "EM-Estimated Bayes IM:");
        TetradLogger.getInstance().log("im", "" + this.estimateBayesIm);
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

    //================================PUBLIC METHODS======================//

    /**
     * <p>Getter for the field <code>estimateBayesIm</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.bayes.BayesIm} object
     */
    public BayesIm getEstimateBayesIm() {
        return this.estimateBayesIm;
    }

    private void estimate(DataSet dataSet, BayesPm bayesPm, double thresh) {
        try {
            EmBayesEstimator estimator = new EmBayesEstimator(bayesPm, dataSet);
            this.estimateBayesIm = estimator.maximization(thresh);
            this.dataSet = estimator.getMixedDataSet();
        } catch (ArrayIndexOutOfBoundsException e) {
            e.printStackTrace();
            throw new RuntimeException("Value assignments between Bayes PM " +
                    "and discrete data set do not match.");
        }
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
     * Adds semantic checks to the default deserialization method. This method must have the standard signature for a
     * readObject method, and the body of the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from version to version. A readObject method of
     * this form may be added to any class, even if Tetrad sessions were previously saved out using a version of the
     * class that didn't include it. (That's what the "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for
     * help.
     *
     * @param s a {@link java.io.ObjectInputStream} object
     * @throws IOException            If any.
     * @throws ClassNotFoundException If any.
     */
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        if (this.dataSet == null) {
            throw new NullPointerException();
        }
    }

    /**
     * <p>getGraph.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public Graph getGraph() {
        return this.estimateBayesIm.getBayesPm().getDag();
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

    //=============================== Private methods ==========================//

}





