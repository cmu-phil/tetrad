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
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.util.Parameters;

import java.io.Serial;

/**
 * Counts the X&lt;-&gt;Y edges of the estimated graph for which X and Y have a direct latent common parent in the
 * true DAG, that is, a latent L with X &lt;- L -&gt; Y.
 *
 * <p>This is not a count of correct bidirected edges, and until November 2026 its abbreviation, "&lt;-&gt;
 * Correct", wrongly suggested it was. A bidirected edge of the true PAG is missed here when its latent trek is
 * longer (a latent with a latent parent) or passes through a measured node; an estimated X&lt;-&gt;Y is counted
 * here when X and Y share a latent parent even if X also causes Y; and the count is zero when the true graph
 * supplied has no latent nodes. For agreement with the true PAG use {@link BidirectedTP}.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class NumCorrectBidirected implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Counts the estimated bidirected edges whose two nodes have a direct latent common parent in the true DAG.
     */
    public NumCorrectBidirected() {
    }

    /**
     * Retrieves the abbreviation for the statistic.
     *
     * @return The abbreviation for the statistic.
     */
    @Override
    public String getAbbreviation() {
        return "#X<->Y,X<-L->Y";
    }

    /**
     * Returns a short one-line description of this statistic. This will be printed at the beginning of the report.
     *
     * @return The description of the statistics as a String.
     */
    @Override
    public String getDescription() {
        return "# X<->Y in estimated where X<-L->Y in true for a latent L (direct latent common parent)";
    }

    /**
     * Returns the number of estimated bidirected edges whose two nodes have a direct latent common parent in the
     * true DAG.
     *
     * @param trueDag The true DAG.
     * @param trueGraph  The true graph (DAG, CPDAG, PAG_of_the_true_DAG).
     * @param estGraph   The estimated graph (same type).
     * @param dataModel  The data model.
     * @param parameters The parameters
     * @return The number of estimated bidirected edges with a direct latent common parent in the true DAG.
     */
    @Override
    public double getValue(Graph trueDag, Graph trueGraph, Graph estGraph, DataModel dataModel, Parameters parameters) {
        Graph dag = Statistic.trueDagOrGraph(trueDag, trueGraph);
        int tp = 0;

        estGraph = GraphUtils.replaceNodes(estGraph, dag.getNodes());

        for (Edge edge : estGraph.getEdges()) {
            if (Edges.isBidirectedEdge(edge)) {
                if (GraphUtils.bidirectedExistsLatentConfounder(edge, dag)) {
                    tp++;
                }
            }
        }

        return tp;
    }

    /**
     * Returns the normalized value of the given statistic.
     *
     * @param value The value of the statistic.
     * @return The normalized value.
     */
    @Override
    public double getNormValue(double value) {
        return value;
    }
}

