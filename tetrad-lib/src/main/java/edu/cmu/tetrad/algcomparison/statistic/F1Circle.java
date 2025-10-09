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

import edu.cmu.tetrad.algcomparison.statistic.utils.CircleConfusion;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;

import java.io.Serial;

/**
 * Calculates the F1 statistic for circles.
 * <p>
 * <a href="https://en.wikipedia.org/wiki/F1_score">...</a>
 * <p>
 * We use what's on this page called the "traditional" F1 statistic. If the true contains X*-oY and estimated graph
 * either does not contain an edge from X to Y or else does not contain a tail at X for an edge from X to Y, one false
 * positive is counted. Similarly for false negatives *
 */
public class F1Circle implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs a new instance of the algorithm.
     */
    public F1Circle() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAbbreviation() {
        return "F1Circle";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "F1 statistic for circles";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getValue(Graph trueDag, Graph trueGraph, Graph estGraph, DataModel dataModel, Parameters parameters) {
        CircleConfusion circleConfusion = new CircleConfusion(trueGraph, estGraph);
        int circleTp = circleConfusion.getTp();
        int circleFp = circleConfusion.getFp();
        int circleFn = circleConfusion.getFn();
        double circlePrecision = circleTp / (double) (circleTp + circleFp);
        double circleRecall = circleTp / (double) (circleTp + circleFn);
        return 2 * (circlePrecision * circleRecall) / (circlePrecision + circleRecall);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getNormValue(double value) {
        return value;
    }
}

