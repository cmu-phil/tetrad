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

package edu.pitt.dbmi.data.reader.metadata;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Dec 20, 2018 11:42:01 AM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 * @version $Id: $Id
 */
public class InterventionalColumn {

    @JsonProperty("value")
    private ColumnMetadata valueColumn;

    @JsonProperty("status")
    private ColumnMetadata statusColumn;

    /**
     * Default constructor.
     */
    public InterventionalColumn() {
    }

    /**
     * Constructor.
     *
     * @param valueColumn  The value column.
     * @param statusColumn The status column.
     */
    public InterventionalColumn(ColumnMetadata valueColumn, ColumnMetadata statusColumn) {
        this.valueColumn = valueColumn;
        this.statusColumn = statusColumn;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns a string representation of this object.
     */
    @Override
    public String toString() {
        return "InterventionalColumn{" + "valueColumn=" + this.valueColumn + ", statusColumn=" + this.statusColumn + '}';
    }

    /**
     * Returns the value column.
     *
     * @return the value column.
     */
    public ColumnMetadata getValueColumn() {
        return this.valueColumn;
    }

    /**
     * Sets the value column.
     *
     * @param valueColumn the value column.
     */
    public void setValueColumn(ColumnMetadata valueColumn) {
        this.valueColumn = valueColumn;
    }

    /**
     * Returns the status column.
     *
     * @return the status column.
     */
    public ColumnMetadata getStatusColumn() {
        return this.statusColumn;
    }

    /**
     * Sets the status column.
     *
     * @param statusColumn the status column.
     */
    public void setStatusColumn(ColumnMetadata statusColumn) {
        this.statusColumn = statusColumn;
    }

}

