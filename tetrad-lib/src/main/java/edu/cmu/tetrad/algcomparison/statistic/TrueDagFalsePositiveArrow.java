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
 * Represents a statistic that calculates the false positives for arrows compared to the true directed acyclic graph
 * (DAG).
 */
public class TrueDagFalsePositiveArrow implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * This class represents a statistic that calculates the false positives for arrows compared to the true DAG.
     */
    public TrueDagFalsePositiveArrow() {
    }

    /**
     * Retrieves the abbreviation for the statistic.
     *
     * @return The abbreviation for the statistic.
     */
    @Override
    public String getAbbreviation() {
        return "DFPA";
    }

    /**
     * Returns a short one-line description of this statistic. This will be printed at the beginning of the report.
     *
     * @return The description of this statistic.
     */
    @Override
    public String getDescription() {
        return "False Positives for Arrows compared to true DAG";
    }

    /**
     * Calculates the number of false positive arrows in the estimated graph compared to the true directed acyclic
     * graph (DAG).
     *
     * @param trueDag The true directed acyclic graph (DAG).
     * @param trueGraph The true graph structure used for comparison.
     * @param estGraph The estimated graph being evaluated.
     * @param dataModel The data model used in the evaluation process.
     * @param parameters Additional parameters for the calculation.
     * @return The number of false positive arrows in the estimated graph compared to the true DAG.
     */
    @Override
    public double getValue(Graph trueDag, Graph trueGraph, Graph estGraph, DataModel dataModel, Parameters parameters) {
        int fp = 0;

        for (Edge edge : estGraph.getEdges()) {
            if (edge.getEndpoint1() == Endpoint.ARROW) {
                if (trueGraph.paths().isAncestorOf(edge.getNode1(), edge.getNode2())) {
                    fp++;
                }
            }

            if (edge.getEndpoint2() == Endpoint.ARROW) {
                if (trueGraph.paths().isAncestorOf(edge.getNode2(), edge.getNode1())) {
                    fp++;
                }
            }
        }

        return fp;
    }

    /**
     * Retrieves the normalized value of the statistic.
     *
     * @param value The value of the statistic.
     * @return The normalized value of the statistic, between 0 and 1, inclusive.
     */
    @Override
    public double getNormValue(double value) {
        return value;
    }
}

