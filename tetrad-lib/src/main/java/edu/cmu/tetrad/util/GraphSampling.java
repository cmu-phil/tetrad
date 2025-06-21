package edu.cmu.tetrad.util;

import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.graph.Edge.Property;
import edu.cmu.tetrad.graph.EdgeTypeProbability.EdgeType;
import edu.pitt.dbmi.algo.resampling.ResamplingEdgeEnsemble;

import java.util.*;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

/**
 * A utility for computing frequency probabilities.
 * <p>
 * Jan 29, 2023 3:28:26 PM
 *
 * @author Kevin V. Bui (kvb2univpitt@gmail.com)
 * @version $Id: $Id
 */
public final class GraphSampling {

    private GraphSampling() {
    }

    /**
     * Create a graph from the given graph that contains no null edges.
     *
     * @param graph the given graph
     * @return graph that contains no null edges
     */
    public static Graph createGraphWithoutNullEdges(Graph graph) {
        Graph myGraph = new EdgeListGraph(graph.getNodes());
        graph.getEdges().stream()
                .filter(edge -> !(edge.getEndpoint1() == Endpoint.NULL || edge.getEndpoint2() == Endpoint.NULL))
                .forEach(myGraph::addEdge);

        return myGraph;
    }

    /**
     * Create a graph for displaying and print out.
     *
     * @param graph    a {@link Graph} object
     * @param ensemble a {@link ResamplingEdgeEnsemble} object
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public static Graph createDisplayGraph(Graph graph, ResamplingEdgeEnsemble ensemble) {
        EdgeListGraph ensembleGraph = new EdgeListGraph(graph.getNodes());

        for (Edge edge : graph.getEdges()) {
            List<EdgeTypeProbability> edgeTypeProbabilities = edge.getEdgeTypeProbabilities();
            if (edgeTypeProbabilities == null || edgeTypeProbabilities.isEmpty()) {
                ensembleGraph.addEdge(edge);
            } else {
                EdgeTypeProbability highestEdgeTypeProbability = getHighestEdgeTypeProbability(edgeTypeProbabilities, ensemble);
                Edge highestProbEdge = createEdge(highestEdgeTypeProbability, edge.getNode1(), edge.getNode2());
                if (highestProbEdge != null) {
                    // copy over edge-type probabilities
                    edgeTypeProbabilities.forEach(highestProbEdge::addEdgeTypeProbability);

                    // copy over edge-type properties
                    highestEdgeTypeProbability.getProperties().forEach(highestProbEdge::addProperty);

                    ensembleGraph.addEdge(highestProbEdge);
                }
            }
        }

        setEdgeProbabilitiesOfNonNullEdges(ensembleGraph);
        return ensembleGraph;
    }

//    /**
//     * <p>createGraphWithHighProbabilityEdges.</p>
//     *
//     * @param graphs   a {@link java.util.List} object
//     * @param ensemble a {@link edu.pitt.dbmi.algo.resampling.ResamplingEdgeEnsemble} object
//     * @return a {@link edu.cmu.tetrad.graph.Graph} object
//     */
//    public static Graph createGraphWithHighProbabilityEdges(List<Graph> graphs, ResamplingEdgeEnsemble ensemble) {
//        Graph graph = createGraphWithHighProbabilityEdges(graphs);
//
//        return createDisplayGraph(graph, ensemble);
//    }

    /**
     * Combine all the edges from the list of graphs onto one graph with the edge type that has the highest frequency
     * probability.
     *
     * @param graphs list of graphs
     * @return graph containing edges with edge type of the highest probability
     */
    public static Graph createGraphWithHighProbabilityEdges(List<Graph> graphs) {
        // filter out null graphs and add PAG edge specializstion markup
        graphs = graphs.stream()
                .filter(Objects::nonNull)
                .map(GraphSampling::addEdgeSpecializationMarkups)
                .collect(Collectors.toList());

        if (graphs.isEmpty()) {
            return new EdgeListGraph();
        }

        // create new graph
        Graph graph = createNewGraph(graphs.getFirst().getNodes());
        for (NodePair nodePair : getEdgeNodePairs(graphs)) {
            String node1 = nodePair.getNode1();
            String node2 = nodePair.getNode2();

            List<EdgeTypeProbability> edgeTypeProbabilities = getEdgeTypeProbabilities(node1, node2, graphs);
            EdgeTypeProbability highestEdgeTypeProbability = getHighestEdgeTypeProbability(edgeTypeProbabilities);

            if (graph.getNode(node1) == null || graph.getNode(node2) == null) {
                continue;
            }

            Edge highestProbEdge = createEdge(highestEdgeTypeProbability, graph.getNode(node1), graph.getNode(node2));
            if (highestProbEdge != null) {
                // copy over edge-type probabilities
                if (node1.equals(highestProbEdge.getNode1().getName()) && node2.equals(highestProbEdge.getNode2().getName())) {
                    edgeTypeProbabilities.forEach(highestProbEdge::addEdgeTypeProbability);
                } else {
                    // reverse the edge type if the nodes of the edge does not line up with the input nodes
                    edgeTypeProbabilities.forEach(etp -> {
                        etp.setEdgeType(getReversed(etp.getEdgeType()));
                        highestProbEdge.addEdgeTypeProbability(etp);
                    });
                }

                graph.addEdge(highestProbEdge);
            }
        }

        setEdgeProbabilitiesOfNonNullEdges(graph);
        return graph;
    }

