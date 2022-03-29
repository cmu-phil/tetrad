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

package edu.cmu.tetrad.data;

import cern.colt.matrix.DoubleMatrix2D;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.TetradAlgebra;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.text.NumberFormat;
import java.util.*;

import static java.lang.Math.sqrt;

/**
 * Stores a covariance matrix together with variable names and sample size,
 * intended as a representation of a data set. When constructed from a
 * continuous data set, the matrix is not checked for positive definiteness;
 * however, when a covariance matrix is supplied, its positive definiteness is
 * always checked. If the sample size is less than the number of variables, the
 * positive definiteness is "spot-checked"--that is, checked for various
 * submatrices.
 *
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 * @see CorrelationMatrix
 */
public class CorrelationMatrixOnTheFly implements ICovarianceMatrix {
    static final long serialVersionUID = 23L;
    private boolean verbose = false;

    private final ICovarianceMatrix cov;

    /**
     * The name of the covariance matrix.
     *
     * @serial May be null.
     */
    private String name;

    /**
     * The variables (in order) for this covariance matrix.
     *
     * @serial Cannot be null.
     */
    private List<Node> variables;

    /**
     * The size of the sample from which this covariance matrix was calculated.
     *
     * @serial Range > 0.
     */
    private int sampleSize;

    /**
     * Stored matrix data. Should be square. This may be set by derived classes,
     * but it must always be set to a legitimate covariance matrix.
     *
     * @serial Cannot be null. Must be symmetric and positive definite.
     */
    private Matrix matrix;

    /**
     * @serial Do not remove this field; it is needed for serialization.
     */
    private DoubleMatrix2D matrixC;

    /**
     * The list of selected variables.
     *
     * @serial Cannot be null.
     */
    private Set<Node> selectedVariables = new HashSet<>();

    /**
     * The knowledge for this data.
     *
     * @serial Cannot be null.
     */
    private final IKnowledge knowledge = new Knowledge2();

    private final double[][] vectors = null;

    private double[] variances;


    //=============================CONSTRUCTORS=========================//

