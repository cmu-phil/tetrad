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
 * Provides a static method for finding the cutoff value for a symmetric
 * probability distribution function about the origin.
 *
 * @author Joseph Ramsey
 */
public class CutoffFinder {

    /**
     * Assumes f is a positive symmetric function between x1 and x2 about 0.
     * Integrates from 0 in the direction of x2 in intervals of deltaX until an
     * area of .5 * (1 - alpha) has been accumulated. Returns the x value at
     * the iteration where this amount of area has been accumulated.</p> </p>
     * <p>This is helpful for finding cutoff levels for normal curves,
     * distributions of correlation coefficients, Student's t, etc. It returns
     * significance level cutoffs.
     *
     * @param f           a function
     * @param xUpperBound an upper bound for the integration.
     * @param alpha       the significance level.
     * @param deltaX      the amount the integration jumps forward each time.
     * @return the cutoff value for these conditions.
     */
    public static double getCutoff(Function f, double xUpperBound, double alpha,
            double deltaX) {

        double area = 0.0;
        double x1 = 0.0, x2 = 0.0;
        double upperAreaLimit = .5 * (1 - alpha);

        while ((area < upperAreaLimit) && (x1 < xUpperBound)) {
            x1 = x2;
            x2 += deltaX;

            double yAve = .5 * (f.valueAt(x1) + f.valueAt(x2));
            area += yAve * deltaX;
        }

        return x1;
    }
}





