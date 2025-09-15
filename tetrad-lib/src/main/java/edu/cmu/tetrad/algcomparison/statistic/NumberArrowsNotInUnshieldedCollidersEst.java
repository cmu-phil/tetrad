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
import org.apache.commons.math3.util.FastMath;

import java.io.Serial;

/**
 * Represents the NumberEdgesEst statistic, which calculates the number of arrows not in unshielded colliders in the
 * estimated graph.
 */
public class NumberArrowsNotInUnshieldedCollidersEst implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs the statistic.
     */
    public NumberArrowsNotInUnshieldedCollidersEst() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAbbreviation() {
        return "#ArrowsNotInUCEst";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Number of Arrows Not in Unshielded Colliders in the Estimated Graph";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel, Parameters parameters) {
        double n1 = new NumberEdgesInUnshieldedCollidersEst().getValue(trueGraph, estGraph, dataModel, new Parameters());
        double n2 = new NumberArrowsEst().getValue(trueGraph, estGraph, dataModel, new Parameters());
        return n2 - n1;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getNormValue(double value) {
        return 1.0 - FastMath.tanh(value / 10.);
    }
}