    private static void setEdgeProbabilitiesOfNonNullEdges(Graph graph) {
        graph.getEdges().forEach(edge -> {
            List<EdgeTypeProbability> etps = edge.getEdgeTypeProbabilities();
            if (!(etps != null && etps.isEmpty())) {
                // add up all the probabilities of non-null edges
                double probability = edge.getEdgeTypeProbabilities().stream()
                        .filter(etp -> etp.getEdgeType() != EdgeTypeProbability.EdgeType.nil)
                        .mapToDouble(EdgeTypeProbability::getProbability)
                        .sum();

                edge.setProbability(probability);
            }
        });
    }

    private static Edge createEdge(EdgeTypeProbability edgeTypeProbability, Node n1, Node n2) {
        if (edgeTypeProbability == null) {
            return null;
        }

        return switch (edgeTypeProbability.getEdgeType()) {
            case ta -> new Edge(n1, n2, Endpoint.TAIL, Endpoint.ARROW, false);
            case at -> new Edge(n1, n2, Endpoint.ARROW, Endpoint.TAIL, false);
            case ca -> new Edge(n1, n2, Endpoint.CIRCLE, Endpoint.ARROW, false);
            case ac -> new Edge(n1, n2, Endpoint.ARROW, Endpoint.CIRCLE, false);
            case cc -> new Edge(n1, n2, Endpoint.CIRCLE, Endpoint.CIRCLE, false);
            case aa -> new Edge(n1, n2, Endpoint.ARROW, Endpoint.ARROW, false);
            case tt -> new Edge(n1, n2, Endpoint.TAIL, Endpoint.TAIL, false);
            default -> new Edge(n1, n2, Endpoint.NULL, Endpoint.NULL, false);
        };
    }

    private static EdgeTypeProbability getHighestEdgeTypeProbability(List<EdgeTypeProbability> edgeTypeProbabilities, ResamplingEdgeEnsemble edgeEnsemble) {
        EdgeTypeProbability highestEdgeTypeProb = null;

        if (!(edgeTypeProbabilities == null || edgeTypeProbabilities.isEmpty())) {
            double maxEdgeProb = 0;
            double noEdgeProb = 0;
            for (EdgeTypeProbability etp : edgeTypeProbabilities) {
                EdgeType edgeType = etp.getEdgeType();
                double prob = etp.getProbability();
                if (edgeType == EdgeType.nil) {
                    noEdgeProb = prob;
                } else {
                    if (prob > maxEdgeProb) {
                        highestEdgeTypeProb = etp;
                        maxEdgeProb = prob;
                    }
                }
            }

            switch (edgeEnsemble) {
                case Highest:
                    if (noEdgeProb > maxEdgeProb) {
                        highestEdgeTypeProb = null;
                    }
                    break;
                case Majority:
                    if (noEdgeProb > maxEdgeProb || maxEdgeProb < .5) {
                        highestEdgeTypeProb = null;
                    }
                    break;
                case Threshold:
                    double threshold = Preferences.userRoot().getDouble("edge.ensemble.threshold", .5);

                    if (noEdgeProb > maxEdgeProb || maxEdgeProb < threshold) {
                        highestEdgeTypeProb = null;
                    }
                    break;
            }
        }

        return highestEdgeTypeProb;
    }

    private static EdgeTypeProbability getHighestEdgeTypeProbability(List<EdgeTypeProbability> edgeTypeProbabilities) {
        if (edgeTypeProbabilities == null || edgeTypeProbabilities.isEmpty()) {
            return null;
        }

        // sort by edge probabilities in descending order
        EdgeTypeProbability[] etps = edgeTypeProbabilities.toArray(EdgeTypeProbability[]::new);
        Arrays.sort(etps, (etp1, etp2) -> {
            return Double.compare(etp2.getProbability(), etp1.getProbability());
        });

        return etps[0];
    }

