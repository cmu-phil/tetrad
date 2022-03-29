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
import edu.cmu.tetrad.graph.Dag;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.sem.*;
import edu.cmu.tetrad.session.SessionModel;
import edu.cmu.tetrad.util.*;

import javax.swing.*;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.LinkedList;
import java.util.List;

/**
 * Wraps a SemEstimator for use in the Tetrad application.
 *
 * @author Joseph Ramsey
 */
public class SemEstimatorWrapper implements SessionModel, Unmarshallable {

    static final long serialVersionUID = 23L;
    private final Parameters params;
    /**
     * @serial Can be null.
     */
    private String name;
    /**
     * @serial Cannot be null.
     */
    private SemEstimator semEstimator;
    private final SemPm semPm;

    //==============================CONSTRUCTORS==========================//

    /**
     * Private constructor for serialization only. Problem is, for the real
     * constructors, I'd like to call the degrees of freedom check, which pops
     * up a dialog. This is irritating when running unit tests. jdramsey 8/29/07
     */
    public SemEstimatorWrapper(DataModel dataModel, SemPm semPm, Parameters params) {
        this.params = params;
        this.semPm = semPm;

        if (dataModel instanceof DataSet) {
            DataSet dataSet = (DataSet) dataModel;
            SemEstimator estimator = new SemEstimator(dataSet, semPm, this.getOptimizer());
            estimator.setNumRestarts(this.getParams().getInt("numRestarts", 1));
            estimator.setScoreType((ScoreType) this.getParams().get("scoreType", ScoreType.Fgls));
            estimator.estimate();
            if (!this.degreesOfFreedomCheck(semPm)) {
                throw new IllegalArgumentException("Cannot proceed.");
            }
            semEstimator = estimator;
        } else if (dataModel instanceof ICovarianceMatrix) {
            ICovarianceMatrix covMatrix = new CovarianceMatrix((ICovarianceMatrix) dataModel);
            SemEstimator estimator = new SemEstimator(covMatrix, semPm, this.getOptimizer());
            estimator.setNumRestarts(this.getParams().getInt("numRestarts", 1));
            estimator.setScoreType((ScoreType) this.getParams().get("scoreType", ScoreType.SemBic));
            estimator.estimate();
            if (!this.degreesOfFreedomCheck(semPm)) {
                throw new IllegalArgumentException("Cannot proceed.");
            }
            semEstimator = estimator;
        } else {
            throw new IllegalArgumentException("Data must consist of continuous data sets or covariance matrices.");
        }
    }

    public SemEstimatorWrapper(DataWrapper dataWrapper,
                               SemPmWrapper semPmWrapper, Parameters params) {
        this(dataWrapper.getSelectedDataModel(), semPmWrapper.getSemPm(), params);
        this.log();
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
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
            int ret = JOptionPane.showConfirmDialog(JOptionUtils.centeringComp(),
                    "This model has nonpositive degrees of freedom (DOF = "
                            + semPm.getDof() + "). "
                            + "\nEstimation will be uninformative. Are you sure you want to proceed?",
                    "Please confirm", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

            if (ret != JOptionPane.YES_OPTION) {
                return false;
            }
        }

        return true;
    }

    //============================PUBLIC METHODS=========================//
    public SemEstimator getSemEstimator() {
        return semEstimator;
    }

    public void setSemEstimator(SemEstimator semEstimator) {
        this.semEstimator = semEstimator;
    }

    public SemIm getEstimatedSemIm() {
        return semEstimator.getEstimatedSem();
    }

    public String getSemOptimizerType() {
        return this.getParams().getString("semOptimizerType", "Regression");
    }

    public void setSemOptimizerType(String type) {
        this.getParams().set("semOptimizerType", type);
    }

    public Graph getGraph() {
        return semEstimator.getEstimatedSem().getSemPm().getGraph();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    //=============================== Private methods =======================//
    private void log() {
        TetradLogger.getInstance().log("info", "SEM Estimator:");
        TetradLogger.getInstance().log("im", "" + this.getEstimatedSemIm());
        TetradLogger.getInstance().log("details", "ChiSq = " + this.getEstimatedSemIm().getChiSquare());
        TetradLogger.getInstance().log("details", "DOF = " + this.getEstimatedSemIm().getSemPm().getDof());
        TetradLogger.getInstance().log("details", "P = " + this.getEstimatedSemIm().getPValue());
    }

    /**
     * Adds semantic checks to the default deserialization method. This method
     * must have the standard signature for a readObject method, and the body of
     * the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from
     * version to version. A readObject method of this form may be added to any
     * class, even if Tetrad sessions were previously saved out using a version
     * of the class that didn't include it. (That's what the
     * "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for help.
     */
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();
    }

    public Parameters getParams() {
        return params;
    }

    private SemOptimizer getOptimizer() {
        SemOptimizer optimizer;
        String type = this.getParams().getString("semOptimizerType", "Regression");

        if ("Regression".equals(type)) {
            SemOptimizer defaultOptimization = this.getDefaultOptimization();

            if (!(defaultOptimization instanceof SemOptimizerRegression)) {
                optimizer = defaultOptimization;
                type = this.getType(defaultOptimization);
                this.getParams().set("semOptimizerType", type);
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
            if (semPm != null) {
                optimizer = this.getDefaultOptimization();

                String _type = this.getType(optimizer);

                if (_type != null) {
                    this.getParams().set("semOptimizerType", _type);
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

    public ScoreType getScoreType() {
        return (ScoreType) params.get("scoreType", ScoreType.SemBic);
    }

    public void setScoreType(ScoreType scoreType) {
        params.set("scoreType", scoreType);
    }

    public int getNumRestarts() {
        return this.getParams().getInt("numRestarts", 1);
    }

    public void setNumRestarts(int numRestarts) {
        this.getParams().set("numRestarts", numRestarts);
    }

    private SemOptimizer getDefaultOptimization() {
        if (semPm == null) {
            throw new NullPointerException(
                    "Sorry, I didn't see a SEM PM as parent to the estimator; perhaps the parents are wrong.");
        }

        boolean containsLatent = false;

        for (Node node : semPm.getGraph().getNodes()) {
            if (node.getNodeType() == NodeType.LATENT) {
                containsLatent = true;
                break;
            }
        }

        SemOptimizer optimizer;

        if (this.containsFixedParam(semPm) || semPm.getGraph().existsDirectedCycle()
                || containsCovarParam(semPm)) {
            optimizer = new SemOptimizerPowell();
        } else if (containsLatent) {
            optimizer = new SemOptimizerEm();
        } else {
            optimizer = new SemOptimizerRegression();
        }

        optimizer.setNumRestarts(this.getParams().getInt("numRestarts", 1));

        return optimizer;
    }
}
