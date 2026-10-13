///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.bayes.BayesIm;
import edu.cmu.tetrad.bayes.BayesPm;
import edu.cmu.tetrad.bayes.EmBayesEstimator;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializableUtils;
import edu.cmu.tetradapp.session.SessionModel;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

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

    /**
     * The data sets of the parent data box, to choose among in the editor; null in sessions saved before this field
     * existed.
     */
    private List<DataSet> dataSets;

    /**
     * The index in dataSets of the data set the current estimate is from.
     */
    private int dataIndex = 0;

    /**
     * The Bayes PM and tolerance the estimate was made with, kept for re-estimating on another data set.
     */
    private BayesPm bayesPm;
    private double tolerance = 0.0001;

    //============================CONSTRUCTORS==========================//

    /**
     * Initializes an instance of the EmBayesEstimatorWrapper class.
     *
     * @param simulation     The simulation used for estimation.
     * @param bayesPmWrapper The BayesPmWrapper used for estimation.
     * @param params         The parameters for the estimator.
     */
    public EmBayesEstimatorWrapper(Simulation simulation, BayesPmWrapper bayesPmWrapper, Parameters params) {
        this(new DataWrapper(simulation, params), bayesPmWrapper, params);
    }

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

        this.bayesPm = bayesPm;
        this.tolerance = params.getDouble("tolerance", 0.0001);
        this.dataSets = tabularDataSets(dataWrapper);
        this.dataIndex = Math.max(0, this.dataSets.indexOf(dataSet));

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
        TetradLogger.getInstance().log("EM-Estimated Bayes IM:");
        TetradLogger.getInstance().log("" + this.estimateBayesIm);
    }

    /**
     * The tabular data sets of a data box, in order.
     */
    private static List<DataSet> tabularDataSets(DataWrapper dataWrapper) {
        List<DataSet> dataSets = new ArrayList<>();

        for (DataModel model : dataWrapper.getDataModelList()) {
            if (model instanceof DataSet dataSet) dataSets.add(dataSet);
        }

        return dataSets;
    }

    /**
     * The data sets of the parent data box, to choose among in the editor.
     *
     * @return the data sets; empty if unknown (a session saved before the choice existed)
     */
    public List<DataSet> getDataSets() {
        return this.dataSets == null ? new ArrayList<>() : this.dataSets;
    }

    /**
     * The index, in getDataSets(), of the data set the current estimate is from.
     *
     * @return the index
     */
    public int getDataIndex() {
        return this.dataIndex;
    }

    /**
     * Re-estimates by EM on another of the parent's data sets, replacing the current estimate. If the estimation
     * fails, the current estimate and index are left as they were and the exception is passed on.
     *
     * @param index the index, in getDataSets(), of the data set to estimate on
     */
    public void setDataIndex(int index) {
        if (this.bayesPm == null || index < 0 || index >= getDataSets().size()) {
            throw new IllegalArgumentException("No data set at index " + index + ".");
        }

        EmBayesEstimator estimator = new EmBayesEstimator(this.bayesPm, this.dataSets.get(index));
        DataSet mixed = estimator.getMixedDataSet();
        estimator.maximization(this.tolerance);
        this.estimateBayesIm = estimator.getEstimatedIm();
        this.dataSet = mixed;
        this.dataIndex = index;
        TetradLogger.getInstance().log("EM-Estimated Bayes IM:");
        TetradLogger.getInstance().log("" + this.estimateBayesIm);
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
     * Writes the object to the specified ObjectOutputStream.
     *
     * @param out The ObjectOutputStream to write the object to.
     * @throws IOException If an I/O error occurs.
     */
    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        try {
            out.defaultWriteObject();
        } catch (IOException e) {
            TetradLogger.getInstance().log("Failed to serialize object: " + getClass().getCanonicalName()
                                           + ", " + e.getMessage());
            throw e;
        }
    }

    /**
     * Reads the object from the specified ObjectInputStream. This method is used during deserialization
     * to restore the state of the object.
     *
     * @param in The ObjectInputStream to read the object from.
     * @throws IOException            If an I/O error occurs.
     * @throws ClassNotFoundException If the class of the serialized object cannot be found.
     */
    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
        } catch (IOException e) {
            TetradLogger.getInstance().log("Failed to deserialize object: " + getClass().getCanonicalName()
                                           + ", " + e.getMessage());
            throw e;
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






