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

import edu.pitt.dbmi.data.reader.Data;
import edu.pitt.dbmi.data.reader.DataColumn;
import edu.pitt.dbmi.data.reader.DatasetReader;
import edu.pitt.dbmi.data.reader.metadata.Metadata;
import java.io.IOException;

/**
 *
 * Nov 5, 2018 2:51:35 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public interface TabularDataReader extends DatasetReader {

    /**
     * Analyze the column data to determine if it contains discrete data based
     * on the number of categories. If the number of categories of a column is
     * equal to or less than the given number of categories, it will be
     * considered to have discrete data. Else, it is considered to have
     * continuous data.
     *
     * @param dataColumns
     * @param numberOfCategories maximum number of categories to be considered
     * discrete
     * @param hasHeader
     * @throws IOException
     */
    public void determineDiscreteDataColumns(DataColumn[] dataColumns, int numberOfCategories, boolean hasHeader) throws IOException;

    public Data read(DataColumn[] dataColumns, boolean hasHeader) throws IOException;

    public Data read(DataColumn[] dataColumns, boolean hasHeader, Metadata metadata) throws IOException;

}
