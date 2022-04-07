/*
 * Copyright (C) 2019 University of Pittsburgh.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package edu.cmu.tetrad.bayes;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.TetradSerializable;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Junction Tree Algorithm.
 * <p>
 * This implementation follows the Weka's implementation.
 * <p>
 * Nov 8, 2019 2:22:34 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 * @see <a href="https://raw.githubusercontent.com/Waikato/weka-3.8/master/weka/src/main/java/weka/classifiers/bayes/net/MarginCalculator.java">MarginCalculator.java</a>
 */
public class JunctionTreeAlgorithm implements TetradSerializable {
    static final long serialVersionUID = 23L;

    private final TreeNode root;

    private final Node[] graphNodes;
    private final double[][] margins;
    private final Node[] maxCardOrdering;

    private final BayesPm bayesPm;
    private final BayesIm bayesIm;
    private final Map<Node, TreeNode> treeNodes;

    public JunctionTreeAlgorithm(Graph graph, DataModel dataModel) {
        this.bayesPm = createBayesPm(dataModel, graph);
        this.bayesIm = createBayesIm(dataModel, this.bayesPm);
        this.treeNodes = new HashMap<>();

        int numOfNodes = graph.getNumNodes();
        this.graphNodes = this.bayesIm.getDag().getNodes().toArray(new Node[numOfNodes]);
        this.margins = new double[numOfNodes][];
        this.maxCardOrdering = new Node[numOfNodes];
        this.root = buildJunctionTree();

        initialize();
    }

    public JunctionTreeAlgorithm(BayesIm bayesIm) {
        this.bayesPm = bayesIm.getBayesPm();
        this.bayesIm = bayesIm;
        this.treeNodes = new HashMap<>();

        int numOfNodes = this.bayesPm.getDag().getNumNodes();
        this.graphNodes = bayesIm.getDag().getNodes().toArray(new Node[numOfNodes]);
        this.margins = new double[numOfNodes][];
        this.maxCardOrdering = new Node[numOfNodes];
        this.root = buildJunctionTree();

        initialize();
    }

    private void initialize() {
        for (int i = this.maxCardOrdering.length - 1; i >= 0; i--) {
            TreeNode treeNode = this.treeNodes.get(this.maxCardOrdering[i]);
            if (treeNode != null) {
                treeNode.initializeUp();
            }
        }
        for (Node node : this.maxCardOrdering) {
            TreeNode treeNode = this.treeNodes.get(node);
            if (treeNode != null) {
                treeNode.initializeDown(false);
            }
        }
    }

    /**
     * Create the junction tree.
     *
     * @return the root of the junction tree
     */
    private TreeNode buildJunctionTree() {
        // moralize dag
        Graph undirectedGraph = GraphTools.moralize(this.bayesIm.getDag());

        // triangulate
        computeMaximumCardinalityOrdering(undirectedGraph, this.maxCardOrdering);
        GraphTools.fillIn(undirectedGraph, this.maxCardOrdering);

        // get set of cliques
        computeMaximumCardinalityOrdering(undirectedGraph, this.maxCardOrdering);
        Map<Node, Set<Node>> cliques = GraphTools.getCliques(this.maxCardOrdering, undirectedGraph);

        // get separator sets
        Map<Node, Set<Node>> separators = GraphTools.getSeparators(this.maxCardOrdering, cliques);

        // get clique tree
        Map<Node, Node> parentCliques = GraphTools.getCliqueTree(this.maxCardOrdering, cliques, separators);

        // create tree nodes
        Set<Node> finishedCalculated = new HashSet<>();
        for (Node node : this.maxCardOrdering) {
            if (cliques.containsKey(node)) {
                this.treeNodes.put(node, new TreeNode(cliques.get(node), finishedCalculated));
            }
        }

        // create tree separators
        for (Node node : this.maxCardOrdering) {
            if (cliques.containsKey(node) && parentCliques.containsKey(node)) {
                TreeNode parent = this.treeNodes.get(parentCliques.get(node));
                TreeNode treeNode = this.treeNodes.get(node);

                treeNode.setParentSeparator(new TreeSeparator(separators.get(node), treeNode, parent));
                parent.addChildClique(treeNode);
            }
        }

        TreeNode rootNode = null;
        for (Node node : this.treeNodes.keySet()) {
            if (!parentCliques.containsKey(node)) {
                rootNode = this.treeNodes.get(node);
            }
        }

        return rootNode;
    }

