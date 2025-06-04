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

import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.math3.linear.SingularMatrixException;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.text.NumberFormat;
import java.util.*;

/**
 * Stores a covariance matrix together with variable names and sample size, intended as a representation of a data set.
 * When constructed from a continuous data set, the matrix is not checked for positive definiteness; however, when a
 * covariance matrix is supplied, its positive definiteness is always checked. If the sample size is less than the
 * number of variables, the positive definiteness is "spot-checked"--that is, checked for various submatrices.
 *
 * @author josephramsey
 * @version $Id: $Id
 * @see edu.cmu.tetrad.data.CorrelationMatrix
 */
public class CovarianceMatrix implements ICovarianceMatrix {
    @Serial
    private static final long serialVersionUID = 23L;
    /**
     * The wrapped covariance matrix data.
     */
    private final Matrix _covariancesMatrix;
    /**
     * The name of the covariance matrix.
     */
    private String name;
    /**
     * The variables (in order) for this covariance matrix.
     */
    private List<Node> variables;
    /**
     * The size of the sample from which this covariance matrix was calculated.
     */
    private int sampleSize;
    /**
     * The list of selected variables.
     */
    private Set<Node> selectedVariables = new HashSet<>();
    /**
     * The knowledge for this data.
     */
    private Knowledge knowledge = new Knowledge();

    /**
     * Constructs a new covariance matrix from the given data set.
     *
     * @param dataSet a {@link edu.cmu.tetrad.data.DataSet} object
     * @throws java.lang.IllegalArgumentException if this is not a continuous data set.
     */
    public CovarianceMatrix(DataSet dataSet) {
        this(dataSet, true);
    }

    /**
     * <p>Constructor for CovarianceMatrix.</p>
     *
     * @param dataSet       a {@link edu.cmu.tetrad.data.DataSet} object
     * @param biasCorrected a boolean
     */
    public CovarianceMatrix(DataSet dataSet, boolean biasCorrected) {
        if (!dataSet.isContinuous()) {
            throw new IllegalArgumentException("Not a continuous data set.");
        }

        CovariancesDoubleForkJoin covariances = new CovariancesDoubleForkJoin(dataSet.getDoubleData().toArray(), biasCorrected);
        this.variables = Collections.unmodifiableList(dataSet.getVariables());
        this.sampleSize = dataSet.getNumRows();
        this._covariancesMatrix = new Matrix(covariances.getMatrix());
    }

    /**
     * Protected constructor to construct a new covariance matrix using the supplied continuous variables and the the
     * given symmetric, positive definite matrix and sample size. The number of variables must equal the dimension of
     * the array.
     *
     * @param variables  the list of variables (in order) for the covariance matrix.
     * @param matrix     an square array of containing covariances.
     * @param sampleSize the sample size of the data for these covariances.
     * @throws java.lang.IllegalArgumentException if the given matrix is not symmetric (to a tolerance of 1.e-5) and
     *                                            positive definite, if the number of variables does not equal the
     *                                            dimension of m, or if the sample size is not positive.
     */
    public CovarianceMatrix(List<Node> variables, Matrix matrix, int sampleSize) {
        this(variables, matrix.toArray(), sampleSize);
    }

    /**
     * <p>Constructor for CovarianceMatrix.</p>
     *
     * @param variables  a {@link java.util.List} object
     * @param matrix     an array of  objects
     * @param sampleSize a int
     */
    public CovarianceMatrix(List<Node> variables, double[][] matrix,
                            int sampleSize) {
        if (variables.size() > matrix.length && variables.size() != matrix[0].length) {
            throw new IllegalArgumentException("# variables not equal to matrix dimension.");
        }

        // This is not calculating covariances, just storing them.
        this._covariancesMatrix = new Matrix(matrix);
        this.variables = variables;
        this.sampleSize = sampleSize;
    }

    /**
     * Copy constructor.
     *
     * @param covMatrix a {@link edu.cmu.tetrad.data.CovarianceMatrix} object
     */
    public CovarianceMatrix(CovarianceMatrix covMatrix) {

        // This is not calculating covariances, just storing them.
        this._covariancesMatrix = new Matrix(covMatrix.getMatrix());
        this.variables = covMatrix.getVariables();
        this.sampleSize = covMatrix.getSampleSize();
        this.name = covMatrix.getName();
        this.knowledge = covMatrix.getKnowledge();
        this.selectedVariables = covMatrix.getSelectedVariables();
    }

