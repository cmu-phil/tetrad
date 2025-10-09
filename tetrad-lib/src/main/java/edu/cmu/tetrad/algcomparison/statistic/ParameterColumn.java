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

package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;

import java.io.Serial;

/**
 * Adds a column to the output table in which values for the given parameter are listed. The parameter must have
 * numerical values, and these will be represented as continuous.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class ParameterColumn implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The name of the parameter to list. If this parameter does not exist, '*' is output.
     */
    private final String parameter;

    /**
     * <p>Constructor for ParameterColumn.</p>
     *
     * @param parameter The name of the parameter to list. If this parameter does not exist, '*' is output.
     */
    public ParameterColumn(String parameter) {
        this.parameter = parameter;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAbbreviation() {
        return this.parameter;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Extra column for " + this.parameter;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getValue(Graph trueDag, Graph trueGraph, Graph estGraph, DataModel dataModel, Parameters parameters) {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getNormValue(double value) {
        throw new UnsupportedOperationException();
    }
}

