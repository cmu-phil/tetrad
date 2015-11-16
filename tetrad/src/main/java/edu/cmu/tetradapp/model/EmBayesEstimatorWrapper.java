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
import edu.cmu.tetrad.bayes.BayesPm;
import edu.cmu.tetrad.bayes.EmBayesEstimator;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.session.SessionModel;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.io.IOException;
import java.io.ObjectInputStream;

/**
 * Wraps a Bayes Pm for use in the Tetrad application.
 *
 * @author Joseph Ramsey
 * @author Frank Wimberly adapted for EM Bayes estimator and structural EM Bayes
 *         estimator
 */
public class EmBayesEstimatorWrapper implements SessionModel, GraphSource {
    static final long serialVersionUID = 23L;

    /**
     * @serial Can be null.
     */
    private String name;

    /**
     * @serial
     * @deprecated
     */
    private BayesPm bayesPm;

    /**
     * @serial Cannot be null.
     */
    private DataSet dataSet;

    /**
     * Contains the estimated BayesIm, or null if it hasn't been estimated yet.
     *
     * @serial Can be null.
     */
    private BayesIm estimateBayesIm;

    //============================CONSTRUCTORS==========================//

//    public EmBayesEstimatorWrapper(DataWrapper dataWrapper,
//            BayesPmWrapper bayesPmWrapper) {
//        if (dataWrapper == null) {
//            throw new NullPointerException(
//                    "BayesDataWrapper must not be null.");
//        }
//
//        if (bayesPmWrapper == null) {
//            throw new NullPointerException("BayesPmWrapper must not be null");
//        }
//
//        this.dataSet = (RectangularDataSet) dataWrapper.getSelectedDataModel();
//        BayesPm bayesPm = bayesPmWrapper.getBayesPm();
//
//        estimate(this.dataSet, bayesPm, 0.001);
//
//        LogUtils.getInstance().finer("Estimated Bayes IM:");
//        LogUtils.getInstance().finer("" + estimateBayesIm);
//    }

    public EmBayesEstimatorWrapper(DataWrapper dataWrapper,
            BayesPmWrapper bayesPmWrapper, EmBayesEstimatorParams params) {
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
            estimator.maximization(params.getTolerance());
            this.estimateBayesIm = estimator.getEstimatedIm();
        }
        catch (IllegalArgumentException e) {
            e.printStackTrace();
            throw new RuntimeException(
                    "Please specify the search tolerance first.");
        }
        TetradLogger.getInstance().log("info", "EM-Estimated Bayes IM:");
        TetradLogger.getInstance().log("im", "" + estimateBayesIm);
    }

    public EmBayesEstimatorWrapper(DataWrapper dataWrapper,
            BayesImWrapper bayesImWrapper, EmBayesEstimatorParams params) {
        TetradLogger.getInstance().log("info", "EM-Estimated Bayes IM:");

        if (dataWrapper == null) {
            throw new NullPointerException();
        }

        if (bayesImWrapper == null) {
            throw new NullPointerException();
        }

        if (params == null) {
            throw new NullPointerException();
        }

        DataSet dataSet =
                (DataSet) dataWrapper.getSelectedDataModel();
        BayesPm bayesPm = bayesImWrapper.getBayesIm().getBayesPm();

        EmBayesEstimator estimator = new EmBayesEstimator(bayesPm, dataSet);
        this.dataSet = estimator.getMixedDataSet();

        System.out.println("B" + dataSet.getVariables());

        try {
            estimator.maximization(params.getTolerance());
            this.estimateBayesIm = estimator.getEstimatedIm();
        }
        catch (IllegalArgumentException e) {
            e.printStackTrace();

            throw new RuntimeException(
                    "Please specify the search tolerance first.");
        }

        TetradLogger.getInstance().log("im", "" + estimateBayesIm);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see edu.cmu.TestSerialization
     * @see TetradSerializableUtils
     */
    public static EmBayesEstimatorWrapper serializableInstance() {
        return new EmBayesEstimatorWrapper(DataWrapper.serializableInstance(),
                BayesPmWrapper.serializableInstance(), new EmBayesEstimatorParams());
    }

    //================================PUBLIC METHODS======================//

    public BayesIm getEstimateBayesIm() {
        return this.estimateBayesIm;
    }

    private void estimate(DataSet dataSet, BayesPm bayesPm, double thresh) {
        try {
            EmBayesEstimator estimator = new EmBayesEstimator(bayesPm, dataSet);
            this.estimateBayesIm = estimator.maximization(thresh);
            this.dataSet = estimator.getMixedDataSet();
        }
        catch (ArrayIndexOutOfBoundsException e) {
            e.printStackTrace();
            throw new RuntimeException("Value assignments between Bayes PM " +
                    "and discrete data set do not match.");
        }
    }

    public DataSet getDataSet() {
        return this.dataSet;
    }

    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        if (dataSet == null) {
            throw new NullPointerException();
        }
    }

    public Graph getGraph() {
        return estimateBayesIm.getBayesPm().getDag();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    //=============================== Private methods ==========================//

}





