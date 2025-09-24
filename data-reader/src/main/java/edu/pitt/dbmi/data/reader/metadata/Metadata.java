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

import java.util.LinkedList;
import java.util.List;

/**
 * Dec 18, 2018 11:21:23 AM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 * @version $Id: $Id
 */
public class Metadata {

    @JsonProperty("domains")
    private List<ColumnMetadata> domainColumnns = new LinkedList<>();

    @JsonProperty("interventions")
    private List<InterventionalColumn> interventionalColumns = new LinkedList<>();

    /**
     * Default constructor.
     */
    public Metadata() {
    }

    /**
     * Constructor.
     *
     * @param domainColumnns        The domain columns.
     * @param interventionalColumns The interventional columns.
     */
    public Metadata(List<ColumnMetadata> domainColumnns, List<InterventionalColumn> interventionalColumns) {
        if (domainColumnns != null) {
            this.domainColumnns.addAll(domainColumnns);
        }
        if (interventionalColumns != null) {
            this.interventionalColumns.addAll(interventionalColumns);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns a string representation of this object.
     */
    @Override
    public String toString() {
        return "Metadata{" + "domainColumnns=" + this.domainColumnns + ", interventionalColumns=" + this.interventionalColumns + '}';
    }

    /**
     * Returns the domain columns.
     *
     * @return the domain columns.
     */
    public List<ColumnMetadata> getDomainColumnns() {
        return this.domainColumnns;
    }

    /**
     * Sets the domain columns.
     *
     * @param domainColumnns the domain columns.
     */
    public void setDomainColumnns(List<ColumnMetadata> domainColumnns) {
        this.domainColumnns = domainColumnns;
    }

    /**
     * Returns the interventional columns.
     *
     * @return the interventional columns.
     */
    public List<InterventionalColumn> getInterventionalColumns() {
        return this.interventionalColumns;
    }

    /**
     * Sets the interventional columns.
     *
     * @param interventionalColumns the interventional columns.
     */
    public void setInterventionalColumns(List<InterventionalColumn> interventionalColumns) {
        this.interventionalColumns = interventionalColumns;
    }
}

