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

import edu.cmu.tetrad.util.ProbUtils;
import org.apache.commons.math3.linear.*;
import org.apache.commons.math3.util.FastMath;

import java.util.Arrays;

/**
 * Estimates the rank of a matrix.
 *
 * @author adambrodie
 */
public class EstimateRank {

    /**
     * Compute canonical correlations from data.
     */
    public static double[] CanCor(double[][] A, double[][] B) {
        RealMatrix Ua = new SingularValueDecomposition(new BlockRealMatrix(A)).getU();
        RealMatrix UTa = Ua.transpose();
        RealMatrix Ub = new SingularValueDecomposition(new BlockRealMatrix(B)).getU();
        return new SingularValueDecomposition(UTa.multiply(Ub)).getSingularValues();
    }

    /**
     * Compute canonical correlations from covariance matrix.
     */
    public static double[] CanCor(int[] iA, int[] iB, double[][] cov) {
        RealMatrix covA = new BlockRealMatrix(cov).getSubMatrix(iA, iA);
        RealMatrix covB = new BlockRealMatrix(cov).getSubMatrix(iB, iB);
        RealMatrix covAB = new BlockRealMatrix(cov).getSubMatrix(iA, iB);
        RealMatrix covBA = new BlockRealMatrix(cov).getSubMatrix(iB, iA);
        RealMatrix S = getInverse(covA).multiply(covAB).multiply(getInverse(covB)).multiply(covBA);
        double[] rtCors = new EigenDecomposition(S).getRealEigenvalues();
        Arrays.sort(rtCors);
        double[] Cors = new double[rtCors.length];
        for (int i = rtCors.length; i > 0; i--) {
            Cors[rtCors.length - i] = FastMath.pow(rtCors[i - 1], .5);
        }
        return Cors;
    }

    /**
     * Estimate rank from data.
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

    private static RealMatrix getInverse(RealMatrix covA) {
        return new LUDecomposition(covA).getSolver().getInverse();
    }
}
