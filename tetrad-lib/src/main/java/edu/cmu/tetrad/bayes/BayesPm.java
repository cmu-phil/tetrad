///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
//                                                                           //
// Copyright (C) 2025 by Joseph Ramsey, Peter Spirtes, Clark Glymour,        //
// and Richard Scheines.                                                     //
//                                                                           //
// This program is free software: you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation, either version 3 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program.  If not, see <https://www.gnu.org/licenses/>.    //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.bayes;

import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.data.VariableSource;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.Pm;
import edu.cmu.tetrad.util.RandomUtil;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.math3.util.FastMath;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.util.*;

/**
 * Implements a discrete Bayes parametric model--that is, a DAG together with a map from the nodes in the graph to a set
 * of discrete variables, specifying the number of categories for each variable and the name of each category for each
 * variable. This is all the information one needs to know in order to determine the parametric form of a Bayes net up
 * to actual values of parameters. Specific values for the Bayes net are stored in a BayesIM object (see).
 *
 * @author josephramsey
 * @version $Id: $Id
 * @see edu.cmu.tetrad.graph.Dag
 * @see BayesIm
 */
public final class BayesPm implements Pm, VariableSource {
    @Serial
    private static final long serialVersionUID = 23L;

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
     * Construct a new BayesPm using the given DAG, assigning each variable two values named "value1" and "value2"
     * unless nodes are discrete variables with categories already defined.
     *
     * @param graph Ibid.
     */
    public BayesPm(Graph graph) {
        if (graph == null) {
            throw new NullPointerException("The graph must not be null.");
        }
        this.dag = new EdgeListGraph(graph);
        this.nodesToVariables = new HashMap<>();

        boolean allDiscreteVars = true;
        for (Node node : graph.getNodes()) {
            if (!(node instanceof DiscreteVariable)) {
                allDiscreteVars = false;
                break;
            }
        }

        if (!allDiscreteVars) {
            initializeValues(2, 2);
        } else {
            for (Node node : this.dag.getNodes()) {
                this.nodesToVariables.put(node, (DiscreteVariable) node);
            }
        }
    }

    /**
     * Constructs a new BayesPm using a given DAG, using as much information from the old BayesPm as possible.
     *
     * @param graph      Ibid.
     * @param oldBayesPm Ibid.
     */
    public BayesPm(Graph graph, BayesPm oldBayesPm) {
        this(graph, oldBayesPm, 2, 2);
    }

    /**
     * Constructs a new BayesPm from the given DAG, assigning each variable a random number of values between
     * <code>lowerBound</code> and
     * <code>upperBound</code>. Uses a fixed number of values if lowerBound ==
     * upperBound. The values are named "value1" ... "valuen".
     *
     * @param graph      Ibid.
     * @param lowerBound Ibid.
     * @param upperBound Ibid.
     */
    public BayesPm(Graph graph, int lowerBound, int upperBound) {
        if (graph == null) {
            throw new NullPointerException("The graph must not be null.");
        }

        this.dag = new EdgeListGraph(graph);
        this.nodesToVariables = new HashMap<>();
        initializeValues(lowerBound, upperBound);
    }

