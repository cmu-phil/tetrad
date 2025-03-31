package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;

import java.util.*;
import java.util.stream.Collectors;

public class DM {

    private final IndependenceTest test;
    private final List<Node> inputs = new ArrayList<>();
    private final List<Node> outputs = new ArrayList<>();

    private int latentIndex = 1;
    private Knowledge knowledge = new Knowledge();

    public DM(IndependenceTest test) {
        this.test = test;
    }

    public Graph search() {

        // Step 1: Identify inputs and outputs.
        Graph initialGraph = runPcDepthZero();
        classifyVariables(initialGraph);

        // Step 2: Build sets of outputs for each input.
        Map<Set<Node>, List<Node>> latentOutputClusters = clusterOutputs();

        // Step 3: Introduce latent nodes and build graph.
        Graph latentGraph = buildLatentStructure(latentOutputClusters);

        // Step 4: Refine latent-latent edges using conditional xindependence.
        refineLatentEdges(latentGraph);

        // Step 5: Final refinement using PC algorithm (full depth).
        finalRefinement(latentGraph);

        return latentGraph;
    }

    private Graph runPcDepthZero() {
        try {
            Pc pc = new Pc(test);
            pc.setDepth(0);
            pc.setKnowledge(knowledge);
            return pc.search();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private void classifyVariables(Graph pattern) {
        for (Node node : pattern.getNodes()) {
            int indegree = pattern.getIndegree(node);
            int outdegree = pattern.getOutdegree(node);

            if (indegree == 0 && outdegree > 0) {
                inputs.add(node);
            } else if (indegree > 0) {
                outputs.add(node);
            }
            // Variables with no edges can be handled separately if needed.
        }
    }

    private Map<Set<Node>, List<Node>> clusterOutputs() {
        Map<Set<Node>, List<Node>> clusters = new HashMap<>();

        for (Node output : outputs) {
            Set<Node> associatedInputs = getAssociatedInputs(output);

            clusters.computeIfAbsent(associatedInputs, k -> new ArrayList<>()).add(output);
        }

        return clusters;
    }

    private Set<Node> getAssociatedInputs(Node output) {
        Set<Node> associatedInputs = new HashSet<>();

        try {
            for (Node input : inputs) {
                if (test.checkIndependence(input, output).isDependent()) {
                    associatedInputs.add(input);
                }
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        return associatedInputs;
    }

    private Graph buildLatentStructure(Map<Set<Node>, List<Node>> clusters) {
        Graph graph = new EdgeListGraph();

        // Add input and output nodes.
        for (Node input : inputs) graph.addNode(input);
        for (Node output : outputs) graph.addNode(output);

        Map<Set<Node>, Node> latentNodes = new HashMap<>();

        // Create latents and connect them.
        for (Set<Node> inputSet : clusters.keySet()) {
            Node latent = createLatentNode(inputSet);
            latentNodes.put(inputSet, latent);
            graph.addNode(latent);

            for (Node input : inputSet) graph.addDirectedEdge(input, latent);
            for (Node output : clusters.get(inputSet)) graph.addDirectedEdge(latent, output);
        }

        // Connect latent-latent based on subset relations.
        for (Set<Node> setA : clusters.keySet()) {
            for (Set<Node> setB : clusters.keySet()) {
                if (!setA.equals(setB) && setA.containsAll(setB)) {
                    graph.addDirectedEdge(latentNodes.get(setB), latentNodes.get(setA));
                }
            }
        }

        return graph;
    }

    private Node createLatentNode(Set<Node> inputSet) {
        String latentName = "L" + latentIndex++;
        Node latent = new GraphNode(latentName);
        latent.setNodeType(NodeType.LATENT);
        return latent;
    }

    private boolean canRemoveLatentEdge(Node latentA, Node latentB, Graph graph) {
        Set<Node> inputsToA = getInputNodes(latentA, graph);
        Set<Node> inputsToB = getInputNodes(latentB, graph);

        Set<Node> combinedInputs = new HashSet<>();
        combinedInputs.addAll(inputsToA);
        combinedInputs.addAll(inputsToB);

        Set<Node> outputsA = getOutputNodes(latentA, graph);
        Set<Node> outputsB = getOutputNodes(latentB, graph);

        // Now you test conditional independence of outputsA and outputsB
        // given combinedInputs. If independent, remove edge.
        return conditionalIndependent(removeLatents(outputsA), removeLatents(outputsB), removeLatents(combinedInputs));
    }

    private Set<Node> removeLatents(Set<Node> set) {
        return set.stream()
                .filter(n -> n.getNodeType() != NodeType.LATENT)
                .collect(Collectors.toSet());
    }



    // You'd implement these helper methods clearly:
    private Set<Node> getInputNodes(Node latent, Graph graph) {
        return new HashSet<>(graph.getParents(latent));
    }

    private Set<Node> getOutputNodes(Node latent, Graph graph) {
        return new HashSet<>(graph.getChildren(latent));
    }

    private boolean conditionalIndependent(Set<Node> setA, Set<Node> setB, Set<Node> givenSet) {
        Set<Node> cleanSetA = removeLatents(setA);
        Set<Node> cleanSetB = removeLatents(setB);
        Set<Node> cleanGivenSet = removeLatents(givenSet);

        try {
            for (Node a : cleanSetA) {
                for (Node b : cleanSetB) {
                    if (!test.checkIndependence(a, b, cleanGivenSet).isIndependent()) {
                        return false; // dependent pair found
                    }
                }
            }
            return true; // all pairs independent
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }



    private void refineLatentEdges(Graph graph) {
        List<Edge> latentEdges = new ArrayList<>(graph.getEdges());

        for (Edge edge : latentEdges) {
            Node latentA = edge.getNode1();
            Node latentB = edge.getNode2();

            if (latentA.getNodeType() == NodeType.LATENT && latentB.getNodeType() == NodeType.LATENT) {
                if (canRemoveLatentEdge(latentA, latentB, graph)) {
                    graph.removeEdge(edge);
                }
            }
        }
    }


    private void finalRefinement(Graph graph) {
        try {
            Pc pc = new Pc(test);
            pc.setDepth(-1);
            pc.setKnowledge(knowledge);
            Graph fullPattern = pc.search();

            for (Node output : outputs) {
                if (fullPattern.getAdjacentNodes(output).stream().noneMatch(inputs::contains)) {
                    // Remove latent-output edge and instead use PC output edges among outputs.
                    for (Node parent : new ArrayList<>(graph.getParents(output))) {
                        if (parent.getNodeType() == NodeType.LATENT) {
                            graph.removeEdge(parent, output);
                        }
                    }

                    // Add edges among outputs according to fullPattern.
                    for (Node neighbor : fullPattern.getAdjacentNodes(output)) {
                        if (outputs.contains(neighbor)) {
                            if (!graph.isAdjacentTo(output, neighbor)) {
                                graph.addUndirectedEdge(output, neighbor);
                            }
                        }
                    }
                }
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }


    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = knowledge;
    }
}