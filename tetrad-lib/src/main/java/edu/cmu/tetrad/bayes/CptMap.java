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

import edu.cmu.tetrad.util.TetradSerializable;

/**
 * An interface representing a map of probabilities or counts for nodes in a Bayesian network. Implementations of this
 * interface should provide methods to get the probability or count for a node at a given row and column, as well as
 * methods to retrieve the number of rows and columns in the map.
 * <p>
 * This interface extends the TetradSerializable interface, indicating that implementations should be serializable and
 * follow certain guidelines for compatibility across different versions of Tetrad.
 *
 * @author josephramsey
 * @see CptMapProbs
 * @see CptMapCounts
 */
public interface CptMap extends TetradSerializable {

    /**
     * Retrieves the value at the specified row and column in the CptMap.
     *
     * @param row    the row index of the value to retrieve.
     * @param column the column index of the value to retrieve.
     * @return the value at the specified row and column in the CptMap.
     */
    double get(int row, int column);

    /**
     * Retrieves the number of rows in the CptMap.
     *
     * @return the number of rows in the CptMap.
     */
    int getNumRows();

    /**
     * Retrieves the number of columns in the CptMap.
     *
     * @return the number of columns in the CptMap.
     */
    int getNumColumns();
}

