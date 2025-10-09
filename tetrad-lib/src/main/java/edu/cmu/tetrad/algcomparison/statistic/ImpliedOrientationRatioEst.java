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
 * The Implied Arrow Orientation Ratio Est statistic calculates the ratio of the number of implied arrows to the number
 * of arrows in unshielded colliders in the estimated graph. Implied Arrow Orientation Ratio in the Estimated Graph =
 * (numImpliedArrows - numArrowsInUnshieldedColliders) / numArrowsInUnshieldedColliders. It implements the Statistic
 * interface.
 */
public class ImpliedOrientationRatioEst implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs the statistic.
     */
    public ImpliedOrientationRatioEst() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAbbreviation() {
        return "IOR";
    }

    /**
     * A {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Implied Arrow Orientation Ratio in the Estimated Graph (# implied arrow and tail orientions / # edges in unshielded colliders)";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getValue(Graph trueDag, Graph trueGraph, Graph estGraph, DataModel dataModel, Parameters parameters) {
        double n1 = new NumberEdgesInUnshieldedCollidersEst().getValue(trueDag, trueGraph, estGraph, dataModel, new Parameters());
        double n2 = new NumberArrowsEst().getValue(trueDag, trueGraph, estGraph, dataModel, new Parameters());
        double n3 = new NumberTailsEst().getValue(trueDag, trueGraph, estGraph, dataModel, new Parameters());
        return (n2 + n3 - n1);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getNormValue(double value) {
        return 1 - value;
    }
}

