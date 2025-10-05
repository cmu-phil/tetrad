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

/**
 * Feb 17, 2017 1:49:26 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 * @version $Id: $Id
 */
public enum MessageType {

    /**
     * Error in file input/output operation.
     */
    FILE_IO_ERROR,

    /**
     * Missing value in file.
     */
    FILE_MISSING_VALUE,

    /**
     * Invalid number in file.
     */
    FILE_INVALID_NUMBER,

    /**
     * Excess or insufficient data in file.
     */
    FILE_EXCESS_DATA,

    /**
     * Excess or insufficient data in file.
     */
    FILE_INSUFFICIENT_DATA,

    /**
     * File summary.
     */
    FILE_SUMMARY

}

