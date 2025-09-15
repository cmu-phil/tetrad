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

package edu.cmu.tetrad.data;

/**
 * Represents a cell table that stores the values of the cells in a table of arbitrary dimension, for use, e.g., in the
 * context of a contingency table--e.g. for chi-square or g-square tests.
 *
 * @author josephramsey
 */
public interface CellTable {

    /**
     * Returns the dimension of the specified variable in the cell table.
     *
     * @param varIndex the index of the variable.
     * @return the dimension of the variable.
     */
    int getDimension(int varIndex);

    /**
     * Calculates the marginal sum for the cell table based on the given coordinates.
     *
     * @param coords an array of coordinates where -1 indicates the variables over which marginal sums should be taken.
     * @return the marginal sum specified.
     */
    int calcMargin(int[] coords);

    /**
     * Calculates the marginal sum for the cell table based on the given coordinates and margin variables.
     *
     * @param coords     the array of coordinates where -1 indicates the variables over which marginal sums should be
     *                   taken.
     * @param marginVars the array of indices of the margin variables.
     * @return the marginal sum specified.
     */
    int calcMargin(int[] coords, int[] marginVars);

    /**
     * Returns the value of the cell specified by the given coordinates.
     *
     * @param coords the coordinates of the cell.
     * @return the value of the cell.
     */
    int getValue(int[] coords);
}

