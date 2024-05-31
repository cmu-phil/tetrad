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

package edu.cmu.tetrad.data;

import cern.colt.matrix.DoubleMatrix2D;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.TetradLogger;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.text.NumberFormat;
import java.util.*;

import static org.apache.commons.math3.util.FastMath.sqrt;

/**
 * Stores a covariance matrix together with variable names and sample size, intended as a representation of a data set.
 * When constructed from a continuous data set, the matrix is not checked for positive definiteness; however, when a
 * covariance matrix is supplied, its positive definiteness is always checked. If the sample size is less than the
 * number of variables, the positive definiteness is "spot-checked"--that is, checked for various submatrices.
 *
 * @author josephramsey
 * @version $Id: $Id
 * @see CorrelationMatrix
 */
public class CorrelationMatrixOnTheFly implements ICovarianceMatrix {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The covariance matrix. Cannot be null. Must be symmetric and positive definite.
     */
    private final ICovarianceMatrix cov;

    /**
     * Whether to print out verbose messages.
     */
    private boolean verbose;
    /**
     * The variables (in order) for this covariance matrix.
     */
    private List<Node> variables;

    /**
     * The matrix data. Should be square. This may be set by derived classes, but it must always be set to a legitimate
     * covariance matrix.
     */
    private DoubleMatrix2D matrixC;

    /**
     * The list of selected variables.
     */
    private Set<Node> selectedVariables = new HashSet<>();

    /**
     * Constructs a new covariance matrix from the given data set. If dataSet is a BoxDataSet with a
     * VerticalDoubleDataBox, the data will be mean-centered by the constructor; is non-mean-centered version of the
     * data is needed, the data should be copied before being send into the constructor.
     *
     * @param cov a {@link edu.cmu.tetrad.data.ICovarianceMatrix} object
     * @throws java.lang.IllegalArgumentException if this is not a continuous data set.
     */
    public CorrelationMatrixOnTheFly(ICovarianceMatrix cov) {
        this.cov = cov;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetrad.data.ICovarianceMatrix} object
     */
    public static ICovarianceMatrix serializableInstance() {
        List<Node> variables = new ArrayList<>();
        Node x = new ContinuousVariable("X");
        variables.add(x);
        Matrix matrix = Matrix.identity(1);
        return new CovarianceMatrix(variables, matrix, 100); //
    }

    /**
     * <p>Getter for the field <code>variables</code>.</p>
     *
     * @return the list of variables (unmodifiable).
     */
    public final List<Node> getVariables() {
        return this.cov.getVariables();
    }

    /**
     * {@inheritDoc}
     */
    public void setVariables(List<Node> variables) {
        if (variables.size() != this.variables.size()) throw new IllegalArgumentException("Wrong # of variables.");
        this.variables = variables;
    }

    /**
     * <p>getVariableNames.</p>
     *
     * @return the variable names, in order.
     */
    public final List<String> getVariableNames() {
        return this.cov.getVariableNames();
    }

    /**
     * {@inheritDoc}
     */
    public final String getVariableName(int index) {
        return this.cov.getVariableName(index);
    }

    /**
     * <p>getDimension.</p>
     *
     * @return the dimension of the covariance matrix.
     */
    public final int getDimension() {
        return this.cov.getDimension();
    }

    /**
     * The size of the sample used to calculated this covariance matrix.
     *
     * @return The sample size (&gt; 0).
     */
    public final int getSampleSize() {
        return this.cov.getSampleSize();
    }

    /**
     * {@inheritDoc}
     */
    public final void setSampleSize(int sampleSize) {
        this.cov.setSampleSize(sampleSize);
    }

    /**
     * Gets the name of the covariance matrix.
     *
     * @return a {@link java.lang.String} object
     */
    public final String getName() {
        return this.cov.getName() + ".corr";
    }

    /**
     * {@inheritDoc}
     * <p>
     * Sets the name of the covariance matrix.
     */
    public final void setName(String name) {
        this.cov.setName(name);
    }

