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

package edu.pitt.dbmi.data.reader.util;

import java.util.Arrays;

/**
 * Dec 11, 2018 2:08:19 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 * @version $Id: $Id
 */
public final class Columns {

    private Columns() {
    }

    /**
     * Sort the columns in ascending order.
     *
     * @param columns the columns to sort.
     * @return the sorted columns.
     */
    public static int[] sortNew(int[] columns) {
        int size = (columns == null) ? 0 : columns.length;
        if (size > 0) {
            int[] copiedColumns = new int[size];
            System.arraycopy(columns, 0, copiedColumns, 0, size);
            Arrays.sort(copiedColumns);

            return copiedColumns;
        } else {
            return new int[0];
        }
    }

    /**
     * Keep all the columns that are between 1 and numOfCols, inclusive.
     *
     * @param numberOfColumns the number of columns.
     * @param columns         the columns to keep.
     * @return the valid columns.
     */
    public static int[] extractValidColumnNumbers(int numberOfColumns, int[] columns) {
        return (columns == null || columns.length == 0)
                ? new int[0]
                : Arrays.stream(columns)
                .filter(e -> e > 0 && e <= numberOfColumns)
                .sorted()
                .distinct()
                .toArray();
    }

}

