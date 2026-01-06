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

import edu.cmu.tetrad.algcomparison.statistic.utils.TailConfusion;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;

import java.io.Serial;

/**
 * Calculates the F1 statistic for tails.
 * <p>
 * <a href="https://en.wikipedia.org/wiki/F1_score">...</a>
 * <p>
 * We use what's on this page called the "traditional" F1 statistic. A true positive arrow is counted for X*-&gt;Y in
 * the estimated graph if X is not adjacent to Y or X--Y or X&lt;--Y. Similarly for false negatives. *
 */
public class F1Tail implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs a new instance of the algorithm.
     */
    public F1Tail() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAbbreviation() {
        return "F1Tail";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "F1 statistic for tails";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getValue(Graph trueDag, Graph trueGraph, Graph estGraph, DataModel dataModel, Parameters parameters) {
        TailConfusion tailConfusion = new TailConfusion(trueGraph, estGraph);
        int arrowTp = tailConfusion.getArrowsTp();
        int arrowFp = tailConfusion.getArrowsFp();
        int arrowFn = tailConfusion.getArrowsFn();
        int twoCycleTp = tailConfusion.getTwoCycleTp();
        int twoCycleFp = tailConfusion.getTwoCycleFp();
        int twoCycleFn = tailConfusion.getTwoCycleFn();

        double arrowsCyclesPrecision = (arrowTp + twoCycleTp) / (double) ((arrowTp + twoCycleTp) + (arrowFp + twoCycleFp));
        double arrowsCyclesRecall = (arrowTp + twoCycleTp) / (double) ((arrowTp + twoCycleTp) + (arrowFn + twoCycleFn));
        return 2 * (arrowsCyclesPrecision * arrowsCyclesRecall) / (arrowsCyclesPrecision + arrowsCyclesRecall);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getNormValue(double value) {
        return value;
    }
}

