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

package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.ProbUtils;
import javassist.Loader;
import org.apache.commons.math3.linear.*;
import org.apache.commons.math3.util.FastMath;
import org.ejml.simple.SimpleBase;
import org.ejml.simple.SimpleEVD;
import org.ejml.simple.SimpleMatrix;

import java.util.Arrays;

/**
 * Estimates the rank of a matrix.
 *
 * @author adambrodie
 * @version $Id: $Id
 */
public class EstimateRank {

    /**
     * Private constructor to prevent instantiation.
     */
    private EstimateRank() {

    }

    /**
     * Compute canonical correlations from data.
     *
     * @param A an array of  objects
     * @param B an array of  objects
     * @return an array of  objects
     */
    public static double[] CanCor(double[][] A, double[][] B) {
        SimpleMatrix Ua = new SimpleMatrix(A).svd().getU();
        SimpleMatrix Ub = new SimpleMatrix(B).svd().getU();
        return Arrays.stream(Ua.transpose().mult(Ub).svd().getSingularValues()).toArray();
    }

    /**
     * Compute canonical correlations from covariance matrix.
     *
     * @param iA  an array of  objects
     * @param iB  an array of  objects
     * @param cov an array of  objects
     * @return an array of  objects
     */
    public static double[] CanCor(int[] iA, int[] iB, double[][] cov) {
        Matrix _cov = new Matrix(cov);

        Matrix covA = _cov.getSelection(iA, iA);
        Matrix covB = _cov.getSelection(iB, iB);
        Matrix covAB = _cov.getSelection(iA, iB);
        Matrix covBA = _cov.getSelection(iB, iA);

        // TODO check the parens at the end of this.
        Matrix S = covA.inverse().times(covAB).times(covB.inverse().times(covBA));
        SimpleEVD<SimpleMatrix> eig = S.getSimpleMatrix().eig();

        double[] rtCors = new double[eig.getNumberOfEigenvalues()];
        for (int i = 0; i < eig.getNumberOfEigenvalues(); i++) {
            rtCors[i] = eig.getEigenvalue(i).getReal();
        }

        Arrays.sort(rtCors);
        double[] Cors = new double[rtCors.length];
        for (int i = rtCors.length; i > 0; i--) {
            Cors[rtCors.length - i] = FastMath.pow(rtCors[i - 1], .5);
        }
        return Cors;
    }

    /**
     * Estimate rank from data.
     *
     * @param A     an array of  objects
     * @param B     an array of  objects
     * @param alpha a double
     * @return a int
     */
    public static int estimate(double[][] A, double[][] B, double alpha) {
        double[] Cors = CanCor(A, B);
        int rank = 0;
        boolean reject = true;

        while (reject) {
            double sum = 0;
            int i;
            for (i = rank; i < FastMath.min(A[0].length, B[0].length); i++) {
                sum += FastMath.log(1 - FastMath.pow(Cors[i], 2));
            }
            double stat = -(A.length - .5 * (A[0].length + B[0].length + 3)) * sum;
            reject = ProbUtils.chisqCdf(stat, (A[0].length - rank) * (B[0].length - rank)) > (1 - alpha);
            if (reject & rank < FastMath.min(A[0].length, B[0].length)) {
                rank++;
            } else {
                reject = false;
            }
        }
        return rank;
    }

    /**
     * Estimate rank from covariance matrix.
     *
     * @param iA    an array of  objects
     * @param iB    an array of  objects
     * @param cov   an array of  objects
     * @param N     a int
     * @param alpha a double
     * @return a int
     */
    public static int estimate(int[] iA, int[] iB, double[][] cov, int N, double alpha) {
        double[] Cors = CanCor(iA, iB, cov);
        int rank = 0;
        boolean reject = true;

        while (reject) {
            double sum = 0;
            int i;
            for (i = rank; i < FastMath.min(iA.length, iB.length); i++) {
                sum += FastMath.log(1 - FastMath.pow(Cors[i], 2));
            }
            double stat = -(N - .5 * (iA.length + iB.length + 3)) * sum;
            reject = ProbUtils.chisqCdf(stat, (iA.length - rank) * (iB.length - rank)) > (1 - alpha);
            if (reject & rank < FastMath.min(iA.length, iB.length)) {
                rank++;
            } else {
                reject = false;
            }
        }

        return rank;
    }
}
