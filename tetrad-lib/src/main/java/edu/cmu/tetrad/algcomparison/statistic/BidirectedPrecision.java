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
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.util.Parameters;

import java.io.Serial;

/**
 * The bidirected edge precision.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class BidirectedPrecision implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs a new instance of the statistic.
     */
    public BidirectedPrecision() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAbbreviation() {
        return "BP";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Precision of bidirected edges compared to true PAG";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getValue(Graph trueDag, Graph trueGraph, Graph estGraph, DataModel dataModel, Parameters parameters) {
        Graph pag = GraphTransforms.dagToPag(trueGraph);
        int tp = 0;
        int fp = 0;

        for (Edge edge : estGraph.getEdges()) {
            if (Edges.isBidirectedEdge(edge)) {
                Edge edge2 = pag.getEdge(edge.getNode1(), edge.getNode2());

                if (edge2 != null && Edges.isBidirectedEdge(edge2)) {
                    tp++;
                } else {
                    fp++;
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

