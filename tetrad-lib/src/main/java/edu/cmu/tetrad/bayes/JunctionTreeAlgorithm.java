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
import edu.cmu.tetrad.graph.Dag;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.TetradSerializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Junction Tree Algorithm.
 *
 * This implementation follows the Weka's implementation.
 *
 * Nov 8, 2019 2:22:34 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 * @see
 * <a href="https://raw.githubusercontent.com/Waikato/weka-3.8/master/weka/src/main/java/weka/classifiers/bayes/net/MarginCalculator.java">MarginCalculator.java</a>
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
        this.graphNodes = bayesIm.getDag().getNodes().toArray(new Node[numOfNodes]);
        this.margins = new double[numOfNodes][];
        this.maxCardOrdering = new Node[numOfNodes];
        this.root = buildJunctionTree();

        initialize();
    }

    public JunctionTreeAlgorithm(BayesIm bayesIm) {
        this.bayesPm = bayesIm.getBayesPm();
        this.bayesIm = bayesIm;
        this.treeNodes = new HashMap<>();

        int numOfNodes = bayesPm.getDag().getNumNodes();
        this.graphNodes = bayesIm.getDag().getNodes().toArray(new Node[numOfNodes]);
        this.margins = new double[numOfNodes][];
        this.maxCardOrdering = new Node[numOfNodes];
        this.root = buildJunctionTree();

        initialize();
    }

    private void initialize() {
        for (int i = maxCardOrdering.length - 1; i >= 0; i--) {
            TreeNode treeNode = treeNodes.get(maxCardOrdering[i]);
            if (treeNode != null) {
                treeNode.initializeUp();
            }
        }
        for (Node node : maxCardOrdering) {
            TreeNode treeNode = treeNodes.get(node);
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
        Graph undirectedGraph = GraphTools.moralize(bayesIm.getDag());

        // triangulate
        computeMaximumCardinalityOrdering(undirectedGraph, maxCardOrdering);
        GraphTools.fillIn(undirectedGraph, maxCardOrdering);

        // get set of cliques
        computeMaximumCardinalityOrdering(undirectedGraph, maxCardOrdering);
        Map<Node, Set<Node>> cliques = GraphTools.getCliques(maxCardOrdering, undirectedGraph);

        // get separator sets
        Map<Node, Set<Node>> separators = GraphTools.getSeparators(maxCardOrdering, cliques);

        // get clique tree
        Map<Node, Node> parentCliques = GraphTools.getCliqueTree(maxCardOrdering, cliques, separators);

        // create tree nodes
        Set<Node> finishedCalculated = new HashSet<>();
        for (Node node : maxCardOrdering) {
            if (cliques.containsKey(node)) {
                treeNodes.put(node, new TreeNode(cliques.get(node), finishedCalculated));
            }
        }

        // create tree separators
        for (Node node : maxCardOrdering) {
            if (cliques.containsKey(node) && parentCliques.containsKey(node)) {
                TreeNode parent = treeNodes.get(parentCliques.get(node));
                TreeNode treeNode = treeNodes.get(node);

                treeNode.setParentSeparator(new TreeSeparator(separators.get(node), treeNode, parent));
                parent.addChildClique(treeNode);
            }
        }

        TreeNode rootNode = null;
        for (Node node : treeNodes.keySet()) {
            if (!parentCliques.containsKey(node)) {
                rootNode = treeNodes.get(node);
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
                            .filter(u -> numbered.contains(u))
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
        for (Node node : graphNodes) {
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
                .map(bayesPm::getNumCategories)
                .reduce(count, (accumulator, element) -> accumulator * element);

        return count;
    }

    private void updateValues(int size, int[] values, Node[] nodes) {
        int j = size - 1;
        values[j]++;
        while (j >= 0 && values[j] == bayesPm.getNumCategories(nodes[j])) {
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
                index *= bayesPm.getNumCategories(nodes[j]);
                index += values[i];
                j++;
            }
        }

        return index;
    }

    private int getIndexOfCPT(Node[] nodes, int[] values) {
        int index = 0;

        for (int i = 0; i < nodes.length; i++) {
            index *= bayesPm.getNumCategories(nodes[i]);
            index += values[i];
        }

        return index;
    }

    private void clear(double[] array) {
        Arrays.fill(array, 0);
    }

    private TreeNode getCliqueContainsNode(Node node) {
        for (Node k : graphNodes) {
            if (treeNodes.containsKey(k) && treeNodes.get(k).contains(node)) {
                return treeNodes.get(k);
            }
        }

        return null;
    }

    private void validate(int iNode) {
        int maxIndex = margins.length - 1;
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

        int maxValue = margins[iNode].length - 1;
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
        if (nodes.length > graphNodes.length) {
            String msg = String.format(
                    "Number of nodes cannot exceed %d.",
                    graphNodes.length);
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
        if (values.length != graphNodes.length) {
            throw new IllegalArgumentException("Number of nodes values must be equal to the number of nodes.");
        }

        for (int i = 0; i < values.length; i++) {
            int maxValue = margins[i].length - 1;
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

        Node node = graphNodes[iNode];
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

        double[] condProbs = new double[margins[iNode].length];
        System.arraycopy(margins[iNode], 0, condProbs, 0, condProbs.length);
        normalize(condProbs);

        // reset
        initialize();

        return condProbs;
    }

    private boolean isAllNodes(int[] nodes) {
        if (nodes.length == graphNodes.length) {
            long sum = Arrays.stream(nodes).sum();
            long total = ((graphNodes.length - 1) * graphNodes.length) / 2;

            return sum == total;
        }

        return false;
    }

    /**
     * Get the conditional probability of a node for all of its values.
     *
     * @param iNode
     * @param parents
     * @param parentValues
     * @return
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

            double[] condProbs = new double[margins[iNode].length];
            System.arraycopy(margins[iNode], 0, condProbs, 0, condProbs.length);
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
     * @return
     */
    public double getJointProbabilityAll(int[] nodeValues) {
        validateAll(nodeValues);

        double logJointClusterPotentials = root.getLogJointClusterPotentials(nodeValues);
        double logJointSeparatorPotentials = root.getLogJointSeparatorPotentials(nodeValues);

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
            for (int i = 0; i < margins.length; i++) {
                if (i < nodes.length && i == nodes[index]) {
                    index++;
                } else {
                    prob += Arrays.stream(margins[i]).sum();
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

        double[] marginals = new double[margins[iNode].length];
        System.arraycopy(margins[iNode], 0, marginals, 0, marginals.length);
        normalize(marginals);

        return marginals;
    }

    public double getMarginalProbability(int iNode, int value) {
        validate(iNode, value);

        return margins[iNode][value];
    }

    public List<Node> getNodes() {
        return Collections.unmodifiableList(Arrays.asList(graphNodes));
    }

    public int getNumberOfNodes() {
        return graphNodes.length;
    }

    @Override
    public String toString() {
        return root.toString().trim();
    }

    private class TreeSeparator implements TetradSerializable {

        static final long serialVersionUID = 23L;

        private final double[] parentPotentials;
        private final double[] childPotentials;

        private final Node[] nodes;
        private final int cardinality;

        private final TreeNode childNode;
        private final TreeNode parentNode;

        public TreeSeparator(Set<Node> separator, TreeNode childNode, TreeNode parentNode) {
            this.childNode = childNode;
            this.parentNode = parentNode;

            this.nodes = toArray(separator);
            this.cardinality = getCardinality(separator);

            this.parentPotentials = new double[cardinality];
            this.childPotentials = new double[cardinality];
        }

        /**
         * Marginalize TreeNode node over all nodes outside the separator set
         *
         * @param node one of the neighboring junction tree nodes of this
         * separator
         */
        public void update(TreeNode node, double[] potentials) {
            clear(potentials);

            if (node.prob != null) {
                int size = node.nodes.length;
                int[] values = new int[size];
                for (int i = 0; i < node.cardinality; i++) {
                    int indexNodeCPT = getIndexOfCPT(node.nodes, values);
                    int indexSepCPT = getIndexOfCPT(nodes, values, node.nodes);
                    potentials[indexSepCPT] += node.prob[indexNodeCPT];

                    updateValues(size, values, node.nodes);
                }
            }
        }

        public void updateFromParent() {
            update(parentNode, parentPotentials);
        }

        public void updateFromChild() {
            update(childNode, childPotentials);
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
            this.potentials = new double[cardinality];
            this.prob = new double[cardinality];

            this.margProb = new double[nodes.length][];
            for (int iNode = 0; iNode < nodes.length; iNode++) {
                margProb[iNode] = new double[bayesPm.getNumCategories(nodes[iNode])];
            }

            calculatePotentials(clique, finishedCalculated);
        }

        private void calculatePotentials(Set<Node> cliques, Set<Node> finishedCalculated) {
            Graph dag = bayesIm.getDag();

            Set<Node> nodesWithParentsInCluster = new HashSet<>();
            for (Node node : nodes) {
                if (!finishedCalculated.contains(node) && cliques.containsAll(dag.getParents(node))) {
                    nodesWithParentsInCluster.add(node);
                    finishedCalculated.add(node);
                }
            }

            // fill in values
            int size = nodes.length;
            int[] values = new int[size];
            for (int i = 0; i < cardinality; i++) {
                int indexCPT = getIndexOfCPT(nodes, values);
                potentials[indexCPT] = 1.0;
                for (int iNode = 0; iNode < nodes.length; iNode++) {
                    Node node = nodes[iNode];
                    if (nodesWithParentsInCluster.contains(node)) {
                        int nodeIndex = bayesIm.getNodeIndex(node);
                        int rowIndex = getRowIndex(nodeIndex, values, nodes);
                        potentials[indexCPT] *= bayesIm.getProbability(nodeIndex, rowIndex, values[iNode]);
                    }
                }

                updateValues(size, values, nodes);
            }
        }

        public void initializeUp() {
            System.arraycopy(potentials, 0, prob, 0, cardinality);

            int size = nodes.length;
            int[] values = new int[size];
            children.forEach(childNode -> {
                TreeSeparator separator = childNode.parentSeparator;
                for (int i = 0; i < cardinality; i++) {
                    int indexSepCPT = getIndexOfCPT(separator.nodes, values, nodes);
                    int indexNodeCPT = getIndexOfCPT(nodes, values);
                    prob[indexNodeCPT] *= separator.childPotentials[indexSepCPT];

                    updateValues(size, values, nodes);
                }
            });

            if (parentSeparator != null) { // not a root node
                parentSeparator.updateFromChild();
            }
        }

        public void initializeDown(boolean recursively) {
            if (parentSeparator == null) {
                calculateMarginalProbabilities();
            } else {
                parentSeparator.updateFromParent();

                int size = nodes.length;
                int[] values = new int[size];
                for (int i = 0; i < cardinality; i++) {
                    int indexSepCPT = getIndexOfCPT(parentSeparator.nodes, values, nodes);
                    int indexNodeCPT = getIndexOfCPT(nodes, values);

                    if (parentSeparator.childPotentials[indexSepCPT] > 0) {
                        prob[indexNodeCPT] *= (parentSeparator.parentPotentials[indexSepCPT] / parentSeparator.childPotentials[indexSepCPT]);
                    } else {
                        prob[indexNodeCPT] = 0;
                    }

                    updateValues(size, values, nodes);
                }

                parentSeparator.updateFromChild();
                calculateMarginalProbabilities();
            }

            if (recursively) {
                children.forEach(childNode -> childNode.initializeDown(true));
            }
        }

        /**
         * Calculate marginal probabilities for the individual nodes in the
         * clique.
         */
        private void calculateMarginalProbabilities() {
            // reset
            for (int iNode = 0; iNode < nodes.length; iNode++) {
                clear(margProb[iNode]);
            }

            int size = nodes.length;
            int[] values = new int[size];
            for (int i = 0; i < cardinality; i++) {
                int indexNodeCPT = getIndexOfCPT(nodes, values);
                for (int iNode = 0; iNode < size; iNode++) {
                    margProb[iNode][values[iNode]] += prob[indexNodeCPT];
                }

                updateValues(size, values, nodes);
            }

            for (int iNode = 0; iNode < size; iNode++) {
                margins[bayesIm.getNodeIndex(nodes[iNode])] = margProb[iNode];
            }
        }

        private int getRowIndex(int nodeIndex, int[] values, Node[] nodes) {
            int index = 0;

            int[] parents = bayesIm.getParents(nodeIndex);
            for (int i = 0; i < parents.length; i++) {
                Node node = bayesIm.getNode(parents[i]);
                index *= bayesPm.getNumCategories(node);
                for (int j = 0; j < nodes.length; j++) {
                    if (node == nodes[j]) {
                        index += values[j];
                    }
                }
            }

            return index;
        }

        private int getNodeIndex(Node node) {
            for (int i = 0; i < nodes.length; i++) {
                if (nodes[i] == node) {
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

            int size = nodes.length;
            int[] values = new int[size];
            for (int i = 0; i < cardinality; i++) {
                if (values[nodeIndex] != value) {
                    int indexNodeCPT = getIndexOfCPT(nodes, values);
                    prob[indexNodeCPT] = 0;
                }

                updateValues(size, values, nodes);
            }

            calculateMarginalProbabilities();
            updateEvidence(this);
        }

        private void updateEvidence(TreeNode source) {
            if (source != this) {
                int size = nodes.length;
                int[] values = new int[size];
                for (int i = 0; i < cardinality; i++) {
                    int indexNodeCPT = getIndexOfCPT(nodes, values);
                    int indexChildNodeCPT = getIndexOfCPT(source.parentSeparator.nodes, values, nodes);
                    if (source.parentSeparator.parentPotentials[indexChildNodeCPT] != 0) {
                        prob[indexNodeCPT] *= source.parentSeparator.childPotentials[indexChildNodeCPT]
                                / source.parentSeparator.parentPotentials[indexChildNodeCPT];
                    } else {
                        prob[indexNodeCPT] = 0;
                    }

                    updateValues(size, values, nodes);
                }

                calculateMarginalProbabilities();
            }

            children.stream()
                    .filter(e -> e != source)
                    .forEach(e -> e.initializeDown(true));

            if (parentSeparator != null) {
                parentSeparator.updateFromChild();
                parentSeparator.parentNode.updateEvidence(this);
                parentSeparator.updateFromParent();
            }
        }

        private double getLogJointSeparatorPotentials(int[] nodeValues) {
            double logJointPotentials = Math.log(1);

            if (parentSeparator != null) {
                Node[] parentNodes = parentSeparator.nodes;
                int size = parentNodes.length;
                int[] values = new int[size];
                for (int iNode = 0; iNode < size; iNode++) {
                    values[iNode] = nodeValues[bayesIm.getNodeIndex(parentNodes[iNode])];
                }

                logJointPotentials += Math.log(parentSeparator.childPotentials[getIndexOfCPT(parentNodes, values)]);
            }

            logJointPotentials = children.stream()
                    .map(child -> child.getLogJointSeparatorPotentials(nodeValues))
                    .reduce(logJointPotentials, (accumulator, value) -> accumulator + value);

            return logJointPotentials;
        }

        private double getLogJointClusterPotentials(int[] nodeValues) {
            int size = nodes.length;
            int[] values = new int[size];
            for (int iNode = 0; iNode < size; iNode++) {
                values[iNode] = nodeValues[bayesIm.getNodeIndex(nodes[iNode])];
            }

            double logJointPotentials = Math.log(prob[getIndexOfCPT(nodes, values)]);
            logJointPotentials = children.stream()
                    .map(child -> child.getLogJointClusterPotentials(nodeValues))
                    .reduce(logJointPotentials, (accumulator, value) -> accumulator + value);

            return logJointPotentials;
        }

        public void setParentSeparator(TreeSeparator parentSeparator) {
            this.parentSeparator = parentSeparator;
        }

        public void addChildClique(TreeNode child) {
            children.add(child);
        }

        public Set<Node> getClique() {
            return clique;
        }

        /**
         * Check if the clique contains the given node.
         *
         * @param node
         * @return
         */
        public boolean contains(Node node) {
            return clique.contains(node);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < nodes.length; i++) {
                sb.append(nodes[i].getName());
                sb.append(": ");
                sb.append(Arrays.stream(margProb[i])
                        .mapToObj(String::valueOf)
                        .collect(Collectors.joining(" ")));
                sb.append('\n');
            }
            children.forEach(childNode -> {
                sb.append("----------------\n");
                sb.append(childNode.toString());
            });

            return sb.toString();
        }

    }

}
