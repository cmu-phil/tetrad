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

package edu.cmu.tetrad.bayes;

import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.data.VariableSource;
import edu.cmu.tetrad.graph.Dag;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.util.PM;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TetradSerializable;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.*;

/**
 * Implements a discrete Bayes parametric model--that is, a DAG together with a
 * map from the nodes in the graph to a set of discrete variables, specifying
 * the number of categories for each variable and the name of each category for
 * each variable. This is all the information one needs to know in order to
 * determine the parametric form of a Bayes net up to actual values of
 * parameters. Specific values for the Bayes net are stored in a BayesIM object
 * (see).
 *
 * @author Joseph Ramsey jdramsey@andrew.cmu.edu
 * @see edu.cmu.tetrad.graph.Dag
 * @see BayesIm
 */
public final class BayesPm implements PM, VariableSource, TetradSerializable {
    static final long serialVersionUID = 23L;

    /**
     * The underlying graph that's being parameterized.
     *
     * @serial Cannot be null.
     */
    private Dag dag;

    /**
     * The map from nodes to variables.
     *
     * @serial Cannot be null.
     */
    private Map<Node, DiscreteVariable> nodesToVariables;

    //=========================CONSTRUCTORS=============================//

    /**
     * Construct a new BayesPm using the given DAG, assigning each variable two
     * values named "value1" and "value2" unless nodes are discrete variables
     * with categories already defined.
     */
    public BayesPm(Graph graph) {
        if (graph == null) {
            throw new NullPointerException("The graph must not be null.");
        }
        this.dag = new Dag(graph);
        this.nodesToVariables = new HashMap<Node, DiscreteVariable>();

        boolean allDiscreteVars = true;
        for (Node node : graph.getNodes()) {
            if (!(node instanceof DiscreteVariable)) {
                allDiscreteVars = false;
                break;
            }
        }

        if (!allDiscreteVars) {
            initializeValues(2, 2);
        }
        else {
            for (Node node : dag.getNodes()) {
                nodesToVariables.put(node, (DiscreteVariable)node);
            }
        }
    }

    /**
     * Constructs a new BayesPm using a given DAG, using as much information
     * from the old BayesPm as possible.
     */
    public BayesPm(Graph graph, BayesPm oldBayesPm) {
        this(graph, oldBayesPm, 2, 2);
    }

    /**
     * Constructs a new BayesPm from the given DAG, assigning each variable a
     * random number of values between <code>lowerBound</code> and
     * <code>upperBound</code>. Uses a fixed number of values if lowerBound ==
     * upperBound. The values are named "value1" ... "valuen".
     */
    public BayesPm(Graph graph, int lowerBound, int upperBound) {
        if (graph == null) {
            throw new NullPointerException("The graph must not be null.");
        }

//        if (graph.getNumNodes() == 0) {
//            throw new IllegalArgumentException(
//                    "The graph must have at least " + "one node in it.");
//        }

        this.dag = new Dag(graph);
        this.nodesToVariables = new HashMap<Node, DiscreteVariable>();
        initializeValues(lowerBound, upperBound);
    }

    /**
     * Constructs a new BayesPm from the given DAG, using as much information
     * from the old BayesPm as possible. For variables not in the old BayesPm,
     * assigns each variable a random number of values between
     * <code>lowerBound</code> and <code>upperBound</code>. Uses a fixed number
     * of values if lowerBound == upperBound. The values are named "value1" ...
     * "valuen".
     */
    public BayesPm(Graph graph, BayesPm oldBayesPm, int lowerBound,
            int upperBound) {

        // Should be OK wrt variable mismatch problems. jdramsey 2004/1/21

        if (graph == null) {
            throw new NullPointerException("The graph must not be null.");
        }

        if (oldBayesPm == null) {
            throw new NullPointerException("BayesPm must not be null.");
        }

        if (graph.getNumNodes() == 0) {
            throw new IllegalArgumentException(
                    "The graph must have at least " + "one node in it.");
        }

        this.dag = new Dag(graph);
        this.nodesToVariables = new HashMap<Node, DiscreteVariable>();
        copyAvailableInformationFromOldBayesPm(oldBayesPm, lowerBound,
                upperBound);
    }

