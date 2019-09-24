/*
 * Copyright (C) 2018 University of Pittsburgh.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package edu.pitt.dbmi.data.reader.util;

import java.util.Arrays;

/**
 *
 * Dec 11, 2018 2:08:19 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public final class Columns {

    private Columns() {
    }

    public static final int[] sortNew(int[] columns) {
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
     * @param numberOfColumns
     * @param columns
     * @return
     */
    public static final int[] extractValidColumnNumbers(int numberOfColumns, int[] columns) {
        return (columns == null || columns.length == 0)
                ? new int[0]
                : Arrays.stream(columns)
                        .filter(e -> e > 0 && e <= numberOfColumns)
                        .sorted()
                        .distinct()
                        .toArray();
    }

}
