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

import edu.cmu.tetrad.data.KnowledgeBoxInput;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.session.SessionModel;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.List;


/**
 * Holds a tetrad dag with all of the constructors necessary for it to serve as
 * a model for the tetrad application.
 *
 * @author Joseph Ramsey
 */
public class TimeLagGraphWrapper implements SessionModel, GraphSource, KnowledgeBoxInput {
    static final long serialVersionUID = 23L;

    /**
     * @serial Can be null.
     */
    private String name;

    /**
     * @serial Cannot be null.
     */
    private TimeLagGraph graph;

    //=============================CONSTRUCTORS==========================//

    public TimeLagGraphWrapper(TimeLagGraph graph) {
        if (graph == null) {
            throw new NullPointerException("Tetrad dag must not be null.");
        }
        this.graph = graph;
        log();
    }

    public TimeLagGraphWrapper(GraphWrapper graphWrapper) {
        if (graphWrapper == null) {
            throw new NullPointerException("No graph wrapper.");
        }

        TimeLagGraph graph = new TimeLagGraph();

        Graph _graph = graphWrapper.getGraph();

        for (Node node : _graph.getNodes()) {
            Node _node = node.like(node.getName() + ":0");
            _node.setNodeType(node.getNodeType());
            graph.addNode(_node);
        }

        for (Edge edge : _graph.getEdges()) {
            if (!Edges.isDirectedEdge(edge)) {
                throw new IllegalArgumentException();
            }

            Node from = edge.getNode1();
            Node to = edge.getNode2();

            Node _from = graph.getNode(from.getName(), 1);
            Node _to = graph.getNode(to.getName(), 0);

            graph.addDirectedEdge(_from, _to);
        }

        this.graph = graph;
    }

    public TimeLagGraphWrapper() {
        this.graph = new TimeLagGraph();
        log();
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static TimeLagGraphWrapper serializableInstance() {
        return new TimeLagGraphWrapper(new TimeLagGraph());
    }

    //============================PRIVATE METHODS========================//


    private void log() {
        TetradLogger.getInstance().log("info", "Directed Acyclic Graph (DAG)");
        TetradLogger.getInstance().log("graph",  graph + "");
    }

    /**
     * Adds semantic checks to the default deserialization method. This method
     * must have the standard signature for a readObject method, and the body of
     * the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from
     * version to version. A readObject method of this form may be added to any
     * class, even if Tetrad sessions were previously saved out using a version
     * of the class that didn't include it. (That's what the
     * "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for help.
     *
     * @throws java.io.IOException
     * @throws ClassNotFoundException
     */
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        if (graph == null) {
            throw new NullPointerException();
        }
    }

    public Graph getGraph() {
        return graph;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

	public Graph getSourceGraph() {
		return getGraph();
	}

    public Graph getResultGraph() {
        return getGraph();
    }

    public List<String> getVariableNames() {
		return getGraph().getNodeNames();
	}

	public List<Node> getVariables() {
		return getGraph().getNodes();
	}

    public void setGraph(TimeLagGraph graph) {
        this.graph = graph;
    }
}


