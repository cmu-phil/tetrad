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

package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.session.DoNotAddOldModel;
import edu.cmu.tetrad.session.SessionModel;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradSerializableUtils;
import edu.cmu.tetradapp.model.DataWrapper;
import edu.cmu.tetradapp.model.GraphSource;
import edu.cmu.tetradapp.model.GraphWrapper;
import edu.cmu.tetradapp.model.PcRunner;

import java.util.ArrayList;
import java.util.List;

/**
 * Calculates the intersection of adjacencies for a list of graphs--the
 * adjacencies that are shared by all graphs, the the adjacencies that
 * are shared by all but one graph, and so on down to one graph.
 *
 * @author Joseph Ramsey
 */
public class GraphAdjacencyIntersectionWrapper implements SessionModel, DoNotAddOldModel {
    static final long serialVersionUID = 23L;
    private List<Graph> graphs;
    private String name = "";

    public GraphAdjacencyIntersectionWrapper(GraphSource[] data1, Parameters parameters) {
        construct(data1);
    }

    private void construct(GraphSource...GraphSources) {
        for (GraphSource wrapper : GraphSources) {
            if (wrapper == null) {
                throw new NullPointerException("The given data must not be null");
            }
        }

        List<Graph> graphs = new ArrayList<>();

        for (GraphSource wrapper : GraphSources) {
            graphs.add(wrapper.getGraph());
        }

        this.graphs = graphs;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static DataWrapper serializableInstance() {
        return new DataWrapper(new Parameters());
    }


    public List<Graph> getGraphs() {
        return graphs;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}



