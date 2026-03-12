package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.test.IndependenceTest;

import java.util.*;

/**
 * A more robust detect-mimic heuristic for MIMIC-like structures.
 *
 * <p>This version avoids clustering outputs by exact input-adjacency signatures.
 * Instead, it computes approximate input signatures for outputs, groups outputs
 * by similarity, estimates a representative measured-parent set for each output
 * cluster, prunes weak clusters, merges redundant clusters, and only then builds
 * a latent structure.</p>
 */
public class DmPcRobust implements IGraphSearch {

    private final List<Node> inputs = new ArrayList<>();
    private final List<Node> outputs = new ArrayList<>();
    private IndependenceTest test;
    private Knowledge knowledge = new Knowledge();
    private int latentIndex = 1;

    /**
     * Minimum Jaccard similarity between two output signatures needed to connect them.
     */
    private double signatureSimilarityThreshold = 0.75;

    /**
     * Minimum fraction of outputs in a cluster that must show an input for that input
     * to be included in the cluster parent set.
     */
    private double parentFrequencyThreshold = 0.60;

    /**
     * Minimum fraction of within-cluster output pairs that must remain dependent
     * given the candidate parent set.
     */
    private double clusterCoherenceThreshold = 0.75;

    /**
     * Minimum number of outputs required for a latent cluster.
     */
    private int minClusterSize = 2;

    /**
     * Constructs the search from an independence test.
     *
     * @param test the independence test
     */
    public DmPcRobust(IndependenceTest test) {
        if (test == null) {
            throw new NullPointerException("Independence test must not be null.");
        }

        this.test = test;
    }

    @Override
    public Graph search() {
        resetState();

        Graph depth0Pattern = runPc(0);
        classifyVariables(depth0Pattern);

        Map<Node, Set<Node>> outputSignatures = computeOutputSignatures(depth0Pattern);
        List<Set<Node>> outputClusters = clusterOutputsBySignature(outputSignatures);

        List<LatentCandidate> candidates = new ArrayList<>();

        for (Set<Node> outputCluster : outputClusters) {
            if (outputCluster.size() < this.minClusterSize) {
                continue;
            }

            Set<Node> parentSet = summarizeParentSet(outputCluster, outputSignatures);

            if (parentSet.isEmpty()) {
                continue;
            }

            if (isCoherentCluster(outputCluster, parentSet)) {
                candidates.add(new LatentCandidate(parentSet, outputCluster));
            }
        }

        candidates = mergeRedundantCandidates(candidates);

        Graph graph = buildLatentGraph(candidates);

        pruneInheritedMeasuredParentsStructurally(graph);
        removeDegenerateLatents(graph);

        return graph;
    }

    public void setKnowledge(Knowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException("Knowledge must not be null.");
        }

