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
package edu.pitt.dbmi.data.reader.tabular;

import edu.pitt.dbmi.data.reader.DataColumn;
import edu.pitt.dbmi.data.reader.DataReader;

import java.io.IOException;
import java.util.Set;

/**
 * Dec 28, 2018 2:44:19 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public interface TabularColumnReader extends DataReader {

    /**
     * Read in the data columns.
     *
     * @param isDiscrete whether the data is discrete.
     * @return the data columns.
     * @throws IOException if an I/O error occurs.
     */
    DataColumn[] readInDataColumns(boolean isDiscrete) throws IOException;

    /**
     * Read in the data columns.
     *
     * @param namesOfColumnsToExclude the names of columns to exclude.
     * @param isDiscrete              whether the data is discrete.
     * @return the data columns.
     * @throws IOException if an I/O error occurs.
     */
    DataColumn[] readInDataColumns(Set<String> namesOfColumnsToExclude, boolean isDiscrete) throws IOException;

    /**
     * Read in the data columns.
     *
     * @param columnsToExclude the columns to exclude.
     * @param isDiscrete       whether the data is discrete.
     * @return the data columns.
     * @throws IOException if an I/O error occurs.
     */
    DataColumn[] readInDataColumns(int[] columnsToExclude, boolean isDiscrete) throws IOException;

    /**
     * Generate the data columns.
     *
     * @param isDiscrete       whether the data is discrete.
     * @param columnsToExclude the columns to exclude.
     * @return the data columns.
     * @throws IOException if an I/O error occurs.
     */
    DataColumn[] generateColumns(int[] columnsToExclude, boolean isDiscrete) throws IOException;

}
