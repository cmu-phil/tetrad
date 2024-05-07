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
package edu.pitt.dbmi.data.reader;

import java.nio.file.Path;

/**
 * Dec 7, 2018 3:43:12 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 * @version $Id: $Id
 */
public abstract class DatasetFileReader extends DataFileReader implements DatasetReader {

    /**
     * The missing data marker.
     */
    protected String missingDataMarker;

    /**
     * Constructor.
     *
     * @param dataFile  the data file
     * @param delimiter the delimiter
     */
    public DatasetFileReader(Path dataFile, Delimiter delimiter) {
        super(dataFile, delimiter);

        this.missingDataMarker = "";
    }

    /**
     * Sets the missing data marker.
     *
     * @param missingDataMarker the missing data marker to be set
     */
    @Override
    public void setMissingDataMarker(String missingDataMarker) {
        this.missingDataMarker = (missingDataMarker == null)
                ? ""
                : missingDataMarker.trim();
    }

}
