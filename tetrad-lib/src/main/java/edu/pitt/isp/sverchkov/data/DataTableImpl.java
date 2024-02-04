///////////////////////////////////////////////////////////////////////////////
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Data table implementation.
 *
 * @param <N> Type of variable names
 * @param <V> Type of variable values
 * @author YUS24
 */
public class DataTableImpl<N, V> implements DataTable<N, V> {

    private final List<N> variables;
    private final List<List<V>> rows;

    /**
     * @param vars The names of the variables in the table
     */
    public DataTableImpl(List<? extends N> vars) {
        this.variables = Collections.unmodifiableList(new ArrayList<>(vars));
        this.rows = new ArrayList<>();
    }

    /**
     * @return The variables in the table
     */
    @Override
    public List<N> variables() {
        return this.variables;
    }

    /**
     * @return The number of columns in the table
     */
    @Override
    public int columnCount() {
        return this.variables.size();
    }

    /**
     * @return The number of rows in the table
     */
    @Override
    public int rowCount() {
        return this.rows.size();
    }

    /**
     * @param row The index of the row to retrieve
     */
    @Override
    public void addRow(List<? extends V> row) {
        int
                m = row.size();
        int w = columnCount();

        if (m != w)
            throw new IllegalArgumentException("Tried to insert a row of length " + m + " into a table of width " + w + ".");

        this.rows.add(Collections.unmodifiableList(new ArrayList<>(row)));
    }

    @Override
    public Iterator<List<V>> iterator() {
        return Collections.unmodifiableList(this.rows).listIterator();
    }

}

