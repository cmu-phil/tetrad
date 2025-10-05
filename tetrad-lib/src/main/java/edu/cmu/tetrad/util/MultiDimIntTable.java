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

package edu.cmu.tetrad.util;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Stores a table of cells with int values of arbitrary dimension. The dimensionality of the table is set in the
 * constructor. The table is initialized with all cells set to zero.
 * <p>
 * Immutable.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class MultiDimIntTable {

    /**
     * A single-dimension array containing all the cells of the table. Must be at least long enough to contain data for
     * each cell allowed for by the given dimension array--in other words, the length must be greater than or equal to
     * dims[0] & dims[1] ... * dims[dims.length - 1].
     */
    private final Map<Integer, Integer> cells;
    /**
     * An array whose length is the number of dimensions of the cell and whose contents, for each value dims[i], are the
     * numbers of values for each i'th dimension. Each of these dimensions must be an integer greater than zero.
     */
    private final int[] dims;
    /**
     * The number of cells in the table. (This may be different from the length of cells[].)
     */
    private int numCells;

    /**
     * Constructs a new multidimensional table of integer cells, with the given (fixed) dimensions. Each dimension must
     * be an integer greater than zero.
     *
     * @param dims An int[] array of length &gt; 0, each element of which specifies the number of values of that
     *             dimension (&gt; 0).
     */
    public MultiDimIntTable(int[] dims) {
        if (dims.length < 1) {
            throw new IllegalArgumentException(
                    "Table must have at " + "least one dimension.");
        }

        // Calculate the number of cells in the table.
        this.numCells = 1;

        for (int dim : dims) {
            this.numCells *= dim;
        }

        // Construct cells array.
        this.cells = new HashMap<>();

        // Store the dimensions, making a copy for security.
        this.dims = Arrays.copyOf(dims, dims.length);
    }

    /**
     * Returns the index in the table for the cell with the given coordinates.
     *
     * @param coords The coordinates of the cell. Each value must be less than the number of possible values for the
     *               corresponding dimension in the table. (Enforced.)
     * @return the row in the table for the given node and combination of parent values.
     */
    public int getCellIndex(int[] coords) {
        int cellIndex = 0;

        for (int i = 0; i < coords.length; i++) {
            if (i < dims.length) {
                cellIndex *= this.dims[i];
                cellIndex += coords[i];
            }
        }

        return cellIndex;
    }

    /**
     * Returns the array representing the combination of parent values for this row.
     *
     * @param cellIndex the index of the cell.
     * @return the array representing the combination of parent values for this row.
     */
    @SuppressWarnings("SameParameterValue")
    public int[] getCoordinates(int cellIndex) {
        int[] coords = new int[this.dims.length];

        for (int i = this.dims.length - 1; i >= 0; i--) {
            coords[i] = cellIndex % this.dims[i];
            cellIndex /= this.dims[i];
        }

        return coords;
    }

    /**
     * Increments the value at the given coordinates by the specified amount, returning the new value.
     *
     * @param coords The coordinates of the table cell to update.
     * @param value  The amount by which the table cell at these coordinates should be incremented (an integer).
     * @return the new value at that table cell.
     */
    public int increment(int[] coords, int value) {
        int cellIndex = getCellIndex(coords);

        // Increment the value at the cell. Make sure that if the updated value is 0, the cell is removed from the map
        // if it is present, to save space.
        int updatedValue = this.cells.getOrDefault(cellIndex, 0) + value;

        if (updatedValue == 0) {
            this.cells.remove(cellIndex);
        } else {
            this.cells.put(cellIndex, updatedValue);
        }

        return updatedValue;
    }

    /**
     * Returns the value of the cell at the given coordinates.
     *
     * @param coords The coordinates of the table cell to update.
     * @return the new value at that table cell.
     */
    public int getValue(int[] coords) {
        int cellIndex = getCellIndex(coords);
        Integer l = this.cells.get(cellIndex);
        return Objects.requireNonNullElse(l, 0);
    }

    /**
     * Returns the number of cells in the table.
     *
     * @return this number.
     */
    public int getNumCells() {
        return this.numCells;
    }

    /**
     * Returns the dimension of the given variable.
     *
     * @param var The index of the variable.
     * @return Its dimension.
     */
    public int getDimension(int var) {
        return this.dims[var];
    }

    /**
     * Returns the number of dimensions in the table.
     *
     * @return The number of dimensions.
     */
    public int getNumDimensions() {
        return this.dims.length;
    }

    /**
     * Returns the dimension of the given variable.
     *
     * @param varIndex the index of the variable.
     * @return the dimension of the variable.
     */
    public int getDim(int varIndex) {
        return this.dims[varIndex];
    }
}




