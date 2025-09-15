///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.data;

import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.StatUtils;
import org.apache.commons.math3.util.FastMath;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Implements the Anderson-Darling test for normality, with P values calculated as in R's ad.test method (in package
 * nortest).
 * <p>
 * Note that in the calculation, points x such that log(1 - normal_cdf(x)) is infinite are ignored.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class AndersonDarlingTest {

    /**
     * The column of data being analyzed.
     */
    private final double[] data;

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
     * Constructs an Anderson-Darling test for the given column of data.
     *
     * @param data the column of data to be analyzed.
     */
    public AndersonDarlingTest(double[] data) {
        this.data = data;
        runTest();
    }

    /**
     * Constructs an Anderson-Darling test for the given column of data.
     *
     * @return the A^2 statistic.
     */
    public double getASquared() {
        return this.aSquared;
    }

    /**
     * Constructs an Anderson-Darling test for the given column of data.
     *
     * @return the A^2* statistic, which is the A^2 statistic adjusted heuristically for sample size.
     */
    public double getASquaredStar() {
        return this.aSquaredStar;
    }

    /**
     * Constructs an Anderson-Darling test for the given column of data.
     *
     * @return the p value of the A^2* statistic, which is interpolated using exponential functions.
     */
    public double getP() {
        return this.p;
    }

    private void runTest() {
        double[] x = leaveOutNanAndInfinite(this.data);
        int n = x.length;

        Arrays.sort(x);

        double mean = StatUtils.mean(x);
        double sd = StatUtils.sd(x);

        for (int i = 0; i < n; i++) {
            x[i] = (x[i] - mean) / sd;
        }

        double h = 0.0;

        int numSummed = 0;

        for (int i = 1; i <= n; i++) {
            double x1 = x[i - 1];
            double a1 = FastMath.log(RandomUtil.getInstance().normalCdf(0, 1, x1));

            double x2 = x[n + 1 - i - 1];
            double a2 = FastMath.log(1.0 - RandomUtil.getInstance().normalCdf(0, 1, x2));

            double k = (2 * i - 1) * (a1 + a2);

            if (!(Double.isNaN(a1) || Double.isNaN(a2) || Double.isInfinite(a1) || Double.isInfinite(a2))) {
                h += k;
                numSummed++;
            }
        }

        double a = -numSummed - (1.0 / numSummed) * h;
        double aa = (1 + 0.75 / numSummed + 2.25 / FastMath.pow(numSummed, 2)) * a;
        double p;

        if (aa < 0.2) {
            p = 1 - FastMath.exp(-13.436 + 101.14 * aa - 223.73 * aa * aa);
        } else if (aa < 0.34) {
            p = 1 - FastMath.exp(-8.318 + 42.796 * aa - 59.938 * aa * aa);
        } else if (aa < 0.6) {
            p = FastMath.exp(0.9177 - 4.279 * aa - 1.38 * aa * aa);
        } else {
            p = FastMath.exp(1.2937 - 5.709 * aa + 0.0186 * aa * aa);
        }

        this.aSquared = a;
        this.aSquaredStar = aa;
        this.p = p;
    }

    private double[] leaveOutNanAndInfinite(double[] data) {
        int numPresent = 0;

        for (double aData1 : data) {
            if (!Double.isNaN(aData1) && !Double.isInfinite(aData1)) {
                numPresent++;
            }
        }

        if (numPresent == data.length) {
            double[] _data = new double[data.length];
            System.arraycopy(data, 0, _data, 0, data.length);
            return _data;
        } else {
            List<Double> _leaveOutMissing = new ArrayList<>();

            for (double aData : data) {
                if (!Double.isNaN(aData) && !Double.isInfinite(aData)) {
                    _leaveOutMissing.add(aData);
                }
            }

            double[] _data = new double[_leaveOutMissing.size()];
            for (int i = 0; i < _leaveOutMissing.size(); i++) _data[i] = _leaveOutMissing.get(i);
            return _data;
        }
    }
}




