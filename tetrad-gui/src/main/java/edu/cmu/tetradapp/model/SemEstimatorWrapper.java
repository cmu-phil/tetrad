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

        if (dataModel instanceof DataSet dataSet) {
            SemEstimator estimator = new SemEstimator(dataSet, semPm, getOptimizer());
            estimator.setNumRestarts(getParams().getInt("numRestarts", 1));
            estimator.setScoreType((ScoreType) getParams().get("scoreType", ScoreType.Fgls));
            estimator.estimate();
            if (!degreesOfFreedomCheck(semPm)) {
                throw new IllegalArgumentException("Cannot proceed.");
            }
            this.semEstimator = estimator;
        } else if (dataModel instanceof ICovarianceMatrix) {
            ICovarianceMatrix covMatrix = new CovarianceMatrix((ICovarianceMatrix) dataModel);
            SemEstimator estimator = new SemEstimator(covMatrix, semPm, getOptimizer());
            estimator.setNumRestarts(getParams().getInt("numRestarts", 1));
            estimator.setScoreType((ScoreType) getParams().get("scoreType", ScoreType.Fml));
            estimator.estimate();
            if (!degreesOfFreedomCheck(semPm)) {
                throw new IllegalArgumentException("Cannot proceed.");
            }
            this.semEstimator = estimator;
        } else {
            throw new IllegalArgumentException("Data must consist of continuous data sets or covariance matrices.");
        }
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
        TetradLogger.getInstance().forceLogMessage("SEM Estimator:");
        String message3 = "" + getEstimatedSemIm();
        TetradLogger.getInstance().forceLogMessage(message3);
        String message2 = "ChiSq = " + getEstimatedSemIm().getChiSquare();
        TetradLogger.getInstance().forceLogMessage(message2);
        String message1 = "DOF = " + getEstimatedSemIm().getSemPm().getDof();
        TetradLogger.getInstance().forceLogMessage(message1);
        String message = "P = " + getEstimatedSemIm().getPValue();
        TetradLogger.getInstance().forceLogMessage(message);
    }

    /**
     * Adds semantic checks to the default deserialization method. This method must have the standard signature for a
     * readObject method, and the body of the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from version to version. A readObject method of
     * this form may be added to any class, even if Tetrad sessions were previously saved out using a version of the
     * class that didn't include it. (That's what the "s.defaultReadObject();" is for). See J. Bloch, Effective Java,
     * for help.
     *
     * @param s The object input stream.
     * @throws IOException            If any.
     * @throws ClassNotFoundException If any.
     */
    @Serial
    private void readObject(ObjectInputStream s) throws IOException, ClassNotFoundException {
        s.defaultReadObject();
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
