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

import edu.cmu.tetrad.calculator.expression.Expression;
import edu.cmu.tetrad.calculator.expression.VariableExpression;
import edu.cmu.tetrad.data.KnowledgeBoxInput;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.search.IndTestDSep;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.sem.GeneralizedSemIm;
import edu.cmu.tetrad.sem.GeneralizedSemPm;
import edu.cmu.tetrad.session.SessionModel;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializableUtils;
import edu.cmu.tetradapp.util.*;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Holds a tetrad-style graph with all of the constructors necessary for it to
 * serve as a model for the tetrad application.
 *
 * @author Joseph Ramsey
 */
public class GraphWrapper implements SessionModel, GraphSource, KnowledgeBoxInput, IonInput, IndTestProducer {
    static final long serialVersionUID = 23L;

    /**
     * @serial Can be null.
     */
    private String name;

    /**
     * @serial Cannot be null.
     */
    private Graph graph;
//    private Graph parentGraph = null;

    //=============================CONSTRUCTORS==========================//

    public GraphWrapper(Graph graph) {
        if (graph == null) {
            throw new NullPointerException("Graph must not be null.");
        }
        this.graph = graph;
        log();
    }

    public GraphWrapper(Graph graph, String message) {
        TetradLogger.getInstance().log("info", message);

        if (graph == null) {
            throw new NullPointerException("Graph must not be null.");
        }

        this.graph = graph;
    }

    // Do not, repeat not, get rid of these params. -jdramsey 7/4/2010
    public GraphWrapper(GraphParams params) {
        if (Preferences.userRoot().getInt("newGraphInitializationMode", GraphParams.MANUAL) == GraphParams.MANUAL) {
            this.graph = new EdgeListGraph();
        } else if (Preferences.userRoot().getInt("newGraphInitializationMode", GraphParams.MANUAL) == GraphParams.RANDOM) {
            Graph graph = edu.cmu.tetradapp.util.GraphUtils.makeRandomGraph(getGraph());

            boolean addCycles = Preferences.userRoot().getBoolean("randomGraphAddCycles", false);

            if (addCycles) {
                int newGraphNumMeasuredNodes = Preferences.userRoot().getInt("newGraphNumMeasuredNodes", 5);
                int newGraphNumEdges = Preferences.userRoot().getInt("newGraphNumEdges", 3);
                graph = GraphUtils.cyclicGraph2(newGraphNumMeasuredNodes ,newGraphNumEdges);
            }
//            GraphUtils.addTwoCycles(graph, editor.getMinNumCycles());

            this.graph = graph;
        }
        log();
    }

    public GraphWrapper(GraphSource graphSource) {
        if (getGraph() != null) {
            this.graph = new EdgeListGraph(getGraph());
        } else if (Preferences.userRoot().getInt("newGraphInitializationMode",
                GraphParams.MANUAL) == GraphParams.RANDOM) {
            edu.cmu.tetradapp.util.GraphUtils.makeRandomGraph(getGraph());
        }

        Graph graph = graphSource.getGraph();
        if (graph != null) {
            try {
                this.graph = new EdgeListGraph(graph);
            } catch (Exception e) {
                e.printStackTrace();
                this.graph = new EdgeListGraph();
            }
        } else if (Preferences.userRoot().getInt("newGraphInitializationMode", GraphParams.MANUAL) == GraphParams.MANUAL) {
            this.graph = new EdgeListGraph();
        }

        log();
    }

    public boolean allowRandomGraph() {
        return true;
    }


    public GraphWrapper(DataWrapper wrapper) {
        this(new EdgeListGraph(wrapper.getVariables()));
        GraphUtils.circleLayout(graph, 200, 200, 150);
    }

    public GraphWrapper(GeneralizedSemImWrapper wrapper) {
        this(getStrongestInfluenceGraph(wrapper.getSemIm()));
    }

    private static Graph getStrongestInfluenceGraph(GeneralizedSemIm im) {
        GeneralizedSemPm pm = im.getGeneralizedSemPm();
        Graph imGraph = im.getGeneralizedSemPm().getGraph();

        List<Node> nodes = new ArrayList<Node>();

        for (Node node : imGraph.getNodes()) {
            if (!(node.getNodeType() == NodeType.ERROR)) {
                nodes.add(node);
            }
        }

        Graph graph2 = new EdgeListGraph(nodes);

        for (Edge edge : imGraph.getEdges()) {
            Node node1 = edge.getNode1();
            Node node2 = edge.getNode2();

            if (!graph2.containsNode(node1)) continue;
            if (!graph2.containsNode(node2)) continue;

            if (graph2.isAdjacentTo(node1, node2)) {
                continue;
            }

            List<Edge> edges = imGraph.getEdges(node1, node2);

            if (edges.size() == 1) {
                graph2.addEdge(edges.get(0));
            } else {
                Expression expression1 = pm.getNodeExpression(node1);
                Expression expression2 = pm.getNodeExpression(node2);

                String param1 = findParameter(expression1, node2.getName());
                String param2 = findParameter(expression2, node1.getName());

                if (param1 == null || param2 == null) {
                    continue;
                }

                double value1 = im.getParameterValue(param1);
                double value2 = im.getParameterValue(param2);

                if (value2 > value1) {
                    graph2.addDirectedEdge(node1, node2);
                } else if (value1 > value2) {
                    graph2.addDirectedEdge(node2, node1);
                }
            }

        }

        return graph2;
    }

    private static String findParameter(Expression expression, String name) {
        List<Expression> expressions = expression.getExpressions();

        if (expression.getToken().equals("*")) {
            Expression expression1 = expressions.get(1);
            VariableExpression varExpr = (VariableExpression) expression1;

            if (varExpr.getVariable().equals(name)) {
                Expression expression2 = expressions.get(0);
                VariableExpression constExpr = (VariableExpression) expression2;
                return constExpr.getVariable();
            }
        }

        for (Expression _expression : expressions) {
            String param = findParameter(_expression, name);

            if (param != null) {
                return param;
            }
        }

        return null;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static GraphWrapper serializableInstance() {
        return new GraphWrapper(Dag.serializableInstance());
    }

    //==============================PUBLIC METHODS======================//

    public Graph getGraph() {
        return graph;
    }

    public void setGraph(Graph graph) {
        this.graph = graph;
        log();
    }

    //==========================PRIVATE METaHODS===========================//

    private void log() {
        TetradLogger.getInstance().log("info", "General Graph");
        TetradLogger.getInstance().log("graph", "" + getGraph());
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
    }

    @Override
    public IndependenceTest getIndependenceTest() {
        return new IndTestDSep(getGraph());
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Graph getSourceGraph() {
        return graph;
    }

    public Graph getResultGraph() {
        return graph;
    }

    public List<String> getVariableNames() {

        return graph.getNodeNames();
    }

    public List<Node> getVariables() {
        return graph.getNodes();
    }
}





