/// ////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
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

