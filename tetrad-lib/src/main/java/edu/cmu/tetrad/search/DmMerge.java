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

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.search.test.IndependenceTest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Implements the Detect-Mimic-PC algorithm.
 *
 * <p>This procedure is intended for settings resembling Multiple Input Multiple Indicator
 * models, where measured input variables may act through one or more intermediate latent
 * variables that then influence measured output variables. The algorithm is a heuristic
 * construction built on top of PC searches over the measured variables.</p>
 *
 * <p>The procedure is:</p>
 *
 * <ol>
 *   <li>Run PC with depth 0 on the measured variables.</li>
 *   <li>Classify measured variables into tentative inputs and outputs using the depth-0
 *   graph. Variables with indegree 0 and positive outdegree are treated as inputs.
 *   Variables with positive indegree are treated as outputs.</li>
 *   <li>For each output, collect the set of adjacent input variables in the depth-0 graph.</li>
 *   <li>Cluster outputs by identical associated-input sets.</li>
 *   <li>For each nonempty cluster, create a latent variable. Add directed edges from the
 *   associated measured inputs into that latent, and from the latent into the outputs in
 *   that cluster.</li>
 *   <li>For two latent variables whose associated input sets are in a strict subset relation,
 *   add a directed edge from the latent with the smaller input set to the latent with the
 *   larger input set.</li>
 *   <li>Refine latent-to-latent edges by removing a latent edge when every measured output
 *   child of one latent is conditionally independent of every measured output child of the
 *   other latent, given the union of the measured input parents of the two latents.</li>
 *   <li>Run a full-depth PC search and use it for a final cleanup of output relationships
 *   and for removal of degenerate latent variables.</li>
 * </ol>
 *
 * <p>This class does not claim to be a general-purpose latent-variable discovery method.
 * It is specialized to the detect-mimic construction described above.</p>
 *
 * @author murraywaters
 * @author josephramsey
 */
public class DmMerge implements IGraphSearch {

    /**
     * Tentative measured input variables identified during the current search.
     */
    private final List<Node> inputs = new ArrayList<>();

    /**
     * Tentative measured output variables identified during the current search.
     */
    private final List<Node> outputs = new ArrayList<>();

    /**
     * The conditional independence test used throughout the procedure.
     */
    private IndependenceTest test;

    /**
     * Background knowledge supplied by the caller.
     */
    private Knowledge knowledge = new Knowledge();

    /**
     * Counter used to generate unique latent names.
     */
    private int latentIndex = 1;

    /**
     * Constructs a new DM-PC search using the given independence test.
     *
     * @param test the independence test to use
     */
    public DmMerge(IndependenceTest test) {
        if (test == null) {
            throw new NullPointerException("Independence test must not be null.");
        }

        this.test = test;
    }

    /**
     * Runs the DM-PC search and returns the resulting graph.
     *
     * <p>The search always starts from a fresh internal state. In particular, the
     * internally stored lists of tentative inputs and outputs are cleared, and latent
     * variable names are regenerated starting at L1 for each call.</p>
     *
     * @return the graph constructed by the DM-PC procedure
     */
    @Override
    public Graph search() {
        resetState();

        Graph depth0Pattern = runPc(0);
        classifyVariables(depth0Pattern);

//        Map<Set<Node>, Set<Node>> clusters = clusterOutputs(depth0Pattern);
//        Graph latentGraph = buildLatentStructure(clusters);

        Map<Set<Node>, Set<Node>> clusters = clusterOutputs(depth0Pattern);
        clusters = mergeSimilarClusters(clusters);
        Graph latentGraph = buildLatentStructure(clusters);

        refineLatentEdges(latentGraph);
        finalRefinement(latentGraph);

        return latentGraph;
    }

