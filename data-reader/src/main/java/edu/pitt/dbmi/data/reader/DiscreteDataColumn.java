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

import java.util.List;

/**
 * Dec 10, 2018 3:24:22 AM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 * @version $Id: $Id
 */
public interface DiscreteDataColumn {

    /**
     * Get the encode value.
     *
     * @param value the value.
     * @return the encode value.
     */
    Integer getEncodeValue(String value);

    /**
     * Recategorize the data.
     */
    void recategorize();

    /**
     * Get the categories.
     *
     * @return the categories.
     */
    List<String> getCategories();

    /**
     * Get the data column.
     *
     * @return the data column.
     */
    DataColumn getDataColumn();

    /**
     * Set the value.
     *
     * @param value the value.
     */
    void setValue(String value);

}