    /**
     * Copy constructor.
     */
    public BayesPm(BayesPm bayesPm) {
        this.dag = new Dag(bayesPm.dag);
        this.nodesToVariables = new HashMap<Node, DiscreteVariable>();

        for (Node node : bayesPm.nodesToVariables.keySet()) {
            DiscreteVariable variable = bayesPm.nodesToVariables.get(node);
            DiscreteVariable newVariable = new DiscreteVariable(variable);

            newVariable.setNodeType(node.getNodeType());

            this.nodesToVariables.put(node, newVariable);
        }
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static BayesPm serializableInstance() {
        return new BayesPm(Dag.serializableInstance());
    }

    //=========================PUBLIC METHODS=============================//

    /**
     * @return the DAG as a Graph.
     */
    public Dag getDag() {
        return this.dag;
    }

    /**
     * @return the number of values for the given node.
     */
    public int getNumCategories(Node node) {
        DiscreteVariable variable = nodesToVariables.get(node);

        if (variable == null) {
            return 0;
        }

        return variable.getNumCategories();
    }

    /**
     * @return the index'th value for the given node.
     */
    public String getCategory(Node node, int index) {
        DiscreteVariable variable = nodesToVariables.get(node);

        if (variable != null) {
            return variable.getCategory(index);
        }

        for (DiscreteVariable _node : nodesToVariables.values()) {
            if (_node == null) {
                continue;
            }

            if (_node.getName().equals(node.getName())) {
                return _node.getCategory(index);
            }
        }

        throw new IllegalStateException();
    }

    /**
     * @return the index of the given category for the given node.
     */
    public int getCategoryIndex(Node node, String category) {
        DiscreteVariable variable = nodesToVariables.get(node);
        return variable.getIndex(category);
    }

    /**
     * Sets the number of values for the given node to the given number.
     */
    public void setNumCategories(Node node, int numCategories) {
        if (!nodesToVariables.containsKey(node)) {
            throw new IllegalArgumentException("Node not in BayesPm: " + node);
        }

        if (numCategories < 1) {
            throw new IllegalArgumentException(
                    "Number of categories must be >= 1: " + numCategories);
        }

        DiscreteVariable variable = nodesToVariables.get(node);

        List<String> oldCategories = variable.getCategories();
        List<String> newCategories = new LinkedList<String>();
        int min = Math.min(numCategories, oldCategories.size());

        for (int i = 0; i < min; i++) {
            newCategories.add(oldCategories.get(i));
        }

        for (int i = min; i < numCategories; i++) {
            String proposedName = DataUtils.defaultCategory(i);

            if (newCategories.contains(proposedName)) {
                throw new IllegalArgumentException("Default name already in " +
                        "list of categories: " + proposedName);
            }

            newCategories.add(proposedName);
        }

        mapNodeToVariable(node, newCategories);
    }

    /**
     * Will return true if the argument is a BayesPm with the same graph and
     * variables.
     */
    public boolean equals(Object o) {
        if (o == null) {
            return false;
        }

        BayesPm bayesPm = (BayesPm) o;

        if (!bayesPm.dag.equals(this.dag)) {
            return false;
        }

        return bayesPm.nodesToVariables.equals(this.nodesToVariables);
    }

    public void setCategories(Node node, List<String> categories) {
        mapNodeToVariable(node, categories);
    }

    public List<Node> getVariables() {
        List<Node> variables = new LinkedList<Node>();

        for (Node node : nodesToVariables.keySet()) {
            variables.add(nodesToVariables.get(node));
        }

        return variables;
    }

    public List<String> getVariableNames() {
        List<Node> variables = getVariables();
        List<String> names = new ArrayList<String>();

        for (Node variable : variables) {
            DiscreteVariable discreteVariable = (DiscreteVariable) variable;
            names.add(discreteVariable.getName());
        }

        return names;
    }

    public Node getVariable(Node node) {
        return this.nodesToVariables.get(node);
    }

    /**
     * @return the list of measured variableNodes.
     */
    public List<Node> getMeasuredNodes() {
        List<Node> measuredNodes = new ArrayList<Node>();

        for (Node variable : getVariables()) {
            if (variable.getNodeType() == NodeType.MEASURED) {
                measuredNodes.add((Node) variable);
            }
        }

        return measuredNodes;
    }


    /**
     * Prints out the list of values for each node.
     */
    public String toString() {
        StringBuilder buf = new StringBuilder();

        for (Node node1 : nodesToVariables.keySet()) {
            Node node = (node1);
            buf.append("\n");
            buf.append(node);
            buf.append(": ");

            DiscreteVariable variable = nodesToVariables.get(node);

            for (int j = 0; j < variable.getNumCategories(); j++) {
                buf.append(variable.getCategory(j));
                if (j < variable.getNumCategories() - 1) {
                    buf.append(", ");
                }
            }
        }

        return buf.toString();
    }

    public Node getNode(String nodeName) {
        return dag.getNode(nodeName);
    }

    public Node getNode(int index) {
        return null;
    }

    public int getNodeIndex(Node node) {
        return -1;
    }

    public int getNumNodes() {
        return dag.getNumNodes();
    }

    //=========================PRIVATE METHODS=============================//

    private void copyAvailableInformationFromOldBayesPm(BayesPm oldbayesPm,
            int lowerBound, int upperBound) {
        Graph newGraph = getDag();
        Graph oldGraph = oldbayesPm.getDag();

        for (Node node1 : newGraph.getNodes()) {
            if (oldGraph.containsNode(node1)) {
                copyOldValues(oldbayesPm, node1, node1, lowerBound, upperBound);
            }
            else {
                setNewValues(node1, lowerBound, upperBound);
            }
        }

        for (Node node2 : newGraph.getNodes()) {
            if (oldGraph.containsNode(node2)) {
                Node _node2 = dag.getNode(node2.getName());
                DiscreteVariable oldNode2 = oldbayesPm.nodesToVariables.get(_node2);
                oldNode2.setNodeType(node2.getNodeType());
                this.nodesToVariables.put(_node2, oldNode2);
            }
            else {
                setNewValues(node2, lowerBound, upperBound);
            }
        }
    }

    private void copyOldValues(BayesPm oldBayesPm, Node oldNode, Node node,
            int lowerBound, int upperBound) {
        List<String> values = new ArrayList<String>();

        List<String> oldNames = new LinkedList<String>();
        List<Node> oldNodes = oldBayesPm.getDag().getNodes();

        for (Node oldNode1 : oldNodes) {
            oldNames.add(oldNode1.getName());
        }

        int numVals;

        if (oldNames.contains(node.getName())) {
            Node oldNode2 = oldBayesPm.getDag().getNode(node.getName());
            numVals = oldBayesPm.getNumCategories(oldNode2);
        }
        else {
            numVals = pickNumVals(lowerBound, upperBound);
        }

        int min = Math.min(oldBayesPm.getNumCategories(oldNode), numVals);

        for (int i = 0; i < min; i++) {
            values.add(oldBayesPm.getCategory(oldNode, i));
        }

        for (int i = min; i < numVals; i++) {
            String proposedName = DataUtils.defaultCategory(i);

            if (values.contains(proposedName)) {
                throw new IllegalArgumentException("Default name already in " +
                        "list of values: " + proposedName);
            }

            values.add(proposedName);
        }

        mapNodeToVariable(node, values);
    }

    private void setNewValues(Node node, int lowerBound, int upperBound) {
        if (node == null) {
            throw new NullPointerException("Node must not be null.");
        }

        List<String> valueList = new ArrayList<String>();

        for (int i = 0; i < pickNumVals(lowerBound, upperBound); i++) {
            valueList.add(DataUtils.defaultCategory(i));
        }

        mapNodeToVariable(node, valueList);
    }

    private void mapNodeToVariable(Node node, List<String> categories) {
        if (categories.size() != new HashSet<String>(categories).size()) {
            throw new IllegalArgumentException("Duplicate variable names.");
        }

        DiscreteVariable variable =
                new DiscreteVariable(node.getName(), categories);

        variable.setNodeType(node.getNodeType());

        this.nodesToVariables.put(node, variable);
    }

    private void initializeValues(int lowerBound, int upperBound) {
        for (Node node : dag.getNodes()) {
            setNewValues(node, lowerBound, upperBound);
        }
    }

    private static int pickNumVals(int lowerBound, int upperBound) {
        if (lowerBound < 2) {
            throw new IllegalArgumentException(
                    "Lower bound must be >= 2: " + lowerBound);
        }

        if (upperBound < lowerBound) {
            throw new IllegalArgumentException(
                    "Upper bound must be >= lower " + "bound.");
        }

        int difference = upperBound - lowerBound;
        RandomUtil randomUtil = RandomUtil.getInstance();
        return randomUtil.nextInt(difference + 1) + lowerBound;
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
     * @throws IOException
     * @throws ClassNotFoundException
     */
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        if (dag == null) {
            throw new NullPointerException();
        }

        if (nodesToVariables == null) {
            throw new NullPointerException();
        }
    }
}





