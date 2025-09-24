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
 * The class TrueDagFalseNegativesTails implements the Statistic interface to calculate the number of false negatives
 * for tails compared to the true Directed Acyclic Graph (DAG).
 */
public class TrueDagFalseNegativesTails implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs the statistic.
     */
    public TrueDagFalseNegativesTails() {
    }

    /**
     * Retrieves the abbreviation for the statistic.
     *
     * @return The abbreviation as a String.
     */
    @Override
    public String getAbbreviation() {
        return "DFNT";
    }

    /**
     * Retrieves a short one-line description of the statistic.
     *
     * @return The description of the statistic.
     */
    @Override
    public String getDescription() {
        return "False Negatives for Tails compared to true DAG";
    }

    /**
     * Calculates the number of false negatives for tails compared to the true DAG.
     *
     * @param trueGraph  The true graph (DAG, CPDAG, PAG_of_the_true_DAG).
     * @param estGraph   The estimated graph (same type).
     * @param dataModel  The data model.
     * @param parameters The parameters.
     * @return The number of false negatives for tails.
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel, Parameters parameters) {
//        int tp = 0;
        int fn = 0;

        List<Node> nodes = trueGraph.getNodes();

        for (Node x : nodes) {
            for (Node y : nodes) {
                if (x == y) continue;

                if (trueGraph.paths().isAncestorOf(x, y)) {
                    Edge e = estGraph.getEdge(x, y);

                    if (e != null && e.getEndpoint(x) != Endpoint.TAIL) {
                        fn++;
                    }
                }
            }
        }

        return fn;
    }

    /**
     * Retrieves the normalized value of the given statistic.
     *
     * @param value The value of the statistic.
     * @return The normalized value of the statistic.
     */
    @Override
    public double getNormValue(double value) {
        return value;
    }
}

