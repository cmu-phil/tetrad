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

package edu.cmu.tetradapp.model.block;

import edu.cmu.tetrad.algcomparison.score.BlocksBicScore;
import edu.cmu.tetrad.algcomparison.score.ScoreWrapper;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.search.blocks.BlockSpec;
import edu.cmu.tetrad.util.Parameters;

/**
 * Factory to create a block score from a BlockSpec.
 */
public final class BlockScoreFactory {

    /**
     * Private constructor to prevent instantiation.
     */
    private BlockScoreFactory() {
    }

    /**
     * Constructs a ScoreWrapper using the provided data, block specification, and parameters.
     *
     * @param data   the data model containing the data to be analyzed
     * @param spec   the block specification defining the structure of the blocks
     * @param params the parameters controlling the behavior of the score calculation
     * @return a ScoreWrapper instance configured with the provided data, block specification, and parameters
     */
    public static ScoreWrapper build(DataModel data, BlockSpec spec, Parameters params) {
        BlocksBicScore blocksBicScore = new BlocksBicScore();
        blocksBicScore.setBlockSpec(spec);
        return blocksBicScore; // minimal; adapt as needed
    }
}
