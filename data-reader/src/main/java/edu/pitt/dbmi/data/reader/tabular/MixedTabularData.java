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

package edu.pitt.dbmi.data.reader.tabular;

import edu.pitt.dbmi.data.reader.DiscreteDataColumn;
import edu.pitt.dbmi.data.reader.MixedData;

/**
 * Dec 29, 2018 5:16:48 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 * @version $Id: $Id
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
     * {@inheritDoc}
     * <p>
     * Get the number of rows.
     */
    @Override
    public int getNumOfRows() {
        return this.numOfRows;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Get the data columns.
     */
    @Override
    public DiscreteDataColumn[] getDataColumns() {
        return this.dataColumns;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Get the continuous data.
     */
    @Override
    public double[][] getContinuousData() {
        return this.continuousData;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Get the discrete data.
     */
    @Override
    public int[][] getDiscreteData() {
        return this.discreteData;
    }

}

