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
 *
 * Dec 29, 2018 12:44:43 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class TabularDataColumn implements DataColumn {

    private final String name;
    private final int columnNumber;
    private final boolean generated;
    private boolean discrete;

    public TabularDataColumn(String name, int columnNumber, boolean generated) {
        this.name = name;
        this.columnNumber = columnNumber;
        this.generated = generated;
    }

    public TabularDataColumn(String name, int columnNumber, boolean generated, boolean discrete) {
        this.name = name;
        this.columnNumber = columnNumber;
        this.generated = generated;
        this.discrete = discrete;
    }

    @Override
    public String toString() {
        return "TabularDataColumn{" + "name=" + name + ", columnNumber=" + columnNumber + ", generated=" + generated + ", discrete=" + discrete + '}';
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public int getColumnNumber() {
        return columnNumber;
    }

    @Override
    public boolean isGenerated() {
        return generated;
    }

    @Override
    public boolean isDiscrete() {
        return discrete;
    }

    @Override
    public void setDiscrete(boolean discrete) {
        this.discrete = discrete;
    }

}
