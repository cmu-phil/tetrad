package edu.cmu.tetrad.graph;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.search.utils.DagInCpcagIterator;
import edu.cmu.tetrad.search.utils.DagToPag;
import edu.cmu.tetrad.search.utils.GraphSearchUtils;
import edu.cmu.tetrad.search.utils.MeekRules;
import edu.cmu.tetrad.util.CombinationGenerator;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Transformations that transform one graph into another.
 *
 * @author josephramsey
 */
public class GraphTransforms {
    public static Graph dagFromCpdag(Graph graph) {
        return dagFromCpdag(graph, null);
    }

    /**
     * Returns a DAG from the given CPDAG. If the given CPDAG is not a PDAG, returns null.
     * @param graph the CPDAG
     * @param knowledge the knowledge
     * @return a DAG from the given CPDAG. If the given CPDAG is not a PDAG, returns null.
     */
    public static Graph dagFromCpdag(Graph graph, Knowledge knowledge) {
        Graph dag = new EdgeListGraph(graph);

        MeekRules rules = new MeekRules();

        if (knowledge != null) {
            rules.setKnowledge(knowledge);
        }

        rules.setRevertToUnshieldedColliders(false);

        NEXT:
        while (true) {
            for (Edge edge : dag.getEdges()) {
                Node x = edge.getNode1();
                Node y = edge.getNode2();

                if (Edges.isUndirectedEdge(edge) && !graph.paths().isAncestorOf(y, x)) {
                    direct(x, y, dag);
                    rules.orientImplied(dag);
                    continue NEXT;
                }
            }

            break;
        }

        return dag;
    }

    // Zhang 2008 Theorem 2
    public static Graph pagToMag(Graph pag) {
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
     */
    public static List<Graph> generateCpdagDags(Graph cpdag, boolean orientBidirectedEdges) {
        if (orientBidirectedEdges) {
            cpdag = GraphUtils.removeBidirectedOrientations(cpdag);
        }

        return getDagsInCpdagMeek(cpdag, new Knowledge());
    }

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

    public static Graph cpdagForDag(Graph dag) {
        Graph cpdag = new EdgeListGraph(dag);
        MeekRules rules = new MeekRules();
        rules.setRevertToUnshieldedColliders(true);
        rules.orientImplied(cpdag);
        return cpdag;
    }

    @NotNull
    public static Graph dagToPag(Graph trueGraph) {
        return new DagToPag(trueGraph).convert();
    }

    private static void direct(Node a, Node c, Graph graph) {
        Edge before = graph.getEdge(a, c);
        Edge after = Edges.directedEdge(a, c);
        graph.removeEdge(before);
        graph.addEdge(after);
    }
}
