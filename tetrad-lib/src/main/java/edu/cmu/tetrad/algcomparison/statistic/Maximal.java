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
 * Checks whether a PAG is maximal.
 */
public class Maximal implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * <p>Constructor for LegalPag.</p>
     */
    public Maximal() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAbbreviation() {
        return "Maximal";
    }

    /**
     * Returns a short one-line description of this statistic. This will be printed at the beginning of the report.
     *
     * @return The description of the statistic.
     */
    @Override
    public String getDescription() {
        return "1 if the estimated graph is maximal, 0 if not";
    }

    /**
     * Checks whether a PAG is maximal.
     */
    @Override
    public double getValue(Graph trueDag, Graph trueGraph, Graph estGraph, DataModel dataModel, Parameters parameters) {
        boolean maximal = estGraph.paths().isMaximal();

        if (!maximal) {
            throw new IllegalArgumentException("Maximality condition fails in Maximal statistic.");
        }

        return maximal ? 1.0 : 0.0;
    }

    /**
     * Returns the normalized value of the given statistic value.
     *
     * @param value The value of the statistic.
     * @return The normalized value of the statistic, between 0 and 1.
     */
    @Override
    public double getNormValue(double value) {
        return value;
    }
}

