package edu.cmu.tetrad.graph;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.search.utils.DagInCpcagIterator;
import edu.cmu.tetrad.search.utils.DagToPag;
import edu.cmu.tetrad.search.utils.MeekRules;
import edu.cmu.tetrad.util.CombinationGenerator;
import edu.cmu.tetrad.util.PagCache;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Transformations that transform one graph into another.
 *
 * @author josephramsey
 * @version $Id: $Id
 */
public class GraphTransforms {

    /**
     * Private constructor to prevent instantiation.
     */
    private GraphTransforms() {
    }

    /**
     * Converts a completed partially directed acyclic graph (CPDAG) into a directed acyclic graph (DAG). If the given
     * CPDAG is not a PDAG (Partially Directed Acyclic Graph), returns null.
     *
     * @param graph the CPDAG to be converted into a DAG
     * @return a directed acyclic graph (DAG) obtained from the given CPDAG. If the given CPDAG is not a PDAG, returns
     * null.
     */
    public static Graph dagFromCpdag(Graph graph) {
        return dagFromCpdag(graph, null, false);
    }

    /**
     * Converts a completed partially directed acyclic graph (CPDAG) into a directed acyclic graph (DAG). If the given
     * CPDAG is not a PDAG (Partially Directed Acyclic Graph), returns null.
     *
     * @param graph   the CPDAG to be converted into a DAG
     * @param verbose whether to print verbose output
     * @return a directed acyclic graph (DAG) obtained from the given CPDAG. If the given CPDAG is not a PDAG, returns
     * null.
     */
    public static Graph dagFromCpdag(Graph graph, boolean verbose) {
        return dagFromCpdag(graph, null, verbose);
    }

    /**
     * Converts a completed partially directed acyclic graph (CPDAG) into a directed acyclic graph (DAG). If the given
     * CPDAG is not a PDAG (Partially Directed Acyclic Graph), returns null.
     *
     * @param graph             the CPDAG to be converted into a DAG
     * @param meekPreventCycles whether to prevent cycles using the Meek rules by orienting additional arbitrary
     *                          unshielded colliders in the graph
     * @param verbose           whether to print verbose output
     * @return a directed acyclic graph (DAG) obtained from the given CPDAG. If the given CPDAG is not a PDAG, returns
     * null.
     */
    public static Graph dagFromCpdag(Graph graph, boolean meekPreventCycles, boolean verbose) {
        return dagFromCpdag(graph, null, verbose);
    }

    /**
     * <p>dagFromCpdag.</p>
     *
     * @param graph     a {@link edu.cmu.tetrad.graph.Graph}
     * @param knowledge a {@link edu.cmu.tetrad.data.Knowledge}
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public static Graph dagFromCpdag(Graph graph, Knowledge knowledge) {
        return dagFromCpdag(graph, knowledge, false);
    }

    /**
     * Returns a random DAG from the given CPDAG. If the given CPDAG is not a PDAG, returns null.
     *
     * @param cpdag     the CPDAG
     * @param knowledge the knowledge
     * @param verbose   whether to print verbose output.
     * @return a DAG from the given CPDAG. If the given CPDAG is not a PDAG, returns null.
     */
    public static Graph dagFromCpdag(Graph cpdag, Knowledge knowledge, boolean verbose) {
        Graph dag = new EdgeListGraph(cpdag);
        transformCpdagIntoDag(dag, knowledge, verbose);
        return dag;
    }

    /**
     * Transforms a completed partially directed acyclic graph (CPDAG) into a directed acyclic graph (DAG) by orienting
     * the undirected edges in the CPDAG.
     *
     * @param graph     The original graph from which the CPDAG was derived.
     * @param knowledge The knowledge available to check if a potential DAG violates any constraints.
     * @param verbose   Whether to print verbose output.
     */
    public static void transformCpdagIntoDag(Graph graph, Knowledge knowledge, boolean verbose) {
        Graph _graph = new EdgeListGraph(graph);

        List<Edge> undirectedEdges = new ArrayList<>();

        for (Edge edge : graph.getEdges()) {
            if (Edges.isUndirectedEdge(edge)) {
                undirectedEdges.add(edge);
            }
        }

        List<Node> order = graph.getNodes();
        order.sort((node1, node2) -> {
            if (graph.paths().isAncestorOf(node1, node2)) {
                return -1;
            } else if (graph.paths().isAncestorOf(node2, node1)) {
                return 1;
            } else {
                return 0;
            }
        });

        MeekRules rules = new MeekRules();
        rules.setMeekPreventCycles(true);
        rules.setVerbose(verbose);

        if (knowledge != null) {
            rules.setKnowledge(knowledge);
        }

        rules.setRevertToUnshieldedColliders(false);

        NEXT:
        while (true) {
            for (Edge edge : undirectedEdges) {
                Node x = edge.getNode1();
                Node y = edge.getNode2();

                if (Edges.isUndirectedEdge(graph.getEdge(x, y))) {
                    if (order.indexOf(x) < order.indexOf(y)) {
                        direct(x, y, graph);
                        rules.orientImplied(graph);
                        continue NEXT;
                    } else {
                        direct(y, x, graph);
                        rules.orientImplied(graph);
                        continue NEXT;
                    }
                }
            }

            break;
        }
    }

