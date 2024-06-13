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

package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.LayoutUtil;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.util.*;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Estimates a SemIm given a CovarianceMatrix and a SemPm. (A DataSet may be substituted for the CovarianceMatrix.)
 *
 * @author Frank Wimberly
 * @author Ricardo Silva
 * @author Don Crimbchin
 * @author josephramsey
 * @version $Id: $Id
 */
public final class SemEstimator implements TetradSerializable {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The SemPm containing the graph and the freeParameters to be estimated.
     */
    private SemPm semPm;

    /**
     * The covariance matrix used to estimate the SemIm. Note that the variables names in the covariance matrix must be
     * in the same order as the variable names in the semPm.
     */
    private ICovarianceMatrix covMatrix;

    /**
     * The algorithm that minimizes the fitting function for the SEM.
     */
    private SemOptimizer semOptimizer;

    /**
     * The most recently estimated model, or null if no model has been estimated yet.
     */
    private SemIm estimatedSem;

    /**
     * The data set being estimated from (needed to calculate means of variables).  May be null in which case means are
     * set to zero.
     */
    private DataSet dataSet;

    /**
     * The score type used to optimize the SEM.
     */
    private ScoreType scoreType = ScoreType.Fgls;

    /**
     * The number of restarts to use.
     */
    private int numRestarts = 1;

    /**
     * Constructs a Sem Estimator that does default estimation.
     *
     * @param semPm   a SemPm specifying the graph and parameterization for the model.
     * @param dataSet a DataSet, all of whose variables are contained in the given SemPm. (They are identified by
     *                name.)
     */
    public SemEstimator(DataSet dataSet, SemPm semPm) {
        this(dataSet, semPm, null);
    }

    /**
     * Constructs a SEM estimator that does default estimation.
     *
     * @param semPm     a SemPm specifying the graph and parameterization for the model.
     * @param covMatrix a CovarianceMatrix, all of whose variables are contained in the given SemPm. (They are
     *                  identified by name.)
     */
    public SemEstimator(ICovarianceMatrix covMatrix, SemPm semPm) {
        this(covMatrix, semPm, null);
    }

    /**
     * Constructs a new SemEstimator that uses the specified optimizer.
     *
     * @param semPm        a SemPm specifying the graph and parameterization for the model.
     * @param dataSet      a DataSet, all of whose variables are contained in the given SemPm. (They are identified by
     *                     name.)
     * @param semOptimizer the optimizer that optimizes the Sem.
     */
    public SemEstimator(DataSet dataSet, SemPm semPm,
                        SemOptimizer semOptimizer) {
        this(new CovarianceMatrix(dataSet), semPm, semOptimizer);
        if (DataUtils.containsMissingValue(dataSet)) {
            throw new IllegalArgumentException("Expecting a data set with no missing values.");
        }
        setDataSet(subset(dataSet, semPm));
    }

