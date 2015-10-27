///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
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

import edu.cmu.tetrad.graph.Dag;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.util.TetradLogger;

/**
 * @author Tyler Gibson
 */
public class PatternFromDagGraphWrapper extends GraphWrapper {
    static final long serialVersionUID = 23L;

    
    public PatternFromDagGraphWrapper(GraphSource source) {
        this(source.getGraph());
    }


    public PatternFromDagGraphWrapper(Graph graph) {
        super(new EdgeListGraph());

        // make sure the given graph is a dag.
        try {
            new Dag(graph);
        } catch (Exception e) {
            throw new IllegalArgumentException("The source graph is not a DAG.");
        }

        Graph pattern = getPattern(new Dag(graph));
        setGraph(pattern);

        TetradLogger.getInstance().log("info", "\nGenerating pattern from DAG.");
        TetradLogger.getInstance().log("pattern", pattern + "");
    }

    public static PatternFromDagGraphWrapper serializableInstance() {
        return new PatternFromDagGraphWrapper(EdgeListGraph.serializableInstance());
    }

    //======================== Private Method ======================//


    private static Graph getPattern(Dag dag) {
        return SearchGraphUtils.patternFromDag(dag);
    }

    @Override
    public boolean allowRandomGraph() {
        return false;
    }
}



