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

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializable;
import org.apache.commons.math3.util.FastMath;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Junction Tree Algorithm.
 *
 * <p>
 * KID-GLOVES FIXES (relative to the original Kevin implementation):
 * <ul>
 *   <li><b>Index alignment is forced.</b> We build {@code graphNodes[i] = bayesIm.getNode(i)} so that
 *       the {@code iNode} indices used by callers always match BayesIm indices.</li>
 *   <li><b>No null margins ever.</b> {@code margins[i]} is always allocated.</li>
 *   <li><b>Forest-safe.</b> If clique tree construction yields multiple roots (disconnected components),
 *       we initialize/calibrate <i>all</i> roots. This prevents "uniform" or "unset" symptoms due to only
 *       calibrating the last root.</li>
 *   <li><b>Robust root handling.</b> Roots are stored explicitly. Evidence propagation and initialization
 *       does not assume a single root.</li>
 * </ul>
 * </p>
 *
 * <p>
 * This implementation follows Weka's MarginCalculator approach.
 * </p>
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 * @author Joseph Ramsey (kid-gloves hardening)
 * @version $Id: $Id
 * @see <a href="https://raw.githubusercontent.com/Waikato/weka-3.8/master/weka/src/main/java/weka/classifiers/bayes/net/MarginCalculator.java">MarginCalculator.java</a>
 */
public class JunctionTreeAlgorithm implements TetradSerializable {
    @Serial
    private static final long serialVersionUID = 23L;

    /** All clique-tree roots (one per connected component). Never empty. */
    private final List<TreeNode> roots;

    /** Nodes indexed exactly like BayesIm: graphNodes[i] == bayesIm.getNode(i). */
    private final Node[] graphNodes;

    /**
     * For each BayesIm node index i: margins[i][k] is the (generally unnormalized) potential after evidence.
     * Normalization occurs in getMarginalProbability().
     */
    private final double[][] margins;

    /** Maximum cardinality ordering over the moralized/triangulated undirected graph. */
    private final Node[] maxCardOrdering;

    /** BayesPm for category counts and structure. */
    private final BayesPm bayesPm;

    /** The BayesIm providing CPT values (must be the ground truth source). */
    private final BayesIm bayesIm;

    /** Clique representative -> TreeNode. */
    private final Map<Node, TreeNode> treeNodes;

    /** Fast BayesIm index lookup by Node identity (kid gloves). */
    private final IdentityHashMap<Node, Integer> bayesImIndexByNode;

    //=========================== CONSTRUCTORS ===========================//

    /**
     * Construct from a graph + data model (estimates a BayesIm).
     */
    public JunctionTreeAlgorithm(Graph graph, DataModel dataModel) {
        this.bayesPm = createBayesPm(dataModel, graph);
        this.bayesIm = createBayesIm(dataModel, this.bayesPm);
        this.treeNodes = new HashMap<>();
        this.bayesImIndexByNode = new IdentityHashMap<>();

        final int n = this.bayesIm.getNumNodes();

        // Force BayesIm index alignment.
        this.graphNodes = new Node[n];
        for (int i = 0; i < n; i++) {
            Node node = this.bayesIm.getNode(i);
            this.graphNodes[i] = node;
            this.bayesImIndexByNode.put(node, i);
        }

        // Never leave margins[i] null.
        this.margins = new double[n][];
        for (int i = 0; i < n; i++) {
            this.margins[i] = new double[this.bayesPm.getNumCategories(this.graphNodes[i])];
        }

        this.maxCardOrdering = new Node[n];
        this.roots = buildJunctionTree();

        initialize();
    }

    /**
     * Construct directly from a BayesIm (common use in Updaters).
     */
    public JunctionTreeAlgorithm(BayesIm bayesIm) {
        if (bayesIm == null) throw new NullPointerException("bayesIm must not be null.");

        this.bayesPm = bayesIm.getBayesPm();
        this.bayesIm = bayesIm;
        this.treeNodes = new HashMap<>();
        this.bayesImIndexByNode = new IdentityHashMap<>();

        final int n = this.bayesIm.getNumNodes();

        // Force BayesIm index alignment (CRITICAL).
        this.graphNodes = new Node[n];
        for (int i = 0; i < n; i++) {
            Node node = this.bayesIm.getNode(i);
            this.graphNodes[i] = node;
            this.bayesImIndexByNode.put(node, i);
        }

        // Never leave margins[i] null.
        this.margins = new double[n][];
        for (int i = 0; i < n; i++) {
            this.margins[i] = new double[this.bayesPm.getNumCategories(this.graphNodes[i])];
        }

        this.maxCardOrdering = new Node[n];
        this.roots = buildJunctionTree();

        initialize();
    }

