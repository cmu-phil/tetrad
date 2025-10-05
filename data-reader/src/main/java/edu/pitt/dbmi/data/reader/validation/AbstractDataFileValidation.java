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

package edu.pitt.dbmi.data.reader.validation;

import edu.pitt.dbmi.data.reader.DataFileReader;
import edu.pitt.dbmi.data.reader.Delimiter;

import java.nio.file.Path;

/**
 * Dec 12, 2018 12:14:52 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 * @version $Id: $Id
 */
public abstract class AbstractDataFileValidation extends DataFileReader implements Validation {

    /**
     * The maximum number of messages to validate.
     */
    protected int maxNumOfMsg;

    /**
     * Constructor.
     *
     * @param dataFile  the data file
     * @param delimiter the delimiter
     */
    public AbstractDataFileValidation(Path dataFile, Delimiter delimiter) {
        super(dataFile, delimiter);
        this.maxNumOfMsg = Integer.MAX_VALUE;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Set the maximum number of messages to validate.
     */
    @Override
    public void setMaximumNumberOfMessages(int maxNumOfMsg) {
        this.maxNumOfMsg = maxNumOfMsg;
    }

}

