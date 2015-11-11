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

package edu.cmu.tetrad.search;

import cern.colt.matrix.DoubleMatrix2D;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.TetradMatrix;

import java.util.List;

public class PermutationMatrixPair {

    private DoubleMatrix2D matrixW;
    private DataSet matrixBhat;
    private TetradMatrix matrixA;
    private List<Integer> permutation;
    private List<Node> vars;

    public PermutationMatrixPair(List<Integer> permutation, DoubleMatrix2D matrixW, List<Node> vars) {
        this.permutation = permutation;
        this.matrixW = matrixW;
    }

    public DoubleMatrix2D getMatrixW() {
        return matrixW;
    }

    public DataSet getMatrixBhat() {
        return matrixBhat;
    }

    public void setMatrixBhat(DataSet matrixBhat) {
        this.matrixBhat = matrixBhat;
    }

    public TetradMatrix getMatrixA() {
        return matrixA;
    }

    public void setMatrixA(TetradMatrix matrixA) {
        this.matrixA = matrixA;
    }

    public List<Integer> getPermutation() {
        return permutation;
    }

    public String toString() {
        StringBuilder buf = new StringBuilder();

        buf.append("Permutation: " + permutation);
        buf.append("matrix W : " + matrixW);

        return buf.toString();
    }

    public void setMatrixW(DoubleMatrix2D matrixW) {
        this.matrixW = matrixW;
    }

    public void setVars(List<Node> vars) {
        this.vars = vars;
    }
}



