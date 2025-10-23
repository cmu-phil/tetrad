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
 * The bidirected true positives.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class TrueDagTruePositiveDirectedPathNonancestor implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * This class represents a statistic that calculates the true positives for arrows compared to the true DAG.
     */
    public TrueDagTruePositiveDirectedPathNonancestor() {
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
     * Retrieves the description of the statistic.
     *
     * @return The description of the statistic.
     */
    @Override
    public String getDescription() {
        return "True Positives for Arrows compared to true DAG";
    }

    /**
     * Computes the number of true positives for directed paths in the estimated graph that are consistent
     * with the true graph, excluding cases where the estimated graph incorrectly identifies the reverse relationship.
     *
     * @param trueDag The true directed acyclic graph (DAG) structure.
     * @param trueGraph The correct graph structure representing the true relationships between nodes.
     * @param estGraph The estimated graph structure to be evaluated.
     * @param dataModel A data model associated with the graphs (not directly used in this method).
     * @param parameters Parameters associated with the evaluation (not directly used in this method).
     * @return The count of true positive directed paths in the estimated graph that match the true graph.
     */
    @Override
    public double getValue(Graph trueDag, Graph trueGraph, Graph estGraph, DataModel dataModel, Parameters parameters) {
        int tp = 0;

        List<Node> nodes = trueGraph.getNodes();

        for (Node x : nodes) {
            for (Node y : nodes) {
                if (x == y) continue;

                if (estGraph.paths().isAncestorOf(x, y)) {
                    if (!trueGraph.paths().isAncestorOf(y, x)) {
                        tp++;
                    }
                }
            }
        }

        return tp;
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