    //=========================== INITIALIZATION =========================//

    /**
     * Reinitialize the clique potentials / messages (clears evidence effects).
     * This is called at construction and after temporary-evidence queries.
     */
    private void initialize() {
        // Clear margins (defensive; they will be recomputed by clique marginals).
        for (int i = 0; i < this.margins.length; i++) {
            Arrays.fill(this.margins[i], 0.0);
        }

        // Upward pass in reverse MCO.
        for (int i = this.maxCardOrdering.length - 1; i >= 0; i--) {
            TreeNode treeNode = this.treeNodes.get(this.maxCardOrdering[i]);
            if (treeNode != null) {
                treeNode.initializeUp();
            }
        }

        // Downward pass: do it root-by-root (forest-safe), recursively to cover all cliques.
        for (TreeNode root : this.roots) {
            root.initializeDown(true);
        }
    }

    //=========================== BUILD JUNCTION TREE =====================//

    /**
     * Create the clique tree (may be a forest if the BN has disconnected components).
     *
     * @return list of roots (never empty)
     */
    private List<TreeNode> buildJunctionTree() {
        // Moralize the BayesIm DAG (node objects consistent with bayesIm).
        Graph undirectedGraph = GraphTools.moralize(this.bayesIm.getDag());

        // Triangulate.
        computeMaximumCardinalityOrdering(undirectedGraph, this.maxCardOrdering);
        GraphTools.fillIn(undirectedGraph, this.maxCardOrdering);

        // Get cliques.
        computeMaximumCardinalityOrdering(undirectedGraph, this.maxCardOrdering);
        Map<Node, Set<Node>> cliques = GraphTools.getCliques(this.maxCardOrdering, undirectedGraph);

        // Kid gloves: ensure every node appears in some clique (singleton fallback).
        for (Node node : this.maxCardOrdering) {
            if (node == null) continue;
            if (!cliques.containsKey(node)) {
                Set<Node> singleton = new HashSet<>();
                singleton.add(node);
                cliques.put(node, singleton);
            }
        }

        // Separators and clique-tree parent mapping (may produce a forest).
        Map<Node, Set<Node>> separators = GraphTools.getSeparators(this.maxCardOrdering, cliques);
        Map<Node, Node> parentCliques = GraphTools.getCliqueTree(this.maxCardOrdering, cliques, separators);

        // Create TreeNodes.
        Set<Node> finishedCalculated = new HashSet<>();
        for (Node rep : this.maxCardOrdering) {
            if (rep == null) continue;
            Set<Node> clique = cliques.get(rep);
            if (clique != null) {
                this.treeNodes.put(rep, new TreeNode(clique, finishedCalculated));
            }
        }

        // Create separators/edges.
        for (Node childRep : this.maxCardOrdering) {
            if (childRep == null) continue;
            if (!cliques.containsKey(childRep)) continue;

            Node parentRep = parentCliques.get(childRep);
            if (parentRep == null) continue; // root of a component

            TreeNode parent = this.treeNodes.get(parentRep);
            TreeNode child = this.treeNodes.get(childRep);

            if (parent == null || child == null) {
                // Should not happen if cliques were built consistently.
                throw new IllegalStateException("Clique tree references missing TreeNode(s). child=" + childRep + ", parent=" + parentRep);
            }

            Set<Node> sep = separators.get(childRep);
            if (sep == null) sep = Set.of(); // be safe

            child.setParentSeparator(new TreeSeparator(sep, child, parent));
            parent.addChildClique(child);
        }

        // Collect roots (all clique reps with no parent).
        List<TreeNode> roots = new ArrayList<>();
        for (Node rep : this.treeNodes.keySet()) {
            if (!parentCliques.containsKey(rep)) {
                roots.add(this.treeNodes.get(rep));
            }
        }

        if (roots.isEmpty()) {
            // Should never happen; but if it does, fall back to any node.
            TreeNode any = this.treeNodes.values().stream().findFirst().orElse(null);
            if (any == null) {
                throw new IllegalStateException("No cliques were created; cannot build junction tree.");
            }
            roots.add(any);
        }

        return Collections.unmodifiableList(roots);
    }

