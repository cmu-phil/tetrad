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

import java.io.Serial;

/**
 * The 2-cycle precision. This counts 2-cycles manually, wherever they occur in the graphs. The true positives are the
 * number of 2-cycles in both the true and estimated graphs. Thus, if the true does not contains X-&gt;Y,Y-&gt;X and
 * estimated graph does contain it, one false positive is counted.
 *
 * @author josephramsey, rubens (November 2016)
 * @version $Id: $Id
 */
public class TwoCycleFalseNegative implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * This class represents a statistic that calculates the 2-cycle false negative. It counts 2-cycles manually,
     * wherever they occur in the graphs. The true positives are the number of 2-cycles in both the true and estimated
     * graphs. Thus, if the true contains X-&gt;Y,Y-&lt;X and estimated graph does not contain it, one false negative is
     * counted.
     */
    public TwoCycleFalseNegative() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAbbreviation() {
        return "2CFN";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "2-cycle false negative";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel, Parameters parameters) {
        ArrowConfusion adjConfusion = new ArrowConfusion(trueGraph, estGraph);
        return adjConfusion.getTwoCycleFn();

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getNormValue(double value) {
        return value;
    }
}

