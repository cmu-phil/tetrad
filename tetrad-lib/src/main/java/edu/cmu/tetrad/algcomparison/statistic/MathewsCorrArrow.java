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

import edu.cmu.tetrad.algcomparison.statistic.utils.ArrowConfusion;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;
import org.apache.commons.math3.util.FastMath;

import java.io.Serial;

/**
 * Calculates the Matthew's correlation coefficient for adjacencies. See this page in Wikipedia:
 * <p>
 * <a href="https://en.wikipedia.org/wiki/Matthews_correlation_coefficient">...</a>
 * <p>
 * We calculate the correlation directly from the confusion matrix.
 * <p>
 * if the true contains X*-&gt;Y and estimated graph either does not contain an edge from X to Y or else does not
 * contain an arrowhead at X for an edge from X to Y, one false positive is counted. Similarly, for false negatives
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class MathewsCorrArrow implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs a new instance of the statistic.
     */
    public MathewsCorrArrow() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAbbreviation() {
        return "McArrow";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Matthew's correlation coefficient for arrowheads";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getValue(Graph trueDag, Graph trueGraph, Graph estGraph, DataModel dataModel, Parameters parameters) {
        ArrowConfusion adjConfusion = new ArrowConfusion(trueGraph, estGraph);
        int arrowsTp = adjConfusion.getTp();
        int arrowsFp = adjConfusion.getFp();
        int arrowsFn = adjConfusion.getFn();
        int arrowsTn = adjConfusion.getTn();
        return mcc(arrowsTp, arrowsFp, arrowsTn, arrowsFn);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getNormValue(double value) {
        return 0.5 + 0.5 * value;
    }

    private double mcc(double adjTp, double adjFp, double adjTn, double adjFn) {
        double a = adjTp * adjTn - adjFp * adjFn;
        double b = (adjTp + adjFp) * (adjTp + adjFn) * (adjTn + adjFp) * (adjTn + adjFn);

        if (b == 0) b = 1;

        return a / FastMath.sqrt(b);
    }
}

