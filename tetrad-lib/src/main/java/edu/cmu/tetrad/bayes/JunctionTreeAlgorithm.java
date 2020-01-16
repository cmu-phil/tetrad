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
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Dag;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
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
public class JunctionTreeAlgorithm {

    private static final double[] NO_PROBABILITIES = new double[0];
    private static final double NO_PROBABILITY = -1.0;

    final private TreeNode root;

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
        BayesPm myBayesPm = new BayesPm(new Dag(graph));

        dataModel.getVariables().stream()
                .map(e -> (DiscreteVariable) e)
                .forEach(var -> myBayesPm.setCategories(var, var.getCategories()));

        return myBayesPm;
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

    private boolean isValid(int iNode) {
        return (iNode >= 0 && iNode < margins.length);
    }

    private boolean isValid(int iNode, int value) {
        return (iNode >= 0 && iNode < margins.length)
                && (value >= 0 && value < margins[iNode].length);
    }

    private boolean isValid(int[] parents, int[] parentValues) {
        if (parents != null && parents.length > 0) {
            // make sure we have values for parents
            if (parentValues == null || parentValues.length == 0) {
                return false;
            }
            // make sure the number of parents equals to the number of values.
            if (parentValues.length != parents.length) {
                return false;
            }

            int len = margins.length;
            for (int i = 0; i < parents.length; i++) {
                // make sure the parent index is valid
                if (parents[i] < 0 || parents[i] >= len) {
                    return false;
                }

                // make sure the parent value is valid
                int size = margins[parents[i]].length;
                if (parentValues[i] < 0 || parentValues[i] >= size) {
                    return false;
                }
            }
        }

        return true;
    }

    public void setEvidence(int iNode, int value) throws Exception {
        if (!isValid(iNode, value)) {
            throw new Exception(String.format("No such node of index %d with value of %d found.", iNode, value));
        }

        Node node = graphNodes[iNode];
        TreeNode treeNode = getCliqueContainsNode(node);
        if (treeNode == null) {
            throw new Exception(String.format("Node %s is not in junction tree.", node.getName()));
        }

        treeNode.setEvidence(node, value);
    }

    public double getConditionalProbability(int iNode, int value, int[] parents, int[] parentValues) {
        double margCondProb = NO_PROBABILITY;

        if (isValid(iNode, value) && isValid(parents, parentValues)) {
            if (parents == null || parents.length == 0) {
                return getMarginalProbability(iNode, value);
            }

            try {
                for (int i = 0; i < parents.length; i++) {
                    setEvidence(parents[i], parentValues[i]);
                }
            } catch (Exception exception) {
                exception.printStackTrace(System.err);
            }

            margCondProb = margins[iNode][value];

            // reset
            initialize();
        }

        return margCondProb;
    }

    public double[] getMarginalProbability(int iNode) {
        return isValid(iNode)
                ? margins[iNode]
                : NO_PROBABILITIES;
    }

    public double getMarginalProbability(int iNode, int value) {
        return isValid(iNode, value)
                ? margins[iNode][value]
                : NO_PROBABILITY;
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

    private class TreeSeparator {

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

                normalize(potentials);
            }
        }

        public void updateFromParent() {
            update(parentNode, parentPotentials);
        }

        public void updateFromChild() {
            update(childNode, childPotentials);
        }

    }

    private class TreeNode {

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

            normalize(prob);

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

                normalize(prob);

                parentSeparator.updateFromChild();
                calculateMarginalProbabilities();
            }

            if (recursively) {
                children.forEach(childNode -> childNode.initializeDown(true));
            }
        }

        /**
         * Calculate marginal probabilities for the individual nodes in the
         * clique. Store results in m_MarginalP
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

        public void setEvidence(Node node, int value) throws Exception {
            int nodeIndex = getNodeIndex(node);
            if (nodeIndex < 0) {
                throw new Exception(String.format("Unable to find node %s in clique.", node.getName()));
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

            normalize(prob);
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

                normalize(prob);

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