    /**
     * Constructs a new BayesPm from the given DAG, using as much information from the old BayesPm as possible. For
     * variables not in the old BayesPm, assigns each variable a random number of values between
     * <code>lowerBound</code> and <code>upperBound</code>. Uses a fixed number
     * of values if lowerBound == upperBound. The values are named "value1" ... "valuen".
     *
     * @param graph      Ibid.
     * @param oldBayesPm Ibid.
     * @param lowerBound Ibid.
     * @param upperBound Ibid.
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

        this.dag = new EdgeListGraph(graph);
        this.nodesToVariables = new HashMap<>();
        copyAvailableInformationFromOldBayesPm(oldBayesPm, lowerBound,
                upperBound);
    }

    /**
     * Copy constructor.
     *
     * @param bayesPm Ibid.
     */
    public BayesPm(BayesPm bayesPm) {
        this.dag = bayesPm.dag;
        this.nodesToVariables = new HashMap<>();

        for (Node node : bayesPm.nodesToVariables.keySet()) {
            DiscreteVariable variable = bayesPm.nodesToVariables.get(node);
            DiscreteVariable newVariable = new DiscreteVariable(variable);

            newVariable.setNodeType(node.getNodeType());

            this.nodesToVariables.put(node, newVariable);
        }
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @return Ibid.
     */
    public static BayesPm serializableInstance() {
        return new BayesPm(Dag.serializableInstance());
    }


    //=========================PUBLIC METHODS=============================//

    /**
     * Returns the parameter names.
     *
     * @return Ibid.
     */
    public static List<String> getParameterNames() {
        List<String> parameters = new ArrayList<>();
        parameters.add("minCategories");
        parameters.add("maxCategories");
        return parameters;
    }

    private static int pickNumVals(int lowerBound, int upperBound) {
        if (lowerBound < 2) {
            throw new IllegalArgumentException(
                    "Lower bound must be >= 2: " + lowerBound);
        }

        if (upperBound < lowerBound) {
            throw new IllegalArgumentException(
                    "Upper bound for number of categories must be >= lower " + "bound.");
        }

        int difference = upperBound - lowerBound;
        RandomUtil randomUtil = RandomUtil.getInstance();
        return randomUtil.nextInt(difference + 1) + lowerBound;
    }

    /**
     * Returns the DAG.
     *
     * @return the DAG as a Graph.
     */
    public Graph getDag() {
        return this.dag;
    }

    /**
     * Returns the number of values for the given node.
     *
     * @param node Ibid.
     * @return the number of values for the given node.
     */
    public int getNumCategories(Node node) {
        DiscreteVariable variable = this.nodesToVariables.get(node);

        if (variable == null) {
            return 0;
        }

        return variable.getNumCategories();
    }

    /**
     * Returns the index'th value for the given node.
     *
     * @param node  Ibid.
     * @param index Ibid.
     * @return the index'th value for the given node.
     */
    public String getCategory(Node node, int index) {
        DiscreteVariable variable = this.nodesToVariables.get(node);

        if (variable != null) {
            return variable.getCategory(index);
        }

        for (DiscreteVariable _node : this.nodesToVariables.values()) {
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
     * Returns the index of the given category for the given node.
     *
     * @param node     Ibid.
     * @param category Ibid.
     * @return the index of the given category for the given node.
     */
    public int getCategoryIndex(Node node, String category) {
        DiscreteVariable variable = this.nodesToVariables.get(node);
        return variable.getIndex(category);
    }

    /**
     * Sets the number of values for the given node to the given number.
     *
     * @param node          Ibid.
     * @param numCategories Ibid.
     */
    public void setNumCategories(Node node, int numCategories) {
        if (!this.nodesToVariables.containsKey(node)) {
            throw new IllegalArgumentException("Node not in BayesPm: " + node);
        }

        if (numCategories < 1) {
            throw new IllegalArgumentException(
                    "Number of categories must be >= 1: " + numCategories);
        }

        DiscreteVariable variable = this.nodesToVariables.get(node);

        List<String> oldCategories = variable.getCategories();
        List<String> newCategories = new LinkedList<>();
        int min = FastMath.min(numCategories, oldCategories.size());

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
     * {@inheritDoc}
     * <p>
     * Will return true if the argument is a BayesPm with the same graph and variables.
     */
    public boolean equals(Object o) {
        if (o == null) {
            return false;
        }

        if (!(o instanceof BayesPm bayesPm)) {
            return false;
        }

        return bayesPm.dag.equals(this.dag) && bayesPm.nodesToVariables.equals(this.nodesToVariables);

    }

    /**
     * Sets the number of values for the given node to the given number.
     *
     * @param node       Ibid.
     * @param categories Ibid.
     */
    public void setCategories(Node node, List<String> categories) {
        mapNodeToVariable(node, categories);
    }

    /**
     * <p>getVariables.</p>
     *
     * @return a {@link java.util.List} object
     */
    public List<Node> getVariables() {
        List<Node> variables = new LinkedList<>();

        for (Node node : this.nodesToVariables.keySet()) {
            variables.add(this.nodesToVariables.get(node));
        }

        return variables;
    }

    /**
     * Returns the variable names.
     *
     * @return Ibid.
     */
    public List<String> getVariableNames() {
        List<Node> variables = getVariables();
        List<String> names = new ArrayList<>();

        for (Node variable : variables) {
            DiscreteVariable discreteVariable = (DiscreteVariable) variable;
            names.add(discreteVariable.getName());
        }

        return names;
    }

    /**
     * Returns the variable for the given node.
     *
     * @param node Ibid.
     * @return Ibid.
     */
    public Node getVariable(Node node) {
        return this.nodesToVariables.get(node);
    }

    /**
     * Returns the measured nodes.
     *
     * @return the list of measured variableNodes.
     */
    public List<Node> getMeasuredNodes() {
        List<Node> measuredNodes = new ArrayList<>();

        for (Node variable : getVariables()) {
            if (variable.getNodeType() == NodeType.MEASURED || variable.getNodeType() == NodeType.SELECTION) {
                measuredNodes.add(variable);
            }
        }

        return measuredNodes;
    }

    /**
     * Prints out the list of values for each node.
     *
     * @return Ibid.
     */
    public String toString() {
        StringBuilder buf = new StringBuilder();

        for (Node node1 : this.nodesToVariables.keySet()) {
            buf.append("\n");
            buf.append((node1));
            buf.append(": ");

            DiscreteVariable variable = this.nodesToVariables.get((node1));

            for (int j = 0; j < variable.getNumCategories(); j++) {
                buf.append(variable.getCategory(j));
                if (j < variable.getNumCategories() - 1) {
                    buf.append(", ");
                }
            }
        }

        return buf.toString();
    }

    /**
     * Returns the node by the given name.
     *
     * @param nodeName Ibid.
     * @return Ibid.
     */
    public Node getNode(String nodeName) {
        return this.dag.getNode(nodeName);
    }

    /**
     * Returns the node at the given index.
     *
     * @param index Ibid.
     * @return Ibid.
     */
    public Node getNode(int index) {
        return getVariables().get(index);
    }

    /**
     * Returns the node index.
     *
     * @return -1.
     */
    public int getNodeIndex() {
        return -1;
    }

    /**
     * Returns the number of nodes.
     *
     * @return Ibid.
     */
    public int getNumNodes() {
        return this.dag.getNumNodes();
    }

    //=========================PRIVATE METHODS=============================//

    private void copyAvailableInformationFromOldBayesPm(BayesPm oldbayesPm,
                                                        int lowerBound, int upperBound) {
        Graph newGraph = getDag();
        Graph oldGraph = oldbayesPm.getDag();

        for (Node node1 : newGraph.getNodes()) {
            if (oldGraph.containsNode(node1)) {
                copyOldValues(oldbayesPm, node1, node1, lowerBound, upperBound);
            } else {
                setNewValues(node1, lowerBound, upperBound);
            }
        }

        for (Node node2 : newGraph.getNodes()) {
            if (oldGraph.containsNode(node2)) {
                Node _node2 = this.dag.getNode(node2.getName());
                DiscreteVariable oldNode2 = oldbayesPm.nodesToVariables.get(_node2);
                oldNode2.setNodeType(node2.getNodeType());
                this.nodesToVariables.put(_node2, oldNode2);
            } else {
                setNewValues(node2, lowerBound, upperBound);
            }
        }
    }

    private void copyOldValues(BayesPm oldBayesPm, Node oldNode, Node node,
                               int lowerBound, int upperBound) {
        List<String> values = new ArrayList<>();

        List<String> oldNames = new LinkedList<>();
        List<Node> oldNodes = oldBayesPm.getDag().getNodes();

        for (Node oldNode1 : oldNodes) {
            oldNames.add(oldNode1.getName());
        }

        int numVals;

        if (oldNames.contains(node.getName())) {
            Node oldNode2 = oldBayesPm.getDag().getNode(node.getName());
            numVals = oldBayesPm.getNumCategories(oldNode2);
        } else {
            numVals = BayesPm.pickNumVals(lowerBound, upperBound);
        }

        int min = FastMath.min(oldBayesPm.getNumCategories(oldNode), numVals);

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

        List<String> valueList = new ArrayList<>();

        for (int i = 0; i < BayesPm.pickNumVals(lowerBound, upperBound); i++) {
            valueList.add(DataUtils.defaultCategory(i));
        }

        mapNodeToVariable(node, valueList);
    }

    private void mapNodeToVariable(Node node, List<String> categories) {
        if (categories.size() != new HashSet<>(categories).size()) {
            throw new IllegalArgumentException("Duplicate variable names.");
        }

        DiscreteVariable variable =
                new DiscreteVariable(node.getName(), categories);

        variable.setNodeType(node.getNodeType());

        this.nodesToVariables.put(node, variable);
    }

    private void initializeValues(int lowerBound, int upperBound) {
        for (Node node : this.dag.getNodes()) {
            setNewValues(node, lowerBound, upperBound);
        }
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