    private void computeMaximumCardinalityOrdering(Graph graph, Node[] nodes) {
        Set<Node> numbered = new HashSet<>();
        for (int i = 0; i < nodes.length; i++) {
            // find an unnumbered node that is adjacent to the most number of numbered nodes
            Node maxCardinalityNode = null;
            int maxCardinality = -1;
            for (Node v : graph.getNodes()) {
                if (!numbered.contains(v)) {
                    // count the number of times node v is adjacent to numbered node w
                    int cardinality = (int) graph.getAdjacentNodes(v).stream()
                            .filter(numbered::contains)
                            .count();

                    // find the maximum cardinality
                    if (cardinality > maxCardinality) {
                        maxCardinality = cardinality;
                        maxCardinalityNode = v;
                    }
                }
            }

            // add the node with maximum cardinality to the ordering and number it
            nodes[i] = maxCardinalityNode;
            numbered.add(maxCardinalityNode);
        }
    }

    private BayesPm createBayesPm(DataModel dataModel, Graph graph) {
        Dag dag = new Dag(dataModel.getVariables());
        (new Dag(graph)).getEdges().forEach(edge -> {
            Node node1 = dag.getNode(edge.getNode1().getName());
            Node node2 = dag.getNode(edge.getNode2().getName());
            Endpoint endpoint1 = edge.getEndpoint1();
            Endpoint endpoint2 = edge.getEndpoint2();

            dag.addEdge(new Edge(node1, node2, endpoint1, endpoint2));
        });

        return new BayesPm(dag);
    }

    private BayesIm createBayesIm(DataModel dataModel, BayesPm bayesPm) {
        return (new EmBayesEstimator(bayesPm, (DataSet) dataModel)).getEstimatedIm();
    }

    /**
     * Put the nodes from the set to an array in the order they appear in the
     * graph.
     *
     * @param nodes set of nodes
     * @return nodes in the order they appear in the graph
     */
    private Node[] toArray(Set<Node> nodes) {
        int size = nodes.size();
        Node[] order = new Node[size];

        int index = 0;
        for (Node node : this.graphNodes) {
            if (nodes.contains(node)) {
                order[index++] = node;

                if (index == size) {
                    break;
                }
            }
        }

        return order;
    }

    private void normalize(double[] values) {
        // sum up all the values
        double sum = 0;
        for (double value : values) {
            sum += value;
        }

        // divide each value by the sum
        for (int i = 0; i < values.length; i++) {
            values[i] /= sum;
        }
    }

    private int getCardinality(Set<Node> nodes) {
        int count = 1;
        count = nodes.stream()
                .map(this.bayesPm::getNumCategories)
                .reduce(count, (accumulator, element) -> accumulator * element);

        return count;
    }

    private void updateValues(int size, int[] values, Node[] nodes) {
        int j = size - 1;
        values[j]++;
        while (j >= 0 && values[j] == this.bayesPm.getNumCategories(nodes[j])) {
            values[j] = 0;
            j--;
            if (j >= 0) {
                values[j]++;
            }
        }
    }

    private int getIndexOfCPT(Node[] nodes, int[] values, Node[] order) {
        int index = 0;

        int j = 0;
        for (int i = 0; i < order.length && j < nodes.length; i++) {
            if (order[i] == nodes[j]) {
                index *= this.bayesPm.getNumCategories(nodes[j]);
                index += values[i];
                j++;
            }
        }

        return index;
    }

