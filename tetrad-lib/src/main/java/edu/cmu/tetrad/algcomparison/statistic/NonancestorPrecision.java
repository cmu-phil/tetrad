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
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Parameters;

import java.io.Serial;
import java.util.List;

/**
 * Number of NOT X~~>Y in true graph for which also NOT X~~>Y in estimated graph.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class NonancestorPrecision implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs a new instance of the statistic.
     */
    public NonancestorPrecision() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAbbreviation() {
        return "Nonanc-Prec";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Proportion of NOT X~~>Y in the estimated graph for which also NOT X~~>Y in true graph";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel, Parameters parameters) {
        int tp = 0;
        int fp = 0;

        List<Node> nodes = estGraph.getNodes();

        for (Node x : nodes) {
            for (Node y : nodes) {
//                if (x == y) continue;
                if (!estGraph.paths().isAncestorOf(x, y)) {
                    if (!trueGraph.paths().isAncestorOf(x, y)) {
                        tp++;
                    } else {
                        fp++;
                    }
                }
            }
        }

        return tp / (double) (tp + fp);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getNormValue(double value) {
        return value;
    }
}

