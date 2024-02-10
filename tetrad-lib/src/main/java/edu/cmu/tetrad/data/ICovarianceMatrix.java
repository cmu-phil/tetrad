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
     * <p>getVariables.</p>
     *
     * @return a {@link java.util.List} object
     */
    List<Node> getVariables();

    /**
     * <p>setVariables.</p>
     *
     * @param variables a {@link java.util.List} object
     */
    void setVariables(List<Node> variables);

    /**
     * <p>getVariableNames.</p>
     *
     * @return a {@link java.util.List} object
     */
    List<String> getVariableNames();

    /**
     * <p>getVariableName.</p>
     *
     * @param index a int
     * @return a {@link java.lang.String} object
     */
    String getVariableName(int index);

    /**
     * <p>getDimension.</p>
     *
     * @return a int
     */
    int getDimension();

    /**
     * <p>getSampleSize.</p>
     *
     * @return a int
     */
    int getSampleSize();

    /**
     * <p>setSampleSize.</p>
     *
     * @param sampleSize a int
     */
    void setSampleSize(int sampleSize);

    /**
     * <p>getName.</p>
     *
     * @return a {@link java.lang.String} object
     */
    String getName();

    /**
     * {@inheritDoc}
     */
    void setName(String name);

    /**
     * <p>getKnowledge.</p>
     *
     * @return a {@link edu.cmu.tetrad.data.Knowledge} object
     */
    Knowledge getKnowledge();

    /**
     * {@inheritDoc}
     */
    void setKnowledge(Knowledge knowledge);

    /**
     * <p>getSubmatrix.</p>
     *
     * @param indices an array of {@link int} objects
     * @return a {@link edu.cmu.tetrad.data.ICovarianceMatrix} object
     */
    ICovarianceMatrix getSubmatrix(int[] indices);

    /**
     * <p>getSubmatrix.</p>
     *
     * @param submatrixVarNames a {@link java.util.List} object
     * @return a {@link edu.cmu.tetrad.data.ICovarianceMatrix} object
     */
    ICovarianceMatrix getSubmatrix(List<String> submatrixVarNames);

    /**
     * <p>getSubmatrix.</p>
     *
     * @param submatrixVarNames an array of {@link java.lang.String} objects
     * @return a {@link edu.cmu.tetrad.data.ICovarianceMatrix} object
     */
    ICovarianceMatrix getSubmatrix(String[] submatrixVarNames);

    /**
     * <p>getValue.</p>
     *
     * @param i a int
     * @param j a int
     * @return a double
     */
    double getValue(int i, int j);

    /**
     * <p>getSize.</p>
     *
     * @return a int
     */
    int getSize();

    /**
     * <p>getMatrix.</p>
     *
     * @return a {@link edu.cmu.tetrad.util.Matrix} object
     */
    Matrix getMatrix();

    /**
     * <p>setMatrix.</p>
     *
     * @param matrix a {@link edu.cmu.tetrad.util.Matrix} object
     */
    void setMatrix(Matrix matrix);

    /**
     * <p>select.</p>
     *
     * @param variable a {@link edu.cmu.tetrad.graph.Node} object
     */
    void select(Node variable);

    /**
     * <p>clearSelection.</p>
     */
    void clearSelection();

    /**
     * <p>isSelected.</p>
     *
     * @param variable a {@link edu.cmu.tetrad.graph.Node} object
     * @return a boolean
     */
    boolean isSelected(Node variable);

    /**
     * <p>getSelectedVariableNames.</p>
     *
     * @return a {@link java.util.List} object
     */
    List<String> getSelectedVariableNames();

    /**
     * <p>toString.</p>
     *
     * @return a {@link java.lang.String} object
     */
    String toString();

    /**
     * {@inheritDoc}
     */
    Node getVariable(String name);

    /**
     * <p>setValue.</p>
     *
     * @param i a int
     * @param j a int
     * @param v a double
     */
    void setValue(int i, int j, double v);

    /**
     * <p>removeVariables.</p>
     *
     * @param remaining a {@link java.util.List} object
     */
    void removeVariables(List<String> remaining);

    /**
     * <p>getSelection.</p>
     *
     * @param rows an array of {@link int} objects
     * @param cols an array of {@link int} objects
     * @return a {@link edu.cmu.tetrad.util.Matrix} object
     */
    Matrix getSelection(int[] rows, int[] cols);
}