    /**
     * <p>Constructor for CovarianceMatrix.</p>
     *
     * @param covMatrix a {@link edu.cmu.tetrad.data.ICovarianceMatrix} object
     */
    public CovarianceMatrix(ICovarianceMatrix covMatrix) {
        this(covMatrix.getVariables(), covMatrix.getMatrix(), covMatrix.getSampleSize());
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
        return new CovarianceMatrix(variables, matrix, 100);
    }

    /**
     * <p>Getter for the field <code>variables</code>.</p>
     *
     * @return the list of variables (unmodifiable).
     */
    public final List<Node> getVariables() {
        return this.variables;
    }

    /**
     * {@inheritDoc}
     */
    public void setVariables(List<Node> variables) {
        if (variables.size() != this.variables.size()) {
            throw new IllegalArgumentException("Wrong # of variables.");
        }
        for (int i = 0; i < variables.size(); i++) {
            if (!variables.get(i).getName().equals(variables.get(i).getName())) {
                throw new IllegalArgumentException("Variable in index " + (i + 1) + " does not have the same name "
                                                   + "as the variable being substituted for it.");
            }
            this.variables = variables;
        }
    }

    /**
     * <p>getVariableNames.</p>
     *
     * @return the variable names, in order.
     */
    public final List<String> getVariableNames() {
        List<String> names = new ArrayList<>();

        for (int i = 0; i < getVariables().size(); i++) {
            Node variable = getVariables().get(i);
            names.add(variable.getName());
        }

        return names;
    }

    /**
     * {@inheritDoc}
     */
    public final String getVariableName(int index) {
        if (index >= getVariables().size()) {
            throw new IllegalArgumentException("Index out of range: " + index);
        }

        Node variable = getVariables().get(index);
        return variable.getName();
    }

    /**
     * <p>getDimension.</p>
     *
     * @return the dimension of the covariance matrix.
     */
    public final int getDimension() {
        return this.variables.size();
    }

    /**
     * The size of the sample used to calculated this covariance matrix.
     *
     * @return The sample size (&gt; 0).
     */
    public final int getSampleSize() {
        return this.sampleSize;
    }

    /**
     * {@inheritDoc}
     */
    public final void setSampleSize(int sampleSize) {
        if (sampleSize <= 0) {
            throw new IllegalArgumentException("Sample size must be > 0.");
        }

        this.sampleSize = sampleSize;
    }

    /**
     * Gets the name of the covariance matrix.
     *
     * @return a {@link java.lang.String} object
     */
    public final String getName() {
        return this.name;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Sets the name of the covariance matrix.
     */
    public final void setName(String name) {
        this.name = name;
    }

    /**
     * <p>Getter for the field <code>knowledge</code>.</p>
     *
     * @return the knowledge associated with this data.
     */
    public final Knowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Associates knowledge with this data.
     */
    public final void setKnowledge(Knowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException();
        }

        this.knowledge = knowledge.copy();
    }

    /**
     * <p>getSubmatrix.</p>
     *
     * @param indices an array of  objects
     * @return a submatrix of the covariance matrix with variables in the given order.
     */
    public final ICovarianceMatrix getSubmatrix(int[] indices) {
        List<Node> submatrixVars = new LinkedList<>();

        for (int indice : indices) {
            submatrixVars.add(this.variables.get(indice));
        }

        Matrix cov = new Matrix(this._covariancesMatrix.view(indices, indices).mat());
        return new CovarianceMatrix(submatrixVars, cov, getSampleSize());
    }

    /**
     * <p>getSubmatrix.</p>
     *
     * @param submatrixVarNames a {@link java.util.List} object
     * @return a {@link edu.cmu.tetrad.data.ICovarianceMatrix} object
     */
    public final ICovarianceMatrix getSubmatrix(List<String> submatrixVarNames) {
        String[] varNames = new String[submatrixVarNames.size()];

        for (int i = 0; i < submatrixVarNames.size(); i++) {
            varNames[i] = submatrixVarNames.get(i);
        }

        return getSubmatrix(varNames);
    }

