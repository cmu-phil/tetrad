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

package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.GraphSampling;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetradapp.session.DoNotAddOldModel;

/**
 * <p>The bootstrapping API will generate graphs will "null edges"--that is, edges that aren't in the compositite graph
 * but for which edge statistics are nonetheless available. These null edges are needed so that when the graph is loaded
 * back into a new Graph box, all of the bootstrapping information is still available. Unfortunately, such graphs cannot
 * be estiamted, so for estimation the null edges must be stripped from the graph.</p>
 *
 * <p>This graph wrapper does this stripping of null edges from the graph.</p>.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class RemoveNullEdgesGraphWrapper extends GraphWrapper implements DoNotAddOldModel {
    private static final long serialVersionUID = 23L;

    /**
     * <p>Constructor for RemoveNullEdgesGraphWrapper.</p>
     *
     * @param source     a {@link edu.cmu.tetradapp.model.GraphSource} object
     * @param parameters a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public RemoveNullEdgesGraphWrapper(GraphSource source, Parameters parameters) {
        this(source.getGraph());
    }

    /**
     * <p>Constructor for RemoveNullEdgesGraphWrapper.</p>
     *
     * @param graph a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public RemoveNullEdgesGraphWrapper(Graph graph) {
        super(GraphSampling.createGraphWithoutNullEdges(graph), "Remove Null Edges from Boostrapping");
        String message = getGraph() + "";
        TetradLogger.getInstance().log(message);
    }


    /**
     * <p>serializableInstance.</p>
     *
     * @return a {@link edu.cmu.tetradapp.model.RemoveNullEdgesGraphWrapper} object
     */
    public static RemoveNullEdgesGraphWrapper serializableInstance() {
        return new RemoveNullEdgesGraphWrapper(EdgeListGraph.serializableInstance());
    }
}



