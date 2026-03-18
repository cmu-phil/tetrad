package edu.cmu.tetrad.search.mimic;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.IGraphSearch;
import edu.cmu.tetrad.search.Pc;
import edu.cmu.tetrad.search.test.IndependenceTest;

import java.util.*;

/**
 * A hybrid detect-mimic heuristic.
 *
 * <p>This version keeps the robust output clustering idea, but assigns measured
 * parents to latent clusters using an input-centered OUT(X) logic inspired by the
 * original DM construction. The goal is to avoid copying upstream inputs into
 * downstream latents merely because those downstream outputs are statistically
 * reachable from upstream inputs.</p>
 */
public class DmPcHybrid implements IGraphSearch {

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
     * Minimum proportion of a latent cluster's outputs that must appear in OUT(X)
     * in order for X to be considered as a candidate parent of that latent.
     */
    private double parentAssignmentThreshold = 0.75;

    /**
     * Minimum number of outputs required for a latent cluster.
     */
    private int minClusterSize = 2;

    /**
     * Constructs the search from an independence test.
     *
     * @param test the independence test
     */
    public DmPcHybrid(IndependenceTest test) {
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

        List<Set<Node>> filteredClusters = new ArrayList<>();
        for (Set<Node> cluster : outputClusters) {
            if (cluster.size() >= this.minClusterSize) {
                filteredClusters.add(new LinkedHashSet<>(cluster));
            }
        }

        // Sort clusters from largest to smallest so higher-level latents are considered first.
        filteredClusters.sort((a, b) -> Integer.compare(b.size(), a.size()));

        Map<Node, Set<Node>> outMap = computeInputOutSets(depth0Pattern, filteredClusters);
        List<LatentCandidate> candidates = assignParentsToClusters(filteredClusters, outMap);

        Graph graph = buildLatentGraph(candidates);
        removeDegenerateLatents(graph);

        return graph;
    }

    /**
     * Sets the background knowledge.
     *
     * @param knowledge the background knowledge
     */
    public void setKnowledge(Knowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException("Knowledge must not be null.");
        }

