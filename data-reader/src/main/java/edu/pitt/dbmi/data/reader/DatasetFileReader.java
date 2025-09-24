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

