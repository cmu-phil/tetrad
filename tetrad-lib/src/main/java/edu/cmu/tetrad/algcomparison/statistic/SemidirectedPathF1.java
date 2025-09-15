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
 * Calculates the F1 statistic for adjacencies. See
 * <p>
 * https://en.wikipedia.org/wiki/F1_score
 * <p>
 * We use what's on this page called the "traditional" F1 statistic.
 *
 * @author Joseh Ramsey
 * @version $Id: $Id
 */
public class SemidirectedPathF1 implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs the statistic.
     */
    public SemidirectedPathF1() {
    }

    /**
     * Retrieves the abbreviation for the statistic.
     *
     * @return The abbreviation for the statistic.
     */
    @Override
    public String getAbbreviation() {
        return "Semidirected-F1";
    }

    /**
     * Returns a short one-line description of this statistic. This will be printed at the beginning of the report.
     *
     * @return A string representing the description of this statistic.
     */
    @Override
    public String getDescription() {
        return "F1 statistic for semidirected paths comparing the estimated graph to the true graph";
    }

    /**
     * Calculates the F1 statistic for adjacencies. See
     * <p>
     * https://en.wikipedia.org/wiki/F1_score
     * <p>
     * We use what's on this page called the "traditional" F1 statistic.
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel, Parameters parameters) {
        double precision = new SemidirectedPrecision().getValue(trueGraph, estGraph, dataModel, new Parameters());
        double recall = new SemidirectedRecall().getValue(trueGraph, estGraph, dataModel, new Parameters());
        return 2 * (precision * recall) / (precision + recall);
    }

    /**
     * Retrieves the normalized value of the statistic.
     *
     * @param value The value of the statistic.
     * @return The normalized value of the statistic.
     */
    @Override
    public double getNormValue(double value) {
        return value;
    }
}

