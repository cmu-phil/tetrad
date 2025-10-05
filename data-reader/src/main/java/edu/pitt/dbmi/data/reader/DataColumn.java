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

/**
 * Dec 8, 2018 4:14:30 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 * @version $Id: $Id
 */
public interface DataColumn {

    /**
     * Get the column's name.
     *
     * @return the column's name.
     */
    String getName();

    /**
     * Get the column's number.
     *
     * @return the column's number.
     */
    int getColumnNumber();

    /**
     * True if this column was not read in from source such as file.
     *
     * @return true if this column was not read in from source such as file.
     */
    boolean isGenerated();

    /**
     * True if the datatype is discrete.
     *
     * @return true if the datatype is discrete.
     */
    boolean isDiscrete();

    /**
     * Set true for discrete datatype.
     *
     * @param discrete true for discrete datatype.
     */
    void setDiscrete(boolean discrete);

}

