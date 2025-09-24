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

import java.io.Serial;

/**
 * Nov 6, 2018 2:26:59 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 * @version $Id: $Id
 */
public class DataReaderException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1123054334542973950L;

    /**
     * Creates a new instance of <code>DataReaderException</code> without detail message.
     */
    public DataReaderException() {
    }

    /**
     * Constructs an instance of <code>DataReaderException</code> with the specified detail message.
     *
     * @param message the detail message.
     */
    public DataReaderException(String message) {
        super(message);
    }

    /**
     * Constructs an instance of <code>DataReaderException</code> with the specified detail message and cause.
     *
     * @param message the detail message.
     * @param cause   the cause (which is saved for later retrieval by the {@link #getCause()} method).
     */
    public DataReaderException(String message, Throwable cause) {
        super(message, cause);
    }

}

