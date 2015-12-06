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

package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.PM;
import edu.cmu.tetrad.util.TetradSerializable;
import edu.cmu.tetrad.util.dist.Normal;
import edu.cmu.tetrad.util.dist.SingleValue;
import edu.cmu.tetrad.util.dist.Split;
import edu.cmu.tetrad.util.dist.Uniform;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.*;

/**
 * Parametric model for Structural Equation Models.
 * <p>
 * Note: Could not
 * get a copy constructor to work properly, so to copy SemPm objects, use object
 * serialization--e.g. java.rmu.MarshalledObject.
 *
 * @author Donald Crimbchin
 * @author Joseph Ramsey
 */
public final class SemPm implements PM, TetradSerializable {
    static final long serialVersionUID = 23L;

    /**
     * The structural model graph that this sem parametric model is based on.
     *
     * @serial Cannot be null.
     */
    private SemGraph graph;

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
     * The index of the most recent "T" parameter. (These are variance and
     * covariance terms.)
     *
     * @serial Range >= 0.
     */
    private int tIndex = 0;

    /**
     * The index of the most recent "M" parameter. (These are means.)
     *
     * @serial Range >= 0.
     */
    private int mIndex = 0;

    /**
     * The index of the most recent "B" parameter. (These are edge
     * coefficients.)
     *
     * @serial Range >= 0.
     */
    private int bIndex = 0;

    //===========================CONSTRUCTORS==========================//

    /**
     * Constructs a BayesPm from the given Graph, which must be convertible
     * first into a ProtoSemGraph and then into a SemGraph.
     */
    public SemPm(Graph graph) {
        this(new SemGraph(graph));
    }

    /**
     * Constructs a new SemPm from the given SemGraph.
     */
    public SemPm(SemGraph graph) {
        if (graph == null) {
            throw new NullPointerException("Graph must not be null.");
        }

        this.graph = graph;
        this.graph.setShowErrorTerms(false);

        initializeNodes(graph);
        initializeVariableNodes();

        initializeParams();
    }

    /**
     * Copy constructor.
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
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static SemPm serializableInstance() {
        return new SemPm(Dag.serializableInstance());
    }

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

    public void fixOneLoadingPerLatent() {
        for (Node x : graph.getNodes()) {
            if (x.getNodeType() != NodeType.LATENT) {
                continue;
            }

            for (Node y : graph.getAdjacentNodes(x)) {
                if (y.getNodeType() != NodeType.MEASURED) {
                    continue;
                }

                Edge edge = graph.getEdge(x, y);

                if (!edge.pointsTowards(y)) {
                    continue;
                }

                Parameter p = getParameter(x, y);

                if (p == null) throw new IllegalArgumentException();

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

    //============================PUBLIC METHODS========================//

    /**
     * @return the structural model graph this SEM PM is using.
     */
    public SemGraph getGraph() {
        return this.graph;
    }

    /**
     * @return a list of all the freeParameters, including variance, covariance,
     * coefficient, and mean freeParameters.
     */
    public List<Parameter> getParameters() {
        return this.parameters;
    }

    /**
     * @return the list of variable nodes--that is, node that are not error
     * nodes.
     */
    public List<Node> getVariableNodes() {
        return this.variableNodes;
    }

//    /**
//     * @return the list of exogenous variableNodes.
//     */
//    public List<Node> getErrorNodes() {
//        List<Node> errorNodes = new ArrayList<>();
//
//        for (Node node1 : this.nodes) {
//            if (node1.getNodeType() == NodeType.ERROR) {
//                errorNodes.add(node1);
//            }
//        }
//
//        return errorNodes;
//    }

    /**
     * @return the list of measured variableNodes.
     */
    public List<Node> getMeasuredNodes() {
        List<Node> measuredNodes = new ArrayList<>();

        for (Node variable : getVariableNodes()) {
            if (variable.getNodeType() == NodeType.MEASURED) {
                measuredNodes.add(variable);
            }
        }

        return measuredNodes;
    }

