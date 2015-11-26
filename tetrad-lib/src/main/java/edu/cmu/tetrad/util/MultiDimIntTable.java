///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.util;

import java.util.HashMap;
import java.util.Map;

/**
 * <p>Stores a table of cells with int values of arbitrary dimension. The
 * dimensionality of the table is set in the reset() method; if a dimensionality
 * is set in the constructor, it will be passed to the reset() method. Every
 * time the dimensionality is changed, the table is reset to zero throughout. If
 * the dimensionality is set to null, the table cannot be used until a non-null
 * dimensionality is set.</p>
 *
 * @author Joseph Ramsey
 */
public class MultiDimIntTable {

    /**
     * A single-dimension array containing all of the cells of the table. Must
     * be at least long enough to contain data for each cell allowed for by the
     * given dimension array--in other words, the length must be greater than or
     * equal to dims[0] & dims[1] ... * dims[dims.length - 1].
     */
    private Map<Integer, Long> cells;

    /**
     * The number of cells in the table. (May be different from the length of
     * cells[].
     */
    private int numCells;

    /**
     * An array whose length is the number of dimensions of the cell and whose
     * contents, for each value dims[i], are the numbers of values for each
     * i'th dimension. Each of these dimensions must be an integer greater than
     * zero.
     */
    private int[] dims;

    /**
     * Constructs a new multidimensional table of integer cells, with the given
     * (fixed) dimensions. Each dimension must be an integer greater than zero.
     *
     * @param dims An int[] array of length > 0, each element of which specifies
     *             the number of values of that dimension (> 0).
     */
    public MultiDimIntTable(int[] dims) {
        reset(dims);
    }

    /**
     * @return the index in the cells array for the cell at the given
     * coordinates.
     *
     * @param coords The coordinates of the cell. Each value must be less
     *               than the number of possible value for the corresponding
     *               dimension in the table. (Enforced.)
     * @return the row in the table for the given node and combination of parent
     *         values.
     */
    public int getCellIndex(int[] coords) {
        int cellIndex = 0;

        for (int i = 0; i < dims.length; i++) {
            cellIndex *= dims[i];
            cellIndex += coords[i];
        }

        return cellIndex;
    }

    /**
     * @return an array containing the coordinates of the cell at the given
     * index in the cells array.
     *
     * @param cellIndex an <code>int</code> value
     * @return the array representing the combination of parent values for this
     *         row.
     */
    @SuppressWarnings({"SameParameterValue"})
    public int[] getCoordinates(int cellIndex) {
        int[] coords = new int[this.dims.length];

        for (int i = this.dims.length - 1; i >= 0; i--) {
            coords[i] = cellIndex % this.dims[i];
            cellIndex /= this.dims[i];
        }

        return coords;
    }

    /**
     * Increments the value at the given coordinates by the specified amount,
     * returning the new value.
     *
     * @param coords The coordinates of the table cell to update.
     * @param value  The amount by which the table cell at these coordinates
     *               should be incremented (an integer).
     * @return the new value at that table cell.
     */
    public long increment(int[] coords, int value) {
        int cellIndex = getCellIndex(coords);

        if (!cells.containsKey(cellIndex)) {
            cells.put(cellIndex, 0L);
        }

        cells.put(cellIndex, cells.get(cellIndex) + value);
        return cells.get(cellIndex);
    }

    /**
     * Sets the value at the given coordinates to the given value,
     * returning the new value.
     *
     * @param coords The coordinates of the table cell to update.
     * @param value  The amount by which the table cell at these coordinates
     *               should be incremented (an integer).
     * @return the new value at that table cell.
     */
    @SuppressWarnings({"UnusedDeclaration"})
    public long setValue(int[] coords, int value) {
        int cellIndex = getCellIndex(coords);
        cells.put(cellIndex, (long) value);
        return cells.get(cellIndex);
    }

    /**
     * @return the value at the given coordinates.
     *
     * @param coords The coordinates of the table cell to update.
     * @return the new value at that table cell.
     */
    public long getValue(int[] coords) {
        int cellIndex = getCellIndex(coords);

        if (cells == null || !cells.containsKey(cellIndex)) {
            return 0L;
        } else {
            return cells.get(cellIndex);
        }
    }

    /**
     * @return the number of cells.
     *
     * @return this number.
     */
    public int getNumCells() {
        return this.numCells;
    }

    /**
     * @return the dimension array.
     *
     * @return this number.
     */
    @SuppressWarnings({"UnusedDeclaration"})
    public int[] getDimensions() {
        return this.dims;
    }

    /**
     * Describe <code>getDimension</code> method here.
     *
     * @param var an <code>int</code> value
     * @return an <code>int</code> value
     */
    public int getDimension(int var) {
        return this.dims[var];
    }

    /**
     * @return the number of dimensions.
     *
     * @return an <code>int</code> value
     */
    public int getNumDimensions() {
        return this.dims.length;
    }

    /**
     * Resets the table, allowing a different dimensionality. All cells are
     * reset to zero. The underlying data array is reused if possible.
     *
     * @param dims an <code>int[]</code> value
     */
    public final void reset(int[] dims) {
        if (dims == null) {
            cells = null;
        } else {
            if (dims.length < 1) {
                throw new IllegalArgumentException(
                        "Table must have at " + "least one dimension.");
            }

            for (int i = 0; i < dims.length; i++) {
                if (dims[i] < 1) {
                    throw new IllegalArgumentException("Dimension " + i +
                            " has fewer than " + "one values: " + dims[i]);
                }
            }

            // Calculate length of cells[] array.
            this.numCells = 1;

            for (int dim : dims) {
                this.numCells *= dim;
            }

            // Construct (or reset) cells array.
            cells = new HashMap<Integer, Long>();

            // Store the dimensions, making a copy for security.
            this.dims = new int[dims.length];

            System.arraycopy(dims, 0, this.dims, 0, dims.length);
        }
    }

    public int getDims(int varIndex) {
        return dims[varIndex];
    }
}



