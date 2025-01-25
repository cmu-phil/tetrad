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

package edu.cmu.tetrad.util;

import org.apache.commons.math3.util.FastMath;

/**
 * Some extra mathematical functions not contained in org.apache.commons.math3.util.FastMath.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class MathUtils {

    /**
     * Private constructor.
     */
    private MathUtils() {

    }

    /**
     * <p>logistic.</p>
     *
     * @param x a double value.
     * @return the logistic function of x = 1 / (1 + exp(-x)).
     */
    public static double logistic(double x) {
        return 1. / (1. + FastMath.exp(-x));
    }

    /**
     * <p>factorial.</p>
     *
     * @param n a int
     * @return a int
     */
    public static int factorial(int n) {
        int i = 1;

        for (int j = 1; j <= n; j++) {
            i *= j;
        }

        return i;
    }

    /**
     * <p>logFactorial.</p>
     *
     * @param n a int
     * @return a double
     */
    public static double logFactorial(int n) {
        double i = 0;

        for (int j = 1; j <= n; j++) {
            i += FastMath.log(j);
        }

        return i;
    }

    /**
     * <p>choose.</p>
     *
     * @param a a int
     * @param b a int
     * @return a int
     */
    public static int choose(int a, int b) {
        if (a == 0 && b == 0) {
            return 1;
        } else if (a == 0 && b > 0) {
            return (int) FastMath.round(FastMath.exp(1 - (MathUtils.logFactorial(b) + MathUtils.logFactorial(-b))));
        } else if (a > 0 && b == 0) {
            return (int) FastMath.round(FastMath.exp(MathUtils.logFactorial(a) - (1 + MathUtils.logFactorial(a))));
        } else if (a > 0 && b > 0) {
            return (int) FastMath.round(FastMath.exp(MathUtils.logFactorial(a) - (MathUtils.logFactorial(b) + MathUtils.logFactorial(a - b))));
        } else {
            throw new IllegalArgumentException();
        }
    }

    /**
     * <p>logChoose.</p>
     *
     * @param a a int
     * @param b a int
     * @return a double
     */
    public static double logChoose(int a, int b) {
        return MathUtils.logFactorial(a) - (MathUtils.logFactorial(b) + MathUtils.logFactorial(a - b));
    }

    /**
     * Applies the Rectified Linear Unit (ReLU) activation function to the given input.
     * ReLU is defined as max(0, x).
     *
     * @param x the input value to which the ReLU function is applied
     * @return the result of applying the ReLU function, which is the input if it is positive, or 0 otherwise
     */
    public static Double relu(Double x) {
        return Math.max(0, x);
    }

    /**
     * Applies the Leaky Rectified Linear Unit (Leaky ReLU) activation function to the given input.
     * Leaky ReLU is defined as max(0.1 * x, x). It allows for a small, non-zero gradient when the unit is not active.
     *
     * @param x the input value to which the Leaky ReLU function is applied
     * @return the result of applying the Leaky ReLU function, which is x if x is positive, or 0.1 * x otherwise
     */
    public static Double leakyRelu(Double x) {
        return Math.max(0.1 * x, x);
    }
}



