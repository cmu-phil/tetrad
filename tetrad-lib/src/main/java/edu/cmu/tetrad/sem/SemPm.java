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

package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.Pm;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializable;
import edu.cmu.tetrad.util.dist.Normal;
import edu.cmu.tetrad.util.dist.SingleValue;
import edu.cmu.tetrad.util.dist.Split;
import edu.cmu.tetrad.util.dist.Uniform;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.util.*;

/**
 * Parametric model for Structural Equation Models.
 * <p>
 * Note: Could not get a copy constructor to work properly, so to copy SemPm objects, use object serialization--e.g.
 * java.rmu.MarshalledObject.
 *
 * @author Donald Crimbchin
 * @author josephramsey
 * @version $Id: $Id
 */
public final class SemPm implements Pm, TetradSerializable {
    private static final long serialVersionUID = 23L;

    /**
     * The structural model graph that this sem parametric model is based on.
     *
     * @serial Cannot be null.
     */
    private final SemGraph graph;

    /**
     * The list of all nodes (unmodifiable).
     *
     * @serial Cannot be null.
     */
    private List<Node> nodes;

    /**
     * The list of Parameters (unmodifiable).
     *
     * @serial Cannot be null.
     */
    private List<Parameter> parameters;

    /**
     * The list of variable nodes (unmodifiable).
     *
     * @serial Cannot be null.
     */
    private List<Node> variableNodes;

    /**
     * The set of parameter comparisons.
     *
     * @serial Cannot be null.
     */
    private Map<ParameterPair, ParamComparison> paramComparisons =
            new HashMap<>();

    /**
     * The index of the most recent "T" parameter. (These are variance and covariance terms.)
     *
     * @serial Range &gt;= 0.
     */
    private int tIndex;

    /**
     * The index of the most recent "M" parameter. (These are means.)
     *
     * @serial Range &gt;= 0.
     */
    private int mIndex;

    /**
     * The index of the most recent "B" parameter. (These are edge coefficients.)
     *
     * @serial Range &gt;= 0.
     */
    private int bIndex;

    /**
     * Constructs a BayesPm from the given Graph, which must be convertible first into a ProtoSemGraph and then into a
     * SemGraph.
     *
     * @param graph a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public SemPm(Graph graph) {
        this(new SemGraph(graph));
    }

    /**
     * Constructs a new SemPm from the given SemGraph.
     *
     * @param graph a {@link edu.cmu.tetrad.graph.SemGraph} object
     */
    public SemPm(SemGraph graph) {
        if (graph == null) {
            throw new NullPointerException("Graph must not be null.");
        }

        this.graph = new SemGraph(graph);
        this.graph.setShowErrorTerms(false);

        initializeNodes(graph);
        initializeVariableNodes();

        initializeParams();
    }

