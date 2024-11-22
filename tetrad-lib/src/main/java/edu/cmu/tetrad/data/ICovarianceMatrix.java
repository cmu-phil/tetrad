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

import java.util.List;

/**
 * Interface for covariance matrices. Implemented in different ways. See implementations.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public interface ICovarianceMatrix extends DataModel {
    /**
     * Retrieves the list of Node variables for the covariance matrix.
     *
     * @return a list of Node objects representing the variables in the covariance matrix.
     */
    List<Node> getVariables();

    /**
     * Sets the list of Node variables for the covariance matrix.
     *
     * @param variables a list of Node objects representing the variables to be set in the covariance matrix
     */
    void setVariables(List<Node> variables);

    /**
     * Retrieves the list of names of the variables in the covariance matrix.
     *
     * @return a list of strings representing the names of the variables.
     */
    List<String> getVariableNames();

    /**
     * Retrieves the name of the variable at the specified index from the covariance matrix.
     *
     * @param index the index of the variable whose name is to be retrieved
     * @return the name of the variable at the specified index
     */
    String getVariableName(int index);

    /**
     * Retrieves the dimension of the covariance matrix.
     *
     * @return the dimension of the covariance matrix as an integer
     */
    int getDimension();

    /**
     * Retrieves the sample size used in the covariance matrix.
     *
     * @return the sample size as an integer
     */
    int getSampleSize();

    /**
     * Sets the sample size used in the covariance matrix.
     *
     * @param sampleSize the sample size to be set
     */
    void setSampleSize(int sampleSize);

    /**
     * Gets the name of the covariance matrix.
     *
     * @return the name of the covariance matrix as a String
     */
    String getName();

    /**
     * Sets the name of the covariance matrix.
     *
     * @param name the new name of the covariance matrix
     */
    void setName(String name);

    /**
     * Retrieves the knowledge associated with the ICovarianceMatrix.
     *
     * @return the Knowledge object representing the associated knowledge
     */
    Knowledge getKnowledge();

    /**
     * Sets the knowledge associated with the ICovarianceMatrix.
     *
     * @param knowledge the knowledge to set
     */
    void setKnowledge(Knowledge knowledge);

    /**
     * Returns a submatrix of the covariance matrix, including only the specified variables.
     *
     * @param indices an array of integers specifying the indices of the variables to include in the submatrix
     * @return a submatrix of the covariance matrix that includes only the specified variables
     */
    ICovarianceMatrix getSubmatrix(int[] indices);

    /**
     * Returns a submatrix of the covariance matrix, including only the specified variables.
     *
     * @param submatrixVarNames a list of strings specifying the variable names to include in the submatrix
     * @return a submatrix of the covariance matrix that includes only the specified variables
     */
    ICovarianceMatrix getSubmatrix(List<String> submatrixVarNames);

    /**
     * Returns a submatrix of the covariance matrix, including only the specified variables.
     *
     * @param submatrixVarNames an array of strings specifying the variable names to include in the submatrix
     * @return a submatrix of the covariance matrix that includes only the specified variables
     */
    ICovarianceMatrix getSubmatrix(String[] submatrixVarNames);

    /**
     * Retrieves the value from the covariance matrix at the specified row and column indices.
     *
     * @param i the row index
     * @param j the column index
     * @return the value at the specified indices in the matrix
     */
    double getValue(int i, int j);

    /**
     * Retrieves the size of the matrix.
     *
     * @return the size of the square matrix.
     */
    int getSize();

    /**
     * Retrieves the covariance matrix.
     *
     * @return a Matrix object representing the covariance matrix
     */
    Matrix getMatrix();

    /**
     * Sets the covariance matrix.
     *
     * @param matrix the matrix to set
     */
    void setMatrix(Matrix matrix);

    /**
     * Selects a specified variable in the covariance matrix.
     *
     * @param variable the node variable to be selected
     */
    void select(Node variable);

    /**
     * Clears the current selection of variables in the covariance matrix.
     * This method resets any selected variables to their default unselected state.
     */
    void clearSelection();

    /**
     * Checks if the specified node is selected in the covariance matrix.
     *
     * @param variable the node to check for selection
     * @return true if the node is selected, false otherwise
     */
    boolean isSelected(Node variable);

    /**
     * Retrieves a list of names of the currently selected variables.
     *
     * @return a list of selected variable names.
     */
    List<String> getSelectedVariableNames();

    /**
     * Renders the covariance matrix as a string representation.
     *
     * @return a string representation of the covariance matrix.
     */
    String toString();

    /**
     * Retrieves a Node instance from the covariance matrix corresponding to the specified variable name.
     *
     * @param name the name of the variable to retrieve
     * @return the Node associated with the specified variable name
     */
    Node getVariable(String name);

    /**
     * Sets the value of the covariance matrix at the specified indices.
     *
     * @param i the row index
     * @param j the column index
     * @param v the value to set at the specified indices
     */
    void setValue(int i, int j, double v);

    /**
     * Removes variables from the covariance matrix, retaining only the variables
     * specified in the provided list.
     *
     * @param remaining a list of variable names to retain in the covariance matrix.
     */
    void removeVariables(List<String> remaining);

    /**
     * Returns a submatrix based on the specified rows and columns.
     *
     * @param rows an array of integers representing the row indices to be included in the selection.
     * @param cols an array of integers representing the column indices to be included in the selection.
     * @return a {@link Matrix} object containing the selected rows and columns.
     */
    Matrix getSelection(int[] rows, int[] cols);
}



