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
 * Ancestor recall.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class AncestorRecall implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs the statistic.
     */
    public AncestorRecall() {

    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the name of the statistic.
     */
    @Override
    public String getAbbreviation() {
        return "Anc-Rec";
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the description of the statistic.
     */
    @Override
    public String getDescription() {
        return "Proportion of X~~>Y in the true graph for which also X~~>Y in estimated graph";
    }

    /**
     * {@inheritDoc}
     * <p>
     * Calculates the statistic.
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel, Parameters parameters) {
        int tp = 0;
        int fn = 0;

        List<Node> nodes = estGraph.getNodes();

        for (Node x : nodes) {
            for (Node y : nodes) {
//                if (x == y) continue;
                if (trueGraph.paths().isAncestorOf(x, y)) {
                    if (estGraph.paths().isAncestorOf(x, y)) {
                        tp++;
                    } else {
                        fn++;
                    }
                }
            }
        }

        return tp / (double) (tp + fn);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the norm value of the statistic.
     */
    @Override
    public double getNormValue(double value) {
        return value;
    }
}

