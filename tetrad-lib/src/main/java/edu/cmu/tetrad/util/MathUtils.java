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

/**
 * Some extra mathematical functions not contained in java.lang.Math.
 *
 * @author Joseph Ramsey
 */
public class MathUtils {

    /**
     * @param x a double value.
     * @return the logistic function of x = 1 / (1 + exp(-x)).
     */
    public static double logistic(double x) {
        return 1. / (1. + Math.exp(-x));
    }

    public static int factorial(int n) {
        int i = 1;

        for (int j = 1; j <= n; j++) {
            i *= j;
        }

        return i;
    }

    public static double logFactorial(int n) {
        double i = 0;

        for (int j = 1; j <= n; j++) {
            i += Math.log(j);
        }

        return i;
    }

    public static int choose(int a, int b) {
        if (a == 0 && b == 0) {
            return 1;
        } else if (a == 0 && b > 0) {
            return (int) Math.round(Math.exp(1 - (logFactorial(b) + logFactorial(a - b))));
        } else if (a > 0 && b == 0) {
            return (int) Math.round(Math.exp(logFactorial(a) - (1 + logFactorial(a - b))));
        } else if (a > 0 && b > 0) {
            return (int) Math.round(Math.exp(logFactorial(a) - (logFactorial(b) + logFactorial(a - b))));
        } else {
            throw new IllegalArgumentException();
        }
    }

    public static double logChoose(int a, int b) {
        return logFactorial(a) - (logFactorial(b) + logFactorial(a - b));
    }
}