    /**
     * <p>getSubmatrix.</p>
     *
     * @param submatrixVarNames an array of {@link java.lang.String} objects
     * @return a submatrix of this matrix, with variables in the given order.
     */
    public final CovarianceMatrix getSubmatrix(String[] submatrixVarNames) {
        List<Node> submatrixVars = new LinkedList<>();

        List<String> missing = new ArrayList<>();

        for (String submatrixVarName : submatrixVarNames) {
            if (submatrixVarName.startsWith("E_")) continue;
            Node variable = getVariable(submatrixVarName);
            if (variable == null) missing.add(submatrixVarName);
            submatrixVars.add(variable);
        }

        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(
                    "The following variables in the submatrix are missing from the data: " +
                    "\n   " + missing +
                    "\nIf there are lagged variables, try using the data box to convert the data to time " +
                    "\nlagged data first.");
        }

//        if (!getVariables().containsAll(submatrixVars)) {
//
//
//
//            throw new IllegalArgumentException(
//                    "The variables in the submatrix "
//                            + "must be in the original matrix: original=="
//                            + getVariables() + ", sub==" + submatrixVars);
//        }

        for (int i = 0; i < submatrixVars.size(); i++) {
            if (submatrixVars.get(i) == null) {
                throw new NullPointerException(
                        "The variable name at index " + i + " is null.");
            }
        }

        int[] indices = new int[submatrixVars.size()];
        for (int i = 0; i < indices.length; i++) {
            indices[i] = getVariables().indexOf(submatrixVars.get(i));
        }

        Matrix cov = getMatrix().view(indices, indices).mat();
        return new CovarianceMatrix(submatrixVars, cov, getSampleSize());
    }

    /**
     * {@inheritDoc}
     */
    public final double getValue(int i, int j) {
        return this._covariancesMatrix.get(i, j);
    }

    /**
     * <p>getSize.</p>
     *
     * @return the size of the square matrix.
     */
    public final int getSize() {
        return this._covariancesMatrix.getNumColumns();
    }

    /**
     * <p>getMatrix.</p>
     *
     * @return a the covariance matrix (not a copy).
     */
    public final Matrix getMatrix() {
        return this._covariancesMatrix;
    }

    /**
     * {@inheritDoc}
     */
    public void setMatrix(Matrix matrix) {
        throw new IllegalStateException();
    }

    /**
     * {@inheritDoc}
     */
    public final void select(Node variable) {
        if (this.variables.contains(variable)) {
            getSelectedVariables().add(variable);
        }
    }

    /**
     * <p>clearSelection.</p>
     */
    public final void clearSelection() {
        getSelectedVariables().clear();
    }

    /**
     * {@inheritDoc}
     */
    public final boolean isSelected(Node variable) {
        if (variable == null) {
            throw new NullPointerException("Null variable. Try again.");
        }

        return getSelectedVariables().contains(variable);
    }

    /**
     * <p>getSelectedVariableNames.</p>
     *
     * @return a {@link java.util.List} object
     */
    public final List<String> getSelectedVariableNames() {
        List<String> selectedVariableNames = new LinkedList<>();

        for (Node variable : this.selectedVariables) {
            selectedVariableNames.add(variable.getName());
        }

        return selectedVariableNames;
    }

    /**
     * Prints out the matrix
     *
     * @return a {@link java.lang.String} object
     */
    public final String toString() {
        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

        StringBuilder buf = new StringBuilder();
        buf.append(this.sampleSize).append("\n");

        // Build the variable names
        buf.append(String.join("\t", getVariableNames()));

        int numVars = getVariableNames().size();
        buf.append("\n");

        for (int j = 0; j < numVars; j++) {
            for (int i = 0; i <= j; i++) {
                buf.append(nf.format(getValue(i, j)));

                // Don't add ending tab to each data line, data reader will validate this extra tab as missing value
                // This is the fix to issue #525: https://github.com/cmu-phil/tetrad/issues/525
                if (i < j) {
                    buf.append("\t");
                }
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
     * {@inheritDoc}
     */
    @Override
    public Matrix getSelection(int[] rows, int[] cols) {
        return getMatrix().view(rows, cols).mat();
    }

    /**
     * {@inheritDoc}
     */
    public Node getVariable(String name) {
        for (int i = 0; i < getVariables().size(); i++) {
            Node variable = getVariables().get(i);
            if (name.equals(variable.getName())) {
                return variable;
            }
        }

        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DataModel copy() {
        return new CovarianceMatrix(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setValue(int i, int j, double v) {
        this._covariancesMatrix.set(i, j, v);
        this._covariancesMatrix.set(j, i, v);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeVariables(List<String> remaining) {
        throw new IllegalStateException();
    }

    private Set<Node> getSelectedVariables() {
        return this.selectedVariables;
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
     * <p>isSingular.</p>
     *
     * @return a boolean
     */
    public boolean isSingular() {
        try {
            _covariancesMatrix.inverse();
        } catch (SingularMatrixException e) {
            return true;
        }

        return false;
    }
}
