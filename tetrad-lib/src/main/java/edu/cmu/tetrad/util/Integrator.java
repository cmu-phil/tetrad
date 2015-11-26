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
 * Integrates under a function from one endpoint to another.
 *
 * @author Joseph Ramsey
 */
public class Integrator {

    /**
     * Finds the area under function f between x1 and x2 using Simpson's rule.
     * Divides the interval [x1, x2] into numIntervals subintervals.
     *
     * @param f            a Function object.
     * @param x1           the lower cutoff
     * @param x2           the upper cutoff
     * @param numIntervals the number of intervals to divide the interval [x1,
     *                     x2] into for integrating.
     * @return the area.
     */
    public static double getArea(Function f, double x1, double x2,
                                 int numIntervals) {
        if (f == null) {
            throw new IllegalArgumentException("Function not specified.");
        }

        if (!(x1 <= x2)) {
            //            return Double.NaN;
            throw new IllegalArgumentException("Integrating area under curve " +
                    "for interval [" + x1 + ", " + x2 + "], but " + x1 +
                    " is not less than " + x2 + ".");
        }

        double deltaX = x2 - x1;
        double area = 0.0;

        for (int n = 0; n <= numIntervals; n++) {
            double xValue = x1 + ((double) n / (double) numIntervals) * deltaX;

            if (n == 0) {
                area += f.valueAt(x1);
            } else if (n == numIntervals) {
                area += f.valueAt(x2);
            } else if (n % 2 == 1) {
                area += 4 * f.valueAt(xValue);
            } else {
                area += 2 * f.valueAt(xValue);
            }
        }

        area *= deltaX / (3.0 * numIntervals);

        // System.out.println(area);
        return area;
    }
}





