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

package edu.pitt.dbmi.data.reader.preview;

import java.nio.file.Path;

/**
 * Feb 20, 2017 2:09:44 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 * @version $Id: $Id
 */
public abstract class AbstractDataPreviewer {

    /**
     * The line feed character.
     */
    protected static final byte LINE_FEED = '\n';

    /**
     * The carriage return character.
     */
    protected static final byte CARRIAGE_RETURN = '\r';

    /**
     * The ellipsis character.
     */
    protected static final String ELLIPSIS = "...";

    /**
     * The data file.
     */
    protected final Path dataFile;

    /**
     * Constructor.
     *
     * @param dataFile The data file.
     */
    public AbstractDataPreviewer(Path dataFile) {
        this.dataFile = dataFile;
    }

    /**
     * Check the number of characters parameter.
     *
     * @param numOfCharacters The number of characters.
     */
    protected void checkCharacterNumberParameter(int numOfCharacters) {
        if (numOfCharacters < 0) {
            throw new IllegalArgumentException("Parameter numOfCharacters must be positive integer.");
        }
    }

    /**
     * Check the line number parameters.
     *
     * @param fromLine The starting line number.
     * @param toLine   The ending line number.
     */
    protected void checkLineNumberParameter(int fromLine, int toLine) {
        if (fromLine < 0) {
            throw new IllegalArgumentException("Parameter fromLine must be positive integer.");
        }
        if (toLine < 0) {
            throw new IllegalArgumentException("Parameter toLine must be positive integer.");
        }
        if (toLine < fromLine) {
            throw new IllegalArgumentException("Parameter toLine must be greater than or equal to fromLine.");
        }
    }

}

