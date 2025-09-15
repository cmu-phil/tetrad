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

import edu.pitt.dbmi.data.reader.DiscreteData;
import edu.pitt.dbmi.data.reader.DiscreteDataColumn;

/**
 * Dec 29, 2018 5:17:39 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 * @version $Id: $Id
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
     * {@inheritDoc}
     * <p>
     * Gets the data columns.
     */
    @Override
    public DiscreteDataColumn[] getDataColumns() {
        return this.dataColumns;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Gets the data.
     */
    @Override
    public int[][] getData() {
        return this.data;
    }

}

