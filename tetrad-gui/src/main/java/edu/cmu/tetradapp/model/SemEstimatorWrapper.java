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

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Dag;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.sem.*;
import edu.cmu.tetrad.util.*;
import edu.cmu.tetradapp.session.SessionModel;

import javax.swing.*;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.util.LinkedList;
import java.util.List;

/**
 * Wraps a SemEstimator for use in the Tetrad application.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class SemEstimatorWrapper implements SessionModel {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The parameters for the estimator.
     */
    private final Parameters params;

    /**
     * The SEM PM for the estimator.
     */
    private final SemPm semPm;

    /**
     * The name of the estimator.
     */
    private String name;
    /**
     * The estimator itself.
     */
    private SemEstimator semEstimator;

    /**
     * The data models of the parent data box, if this was built from one, for choosing among in the editor; null
     * otherwise, and in sessions saved before this field existed.
     */
    private DataModelList dataModels;

    /**
     * The index in dataModels of the data model the current estimate is from.
     */
    private int dataIndex = 0;

    //==============================CONSTRUCTORS==========================//

    /**
     * Private constructor for serialization only. Problem is, for the real constructors, I'd like to call the degrees
     * of freedom check, which pops up a dialog. This is irritating when running unit tests. jdramsey 8/29/07
     *
     * @param dataModel a {@link edu.cmu.tetrad.data.DataModel} object
     * @param semPm     a {@link edu.cmu.tetrad.sem.SemPm} object
     * @param params    a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public SemEstimatorWrapper(DataModel dataModel, SemPm semPm, Parameters params) {
        this.params = params;
        this.semPm = semPm;

        SemEstimator estimator = estimateOn(dataModel);

        if (!degreesOfFreedomCheck(semPm)) {
            throw new IllegalArgumentException("Cannot proceed.");
        }

        this.semEstimator = estimator;
    }

    /**
     * Estimates the SEM PM on the given data with the current optimizer, restarts and score settings.
     */
    private SemEstimator estimateOn(DataModel dataModel) {
        SemEstimator estimator;

        if (dataModel instanceof DataSet dataSet) {
            estimator = new SemEstimator(dataSet, this.semPm, getOptimizer());
            estimator.setNumRestarts(getParams().getInt("numRestarts", 1));
            estimator.setScoreType((ScoreType) getParams().get("scoreType", ScoreType.Fgls));
        } else if (dataModel instanceof ICovarianceMatrix) {
            ICovarianceMatrix covMatrix = new CovarianceMatrix((ICovarianceMatrix) dataModel);
            estimator = new SemEstimator(covMatrix, this.semPm, getOptimizer());
            estimator.setNumRestarts(getParams().getInt("numRestarts", 1));
            estimator.setScoreType((ScoreType) getParams().get("scoreType", ScoreType.Fml));
        } else {
            throw new IllegalArgumentException("Data must consist of continuous data sets or covariance matrices.");
        }

        estimator.estimate();
        return estimator;
    }

    /**
     * The data models of the parent data box, to choose among.
     *
     * @return the data models, or null if this estimator was not built from a data box
     */
    public DataModelList getDataModels() {
        return this.dataModels;
    }

    /**
     * The index, in getDataModels(), of the data model the current estimate is from.
     *
     * @return the index
     */
    public int getDataIndex() {
        return this.dataIndex;
    }

    /**
     * Re-estimates on another of the parent's data models, replacing the current estimate. If the estimation
     * fails, the current estimate and index are left as they were and the exception is passed on.
     *
     * @param index the index, in getDataModels(), of the data model to estimate on
     */
    public void setDataIndex(int index) {
        if (this.dataModels == null || index < 0 || index >= this.dataModels.size()) {
            throw new IllegalArgumentException("No data model at index " + index + ".");
        }

        this.semEstimator = estimateOn(this.dataModels.get(index));
        this.dataIndex = index;
        log();
    }

    /**
     * <p>Constructor for SemEstimatorWrapper.</p>
     *
     * @param dataWrapper  a {@link edu.cmu.tetradapp.model.DataWrapper} object
     * @param semPmWrapper a {@link edu.cmu.tetradapp.model.SemPmWrapper} object
     * @param params       a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public SemEstimatorWrapper(DataWrapper dataWrapper, SemPmWrapper semPmWrapper, Parameters params) {
        this(dataWrapper.getSelectedDataModel(), semPmWrapper.getSemPm(), params);
        this.dataModels = dataWrapper.getDataModelList();
        this.dataIndex = Math.max(0, this.dataModels.indexOf(dataWrapper.getSelectedDataModel()));
        log();
    }

    /**
     * Constructs a SemEstimatorWrapper object.
     *
     * @param simulation      a Simulation object
     * @param semPmWrapper    a SemPmWrapper object
     * @param parameters      a Parameters object
     */
    public SemEstimatorWrapper(Simulation simulation, SemPmWrapper semPmWrapper, Parameters parameters) {
        this(new DataWrapper(simulation, parameters), semPmWrapper, parameters);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetradapp.model.SemEstimatorWrapper} object
     * @see TetradSerializableUtils
     */
    public static SemEstimatorWrapper serializableInstance() {
        List<Node> variables = new LinkedList<>();
        ContinuousVariable x = new ContinuousVariable("X");
        variables.add(x);
        DataSet dataSet = new BoxDataSet(new VerticalDoubleDataBox(10, variables.size()), variables);

        for (int i = 0; i < dataSet.getNumRows(); i++) {
            for (int j = 0; j < dataSet.getNumColumns(); j++) {
                dataSet.setDouble(i, j, RandomUtil.getInstance().nextDouble());
            }
        }
        Dag dag = new Dag();
        dag.addNode(x);
        SemPm pm = new SemPm(dag);
        Parameters params1 = new Parameters();
        return new SemEstimatorWrapper(dataSet, pm, params1);
    }

    private static boolean containsCovarParam(SemPm semPm) {
        boolean containsCovarParam = false;
        List<Parameter> params = semPm.getParameters();

        for (Parameter param : params) {
            if (param.getType() == ParamType.COVAR) {
                containsCovarParam = true;
                break;
            }
        }
        return containsCovarParam;
    }

    private boolean degreesOfFreedomCheck(SemPm semPm) {
        if (semPm.getDof() < 1) {
            int ret = JOptionPane.showConfirmDialog(JOptionUtils.centeringComp(), "This model has non-positive degrees of freedom (DOF = " + semPm.getDof() + "). " + "\nEstimation will be uninformative. Are you sure you want to proceed?", "Please confirm", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

            return ret == JOptionPane.YES_OPTION;
        }

        return true;
    }

    //============================PUBLIC METHODS=========================//

    /**
     * <p>Getter for the field <code>semEstimator</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.sem.SemEstimator} object
     */
    public SemEstimator getSemEstimator() {
        return this.semEstimator;
    }

    /**
     * <p>Setter for the field <code>semEstimator</code>.</p>
     *
     * @param semEstimator a {@link edu.cmu.tetrad.sem.SemEstimator} object
     */
    public void setSemEstimator(SemEstimator semEstimator) {
        this.semEstimator = semEstimator;
    }

    /**
     * <p>getEstimatedSemIm.</p>
     *
     * @return a {@link edu.cmu.tetrad.sem.SemIm} object
     */
    public SemIm getEstimatedSemIm() {
        return this.semEstimator.getEstimatedSem();
    }

    /**
     * <p>getSemOptimizerType.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getSemOptimizerType() {
        return getParams().getString("semOptimizerType", "Regression");
    }

    /**
     * <p>setSemOptimizerType.</p>
     *
     * @param type a {@link java.lang.String} object
     */
    public void setSemOptimizerType(String type) {
        getParams().set("semOptimizerType", type);
    }

    /**
     * <p>getGraph.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public Graph getGraph() {
        return this.semEstimator.getEstimatedSem().getSemPm().getGraph();
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

    //=============================== Private methods =======================//
    private void log() {
        TetradLogger.getInstance().log("SEM Estimator:");
        String message3 = "" + getEstimatedSemIm();
        TetradLogger.getInstance().log(message3);
        String message2 = "ChiSq = " + getEstimatedSemIm().getChiSquare();
        TetradLogger.getInstance().log(message2);
        String message1 = "DOF = " + getEstimatedSemIm().getSemPm().getDof();
        TetradLogger.getInstance().log(message1);
        String message = "P = " + getEstimatedSemIm().getPValue();
        TetradLogger.getInstance().log(message);
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
     * <p>Getter for the field <code>params</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public Parameters getParams() {
        return this.params;
    }

    private SemOptimizer getOptimizer() {
        SemOptimizer optimizer;
        String type = getParams().getString("semOptimizerType", "Regression");

        if ("Regression".equals(type)) {
            SemOptimizer defaultOptimization = getDefaultOptimization();

            if (!(defaultOptimization instanceof SemOptimizerRegression)) {
                optimizer = defaultOptimization;
                type = getType(defaultOptimization);
                getParams().set("semOptimizerType", type);
            } else {
                optimizer = new SemOptimizerRegression();
            }
        } else if ("EM".equals(type)) {
            optimizer = new SemOptimizerEm();
        } else if ("Powell".equals(type)) {
            optimizer = new SemOptimizerPowell();
        } else if ("Random Search".equals(type)) {
            optimizer = new SemOptimizerScattershot();
        } else if ("RICF".equals(type)) {
            optimizer = new SemOptimizerRicf();
        } else {
            if (this.semPm != null) {
                optimizer = getDefaultOptimization();

                String _type = getType(optimizer);

                if (_type != null) {
                    getParams().set("semOptimizerType", _type);
                }
            } else {
                optimizer = null;
            }
        }

        return optimizer;
    }

    private String getType(SemOptimizer optimizer) {
        String _type = null;

        if (optimizer instanceof SemOptimizerRegression) {
            _type = "Regression";
        } else if (optimizer instanceof SemOptimizerEm) {
            _type = "EM";
        } else if (optimizer instanceof SemOptimizerPowell) {
            _type = "Powell";
        } else if (optimizer instanceof SemOptimizerScattershot) {
            _type = "Random Search";
        } else if (optimizer instanceof SemOptimizerRicf) {
            _type = "RICF";
        }

        return _type;
    }

    private boolean containsFixedParam(SemPm semPm) {
        return new SemIm(semPm).getNumFixedParams() > 0;
    }

    /**
     * <p>getScoreType.</p>
     *
     * @return a {@link edu.cmu.tetrad.sem.ScoreType} object
     */
    public ScoreType getScoreType() {
        return (ScoreType) this.params.get("scoreType", ScoreType.SemBic);
    }

    /**
     * <p>setScoreType.</p>
     *
     * @param scoreType a {@link edu.cmu.tetrad.sem.ScoreType} object
     */
    public void setScoreType(ScoreType scoreType) {
        this.params.set("scoreType", scoreType);
    }

    /**
     * <p>getNumRestarts.</p>
     *
     * @return a int
     */
    public int getNumRestarts() {
        return getParams().getInt("numRestarts", 1);
    }

    /**
     * <p>setNumRestarts.</p>
     *
     * @param numRestarts a int
     */
    public void setNumRestarts(int numRestarts) {
        getParams().set("numRestarts", numRestarts);
    }

    private SemOptimizer getDefaultOptimization() {
        if (this.semPm == null) {
            throw new NullPointerException("Sorry, I didn't see a SEM PM as parent to the estimator; perhaps the parents are wrong.");
        }

        boolean containsLatent = false;

        for (Node node : this.semPm.getGraph().getNodes()) {
            if (node.getNodeType() == NodeType.LATENT) {
                containsLatent = true;
                break;
            }
        }

        SemOptimizer optimizer;

        if (containsFixedParam(this.semPm) || this.semPm.getGraph().paths().existsDirectedCycle() || SemEstimatorWrapper.containsCovarParam(this.semPm)) {
            optimizer = new SemOptimizerPowell();
        } else if (containsLatent) {
            optimizer = new SemOptimizerEm();
        } else {
            optimizer = new SemOptimizerRegression();
        }

        optimizer.setNumRestarts(getParams().getInt("numRestarts", 1));

        return optimizer;
    }
}

