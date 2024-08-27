///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
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

package edu.cmu.tetrad.data;

import edu.cmu.tetrad.search.utils.AdLeafTree;
import edu.cmu.tetrad.util.MultiDimIntTable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


/**
 * Stores a cell count table of arbitrary dimension. Provides methods for incrementing particular cells and for
 * calculating marginals.
 * <p>
 * Immutable.
 *
 * @author josephramsey
 * @version $Id: $Id
 * @see MultiDimIntTable
 */
public final class CellTableAdTree implements ICellTable {

    private final AdLeafTree adTree;
    private final int[] dims;
    private final List<List<Integer>> cellLeaves;
    /**
     * The value used in the data for missing values.
     */
    private int missingValue = -99;

    /**
     * Constructs a new cell table using the given array for dimensions, initializing all cells in the table to zero.
     *
     * @param dims         the dimensions of the table.
     * @param missingValue the value used in the data for missing values.
     * @param dataSet      the data set to be used in the table.
     * @param testIndices  the indices of the variables to be used in the table.
     */
    public CellTableAdTree(int[] dims, int missingValue, DataSet dataSet, int[] testIndices) {
        if (dims == null) {
            throw new NullPointerException("The dimensions array is null.");
        }

        this.adTree = new AdLeafTree(dataSet);
        this.dims = Arrays.copyOf(dims, dims.length);

        setMissingValue(missingValue);

        List<DiscreteVariable> vars = new ArrayList<>();

        for (int i : testIndices) {
            vars.add((DiscreteVariable) dataSet.getVariable(i));
        }

        cellLeaves = adTree.getCellLeaves(vars);
    }

    /**
     * Returns the dimensions of the given variable.
     *
     * @param varIndex the index of the variable in question.
     * @return the dimension of the variable.
     */
    public int getDimension(int varIndex) {
        return dims[varIndex];
    }

    /**
     * Calculates a marginal sum for the cell table. The variables over which marginal sums should be taken are
     * indicated by placing "-1's" in the appropriate positions in the coordinate argument. For instance, to find the
     * margin for v0 = 1, v1 = 3, and v3 = 2, where the marginal sum ranges over all values of v2 and v4, the array [1,
     * 3, -1, 2, -1] should be used.
     *
     * @param coords an array of the sort described above.
     * @return the marginal sum specified.
     */
    public int calcMargin(int[] coords) {
        int[] coordCopy = internalCoordCopy(coords);

        int sum = 0;
        int i = -1;

        while (++i < coordCopy.length) {
            if (coordCopy[i] == -1) {
                for (int j = 0; j < dims[i]; j++) {
                    coordCopy[i] = j;
                    sum += calcMargin(coordCopy);
                }

                coordCopy[i] = -1;
                return sum;
            }
        }

        return getValue(coords);
    }

    /**
     * An alternative way to specify a marginal calculation. In this case, coords specifies a particular cell in the
     * table, and varIndices is an array containing the indices of the variables over which the margin sum should be
     * calculated. The sum is over the cell specified by 'coord' and all the cells which differ from that cell in any of
     * the specified coordinates.
     *
     * @param coords     an <code>int[]</code> value
     * @param marginVars an <code>int[]</code> value
     * @return an <code>int</code> value
     */
    public int calcMargin(int[] coords, int[] marginVars) {
        int[] coordCopy = internalCoordCopy(coords);

        for (int marginVar : marginVars) {
            coordCopy[marginVar] = -1;
        }

        return calcMargin(coordCopy);
    }

    /**
     * Returns the value of the cell specified by the given coordinates.
     *
     * @param coords the coordinates of the cell.
     * @return the value of the cell.
     */
    public int getValue(int[] coords) {
        return cellLeaves.get(adTree.getCellIndex(coords)).size();
    }

    /**
     * Makes a copy of the coordinate array so that the original is not messed up.
     */
    private int[] internalCoordCopy(int[] coords) {
        return Arrays.copyOf(coords, coords.length);
    }

    /**
     * Returns the missing value marker.
     *
     * @return the missing value marker.
     */
    private int getMissingValue() {
        return this.missingValue;
    }

    /**
     * Sets the missing value marker.
     *
     * @param missingValue the missing value marker.
     */
    private void setMissingValue(int missingValue) {
        this.missingValue = missingValue;
    }
}





