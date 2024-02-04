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

import edu.pitt.dbmi.data.reader.DiscreteData;
import edu.pitt.dbmi.data.reader.DiscreteDataColumn;

/**
 * Dec 29, 2018 5:17:39 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class VerticalDiscreteTabularData implements DiscreteData {

    private final DiscreteDataColumn[] dataColumns;
    private final int[][] data;

    /**
     * Constructor.
     *
     * @param dataColumns the data columns.
     * @param data        the data.
     */
    public VerticalDiscreteTabularData(DiscreteDataColumn[] dataColumns, int[][] data) {
        this.dataColumns = dataColumns;
        this.data = data;
    }

    /**
     * Gets the data columns.
     *
     * @return the data columns.
     */
    @Override
    public DiscreteDataColumn[] getDataColumns() {
        return this.dataColumns;
    }

    /**
     * Gets the data.
     *
     * @return the data.
     */
    @Override
    public int[][] getData() {
        return this.data;
    }

}
