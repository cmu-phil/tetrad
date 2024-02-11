///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.session.DoNotAddOldModel;
import edu.cmu.tetrad.util.GraphSampling;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;

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
        TetradLogger.getInstance().log("graph", getGraph() + "");
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


