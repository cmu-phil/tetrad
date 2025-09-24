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
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.PagCache;
import edu.cmu.tetrad.util.Parameters;

import java.io.Serial;
import java.util.List;

/**
 * Represents a statistic that calculates the number of correct visible ancestors in the true graph that are also
 * visible ancestors in the estimated graph.
 */
public class NumCorrectVisibleEdges implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs a new instance of the statistic.
     */
    public NumCorrectVisibleEdges() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAbbreviation() {
        return "#CorrectVis";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Returns the number of visible edges X->Y in the estimated graph where X and Y have no latent confounder in the true graph.";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel, Parameters parameters) {
        Graph dag = PagCache.getInstance().getDag(trueGraph);

        GraphUtils.addEdgeSpecializationMarkup(estGraph);

        if (dag == null) {
            return -99;
        }

        int tp = 0;

        for (Edge edge : estGraph.getEdges()) {
            if (edge.getProperties().contains(Edge.Property.nl)) {
                Node x = edge.getNode1();
                Node y = edge.getNode2();

                boolean existsLatentConfounder = false;

                // A latent confounder is a latent node z such that there is a trek x<~~(z)~~>y, so we can limit the
                // length of these treks to 3.
                List<List<Node>> treks = dag.paths().treks(x, y, 3);

                // If there is a trek, x<~~z~~>y, where z is latent, then the edge is not semantically visible.
                for (List<Node> trek : treks) {
                    if (GraphUtils.isConfoundingTrek(dag, trek, x, y)) {
                        existsLatentConfounder = true;
                        break;
                    }
                }

                if (!existsLatentConfounder) {
                    tp++;
                }
            }
        }

        return tp;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getNormValue(double value) {
        return value;
    }
}

