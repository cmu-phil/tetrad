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

import edu.cmu.tetrad.util.ProbUtils;
import org.apache.commons.math3.linear.*;

import java.util.Arrays;

/**
 * Estimates the rank of a matrix.
 */
public class EstimateRank {
    double alpha;
    double[][] A;
    double[][] B;
    int[] iA;
    int[] iB;
    double[][] cov;
    int N;

    //Compute canonical correlations from data.
    public double[] CanCor(final double[][] A, final double[][] B) {
        this.A = A;
        this.B = B;
        final RealMatrix Ua = new SingularValueDecomposition(new BlockRealMatrix(A)).getU();
        final RealMatrix UTa = Ua.transpose();
        final RealMatrix Ub = new SingularValueDecomposition(new BlockRealMatrix(B)).getU();
        return new SingularValueDecomposition(UTa.multiply(Ub)).getSingularValues();
    }

    //Compute canonical correlations from covariance matrix.
    public double[] CanCor(final int[] iA, final int[] iB, final double[][] cov) {
        this.iA = iA;
        this.iB = iB;
        this.cov = cov;
        final RealMatrix covA = new BlockRealMatrix(cov).getSubMatrix(iA, iA);
        final RealMatrix covB = new BlockRealMatrix(cov).getSubMatrix(iB, iB);
        final RealMatrix covAB = new BlockRealMatrix(cov).getSubMatrix(iA, iB);
        final RealMatrix covBA = new BlockRealMatrix(cov).getSubMatrix(iB, iA);
        final RealMatrix S = getInverse(covA).multiply(covAB).multiply(getInverse(covB)).multiply(covBA);
        final double[] rtCors = new EigenDecomposition(S).getRealEigenvalues();
        Arrays.sort(rtCors);
        final double[] Cors = new double[rtCors.length];
        for (int i = rtCors.length; i > 0; i--) {
            Cors[rtCors.length - i] = Math.pow(rtCors[i - 1], .5);
        }
        return Cors;
    }

    private RealMatrix getInverse(final RealMatrix covA) {
        return new LUDecomposition(covA).getSolver().getInverse();
    }

    //Estimate rank from data.
    public int Estimate(final double[][] A, final double[][] B, final double alpha) {
        this.alpha = alpha;
        this.A = A;
        this.B = B;
        final double[] Cors = CanCor(A, B);
        int rank = 0;
        boolean reject = true;

        while (reject) {
            double sum = 0;
            int i;
            for (i = rank; i < Math.min(A[0].length, B[0].length); i++) {
                sum += Math.log(1 - Math.pow(Cors[i], 2));
            }
            final double stat = -(A.length - .5 * (A[0].length + B[0].length + 3)) * sum;
            reject = ProbUtils.chisqCdf(stat, (A[0].length - rank) * (B[0].length - rank)) > (1 - alpha);
            if (reject & rank < Math.min(A[0].length, B[0].length)) {
                rank++;
            } else {
                reject = false;
            }
        }
        return rank;
    }

    //Estimate rank from covariance matrix.
    public int Estimate(final int[] iA, final int[] iB, final double[][] cov, final int N, final double alpha) {
        this.alpha = alpha;
        this.iA = iA;
        this.iB = iB;
        this.cov = cov;
        this.N = N;
        final double[] Cors = CanCor(iA, iB, cov);
        int rank = 0;
        boolean reject = true;

        while (reject) {
            double sum = 0;
            int i;
            for (i = rank; i < Math.min(iA.length, iB.length); i++) {
                sum += Math.log(1 - Math.pow(Cors[i], 2));
            }
            final double stat = -(N - .5 * (iA.length + iB.length + 3)) * sum;
            reject = ProbUtils.chisqCdf(stat, (iA.length - rank) * (iB.length - rank)) > (1 - alpha);
            if (reject & rank < Math.min(iA.length, iB.length)) {
                rank++;
            } else {
                reject = false;
            }
        }

        return rank;
    }
}
