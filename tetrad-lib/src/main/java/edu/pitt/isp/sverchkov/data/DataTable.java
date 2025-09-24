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

package edu.pitt.isp.sverchkov.data;

import java.util.List;

/**
 * Data table implementation.
 *
 * @param <N> Type of variable names
 * @param <V> Type of variable values
 * @author YUS24
 * @version $Id: $Id
 */
public interface DataTable<N, V> extends Iterable<List<V>> {

    /**
     * <p>variables.</p>
     *
     * @return The names of the variables in the table
     */
    List<N> variables();

    /**
     * <p>columnCount.</p>
     *
     * @return The number of columns in the table
     */
    int columnCount();

    /**
     * <p>rowCount.</p>
     *
     * @return The number of rows in the table
     */
    int rowCount();

    /**
     * <p>addRow.</p>
     *
     * @param row The index of the row to retrieve
     */
    void addRow(List<? extends V> row);
}


