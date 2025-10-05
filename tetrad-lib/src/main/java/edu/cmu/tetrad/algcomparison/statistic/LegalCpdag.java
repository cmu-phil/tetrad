/// ////////////////////////////////////////////////////////////////////////////
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
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;

import java.io.Serial;

/**
 * The LegalCpdag class implements the Statistic interface and provides methods to evaluate whether an estimated graph
 * is a Legal CPDAG. A Legal CPDAG is determined based on the structure of the estimated graph.
 */
public class LegalCpdag implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs a new instance of the LegalCpdag class, representing a statistic used to evaluate whether an estimated
     * graph is a Legal CPDAG.
     */
    public LegalCpdag() {
    }

    /**
     * Returns the abbreviation "CPDAG", representing a Legal CPDAG.
     *
     * @return a string abbreviation "CPDAG"
     */
    @Override
    public String getAbbreviation() {
        return "CPDAG";
    }

    /**
     * Provides a description of the output indicating whether the estimated graph is a Legal CPDAG.
     *
     * @return a string description stating "1 if the estimated graph is Legal CPDAG, 0 if not"
     */
    @Override
    public String getDescription() {
        return "1 if the estimated graph is Legal CPDAG, 0 if not";
    }

    /**
     * Calculates the value indicating whether the estimated graph is a Legal CPDAG (1.0) or not (0.0).
     *
     * @param trueGraph  the true graph, not used in this implementation
     * @param estGraph   the estimated graph to be checked for being a Legal CPDAG
     * @param dataModel  the data model, not used in this implementation
     * @param parameters the parameters, not used in this implementation
     * @return 1.0 if the estimated graph is a Legal CPDAG, 0.0 otherwise
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel, Parameters parameters) {
        if (estGraph.paths().isLegalCpdag()) {
            return 1.0;
        } else {
            return 0.0;
        }
    }

    /**
     * Returns the normalized value for the given input. This method currently returns the input value as it is, without
     * any modifications.
     *
     * @param value the input value to be normalized
     * @return the normalized value, which is the same as the input value
     */
    @Override
    public double getNormValue(double value) {
        return value;
    }
}

