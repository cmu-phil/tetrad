package edu.cmu.tetrad.util;

import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.EdgeTypeProbability;
import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.pitt.dbmi.algo.resampling.ResamplingEdgeEnsemble;
import static edu.pitt.dbmi.algo.resampling.ResamplingEdgeEnsemble.Highest;
import static edu.pitt.dbmi.algo.resampling.ResamplingEdgeEnsemble.Majority;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A utility for computing frequency probabilities.
 *
 * Jan 29, 2023 3:28:26 PM
 *
 * @author Kevin V. Bui (kvb2univpitt@gmail.com)
 */
public final class GraphTools {

    private GraphTools() {
    }

    /**
     * Collect all the edges in the graph as undirected edges.
     *
     * @param graphs list of graphs
     * @return a set of undirected edges
     */
    private static Set<Edge> getUndirectedEdges(List<Graph> graphs) {
        Set<Edge> edges = new HashSet();
        graphs.forEach(graph -> {
            graph.getEdges().forEach(edge -> {
                edges.add(new Edge(edge.getNode1(), edge.getNode2(), Endpoint.NULL, Endpoint.NULL));
            });
        });

        return edges;
    }

    /**
     * Get the type of edge.
     *
     * @param node1 node
     * @param node2 node
     * @param edge a graph edge containing the node node1 and node node2
     * @return the edge-type
     */
    private static EdgeTypeProbability.EdgeType getEdgeType(Node node1, Node node2, Edge edge) {
        Endpoint end1 = edge.getProximalEndpoint(node1);
        Endpoint end2 = edge.getProximalEndpoint(node2);

        if (end1 == Endpoint.TAIL && end2 == Endpoint.ARROW) {
            return EdgeTypeProbability.EdgeType.ta;
        } else if (end1 == Endpoint.ARROW && end2 == Endpoint.TAIL) {
            return EdgeTypeProbability.EdgeType.at;
        } else if (end1 == Endpoint.CIRCLE && end2 == Endpoint.ARROW) {
            return EdgeTypeProbability.EdgeType.ca;
        } else if (end1 == Endpoint.ARROW && end2 == Endpoint.CIRCLE) {
            return EdgeTypeProbability.EdgeType.ac;
        } else if (end1 == Endpoint.CIRCLE && end2 == Endpoint.CIRCLE) {
            return EdgeTypeProbability.EdgeType.cc;
        } else if (end1 == Endpoint.ARROW && end2 == Endpoint.ARROW) {
            return EdgeTypeProbability.EdgeType.aa;
        } else if (end1 == Endpoint.TAIL && end2 == Endpoint.TAIL) {
            return EdgeTypeProbability.EdgeType.tt;
        } else {
            return EdgeTypeProbability.EdgeType.nil;
        }
    }

    /**
     * Get a list of probabilities for each edge type for the given nodes.
     *
     * @param node1 node
     * @param node2 node
     * @param graphs list of graphs
     * @return a list of edge-type probabilities
     */
    private static List<EdgeTypeProbability> getEdgeTypeProbabilities(Node node1, Node node2, List<Graph> graphs) {
        List<EdgeTypeProbability> edgeTypeProbabilities = new LinkedList<>();

        // frequency counts
        int noEdgeCounts = 0;
        Map<Edge, Integer> edgeTypeCounts = new HashMap<>();
        for (Graph graph : graphs) {
            if (graph == null) {
                continue;
            }

            Edge edge = graph.getEdge(node1, node2);
            if (edge != null) {
                Integer edgeCounts = edgeTypeCounts.get(edge);
                edgeCounts = (edgeCounts == null) ? 1 : edgeCounts + 1;

                edgeTypeCounts.put(edge, edgeCounts);
            } else {
                noEdgeCounts++;
            }
        }

        for (Edge edge : edgeTypeCounts.keySet()) {
            double probability = (double) edgeTypeCounts.get(edge) / graphs.size();
            EdgeTypeProbability.EdgeType edgeType = getEdgeType(node1, node2, edge);

            edgeTypeProbabilities.add(new EdgeTypeProbability(edgeType, edge.getProperties(), probability));
        }
        if (noEdgeCounts < graphs.size()) {
            edgeTypeProbabilities.add(new EdgeTypeProbability(EdgeTypeProbability.EdgeType.nil, (double) noEdgeCounts / graphs.size()));
        }

        return edgeTypeProbabilities;
    }

    private static EdgeTypeProbability getHighestEdgeTypeProbability(List<EdgeTypeProbability> edgeTypeProbabilities) {
        EdgeTypeProbability highestEdgeTypeProb = null;

        if (!(edgeTypeProbabilities == null || edgeTypeProbabilities.isEmpty())) {
            double maxEdgeProb = 0;
            for (EdgeTypeProbability etp : edgeTypeProbabilities) {
                EdgeTypeProbability.EdgeType edgeType = etp.getEdgeType();
                double prob = etp.getProbability();
                if (edgeType != EdgeTypeProbability.EdgeType.nil) {
                    if (prob > maxEdgeProb) {
                        highestEdgeTypeProb = etp;
                        maxEdgeProb = prob;
                    }
                }
            }
        }

        return highestEdgeTypeProb;
    }

