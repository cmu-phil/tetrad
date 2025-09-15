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

package edu.cmu.tetrad.algcomparison.score;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.blocks.BlockSpec;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradSerializable;

import java.io.Serial;
import java.util.List;

/**
 * Represents an interface for scoring data models using predefined block structures. This interface extends
 * {@code ScoreWrapper} and {@code TetradSerializable}, providing additional functionality for handling block-based
 * scoring.
 */
public interface BlockScoreWrapper extends ScoreWrapper, TetradSerializable {
    /**
     * Constant <code>serialVersionUID=23L</code>
     */
    @Serial
    long serialVersionUID = 23L;

    /**
     * Sets the block specification to be used by this implementation.
     *
     * @param blockSpec The block specification, which contains details about the dataset, blocks, and block nodes used
     *                  in the algorithm.
     */
    void setBlockSpec(BlockSpec blockSpec);

    /**
     * Computes and returns a score for the given data model and parameters.
     *
     * @param model      The data model containing the data to be scored.
     * @param parameters The parameters to be used for score computation.
     * @return A Score object representing the computed score.
     */
    Score getScore(DataModel model, Parameters parameters);

    /**
     * Returns a short of this independence test.
     *
     * @return This description.
     */
    String getDescription();

    /**
     * Returns the data type that the search requires, whether continuous, discrete, or mixed.
     *
     * @return This type.
     */
    DataType getDataType();

    /**
     * Returns the parameters that this search uses.
     *
     * @return A list for String names of parameters.
     */
    List<String> getParameters();

    /**
     * Returns the variable with the given name.
     *
     * @param name the name.
     * @return the variable.
     */
    Node getVariable(String name);

}

