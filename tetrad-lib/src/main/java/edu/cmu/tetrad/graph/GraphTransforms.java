package edu.cmu.tetrad.graph;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.search.RecursiveDiscriminatingPathRule;
import edu.cmu.tetrad.search.utils.*;
import edu.cmu.tetrad.util.CombinationGenerator;
import edu.cmu.tetrad.util.PagCache;
import edu.cmu.tetrad.util.RandomUtil;
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
        return dagFromCpdag(graph, null, true, false);
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
        return dagFromCpdag(graph, null, true, verbose);
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
        return dagFromCpdag(graph, null, meekPreventCycles, verbose);
    }

    /**
     * <p>dagFromCpdag.</p>
     *
     * @param graph     a {@link edu.cmu.tetrad.graph.Graph}
     * @param knowledge a {@link edu.cmu.tetrad.data.Knowledge}
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public static Graph dagFromCpdag(Graph graph, Knowledge knowledge) {
        return dagFromCpdag(graph, knowledge, true, false);
    }

    /**
     * Returns a random DAG from the given CPDAG. If the given CPDAG is not a PDAG, returns null.
     *
     * @param cpdag             the CPDAG
     * @param knowledge         the knowledge
     * @param meekPreventCycles whether to prevent cycles using the Meek rules by orienting additional arbitrary
     *                          unshielded colliders in the graph.
     * @param verbose           whether to print verbose output.
     * @return a DAG from the given CPDAG. If the given CPDAG is not a PDAG, returns null.
     */
    public static Graph dagFromCpdag(Graph cpdag, Knowledge knowledge, boolean meekPreventCycles, boolean verbose) {
        Graph dag = new EdgeListGraph(cpdag);
        transformCpdagIntoDag(dag, knowledge, meekPreventCycles, verbose);
        return dag;
    }

    /**
     * Transforms a completed partially directed acyclic graph (CPDAG) into a directed acyclic graph (DAG) by orienting
     * the undirected edges in the CPDAG.
     *
     * @param graph             The original graph from which the CPDAG was derived.
     * @param knowledge         The knowledge available to check if a potential DAG violates any constraints.
     * @param meekPreventCycles Whether to prevent cycles using the Meek rules by orienting additional arbitrary
     *                          unshielded colliders in the graph.
     * @param verbose           Whether to print verbose output.
     */
    public static void transformCpdagIntoDag(Graph graph, Knowledge knowledge, boolean meekPreventCycles, boolean verbose) {
        List<Edge> undirectedEdges = new ArrayList<>();

        for (Edge edge : graph.getEdges()) {
            if (Edges.isUndirectedEdge(edge)) {
                undirectedEdges.add(edge);
            }
        }

        undirectedEdges.sort((e1, e2) -> {
            String s1 = e1.getNode1().getName() + e1.getNode2().getName();
            String s2 = e2.getNode1().getName() + e2.getNode2().getName();
            return s1.compareTo(s2);
        });


        MeekRules rules = new MeekRules();
        rules.setMeekPreventCycles(meekPreventCycles);
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
                    if (!graph.paths().isAncestorOf(y, x)) {
                        direct(x, y, graph);
                    } else if (!graph.paths().isAncestorOf(x, y)) {
                        direct(y, x, graph);
                    } else {
                        if (x.getName().compareTo(y.getName()) < 0) {
                            direct(x, y, graph);
                        } else {
                            direct(y, x, graph);
                        }
                    }
                    rules.orientImplied(graph);
                    continue NEXT;
                }

            }

            break;
        }
    }

    /**
     * Picks a random Maximal Ancestral Graph (MAG) from the given Partial Ancestral Graph (PAG) by randomly orienting
     * the circle endpoints as either tail or arrow and then applying the final FCI orient algorithm after each change.
     * The PAG graph type is not checked.
     *
     * @param pag The partially ancestral pag to transform.
     * @return The maximally ancestral pag obtained from the PAG.
     */
    public static Graph magFromPag(Graph pag) {
        Graph mag = new EdgeListGraph(pag);
        transormPagIntoRandomMag(mag);
        return mag;
    }

    /**
     * Transforms a partially ancestral graph (PAG) into a maximally ancestral graph (MAG) by randomly orienting the
     * circle endpoints as either tail or arrow and then applying the final FCI orient algorithm after each change.
     *
     * @param pag The partially ancestral graph to transform.
     */
    public static void transormPagIntoRandomMag(Graph pag) {
        for (Edge e : pag.getEdges()) pag.addEdge(new Edge(e));

        List<NodePair> nodePairs = new ArrayList<>();

        for (Edge edge : pag.getEdges()) {
            if (!pag.isAdjacentTo(edge.getNode1(), edge.getNode2())) continue;
            nodePairs.add(new NodePair(edge.getNode1(), edge.getNode2()));
            nodePairs.add(new NodePair(edge.getNode2(), edge.getNode1()));
        }

        Collections.shuffle(nodePairs);

        for (NodePair edge : new ArrayList<>(nodePairs)) {
            if (pag.getEndpoint(edge.getFirst(), edge.getSecond()).equals(Endpoint.CIRCLE)) {
                double d = RandomUtil.getInstance().nextDouble();

                if (d < 0.5) {
                    pag.setEndpoint(edge.getFirst(), edge.getSecond(), Endpoint.TAIL);
                } else {
                    pag.setEndpoint(edge.getFirst(), edge.getSecond(), Endpoint.ARROW);
                }

                FciOrient fciOrient = new FciOrient(RecursiveDiscriminatingPathRule.defaultConfiguration(pag, new Knowledge()));
                fciOrient.finalOrientation(pag);
            }
        }
    }

    /**
     * Transforms a partial ancestral graph (PAG) into a maximal ancestral graph (MAG) using Zhang's 2008 Theorem 2.
     *
     * @param pag The partially ancestral graph to transform.
     * @return The maximally ancestral graph obtained from the PAG.
     */
    public static Graph zhangMagFromPag(Graph pag) {
        List<Node> nodes = pag.getNodes();

        Graph pcafci = new EdgeListGraph(pag);

        // pcafcic is the graph with only the circle-circle edges
        Graph pcafcic = new EdgeListGraph(pag.getNodes());

        for (Edge e : pcafci.getEdges()) {
            if (Edges.isNondirectedEdge(e)) {
                pcafcic.addUndirectedEdge(e.getNode1(), e.getNode2());
            }
        }

        pcafcic = GraphTransforms.dagFromCpdag(pcafcic, new Knowledge(), true, false);

        for (Edge e : pcafcic.getEdges()) {
            pcafci.removeEdge(e.getNode1(), e.getNode2());
            pcafci.addEdge(e);
        }

        Graph H = new EdgeListGraph(pcafci);

        for (Node x : nodes) {
            for (Node y : nodes) {
                if (x.equals(y)) {
                    continue;
                }

                if (!H.isAdjacentTo(x, y)) {
                    continue;
                }

                if (H.getEndpoint(y, x) == Endpoint.CIRCLE && H.getEndpoint(x, y) == Endpoint.ARROW) {
                    H.setEndpoint(y, x, Endpoint.TAIL);
                }

                if (H.getEndpoint(y, x) == Endpoint.TAIL && H.getEndpoint(x, y) == Endpoint.CIRCLE) {
                    H.setEndpoint(x, y, Endpoint.ARROW);
                }
            }
        }

        return H;
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

        for (Edge edge : graph.getEdges()) {
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
