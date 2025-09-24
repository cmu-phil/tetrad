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

package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.TakesScoreWrapper;
import edu.cmu.tetradapp.ui.model.AlgorithmModel;

public final class AlgorithmFilters {
    private AlgorithmFilters() {
    }

    /**
     * Block-capable if it can accept a wrapper for a block-based test and/or a block-based score.
     */
    public static boolean supportsBlocks(AlgorithmModel model) {
        var algo = model.getAlgorithm();

        // If it's a class descriptor, check with reflection
        try {
            Class<?> clazz = algo.clazz();  // or however AlgorithmModel exposes the underlying Class
            return TakesIndependenceWrapper.class.isAssignableFrom(clazz)
                   || TakesScoreWrapper.class.isAssignableFrom(clazz);
        } catch (Exception ignored) {
            return false;
        }

//        return (algo.clazz() instanceof TakesIndependenceWrapper) || (algo instanceof UsesScoreWrapper);
    }
}
