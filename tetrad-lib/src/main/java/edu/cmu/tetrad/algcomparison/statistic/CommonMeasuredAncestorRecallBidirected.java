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
import java.util.List;

import static edu.cmu.tetrad.algcomparison.statistic.CommonAncestorTruePositiveBidirected.existsCommonAncestor;
import static edu.cmu.tetrad.algcomparison.statistic.LatentCommonAncestorTruePositiveBidirected.existsLatentCommonAncestor;

/**
 * The bidirected true positives.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class CommonMeasuredAncestorRecallBidirected implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Initializes a new instance of the CommonMeasuredAncestorRecallBidirected class.
     */
    public CommonMeasuredAncestorRecallBidirected() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAbbreviation() {
        return "#X<-M->Y,adj(X,Y),X<->Y";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "Number of X<~~M->...>Y for X*-*Y in est marked as bidirected";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel, Parameters parameters) {
        int tp = 0;
        int fn = 0;

        List<Node> nodes = estGraph.getNodes();

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                Node x = nodes.get(i);
                Node y = nodes.get(j);

                if (existsCommonAncestor(trueGraph, Edges.nondirectedEdge(x, y))
                    && !existsLatentCommonAncestor(trueGraph, Edges.nondirectedEdge(x, y))) {
                    Edge edge2 = estGraph.getEdge(x, y);

                    if (edge2 != null) {
                        if (Edges.isBidirectedEdge(edge2)) {
                            tp++;
                        } else {
                            fn++;
                        }
                    }
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