    private int getIndexOfCPT(Node[] nodes, int[] values) {
        int index = 0;

        for (int i = 0; i < nodes.length; i++) {
            index *= this.bayesPm.getNumCategories(nodes[i]);
            index += values[i];
        }

        return index;
    }

    private void clear(double[] array) {
        Arrays.fill(array, 0);
    }

    private TreeNode getCliqueContainsNode(Node node) {
        for (Node k : this.graphNodes) {
            if (this.treeNodes.containsKey(k) && this.treeNodes.get(k).contains(node)) {
                return this.treeNodes.get(k);
            }
        }

        return null;
    }

    private void validate(int iNode) {
        int maxIndex = this.margins.length - 1;
        if (iNode < 0 || iNode > maxIndex) {
            String msg = String.format(
                    "Invalid node index %d. Node index must be between 0 and %d.",
                    iNode,
                    maxIndex);
            throw new IllegalArgumentException(msg);
        }
    }

    private void validate(int iNode, int value) {
        validate(iNode);

        int maxValue = this.margins[iNode].length - 1;
        if (value < 0 || value > maxValue) {
            String msg = String.format(
                    "Invalid value %d for node index %d. Value must be between 0 and %d.",
                    value,
                    iNode,
                    maxValue);
            throw new IllegalArgumentException(msg);
        }
    }

    private void validate(int[] nodes) {
        if (nodes == null) {
            throw new IllegalArgumentException("Node indices cannot be null.");
        }

        if (nodes.length == 0) {
            throw new IllegalArgumentException("Node indices are required.");
        }
        if (nodes.length > this.graphNodes.length) {
            String msg = String.format(
                    "Number of nodes cannot exceed %d.",
                    this.graphNodes.length);
            throw new IllegalArgumentException(msg);
        }
    }

    private void validate(int[] nodes, int[] values) {
        validate(nodes);

        if (values == null) {
            throw new IllegalArgumentException("Node values cannot be null.");
        }
        if (values.length == 0) {
            throw new IllegalArgumentException("Node values are required.");
        }
        if (values.length != nodes.length) {
            throw new IllegalArgumentException("Number of nodes values must be equal to the number of nodes.");
        }

        for (int i = 0; i < nodes.length; i++) {
            validate(nodes[i], values[i]);
        }
    }

    private void validateAll(int[] values) {
        if (values == null) {
            throw new IllegalArgumentException("Node values cannot be null.");
        }
        if (values.length == 0) {
            throw new IllegalArgumentException("Node values are required.");
        }
        if (values.length != this.graphNodes.length) {
            throw new IllegalArgumentException("Number of nodes values must be equal to the number of nodes.");
        }

        for (int i = 0; i < values.length; i++) {
            int maxValue = this.margins[i].length - 1;
            if (values[i] < 0 && values[i] > maxValue) {
                String msg = String.format(
                        "Invalid value %d for node index %d. Value must be between 0 and %d.",
                        values[i],
                        i,
                        maxValue);
                throw new IllegalArgumentException(msg);
            }
        }
    }

    public void setEvidence(int iNode, int value) {
        validate(iNode, value);

        Node node = this.graphNodes[iNode];
        TreeNode treeNode = getCliqueContainsNode(node);
        if (treeNode == null) {
            String msg = String.format("Node %s is not in junction tree.", node.getName());
            throw new IllegalArgumentException(msg);
        }

        treeNode.setEvidence(node, value);
    }

    private double[] getConditionalProbabilities(int iNode, int parent, int parentValue) {
        validate(iNode);
        validate(parent, parentValue);

        setEvidence(parent, parentValue);

        double[] condProbs = new double[this.margins[iNode].length];
        System.arraycopy(this.margins[iNode], 0, condProbs, 0, condProbs.length);
        normalize(condProbs);

        // reset
        initialize();

        return condProbs;
    }

