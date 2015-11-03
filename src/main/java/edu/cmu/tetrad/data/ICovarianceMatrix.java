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
import edu.cmu.tetrad.util.TetradMatrix;
import edu.cmu.tetrad.util.TetradSerializable;

import java.util.List;

/**
 * Interface for covariance matrices. Implemented in different ways.
 * See implementations.
 */
public interface ICovarianceMatrix extends DataModel, TetradSerializable {
    List<Node> getVariables();

    List<String> getVariableNames();

    String getVariableName(int index);

    int getDimension();

    int getSampleSize();

    String getName();

    void setName(String name);

    IKnowledge getKnowledge();

    void setKnowledge(IKnowledge knowledge);

    ICovarianceMatrix getSubmatrix(int[] indices);

    ICovarianceMatrix getSubmatrix(List<String> submatrixVarNames);

    ICovarianceMatrix getSubmatrix(String[] submatrixVarNames);

    double getValue(int i, int j);

    void setMatrix(TetradMatrix matrix);

    void setSampleSize(int sampleSize);

    int getSize();

    TetradMatrix getMatrix();

    void select(Node variable);

    void clearSelection();

    boolean isSelected(Node variable);

    List<String> getSelectedVariableNames();

    String toString();

    Node getVariable(String name);

    void setValue(int i, int j, double v);

    void removeVariables(List<String> remaining);

    void setVariables(List<Node> variables);

    TetradMatrix getSelection(int[] rows, int[] cols);
}



