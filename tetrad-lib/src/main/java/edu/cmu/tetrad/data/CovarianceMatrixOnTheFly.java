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

import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.TetradAlgebra;
import edu.cmu.tetrad.util.TetradMatrix;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.*;

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
public class CovarianceMatrixOnTheFly implements ICovarianceMatrix {
    static final long serialVersionUID = 23L;
    private boolean verbose = false;
    CovarianceMatrix covMatrix;


    //=============================CONSTRUCTORS=========================//

    /**
     * Constructs a new covariance matrix from the given data set. If dataSet is
     * a BoxDataSet with a VerticalDoubleDataBox, the data will be mean-centered
     * by the constructor; is non-mean-centered version of the data is needed,
     * the data should be copied before being send into the constructor.
     *
     * @throws IllegalArgumentException if this is not a continuous data set.
     */
    public CovarianceMatrixOnTheFly(DataSet dataSet) {
        covMatrix = new CovarianceMatrix(dataSet);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static ICovarianceMatrix serializableInstance() {
        List<Node> variables = new ArrayList<>();
        Node x = new ContinuousVariable("X");
        variables.add(x);
        TetradMatrix matrix = TetradAlgebra.identity(1);
        return new CovarianceMatrix(variables, matrix, 100); //
    }

    //============================PUBLIC METHODS=========================//

    /**
     * @return the list of variables (unmodifiable).
     */
    public final List<Node> getVariables() {
        return covMatrix.getVariables();
    }

    /**
     * @return the variable names, in order.
     */
    public final List<String> getVariableNames() {
        return covMatrix.getVariableNames();
    }

    /**
     * @return the variable name at the given index.
     */
    public final String getVariableName(int index) {
        return covMatrix.getVariableName(index);
    }

    /**
     * @return the dimension of the covariance matrix.
     */
    public final int getDimension() {
        return covMatrix.getDimension();
    }

    /**
     * The size of the sample used to calculated this covariance matrix.
     *
     * @return The sample size (> 0).
     */
    public final int getSampleSize() {
        return covMatrix.getSampleSize();
    }

    /**
     * Gets the name of the covariance matrix.
     */
    public final String getName() {
        return covMatrix.getName();
    }

    /**
     * Sets the name of the covariance matrix.
     */
    public final void setName(String name) {
        covMatrix.setName(name);
    }

    /**
     * @return the knowledge associated with this data.
     */
    public final IKnowledge getKnowledge() {
        return covMatrix.getKnowledge();
    }

    /**
     * Associates knowledge with this data.
     */
    public final void setKnowledge(IKnowledge knowledge) {
        covMatrix.setKnowledge(knowledge);
    }

    /**
     * @return a submatrix of the covariance matrix with variables in the
     * given order.
     */
    public final ICovarianceMatrix getSubmatrix(int[] indices) {
        return covMatrix.getSubmatrix(indices);
    }

    public final ICovarianceMatrix getSubmatrix(List<String> submatrixVarNames) {
        return covMatrix.getSubmatrix(submatrixVarNames);
    };

    /**
     * @return a submatrix of this matrix, with variables in the given
     * order.
     */
    public final CovarianceMatrix getSubmatrix(String[] submatrixVarNames) {
        return covMatrix.getSubmatrix(submatrixVarNames);
    }

    /**
     * @return the value of element (i,j) in the matrix
     */
    public final double getValue(int i, int j) {
        return covMatrix.getValue(i, j);
    }

    public void setMatrix(TetradMatrix matrix) {
        covMatrix.setMatrix(matrix);
    }

    public final void setSampleSize(int sampleSize) {
        covMatrix.setSampleSize(sampleSize);
    }

    /**
     * @return the size of the square matrix.
     */
    public final int getSize() {
        return covMatrix.getSize();
    }

    /**
     * @return a copy of the covariance matrix.
     */
    public final TetradMatrix getMatrix() {
        return covMatrix.getMatrix();
    }

    public final void select(Node variable) {
        covMatrix.select(variable);
    }

    public final void clearSelection() {
        covMatrix.clearSelection();;
    }

    public final boolean isSelected(Node variable) {
        return covMatrix.isSelected(variable);
    }

    public final List<String> getSelectedVariableNames() {
        return covMatrix.getSelectedVariableNames();
    }

    /**
     * Prints out the matrix
     */
    public final String toString() {
        return covMatrix.toString();
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

    public void setVariables(List<Node> variables) {
        covMatrix.setVariables(variables);
    }

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    @Override
    public TetradMatrix getSelection(int[] rows, int[] cols) {
        return covMatrix.getSelection(rows, cols);
    }

    //========================PRIVATE METHODS============================//

    public Node getVariable(String name) {
        for (int i = 0; i < getVariables().size(); i++) {
            Node variable = getVariables().get(i);
            if (name.equals(variable.getName())) {
                return variable;
            }
        }

        return null;
    }

    @Override
    public DataModel copy() {
        return null;
    }

    @Override
    public void setValue(int i, int j, double v) {
        throw new IllegalArgumentException();
    }

    @Override
    public void removeVariables(List<String> remaining) {
        covMatrix.removeVariables(remaining);
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
     * @throws java.io.IOException
     * @throws ClassNotFoundException
     */
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        if (getVariables() == null) {
            throw new NullPointerException();
        }
    }
}





