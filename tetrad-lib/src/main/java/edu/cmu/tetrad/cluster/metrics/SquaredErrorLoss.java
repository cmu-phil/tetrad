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

package edu.cmu.tetrad.cluster.metrics;

import edu.cmu.tetrad.util.Vector;

/**
 * Euclidean dissimilarity metric--i.e., the sum of the differences in corresponding variable values.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class SquaredErrorLoss implements Dissimilarity {

    /**
     * Calculates the squared error dissimilarity between two vectors using the Euclidean dissimilarity metric.
     */
    public SquaredErrorLoss() {
    }

    /**
     * Calculates the dissimilarity between two vectors using the Euclidean dissimilarity metric.
     *
     * @param v1 the first vector
     * @param v2 the second vector
     * @return the dissimilarity between the two vectors
     * @throws IllegalArgumentException if the vectors are not the same length
     */
    public double dissimilarity(Vector v1, Vector v2) {
        if (v1.size() != v2.size()) {
            throw new IllegalArgumentException("Vectors not the same length.");
        }

        double sum = 0.0;

        for (int j = 0; j < v1.size(); j++) {
            double diff = v1.get(j) - v2.get(j);
            sum += diff * diff;
        }

        return sum;
    }
}



