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
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.PM;
import edu.cmu.tetrad.util.RandomUtil;

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
public final class BayesPm implements PM, VariableSource {
    static final long serialVersionUID = 23L;

    /**
     * The underlying graph that's being parameterized.
     *
     * @serial Cannot be null.
     */
    private final Graph dag;

    /**
     * The map from nodes to variables.
     *
     * @serial Cannot be null.
     */
    private final Map<Node, DiscreteVariable> nodesToVariables;

    //=========================CONSTRUCTORS=============================//

    /**
     * Construct a new BayesPm using the given DAG, assigning each variable two
     * values named "value1" and "value2" unless nodes are discrete variables
     * with categories already defined.
     */
    public BayesPm(final Graph graph) {
        if (graph == null) {
            throw new NullPointerException("The graph must not be null.");
        }
        this.dag = new EdgeListGraph(graph);
        this.nodesToVariables = new HashMap<>();

        boolean allDiscreteVars = true;
        for (final Node node : graph.getNodes()) {
            if (!(node instanceof DiscreteVariable)) {
                allDiscreteVars = false;
                break;
            }
        }

        if (!allDiscreteVars) {
            initializeValues(2, 2);
        } else {
            for (final Node node : this.dag.getNodes()) {
                this.nodesToVariables.put(node, (DiscreteVariable) node);
            }
        }
    }

    /**
     * Constructs a new BayesPm using a given DAG, using as much information
     * from the old BayesPm as possible.
     */
    public BayesPm(final Graph graph, final BayesPm oldBayesPm) {
        this(graph, oldBayesPm, 2, 2);
    }