    private boolean isAllNodes(int[] nodes) {
        if (nodes.length == this.graphNodes.length) {
            long sum = Arrays.stream(nodes).sum();
            long total = ((this.graphNodes.length - 1) * this.graphNodes.length) / 2;

            return sum == total;
        }

        return false;
    }

    /**
     * Get the joint probability of the nodes given their parents. Example:
     * given x <-- z --> y, we can find P(x,y|z). Another example: given x
     * <-- z --> y <-- w, we can find P(x,y|z,w)
     */
    public double getConditionalProbabilities(int[] nodes, int[] values, int[] parents, int[] parentValues) {
        validate(nodes, values);
        validate(parents, parentValues);

        for (int i = 0; i < parents.length; i++) {
            setEvidence(parents[i], parentValues[i]);
        }

        double prob = 1;
        for (int i = 0; i < nodes.length; i++) {
            double[] marg = this.margins[nodes[i]];

            double[] condProbs = new double[marg.length];
            System.arraycopy(marg, 0, condProbs, 0, marg.length);
            normalize(condProbs);

            prob *= condProbs[values[i]];
        }

        // reset
        initialize();

        return prob;
    }

    /**
     * Get the conditional probability of a node for all of its values.
     */
    public double[] getConditionalProbabilities(int iNode, int[] parents, int[] parentValues) {
        validate(iNode);
        validate(parents, parentValues);

        if (parents.length == 1) {
            return getConditionalProbabilities(iNode, parents[0], parentValues[0]);
        } else {
            for (int i = 0; i < parents.length; i++) {
                setEvidence(parents[i], parentValues[i]);
            }

            double[] condProbs = new double[this.margins[iNode].length];
            System.arraycopy(this.margins[iNode], 0, condProbs, 0, condProbs.length);
            normalize(condProbs);

            // reset
            initialize();

            return condProbs;
        }
    }

    public double getConditionalProbability(int iNode, int value, int[] parents, int[] parentValues) {
        validate(iNode, value);

        return getConditionalProbabilities(iNode, parents, parentValues)[value];
    }

    /**
     * Get the joint probability of all nodes (variables). Given the nodes are
     * X1, X2,...,Xn, then nodeValues[0] = value(X1), nodeValues[1] =
     * value(X2),...,nodeValues[n-1] = value(Xn).
     *
     * @param nodeValues an array of values for each node
     */
    public double getJointProbabilityAll(int[] nodeValues) {
        validateAll(nodeValues);

        double logJointClusterPotentials = this.root.getLogJointClusterPotentials(nodeValues);
        double logJointSeparatorPotentials = this.root.getLogJointSeparatorPotentials(nodeValues);

        return Math.exp(logJointClusterPotentials - logJointSeparatorPotentials);
    }

    public double getJointProbability(int[] nodes, int[] values) {
        validate(nodes, values);
        if (isAllNodes(nodes)) {
            return getJointProbabilityAll(values);
        } else {
            for (int i = 0; i < nodes.length; i++) {
                setEvidence(nodes[i], values[i]);
            }

            // sum out a non-evidence variable
            double prob = 0;
            int index = 0;
            for (int i = 0; i < this.margins.length; i++) {
                if (i < nodes.length && i == nodes[index]) {
                    index++;
                } else {
                    prob += Arrays.stream(this.margins[i]).sum();
                    break;
                }
            }

            // reset
            initialize();

            return prob;
        }
    }

    public double[] getMarginalProbability(int iNode) {
        validate(iNode);

        double[] marginals = new double[this.margins[iNode].length];
        System.arraycopy(this.margins[iNode], 0, marginals, 0, marginals.length);
        normalize(marginals);

        return marginals;
    }

    public double getMarginalProbability(int iNode, int value) {
        validate(iNode, value);

        return this.margins[iNode][value];
    }

    public List<Node> getNodes() {
        return Collections.unmodifiableList(Arrays.asList(this.graphNodes));
    }