    private void computeMaximumCardinalityOrdering(Graph graph, Node[] nodes) {
        Set<Node> numbered = new HashSet<>();

        for (int i = 0; i < nodes.length; i++) {
            Node maxCardinalityNode = null;
            int maxCardinality = -1;

            for (Node v : graph.getNodes()) {
                if (!numbered.contains(v)) {
                    int cardinality = (int) graph.getAdjacentNodes(v).stream()
                            .filter(numbered::contains)
                            .count();
                    if (cardinality > maxCardinality) {
                        maxCardinality = cardinality;
                        maxCardinalityNode = v;
                    }
                }
            }

            // Kid gloves: if graph has disconnected components, we still must pick something.
            if (maxCardinalityNode == null) {
                // pick any unnumbered
                for (Node v : graph.getNodes()) {
                    if (!numbered.contains(v)) {
                        maxCardinalityNode = v;
                        break;
                    }
                }
            }

            nodes[i] = maxCardinalityNode;
            if (maxCardinalityNode != null) {
                numbered.add(maxCardinalityNode);
            }
        }
    }

    //=========================== BAYES PM/IM HELPERS =====================//

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

    //=========================== UTILITIES ===============================//

    /**
     * Put nodes into an array in BayesIm index order (kid gloves).
     */
    private Node[] toArray(Set<Node> nodes) {
        int size = nodes.size();
        Node[] order = new Node[size];

        int index = 0;
        for (Node node : this.graphNodes) {
            if (nodes.contains(node)) {
                order[index++] = node;
                if (index == size) break;
            }
        }

        // If something went wrong with identity membership, fill remaining deterministically.
        if (index < size) {
            for (Node node : nodes) {
                boolean already = false;
                for (int j = 0; j < index; j++) {
                    if (order[j] == node) { already = true; break; }
                }
                if (!already) order[index++] = node;
                if (index == size) break;
            }
        }

        return order;
    }

    private void normalize(double[] values) {
        double sum = 0.0;

        for (double v : values) {
            if (!Double.isFinite(v)) {
                Arrays.fill(values, Double.NaN);
                return;
            }
            sum += v;
        }

        if (!Double.isFinite(sum) || sum <= 0.0) {
            Arrays.fill(values, Double.NaN);
            return;
        }

        for (int i = 0; i < values.length; i++) values[i] /= sum;
    }

    private int getCardinality(Set<Node> nodes) {
        int count = 1;
        for (Node n : nodes) {
            count *= this.bayesPm.getNumCategories(n);
        }
        return count;
    }

