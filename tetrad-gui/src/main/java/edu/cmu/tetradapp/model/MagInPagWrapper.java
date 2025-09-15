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
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetradapp.session.DoNotAddOldModel;

/**
 * Picks the Zhang MAG in a PAG.
 *
 * @author Tyler Gibson
 * @version $Id: $Id
 */
public class MagInPagWrapper extends GraphWrapper implements DoNotAddOldModel {
    private static final long serialVersionUID = 23L;

    /**
     * <p>Constructor for MagInPagWrapper.</p>
     *
     * @param source     a {@link edu.cmu.tetradapp.model.GraphSource} object
     * @param parameters a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public MagInPagWrapper(GraphSource source, Parameters parameters) {
        this(source.getGraph());
    }


    /**
     * <p>Constructor for MagInPagWrapper.</p>
     *
     * @param graph a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public MagInPagWrapper(Graph graph) {
        super(MagInPagWrapper.getGraph(graph), "Choose Zhang MAG in PAG.");
        String message = getGraph() + "";
        TetradLogger.getInstance().log(message);
    }

    private static Graph getGraph(Graph graph) {
        return GraphTransforms.zhangMagFromPag(graph);
    }


    /**
     * <p>serializableInstance.</p>
     *
     * @return a {@link edu.cmu.tetradapp.model.MagInPagWrapper} object
     */
    public static MagInPagWrapper serializableInstance() {
        return new MagInPagWrapper(EdgeListGraph.serializableInstance());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean allowRandomGraph() {
        return false;
    }
}





