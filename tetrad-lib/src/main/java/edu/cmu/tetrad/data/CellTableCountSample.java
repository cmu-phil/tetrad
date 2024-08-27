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

import edu.cmu.tetrad.util.MultiDimIntTable;
import org.jetbrains.annotations.NotNull;

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
 * @see edu.cmu.tetrad.util.MultiDimIntTable
 */
public final class CellTableCountSample implements CellTable {

    private final int[] _dims;
    /**
     * The table of cell counts.
     */
    private MultiDimIntTable table;
    /**
     * The value used in the data for missing values.
     */
    private int missingValue = -99;

    /**
     * Constructs a new cell table using the given array for dimensions, initializing all cells in the table to zero.
     *
     * @param dims        The dimensions of the table.
     * @param dataSet     The data set to be used in the table.
     * @param testIndices The indices of the variables to be used in the table.
     */
    public CellTableCountSample(int[] dims, DataSet dataSet, int[] testIndices) {
        this(dataSet, testIndices);
    }

    /**
     * Constructs a new cell table using the given array for dimensions, initializing all cells in the table to zero.
     *
     * @param dims         the dimensions of the variables in the data.
     * @param missingValue the value used in the data for missing values.
     * @param dataSet      the data set to be used in the table.
     * @param testIndices  the indices of the variables to be used in the table.
     */
    public CellTableCountSample(DataSet dataSet, int[] testIndices) {
        _dims = selectDims(getDiscreteVariables(dataSet, testIndices));
        this.table = new MultiDimIntTable(_dims);
        setMissingValue(missingValue);
        countTable(dataSet, testIndices);
    }

    private static @NotNull List<DiscreteVariable> getDiscreteVariables(DataSet dataSet, int[] testIndices) {
        List<DiscreteVariable> vars = new ArrayList<>();

        for (int i : testIndices) {
            vars.add((DiscreteVariable) dataSet.getVariable(i));
        }

        return vars;
    }

    private int @NotNull [] selectDims(List<DiscreteVariable> vars) {
        int[] _dims = new int[vars.size()];

        for (DiscreteVariable variable : vars) {
            _dims[vars.indexOf(variable)] = variable.getNumCategories();
        }

        return _dims;
    }

    /**
     * Adds the given data set to the table, using the given indices to specify the variables to be used in the table.
     *
     * @param dataSet the data set to be used in the table.
     * @param indices the indices of the variables to be used in the table.
     */
    private void countTable(DataSet dataSet, int[] indices) {
        int[] dims = new int[indices.length];

        for (int i = 0; i < indices.length; i++) {
            DiscreteVariable variable = (DiscreteVariable) dataSet.getVariable(indices[i]);
            dims[i] = variable.getNumCategories();
        }

        this.table = new MultiDimIntTable(dims);

        int[] coords = new int[indices.length];

        POINTS:
        for (int i = 0; i < dataSet.getNumRows(); i++) {
            for (int j = 0; j < indices.length; j++) {
                try {
                    coords[j] = dataSet.getInt(i, indices[j]);
                } catch (Exception e) {
                    coords[j] = dataSet.getInt(i, j);
                }

                if (coords[j] == getMissingValue()) {
                    continue POINTS;
                }
            }

            this.table.increment(coords, 1);
        }
    }

    /**
     * Returns the dimensions of the given variable.
     *
     * @param varIndex the index of the variable in question.
     * @return the dimension of the variable.
     */
    @Override
    public int getDimension(int varIndex) {
        return _dims[varIndex];
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
    @Override
    public int calcMargin(int[] coords) {
        int[] coordCopy = internalCoordCopy(coords);

        int sum = 0;
        int i = -1;

        while (++i < coordCopy.length) {
            if (coordCopy[i] == -1) {
                for (int j = 0; j < this.table.getDimension(i); j++) {
                    coordCopy[i] = j;
                    sum += calcMargin(coordCopy);
                }

                coordCopy[i] = -1;
                return sum;
            }
        }

        return this.table.getValue(coordCopy);
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
    @Override
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
    @Override
    public int getValue(int[] coords) {
        return this.table.getValue(coords);
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