    /**
     * Constructs a new SemEstimator that uses the specified optimizer.
     *
     * @param semPm        a SemPm specifying the graph and parameterization for the model.
     * @param covMatrix    a covariance matrix, all of whose variables are contained in the given SemPm. (They are
     *                     identified by name.)
     * @param semOptimizer the optimizer that optimizes the Sem.
     */
    public SemEstimator(ICovarianceMatrix covMatrix, SemPm semPm,
                        SemOptimizer semOptimizer) {
        if (covMatrix == null) {
            throw new NullPointerException(
                    "CovarianceMatrix must not be null.");
        }

        if (semPm == null) {
            throw new NullPointerException("SemPm must not be null.");
        }

        if (DataUtils.containsMissingValue(covMatrix.getMatrix())) {
            throw new IllegalArgumentException("Expecting a covariance matrix with no missing values.");
        }

        semPm.getGraph().setShowErrorTerms(false);

        setCovMatrix(submatrix(covMatrix, semPm));
        setSemPm(semPm);
        setSemOptimizer(semOptimizer);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetrad.sem.SemEstimator} object
     */
    public static SemEstimator serializableInstance() {
        return new SemEstimator(CovarianceMatrix.serializableInstance(),
                SemPm.serializableInstance());
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

    /**
     * Runs the estimator on the data and SemPm passed in through the constructor.
     *
     * @return a {@link edu.cmu.tetrad.sem.SemIm} object
     */
    public SemIm estimate() {
        if (getSemOptimizer() != null) {
            getSemOptimizer().setNumRestarts(this.numRestarts);
//            TetradLogger.getInstance().log("info", getSemOptimizer().toString());
//            TetradLogger.getInstance().log("info", "Score = " + getScoreType());
//            TetradLogger.getInstance().log("info", "Num restarts = " + getSemOptimizer().getNumRestarts());
        }

        //long time = MillisecondTimes.timeMillis();
        //System.out.println("Start timer.");

        // Forget any previous estimation results. (If the estimation fails,
        // the estimatedSem should be null.)
        setEstimatedSem(null);

        // Create the Sem from the SemPm and CovarianceMatrix.
        SemIm semIm = new SemIm(getSemPm(), getCovMatrix());
        LayoutUtil.arrangeBySourceGraph(semIm.getSemPm().getGraph(),
                getSemPm().getGraph());

        // Optimize the Sem.
        semIm.setParameterBoundsEnforced(false);
        semIm.setScoreType(getScoreType());

        SemOptimizer defaultOptimizer = getDefaultOptimization(semIm);

        if (this.semOptimizer == null) {
            this.semOptimizer = defaultOptimizer;
        }

        getSemOptimizer().setNumRestarts(this.numRestarts);
        getSemOptimizer().optimize(semIm);

        semIm.setParameterBoundsEnforced(true);
        setMeans(semIm, getDataSet());

        // Marks semIm as estimated
        semIm.setEstimated(true);

        // Set the estimated semIm to this.
        setEstimatedSem(semIm);

        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();
//        TetradLogger.getInstance().log("stats", "Final Score = " + nf.format(semIm.getScore()));
        TetradLogger.getInstance().log("Sample Size = " + semIm.getSampleSize());
        String message3 = "Model Chi Square = " + nf.format(semIm.getChiSquare());
        TetradLogger.getInstance().log(message3);
        String message2 = "Model DOF = " + nf.format(this.semPm.getDof());
        TetradLogger.getInstance().log(message2);
        String message1 = "Model P Value = " + nf.format(semIm.getPValue());
        TetradLogger.getInstance().log(message1);
        String message = "Model BIC = " + nf.format(semIm.getBicScore());
        TetradLogger.getInstance().log(message);

        System.out.println(this.estimatedSem);

        return this.estimatedSem;
    }

    /**
     * <p>Getter for the field <code>estimatedSem</code>.</p>
     *
     * @return the estimated SemIm. If the <code>estimate</code> method has not yet been called, <code>null</code> is
     * returned.
     */
    public SemIm getEstimatedSem() {
        return this.estimatedSem;
    }

    private void setEstimatedSem(SemIm estimatedSem) {
        this.estimatedSem = estimatedSem;
    }

    /**
     * <p>Getter for the field <code>dataSet</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.data.DataSet} object
     */
    public DataSet getDataSet() {
        return this.dataSet;
    }

    private void setDataSet(DataSet dataSet) {
        List<Node> nodes1 = this.semPm.getMeasuredNodes();

        List<Node> vars = new ArrayList<>();

        for (Node node : nodes1) {
            Node _node = dataSet.getVariable(node.getName());
            vars.add(_node);
        }

        DataSet _dataSet = new BoxDataSet(new VerticalDoubleDataBox(dataSet.getDoubleData().transpose().toArray()), vars);
        _dataSet.setName(dataSet.getName());

        this.dataSet = _dataSet;
    }

    /**
     * <p>Getter for the field <code>semPm</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.sem.SemPm} object
     */
    public SemPm getSemPm() {
        return this.semPm;
    }

    private void setSemPm(SemPm semPm) {
        this.semPm = semPm;
    }

    /**
     * <p>Getter for the field <code>covMatrix</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.data.ICovarianceMatrix} object
     */
    public ICovarianceMatrix getCovMatrix() {
        return this.covMatrix;
    }

    private void setCovMatrix(ICovarianceMatrix covMatrix) {
        this.covMatrix = covMatrix;
    }

    private SemOptimizer getSemOptimizer() {
        return this.semOptimizer;
    }

    /**
     * <p>Setter for the field <code>semOptimizer</code>.</p>
     *
     * @param semOptimizer a {@link edu.cmu.tetrad.sem.SemOptimizer} object
     */
    public void setSemOptimizer(SemOptimizer semOptimizer) {
        this.semOptimizer = semOptimizer;
    }

    /**
     * <p>toString.</p>
     *
     * @return a string representation of the Sem.
     */
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append("\nSemEstimator");

        if (this.getEstimatedSem() == null) {
            buf.append("\n\t...SemIm has not been estimated yet.");
        } else {
            SemIm sem = this.getEstimatedSem();
            buf.append("\n\n\tfml = ");

            buf.append("\n\n\tmeasuredNodes:\n");
            buf.append("\t").append(sem.getMeasuredNodes());

            buf.append("\n\n\tedgeCoef:\n");
            buf.append(MatrixUtils.toString(sem.getEdgeCoef().toArray()));

            buf.append("\n\n\terrCovar:\n");
            buf.append(MatrixUtils.toString(sem.getErrCovar().toArray()));
        }

        return buf.toString();
    }

