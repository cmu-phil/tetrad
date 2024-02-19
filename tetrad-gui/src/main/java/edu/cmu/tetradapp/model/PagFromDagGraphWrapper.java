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
import edu.cmu.tetrad.graph.GraphTransforms;
import edu.cmu.tetradapp.session2.session.session.DoNotAddOldModel;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;

/**
 * <p>PagFromDagGraphWrapper class.</p>
 *
 * @author Tyler Gibson
 * @version $Id: $Id
 */
public class PagFromDagGraphWrapper extends GraphWrapper implements DoNotAddOldModel {
    private static final long serialVersionUID = 23L;


    /**
     * <p>Constructor for PagFromDagGraphWrapper.</p>
     *
     * @param source     a {@link edu.cmu.tetradapp.model.GraphSource} object
     * @param parameters a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public PagFromDagGraphWrapper(GraphSource source, Parameters parameters) {
        this(source.getGraph());
    }


    /**
     * <p>Constructor for PagFromDagGraphWrapper.</p>
     *
     * @param graph a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public PagFromDagGraphWrapper(Graph graph) {
        super(graph);

        if (graph.paths().existsDirectedCycle()) {
            throw new IllegalArgumentException("The source graph is not a DAG.");
        }

        Graph pag = GraphTransforms.dagToPag(graph);
        setGraph(pag);

        TetradLogger.getInstance().log("info", "\nGenerating allow_latent_common_causes from DAG.");
        TetradLogger.getInstance().log("CPDAG", pag + "");
    }

    /**
     * <p>serializableInstance.</p>
     *
     * @return a {@link edu.cmu.tetradapp.model.PagFromDagGraphWrapper} object
     */
    public static PagFromDagGraphWrapper serializableInstance() {
        return new PagFromDagGraphWrapper(EdgeListGraph.serializableInstance());
    }

    //======================== Private Method ======================//


    /**
     * {@inheritDoc}
     */
    @Override
    public boolean allowRandomGraph() {
        return false;
    }
}



