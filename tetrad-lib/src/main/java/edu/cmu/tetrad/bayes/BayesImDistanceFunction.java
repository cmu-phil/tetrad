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

package edu.cmu.tetrad.bayes;

import edu.cmu.tetrad.graph.Graph;
import org.apache.commons.math3.util.FastMath;

/**
 * <p>Provides a static method for finding the Euclidean distance between a pair
 * of BayesIm's.  That is, it computes the square root of the sum of the squares of the differences between
 * corresponding parameters of the two Bayes nets.&gt; 0 <p>The BayesPm's should be equal in the sense of the "equals"
 * method of that class.&gt; 0
 *
 * @author Frank Wimberly
 */
final class BayesImDistanceFunction {

    /**
     * The static distance method's arguments are the two BayesIM's whose BayesPm's are "equal".
     *
     * @param firstBn  a {@link edu.cmu.tetrad.bayes.BayesIm} object
     * @param secondBn a {@link edu.cmu.tetrad.bayes.BayesIm} object
     * @return a double
     */
    public static double distance(BayesIm firstBn, BayesIm secondBn) {
        if (!firstBn.getBayesPm().equals(secondBn.getBayesPm())) {
            throw new IllegalArgumentException("BayesPms must be equal.");
        }

        Graph graph = firstBn.getBayesPm().getDag();
        int numNodes = graph.getNumNodes();

        double sum = 0.0;

        for (int i = 0; i < numNodes; i++) {
            int numRows = firstBn.getNumRows(i);

            for (int j = 0; j < numRows; j++) {
                int numCols = firstBn.getNumColumns(i);

                for (int k = 0; k < numCols; k++) {
                    double diff = firstBn.getProbability(i, j, k) -
                                  secondBn.getProbability(i, j, k);
                    sum += diff * diff;
                }
            }
        }

        return FastMath.sqrt(sum);
    }
}






