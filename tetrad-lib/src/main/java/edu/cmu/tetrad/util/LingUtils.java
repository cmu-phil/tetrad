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

package edu.cmu.tetrad.util;

import cern.colt.matrix.DoubleMatrix1D;
import cern.colt.matrix.DoubleMatrix2D;
import cern.colt.matrix.impl.DenseDoubleMatrix1D;
import cern.colt.matrix.impl.DenseDoubleMatrix2D;
import cern.colt.matrix.linalg.CholeskyDecomposition;

/**
 * Sundry utilities for the Ling algorithm.
 */
public class LingUtils {
    //Gustavo 7 May 2007
    //
    //makes the diagonal 1, scaling the remainder of each row appropriately
    //pre: 'matrix' must be square
    public static TetradMatrix normalizeDiagonal(TetradMatrix matrix) {
        TetradMatrix resultMatrix = matrix.copy();
        for (int i = 0; i < resultMatrix.rows(); i++) {
            double factor = 1 / resultMatrix.get(i, i);
            for (int j = 0; j < resultMatrix.columns(); j++)
                resultMatrix.set(i, j, factor * resultMatrix.get(i, j));
        }
        return resultMatrix;
    }

    //Gustavo 7 May 2007
    //returns the identity matrix of dimension n
    public static DoubleMatrix2D identityMatrix(int n) {
        DoubleMatrix2D I = new DenseDoubleMatrix2D(n, n) {
        };
        I.assign(0);
        for (int i = 0; i < n; i++)
            I.set(i, i, 1);
        return I;
    }

    //Gustavo 7 May 2007
    //returns the linear combination of two vectors a, b (aw is the coefficient of a, bw is the coefficient of b)
    public static DoubleMatrix1D linearCombination(DoubleMatrix1D a, double aw, DoubleMatrix1D b, double bw) {
        DoubleMatrix1D resultMatrix = new DenseDoubleMatrix1D(a.size());
        for (int i = 0; i < a.size(); i++) {
            resultMatrix.set(i, aw * a.get(i) + bw * b.get(i));
        }
        return resultMatrix;
    }

    //the vectors are in vecs
    //the coefficients are in the vector 'weights'
    public static DoubleMatrix1D linearCombination(DoubleMatrix1D[] vecs, double[] weights) {
        //the elements of vecs must be vectors of the same size
        DoubleMatrix1D resultMatrix = new DenseDoubleMatrix1D(vecs[0].size());

        for (int i = 0; i < vecs[0].size(); i++) { //each entry
            double sum = 0;
            for (int j = 0; j < vecs.length; j++) { //for each vector
                sum += vecs[j].get(i) * weights[j];
            }
            resultMatrix.set(i, sum);
        }
        return resultMatrix;
    }

    //linear combination of matrices a,b
    public static DoubleMatrix2D linearCombination(DoubleMatrix2D a, double aw, DoubleMatrix2D b, double bw) {
        if (a.rows() != b.rows()) {
            System.out.println();
        }

        DoubleMatrix2D resultMatrix = new DenseDoubleMatrix2D(a.rows(), a.columns());
        for (int i = 0; i < a.rows(); i++) {
            for (int j = 0; j < a.columns(); j++) {
                resultMatrix.set(i, j, aw * a.get(i, j) + bw * b.get(i, j));
            }
        }
        return resultMatrix;
    }

    //Gustavo 7 May 2007
    //converts Colt vectors into double[]
    public static double[] convert(DoubleMatrix1D vector) {
        int n = vector.size();
        double[] v = new double[n];
        for (int i = 0; i < n; i++)
            v[i] = vector.get(i);
        return v;
    }

    //Gustavo 7 May 2007
    //converts Colt matrices into double[]
    public static double[][] convert(DoubleMatrix2D inVectors) {
        return inVectors.toArray();
//        if (inVectors == null) return null;
//
//        int m = inVectors.rows();
//        int n = inVectors.columns();
//
//        double[][] inV = new double[m][n];
//        for (int i = 0; i < m; i++)
//            for (int j = 0; j < n; j++)
//                inV[i][j] = inVectors.get(i, j);
//
//        return inV;
    }

    //Gustavo 7 May 2007
    //converts double[] into Colt matrices
    public static DoubleMatrix2D convertToColt(double[][] vectors) {
        int m = vectors.length; //TetradMatrix.getNumOfRows(vectors);
        int n = vectors[0].length; //TetradMatrix.getNumOfColumns(vectors);

        DoubleMatrix2D mat = new DenseDoubleMatrix2D(m, n);
        for (int i = 0; i < m; i++)
            for (int j = 0; j < n; j++)
                mat.set(i, j, vectors[i][j]);

        return mat;
    }

    public static TetradMatrix inverse(DoubleMatrix2D mat) {
        TetradMatrix m = new TetradMatrix(mat.toArray());

        return m.inverse();
    }

    public static boolean isPositiveDefinite(TetradMatrix matrix) {
        return new CholeskyDecomposition(new DenseDoubleMatrix2D(matrix.toArray())).isSymmetricPositiveDefinite();
    }

}