    public int getNumberOfNodes() {
        return this.graphNodes.length;
    }

    @Override
    public String toString() {
        return this.root.toString().trim();
    }

    private class TreeSeparator implements TetradSerializable {

        static final long serialVersionUID = 23L;

        private final double[] parentPotentials;
        private final double[] childPotentials;

        private final Node[] nodes;

        private final TreeNode childNode;
        private final TreeNode parentNode;

        public TreeSeparator(Set<Node> separator, TreeNode childNode, TreeNode parentNode) {
            this.childNode = childNode;
            this.parentNode = parentNode;

            this.nodes = toArray(separator);
            int cardinality = getCardinality(separator);

            this.parentPotentials = new double[cardinality];
            this.childPotentials = new double[cardinality];
        }

        /**
         * Marginalize TreeNode node over all nodes outside the separator set
         *
         * @param node one of the neighboring junction tree nodes of this
         *             separator
         */
        public void update(TreeNode node, double[] potentials) {
            clear(potentials);

            if (node.prob != null) {
                int size = node.nodes.length;
                int[] values = new int[size];
                for (int i = 0; i < node.cardinality; i++) {
                    int indexNodeCPT = getIndexOfCPT(node.nodes, values);
                    int indexSepCPT = getIndexOfCPT(this.nodes, values, node.nodes);
                    potentials[indexSepCPT] += node.prob[indexNodeCPT];

                    updateValues(size, values, node.nodes);
                }
            }
        }

        public void updateFromParent() {
            update(this.parentNode, this.parentPotentials);
        }

        public void updateFromChild() {
            update(this.childNode, this.childPotentials);
        }

    }

    private class TreeNode implements TetradSerializable {

        static final long serialVersionUID = 23L;

        /**
         * Distribution over this junction node according to its potentials.
         */
        private final double[] prob;

        private final double[][] margProb;

        private TreeSeparator parentSeparator;

        private final double[] potentials;

        private final List<TreeNode> children;

        private final int cardinality;

        private final Set<Node> clique;
        private final Node[] nodes;

        public TreeNode(Set<Node> clique, Set<Node> finishedCalculated) {
            this.clique = clique;
            this.nodes = toArray(clique);

            this.children = new LinkedList<>();

            this.cardinality = getCardinality(clique);
            this.potentials = new double[this.cardinality];
            this.prob = new double[this.cardinality];

            this.margProb = new double[this.nodes.length][];
            for (int iNode = 0; iNode < this.nodes.length; iNode++) {
                this.margProb[iNode] = new double[JunctionTreeAlgorithm.this.bayesPm.getNumCategories(this.nodes[iNode])];
            }

            calculatePotentials(clique, finishedCalculated);
        }

        private void calculatePotentials(Set<Node> cliques, Set<Node> finishedCalculated) {
            Graph dag = JunctionTreeAlgorithm.this.bayesIm.getDag();

            Set<Node> nodesWithParentsInCluster = new HashSet<>();
            for (Node node : this.nodes) {
                if (!finishedCalculated.contains(node) && cliques.containsAll(dag.getParents(node))) {
                    nodesWithParentsInCluster.add(node);
                    finishedCalculated.add(node);
                }
            }

            // fill in values
            int size = this.nodes.length;
            int[] values = new int[size];
            for (int i = 0; i < this.cardinality; i++) {
                int indexCPT = getIndexOfCPT(this.nodes, values);
                this.potentials[indexCPT] = 1.0;
                for (int iNode = 0; iNode < this.nodes.length; iNode++) {
                    Node node = this.nodes[iNode];
                    if (nodesWithParentsInCluster.contains(node)) {
                        int nodeIndex = JunctionTreeAlgorithm.this.bayesIm.getNodeIndex(node);
                        int rowIndex = getRowIndex(nodeIndex, values, this.nodes);
                        this.potentials[indexCPT] *= JunctionTreeAlgorithm.this.bayesIm.getProbability(nodeIndex, rowIndex, values[iNode]);
                    }
                }

                updateValues(size, values, this.nodes);
            }
        }

