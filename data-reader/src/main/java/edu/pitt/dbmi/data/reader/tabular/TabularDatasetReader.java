/*
 * Copyright (C) 2019 University of Pittsburgh.
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

import edu.pitt.dbmi.data.reader.Data;
import edu.pitt.dbmi.data.reader.DatasetReader;

import java.io.IOException;
import java.util.Set;

/**
 * Dec 14, 2018 10:58:01 AM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 * @version $Id: $Id
 */
public interface TabularDatasetReader extends DatasetReader {

    /**
     * Read in the data.
     *
     * @return The data.
     * @throws java.io.IOException If an I/O error occurs.
     */
    Data readInData() throws IOException;

    /**
     * Read in the data.
     *
     * @param namesOfColumnsToExclude the names of columns to exclude.
     * @return The data.
     * @throws java.io.IOException If an I/O error occurs.
     */
    Data readInData(Set<String> namesOfColumnsToExclude) throws IOException;

    /**
     * Read in the data.
     *
     * @param columnsToExclude the columns to exclude.
     * @return The data.
     * @throws java.io.IOException If an I/O error occurs.
     */
    Data readInData(int[] columnsToExclude) throws IOException;

    /**
     * Set whether the data has a header.
     *
     * @param hasHeader whether the data has a header.
     */
    void setHasHeader(boolean hasHeader);

}