    /**
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
     * @return the first parameter encountered with the given name, or null if
     * there is no such parameter.
     */
    public Parameter getParameter(String name) {
        for (Parameter parameter1 : getParameters()) {
            if (name.equals(parameter1.getName())) {
                return parameter1;
            }
        }

        return null;
    }

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
     * Return the parameter for the variance of the error term for the given
     * node, which is the variance of the node if the node is an error term,
     * and the variance of the node's error term if not.
     */
    public Parameter getVarianceParameter(Node node) {
//        if (getGraph().getChildren(node).size() == 0) {
//            return null;
//        }

        if (node.getNodeType() == NodeType.ERROR) {
            node = getGraph().getChildren(node).get(0);
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

    public Parameter getCovarianceParameter(Node nodeA, Node nodeB) {
        getGraph().setShowErrorTerms(true);
        nodeA = getGraph().getExogenous(nodeA);
        nodeB = getGraph().getExogenous(nodeB);

        if (nodeA.getNodeType() == NodeType.ERROR) {
            nodeA = getGraph().getChildren(nodeA).get(0);
        }

        if (nodeB.getNodeType() == NodeType.ERROR) {
            nodeB = getGraph().getChildren(nodeB).get(0);
        }

        for (Parameter parameter : this.parameters) {
//            Node _nodeA = parameter.getNodeA();
//            Node _nodeB = parameter.getNodeB();

            if (parameter.getNodeA() == nodeA && parameter.getNodeB() == nodeB) {
                return parameter;
            }

            if (parameter.getNodeA() == nodeB && parameter.getNodeA() == nodeA) {
                return parameter;
            }

//            if (nodeA == _nodeA && nodeB == _nodeB && parameter.getType() == ParamType.COVAR) {
//                return parameter;
//            } else if (nodeB == _nodeA && nodeA == _nodeB && parameter.getType() == ParamType.COVAR) {
//                return parameter;
//            }
        }

        return null;
    }

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
     * @return the list of measured variable names in the order in which they
     * appear in the list of nodes. (This order is fixed.)
     */
    public String[] getMeasuredVarNames() {
        List<Node> semPmVars = getVariableNodes();
        List<String> varNamesList = new ArrayList<>();

        for (Node semPmVar : semPmVars) {
            if (semPmVar.getNodeType() == NodeType.MEASURED) {
                varNamesList.add(semPmVar.toString());
            }
        }

        return varNamesList.toArray(new String[varNamesList.size()]);
    }

    /**
     * @return the comparison of parmeter a to parameter b.
     */
    public ParamComparison getParamComparison(Parameter a, Parameter b) {
        if (a == null || b == null) {
            throw new NullPointerException();
        }

        ParameterPair pair1 = new ParameterPair(a, b);
        ParameterPair pair2 = new ParameterPair(b, a);

        if (paramComparisons.containsKey(pair1)) {
            return paramComparisons.get(pair1);
        } else if (paramComparisons.containsKey(pair2)) {
            return paramComparisons.get(pair2);
        } else {
            return ParamComparison.NC;
        }
    }

    /**
     * Sets the comparison of parameter a to parameter b.
     */
    public void setParamComparison(Parameter a, Parameter b,
                                   ParamComparison comparison) {
        if (a == null || b == null || comparison == null) {
            throw new NullPointerException();
        }

        ParameterPair pair1 = new ParameterPair(a, b);
        ParameterPair pair2 = new ParameterPair(b, a);

        paramComparisons.remove(pair2);
        paramComparisons.remove(pair1);

        if (comparison != ParamComparison.NC) {
            paramComparisons.put(pair1, comparison);
        }
    }


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

    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append("\nParameters:\n");

        for (Parameter parameter : parameters) {
            buf.append("\n").append(parameter);
        }

        buf.append("\n\nNodes: ");
        buf.append(nodes);

        buf.append("\n\nVariable nodes: ");
        buf.append(variableNodes);

        return buf.toString();
    }

    //============================PRIVATE METHODS======================//

    private void initializeNodes(SemGraph graph) {
        this.nodes = Collections.unmodifiableList(graph.getNodes());
    }

    private void initializeVariableNodes() {
        List<Node> varNodes = new ArrayList<>();

        for (Node node : this.nodes) {

            if (node.getNodeType() == NodeType.MEASURED ||
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
        Set<Edge> edges = graph.getEdges();

        Collections.sort(new ArrayList<>(edges), new Comparator<Edge>() {
            public int compare(Edge o1, Edge o2) {
                int compareFirst = o1.getNode1().getName().compareTo(o2.getNode1().toString());
                int compareSecond = o1.getNode2().getName().compareTo(o2.getNode2().toString());

                if (compareFirst != 0) {
                    return compareFirst;
                }

                if (compareSecond != 0) {
                    return compareSecond;
                }

                return 0;
            }
        });

        // Add linear coefficient freeParameters for all directed edges that
        // aren't error edges *and are into parameterizable node* (the last bit jdramsey 4/14/10).
        for (Edge edge : edges) {
            if (edge.getNode1() == edge.getNode2()) {
                throw new IllegalStateException("There should not be any" +
                        "edges from a node to itself in a SemGraph: " + edge);
            }

            if (!SemGraph.isErrorEdge(edge) &&
                    edge.getEndpoint1() == Endpoint.TAIL &&
                    edge.getEndpoint2() == Endpoint.ARROW) {
                if (!graph.isParameterizable(edge.getNode2())) {
                    continue;
                }

                Parameter param = new Parameter(newBName(), ParamType.COEF,
                        edge.getNode1(), edge.getNode2());

                param.setDistribution(new Split(0.5, 1.5));
//                param.setDistribution(new SplitDistributionSpecial(0.5, 1.5));
//                param.setDistribution(new UniformDistribution(-0.2, 0.2));
//                param.setDistribution(coefDistribution);
                parameters.add(param);
            }
        }

        // Add error variance freeParameters for exogenous nodes of all *parameterizable* nodes.
        for (Node node : getVariableNodes()) {
            if (!graph.isParameterizable(node)) {
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
                if (!graph.isParameterizable(node1) || !graph.isParameterizable(node2)) {
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
            if (!graph.isParameterizable(node)) {
                continue;
            }

            Parameter mean = new Parameter(newMName(), ParamType.MEAN, node,
                    node);
            mean.setDistribution(new Normal(0.0, 1.0));
            parameters.add(mean);
        }

        this.parameters = Collections.unmodifiableList(parameters);
    }

//    // unfinished
//    private void initializeParamsTimeLagModel() {
//        if (!graph.isTimeLagModel()) {
//            throw new IllegalArgumentException();
//        }
//
//        SemGraph graph = this.graph;
//        TimeLagGraph timeLagGraph = graph.getTimeLagGraph();
//
//        List<Node> lag0Nodes = timeLagGraph.getLag0Nodes();
//
//        for (Node node : lag0Nodes) {
//            Parameter param = new Parameter(newTName(), ParamType.VAR, node, node);
//            param.setDistribution(new Uniform(1.0, 3.0));
//            parameters.add(param);
//
//        }
//
//        List<Parameter> parameters = new ArrayList<>();
//        List<Edge> edges = new ArrayList<>(graph.getEdges());
//
//        Collections.sort(edges, new Comparator<Edge>() {
//            public int compare(Edge o1, Edge o2) {
//                int compareFirst = o1.getNode1().getName().compareTo(o2.getNode1().toString());
//                int compareSecond = o1.getNode1().getName().compareTo(o2.getNode2().toString());
//
//                if (compareFirst != 0) {
//                    return compareFirst;
//                }
//
//                if (compareSecond != 0) {
//                    return compareSecond;
//                }
//
//                return 0;
//            }
//        });
//
//        // Add linear coefficient freeParameters for all directed edges that
//        // aren't error edges *and are into parameterizable node* (the last bit jdramsey 4/14/10).
//        for (Edge edge : edges) {
//            if (edge.getNode1() == edge.getNode2()) {
//                throw new IllegalStateException("There should not be any" +
//                        "edges from a node to itself in a SemGraph: " + edge);
//            }
//
//            if (!SemGraph.isErrorEdge(edge) &&
//                    edge.getEndpoint1() == Endpoint.TAIL &&
//                    edge.getEndpoint2() == Endpoint.ARROW) {
//                if (!graph.isParameterizable(edge.getNode2())) {
//                    continue;
//                }
//
//                Parameter param = new Parameter(newBName(), ParamType.COEF,
//                        edge.getNode1(), edge.getNode2());
//
//                param.setDistribution(new Split(0.5, 1.5));
////                param.setDistribution(new SplitDistributionSpecial(0.5, 1.5));
////                param.setDistribution(new UniformDistribution(-0.2, 0.2));
////                param.setDistribution(coefDistribution);
//                parameters.add(param);
//            }
//        }
//
//        // Add error variance freeParameters for exogenous nodes of all *parameterizable* nodes.
//        for (Node node : getVariableNodes()) {
//            if (!graph.isParameterizable(node)) {
//                continue;
//            }
//
//            Parameter param = new Parameter(newTName(), ParamType.VAR, node, node);
//            param.setDistribution(new Uniform(1.0, 3.0));
//            parameters.add(param);
//        }
//
//        // Add error covariance freeParameters for all bidirected edges. (These
//        // connect exogenous nodes.)
//        for (Edge edge : edges) {
//            if (Edges.isBidirectedEdge(edge)) {
//                Node node1 = edge.getNode1();
//                Node node2 = edge.getNode2();
//
//                // no...
//                if (!graph.isParameterizable(node1) || !graph.isParameterizable(node2)) {
//                    continue;
//                }
//
//                node1 = getGraph().getVarNode(node1);
//                node2 = getGraph().getVarNode(node2);
//
//                Parameter param = new Parameter(newTName(), ParamType.COVAR,
//                        node1, node2);
//                param.setDistribution(new SingleValue(0.2));
//                parameters.add(param);
//            }
//        }
//
//        // Add mean freeParameters for all parameterizable nodes.
//        for (Node node : getVariableNodes()) {
//            if (!graph.isParameterizable(node)) {
//                continue;
//            }
//
//            Parameter mean = new Parameter(newMName(), ParamType.MEAN, node,
//                    node);
//            mean.setDistribution(new Normal(0.0, 1.0));
//            parameters.add(mean);
//        }
//
//        this.parameters = Collections.unmodifiableList(parameters);
//    }

    /**
     * @return a unique (for this PM) parameter name beginning with the Greek
     * letter theta.
     */
    private String newTName() {
        return "T" + (++this.tIndex);
    }

    /**
     * @return a unique (for this PM) parameter name beginning with the Greek
     * letter mu.
     */
    private String newMName() {
        return "M" + (++this.mIndex);
    }

    /**
     * @return a unique (for this PM) parameter name beginning with the letter
     * "B".
     */
    private String newBName() {
        return "B" + (++this.bIndex);
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

        if (nodes == null) {
            throw new NullPointerException();
        }

        if (parameters == null) {
            throw new NullPointerException();
        }

//        if (means == null) {
//            throw new NullPointerException();
//        }

        if (variableNodes == null) {
            throw new NullPointerException();
        }

//        if (exogenousNodes == null) {
//            throw new NullPointerException();
//        }

        if (paramComparisons == null) {
            throw new NullPointerException();
        }

        if (tIndex < 0) {
            throw new IllegalStateException("TIndex out of range: " + tIndex);
        }

        if (mIndex < 0) {
            throw new IllegalStateException("MIndex out of range: " + mIndex);
        }

        if (bIndex < 0) {
            throw new IllegalStateException("BIndex out of range: " + bIndex);
        }
    }
}





