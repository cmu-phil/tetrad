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

package edu.cmu.tetrad.util;


/**
 * Provides a static method for finding the cutoff value for a symmetric probability distribution function about the
 * origin.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class CutoffFinder {

    /**
     * Prevents instantiation.
     */
    private CutoffFinder() {

    }

    /**
     * Assumes f is a positive symmetric function between x1 and x2 about 0. Integrates from 0 in the direction of x2 in
     * intervals of deltaX until an area of .5 * (1 - alpha) has been accumulated. Returns the x value at the iteration
     * where this amount of area has been accumulated.
     * <p>
     * This is helpful for finding cutoff levels for normal curves, distributions of correlation coefficients, Student's
     * t, etc. It returns significance level cutoffs.
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






