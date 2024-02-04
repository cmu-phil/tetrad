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

import edu.pitt.dbmi.data.reader.DiscreteDataColumn;
import edu.pitt.dbmi.data.reader.MixedData;

/**
 * Dec 29, 2018 5:16:48 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class MixedTabularData implements MixedData {

    private final int numOfRows;
    private final DiscreteDataColumn[] dataColumns;
    private final double[][] continuousData;
    private final int[][] discreteData;

    /**
     * Constructor.
     *
     * @param numOfRows      the number of rows.
     * @param dataColumns    the data columns.
     * @param continuousData the continuous data.
     * @param discreteData   the discrete data.
     */
    public MixedTabularData(int numOfRows, DiscreteDataColumn[] dataColumns, double[][] continuousData, int[][] discreteData) {
        this.numOfRows = numOfRows;
        this.dataColumns = dataColumns;
        this.continuousData = continuousData;
        this.discreteData = discreteData;
    }

    /**
     * Get the number of rows.
     *
     * @return the number of rows.
     */
    @Override
    public int getNumOfRows() {
        return this.numOfRows;
    }

    /**
     * Get the data columns.
     *
     * @return the data columns.
     */
    @Override
    public DiscreteDataColumn[] getDataColumns() {
        return this.dataColumns;
    }

    /**
     * Get the continuous data.
     *
     * @return the continuous data.
     */
    @Override
    public double[][] getContinuousData() {
        return this.continuousData;
    }

    /**
     * Get the discrete data.
     *
     * @return the discrete data.
     */
    @Override
    public int[][] getDiscreteData() {
        return this.discreteData;
    }

}