    private static List<EdgeTypeProbability> getEdgeTypeProbabilities(String node1, String node2, List<Graph> graphs) {
        List<EdgeTypeProbability> edgeTypeProbabilities = new LinkedList<>();

        // frequency counts
        int numOfNullEdges = 0;
        Map<EdgeType, Integer> edgeTypeCounts = new HashMap<>();
        Map<EdgeType, Edge> edgeTypeEdges = new HashMap<>();
        for (Graph graph : graphs) {
            Node n1 = graph.getNode(node1);
            Node n2 = graph.getNode(node2);

            Edge edge = graph.getEdge(n1, n2);
            if (edge == null) {
                numOfNullEdges++;
            } else {
                EdgeType edgeType = getEdgeType(edge, n1, n2);

                // save the edge for a given edge type
                edgeTypeEdges.put(edgeType, edge);

                // tally the counts for a given edge type
                Integer edgeCounts = edgeTypeCounts.get(edgeType);
                edgeCounts = (edgeCounts == null) ? 1 : edgeCounts + 1;
                edgeTypeCounts.put(edgeType, edgeCounts);
            }
        }

        // compute probabilities
        for (EdgeType edgeType : edgeTypeCounts.keySet()) {
            Edge edge = edgeTypeEdges.get(edgeType);
            List<Property> properties = edge.getProperties();
            double probability = ((double) edgeTypeCounts.get(edgeType)) / graphs.size();

            edgeTypeProbabilities.add(new EdgeTypeProbability(edgeType, properties, probability));
        }
        if ((numOfNullEdges > 0) && (numOfNullEdges < graphs.size())) {
            edgeTypeProbabilities.add(new EdgeTypeProbability(EdgeTypeProbability.EdgeType.nil, ((double) numOfNullEdges) / graphs.size()));
        }

        // sort by edge probabilities in descending order
        EdgeTypeProbability[] etps = edgeTypeProbabilities.toArray(EdgeTypeProbability[]::new);
        Arrays.sort(etps, (etp1, etp2) -> Double.compare(etp2.getProbability(), etp1.getProbability()));

        return Arrays.asList(etps);
    }

    public static EdgeType getReversed(EdgeType edgeType) {
        return switch (edgeType) {
            case ac -> EdgeType.ca;
            case at -> EdgeType.ta;
            case ca -> EdgeType.ac;
            case ta -> EdgeType.at;
            default -> edgeType;
        };
    }

    private static EdgeType getEdgeType(Edge edge, Node node1, Node node2) {
        Endpoint node1Endpoint = edge.getProximalEndpoint(node1);
        Endpoint node2Endpoint = edge.getProximalEndpoint(node2);

        if (node1Endpoint == Endpoint.TAIL && node2Endpoint == Endpoint.ARROW) {
            return EdgeType.ta;
        } else if (node1Endpoint == Endpoint.ARROW && node2Endpoint == Endpoint.TAIL) {
            return EdgeType.at;
        } else if (node1Endpoint == Endpoint.CIRCLE && node2Endpoint == Endpoint.ARROW) {
            return EdgeType.ca;
        } else if (node1Endpoint == Endpoint.ARROW && node2Endpoint == Endpoint.CIRCLE) {
            return EdgeType.ac;
        } else if (node1Endpoint == Endpoint.CIRCLE && node2Endpoint == Endpoint.CIRCLE) {
            return EdgeType.cc;
        } else if (node1Endpoint == Endpoint.ARROW && node2Endpoint == Endpoint.ARROW) {
            return EdgeType.aa;
        } else if (node1Endpoint == Endpoint.TAIL && node2Endpoint == Endpoint.TAIL) {
            return EdgeType.tt;
        } else {
            return EdgeType.nil;
        }
    }

    /**
     * <p>getEdgeNodePairs.</p>
     *
     * @param graphs a {@link java.util.List} object
     * @return a {@link java.util.Set} object
     */
    private static Set<NodePair> getEdgeNodePairs(List<Graph> graphs) {
        HashSet<NodePair> nodePairs = new HashSet<>();

        graphs.forEach(graph -> {
            graph.getEdges().forEach(edge -> {
                String node1Name = edge.getNode1().getName();
                String node2Name = edge.getNode2().getName();
                nodePairs.add(new NodePair(node1Name, node2Name));
            });
        });

        return new TreeSet<>(nodePairs);
    }

    private static Graph createNewGraph(List<Node> graphNodes) {
        Node[] nodes = graphNodes.toArray(Node[]::new);
        Arrays.sort(nodes);

        return new EdgeListGraph(Arrays.asList(nodes));
    }

    private static Graph addEdgeSpecializationMarkups(Graph graph) {
        GraphUtils.addEdgeSpecializationMarkup(graph);

        return graph;
    }

    private static class NodePair implements Comparable<NodePair> {

        private final String node1;
        private final String node2;

        public NodePair(String node1, String node2) {
            this.node1 = node1;
            this.node2 = node2;
        }

        @Override
        public int compareTo(NodePair other) {
            int firstNodeComparison = this.node1.compareTo(other.node1);

            return (firstNodeComparison == 0)
                    ? this.node2.compareTo(other.node2)
                    : firstNodeComparison;
        }

        @Override
        public int hashCode() {
            return this.node1.hashCode() + this.node2.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }

            final NodePair other = (NodePair) obj;
            boolean isDirectlyEqual = (this.node1.equals(other.node1) && this.node2.equals(other.node2));
            boolean isIndirectlyEqual = (this.node1.equals(other.node2) && this.node2.equals(other.node1));

            return isDirectlyEqual || isIndirectlyEqual;
        }

        @Override
        public String toString() {
            return String.format("(%s, %s)", node1, node2);
        }

        public String getNode1() {
            return node1;
        }

        public String getNode2() {
            return node2;
        }

    }

}