    /**
     * <p>getKnowledge.</p>
     *
     * @return the knowledge associated with this data.
     */
    public final Knowledge getKnowledge() {
        return this.cov.getKnowledge();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Associates knowledge with this data.
     */
    public final void setKnowledge(Knowledge knowledge) {
        this.cov.setKnowledge(knowledge);
    }

    /**
     * <p>getSubmatrix.</p>
     *
     * @param indices an array of {@link int} objects
     * @return a submatrix of the covariance matrix with variables in the given order.
     */
    public final ICovarianceMatrix getSubmatrix(int[] indices) {
        List<Node> submatrixVars = new LinkedList<>();

        for (int indice : indices) {
            submatrixVars.add(this.cov.getVariables().get(indice));
        }

        Matrix cov = new Matrix(indices.length, indices.length);

        for (int i = 0; i < indices.length; i++) {
            for (int j = i; j < indices.length; j++) {
                double d = getValue(indices[i], indices[j]);
                cov.set(i, j, d);
                cov.set(j, i, d);
            }
        }

        return new CovarianceMatrix(submatrixVars, cov, getSampleSize());
    }

    /**
     * <p>getSubmatrix.</p>
     *
     * @param submatrixVarNames a {@link java.util.List} object
     * @return a {@link edu.cmu.tetrad.data.ICovarianceMatrix} object
     */
    public final ICovarianceMatrix getSubmatrix(List<String> submatrixVarNames) {
        throw new UnsupportedOperationException();
    }

    /**
     * <p>getSubmatrix.</p>
     *
     * @param submatrixVarNames an array of {@link java.lang.String} objects
     * @return a submatrix of this matrix, with variables in the given order.
     */
    public final CorrelationMatrixOnTheFly getSubmatrix(String[] submatrixVarNames) {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    public final double getValue(int i, int j) {
        double v = this.cov.getValue(i, j);
        v /= sqrt(this.cov.getValue(i, i) * this.cov.getValue(j, j));
        return v;
    }

    /**
     * <p>getSize.</p>
     *
     * @return the size of the square matrix.
     */
    public final int getSize() {
        return this.cov.getSize();
    }

    /**
     * <p>getMatrix.</p>
     *
     * @return a copy of the covariance matrix.
     */
    public final Matrix getMatrix() {
        Matrix matrix = new Matrix(getDimension(), getDimension());

        for (int i = 0; i < getDimension(); i++) {
            for (int j = 0; j < getDimension(); j++) {
                matrix.set(i, j, getValue(i, j));
            }
        }

        return matrix;
    }

    /**
     * {@inheritDoc}
     */
    public void setMatrix(Matrix matrix) {
        this.cov.setMatrix(matrix);
    }

    /**
     * {@inheritDoc}
     */
    public final void select(Node variable) {
        this.cov.select(variable);
    }

    /**
     * <p>clearSelection.</p>
     */
    public final void clearSelection() {
        this.cov.clearSelection();
    }

    /**
     * {@inheritDoc}
     */
    public final boolean isSelected(Node variable) {
        return this.cov.isSelected(variable);
    }

    /**
     * <p>getSelectedVariableNames.</p>
     *
     * @return a {@link java.util.List} object
     */
    public final List<String> getSelectedVariableNames() {
        return this.cov.getSelectedVariableNames();
    }

    /**
     * Prints out the matrix
     *
     * @return a {@link java.lang.String} object
     */
    public final String toString() {
        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

        StringBuilder buf = new StringBuilder();

        int numVars = getVariableNames().size();
        buf.append(getSampleSize()).append("\n");

        for (int i = 0; i < numVars; i++) {
            String name = getVariableNames().get(i);
            buf.append(name).append("\t");
        }

        buf.append("\n");

        for (int j = 0; j < numVars; j++) {
            for (int i = 0; i <= j; i++) {
                buf.append(nf.format(getValue(i, j))).append("\t");
            }
            buf.append("\n");
        }

        return buf.toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isContinuous() {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isDiscrete() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isMixed() {
        return false;
    }

    /**
     * <p>isVerbose.</p>
     *
     * @return a boolean
     */
    public boolean isVerbose() {
        return this.verbose;
    }

    /**
     * <p>Setter for the field <code>verbose</code>.</p>
     *
     * @param verbose a boolean
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Matrix getSelection(int[] rows, int[] cols) {
        Matrix m = new Matrix(rows.length, cols.length);

        if (Arrays.equals(rows, cols)) {
            for (int i = 0; i < rows.length; i++) {
                for (int j = i; j < cols.length; j++) {
                    double value = getValue(rows[i], cols[j]);
                    m.set(i, j, value);
                    m.set(j, i, value);
                }
            }
        } else {
            for (int i = 0; i < rows.length; i++) {
                for (int j = 0; j < cols.length; j++) {
                    double value = getValue(rows[i], cols[j]);
                    m.set(i, j, value);
                }
            }
        }

        return m;
    }

    /**
     * {@inheritDoc}
     */
    public Node getVariable(String name) {
        return this.cov.getVariable(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataModel copy() {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setValue(int i, int j, double v) {
        throw new IllegalArgumentException();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeVariables(List<String> remaining) {
        this.cov.removeVariables(remaining);
    }

    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        try {
            out.defaultWriteObject();
        } catch (IOException e) {
            TetradLogger.getInstance().forceLogMessage("Failed to serialize object: " + getClass().getCanonicalName()
                    + ", " + e.getMessage());
            throw e;
        }
    }

    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
        } catch (IOException e) {
            TetradLogger.getInstance().forceLogMessage("Failed to deserialize object: " + getClass().getCanonicalName()
                    + ", " + e.getMessage());
            throw e;
        }
    }
}





