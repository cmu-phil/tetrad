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
 * No almost cyclic paths condition.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class NoAlmostCyclicPathsCondition implements Statistic {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * Constructs a new instance of the statistic.
     */
    public NoAlmostCyclicPathsCondition() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getAbbreviation() {
        return "NoAlmostCyclic";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDescription() {
        return "1 if the no almost cyclic paths condition passes, 0 if not";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel, Parameters parameters) {
        Graph pag = estGraph;

        for (Edge e : pag.getEdges()) {
            Node x = e.getNode1();
            Node y = e.getNode2();

            if (Edges.isBidirectedEdge(e)) {
                if (pag.paths().existsDirectedPath(x, y)) {
                    return 0;
                } else if (pag.paths().existsDirectedPath(y, x)) {
                    return 0;
                }
            }
        }

        return 1;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public double getNormValue(double value) {
        return value;
    }
}

