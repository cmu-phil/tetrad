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
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetradapp.session.DoNotAddOldModel;

import java.io.Serial;

/**
 * Picks a DAG from the given graph.
 *
 * @author Tyler Gibson
 * @version $Id: $Id
 */
public class AllEdgesUndirectedWrapper extends GraphWrapper implements DoNotAddOldModel {
    @Serial
    private static final long serialVersionUID = 23L;


    /**
     * <p>Constructor for AllEdgesUndirectedWrapper.</p>
     *
     * @param source     a {@link edu.cmu.tetradapp.model.GraphSource} object
     * @param parameters a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public AllEdgesUndirectedWrapper(GraphSource source, Parameters parameters) {
        this(source.getGraph());
    }


    /**
     * <p>Constructor for AllEdgesUndirectedWrapper.</p>
     *
     * @param graph a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public AllEdgesUndirectedWrapper(Graph graph) {
        super(GraphUtils.undirectedGraph(graph), "Make all Edges Undirected");
        String message = getGraph() + "";
        TetradLogger.getInstance().log(message);
    }


    /**
     * <p>serializableInstance.</p>
     *
     * @return a {@link edu.cmu.tetradapp.model.AllEdgesUndirectedWrapper} object
     */
    public static AllEdgesUndirectedWrapper serializableInstance() {
        return new AllEdgesUndirectedWrapper(EdgeListGraph.serializableInstance());
    }


    //======================== Private Methods ================================//


    /**
     * {@inheritDoc}
     */
    @Override
    public boolean allowRandomGraph() {
        return false;
    }
}



