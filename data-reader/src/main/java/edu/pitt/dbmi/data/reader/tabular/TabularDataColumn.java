/*
 * Copyright (C) 2018 University of Pittsburgh.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package edu.pitt.dbmi.data.reader.tabular;

import edu.pitt.dbmi.data.reader.DataColumn;

/**
 * Dec 29, 2018 12:44:43 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 * @version $Id: $Id
 */
public class TabularDataColumn implements DataColumn {

    private final String name;
    private final int columnNumber;
    private final boolean generated;
    private boolean discrete;

    /**
     * Constructor.
     *
     * @param name         the name of the column.
     * @param columnNumber the column number.
     * @param generated    whether the column is generated.
     */
    public TabularDataColumn(String name, int columnNumber, boolean generated) {
        this.name = name;
        this.columnNumber = columnNumber;
        this.generated = generated;
    }

    /**
     * Constructor.
     *
     * @param name         the name of the column.
     * @param columnNumber the column number.
     * @param generated    whether the column is generated.
     * @param discrete     whether the column is discrete.
     */
    public TabularDataColumn(String name, int columnNumber, boolean generated, boolean discrete) {
        this.name = name;
        this.columnNumber = columnNumber;
        this.generated = generated;
        this.discrete = discrete;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns a string representation of the object.
     */
    @Override
    public String toString() {
        return "TabularDataColumn{" + "name=" + this.name + ", columnNumber=" + this.columnNumber + ", generated=" + this.generated + ", discrete=" + this.discrete + '}';
    }

    /**
     * {@inheritDoc}
     * <p>
     * Get the name of the column.
     */
    @Override
    public String getName() {
        return this.name;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Get the column number.
     */
    @Override
    public int getColumnNumber() {
        return this.columnNumber;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Is the column generated?
     */
    @Override
    public boolean isGenerated() {
        return this.generated;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Is the column discrete?
     */
    @Override
    public boolean isDiscrete() {
        return this.discrete;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Set the discrete status of the column.
     */
    @Override
    public void setDiscrete(boolean discrete) {
        this.discrete = discrete;
    }

}
