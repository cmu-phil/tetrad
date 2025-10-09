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
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.Parameters;

import java.io.Serial;
import java.util.List;

import static edu.cmu.tetrad.algcomparison.statistic.LatentCommonAncestorTruePositiveBidirected.existsLatentCommonAncestor;

/**
 * The bidirected true positives.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class LatentCommonAncestorFalseNegativeBidirected implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * <p>Constructor for LatentCommonAncestorFalseNegativeBidirected.</p>
     */
    public LatentCommonAncestorFalseNegativeBidirected() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAbbreviation() {
        return "LCAFNB";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Latent Common Ancestor False Negative Bidirected";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getValue(Graph trueDag, Graph trueGraph, Graph estGraph, DataModel dataModel, Parameters parameters) {
        Graph pag = GraphTransforms.dagToPag(trueGraph);

        int fn = 0;

        List<Node> nodes = trueGraph.getNodes();

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                Node x = nodes.get(i);
                Node y = nodes.get(j);

                if (x.getNodeType() == NodeType.MEASURED && y.getNodeType() == NodeType.MEASURED) {
                    if (existsLatentCommonAncestor(trueGraph, new Edge(x, y, Endpoint.CIRCLE, Endpoint.CIRCLE))) {
                        Edge edge2 = estGraph.getEdge(x, y);

                        if (edge2 == null) continue;

                        if (!(edge2 != null && Edges.isBidirectedEdge(edge2)
                              && existsLatentCommonAncestor(trueGraph, edge2))) {
                            fn++;
                        }
                    }
                }
            }
        }

        return fn;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getNormValue(double value) {
        return value;
    }
}