    /**
     * Transforms a partial ancestral graph (PAG) into a maximal ancestral graph (MAG) using Zhang's 2008 Theorem 2.
     *
     * @param pag The partially ancestral graph to transform.
     * @return The maximally ancestral graph obtained from the PAG.
     */
    public static Graph zhangMagFromPag(Graph pag) {
        Graph pafci = new EdgeListGraph(pag);

        for (Edge e : pafci.getEdges()) {
            Node x = e.getNode1();
            Node y = e.getNode2();
            Endpoint endx = e.getEndpoint1();
            Endpoint endy = e.getEndpoint2();

            if (endx == Endpoint.CIRCLE && endy == Endpoint.ARROW) {
                pafci.removeEdge(e);
                pafci.addDirectedEdge(x, y);
            } else if (endx == Endpoint.ARROW && endy == Endpoint.CIRCLE) {
                pafci.removeEdge(e);
                pafci.addDirectedEdge(y, x);
            } else if (endx == Endpoint.TAIL && endy == Endpoint.CIRCLE) {
                pafci.removeEdge(e);
                pafci.addDirectedEdge(x, y);
            } else if (endx == Endpoint.CIRCLE && endy == Endpoint.TAIL) {
                pafci.removeEdge(e);
                pafci.addDirectedEdge(y, x);
            }
        }

        // pcafci is the graph with only the circle-circle edges
        Graph pcafci = new EdgeListGraph(pafci.getNodes());

        for (Edge e : pafci.getEdges()) {
            if (Edges.isNondirectedEdge(e)) {
                pcafci.addUndirectedEdge(e.getNode1(), e.getNode2());
            }
        }

        pcafci = GraphTransforms.dagFromCpdag(pcafci, new Knowledge(), false);

        for (Edge e : pcafci.getEdges()) {
            pafci.removeEdges(e.getNode1(), e.getNode2());
            pafci.addEdge(e);
        }

        return pafci;
    }

    /**
     * Generates the list of DAGs in the given cpdag.
     *
     * @param cpdag                 a {@link edu.cmu.tetrad.graph.Graph} object
     * @param orientBidirectedEdges a boolean
     * @return a {@link java.util.List} object
     */
    public static List<Graph> generateCpdagDags(Graph cpdag, boolean orientBidirectedEdges) {
        if (orientBidirectedEdges) {
            cpdag = GraphUtils.removeBidirectedOrientations(cpdag);
        }

        return getDagsInCpdagMeek(cpdag, new Knowledge());
    }

    /**
     * Retrieves a list of directed acyclic graphs (DAGs) within the given completed partially directed acyclic graph
     * (CPDAG) using the Meek rules.
     *
     * @param cpdag     The completed partially directed acyclic graph (CPDAG) from which to retrieve the DAGs.
     * @param knowledge The knowledge available to check if a potential DAG violates any constraints.
     * @return A {@link List} of {@link Graph} objects representing the DAGs within the CPDAG.
     */
    public static List<Graph> getDagsInCpdagMeek(Graph cpdag, Knowledge knowledge) {
        DagInCpcagIterator iterator = new DagInCpcagIterator(cpdag, knowledge);
        List<Graph> dags = new ArrayList<>();

        while (iterator.hasNext()) {
            Graph graph = iterator.next();

            try {
                if (knowledge.isViolatedBy(graph)) {
                    continue;
                }

                dags.add(graph);
            } catch (IllegalArgumentException e) {
                System.out.println("Found a non-DAG: " + graph);
            }
        }

        return dags;
    }

