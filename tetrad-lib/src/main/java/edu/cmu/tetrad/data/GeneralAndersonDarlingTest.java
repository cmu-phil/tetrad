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

import edu.cmu.tetrad.util.RandomUtil;
import org.apache.commons.math3.distribution.RealDistribution;
import org.apache.commons.math3.distribution.UniformRealDistribution;
import org.apache.commons.math3.util.FastMath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.apache.commons.math3.util.FastMath.*;

/**
 * Implements the Anderson-Darling test against the given CDF, with P values calculated as in R's ad.test method (in
 * package nortest).
 * <p>
 * Note that in the calculation, points x such that log(1 - dist.get(x))) is infinite are ignored.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class GeneralAndersonDarlingTest {

    /**
     * The column of data being analyzed.
     */
    private final List<Double> data;
    /**
     * The reference CDF.
     */
    private final RealDistribution dist;
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
     * @param data a {@link java.util.List} object
     * @param dist a {@link org.apache.commons.math3.distribution.RealDistribution} object
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

    /**
     * <p>main.</p>
     *
     * @param args an array of {@link java.lang.String} objects
     */
    public static void main(String[] args) {
        List<Double> data = new ArrayList<>();

        for (int i = 0; i < 500; i++) {
//            data.add(RandomUtil.getInstance().nextUniform(0, 1));
            data.add(RandomUtil.getInstance().nextBeta(2, 5));
        }

        GeneralAndersonDarlingTest test = new GeneralAndersonDarlingTest(data, new UniformRealDistribution(0, 1));

        System.out.println(test.getASquared());
        System.out.println(test.getASquaredStar());
        System.out.println(test.getP());
        System.out.println(test.getProbTail(data.size(), test.getASquared()));
    }

    /**
     * <p>Getter for the field <code>aSquared</code>.</p>
     *
     * @return the A^2 statistic.
     */
    public double getASquared() {
        return this.aSquared;
    }

    /**
     * <p>Getter for the field <code>aSquaredStar</code>.</p>
     *
     * @return the A^2* statistic, which is the A^2 statistic adjusted heuristically for sample size.
     */
    public double getASquaredStar() {
        return this.aSquaredStar;
    }

    /**
     * <p>Getter for the field <code>p</code>.</p>
     *
     * @return the p value of the A^2* statistic, which is interpolated using exponential functions.
     */
    public double getP() {
        return this.p;
    }

    private void runTest() {
        int n = this.data.size();
        double h = 0.0;

        int numSummed = 0;

        for (int i = 1; i <= n; i++) {
            double x1 = this.data.get(i - 1);
            double a1 = FastMath.log(this.dist.cumulativeProbability(x1));

            double x2 = this.data.get(n + 1 - i - 1);
            double a2 = FastMath.log(1.0 - this.dist.cumulativeProbability(x2));

            double k = (2 * i - 1) * (a1 + a2);

            if (!(Double.isNaN(a1) || Double.isNaN(a2) || Double.isInfinite(a1) || Double.isInfinite(a2))) {
                h += k;
                numSummed++;
            }
        }

        double a = -n - (1.0 / numSummed) * h;
        double aa = (1 + 0.75 / numSummed + 2.25 / pow(numSummed, 2)) * a;
        double p;

        if (aa < 0.2) {
            p = 1 - exp(-13.436 + 101.14 * aa - 223.73 * aa * aa);
        } else if (aa < 0.34) {
            p = 1 - exp(-8.318 + 42.796 * aa - 59.938 * aa * aa);
        } else if (aa < 0.6) {
            p = exp(0.9177 - 4.279 * aa - 1.38 * aa * aa);
        } else {
            p = exp(1.2937 - 5.709 * aa + 0.0186 * aa * aa);
        }

        this.aSquared = a;
        this.aSquaredStar = aa;
        this.p = p;
    }

    private double c(double n) {
        return .01265 + .1757 / n;
    }

    private double g1(double x) {
        return sqrt(x) * (1 - x) * (49 * x - 102);
    }

    private double g2(double x) {
        return -.00022633 + (6.54034 - (14.6538 - (14.458 - (8.259 - 1.91864 * x) * x) * x) * x) * x;
    }

    private double g3(double x) {
        return -130.2137 + (745.2337 - (1705.091 - (1950.646 - (1116.360 - 255.7844 * x) * x) * x) * x) * x;
    }

    private double errfix(double n, double x) {
        if (x < c(n)) {
            return (.0037 / pow(n, 3) + .00078 / pow(n, 2) + .00006 / n) * g1(x / c(n));
        } else if (x < .8) {
            return (.04213 / n + .01365 / pow(n, 2)) * g2((x - c(n)) / (.8 - c(n)));
        } else {
            return g3(x) / n;
        }
    }

    private double adinf(double z) {
        if (0 < z && z < 2) {
            return pow(z, -0.5) * exp(-1.2337141 / z) * (2.00012 + (0.247105 - (.0649821 - (.0347962 - (.0116720 - .00168691 * z) * z) * z) * z) * z);
        } else if (z >= 2) {
            return exp(-exp(1.0776 - (2.30695 - (.43424 - (.082433 - (.008056 - .0003146 * z) * z) * z) * z) * z));
        } else {
            return 0;
        }
    }

    /**
     * <p>getProbTail.</p>
     *
     * @param n a double
     * @param z a double
     * @return a double
     */
    public double getProbTail(double n, double z) {
        return adinf(z) + errfix(n, adinf(z));
    }
}