        this.knowledge = new Knowledge(knowledge);
    }

    public void setSignatureSimilarityThreshold(double threshold) {
        this.signatureSimilarityThreshold = threshold;
    }

    public void setParentFrequencyThreshold(double threshold) {
        this.parentFrequencyThreshold = threshold;
    }

    public void setClusterCoherenceThreshold(double threshold) {
        this.clusterCoherenceThreshold = threshold;
    }

    public void setMinClusterSize(int minClusterSize) {
        this.minClusterSize = minClusterSize;
    }

    private void resetState() {
        this.inputs.clear();
        this.outputs.clear();
        this.latentIndex = 1;
    }

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

    private Map<Node, Set<Node>> computeOutputSignatures(Graph graph) {
        Map<Node, Set<Node>> signatures = new HashMap<>();

        for (Node output : this.outputs) {
            Set<Node> signature = new HashSet<>();

            for (Node input : this.inputs) {
                if (graph.isAdjacentTo(input, output)) {
                    signature.add(input);
                }
            }

            signatures.put(output, signature);
        }

        return signatures;
    }

    private List<Set<Node>> clusterOutputsBySignature(Map<Node, Set<Node>> signatures) {
        Graph similarityGraph = new EdgeListGraph();

        for (Node output : this.outputs) {
            similarityGraph.addNode(output);
        }

        for (int i = 0; i < this.outputs.size(); i++) {
            for (int j = i + 1; j < this.outputs.size(); j++) {
                Node a = this.outputs.get(i);
                Node b = this.outputs.get(j);

                double similarity = jaccard(signatures.get(a), signatures.get(b));

                if (similarity >= this.signatureSimilarityThreshold) {
                    similarityGraph.addUndirectedEdge(a, b);
                }
            }
        }

        return GraphUtils.connectedComponents(similarityGraph);
    }

    private Set<Node> summarizeParentSet(Set<Node> outputCluster, Map<Node, Set<Node>> signatures) {
        Map<Node, Integer> counts = new HashMap<>();

        for (Node output : outputCluster) {
            for (Node input : signatures.get(output)) {
                counts.put(input, counts.getOrDefault(input, 0) + 1);
            }
        }

        Set<Node> parentSet = new HashSet<>();
        int clusterSize = outputCluster.size();

        for (Node input : this.inputs) {
            int count = counts.getOrDefault(input, 0);
            double frequency = count / (double) clusterSize;

            if (frequency >= this.parentFrequencyThreshold) {
                parentSet.add(input);
            }
        }

        return parentSet;
    }

    private boolean isCoherentCluster(Set<Node> outputs, Set<Node> parents) {
        List<Node> list = new ArrayList<>(outputs);

        int totalPairs = 0;
        int dependentPairs = 0;

        try {
            for (int i = 0; i < list.size(); i++) {
                for (int j = i + 1; j < list.size(); j++) {
                    totalPairs++;

                    if (!this.test.checkIndependence(list.get(i), list.get(j), parents).isIndependent()) {
                        dependentPairs++;
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed while checking cluster coherence.", e);
        }

        if (totalPairs == 0) {
            return false;
        }

        double fraction = dependentPairs / (double) totalPairs;
        return fraction >= this.clusterCoherenceThreshold;
    }

    private List<LatentCandidate> mergeRedundantCandidates(List<LatentCandidate> candidates) {
        candidates = new ArrayList<>(candidates);

        boolean changed = true;

        while (changed) {
            changed = false;

            outer:
            for (int i = 0; i < candidates.size(); i++) {
                for (int j = i + 1; j < candidates.size(); j++) {
                    LatentCandidate a = candidates.get(i);
                    LatentCandidate b = candidates.get(j);

                    if (jaccard(a.parents, b.parents) >= 0.8 && jaccard(a.children, b.children) >= 0.8) {
                        Set<Node> mergedParents = new HashSet<>(a.parents);
                        mergedParents.addAll(b.parents);

                        Set<Node> mergedChildren = new HashSet<>(a.children);
                        mergedChildren.addAll(b.children);

                        candidates.remove(j);
                        candidates.remove(i);
                        candidates.add(new LatentCandidate(mergedParents, mergedChildren));
                        changed = true;
                        break outer;
                    }
                }
            }
        }

        return candidates;
    }

    private Graph buildLatentGraph(List<LatentCandidate> candidates) {
        Graph graph = new EdgeListGraph();

        for (Node input : this.inputs) {
            graph.addNode(input);
        }

        for (Node output : this.outputs) {
            graph.addNode(output);
        }

        Map<LatentCandidate, Node> latentMap = new HashMap<>();

        for (LatentCandidate candidate : candidates) {
            Node latent = createLatentNode();
            graph.addNode(latent);
            latentMap.put(candidate, latent);

            for (Node parent : candidate.parents) {
                graph.addDirectedEdge(parent, latent);
            }

            for (Node child : candidate.children) {
                graph.addDirectedEdge(latent, child);
            }
        }

        for (LatentCandidate a : candidates) {
            for (LatentCandidate b : candidates) {
                if (a == b) {
                    continue;
                }

                if (b.parents.containsAll(a.parents) && !a.parents.equals(b.parents)) {
                    Node la = latentMap.get(a);
                    Node lb = latentMap.get(b);

                    if (!graph.isAdjacentTo(la, lb)) {
                        graph.addDirectedEdge(la, lb);
                    }
                }
            }
        }

        return graph;
    }

    private Node createLatentNode() {
        GraphNode latent = new GraphNode("L" + this.latentIndex++);
        latent.setNodeType(NodeType.LATENT);
        return latent;
    }

    private double jaccard(Set<Node> a, Set<Node> b) {
        if (a.isEmpty() && b.isEmpty()) {
            return 1.0;
        }

        Set<Node> intersection = new HashSet<>(a);
        intersection.retainAll(b);

        Set<Node> union = new HashSet<>(a);
        union.addAll(b);

        return intersection.size() / (double) union.size();
    }

    private static final class LatentCandidate {
        private final Set<Node> parents;
        private final Set<Node> children;

        private LatentCandidate(Set<Node> parents, Set<Node> children) {
            this.parents = new HashSet<>(parents);
            this.children = new HashSet<>(children);
        }
    }

    private void pruneIndirectMeasuredParents(Graph graph) {
        for (Node latent : new ArrayList<>(graph.getNodes())) {
            if (latent.getNodeType() != NodeType.LATENT) {
                continue;
            }

            Set<Node> measuredParents = getMeasuredParents(latent, graph);
            Set<Node> measuredChildren = getMeasuredChildren(latent, graph);

            if (measuredChildren.isEmpty()) {
                continue;
            }

            for (Node candidateParent : new ArrayList<>(measuredParents)) {
                Set<Node> conditioningSet = new LinkedHashSet<>();

                // Other measured parents of this latent.
                for (Node parent : measuredParents) {
                    if (!parent.equals(candidateParent)) {
                        conditioningSet.add(parent);
                    }
                }

                // Measured children of latent parents of this latent.
                conditioningSet.addAll(getMeasuredChildrenOfLatentParents(latent, graph));

                // Do not condition on the target children themselves.
                conditioningSet.removeAll(measuredChildren);

                boolean independentOfAllChildren = true;

                try {
                    for (Node child : measuredChildren) {
                        if (!this.test.checkIndependence(candidateParent, child, conditioningSet).isIndependent()) {
                            independentOfAllChildren = false;
                            break;
                        }
                    }
                } catch (Exception e) {
                    throw new RuntimeException("Failed while pruning indirect measured parents.", e);
                }

                if (independentOfAllChildren) {
                    graph.removeEdge(candidateParent, latent);
                }
            }
        }
    }

    private Set<Node> getMeasuredChildrenOfLatentParents(Node latent, Graph graph) {
        Set<Node> measuredChildren = new LinkedHashSet<>();

        for (Node parent : graph.getParents(latent)) {
            if (parent.getNodeType() == NodeType.LATENT) {
                measuredChildren.addAll(getMeasuredChildren(parent, graph));
            }
        }

        return measuredChildren;
    }

    /**
     * Returns the measured parents of the given node in the supplied graph.
     *
     * <p>Only non-latent parents are returned.</p>
     *
     * @param node the node whose measured parents are requested
     * @param graph the graph containing the node
     * @return the measured parents of the node
     */
    private Set<Node> getMeasuredParents(Node node, Graph graph) {
        Set<Node> measuredParents = new LinkedHashSet<>();

        for (Node parent : graph.getParents(node)) {
            if (parent.getNodeType() != NodeType.LATENT) {
                measuredParents.add(parent);
            }
        }

        return measuredParents;
    }

    /**
     * Returns the measured children of the given node in the supplied graph.
     *
     * <p>Only non-latent children are returned.</p>
     *
     * @param node the node whose measured children are requested
     * @param graph the graph containing the node
     * @return the measured children of the node
     */
    private Set<Node> getMeasuredChildren(Node node, Graph graph) {
        Set<Node> measuredChildren = new LinkedHashSet<>();

        for (Node child : graph.getChildren(node)) {
            if (child.getNodeType() != NodeType.LATENT) {
                measuredChildren.add(child);
            }
        }

        return measuredChildren;
    }

    /**
     * Removes measured-parent edges into a latent when that measured parent already feeds an
     * upstream latent parent and does not add explanatory power for the downstream latent's
     * measured children once the upstream latent's indicators are conditioned on.
     *
     * <p>This is a targeted cleanup for the common failure mode:
     * x -> U -> L, together with an extra spurious edge x -> L.</p>
     *
     * @param graph the graph to refine
     */
    private void pruneInheritedMeasuredParents(Graph graph) {
        for (Node downstreamLatent : new ArrayList<>(graph.getNodes())) {
            if (downstreamLatent.getNodeType() != NodeType.LATENT) {
                continue;
            }

            Set<Node> downstreamMeasuredChildren = getMeasuredChildren(downstreamLatent, graph);

            if (downstreamMeasuredChildren.isEmpty()) {
                continue;
            }

            Set<Node> downstreamMeasuredParents = getMeasuredParents(downstreamLatent, graph);

            for (Node upstreamLatent : graph.getParents(downstreamLatent)) {
                if (upstreamLatent.getNodeType() != NodeType.LATENT) {
                    continue;
                }

                Set<Node> upstreamMeasuredParents = getMeasuredParents(upstreamLatent, graph);
                Set<Node> upstreamMeasuredChildren = getMeasuredChildren(upstreamLatent, graph);

                for (Node candidateParent : new ArrayList<>(downstreamMeasuredParents)) {
                    if (!upstreamMeasuredParents.contains(candidateParent)) {
                        continue;
                    }

                    Set<Node> conditioningSet = new LinkedHashSet<>();

                    // Other measured parents of the downstream latent.
                    for (Node parent : downstreamMeasuredParents) {
                        if (!parent.equals(candidateParent)) {
                            conditioningSet.add(parent);
                        }
                    }

                    // Indicators of the upstream latent.
                    conditioningSet.addAll(upstreamMeasuredChildren);

                    // Never condition on the response variable itself.
                    conditioningSet.remove(candidateParent);

                    boolean independentOfAllChildren = true;

                    try {
                        for (Node child : downstreamMeasuredChildren) {
                            Set<Node> cond = new LinkedHashSet<>(conditioningSet);
                            cond.remove(child);

                            if (!this.test.checkIndependence(candidateParent, child, cond).isIndependent()) {
                                independentOfAllChildren = false;
                                break;
                            }
                        }
                    } catch (Exception e) {
                        throw new RuntimeException("Failed while pruning inherited measured parents.", e);
                    }

                    if (independentOfAllChildren) {
                        graph.removeEdge(candidateParent, downstreamLatent);
                    }
                }
            }
        }
    }

    /**
     * Removes latent variables that no longer have both measured parents and measured children.
     *
     * @param graph the graph to modify
     */
    private void removeDegenerateLatents(Graph graph) {
        for (Node node : new ArrayList<>(graph.getNodes())) {
            if (node.getNodeType() != NodeType.LATENT) {
                continue;
            }

            Set<Node> measuredParents = getMeasuredParents(node, graph);
            Set<Node> measuredChildren = getMeasuredChildren(node, graph);

            if (measuredParents.isEmpty() || measuredChildren.isEmpty()) {
                graph.removeNode(node);
            }
        }
    }

    /**
     * Removes measured-parent edges that are inherited through an already discovered
     * latent-to-latent path.
     *
     * <p>If x is a measured parent of a latent ancestor U of L, and x is also a measured
     * parent of L, then the edge x -> L is removed. This treats x -> L as an inherited
     * effect of the path x -> U -> ... -> L rather than as a separate direct parent edge.</p>
     *
     * @param graph the graph to refine
     */
    private void pruneInheritedMeasuredParentsStructurally(Graph graph) {
        for (Node latent : new ArrayList<>(graph.getNodes())) {
            if (latent.getNodeType() != NodeType.LATENT) {
                continue;
            }

            Set<Node> measuredParents = getMeasuredParents(latent, graph);
            Set<Node> ancestorLatents = getLatentAncestors(latent, graph);

            if (ancestorLatents.isEmpty()) {
                continue;
            }

            Set<Node> inheritedMeasuredParents = new LinkedHashSet<>();
            for (Node ancestorLatent : ancestorLatents) {
                inheritedMeasuredParents.addAll(getMeasuredParents(ancestorLatent, graph));
            }

            for (Node parent : new ArrayList<>(measuredParents)) {
                if (inheritedMeasuredParents.contains(parent)) {
                    graph.removeEdge(parent, latent);
                }
            }
        }
    }

    /**
     * Returns all latent ancestors of the given latent node.
     *
     * @param latent the latent node
     * @param graph the graph containing the node
     * @return the set of latent ancestors
     */
    private Set<Node> getLatentAncestors(Node latent, Graph graph) {
        Set<Node> ancestors = new LinkedHashSet<>();
        Deque<Node> stack = new ArrayDeque<>();

        for (Node parent : graph.getParents(latent)) {
            if (parent.getNodeType() == NodeType.LATENT) {
                stack.push(parent);
            }
        }

        while (!stack.isEmpty()) {
            Node current = stack.pop();

            if (!ancestors.add(current)) {
                continue;
            }

            for (Node parent : graph.getParents(current)) {
                if (parent.getNodeType() == NodeType.LATENT) {
                    stack.push(parent);
                }
            }
        }

        return ancestors;
    }
}