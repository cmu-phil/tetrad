package edu.cmu.tetrad.graph;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.search.utils.*;
import edu.cmu.tetrad.util.CombinationGenerator;
import edu.cmu.tetrad.util.RandomUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
     * <p>dagFromCpdag.</p>
     *
     * @param graph a {@link edu.cmu.tetrad.graph.Graph} object
     * @return a {@link edu.cmu.tetrad.graph.Graph} object
     */
    public static Graph dagFromCpdag(Graph graph) {
        return dagFromCpdag(graph, null);
    }

    /**
     * Returns a random DAG from the given CPDAG. If the given CPDAG is not a PDAG, returns null.
     *
     * @param cpdag     the CPDAG
     * @param knowledge the knowledge
     * @return a DAG from the given CPDAG. If the given CPDAG is not a PDAG, returns null.
     */
    public static Graph dagFromCpdag(Graph cpdag, Knowledge knowledge) {
        Graph dag = new EdgeListGraph(cpdag);
        transformCpdagIntoRandomDag(dag, knowledge);
        return dag;
    }

    /**
     * Transforms a completed partially directed acyclic graph (CPDAG) into a random directed acyclic graph (DAG) by
     * randomly orienting the undirected edges in the CPDAG in shuffled order.
     *
     * @param graph     The original graph from which the CPDAG was derived.
     * @param knowledge The knowledge available to check if a potential DAG violates any constraints.
     */
    public static void transformCpdagIntoRandomDag(Graph graph, Knowledge knowledge) {
        List<Edge> undirectedEdges = new ArrayList<>();

        for (Edge edge : graph.getEdges()) {
            if (Edges.isUndirectedEdge(edge)) {
                undirectedEdges.add(edge);
            }
        }

        Collections.shuffle(undirectedEdges);

        MeekRules rules = new MeekRules();

        if (knowledge != null) {
            rules.setKnowledge(knowledge);
        }

        rules.setRevertToUnshieldedColliders(false);

        NEXT:
        while (true) {
            for (Edge edge : undirectedEdges) {
                Node x = edge.getNode1();
                Node y = edge.getNode2();

                if (!Edges.isUndirectedEdge(graph.getEdge(x, y))) {
                    continue;
                }

                if (Edges.isUndirectedEdge(edge) && !graph.paths().isAncestorOf(y, x)) {
                    double d = RandomUtil.getInstance().nextDouble();

                    if (d < 0.5) {
                        direct(x, y, graph);
                    } else {
                        direct(y, x, graph);
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

                FciOrient orient = new FciOrient(new DagSepsets(pag));
                orient.zhangFinalOrientation(pag);
            }
        }
    }

    /**
     * Transforms a partially ancestral graph (PAG) into a maximally ancestral graph (MAG) using Zhang's 2008 Theorem
     * 2.
     *
     * @param pag The partially ancestral graph to transform.
     * @return The maximally ancestral graph obtained from the PAG.
     */
    public static Graph zhangMagFromPag(Graph pag) {
        Graph mag = new EdgeListGraph(pag.getNodes());
        for (Edge e : pag.getEdges()) mag.addEdge(new Edge(e));

        List<Node> nodes = mag.getNodes();

        Graph pcafci = new EdgeListGraph(nodes);

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = 0; j < nodes.size(); j++) {
                if (i == j) continue;

                Node x = nodes.get(i);
                Node y = nodes.get(j);

                if (mag.getEndpoint(y, x) == Endpoint.CIRCLE && mag.getEndpoint(x, y) == Endpoint.ARROW) {
                    mag.setEndpoint(y, x, Endpoint.TAIL);
                }

                if (mag.getEndpoint(y, x) == Endpoint.TAIL && mag.getEndpoint(x, y) == Endpoint.CIRCLE) {
                    mag.setEndpoint(x, y, Endpoint.ARROW);
                }

                if (mag.getEndpoint(y, x) == Endpoint.CIRCLE && mag.getEndpoint(x, y) == Endpoint.CIRCLE) {
                    pcafci.addEdge(mag.getEdge(x, y));
                }
            }
        }

        for (Edge e : pcafci.getEdges()) {
            e.setEndpoint1(Endpoint.TAIL);
            e.setEndpoint2(Endpoint.TAIL);
        }

        W:
        while (true) {
            for (Edge e : pcafci.getEdges()) {
                if (Edges.isUndirectedEdge(e)) {
                    Node x = e.getNode1();
                    Node y = e.getNode2();

                    pcafci.setEndpoint(y, x, Endpoint.TAIL);
                    pcafci.setEndpoint(x, y, Endpoint.ARROW);

                    MeekRules meekRules = new MeekRules();
                    meekRules.setRevertToUnshieldedColliders(false);
                    meekRules.orientImplied(pcafci);

                    continue W;
                }
            }

            break;
        }

        for (Edge e : pcafci.getEdges()) {
            mag.removeEdge(e.getNode1(), e.getNode2());
            mag.addEdge(e);
        }

        return mag;
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
        rules.orientImplied(cpdag);
        return cpdag;
    }

    /**
     * Converts a Directed Acyclic Graph (DAG) to a Partial Ancestral Graph (PAG) using the DagToPag algorithm.
     *
     * @param trueGraph The input DAG to be converted.
     * @return The resulting PAG obtained from the input DAG.
     */
    @NotNull
    public static Graph dagToPag(Graph trueGraph) {
        return new DagToPag(trueGraph).convert();
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
}
