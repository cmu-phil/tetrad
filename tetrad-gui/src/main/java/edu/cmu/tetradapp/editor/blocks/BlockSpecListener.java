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

package edu.cmu.tetradapp.editor.blocks;

import edu.cmu.tetrad.search.blocks.BlockSpec;

/**
 * Represents a functional interface for listening to events where a BlockSpec becomes available.
 * <p>
 * It can be utilized to handle scenarios where a BlockSpec is produced as a result of an operation, such as a search
 * completion or an application of some functionality.
 */
@FunctionalInterface
public interface BlockSpecListener {

    /**
     * Fired when a BlockSpec is available (from Search done or Apply).
     *
     * @param spec The BlockSpec that is available.
     */
    void onBlockSpec(BlockSpec spec);
}