    /**
     * Constructs a new covariance matrix from the given data set. If dataSet is
     * a BoxDataSet with a VerticalDoubleDataBox, the data will be mean-centered
     * by the constructor; is non-mean-centered version of the data is needed,
     * the data should be copied before being send into the constructor.
     *
     * @throws IllegalArgumentException if this is not a continuous data set.
     */
    public CorrelationMatrixOnTheFly(final ICovarianceMatrix cov) {
        this.cov = cov;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static ICovarianceMatrix serializableInstance() {
        final List<Node> variables = new ArrayList<>();
        final Node x = new ContinuousVariable("X");
        variables.add(x);
        final Matrix matrix = TetradAlgebra.identity(1);
        return new CovarianceMatrix(variables, matrix, 100); //
    }

    //============================PUBLIC METHODS=========================//

    /**
     * @return the list of variables (unmodifiable).
     */
    public final List<Node> getVariables() {
        return this.cov.getVariables();
    }

    /**
     * @return the variable names, in order.
     */
    public final List<String> getVariableNames() {
        return this.cov.getVariableNames();
    }

    /**
     * @return the variable name at the given index.
     */
    public final String getVariableName(final int index) {
        return this.cov.getVariableName(index);
    }

    /**
     * @return the dimension of the covariance matrix.
     */
    public final int getDimension() {
        return this.cov.getDimension();
    }

    /**
     * The size of the sample used to calculated this covariance matrix.
     *
     * @return The sample size (> 0).
     */
    public final int getSampleSize() {
        return this.cov.getSampleSize();
    }

    /**
     * Gets the name of the covariance matrix.
     */
    public final String getName() {
        return this.cov.getName() + ".corr";
    }

    /**
     * Sets the name of the covariance matrix.
     */
    public final void setName(final String name) {
        this.cov.setName(name);
    }

    /**
     * @return the knowledge associated with this data.
     */
    public final IKnowledge getKnowledge() {
        return this.cov.getKnowledge();
    }

    /**
     * Associates knowledge with this data.
     */
    public final void setKnowledge(final IKnowledge knowledge) {
        this.cov.setKnowledge(knowledge);
    }

    /**
     * @return a submatrix of the covariance matrix with variables in the
     * given order.
     */
    public final ICovarianceMatrix getSubmatrix(final int[] indices) {
        final List<Node> submatrixVars = new LinkedList<>();

        for (final int indice : indices) {
            submatrixVars.add(this.cov.getVariables().get(indice));
        }

        final Matrix cov = new Matrix(indices.length, indices.length);

        for (int i = 0; i < indices.length; i++) {
            for (int j = i; j < indices.length; j++) {
                final double d = getValue(indices[i], indices[j]);
                cov.set(i, j, d);
                cov.set(j, i, d);
            }
        }

        return new CovarianceMatrix(submatrixVars, cov, getSampleSize());
    }

    public final ICovarianceMatrix getSubmatrix(final int[] indices, final int[] dataRows) {
        final List<Node> submatrixVars = new LinkedList<>();

        for (final int indice : indices) {
            submatrixVars.add(this.variables.get(indice));
        }

        final Matrix cov = new Matrix(indices.length, indices.length);

        for (int i = 0; i < indices.length; i++) {
            for (int j = i; j < indices.length; j++) {
                final double d = getValue(indices[i], indices[j]);
                cov.set(i, j, d);
                cov.set(j, i, d);
            }
        }

        return new CovarianceMatrix(submatrixVars, cov, getSampleSize());
    }

    public final ICovarianceMatrix getSubmatrix(final List<String> submatrixVarNames) {
        throw new UnsupportedOperationException();
    }

    /**
     * @return a submatrix of this matrix, with variables in the given
     * order.
     */
    public final CorrelationMatrixOnTheFly getSubmatrix(final String[] submatrixVarNames) {
        throw new UnsupportedOperationException();
    }

    /**
     * @return the value of element (i,j) in the matrix
     */
    public final double getValue(final int i, final int j) {
        double v = this.cov.getValue(i, j);
        v /= sqrt(this.cov.getValue(i, i) * this.cov.getValue(j, j));
        return v;
    }

    public void setMatrix(final Matrix matrix) {
        this.cov.setMatrix(matrix);
    }

    public final void setSampleSize(final int sampleSize) {
        this.cov.setSampleSize(sampleSize);
    }

    /**
     * @return the size of the square matrix.
     */
    public final int getSize() {
        return this.cov.getSize();
    }

    /**
     * @return a copy of the covariance matrix.
     */
    public final Matrix getMatrix() {
        final Matrix matrix = new Matrix(getDimension(), getDimension());

        for (int i = 0; i < getDimension(); i++) {
            for (int j = 0; j < getDimension(); j++) {
                matrix.set(i, j, getValue(i, j));
            }
        }

        return matrix;
    }

    public final Matrix getMatrix(final int[] rows) {
        final Matrix matrix = new Matrix(getDimension(), getDimension());

        for (int i = 0; i < getDimension(); i++) {
            for (int j = 0; j < getDimension(); j++) {
                matrix.set(i, j, getValue(i, j));
            }
        }

        return matrix;
    }

    public final void select(final Node variable) {
        this.cov.select(variable);
    }

    public final void clearSelection() {
        this.cov.clearSelection();
    }

    public final boolean isSelected(final Node variable) {
        return this.cov.isSelected(variable);
    }

    public final List<String> getSelectedVariableNames() {
        return this.cov.getSelectedVariableNames();
    }

    /**
     * Prints out the matrix
     */
    public final String toString() {
        final NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

        final StringBuilder buf = new StringBuilder();

        final int numVars = getVariableNames().size();
        buf.append(getSampleSize()).append("\n");

        for (int i = 0; i < numVars; i++) {
            final String name = getVariableNames().get(i);
            buf.append(name).append("\t");
        }

        buf.append("\n");

        for (int j = 0; j < numVars; j++) {
            for (int i = 0; i <= j; i++) {
                buf.append(nf.format(getValue(i, j))).append("\t");
            }
            buf.append("\n");
        }


//        buf.append("\nCovariance matrix:");
//        buf.append("\n\tVariables = ").append(getVariable());
//        buf.append("\n\tSample size = ").append(getSampleSize());
//        buf.append("\n");
//        buf.append(MatrixUtils.toString(matrixC.toArray()));
//
//        if (getKnowledge() != null && !getKnowledge().isEmpty()) {
//            buf.append(getKnowledge());
//        }

        return buf.toString();
    }

    @Override
    public boolean isContinuous() {
        return true;
    }

    @Override
    public boolean isDiscrete() {
        return false;
    }

    @Override
    public boolean isMixed() {
        return false;
    }

    public void setVariables(final List<Node> variables) {
        if (variables.size() != this.variables.size()) throw new IllegalArgumentException("Wrong # of variables.");

//        for (int i = 0; i < variables.size(); i++) {
//            if (!variables.get(i).getNode().equals(variables.get(i).getNode())) {
//                throw new IllegalArgumentException("Variable in index " + (i + 1) + " does not have the same name " +
//                        "as the variable being substituted for it.");
//            }
//        }

        this.variables = variables;
    }

    public boolean isVerbose() {
        return this.verbose;
    }

    public void setVerbose(final boolean verbose) {
        this.verbose = verbose;
    }

    @Override
    public Matrix getSelection(final int[] rows, final int[] cols) {
        final Matrix m = new Matrix(rows.length, cols.length);

        if (Arrays.equals(rows, cols)) {
            for (int i = 0; i < rows.length; i++) {
                for (int j = i; j < cols.length; j++) {
                    final double value = getValue(rows[i], cols[j]);
                    m.set(i, j, value);
                    m.set(j, i, value);
                }
            }
        } else {
            for (int i = 0; i < rows.length; i++) {
                for (int j = 0; j < cols.length; j++) {
                    final double value = getValue(rows[i], cols[j]);
                    m.set(i, j, value);
                }
            }
        }

        return m;
    }

    public Matrix getSelection(final int[] rows, final int[] cols, final int[] dataRows) {
        final Matrix m = new Matrix(rows.length, cols.length);

        if (Arrays.equals(rows, cols)) {
            for (int i = 0; i < rows.length; i++) {
                for (int j = i; j < cols.length; j++) {
                    final double value = getValue(rows[i], cols[j]);
                    m.set(i, j, value);
                    m.set(j, i, value);
                }
            }
        } else {
            for (int i = 0; i < rows.length; i++) {
                for (int j = 0; j < cols.length; j++) {
                    final double value = getValue(rows[i], cols[j]);
                    m.set(i, j, value);
                }
            }
        }

        return m;
    }

    public Node getVariable(final String name) {
        return this.cov.getVariable(name);
    }

    @Override
    public DataModel copy() {
        return null;
    }

    @Override
    public void setValue(final int i, final int j, final double v) {
        throw new IllegalArgumentException();
//        if (i == j) {
//            matrix.set(i, j, v);
//        } else {
//            matrix.set(i, j, v);
//            matrix.set(j, i, v);
//        }
    }

    @Override
    public void removeVariables(final List<String> remaining) {
        this.cov.removeVariables(remaining);
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
     *
     * @throws IOException
     * @throws ClassNotFoundException
     */
    private void readObject(final ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        if (getVariables() == null) {
            throw new NullPointerException();
        }

        if (this.matrixC != null) {
            this.matrix = new Matrix(this.matrixC.toArray());
            this.matrixC = null;
        }

        if (this.knowledge == null) {
            throw new NullPointerException();
        }

        if (this.sampleSize < -1) {
            throw new IllegalStateException();
        }

        if (this.selectedVariables == null) {
            this.selectedVariables = new HashSet<>();
        }
    }
}





