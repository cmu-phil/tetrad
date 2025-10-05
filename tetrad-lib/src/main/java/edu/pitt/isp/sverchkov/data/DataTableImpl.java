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
 * @version $Id: $Id
 */
public class DataTableImpl<N, V> implements DataTable<N, V> {

    private final List<N> variables;
    private final List<List<V>> rows;

    /**
     * <p>Constructor for DataTableImpl.</p>
     *
     * @param vars The names of the variables in the table
     */
    public DataTableImpl(List<? extends N> vars) {
        this.variables = Collections.unmodifiableList(new ArrayList<>(vars));
        this.rows = new ArrayList<>();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<N> variables() {
        return this.variables;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int columnCount() {
        return this.variables.size();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int rowCount() {
        return this.rows.size();
    }

    /**
     * {@inheritDoc}
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

    /**
     * {@inheritDoc}
     */
    @Override
    public Iterator<List<V>> iterator() {
        return Collections.unmodifiableList(this.rows).listIterator();
    }

}


