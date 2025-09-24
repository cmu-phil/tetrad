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

import edu.pitt.dbmi.data.reader.ContinuousData;
import edu.pitt.dbmi.data.reader.DataColumn;

/**
 * Dec 29, 2018 5:18:32 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 * @version $Id: $Id
 */
public class ContinuousTabularData implements ContinuousData {

    private final DataColumn[] dataColumns;
    private final double[][] data;

    /**
     * Constructor.
     *
     * @param dataColumns The data columns.
     * @param data        The data.
     */
    public ContinuousTabularData(DataColumn[] dataColumns, double[][] data) {
        this.dataColumns = dataColumns;
        this.data = data;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Get the data columns.
     */
    @Override
    public DataColumn[] getDataColumns() {
        return this.dataColumns;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Get the data.
     */
    @Override
    public double[][] getData() {
        return this.data;
    }

}