    /**
     * Copy constructor.
     *
     * @param semPm a {@link edu.cmu.tetrad.sem.SemPm} object
     */
    public SemPm(SemPm semPm) {
        this.graph = semPm.graph;
        this.nodes = new LinkedList<>(semPm.nodes);
        this.parameters = new LinkedList<>(semPm.parameters);
        this.variableNodes = new LinkedList<>(semPm.variableNodes);
        this.paramComparisons = new HashMap<>(
                semPm.paramComparisons);
        this.tIndex = semPm.tIndex;
        this.bIndex = semPm.bIndex;
        this.mIndex = semPm.mIndex;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return a {@link edu.cmu.tetrad.sem.SemPm} object
     */
    public static SemPm serializableInstance() {
        return new SemPm(Dag.serializableInstance());
    }

    /**
     * <p>fixLatentErrorVariances.</p>
     */
    public void fixLatentErrorVariances() {
        Graph graph = getGraph();
        List<Node> nodes = graph.getNodes();

        for (Node x : nodes) {
            if (x.getNodeType() == NodeType.LATENT) {
                Parameter p = getParameter(x, x);
                p.setFixed(true);
                p.setInitializedRandomly(false);
                p.setStartingValue(0.5);
            }
        }
    }

    /**
     * <p>fixOneLoadingPerLatent.</p>
     */
    public void fixOneLoadingPerLatent() {
        for (Node x : this.graph.getNodes()) {
            if (x.getNodeType() != NodeType.LATENT) {
                continue;
            }

            for (Node y : this.graph.getAdjacentNodes(x)) {
                if (y.getNodeType() != NodeType.MEASURED && y.getNodeType() != NodeType.SELECTION) {
                    continue;
                }

                Edge edge = this.graph.getEdge(x, y);

                if (!edge.pointsTowards(y)) {
                    continue;
                }

                Parameter p = getParameter(x, y);

                if (p.isFixed()) {
                    continue;
                }

                p.setFixed(true);
                p.setInitializedRandomly(false);
                p.setStartingValue(1.0);
                break;
            }
        }
    }

    /**
     * <p>Getter for the field <code>graph</code>.</p>
     *
     * @return the structural model graph this SEM PM is using.
     */
    public SemGraph getGraph() {
        return this.graph;
    }

    /**
     * <p>Getter for the field <code>parameters</code>.</p>
     *
     * @return a list of all the freeParameters, including variance, covariance, coefficient, and mean freeParameters.
     */
    public List<Parameter> getParameters() {
        return this.parameters;
    }

    /**
     * <p>Getter for the field <code>variableNodes</code>.</p>
     *
     * @return the list of variable nodes--that is, node that are not error nodes.
     */
    public List<Node> getVariableNodes() {
        return this.variableNodes;
    }

    /**
     * <p>getMeasuredNodes.</p>
     *
     * @return the list of measured variableNodes.
     */
    public List<Node> getMeasuredNodes() {
        List<Node> measuredNodes = new ArrayList<>();

        for (Node variable : getVariableNodes()) {
            if (variable.getNodeType() == NodeType.MEASURED || variable.getNodeType() == NodeType.SELECTION) {
                measuredNodes.add(variable);
            }
        }

        return measuredNodes;
    }

    /**
     * <p>getLatentNodes.</p>
     *
     * @return the list of latent variableNodes.
     */
    public List<Node> getLatentNodes() {
        List<Node> latentNodes = new ArrayList<>();

        for (Node node1 : this.nodes) {
            if (node1.getNodeType() == NodeType.LATENT) {
                latentNodes.add(node1);
            }
        }

        return latentNodes;
    }

    /**
     * <p>getParameter.</p>
     *
     * @param name a {@link java.lang.String} object
     * @return the first parameter encountered with the given name, or null if there is no such parameter.
     */
    public Parameter getParameter(String name) {
        for (Parameter parameter1 : getParameters()) {
            if (name.equals(parameter1.getName())) {
                return parameter1;
            }
        }

        return null;
    }

    /**
     * <p>getParameter.</p>
     *
     * @param nodeA a {@link edu.cmu.tetrad.graph.Node} object
     * @param nodeB a {@link edu.cmu.tetrad.graph.Node} object
     * @return a {@link edu.cmu.tetrad.sem.Parameter} object
     */
    public Parameter getParameter(Node nodeA, Node nodeB) {
        nodeA = getGraph().getVarNode(nodeA);
        nodeB = getGraph().getVarNode(nodeB);

        for (Parameter parameter : this.parameters) {
            Node _nodeA = parameter.getNodeA();
            Node _nodeB = parameter.getNodeB();

            if (nodeA == _nodeA && nodeB == _nodeB) {
                return parameter;
            }

            if (nodeA == _nodeB && nodeB == _nodeA) {
                return parameter;
            }
        }

        throw new NullPointerException("No such parameter in this model: " + nodeA + "---" + nodeB);
    }

    /**
     * Return the parameter for the variance of the error term for the given node, which is the variance of the node if
     * the node is an error term, and the variance of the node's error term if not.
     *
     * @param node a {@link edu.cmu.tetrad.graph.Node} object
     * @return a {@link edu.cmu.tetrad.sem.Parameter} object
     */
    public Parameter getVarianceParameter(Node node) {
        if (node.getNodeType() == NodeType.ERROR) {
            node = getGraph().getChildren(node).iterator().next();
        }

        for (Parameter parameter : this.parameters) {
            Node _nodeA = parameter.getNodeA();
            Node _nodeB = parameter.getNodeB();

            if (node == _nodeA && node == _nodeB && parameter.getType() == ParamType.VAR) {
                return parameter;
            }
        }

        return null;
    }

    /**
     * <p>getCovarianceParameter.</p>
     *
     * @param nodeA a {@link edu.cmu.tetrad.graph.Node} object
     * @param nodeB a {@link edu.cmu.tetrad.graph.Node} object
     * @return a {@link edu.cmu.tetrad.sem.Parameter} object
     */
    public Parameter getCovarianceParameter(Node nodeA, Node nodeB) {
        if (nodeA.getNodeType() == NodeType.ERROR) {
            List<Node> children = getGraph().getChildren(nodeA);
            if (children == null || children.isEmpty()) return null;
            nodeA = children.iterator().next();
        }

        if (nodeB.getNodeType() == NodeType.ERROR) {
            List<Node> children = getGraph().getChildren(nodeB);
            if (children == null || children.isEmpty()) return null;
            nodeB = children.iterator().next();
        }

        for (Parameter parameter : this.parameters) {
            if (parameter.getNodeA() == nodeA && parameter.getNodeB() == nodeB) {
                return parameter;
            }

            if (parameter.getNodeA() == nodeB && parameter.getNodeA() == nodeA) {
                return parameter;
            }
        }

        return null;
    }

    /**
     * <p>getCoefficientParameter.</p>
     *
     * @param nodeA a {@link edu.cmu.tetrad.graph.Node} object
     * @param nodeB a {@link edu.cmu.tetrad.graph.Node} object
     * @return a {@link edu.cmu.tetrad.sem.Parameter} object
     */
    public Parameter getCoefficientParameter(Node nodeA, Node nodeB) {
        for (Parameter parameter : this.parameters) {
            Node _nodeA = parameter.getNodeA();
            Node _nodeB = parameter.getNodeB();

            if (nodeA == _nodeA && nodeB == _nodeB && parameter.getType() == ParamType.COEF) {
                return parameter;
            }
        }

        return null;
    }

    /**
     * <p>getMeanParameter.</p>
     *
     * @param node a {@link edu.cmu.tetrad.graph.Node} object
     * @return a {@link edu.cmu.tetrad.sem.Parameter} object
     */
    public Parameter getMeanParameter(Node node) {
        for (Parameter parameter : this.parameters) {
            Node _nodeA = parameter.getNodeA();
            Node _nodeB = parameter.getNodeB();

            if (node == _nodeA && node == _nodeB && parameter.getType() == ParamType.MEAN) {
                return parameter;
            }
        }

        return null;
    }

    /**
     * <p>getMeasuredVarNames.</p>
     *
     * @return the list of measured variable names in the order in which they appear in the list of nodes. (This order
     * is fixed.)
     */
    public String[] getMeasuredVarNames() {
        List<Node> semPmVars = getVariableNodes();
        List<String> varNamesList = new ArrayList<>();

        for (Node semPmVar : semPmVars) {
            if (semPmVar.getNodeType() == NodeType.MEASURED || semPmVar.getNodeType() == NodeType.SELECTION) {
                varNamesList.add(semPmVar.toString());
            }
        }

        return varNamesList.toArray(new String[0]);
    }

    /**
     * <p>getParamComparison.</p>
     *
     * @param a a {@link edu.cmu.tetrad.sem.Parameter} object
     * @param b a {@link edu.cmu.tetrad.sem.Parameter} object
     * @return the comparison of parameter a to parameter b.
     */
    public ParamComparison getParamComparison(Parameter a, Parameter b) {
        if (a == null || b == null) {
            throw new NullPointerException();
        }

        ParameterPair pair1 = new ParameterPair(a, b);
        ParameterPair pair2 = new ParameterPair(b, a);

        if (this.paramComparisons.containsKey(pair1)) {
            return this.paramComparisons.get(pair1);
        } else return this.paramComparisons.getOrDefault(pair2, ParamComparison.NC);
    }

    /**
     * Sets the comparison of parameter a to parameter b.
     *
     * @param a          a {@link edu.cmu.tetrad.sem.Parameter} object
     * @param b          a {@link edu.cmu.tetrad.sem.Parameter} object
     * @param comparison a {@link edu.cmu.tetrad.sem.ParamComparison} object
     */
    public void setParamComparison(Parameter a, Parameter b,
                                   ParamComparison comparison) {
        if (a == null || b == null || comparison == null) {
            throw new NullPointerException();
        }

        ParameterPair pair1 = new ParameterPair(a, b);
        ParameterPair pair2 = new ParameterPair(b, a);

        this.paramComparisons.remove(pair2);
        this.paramComparisons.remove(pair1);

        if (comparison != ParamComparison.NC) {
            this.paramComparisons.put(pair1, comparison);
        }
    }


    /**
     * <p>getFreeParameters.</p>
     *
     * @return a {@link java.util.List} object
     */
    public List<Parameter> getFreeParameters() {
        List<Parameter> parameters = getParameters();

        List<Parameter> freeParameters = new ArrayList<>();

        for (Parameter _parameter : parameters) {
            ParamType type = _parameter.getType();

            if (type == ParamType.VAR || type == ParamType.COVAR || type == ParamType.COEF) {
                if (!_parameter.isFixed()) {
                    freeParameters.add(_parameter);
                }
            }
        }

        return freeParameters;
    }

    /**
     * <p>getDof.</p>
     *
     * @return the degrees of freedom for the model.
     */
    public int getDof() {
        int numMeasured = getMeasuredNodes().size();
        int numFreeParams = getFreeParameters().size();

        for (Parameter param : getFreeParameters()) {
            if (param.isFixed()) {
                throw new IllegalArgumentException();
            }
        }

        return (numMeasured * (numMeasured + 1)) / 2 - numFreeParams;
    }

    /**
     * <p>toString.</p>
     *
     * @return a {@link java.lang.String} object
     */
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append("\nParameters:\n");

        for (Parameter parameter : this.parameters) {
            buf.append("\n").append(parameter);
        }

        buf.append("\n\nNodes: ");
        buf.append(this.nodes);

        buf.append("\n\nVariable nodes: ");
        buf.append(this.variableNodes);

        return buf.toString();
    }