    private void updateValues(int size, int[] values, Node[] nodes) {
        int j = size - 1;
        values[j]++;
        while (j >= 0 && values[j] == this.bayesPm.getNumCategories(nodes[j])) {
            values[j] = 0;
            j--;
            if (j >= 0) values[j]++;
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
        Arrays.fill(array, 0.0);
    }

    private TreeNode getCliqueContainsNode(Node node) {
        // Kid gloves: search all cliques; forest-safe.
        for (TreeNode tn : this.treeNodes.values()) {
            if (tn.contains(node)) return tn;
        }
        return null;
    }

    private void validate(int iNode) {
        int maxIndex = this.margins.length - 1;
        if (iNode < 0 || iNode > maxIndex) {
            String msg = String.format("Invalid node index %d. Node index must be between 0 and %d.", iNode, maxIndex);
            throw new IllegalArgumentException(msg);
        }
        // margins[iNode] is never null in this hardened implementation.
    }

    private void validate(int iNode, int value) {
        validate(iNode);
        int maxValue = this.margins[iNode].length - 1;
        if (value < 0 || value > maxValue) {
            String msg = String.format("Invalid value %d for node index %d. Value must be between 0 and %d.", value, iNode, maxValue);
            throw new IllegalArgumentException(msg);
        }
    }

    private void validate(int[] nodes) {
        if (nodes == null) throw new IllegalArgumentException("Node indices cannot be null.");
        if (nodes.length == 0) throw new IllegalArgumentException("Node indices are required.");
        if (nodes.length > this.graphNodes.length) {
            String msg = String.format("Number of nodes cannot exceed %d.", this.graphNodes.length);
            throw new IllegalArgumentException(msg);
        }
    }

    private void validate(int[] nodes, int[] values) {
        validate(nodes);
        if (values == null) throw new IllegalArgumentException("Node values cannot be null.");
        if (values.length == 0) throw new IllegalArgumentException("Node values are required.");
        if (values.length != nodes.length) throw new IllegalArgumentException("Number of node values must equal number of nodes.");
        for (int i = 0; i < nodes.length; i++) validate(nodes[i], values[i]);
    }

    private void validateAll(int[] values) {
        if (values == null) throw new IllegalArgumentException("Node values cannot be null.");
        if (values.length == 0) throw new IllegalArgumentException("Node values are required.");
        if (values.length != this.graphNodes.length) throw new IllegalArgumentException("Number of node values must equal number of nodes.");
        for (int i = 0; i < values.length; i++) validate(i, values[i]);
    }

    private boolean isAllNodes(int[] nodes) {
        if (nodes.length != this.graphNodes.length) return false;
        long sum = 0;
        for (int v : nodes) sum += v;
        long total = ((long) (this.graphNodes.length - 1) * this.graphNodes.length) / 2;
        return sum == total;
    }

    //=========================== EVIDENCE API ============================//

    /**
     * Enter hard evidence X_i = value.
     */
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

    //=========================== QUERY API ===============================//

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

    /**
     * Joint probability of nodes given parents (implemented via setting parents as evidence and multiplying).
     * (Matches original semantics; exact clique queries would be richer but this preserves prior behavior.)
     */
    public double getConditionalProbabilities(int[] nodes, int[] values, int[] parents, int[] parentValues) {
        validate(nodes, values);
        validate(parents, parentValues);

        for (int i = 0; i < parents.length; i++) {
            setEvidence(parents[i], parentValues[i]);
        }

        double prob = 1.0;
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
     * Conditional probability table P(X_i | parents=parentValues) for all categories of X_i.
     */
    public double[] getConditionalProbabilities(int iNode, int[] parents, int[] parentValues) {
        validate(iNode);
        validate(parents, parentValues);

        if (parents.length == 1) {
            return getConditionalProbabilities(iNode, parents[0], parentValues[0]);
        }

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

    public double getConditionalProbability(int iNode, int value, int[] parents, int[] parentValues) {
        validate(iNode, value);
        return getConditionalProbabilities(iNode, parents, parentValues)[value];
    }

    /**
     * Joint probability of all nodes: values[i] = X_i value.
     */
    public double getJointProbabilityAll(int[] nodeValues) {
        validateAll(nodeValues);

        // Forest-safe: sum over roots of log potentials (independent components multiply => logs add).
        double logCluster = 0.0;
        double logSep = 0.0;
        for (TreeNode r : this.roots) {
            logCluster += r.getLogJointClusterPotentials(nodeValues);
            logSep += r.getLogJointSeparatorPotentials(nodeValues);
        }

        return FastMath.exp(logCluster - logSep);
    }

    /**
     * P(nodes=values) treating those assignments as evidence, returning P(evidence).
     */
    public double getJointProbability(int[] nodes, int[] values) {
        validate(nodes, values);

        if (isAllNodes(nodes)) {
            return getJointProbabilityAll(values);
        }

        for (int i = 0; i < nodes.length; i++) {
            setEvidence(nodes[i], values[i]);
        }

        // After evidence, margins[i][*] is proportional to P(X_i=*, evidence). Summing gives P(evidence).
        // Choose any queried node as anchor.
        double prob = Arrays.stream(this.margins[nodes[0]]).sum();

        // reset
        initialize();

        return prob;
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

        double[] marg = new double[this.margins[iNode].length];
        System.arraycopy(this.margins[iNode], 0, marg, 0, marg.length);
        normalize(marg);

        return marg[value];
    }

    public List<Node> getNodes() {
        return Collections.unmodifiableList(Arrays.asList(this.graphNodes));
    }

    public int getNumberOfNodes() {
        return this.graphNodes.length;
    }

    @Override
    public String toString() {
        // Forest-safe print.
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < roots.size(); r++) {
            if (r > 0) sb.append("\n==== COMPONENT ").append(r + 1).append(" ====\n");
            sb.append(roots.get(r).toString().trim());
        }
        return sb.toString().trim();
    }

    //=========================== SERIALIZATION ===========================//

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

    //=========================== INNER CLASSES ===========================//

    private class TreeSeparator implements TetradSerializable {
        private static final long serialVersionUID = 23L;

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
        private static final long serialVersionUID = 23L;

        /** Distribution over this clique (unnormalized potentials/messages applied). */
        private final double[] prob;

        private final double[][] margProb;
        private final double[] potentials;
        private final List<TreeNode> children;
        private final int cardinality;
        private final Set<Node> clique;
        private final Node[] nodes;
        private TreeSeparator parentSeparator;

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

            int size = this.nodes.length;
            int[] values = new int[size];

            for (int i = 0; i < this.cardinality; i++) {
                int indexCPT = getIndexOfCPT(this.nodes, values);
                this.potentials[indexCPT] = 1.0;

                for (int iNode = 0; iNode < this.nodes.length; iNode++) {
                    Node node = this.nodes[iNode];
                    if (nodesWithParentsInCluster.contains(node)) {
                        int nodeIndex = JunctionTreeAlgorithm.this.getBayesImIndex(node);
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

            if (this.parentSeparator != null) {
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
                        this.prob[indexNodeCPT] *= (this.parentSeparator.parentPotentials[indexSepCPT]
                                / this.parentSeparator.childPotentials[indexSepCPT]);
                    } else {
                        this.prob[indexNodeCPT] = 0.0;
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

        private void calculateMarginalProbabilities() {
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

            // Write clique marginals into global margins by *BayesIm index* (kid gloves).
            for (int iNode = 0; iNode < size; iNode++) {
                int idx = JunctionTreeAlgorithm.this.getBayesImIndex(this.nodes[iNode]);
                JunctionTreeAlgorithm.this.margins[idx] = this.margProb[iNode];
            }
        }

        private int getRowIndex(int nodeIndex, int[] values, Node[] nodes) {
            int index = 0;

            int[] parents = JunctionTreeAlgorithm.this.bayesIm.getParents(nodeIndex);
            for (int parent : parents) {
                Node parentNode = JunctionTreeAlgorithm.this.bayesIm.getNode(parent);
                index *= JunctionTreeAlgorithm.this.bayesPm.getNumCategories(parentNode);

                for (int j = 0; j < nodes.length; j++) {
                    if (parentNode == nodes[j]) {
                        index += values[j];
                        break;
                    }
                }
            }

            return index;
        }

        private int getNodeIndexInClique(Node node) {
            for (int i = 0; i < this.nodes.length; i++) {
                if (this.nodes[i] == node) return i;
            }
            return -1;
        }

        public void setEvidence(Node node, int value) {
            int nodeIndex = getNodeIndexInClique(node);
            if (nodeIndex < 0) {
                String msg = String.format("Unable to find node %s in clique.", node.getName());
                throw new IllegalArgumentException(msg);
            }

            int size = this.nodes.length;
            int[] values = new int[size];

            for (int i = 0; i < this.cardinality; i++) {
                if (values[nodeIndex] != value) {
                    int indexNodeCPT = getIndexOfCPT(this.nodes, values);
                    this.prob[indexNodeCPT] = 0.0;
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

                    if (source.parentSeparator.parentPotentials[indexChildNodeCPT] != 0.0) {
                        this.prob[indexNodeCPT] *= source.parentSeparator.childPotentials[indexChildNodeCPT]
                                / source.parentSeparator.parentPotentials[indexChildNodeCPT];
                    } else {
                        this.prob[indexNodeCPT] = 0.0;
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
            double logJointPotentials = FastMath.log(1.0);

            if (this.parentSeparator != null) {
                Node[] parentNodes = this.parentSeparator.nodes;
                int size = parentNodes.length;
                int[] values = new int[size];

                for (int iNode = 0; iNode < size; iNode++) {
                    int idx = JunctionTreeAlgorithm.this.getBayesImIndex(parentNodes[iNode]);
                    values[iNode] = nodeValues[idx];
                }

                logJointPotentials += FastMath.log(this.parentSeparator.childPotentials[getIndexOfCPT(parentNodes, values)]);
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
                int idx = JunctionTreeAlgorithm.this.getBayesImIndex(this.nodes[iNode]);
                values[iNode] = nodeValues[idx];
            }

            double logJointPotentials = FastMath.log(this.prob[getIndexOfCPT(this.nodes, values)]);
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

        public boolean contains(Node node) {
            return this.clique.contains(node);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();

            for (int i = 0; i < this.nodes.length; i++) {
                sb.append(this.nodes[i].getName()).append(": ");
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

    /**
     * Kid-gloves BayesIm index lookup (identity first, then name fallback).
     */
    private int getBayesImIndex(Node node) {
        Integer idx = this.bayesImIndexByNode.get(node);
        if (idx != null) return idx;

        // Fallback: name lookup (handles cases where moralization/clique tools cloned nodes).
        Node byName = this.bayesIm.getNode(node.getName());
        if (byName == null) {
            throw new IllegalStateException("Node '" + node.getName() + "' not found in BayesIm.");
        }
        int i = this.bayesIm.getNodeIndex(byName);
        // Cache the mapping for future (even if identity differs).
        this.bayesImIndexByNode.put(node, i);
        return i;
    }
}