    private SemOptimizer getDefaultOptimization(SemIm semIm) {
        if (semIm == null) throw new NullPointerException();

        boolean containsLatent = false;

        for (Node node : getSemPm().getGraph().getNodes()) {
            if (node.getNodeType() == NodeType.LATENT) {
                containsLatent = true;
                break;
            }
        }

        SemOptimizer optimizer;

        if (containsFixedParam() || getSemPm().getGraph().paths().existsDirectedCycle() ||
            SemEstimator.containsCovarParam(getSemPm())) {
            optimizer = new SemOptimizerPowell();
        } else if (containsLatent) {
            optimizer = new SemOptimizerEm();
        } else {
            optimizer = new SemOptimizerRegression();
        }

        optimizer.setNumRestarts(this.numRestarts);

        return optimizer;

    }

    private boolean containsFixedParam() {
        return new SemIm(getSemPm()).getNumFixedParams() > 0;
    }

    /**
     * @return A submatrix of <code>covMatrix</code> with the order of its variables the same as in <code>semPm</code>.
     * @throws IllegalArgumentException if not all of the variables of
     *                                  <code>semPm</code> are in <code>covMatrix</code>.
     */
    private ICovarianceMatrix submatrix(ICovarianceMatrix covMatrix,
                                        SemPm semPm) {
        String[] measuredVarNames = semPm.getMeasuredVarNames();

        try {
            return covMatrix.getSubmatrix(measuredVarNames);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            throw new RuntimeException(
                    "All of the variables from the SEM parameterized model " +
                    "must be in the data set.", e);
        }
    }

    private DataSet subset(DataSet dataSet, SemPm semPm) {
        String[] measuredVarNames = semPm.getMeasuredVarNames();
        int[] varIndices = new int[measuredVarNames.length];
        List<Node> dataVars = dataSet.getVariables();

        for (int i = 0; i < measuredVarNames.length; i++) {
            Node variable = dataSet.getVariable(measuredVarNames[i]);
            varIndices[i] = dataVars.indexOf(variable);
        }

        return dataSet.subsetColumns(varIndices);
    }

    /**
     * Sets the means of variables in the SEM IM based on the given data set.
     */
    private void setMeans(SemIm semIm, DataSet dataSet) {
        if (dataSet != null) {
            int numColumns = dataSet.getNumColumns();

            for (int j = 0; j < numColumns; j++) {
                double[] column = dataSet.getDoubleData().getColumn(j).toArray();
                double mean = StatUtils.mean(column);

                Node node = dataSet.getVariable(j);
                Node variableNode = semIm.getVariableNode(node.getName());
                semIm.setMean(variableNode, mean);

                double standardDeviation = StatUtils.sd(column);

                semIm.setMeanStandardDeviation(variableNode, standardDeviation);
            }
        } else if (getCovMatrix() != null) {
            List<Node> variables = getCovMatrix().getVariables();

            for (Node node : variables) {
                Node variableNode = semIm.getVariableNode(node.getName());
                semIm.setMean(variableNode, 0.0);
            }
        }
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

    private ScoreType getScoreType() {
        return this.scoreType;
    }

    /**
     * <p>Setter for the field <code>scoreType</code>.</p>
     *
     * @param scoreType a {@link edu.cmu.tetrad.sem.ScoreType} object
     */
    public void setScoreType(ScoreType scoreType) {
        this.scoreType = scoreType;
    }

    /**
     * <p>Setter for the field <code>numRestarts</code>.</p>
     *
     * @param numRestarts a int
     */
    public void setNumRestarts(int numRestarts) {
        this.numRestarts = numRestarts;
    }


}