        public void initializeUp() {
            System.arraycopy(this.potentials, 0, this.prob, 0, this.cardinality);

            int size = this.nodes.length;
            int[] values = new int[size];
            this.children.forEach(childNode -> {
                TreeSeparator separator = childNode.parentSeparator;
                for (int i = 0; i < this.cardinality; i++) {
                    int indexSepCPT = getIndexOfCPT(separator.nodes, values, this.nodes);
                    int indexNodeCPT = getIndexOfCPT(this.nodes, values);
                    this.prob[indexNodeCPT] *= separator.childPotentials[indexSepCPT];

                    updateValues(size, values, this.nodes);
                }
            });

            if (this.parentSeparator != null) { // not a root node
                this.parentSeparator.updateFromChild();
            }
        }

        public void initializeDown(boolean recursively) {
            if (this.parentSeparator != null) {
                this.parentSeparator.updateFromParent();

                int size = this.nodes.length;
                int[] values = new int[size];
                for (int i = 0; i < this.cardinality; i++) {
                    int indexSepCPT = getIndexOfCPT(this.parentSeparator.nodes, values, this.nodes);
                    int indexNodeCPT = getIndexOfCPT(this.nodes, values);

                    if (this.parentSeparator.childPotentials[indexSepCPT] > 0) {
                        this.prob[indexNodeCPT] *= (this.parentSeparator.parentPotentials[indexSepCPT] / this.parentSeparator.childPotentials[indexSepCPT]);
                    } else {
                        this.prob[indexNodeCPT] = 0;
                    }

                    updateValues(size, values, this.nodes);
                }

                this.parentSeparator.updateFromChild();
            }
            calculateMarginalProbabilities();

            if (recursively) {
                this.children.forEach(childNode -> childNode.initializeDown(true));
            }
        }

        /**
         * Calculate marginal probabilities for the individual nodes in the
         * clique.
         */
        private void calculateMarginalProbabilities() {
            // reset
            for (int iNode = 0; iNode < this.nodes.length; iNode++) {
                clear(this.margProb[iNode]);
            }

            int size = this.nodes.length;
            int[] values = new int[size];
            for (int i = 0; i < this.cardinality; i++) {
                int indexNodeCPT = getIndexOfCPT(this.nodes, values);
                for (int iNode = 0; iNode < size; iNode++) {
                    this.margProb[iNode][values[iNode]] += this.prob[indexNodeCPT];
                }

                updateValues(size, values, this.nodes);
            }

            for (int iNode = 0; iNode < size; iNode++) {
                JunctionTreeAlgorithm.this.margins[JunctionTreeAlgorithm.this.bayesIm.getNodeIndex(this.nodes[iNode])] = this.margProb[iNode];
            }
        }

        private int getRowIndex(int nodeIndex, int[] values, Node[] nodes) {
            int index = 0;

            int[] parents = JunctionTreeAlgorithm.this.bayesIm.getParents(nodeIndex);
            for (int parent : parents) {
                Node node = JunctionTreeAlgorithm.this.bayesIm.getNode(parent);
                index *= JunctionTreeAlgorithm.this.bayesPm.getNumCategories(node);
                for (int j = 0; j < nodes.length; j++) {
                    if (node == nodes[j]) {
                        index += values[j];
                    }
                }
            }

            return index;
        }

        private int getNodeIndex(Node node) {
            for (int i = 0; i < this.nodes.length; i++) {
                if (this.nodes[i] == node) {
                    return i;
                }
            }

            return -1;
        }

