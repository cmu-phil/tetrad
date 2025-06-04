/// ////////////////////////////////////////////////////////////////////////////
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
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.util;

import org.apache.commons.math3.util.FastMath;

import java.util.ArrayList;
import java.util.List;

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
     * Calculates the factorial of a non-negative integer n.
     * The factorial of n (denoted as n!) is the product of all positive integers
     * less than or equal to n. By definition, the factorial of 0 is 1.
     *
     * @param n the non-negative integer for which the factorial is to be calculated
     * @return the factorial of the given integer n as a long value
     * @throws IllegalArgumentException if n is negative
     */
    public static long factorial(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("n must be non-negative.");
        }

        long i = 1;

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
     * Applies the Rectified Linear Unit (ReLU) activation function to the given input. ReLU is defined as max(0, x).
     *
     * @param x the input value to which the ReLU function is applied
     * @return the result of applying the ReLU function, which is the input if it is positive, or 0 otherwise
     */
    public static Double relu(Double x) {
        return Math.max(0, x);
    }

    /**
     * Applies the Leaky Rectified Linear Unit (Leaky ReLU) activation function to the given input. Leaky ReLU is
     * defined as max(0.1 * x, x). It allows for a small, non-zero gradient when the unit is not active.
     *
     * @param x the input value to which the Leaky ReLU function is applied
     * @return the result of applying the Leaky ReLU function, which is x if x is positive, or 0.1 * x otherwise
     */
    public static Double leakyRelu(Double x) {
        return Math.max(0.1 * x, x);
    }

    /**
     * Computes the inverse hyperbolic tangent (arctanh) of the given input. The input value must be strictly between -1
     * and 1.
     *
     * @param x the input value for which to calculate the arctanh. Must be between -1 and 1 (exclusive).
     * @return the arctanh of the specified input.
     * @throws IllegalArgumentException if the input is not strictly within the range (-1, 1).
     */
    public static double arctanh(double x) {
        if (x <= -1 || x >= 1) {
            throw new IllegalArgumentException("Input x must be between -1 and 1 (exclusive).");
        }
        return 0.5 * Math.log((1 + x) / (1 - x));
    }

    /**
     * Converts a list of indices into a list of Integers representing a cluster.
     *
     * @param indices The indices of the variables.
     * @return The extracted index list.
     */
    public static List<Integer> getInts(int[] indices) {
        List<Integer> cluster = new ArrayList<>();
        for (int i : indices) cluster.add(i);
        return cluster;
    }
}