    /**
     * Returns a list of all possible graphs obtained by directing undirected edges in the given graph.
     *
     * @param skeleton the graph to transform
     * @return a list of all possible graphs obtained by directing undirected edges
     */
    public static List<Graph> getAllGraphsByDirectingUndirectedEdges(Graph skeleton) {
        List<Graph> graphs = new ArrayList<>();
        List<Edge> edges = new ArrayList<>(skeleton.getEdges());

        List<Integer> undirectedIndices = new ArrayList<>();

        for (int i = 0; i < edges.size(); i++) {
            if (Edges.isUndirectedEdge(edges.get(i))) {
                undirectedIndices.add(i);
            }
        }

        int[] dims = new int[undirectedIndices.size()];

        for (int i = 0; i < undirectedIndices.size(); i++) {
            dims[i] = 2;
        }

        CombinationGenerator gen = new CombinationGenerator(dims);
        int[] comb;

        while ((comb = gen.next()) != null) {
            Graph graph = new EdgeListGraph(skeleton.getNodes());

            for (Edge edge : edges) {
                if (!Edges.isUndirectedEdge(edge)) {
                    graph.addEdge(edge);
                }
            }

            for (int i = 0; i < undirectedIndices.size(); i++) {
                Edge edge = edges.get(undirectedIndices.get(i));
                Node node1 = edge.getNode1();
                Node node2 = edge.getNode2();

                if (comb[i] == 1) {
                    graph.addEdge(Edges.directedEdge(node1, node2));
                } else {
                    graph.addEdge(Edges.directedEdge(node2, node1));
                }
            }

            graphs.add(graph);
        }

        return graphs;
    }

    /**
     * Returns the completed partially directed acyclic graph (CPDAG) for a given directed acyclic graph (DAG).
     *
     * @param dag The input DAG.
     * @return The CPDAG resulting from applying Meek Rules to the input DAG.
     */
    public static Graph dagToCpdag(Graph dag) {
        Graph cpdag = new EdgeListGraph(dag);
        MeekRules rules = new MeekRules();
        rules.setRevertToUnshieldedColliders(true);
        rules.setVerbose(false);
        rules.orientImplied(cpdag);
        return cpdag;
    }

    /**
     * Converts a Directed Acyclic Graph (DAG) to a Partial Ancestral Graph (PAG) using the DagToPag algorithm.
     *
     * @param graph The input DAG to be converted.
     * @return The resulting PAG obtained from the input DAG.
     */
    @NotNull
    public static Graph dagToPag(Graph graph) {
        return PagCache.getInstance().getPag(graph);
//        return new DagToPag(graph).convert();
    }

    /**
     * Directs an edge between two nodes in a graph.
     *
     * @param a     the start node of the edge
     * @param c     the end node of the edge
     * @param graph the graph in which the edge needs to be directed
     */
    private static void direct(Node a, Node c, Graph graph) {
        Edge before = graph.getEdge(a, c);
        Edge after = Edges.directedEdge(a, c);
        graph.removeEdge(before);
        graph.addEdge(after);
    }

    /**
     * Converts a Directed Acyclic Graph (DAG) to a Maximal Ancestral Graph (MAG) by adding arrows to the edges.
     *
     * @param dag The input DAG to be converted.
     * @return The resulting MAG obtained from the input DAG.
     */
    public static @NotNull Graph dagToMag(Graph dag) {
        Map<Node, Set<Node>> ancestorMap = dag.paths().getDescendantsMap();
        Graph graph = DagToPag.calcAdjacencyGraph(dag);

        List<Node> allNodes = dag.getNodes();

        Set<Node> selection = new HashSet<>(allNodes.stream()
                .filter(node -> node.getNodeType() == NodeType.SELECTION).toList());

        graph.reorientAllWith(Endpoint.TAIL);

        Set<Edge> edges = graph.getEdges();
        List<Edge> _edges = new ArrayList<>(edges);
        Collections.sort(_edges);

        for (Edge edge : _edges) {
            Node x = edge.getNode1();
            Node y = edge.getNode2();

            // If not y ~~> x put an arrow at y. If not x ~~> y put an arrow at x.
            if (!ancestorMap.get(y).contains(x) && !dag.paths().isAncestorOfAnyZ(y, selection)) {
                graph.setEndpoint(x, y, Endpoint.ARROW);
            }

            if (!ancestorMap.get(x).contains(y) && !dag.paths().isAncestorOfAnyZ(x, selection)) {
                graph.setEndpoint(y, x, Endpoint.ARROW);
            }
        }

        return graph;
    }
}
