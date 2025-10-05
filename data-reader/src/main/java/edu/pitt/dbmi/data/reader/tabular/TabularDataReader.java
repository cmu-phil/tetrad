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

import edu.pitt.dbmi.data.reader.Data;
import edu.pitt.dbmi.data.reader.DataColumn;
import edu.pitt.dbmi.data.reader.DatasetReader;
import edu.pitt.dbmi.data.reader.metadata.Metadata;

import java.io.IOException;

/**
 * Nov 5, 2018 2:51:35 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 * @version $Id: $Id
 */
public interface TabularDataReader extends DatasetReader {

    /**
     * Analyze the column data to determine if it contains discrete data based on the number of categories. If the
     * number of categories of a column is equal to or less than the given number of categories, it will be considered
     * to have discrete data. Else, it is considered to have continuous data.
     *
     * @param dataColumns        the data columns
     * @param numberOfCategories maximum number of categories to be considered discrete
     * @param hasHeader          whether the data has a header
     * @throws java.io.IOException if an I/O error occurs
     */
    void determineDiscreteDataColumns(DataColumn[] dataColumns, int numberOfCategories, boolean hasHeader) throws IOException;

    /**
     * Read the data.
     *
     * @param dataColumns the data columns
     * @param hasHeader   whether the data has a header
     * @return the data
     * @throws java.io.IOException if an I/O error occurs
     */
    Data read(DataColumn[] dataColumns, boolean hasHeader) throws IOException;

    /**
     * Read the data.
     *
     * @param dataColumns the data columns
     * @param hasHeader   whether the data has a header
     * @param metadata    the metadata
     * @return the data
     * @throws java.io.IOException if an I/O error occurs
     */
    Data read(DataColumn[] dataColumns, boolean hasHeader, Metadata metadata) throws IOException;

}