        this.knowledge = new Knowledge(knowledge);
    }

    /**
     * Sets the minimum output-signature similarity required to cluster outputs together.
     *
     * @param threshold the similarity threshold
     */
    public void setSignatureSimilarityThreshold(double threshold) {
        this.signatureSimilarityThreshold = threshold;
    }

    /**
     * Sets the minimum fraction of a latent cluster's outputs that must appear in OUT(X)
     * for X to be assigned to that latent.
     *
     * @param threshold the parent-assignment threshold
     */
    public void setParentAssignmentThreshold(double threshold) {
        this.parentAssignmentThreshold = threshold;
    }

    /**
     * Sets the minimum cluster size.
     *
     * @param minClusterSize the minimum number of outputs in a latent cluster
     */
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
            Set<Node> signature = new LinkedHashSet<>();

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

    /**
     * Computes OUT(X) for each input X, where OUT(X) is the set of clustered outputs
     * that are adjacent to X in the shallow pattern.
     *
     * @param depth0Pattern the shallow PC graph
     * @param clusters the output clusters
     * @return a map from each input to its OUT(X) set
     */
    private Map<Node, Set<Node>> computeInputOutSets(Graph depth0Pattern, List<Set<Node>> clusters) {
        Map<Node, Set<Node>> outMap = new LinkedHashMap<>();

        for (Node input : this.inputs) {
            Set<Node> outSet = new LinkedHashSet<>();

            for (Set<Node> cluster : clusters) {
                boolean touchesCluster = false;

                for (Node output : cluster) {
                    if (depth0Pattern.isAdjacentTo(input, output)) {
                        touchesCluster = true;
                        break;
                    }
                }

                if (touchesCluster) {
                    outSet.addAll(cluster);
                }
            }

            outMap.put(input, outSet);
        }

        return outMap;
    }

    /**
     * Assigns inputs to the best-fitting latent cluster using OUT(X).
     *
     * <p>An input is assigned to the largest cluster whose outputs are sufficiently
     * covered by OUT(X). This biases inputs toward the highest cluster in the latent
     * hierarchy instead of copying them into all downstream clusters they influence.</p>
     *
     * @param clusters the output clusters
     * @param outMap the OUT(X) sets for inputs
     * @return the latent candidates
     */
    private List<LatentCandidate> assignParentsToClusters(List<Set<Node>> clusters, Map<Node, Set<Node>> outMap) {
        List<LatentCandidate> candidates = new ArrayList<>();

        for (Set<Node> cluster : clusters) {
            Set<Node> parents = new LinkedHashSet<>();

            for (Node input : this.inputs) {
                Set<Node> outSet = outMap.get(input);

                if (outSet == null || outSet.isEmpty()) {
                    continue;
                }

                Set<Node> intersection = new LinkedHashSet<>(outSet);
                intersection.retainAll(cluster);

                double coverage = intersection.size() / (double) cluster.size();

                if (coverage >= this.parentAssignmentThreshold) {
                    boolean assignedToLargerCluster = false;

                    for (Set<Node> largerCluster : clusters) {
                        if (largerCluster == cluster) {
                            continue;
                        }

                        if (largerCluster.size() <= cluster.size()) {
                            continue;
                        }

                        if (!largerCluster.containsAll(cluster)) {
                            continue;
                        }

                        Set<Node> largerIntersection = new LinkedHashSet<>(outSet);
                        largerIntersection.retainAll(largerCluster);

                        double largerCoverage = largerIntersection.size() / (double) largerCluster.size();

                        if (largerCoverage >= this.parentAssignmentThreshold) {
                            assignedToLargerCluster = true;
                            break;
                        }
                    }

                    if (!assignedToLargerCluster) {
                        parents.add(input);
                    }
                }
            }

            if (!parents.isEmpty()) {
                candidates.add(new LatentCandidate(parents, cluster));
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

        Map<LatentCandidate, Node> latentMap = new LinkedHashMap<>();

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

                if (a.children.containsAll(b.children) && !a.children.equals(b.children)) {
                    Node latentA = latentMap.get(a);
                    Node latentB = latentMap.get(b);

                    if (!graph.isAdjacentTo(latentA, latentB)) {
                        graph.addDirectedEdge(latentA, latentB);
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

    private void removeDegenerateLatents(Graph graph) {
        for (Node node : new ArrayList<>(graph.getNodes())) {
            if (node.getNodeType() != NodeType.LATENT) {
                continue;
            }

            if (getMeasuredParents(node, graph).isEmpty() || getMeasuredChildren(node, graph).isEmpty()) {
                graph.removeNode(node);
            }
        }
    }

    private Set<Node> getMeasuredParents(Node node, Graph graph) {
        Set<Node> measuredParents = new LinkedHashSet<>();

        for (Node parent : graph.getParents(node)) {
            if (parent.getNodeType() != NodeType.LATENT) {
                measuredParents.add(parent);
            }
        }

        return measuredParents;
    }

    private Set<Node> getMeasuredChildren(Node node, Graph graph) {
        Set<Node> measuredChildren = new LinkedHashSet<>();

        for (Node child : graph.getChildren(node)) {
            if (child.getNodeType() != NodeType.LATENT) {
                measuredChildren.add(child);
            }
        }

        return measuredChildren;
    }

    private double jaccard(Set<Node> a, Set<Node> b) {
        if (a.isEmpty() && b.isEmpty()) {
            return 1.0;
        }

        Set<Node> intersection = new LinkedHashSet<>(a);
        intersection.retainAll(b);

        Set<Node> union = new LinkedHashSet<>(a);
        union.addAll(b);

        return intersection.size() / (double) union.size();
    }

    private static final class LatentCandidate {
        private final Set<Node> parents;
        private final Set<Node> children;

        private LatentCandidate(Set<Node> parents, Set<Node> children) {
            this.parents = new LinkedHashSet<>(parents);
            this.children = new LinkedHashSet<>(children);
        }
    }
}