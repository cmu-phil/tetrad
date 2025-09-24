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

package edu.cmu.tetrad.study.gene.tetrad.gene.algorithm.ideker;


/**
 * <p>LTestPredictorSearch class.</p>
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class LTestPredictorSearch {

    /**
     * Private constructor.
     */
    private LTestPredictorSearch() {
    }

    /**
     * <p>main.</p>
     *
     * @param argv an array of {@link java.lang.String} objects
     */
    public static void main(String[] argv) {

        final int ngenes = 4;
        //int[][] exp = new int[4][4];
        String[] names = {"a0", "a1", "a2", "a3"};

        int[][] exp = {{1, 1, 1, 0}, {-1, 1, 0, 1}, {1, -1, 0, 0},
                {1, 1, -1, 1}, {1, 1, 1, 2}};

        ItkPredictorSearch ips = new ItkPredictorSearch(ngenes, exp, names);

        for (int gene = 0; gene < ngenes; gene++) {
            ips.predictor(gene);
        }
    }
}







