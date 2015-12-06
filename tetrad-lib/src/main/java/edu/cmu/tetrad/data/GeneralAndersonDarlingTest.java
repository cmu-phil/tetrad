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

import org.apache.commons.math3.distribution.RealDistribution;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Implements the Anderson-Darling test against the given CDF, with P values calculated
 * as in R's ad.test method (in package nortest).
 * <p>
 * Note that in the calculation, points x such that log(1 - dist.get(x)))
 * is infinite are ignored.
 *
 * @author Joseph Ramsey
 */
public class GeneralAndersonDarlingTest {

    /**
     * The column of data being analyzed.
     */
    private List<Double> data;

    /**
     * The A^2 statistic for <code>data</code>
     */
    private double aSquared;

    /**
     * The A^2 statistic adjusted for sample size.
     */
    private double aSquaredStar;

    /**
     * The interpolated p value for the adjusted a squared.
     */
    private double p;

    /**
     * The reference CDF.
     */
    private RealDistribution dist;

    //============================CONSTRUCTOR===========================//

    /**
     * Constructs an Anderson-Darling test for the given column of data.
     */
    public GeneralAndersonDarlingTest(List<Double> data, RealDistribution dist) {
        if (dist == null) {
            throw new NullPointerException();
        }

        this.dist = dist;

        Collections.sort(data);

        this.data = data;

        runTest();
    }

    //============================PUBLIC METHODS=========================//

    /**
     * @return the A^2 statistic.
     */
    public double getASquared() {
        return aSquared;
    }

    /**
     * @return the A^2* statistic, which is the A^2 statistic adjusted
     * heuristically for sample size.
     */
    public double getASquaredStar() {
        return aSquaredStar;
    }

    /**
     * @return the p value of the A^2* statistic, which is interpolated using
     * exponential functions.
     */
    public double getP() {
        return p;
    }

    //============================PRIVATE METHODS========================//

    private void runTest() {
        int n = data.size();
        double h = 0.0;

        int numSummed = 0;

        for (int i = 1; i <= n; i++) {
            double x1 = data.get(i - 1);
            double a1 = Math.log(dist.cumulativeProbability(x1));

            double x2 = data.get(n + 1 - i - 1);
            double a2 = Math.log(1.0 - dist.cumulativeProbability(x2));

            double k = (2 * i - 1) * (a1 + a2);

            if (!(Double.isNaN(a1) || Double.isNaN(a2) || Double.isInfinite(a1) || Double.isInfinite(a2))) {
                h += k;
                numSummed++;
            }
        }

        double a = -n - (1.0 / numSummed) * h;
        double aa = (1 + 0.75 / numSummed + 2.25 / Math.pow(numSummed, 2)) * a;
        double p;

        if (aa < 0.2) {
            p = 1 - Math.exp(-13.436 + 101.14 * aa - 223.73 * aa * aa);
        } else if (aa < 0.34) {
            p = 1 - Math.exp(-8.318 + 42.796 * aa - 59.938 * aa * aa);
        } else if (aa < 0.6) {
            p = Math.exp(0.9177 - 4.279 * aa - 1.38 * aa * aa);
        } else {
            p = Math.exp(1.2937 - 5.709 * aa + 0.0186 * aa * aa);
        }

        this.aSquared = a;
        this.aSquaredStar = aa;
        this.p = p;
    }

//    private double[] leaveOutNaN(double[] data) {
//        int numPresent = 0;
//
//        for (double aData1 : data) {
//            if (!Double.isNaN(aData1)) {
//                numPresent++;
//            }
//        }
//
//        if (numPresent == data.length) {
//            double[] _data = new double[data.length];
//            System.arraycopy(data, 0, _data, 0, data.length);
//            return _data;
//        } else {
//            List<Double> _leaveOutMissing = new ArrayList<>();
//
//            for (double aData : data) {
//                if (!Double.isNaN(aData)) {
//                    _leaveOutMissing.add(aData);
//                }
//            }
//
//            double[] _data = new double[_leaveOutMissing.size()];
//            for (int i = 0; i < _leaveOutMissing.size(); i++) _data[i] = _leaveOutMissing.get(i);
//            return _data;
//        }
//    }
}



