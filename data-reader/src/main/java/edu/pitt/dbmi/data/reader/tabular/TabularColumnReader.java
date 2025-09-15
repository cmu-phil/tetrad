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

package edu.pitt.dbmi.data.reader.tabular;

import edu.pitt.dbmi.data.reader.DataColumn;
import edu.pitt.dbmi.data.reader.DataReader;

import java.io.IOException;
import java.util.Set;

/**
 * Dec 28, 2018 2:44:19 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 * @version $Id: $Id
 */
public interface TabularColumnReader extends DataReader {

    /**
     * Read in the data columns.
     *
     * @param isDiscrete whether the data is discrete.
     * @return the data columns.
     * @throws java.io.IOException if an I/O error occurs.
     */
    DataColumn[] readInDataColumns(boolean isDiscrete) throws IOException;

    /**
     * Read in the data columns.
     *
     * @param namesOfColumnsToExclude the names of columns to exclude.
     * @param isDiscrete              whether the data is discrete.
     * @return the data columns.
     * @throws java.io.IOException if an I/O error occurs.
     */
    DataColumn[] readInDataColumns(Set<String> namesOfColumnsToExclude, boolean isDiscrete) throws IOException;

    /**
     * Read in the data columns.
     *
     * @param columnsToExclude the columns to exclude.
     * @param isDiscrete       whether the data is discrete.
     * @return the data columns.
     * @throws java.io.IOException if an I/O error occurs.
     */
    DataColumn[] readInDataColumns(int[] columnsToExclude, boolean isDiscrete) throws IOException;

    /**
     * Generate the data columns.
     *
     * @param isDiscrete       whether the data is discrete.
     * @param columnsToExclude the columns to exclude.
     * @return the data columns.
     * @throws java.io.IOException if an I/O error occurs.
     */
    DataColumn[] generateColumns(int[] columnsToExclude, boolean isDiscrete) throws IOException;

}

