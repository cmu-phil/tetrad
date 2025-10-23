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
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Parameters;

import java.io.Serial;
import java.util.List;

/**
 * Represents the statistic of False Negatives for Arrows compared to the true DAG.
 */
public class TrueDagFalseNegativesArrows implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs the statistic.
     */
    public TrueDagFalseNegativesArrows() {
    }

    /**
     * Retrieves the abbreviation for the statistic.
     *
     * @return The abbreviation for the statistic.
     */
    @Override
    public String getAbbreviation() {
        return "DFNA";
    }

    /**
     * Returns a short one-line description of this statistic. This will be printed at the beginning of the report.
     *
     * @return The description of the statistic.
     */
    @Override
    public String getDescription() {
        return "False Negatives for Arrows compared to true DAG";
    }

    /**
     * Computes the number of False Negatives for Arrows in the estimated graph compared to the true DAG.
     *
     * @param trueDag The true Directed Acyclic Graph (DAG).
     * @param trueGraph The true graph containing the correct structure of nodes and edges.
     * @param estGraph The estimated graph to compare against the true graph.
     * @param dataModel The data model used in the analysis. This parameter is not utilized in this method.
     * @param parameters Additional parameters for computation. This parameter is not utilized in this method.
     * @return The number of False Negatives for Arrows in the estimated graph compared to the true DAG.
     */
    @Override
    public double getValue(Graph trueDag, Graph trueGraph, Graph estGraph, DataModel dataModel, Parameters parameters) {
        int fn = 0;

        List<Node> nodes = trueGraph.getNodes();

        for (Node x : nodes) {
            for (Node y : nodes) {
                if (x == y) continue;

                if (!trueGraph.paths().isAncestorOf(x, y)) {
                    Edge e = estGraph.getEdge(x, y);

                    if (e != null && e.getEndpoint(x) != Endpoint.ARROW) {
                        fn++;
                    }
                }
            }
        }

        return fn;
    }

    /**
     * Retrieves the normalized value of a statistic.
     *
     * @param value The value of the statistic.
     * @return The normalized value of the statistic.
     */
    @Override
    public double getNormValue(double value) {
        return value;
    }
}