        public void setEvidence(Node node, int value) {
            int nodeIndex = getNodeIndex(node);
            if (nodeIndex < 0) {
                String msg = String.format("Unable to find node %s in clique.", node.getName());
                throw new IllegalArgumentException(msg);
            }

            int size = this.nodes.length;
            int[] values = new int[size];
            for (int i = 0; i < this.cardinality; i++) {
                if (values[nodeIndex] != value) {
                    int indexNodeCPT = getIndexOfCPT(this.nodes, values);
                    this.prob[indexNodeCPT] = 0;
                }

                updateValues(size, values, this.nodes);
            }

            calculateMarginalProbabilities();
            updateEvidence(this);
        }

        private void updateEvidence(TreeNode source) {
            if (source != this) {
                int size = this.nodes.length;
                int[] values = new int[size];
                for (int i = 0; i < this.cardinality; i++) {
                    int indexNodeCPT = getIndexOfCPT(this.nodes, values);
                    int indexChildNodeCPT = getIndexOfCPT(source.parentSeparator.nodes, values, this.nodes);
                    if (source.parentSeparator.parentPotentials[indexChildNodeCPT] != 0) {
                        this.prob[indexNodeCPT] *= source.parentSeparator.childPotentials[indexChildNodeCPT]
                                / source.parentSeparator.parentPotentials[indexChildNodeCPT];
                    } else {
                        this.prob[indexNodeCPT] = 0;
                    }

                    updateValues(size, values, this.nodes);
                }

                calculateMarginalProbabilities();
            }

            this.children.stream()
                    .filter(e -> e != source)
                    .forEach(e -> e.initializeDown(true));

            if (this.parentSeparator != null) {
                this.parentSeparator.updateFromChild();
                this.parentSeparator.parentNode.updateEvidence(this);
                this.parentSeparator.updateFromParent();
            }
        }

        private double getLogJointSeparatorPotentials(int[] nodeValues) {
            double logJointPotentials = Math.log(1);

            if (this.parentSeparator != null) {
                Node[] parentNodes = this.parentSeparator.nodes;
                int size = parentNodes.length;
                int[] values = new int[size];
                for (int iNode = 0; iNode < size; iNode++) {
                    values[iNode] = nodeValues[JunctionTreeAlgorithm.this.bayesIm.getNodeIndex(parentNodes[iNode])];
                }

                logJointPotentials += Math.log(this.parentSeparator.childPotentials[getIndexOfCPT(parentNodes, values)]);
            }

            logJointPotentials = this.children.stream()
                    .map(child -> child.getLogJointSeparatorPotentials(nodeValues))
                    .reduce(logJointPotentials, Double::sum);

            return logJointPotentials;
        }

        private double getLogJointClusterPotentials(int[] nodeValues) {
            int size = this.nodes.length;
            int[] values = new int[size];
            for (int iNode = 0; iNode < size; iNode++) {
                values[iNode] = nodeValues[JunctionTreeAlgorithm.this.bayesIm.getNodeIndex(this.nodes[iNode])];
            }

            double logJointPotentials = Math.log(this.prob[getIndexOfCPT(this.nodes, values)]);
            logJointPotentials = this.children.stream()
                    .map(child -> child.getLogJointClusterPotentials(nodeValues))
                    .reduce(logJointPotentials, Double::sum);

            return logJointPotentials;
        }

        public void setParentSeparator(TreeSeparator parentSeparator) {
            this.parentSeparator = parentSeparator;
        }

        public void addChildClique(TreeNode child) {
            this.children.add(child);
        }

        public Set<Node> getClique() {
            return this.clique;
        }

        /**
         * Check if the clique contains the given node.
         */
        public boolean contains(Node node) {
            return this.clique.contains(node);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < this.nodes.length; i++) {
                sb.append(this.nodes[i].getName());
                sb.append(": ");
                sb.append(Arrays.stream(this.margProb[i])
                        .mapToObj(String::valueOf)
                        .collect(Collectors.joining(" ")));
                sb.append('\n');
            }
            this.children.forEach(childNode -> {
                sb.append("----------------\n");
                sb.append(childNode.toString());
            });

            return sb.toString();
        }

    }

}