    private void initializeNodes(SemGraph graph) {
        this.nodes = Collections.unmodifiableList(graph.getNodes());
    }

    private void initializeVariableNodes() {
        List<Node> varNodes = new ArrayList<>();

        for (Node node : this.nodes) {

            if (node.getNodeType() == NodeType.MEASURED || node.getNodeType() == NodeType.SELECTION ||
                node.getNodeType() == NodeType.LATENT) {
                varNodes.add(node);
            }
        }

        this.variableNodes = Collections.unmodifiableList(varNodes);
    }

    private void initializeParams() {

        // Note that a distinction between parameterizable and non-parameterizable nodes is being added
        // to accomodate time lag graphs. jdramsey 4/14/10.

        List<Parameter> parameters = new ArrayList<>();
        List<Edge> edges = new ArrayList<>(this.graph.getEdges());

        edges.sort((o1, o2) -> {
            int compareFirst = o1.getNode1().getName().compareTo(o2.getNode1().toString());
            int compareSecond = o1.getNode2().getName().compareTo(o2.getNode2().toString());

            if (compareFirst != 0) {
                return compareFirst;
            }

            return compareSecond;
        });

        // Add linear coefficient freeParameters for all directed edges that
        // aren't error edges *and are into parameterizable node* (the last bit jdramsey 4/14/10).
        for (Edge edge : edges) {
            if (edge.getNode1() == edge.getNode2()) {
                continue;
            }

            if (!SemGraph.isErrorEdge(edge) &&
                edge.getEndpoint1() == Endpoint.TAIL &&
                edge.getEndpoint2() == Endpoint.ARROW) {
                if (!this.graph.isParameterizable(edge.getNode2())) {
                    continue;
                }

                Parameter param = new Parameter(newBName(), ParamType.COEF,
                        edge.getNode1(), edge.getNode2());

                param.setDistribution(new Split(0, 1));
                parameters.add(param);
            }
        }

        // Add error variance freeParameters for exogenous nodes of all *parameterizable* nodes.
        for (Node node : getVariableNodes()) {
            if (!this.graph.isParameterizable(node)) {
                continue;
            }

            Parameter param = new Parameter(newTName(), ParamType.VAR, node, node);
            param.setDistribution(new Uniform(1.0, 3.0));
            parameters.add(param);
        }

        // Add error covariance freeParameters for all bidirected edges. (These
        // connect exogenous nodes.)
        for (Edge edge : edges) {
            if (Edges.isBidirectedEdge(edge)) {
                Node node1 = edge.getNode1();
                Node node2 = edge.getNode2();

                // no...
                if (!this.graph.isParameterizable(node1) || !this.graph.isParameterizable(node2)) {
                    continue;
                }

                node1 = getGraph().getVarNode(node1);
                node2 = getGraph().getVarNode(node2);

                Parameter param = new Parameter(newTName(), ParamType.COVAR,
                        node1, node2);
                param.setDistribution(new SingleValue(0.2));
                parameters.add(param);
            }
        }

        // Add mean freeParameters for all parameterizable nodes.
        for (Node node : getVariableNodes()) {
            if (!this.graph.isParameterizable(node)) {
                continue;
            }

            Parameter mean = new Parameter(newMName(), ParamType.MEAN, node,
                    node);
            mean.setDistribution(new Normal(0.0, 1.0));
            parameters.add(mean);
        }

        this.parameters = Collections.unmodifiableList(parameters);
    }

    /**
     * @return a unique (for this PM) parameter name beginning with the Greek letter theta.
     */
    private String newTName() {
        return "T" + (++this.tIndex);
    }

    /**
     * @return a unique (for this PM) parameter name beginning with the Greek letter mu.
     */
    private String newMName() {
        return "M" + (++this.mIndex);
    }

    /**
     * @return a unique (for this PM) parameter name beginning with the letter "B".
     */
    private String newBName() {
        return "B" + (++this.bIndex);
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
}





