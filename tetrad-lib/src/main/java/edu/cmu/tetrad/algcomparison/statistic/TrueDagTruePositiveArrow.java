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
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;

import java.io.Serial;

/**
 * The bidirected true positives.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class TrueDagTruePositiveArrow implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * This class represents a statistic that calculates the true positives for arrows compared to the true DAG.
     */
    public TrueDagTruePositiveArrow() {
    }

    /**
     * Retrieves the abbreviation for the statistic.
     *
     * @return The abbreviation.
     */
    @Override
    public String getAbbreviation() {
        return "DTPA";
    }

    /**
     * Retrieves a short one-line description of this statistic.
     *
     * @return The description of the statistic.
     */
    @Override
    public String getDescription() {
        return "True Positives for Arrows compared to true DAG";
    }

    /**
     * Computes the count of bidirected true positives for arrows in the estimated graph compared
     * to the true graph. An edge is considered a bidirected true positive if it contains an arrow
     * endpoint that does not imply ancestry contrary to the structure of the true graph.
     *
     * @param trueDag     The true directed acyclic graph (DAG) representing the ground truth.
     * @param trueGraph   The true graph representing the ground-truth relationships.
     * @param estGraph    The estimated graph being evaluated.
     * @param dataModel   The data model used in the evaluation.
     * @param parameters  Additional parameters required for the computation.
     * @return The count of bidirected true positives for the arrow endpoints.
     */
    @Override
    public double getValue(Graph trueDag, Graph trueGraph, Graph estGraph, DataModel dataModel, Parameters parameters) {
        int tp = 0;

        for (Edge edge : estGraph.getEdges()) {
            if (edge.getEndpoint1() == Endpoint.ARROW) {
                if (!trueGraph.paths().isAncestorOf(edge.getNode1(), edge.getNode2())) {
                    tp++;
                }
            }

            if (edge.getEndpoint2() == Endpoint.ARROW) {
                if (!trueGraph.paths().isAncestorOf(edge.getNode2(), edge.getNode1())) {
                    tp++;
                }
            }
        }

        return tp;
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