    /**
     * Constructs a new BayesPm from the given DAG, assigning each variable a
     * random number of values between <code>lowerBound</code> and
     * <code>upperBound</code>. Uses a fixed number of values if lowerBound ==
     * upperBound. The values are named "value1" ... "valuen".
     */
    public BayesPm(final Graph graph, final int lowerBound, final int upperBound) {
        if (graph == null) {
            throw new NullPointerException("The graph must not be null.");
        }

        this.dag = new EdgeListGraph(graph);
        this.nodesToVariables = new HashMap<>();
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
    public BayesPm(final Graph graph, final BayesPm oldBayesPm, final int lowerBound,
                   final int upperBound) {

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

        this.dag = new EdgeListGraph(graph);
        this.nodesToVariables = new HashMap<>();
        copyAvailableInformationFromOldBayesPm(oldBayesPm, lowerBound,
                upperBound);
    }

    /**
     * Copy constructor.
     */
    public BayesPm(final BayesPm bayesPm) {
        this.dag = bayesPm.dag;
        this.nodesToVariables = new HashMap<>();

        for (final Node node : bayesPm.nodesToVariables.keySet()) {
            final DiscreteVariable variable = bayesPm.nodesToVariables.get(node);
            final DiscreteVariable newVariable = new DiscreteVariable(variable);

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
    public Graph getDag() {
        return this.dag;
    }

    public static List<String> getParameterNames() {
        final List<String> parameters = new ArrayList<>();
        parameters.add("minCategories");
        parameters.add("maxCategories");
        return parameters;
    }

    /**
     * @return the number of values for the given node.
     */
    public int getNumCategories(final Node node) {
        final DiscreteVariable variable = this.nodesToVariables.get(node);

        if (variable == null) {
            return 0;
        }

        return variable.getNumCategories();
    }

    /**
     * @return the index'th value for the given node.
     */
    public String getCategory(final Node node, final int index) {
        final DiscreteVariable variable = this.nodesToVariables.get(node);

        if (variable != null) {
            return variable.getCategory(index);
        }

        for (final DiscreteVariable _node : this.nodesToVariables.values()) {
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
    public int getCategoryIndex(final Node node, final String category) {
        final DiscreteVariable variable = this.nodesToVariables.get(node);
        return variable.getIndex(category);
    }

    /**
     * Sets the number of values for the given node to the given number.
     */
    public void setNumCategories(final Node node, final int numCategories) {
        if (!this.nodesToVariables.containsKey(node)) {
            throw new IllegalArgumentException("Node not in BayesPm: " + node);
        }

        if (numCategories < 1) {
            throw new IllegalArgumentException(
                    "Number of categories must be >= 1: " + numCategories);
        }

        final DiscreteVariable variable = this.nodesToVariables.get(node);

        final List<String> oldCategories = variable.getCategories();
        final List<String> newCategories = new LinkedList<>();
        final int min = Math.min(numCategories, oldCategories.size());

        for (int i = 0; i < min; i++) {
            newCategories.add(oldCategories.get(i));
        }

        for (int i = min; i < numCategories; i++) {
            final String proposedName = DataUtils.defaultCategory(i);

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
    public boolean equals(final Object o) {
        if (o == null) {
            return false;
        }

        if (!(o instanceof BayesPm)) {
            return false;
        }

        final BayesPm bayesPm = (BayesPm) o;

        return bayesPm.dag.equals(this.dag) && bayesPm.nodesToVariables.equals(this.nodesToVariables);

    }

    public void setCategories(final Node node, final List<String> categories) {
        mapNodeToVariable(node, categories);
    }

    public List<Node> getVariables() {
        final List<Node> variables = new LinkedList<>();

        for (final Node node : this.nodesToVariables.keySet()) {
            variables.add(this.nodesToVariables.get(node));
        }

        return variables;
    }

    public List<String> getVariableNames() {
        final List<Node> variables = getVariables();
        final List<String> names = new ArrayList<>();

        for (final Node variable : variables) {
            final DiscreteVariable discreteVariable = (DiscreteVariable) variable;
            names.add(discreteVariable.getName());
        }

        return names;
    }

    public Node getVariable(final Node node) {
        return this.nodesToVariables.get(node);
    }

    /**
     * @return the list of measured variableNodes.
     */
    public List<Node> getMeasuredNodes() {
        final List<Node> measuredNodes = new ArrayList<>();

        for (final Node variable : getVariables()) {
            if (variable.getNodeType() == NodeType.MEASURED) {
                measuredNodes.add(variable);
            }
        }

        return measuredNodes;
    }


    /**
     * Prints out the list of values for each node.
     */
    public String toString() {
        final StringBuilder buf = new StringBuilder();

        for (final Node node1 : this.nodesToVariables.keySet()) {
            buf.append("\n");
            buf.append((node1));
            buf.append(": ");

            final DiscreteVariable variable = this.nodesToVariables.get((node1));

            for (int j = 0; j < variable.getNumCategories(); j++) {
                buf.append(variable.getCategory(j));
                if (j < variable.getNumCategories() - 1) {
                    buf.append(", ");
                }
            }
        }

        return buf.toString();
    }

    public Node getNode(final String nodeName) {
        return this.dag.getNode(nodeName);
    }

    public Node getNode(final int index) {
        return getVariables().get(index);
    }

    public int getNodeIndex() {
        return -1;
    }

    public int getNumNodes() {
        return this.dag.getNumNodes();
    }

    //=========================PRIVATE METHODS=============================//

    private void copyAvailableInformationFromOldBayesPm(final BayesPm oldbayesPm,
                                                        final int lowerBound, final int upperBound) {
        final Graph newGraph = getDag();
        final Graph oldGraph = oldbayesPm.getDag();

        for (final Node node1 : newGraph.getNodes()) {
            if (oldGraph.containsNode(node1)) {
                copyOldValues(oldbayesPm, node1, node1, lowerBound, upperBound);
            } else {
                setNewValues(node1, lowerBound, upperBound);
            }
        }

        for (final Node node2 : newGraph.getNodes()) {
            if (oldGraph.containsNode(node2)) {
                final Node _node2 = this.dag.getNode(node2.getName());
                final DiscreteVariable oldNode2 = oldbayesPm.nodesToVariables.get(_node2);
                oldNode2.setNodeType(node2.getNodeType());
                this.nodesToVariables.put(_node2, oldNode2);
            } else {
                setNewValues(node2, lowerBound, upperBound);
            }
        }
    }

    private void copyOldValues(final BayesPm oldBayesPm, final Node oldNode, final Node node,
                               final int lowerBound, final int upperBound) {
        final List<String> values = new ArrayList<>();

        final List<String> oldNames = new LinkedList<>();
        final List<Node> oldNodes = oldBayesPm.getDag().getNodes();

        for (final Node oldNode1 : oldNodes) {
            oldNames.add(oldNode1.getName());
        }

        final int numVals;

        if (oldNames.contains(node.getName())) {
            final Node oldNode2 = oldBayesPm.getDag().getNode(node.getName());
            numVals = oldBayesPm.getNumCategories(oldNode2);
        } else {
            numVals = BayesPm.pickNumVals(lowerBound, upperBound);
        }

        final int min = Math.min(oldBayesPm.getNumCategories(oldNode), numVals);

        for (int i = 0; i < min; i++) {
            values.add(oldBayesPm.getCategory(oldNode, i));
        }

        for (int i = min; i < numVals; i++) {
            final String proposedName = DataUtils.defaultCategory(i);

            if (values.contains(proposedName)) {
                throw new IllegalArgumentException("Default name already in " +
                        "list of values: " + proposedName);
            }

            values.add(proposedName);
        }

        mapNodeToVariable(node, values);
    }

    private void setNewValues(final Node node, final int lowerBound, final int upperBound) {
        if (node == null) {
            throw new NullPointerException("Node must not be null.");
        }

        final List<String> valueList = new ArrayList<>();

        for (int i = 0; i < BayesPm.pickNumVals(lowerBound, upperBound); i++) {
            valueList.add(DataUtils.defaultCategory(i));
        }

        mapNodeToVariable(node, valueList);
    }

    private void mapNodeToVariable(final Node node, final List<String> categories) {
        if (categories.size() != new HashSet<>(categories).size()) {
            throw new IllegalArgumentException("Duplicate variable names.");
        }

        final DiscreteVariable variable =
                new DiscreteVariable(node.getName(), categories);

        variable.setNodeType(node.getNodeType());

        this.nodesToVariables.put(node, variable);
    }

    private void initializeValues(final int lowerBound, final int upperBound) {
        for (final Node node : this.dag.getNodes()) {
            setNewValues(node, lowerBound, upperBound);
        }
    }

    private static int pickNumVals(final int lowerBound, final int upperBound) {
        if (lowerBound < 2) {
            throw new IllegalArgumentException(
                    "Lower bound must be >= 2: " + lowerBound);
        }

        if (upperBound < lowerBound) {
            throw new IllegalArgumentException(
                    "Upper bound for number of categories must be >= lower " + "bound.");
        }

        final int difference = upperBound - lowerBound;
        final RandomUtil randomUtil = RandomUtil.getInstance();
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
     */
    private void readObject(final ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        if (this.dag == null) {
            throw new NullPointerException();
        }

        if (this.nodesToVariables == null) {
            throw new NullPointerException();
        }
    }
}





