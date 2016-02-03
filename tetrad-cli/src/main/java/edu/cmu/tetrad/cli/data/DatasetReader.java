/*
 * Copyright (C) 2016 University of Pittsburgh.
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
package edu.cmu.tetrad.cli.data;

import edu.cmu.tetrad.data.DataSet;
import java.io.IOException;
import java.util.Set;

/**
 * Dataset reader.
 *
 * Feb 3, 2016 12:33:56 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public interface DatasetReader {

    public DataSet readInContinuousData() throws IOException;

    public DataSet readInContinuousData(Set<String> excludedVariables) throws IOException;

    /**
     * Counts the number of lines containing data. Ignores empty lines.
     *
     * @return the number of lines containing data
     * @throws IOException whenever an I/O exception of some sort has occurred
     */
    public int countNumberOfLines() throws IOException;

    /**
     * Counts the number of columns on the first line. The rest of the lines
     * will be not be counted.
     *
     * @return the number of columns on the first line
     * @throws IOException whenever an I/O exception of some sort has occurred
     */
    public int countNumberOfColumns() throws IOException;

}