    private static EdgeTypeProbability getHighestEdgeTypeProbability(List<EdgeTypeProbability> edgeTypeProbabilities, ResamplingEdgeEnsemble edgeEnsemble) {
        EdgeTypeProbability highestEdgeTypeProb = null;

        if (!(edgeTypeProbabilities == null || edgeTypeProbabilities.isEmpty())) {
            double maxEdgeProb = 0;
            double noEdgeProb = 0;
            for (EdgeTypeProbability etp : edgeTypeProbabilities) {
                EdgeTypeProbability.EdgeType edgeType = etp.getEdgeType();
                double prob = etp.getProbability();
                if (edgeType == EdgeTypeProbability.EdgeType.nil) {
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
            }
        }

        return highestEdgeTypeProb;
    }

    private static Edge createEdge(EdgeTypeProbability edgeTypeProbability, Node n1, Node n2) {
        if (edgeTypeProbability == null) {
            return null;
        }

        switch (edgeTypeProbability.getEdgeType()) {
            case ta:
                return new Edge(n1, n2, Endpoint.TAIL, Endpoint.ARROW);
            case at:
                return new Edge(n1, n2, Endpoint.ARROW, Endpoint.TAIL);
            case ca:
                return new Edge(n1, n2, Endpoint.CIRCLE, Endpoint.ARROW);
            case ac:
                return new Edge(n1, n2, Endpoint.ARROW, Endpoint.CIRCLE);
            case cc:
                return new Edge(n1, n2, Endpoint.CIRCLE, Endpoint.CIRCLE);
            case aa:
                return new Edge(n1, n2, Endpoint.ARROW, Endpoint.ARROW);
            case tt:
                return new Edge(n1, n2, Endpoint.TAIL, Endpoint.TAIL);
            default:
                return null;
        }
    }

    private static Graph computeEdgeProbabilities(Graph graph) {
        for (Edge edge : graph.getEdges()) {
            List<EdgeTypeProbability> edgeTypeProbs = edge.getEdgeTypeProbabilities();
            if (!(edgeTypeProbs == null || edgeTypeProbs.isEmpty())) {
                double prob = 0;
                for (EdgeTypeProbability typeProbability : edgeTypeProbs) {
                    if (typeProbability.getEdgeType() != EdgeTypeProbability.EdgeType.nil) {
                        prob += typeProbability.getProbability();
                    }
                }
                edge.setProbability(prob);
            }
        }

        return graph;
    }

    public static Graph createHighEdgeProbabilityGraph(List<Graph> graphs) {
        graphs = addPagColorings(graphs);
        if (graphs.isEmpty()) {
            return new EdgeListGraph();
        }

        List<Node> nodes = graphs.get(0).getNodes();
        Collections.sort(nodes);

        Graph graph = new EdgeListGraph(nodes);
        for (Edge e : getUndirectedEdges(graphs)) {
            Node n1 = e.getNode1();
            Node n2 = e.getNode2();

            List<EdgeTypeProbability> edgeTypeProbabilities = getEdgeTypeProbabilities(n1, n2, graphs);
            EdgeTypeProbability highestEdgeTypeProb = getHighestEdgeTypeProbability(edgeTypeProbabilities);
            Edge edge = createEdge(highestEdgeTypeProb, n1, n2);
            if (edge != null) {
                for (EdgeTypeProbability etp : edgeTypeProbabilities) {
                    edge.addEdgeTypeProbability(etp);
                }
                for (Edge.Property property : highestEdgeTypeProb.getProperties()) {
                    edge.addProperty(property);
                }

                graph.addEdge(edge);
            }
        }

        graph = computeEdgeProbabilities(graph);

        return graph;
    }

    public static Graph createHighEdgeProbabilityGraph(List<Graph> graphs, ResamplingEdgeEnsemble edgeEnsemble) {
        graphs = addPagColorings(graphs);
        if (graphs.isEmpty()) {
            return new EdgeListGraph();
        }

        List<Node> nodes = graphs.get(0).getNodes();
        Collections.sort(nodes);

        Graph graph = new EdgeListGraph(nodes);
        for (Edge e : getUndirectedEdges(graphs)) {
            Node n1 = e.getNode1();
            Node n2 = e.getNode2();

            List<EdgeTypeProbability> edgeTypeProbabilities = getEdgeTypeProbabilities(n1, n2, graphs);

            EdgeTypeProbability highestEdgeTypeProb = getHighestEdgeTypeProbability(edgeTypeProbabilities, edgeEnsemble);
            Edge edge = createEdge(highestEdgeTypeProb, n1, n2);
            if (edge != null) {
                for (EdgeTypeProbability etp : edgeTypeProbabilities) {
                    edge.addEdgeTypeProbability(etp);
                }

                for (Edge.Property property : highestEdgeTypeProb.getProperties()) {
                    edge.addProperty(property);
                }

                graph.addEdge(edge);
            }
        }

        graph = computeEdgeProbabilities(graph);

        return graph;
    }

    private static List<Graph> addPagColorings(List<Graph> graphs) {
        if (graphs == null) {
            return Collections.EMPTY_LIST;
        }

        return graphs.stream()
                .filter(graph -> graph != null)
                .map(graph -> {
                    GraphUtils.addPagColoring(graph);
                    return graph;
                })
                .collect(Collectors.toList());
    }

}
