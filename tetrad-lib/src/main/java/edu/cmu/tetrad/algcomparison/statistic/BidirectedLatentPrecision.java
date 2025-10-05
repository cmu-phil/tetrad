/// ////////////////////////////////////////////////////////////////////////////
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
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.util.PagCache;
import edu.cmu.tetrad.util.Parameters;

import java.io.Serial;

/**
 * The BidirectedLatentPrecision class implements the Statistic interface and represents a statistic that calculates the
 * percentage of bidirected edges in an estimated graph for which a latent confounder exists in the true graph.
 */
public class BidirectedLatentPrecision implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The BidirectedLatentPrecision class implements the Statistic interface and represents a statistic that calculates
     * the percentage of bidirected edges in an estimated graph for which a latent confounder exists in the true graph.
     */
    public BidirectedLatentPrecision() {
    }

    /**
     * Returns the abbreviation for the statistic. The abbreviation is a short string that represents the statistic. For
     * this statistic, the abbreviation is "&lt;-&gt;-Lat-Prec".
     *
     * @return The abbreviation for the statistic.
     */
    @Override
    public String getAbbreviation() {
        return "<->-Lat-Prec";
    }

    /**
     * Returns a short description of the statistic, which is the percentage of bidirected edges for which a latent
     * confounder exists.
     *
     * @return The description of the statistic.
     */
    @Override
    public String getDescription() {
        return "Percent of bidirected edges for which a latent confounder exists (an latent L such that X <- (L) -> Y).";
    }

    /**
     * Calculates the percentage of correctly identified bidirected edges in an estimated graph for which a latent
     * confounder exists in the true graph.
     *
     * @param trueGraph  The true graph (DAG, CPDAG, PAG_of_the_true_DAG).
     * @param estGraph   The estimated graph (same type).
     * @param dataModel  The data model.
     * @param parameters The parameters
     * @return The percentage of correctly identified bidirected edges.
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel, Parameters parameters) {
        Graph dag;

        if (trueGraph.paths().isLegalDag()) {
            dag = trueGraph;
        } else {
            dag = PagCache.getInstance().getDag(trueGraph);
        }

        if (dag == null) {
            throw new IllegalArgumentException("Dag is null");
        }

        int tp = 0;
        int pos = 0;

        estGraph = GraphUtils.replaceNodes(estGraph, dag.getNodes());

        for (Edge edge : estGraph.getEdges()) {
            if (Edges.isBidirectedEdge(edge)) {
                if (GraphUtils.isCorrectBidirectedEdge(edge, dag)) {
                    tp++;
                }

                pos++;
            }
        }

        return tp / (double) pos;
    }

    /**
     * Calculates the normalized value of a given statistic value.
     *
     * @param value The value of the statistic.
     * @return The normalized value of the statistic.
     */
    @Override
    public double getNormValue(double value) {
        return value;
    }
}

