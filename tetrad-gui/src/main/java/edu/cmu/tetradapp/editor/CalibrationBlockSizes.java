///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2026 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
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

package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Node;

import java.util.Arrays;
import java.util.List;

/**
 * Parameter block sizes per variable for the score and test families the two calibration calculators serve. A
 * pair (x, y) costs size[x] * size[y] degrees of freedom in both the score's penalty and the test's null, so this
 * is the one input both {@link PenaltyDiscountCalculatorPanel} and {@link AlphaCalculatorPanel} need from the
 * data, and it is kept here so that the two cannot disagree about it.
 *
 * <p>The Basis Function family is not here: its sizes are read off a
 * {@link edu.cmu.tetrad.search.score.BasisFunctionBicScore} constructed on the data, which the callers do under a
 * {@link edu.cmu.tetradapp.util.WatchedProcess} because the embedding is not free.</p>
 *
 * @author josephramsey
 */
final class CalibrationBlockSizes {

    private CalibrationBlockSizes() {
    }

    /**
     * One parameter per variable: SEM BIC and Fisher z.
     *
     * @param numVariables p.
     * @return All ones.
     */
    static int[] allOnes(int numVariables) {
        int[] sizes = new int[numVariables];
        Arrays.fill(sizes, 1);
        return sizes;
    }

    /**
     * One parameter per continuous variable and categories minus one per discrete variable: the Degenerate
     * Gaussian and conditional Gaussian scores and tests, and the discrete BIC score.
     *
     * @param dataSet The data.
     * @return The sizes, in variable order.
     */
    static int[] categoriesMinusOne(DataSet dataSet) {
        List<Node> variables = dataSet.getVariables();
        int[] sizes = new int[variables.size()];

        for (int i = 0; i < sizes.length; i++) {
            sizes[i] = variables.get(i) instanceof DiscreteVariable d
                    ? Math.max(1, d.getNumCategories() - 1) : 1;
        }

        return sizes;
    }
}
