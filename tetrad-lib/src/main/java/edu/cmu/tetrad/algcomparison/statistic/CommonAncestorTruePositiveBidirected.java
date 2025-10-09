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
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Parameters;

import java.io.Serial;

/**
 * The bidirected true positives.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class CommonAncestorTruePositiveBidirected implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs a new instance of the statistic.
     */
    public CommonAncestorTruePositiveBidirected() {
    }

    /**
     * Returns true if there is a common ancestor of X and Y in the graph.
     *
     * @param trueGraph the true graph.
     * @param edge      the edge.
     * @return true if there is a common ancestor of X and Y in the graph.
     */
    public static boolean existsCommonAncestor(Graph trueGraph, Edge edge) {

        // edge X*-*Y where there is a common ancestor of X and Y in the graph.
        for (Node c : trueGraph.getNodes()) {
            if (trueGraph.paths().isAncestorOf(c, edge.getNode1())
                && trueGraph.paths().isAncestorOf(c, edge.getNode2())) {
                return true;
            }
        }

        return false;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the name of the statistic.
     */
    @Override
    public String getAbbreviation() {
        return "CATPB";
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the description of the statistic.
     */
    @Override
    public String getDescription() {
        return "Common Ancestor True Positive Bidirected";
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the value of the statistic.
     */
    @Override
    public double getValue(Graph trueDag, Graph trueGraph, Graph estGraph, DataModel dataModel, Parameters parameters) {
        int tp = 0;

        for (Edge edge : estGraph.getEdges()) {
            if (Edges.isBidirectedEdge(edge)) {
                if (existsCommonAncestor(trueGraph, edge)) tp++;
            }
        }

        return tp;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Returns the normed value of the statistic.
     */
    @Override
    public double getNormValue(double value) {
        return value;
    }
}