    /**
     * Merges clusters whose associated-input sets are sufficiently similar.
     *
     * <p>This is intended to reduce overfragmentation caused by clustering outputs by
     * exact associated-input sets. Two clusters are merged when their input sets have
     * high Jaccard similarity. The merged cluster uses the union of the input sets and
     * the union of the output sets.</p>
     *
     * @param clusters the initial clusters
     * @return the merged clusters
     */
    private Map<Set<Node>, Set<Node>> mergeSimilarClusters(Map<Set<Node>, Set<Node>> clusters) {
        List<ClusterRecord> records = new ArrayList<>();

        for (Map.Entry<Set<Node>, Set<Node>> entry : clusters.entrySet()) {
            records.add(new ClusterRecord(entry.getKey(), entry.getValue()));
        }

        double threshold = 0.8;
        boolean changed;

        do {
            changed = false;

            outer:
            for (int i = 0; i < records.size(); i++) {
                for (int j = i + 1; j < records.size(); j++) {
                    ClusterRecord a = records.get(i);
                    ClusterRecord b = records.get(j);

                    double similarity = jaccard(a.inputSet(), b.inputSet());

                    if (similarity >= threshold) {
                        Set<Node> mergedInputs = new HashSet<>(a.inputSet());
                        mergedInputs.addAll(b.inputSet());

                        Set<Node> mergedOutputs = new HashSet<>(a.outputSet());
                        mergedOutputs.addAll(b.outputSet());

                        records.remove(j);
                        records.remove(i);
                        records.add(new ClusterRecord(mergedInputs, mergedOutputs));

                        changed = true;
                        break outer;
                    }
                }
            }
        } while (changed);

        Map<Set<Node>, Set<Node>> merged = new HashMap<>();

        for (ClusterRecord record : records) {
            merged.put(record.inputSet(), record.outputSet());
        }

        return merged;
    }

    /**
     * Returns the Jaccard similarity of two sets.
     *
     * @param a the first set
     * @param b the second set
     * @return the Jaccard similarity
     */
    private double jaccard(Set<Node> a, Set<Node> b) {
        if (a.isEmpty() && b.isEmpty()) {
            return 1.0;
        }

        Set<Node> intersection = new HashSet<>(a);
        intersection.retainAll(b);

        Set<Node> union = new HashSet<>(a);
        union.addAll(b);

        if (union.isEmpty()) {
            return 1.0;
        }

        return (double) intersection.size() / union.size();
    }

    /**
     * Small record type for cluster merging.
     *
     * @param inputSet the associated input set
     * @param outputSet the associated output set
     */
    private record ClusterRecord(Set<Node> inputSet, Set<Node> outputSet) {
        private ClusterRecord(Set<Node> inputSet, Set<Node> outputSet) {
            this.inputSet = new HashSet<>(inputSet);
            this.outputSet = new HashSet<>(outputSet);
        }
    }

    /**
     * Returns the independence test currently used by this search.
     *
     * @return the independence test
     */
    public IndependenceTest getTest() {
        return this.test;
    }

    /**
     * Replaces the independence test used by this search.
     *
     * <p>The replacement test must be defined over exactly the same variable list, in the
     * same order, as the current test. This prevents accidental replacement by a test over
     * a different dataset or variable order.</p>
     *
     * @param test the replacement independence test
     * @throws NullPointerException if the supplied test is null
     * @throws IllegalArgumentException if the variable lists are not equal list-wise
     */
    public void setTest(IndependenceTest test) {
        if (test == null) {
            throw new NullPointerException("Independence test must not be null.");
        }

        List<Node> nodes = this.test.getVariables();
        List<Node> newNodes = test.getVariables();

        if (!nodes.equals(newNodes)) {
            throw new IllegalArgumentException(
                    "The nodes of the proposed new test are not equal list-wise to the nodes of the existing test."
            );
        }

        this.test = test;
    }

    /**
     * Sets the background knowledge used by this search.
     *
     * <p>A defensive copy is stored so later external modifications to the supplied
     * knowledge object do not change the behavior of this search unexpectedly.</p>
     *
     * @param knowledge the background knowledge to use
     * @throws NullPointerException if the supplied knowledge is null
     */
    public void setKnowledge(Knowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException("Knowledge must not be null.");
        }

