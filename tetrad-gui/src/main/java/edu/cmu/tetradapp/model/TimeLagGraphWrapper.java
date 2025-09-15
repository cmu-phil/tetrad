/// ////////////////////////////////////////////////////////////////////////////
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

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * Holds a tetrad dag with all of the constructors necessary for it to serve as a model for the tetrad application.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class TimeLagGraphWrapper implements GraphSource, KnowledgeBoxInput {
    @Serial
    private static final long serialVersionUID = 23L;

    /**
     * The name of the wrapper.
     */
    private String name;

    /**
     * The graph.
     */
    private TimeLagGraph graph;

    /**
     * The parameters.
     */
    private Parameters parameters;

    //=============================CONSTRUCTORS==========================//

    /**
     * <p>Constructor for TimeLagGraphWrapper.</p>
     *
     * @param parameters a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public TimeLagGraphWrapper(Parameters parameters) {
        this.graph = new TimeLagGraph();
        this.parameters = parameters;
        log();
    }

    /**
     * <p>Constructor for TimeLagGraphWrapper.</p>
     *
     * @param graph      a {@link edu.cmu.tetrad.graph.TimeLagGraph} object
     * @param parameters a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public TimeLagGraphWrapper(TimeLagGraph graph, Parameters parameters) {
        if (graph == null) {
            throw new NullPointerException("Tetrad dag must not be null.");
        }
        this.graph = graph;
        this.parameters = parameters;
        log();
    }

    /**
     * <p>Constructor for TimeLagGraphWrapper.</p>
     *
     * @param graphWrapper a {@link edu.cmu.tetradapp.model.GraphWrapper} object
     */
    public TimeLagGraphWrapper(GraphWrapper graphWrapper) {
        if (graphWrapper == null) {
            throw new NullPointerException("No graph wrapper.");
        }

        this.parameters = graphWrapper.getParameters();

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
        int numLags = 1; // need to fix this!
        List<Node> variables = graph.getNodes();
        List<Integer> laglist = new ArrayList<>();
        Knowledge knowledge1 = new Knowledge();
        int lag;
        for (Node node : variables) {
            String varName = node.getName();
            String tmp;
            if (varName.indexOf(':') == -1) {
                lag = 0;
            } else {
                tmp = varName.substring(varName.indexOf(':') + 1);
                lag = Integer.parseInt(tmp);
            }
            laglist.add(lag);
        }
        numLags = Collections.max(laglist);
        for (Node node : variables) {
            String varName = node.getName();
            String tmp;
            if (varName.indexOf(':') == -1) {
                lag = 0;
            } else {
                tmp = varName.substring(varName.indexOf(':') + 1);
                lag = Integer.parseInt(tmp);
            }
            laglist.add(lag);
            knowledge1.addToTier(numLags - lag, node.getName());
        }

        System.out.println("Knowledge in graph = " + knowledge1);
    }

    /**
     * <p>Constructor for TimeLagGraphWrapper.</p>
     */
    public TimeLagGraphWrapper() {
        this.graph = new TimeLagGraph();
        log();
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetradapp.model.TimeLagGraphWrapper} object
     * @see TetradSerializableUtils
     */
    public static TimeLagGraphWrapper serializableInstance() {
        return new TimeLagGraphWrapper(new TimeLagGraph(),
                new Parameters());
    }

    //============================PRIVATE METHODS========================//


    private void log() {
        TetradLogger.getInstance().log("Directed Acyclic Graph (DAG)");
        TetradLogger.getInstance().log(this.graph + "");
    }

    /**
     * Writes the object to the specified ObjectOutputStream.
     *
     * @param out The ObjectOutputStream to write the object to.
     * @throws IOException If an I/O error occurs.
     */
    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        try {
            out.defaultWriteObject();
        } catch (IOException e) {
            TetradLogger.getInstance().log("Failed to serialize object: " + getClass().getCanonicalName()
                                           + ", " + e.getMessage());
            throw e;
        }
    }

    /**
     * Reads the object from the specified ObjectInputStream. This method is used during deserialization
     * to restore the state of the object.
     *
     * @param in The ObjectInputStream to read the object from.
     * @throws IOException            If an I/O error occurs.
     * @throws ClassNotFoundException If the class of the serialized object cannot be found.
     */
    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
        } catch (IOException e) {
            TetradLogger.getInstance().log("Failed to deserialize object: " + getClass().getCanonicalName()
                                           + ", " + e.getMessage());
            throw e;
        }
    }

    /**
     * <p>Getter for the field <code>graph</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public Graph getGraph() {
        return this.graph;
    }

    /**
     * <p>Setter for the field <code>graph</code>.</p>
     *
     * @param graph a {@link edu.cmu.tetrad.graph.TimeLagGraph} object
     */
    public void setGraph(TimeLagGraph graph) {
        this.graph = graph;
    }

    /**
     * <p>Getter for the field <code>name</code>.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String getName() {
        return this.name;
    }

    /**
     * {@inheritDoc}
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * <p>getSourceGraph.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public Graph getSourceGraph() {
        return getGraph();
    }

    /**
     * <p>getResultGraph.</p>
     *
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public Graph getResultGraph() {
        return getGraph();
    }

    /**
     * <p>getVariableNames.</p>
     *
     * @return a {@link java.util.List} object
     */
    public List<String> getVariableNames() {
        return getGraph().getNodeNames();
    }

    /**
     * <p>getVariables.</p>
     *
     * @return a {@link java.util.List} object
     */
    public List<Node> getVariables() {
        return getGraph().getNodes();
    }

    /**
     * <p>getKnowledge.</p>
     *
     * @return a {@link edu.cmu.tetrad.data.Knowledge} object
     */
    public Knowledge getKnowledge() {
        int numLags = 1; // need to fix this!
        List<Node> variables = this.graph.getNodes();
        List<Integer> laglist = new ArrayList<>();
        Knowledge knowledge1 = new Knowledge();
        int lag;
        for (Node node : variables) {
            String varName = node.getName();
            String tmp;
            if (varName.indexOf(':') == -1) {
                lag = 0;
            } else {
                tmp = varName.substring(varName.indexOf(':') + 1);
                lag = Integer.parseInt(tmp);
            }
            laglist.add(lag);
        }
        numLags = Collections.max(laglist);
        for (Node node : variables) {
            String varName = node.getName();
            String tmp;
            if (varName.indexOf(':') == -1) {
                lag = 0;
            } else {
                tmp = varName.substring(varName.indexOf(':') + 1);
                lag = Integer.parseInt(tmp);
            }
            laglist.add(lag);
            knowledge1.addToTier(numLags - lag, node.getName());
        }

        System.out.println("Knowledge in graph = " + knowledge1);
        return knowledge1;
    }

    /**
     * <p>Getter for the field <code>parameters</code>.</p>
     *
     * @return a {@link edu.cmu.tetrad.util.Parameters} object
     */
    public Parameters getParameters() {
        return this.parameters;
    }
}


