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

package edu.cmu.tetrad.test;

import edu.cmu.tetrad.algcomparison.graph.RandomGraph;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Parameters;

import java.util.ArrayList;
import java.util.List;

/**
 * A special graph for testing a model of Clark's.
 *
 * @author josephramsey
 */
public class SpecialGraphClark implements RandomGraph {
    private static final long serialVersionUID = 23L;

    @Override
    public Graph createGraph(Parameters parameters) {

        Node x = new ContinuousVariable("X");
        Node y = new ContinuousVariable("Y");
        Node z = new ContinuousVariable("Z");

        Graph g = new EdgeListGraph();
        g.addNode(x);
        g.addNode(y);
        g.addNode(z);

        g.addDirectedEdge(x, y);
        g.addDirectedEdge(x, z);
        g.addDirectedEdge(y, z);
//
        return g;
    }

    @Override
    public String getDescription() {
        return "Graph constructed by adding random forward edges";
    }

    @Override
    public List<String> getParameters() {
        return new ArrayList<>();
    }
}