        this.knowledge = new Knowledge(knowledge);
    }

    /**
     * Clears state that should not persist across repeated calls to {@link #search()}.
     */
    private void resetState() {
        this.inputs.clear();
        this.outputs.clear();
        this.latentIndex = 1;
    }

    /**
     * Runs PC with the given depth using the current test and knowledge.
     *
     * @param depth the PC depth, where -1 means unrestricted depth
     * @return the graph returned by PC
     */
    private Graph runPc(int depth) {
        try {
            Pc pc = new Pc(this.test);
            pc.setDepth(depth);
            pc.setKnowledge(this.knowledge);
            return pc.search();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("PC search was interrupted.", e);
        }
    }

    /**
     * Classifies measured variables into tentative inputs and outputs using the given graph.
     *
     * <p>The classification rule is simple:</p>
     *
     * <ul>
     *   <li>indegree 0 and outdegree greater than 0 implies input</li>
     *   <li>indegree greater than 0 implies output</li>
     * </ul>
     *
     * <p>Isolated variables are ignored by this procedure.</p>
     *
     * @param graph the graph used for classification
     */
    private void classifyVariables(Graph graph) {
        for (Node node : graph.getNodes()) {
            int indegree = graph.getIndegree(node);
            int outdegree = graph.getOutdegree(node);

            if (indegree == 0 && outdegree > 0) {
                this.inputs.add(node);
            } else if (indegree > 0) {
                this.outputs.add(node);
            }
        }
    }

    /**
     * Groups outputs by the set of measured inputs adjacent to them in the given graph.
     *
     * <p>Each key is a set of associated inputs, and each value is the set of outputs that
     * share exactly that associated-input set.</p>
     *
     * @param initialGraph the depth-0 PC graph
     * @return a map from associated-input sets to output clusters
     */
    private Map<Set<Node>, Set<Node>> clusterOutputs(Graph initialGraph) {
        Map<Set<Node>, Set<Node>> clusters = new HashMap<>();

        for (Node output : this.outputs) {
            if (output.getNodeType() == NodeType.LATENT) {
                continue;
            }

            Set<Node> associatedInputs = getAssociatedInputs(output, initialGraph);
            clusters.computeIfAbsent(associatedInputs, k -> new HashSet<>()).add(output);
        }

        return clusters;
    }

    /**
     * Returns the measured inputs adjacent to the given output in the supplied graph.
     *
     * @param output the output node
     * @param graph the graph in which adjacency is checked
     * @return the set of adjacent measured inputs
     */
    private Set<Node> getAssociatedInputs(Node output, Graph graph) {
        Set<Node> associatedInputs = new HashSet<>();

        for (Node input : this.inputs) {
            if (graph.isAdjacentTo(input, output)) {
                associatedInputs.add(input);
            }
        }

        return associatedInputs;
    }

    /**
     * Builds the initial latent structure from the clustered outputs.
     *
     * <p>For each nonempty cluster, a latent variable is created. The measured inputs in the
     * key set point to the latent, and the latent points to the measured outputs in the value
     * set.</p>
     *
     * <p>After this, latent-to-latent edges are added for strict subset relations among the
     * associated-input sets. If input set B is a strict subset of input set A, the latent for
     * B points to the latent for A. This follows the convention used by the existing DM-PC
     * construction.</p>
     *
     * @param clusters the clustered outputs
     * @return the initial graph containing measured and latent nodes
     */
    private Graph buildLatentStructure(Map<Set<Node>, Set<Node>> clusters) {
        Graph graph = new EdgeListGraph();

        for (Node input : this.inputs) {
            graph.addNode(input);
        }

        for (Node output : this.outputs) {
            graph.addNode(output);
        }

        Map<Set<Node>, Node> latentNodes = new HashMap<>();

        for (Map.Entry<Set<Node>, Set<Node>> entry : clusters.entrySet()) {
            Set<Node> inputSet = entry.getKey();
            Set<Node> outputSet = entry.getValue();

            if (inputSet.isEmpty() || outputSet.isEmpty()) {
                continue;
            }

            Node latent = createLatentNode();
            latentNodes.put(inputSet, latent);
            graph.addNode(latent);

            for (Node input : inputSet) {
                graph.addDirectedEdge(input, latent);
            }

            for (Node output : outputSet) {
                graph.addDirectedEdge(latent, output);
            }
        }

        List<Set<Node>> inputSets = new ArrayList<>(latentNodes.keySet());

        for (Set<Node> setA : inputSets) {
            for (Set<Node> setB : inputSets) {
                if (setA.equals(setB)) {
                    continue;
                }

                if (setA.containsAll(setB)) {
                    Node latentB = latentNodes.get(setB);
                    Node latentA = latentNodes.get(setA);

                    if (latentB != null && latentA != null && !graph.isAdjacentTo(latentB, latentA)) {
                        graph.addDirectedEdge(latentB, latentA);
                    }
                }
            }
        }

        return graph;
    }

    /**
     * Creates a new latent node with a unique name.
     *
     * @return the newly created latent node
     */
    private Node createLatentNode() {
        String latentName = "L" + this.latentIndex++;
        Node latent = new GraphNode(latentName);
        latent.setNodeType(NodeType.LATENT);
        return latent;
    }

    /**
     * Refines latent-to-latent edges in the given graph.
     *
     * <p>A latent edge is removed if every measured output child of one latent is conditionally
     * independent of every measured output child of the other latent, given the union of the
     * measured input parents of the two latents.</p>
     *
     * @param graph the graph whose latent edges are to be refined
     */
    private void refineLatentEdges(Graph graph) {
        List<Edge> edges = new ArrayList<>(graph.getEdges());

        for (Edge edge : edges) {
            Node node1 = edge.getNode1();
            Node node2 = edge.getNode2();

            if (node1.getNodeType() == NodeType.LATENT && node2.getNodeType() == NodeType.LATENT) {
                if (canRemoveLatentEdge(node1, node2, graph)) {
                    graph.removeEdge(edge);
                }
            }
        }
    }

    /**
     * Returns true if the latent edge between the two supplied latents should be removed.
     *
     * @param latentA the first latent
     * @param latentB the second latent
     * @param graph the graph containing the latents
     * @return true if the edge should be removed
     */
    private boolean canRemoveLatentEdge(Node latentA, Node latentB, Graph graph) {
        Set<Node> inputsToA = getMeasuredParents(latentA, graph);
        Set<Node> inputsToB = getMeasuredParents(latentB, graph);

        Set<Node> conditioningSet = new HashSet<>(inputsToA);
        conditioningSet.addAll(inputsToB);

        Set<Node> outputsOfA = getMeasuredChildren(latentA, graph);
        Set<Node> outputsOfB = getMeasuredChildren(latentB, graph);

        return areConditionallyIndependent(outputsOfA, outputsOfB, conditioningSet);
    }

    /**
     * Returns the measured parents of the given node in the given graph.
     *
     * @param node the node of interest
     * @param graph the graph containing the node
     * @return the measured parents of the node
     */
    private Set<Node> getMeasuredParents(Node node, Graph graph) {
        return removeLatents(new HashSet<>(graph.getParents(node)));
    }

    /**
     * Returns the measured children of the given node in the given graph.
     *
     * @param node the node of interest
     * @param graph the graph containing the node
     * @return the measured children of the node
     */
    private Set<Node> getMeasuredChildren(Node node, Graph graph) {
        return removeLatents(new HashSet<>(graph.getChildren(node)));
    }

    /**
     * Removes latent nodes from the given set.
     *
     * @param nodes the input set
     * @return a new set containing only measured nodes
     */
    private Set<Node> removeLatents(Set<Node> nodes) {
        Set<Node> measured = new HashSet<>();

        for (Node node : nodes) {
            if (node.getNodeType() != NodeType.LATENT) {
                measured.add(node);
            }
        }

        return measured;
    }

    /**
     * Returns true if every cross-pair from the two supplied sets is conditionally independent
     * given the supplied conditioning set.
     *
     * @param setA the first measured-node set
     * @param setB the second measured-node set
     * @param conditioningSet the conditioning set
     * @return true if all tested cross-pairs are conditionally independent
     */
    private boolean areConditionallyIndependent(Set<Node> setA, Set<Node> setB, Set<Node> conditioningSet) {
        Set<Node> cleanSetA = removeLatents(setA);
        Set<Node> cleanSetB = removeLatents(setB);
        Set<Node> cleanConditioningSet = removeLatents(conditioningSet);

        try {
            for (Node a : cleanSetA) {
                for (Node b : cleanSetB) {
                    if (!this.test.checkIndependence(a, b, cleanConditioningSet).isIndependent()) {
                        return false;
                    }
                }
            }

            return true;
        } catch (Exception e) {
            throw new RuntimeException("Conditional independence testing failed.", e);
        }
    }

    /**
     * Performs the final cleanup step using a full-depth PC graph on the measured variables.
     *
     * <p>For each measured output, if the full PC graph shows no adjacency from that output to
     * any tentative input, latent-to-output edges into that output are removed. Measured
     * output-to-output adjacencies from the full PC graph are then added back as undirected
     * edges if they are missing.</p>
     *
     * <p>Finally, latent variables with no measured parents or no measured children are removed.</p>
     *
     * @param graph the graph to refine
     */
    private void finalRefinement(Graph graph) {
        Graph fullPattern = runPc(-1);

        for (Node output : this.outputs) {
            boolean adjacentToAnyInput = false;

            for (Node neighbor : fullPattern.getAdjacentNodes(output)) {
                if (this.inputs.contains(neighbor)) {
                    adjacentToAnyInput = true;
                    break;
                }
            }

            if (!adjacentToAnyInput) {
                removeLatentParents(output, graph);
                addMeasuredOutputAdjacencies(output, fullPattern, graph);
            }
        }

//        mergeEquivalentLatents(graph);
////        removeExplainedMeasuredToLatentEdges(graph);
//        removeDegenerateLatents(graph);

        mergeEquivalentLatents(graph);
        removeExplainedMeasuredToLatentEdgesByImmediateParents(graph);
        removeDegenerateLatents(graph);
    }

    /**
     * Removes measured-to-latent edges that are explained by an immediate latent parent.
     *
     * <p>Specifically, if X -> P, P -> L, and X -> L, where P is a latent parent of L,
     * then X -> L is removed as redundant. This is a local pruning rule intended to
     * reduce the tendency of measured parent sets to smear downward through the latent
     * hierarchy.</p>
     *
     * @param graph the graph to modify
     */
    private void removeExplainedMeasuredToLatentEdgesByImmediateParents(Graph graph) {
        boolean changed;

        do {
            changed = false;
            List<Edge> edges = new ArrayList<>(graph.getEdges());

            for (Edge edge : edges) {
                Node x = edge.getNode1();
                Node latent = edge.getNode2();

                if (x.getNodeType() == NodeType.LATENT) {
                    continue;
                }

                if (latent.getNodeType() != NodeType.LATENT) {
                    continue;
                }

                if (!graph.isParentOf(x, latent)) {
                    continue;
                }

                if (isExplainedByImmediateLatentParent(x, latent, graph)) {
                    graph.removeEdge(edge);
                    changed = true;
                }
            }
        } while (changed);
    }

    /**
     * Returns true if the measured-to-latent edge X -> L is explained by some immediate
     * latent parent P of L such that X -> P.
     *
     * @param measuredParent the measured parent X
     * @param latent the latent node L
     * @param graph the graph
     * @return true if X -> L is explained by an immediate latent parent
     */
    private boolean isExplainedByImmediateLatentParent(Node measuredParent, Node latent, Graph graph) {
        for (Node parent : graph.getParents(latent)) {
            if (parent.getNodeType() != NodeType.LATENT) {
                continue;
            }

            if (graph.isParentOf(measuredParent, parent)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Removes measured-to-latent edges that are explained by a path through an upstream latent.
     *
     * <p>Specifically, if X -> L1, L1 -> ... -> L2, and X -> L2, then X -> L2 is removed as
     * redundant. This acts like a transitive reduction for measured-to-latent parent edges
     * relative to the latent graph.</p>
     *
     * @param graph the graph to modify
     */
    private void removeExplainedMeasuredToLatentEdges(Graph graph) {
        boolean changed;

        do {
            changed = false;

            List<Edge> edges = new ArrayList<>(graph.getEdges());

            for (Edge edge : edges) {
                Node x = edge.getNode1();
                Node latent = edge.getNode2();

                if (x.getNodeType() == NodeType.LATENT) {
                    continue;
                }

                if (latent.getNodeType() != NodeType.LATENT) {
                    continue;
                }

                if (!graph.isParentOf(x, latent)) {
                    continue;
                }

                if (hasExplainingLatentPath(x, latent, graph)) {
                    graph.removeEdge(x, latent);
                    changed = true;
                }
            }
        } while (changed);
    }

    /**
     * Returns true if the measured-to-latent edge X -> targetLatent is explained by some
     * upstream latent L such that X -> L and L -> ... -> targetLatent.
     *
     * @param measuredParent the measured parent X
     * @param targetLatent the downstream latent L2
     * @param graph the graph
     * @return true if the edge is explained by an upstream latent path
     */
    private boolean hasExplainingLatentPath(Node measuredParent, Node targetLatent, Graph graph) {
        for (Node parent : graph.getParents(targetLatent)) {
            if (parent.getNodeType() != NodeType.LATENT) {
                continue;
            }

            if (graph.isParentOf(measuredParent, parent)) {
                return true;
            }

            if (hasLatentPathFromMeasuredParent(measuredParent, parent, graph, new HashSet<>())) {
                return true;
            }
        }

        return false;
    }

    /**
     * Returns true if there exists a directed path measuredParent -> L1 -> ... -> latent.
     *
     * @param measuredParent the measured parent X
     * @param latent the latent node currently being tested
     * @param graph the graph
     * @param visited visited latent nodes
     * @return true if such a path exists
     */
    private boolean hasLatentPathFromMeasuredParent(Node measuredParent,
                                                    Node latent,
                                                    Graph graph,
                                                    Set<Node> visited) {
        if (!visited.add(latent)) {
            return false;
        }

        if (graph.isParentOf(measuredParent, latent)) {
            return true;
        }

        for (Node parent : graph.getParents(latent)) {
            if (parent.getNodeType() == NodeType.LATENT) {
                if (hasLatentPathFromMeasuredParent(measuredParent, parent, graph, visited)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Merges latent variables that have the same measured parents and measured children.
     *
     * @param graph the graph to modify
     */
    private void mergeEquivalentLatents(Graph graph) {
        boolean changed;

        do {
            changed = false;
            List<Node> latents = new ArrayList<>();

            for (Node node : graph.getNodes()) {
                if (node.getNodeType() == NodeType.LATENT) {
                    latents.add(node);
                }
            }

            outer:
            for (int i = 0; i < latents.size(); i++) {
                for (int j = i + 1; j < latents.size(); j++) {
                    Node a = latents.get(i);
                    Node b = latents.get(j);

                    if (getMeasuredParents(a, graph).equals(getMeasuredParents(b, graph))
                            && getMeasuredChildren(a, graph).equals(getMeasuredChildren(b, graph))) {

                        for (Node parent : graph.getParents(b)) {
                            if (graph.getEdge(parent, a) == null) {
                                graph.addDirectedEdge(parent, a);
                            }
                        }

                        for (Node child : graph.getChildren(b)) {
                            if (graph.getEdge(a, child) == null) {
                                graph.addDirectedEdge(a, child);
                            }
                        }

                        graph.removeNode(b);
                        changed = true;
                        break outer;
                    }
                }
            }
        } while (changed);
    }

    /**
     * Removes all latent-to-output edges into the given measured output.
     *
     * @param output the measured output
     * @param graph the graph to modify
     */
    private void removeLatentParents(Node output, Graph graph) {
        List<Node> parents = new ArrayList<>(graph.getParents(output));

        for (Node parent : parents) {
            if (parent.getNodeType() == NodeType.LATENT) {
                graph.removeEdge(parent, output);
            }
        }
    }

    /**
     * Adds undirected measured-output adjacencies for the given output based on the supplied
     * full PC graph.
     *
     * @param output the output whose measured-output adjacencies are to be added
     * @param fullPattern the full-depth PC graph
     * @param graph the graph being refined
     */
    private void addMeasuredOutputAdjacencies(Node output, Graph fullPattern, Graph graph) {
        for (Node neighbor : fullPattern.getAdjacentNodes(output)) {
            if (this.outputs.contains(neighbor) && !graph.isAdjacentTo(output, neighbor)) {
                graph.addUndirectedEdge(output, neighbor);
            }
        }
    }

//    /**
//     * Removes latent variables that no longer have both measured parents and measured children.
//     *
//     * @param graph the graph to modify
//     */
//    private void removeDegenerateLatents(Graph graph) {
//        List<Node> nodes = new ArrayList<>(graph.getNodes());
//
//        for (Node node : nodes) {
//            if (node.getNodeType() != NodeType.LATENT) {
//                continue;
//            }
//
//            Set<Node> measuredParents = getMeasuredParents(node, graph);
//            Set<Node> measuredChildren = getMeasuredChildren(node, graph);
//
//            if (measuredParents.isEmpty() || measuredChildren.isEmpty()) {
//                graph.removeNode(node);
//            }
//        }
//    }

    /**
     * Removes latent variables that no longer have both measured parents and measured children.
     *
     * <p>In addition, removes latents that are completely disconnected from the rest of the
     * latent structure, since these often represent over-fragmentation of the measurement
     * model rather than meaningful intermediate latent structure.</p>
     *
     * @param graph the graph to modify
     */
    private void removeDegenerateLatents(Graph graph) {
        boolean changed;

        do {
            changed = false;
            List<Node> nodes = new ArrayList<>(graph.getNodes());

            for (Node node : nodes) {
                if (node.getNodeType() != NodeType.LATENT) {
                    continue;
                }

                Set<Node> measuredParents = getMeasuredParents(node, graph);
                Set<Node> measuredChildren = getMeasuredChildren(node, graph);
                Set<Node> latentNeighbors = getLatentNeighbors(node, graph);

                boolean structurallyDegenerate =
                        measuredParents.isEmpty() || measuredChildren.isEmpty();

                boolean weakIsolatedLatent =
                        latentNeighbors.isEmpty()
                                && (measuredParents.size() <= 1 || measuredChildren.size() <= 1);

                if (structurallyDegenerate || weakIsolatedLatent) {
                    graph.removeNode(node);
                    changed = true;
                }
            }
        } while (changed);
    }
    /**
     * Returns the latent neighbors of the given latent node.
     *
     * @param node the latent node
     * @param graph the graph containing the node
     * @return the latent neighbors of the node
     */
    private Set<Node> getLatentNeighbors(Node node, Graph graph) {
        Set<Node> latentNeighbors = new HashSet<>();

        for (Node adjacent : graph.getAdjacentNodes(node)) {
            if (adjacent.getNodeType() == NodeType.LATENT) {
                latentNeighbors.add(adjacent);
            }
        }

        return latentNeighbors;
    }
}