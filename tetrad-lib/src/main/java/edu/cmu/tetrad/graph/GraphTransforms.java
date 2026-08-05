/// ////////////////////////////////////////////////////////////////////////////
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
/// ////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.graph;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.search.utils.DagInCpcagIterator;
import edu.cmu.tetrad.search.utils.MagToPag;
import edu.cmu.tetrad.search.utils.MeekRules;
import edu.cmu.tetrad.util.CombinationGenerator;
import edu.cmu.tetrad.util.PagCache;
import edu.cmu.tetrad.util.TetradLogger;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.*;

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
//        Graph _graph = new EdgeListGraph(graph);

        List<Edge> undirectedEdges = new ArrayList<>();

        for (Edge edge : graph.getEdges()) {
            if (Edges.isUndirectedEdge(edge)) {
                undirectedEdges.add(edge);
            }
        }

//        // This method failed on an example that was a valid CPDAG. jdramsey 2025-12-3
//        List<Node> order = graph.getNodes();
//        order.sort((node1, node2) -> {
//            if (graph.paths().isAncestorOf(node1, node2)) {
//                return -1;
//            } else if (graph.paths().isAncestorOf(node2, node1)) {
//                return 1;
//            } else {
//                return 0;
//            }
//        });

        // Peel the node with no remaining directed parents; when the directed part is
        // acyclic this is a topological order (Kahn's algorithm). If a cycle makes the
        // minimum parent count positive, peel the node with fewest remaining parents,
        // breaking the cycle deterministically instead of failing. Tie-break by name
        // for determinism. (Cf. the sort-based version, removed 2025-12: ancestry is a
        // partial order, and List.sort requires a total one -- TimSort throws
        // "Comparison method violates its general contract!" on inputs where it
        // detects the intransitivity, and silently produces non-topological orders on
        // inputs where it doesn't.)
        List<Node> nodes = graph.getNodes();

        Map<Node, Set<Node>> parents = new HashMap<>();
        Map<Node, Set<Node>> children = new HashMap<>();

        for (Node n : nodes) {
            parents.put(n, new HashSet<>());
            children.put(n, new HashSet<>());
        }

        for (Edge e : graph.getEdges()) {
            if (Edges.isDirectedEdge(e)) {
                Node tail = Edges.getDirectedEdgeTail(e);
                Node head = Edges.getDirectedEdgeHead(e);
                parents.get(head).add(tail);
                children.get(tail).add(head);
            }
        }

        Comparator<Node> pick = Comparator
                .comparingInt((Node n) -> parents.get(n).size())
                .thenComparing(Node::getName);

        List<Node> order = new ArrayList<>(nodes.size());
        Set<Node> remaining = new LinkedHashSet<>(nodes);

        while (!remaining.isEmpty()) {
            Node next = remaining.stream().min(pick).orElseThrow();
            order.add(next);
            remaining.remove(next);
            for (Node c : children.get(next)) {
                parents.get(c).remove(next);
            }
        }

        // Replacing with this method.
//        List<Node> order = graph.paths().getValidOrder(graph.getNodes(), true);

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

//    /**
//     * Transforms a partial ancestral graph (PAG) into a maximal ancestral graph (MAG) using Zhang's 2008 Theorem 2.
//     *
//     * @param pag The partially ancestral graph to transform.
//     * @return The maximally ancestral graph obtained from the PAG.
//     */
//    public static Graph zhangMagFromPag(Graph pag) {
//        Graph pafci = new EdgeListGraph(pag);
//
//        for (Edge e : pafci.getEdges()) {
//            Node x = e.getNode1();
//            Node y = e.getNode2();
//            Endpoint endx = e.getEndpoint1();
//            Endpoint endy = e.getEndpoint2();
//
//            if (endx == Endpoint.CIRCLE && endy == Endpoint.ARROW) {
//                pafci.removeEdge(e);
//                pafci.addDirectedEdge(x, y);
//            } else if (endx == Endpoint.ARROW && endy == Endpoint.CIRCLE) {
//                pafci.removeEdge(e);
//                pafci.addDirectedEdge(y, x);
//            } else if (endx == Endpoint.TAIL && endy == Endpoint.CIRCLE) {
//                pafci.removeEdge(e);
//                pafci.addDirectedEdge(x, y);
//            } else if (endx == Endpoint.CIRCLE && endy == Endpoint.TAIL) {
//                pafci.removeEdge(e);
//                pafci.addDirectedEdge(y, x);
//            }
//        }
//
//        // pcafci is the graph with only the circle-circle edges
//        Graph pcafci = new EdgeListGraph(pafci.getNodes());
//
//        for (Edge e : pafci.getEdges()) {
//            if (Edges.isNondirectedEdge(e)) {
//                pcafci.addUndirectedEdge(e.getNode1(), e.getNode2());
//            }
//        }
//
//        pcafci = GraphTransforms.dagFromCpdag(pcafci, new Knowledge(), false);
//
//        for (Edge e : pcafci.getEdges()) {
//            pafci.removeEdges(e.getNode1(), e.getNode2());
//            pafci.addEdge(e);
//        }
//
//        return pafci;
//    }

    /**
     * Transforms a partial ancestral graph (PAG) into a maximal ancestral graph (MAG) using Zhang's 2008 Theorem 2.
     *
     * @param pag The partially ancestral graph to transform.
     * @return The maximally ancestral graph obtained from the PAG.
     */
    public static Graph zhangMagFromPag(Graph pag) {
        UnorientedComponentAsUndirected result = getGetUnorientedComponentAsUndirected(pag);
        Graph pcafci;

        pcafci = GraphTransforms.dagFromCpdag(result.pcafci(), new Knowledge(), false);

        for (Edge e : pcafci.getEdges()) {
            result.pafci().removeEdges(e.getNode1(), e.getNode2());
            result.pafci().addEdge(e);
        }

        return result.pafci();
    }

    /**
     * Lazily enumerates the Zhang MAGs of a PAG: one MAG per consistent DAG orientation of the
     * PAG's unoriented component. Identical to {@link #zhangMagFromPag} except that
     * {@link ConsistentDagIterator} supplies EVERY no-unshielded-collider orientation of
     * {@code result.pcafci()} in place of the single {@code dagFromCpdag} extension. Each MAG is a
     * fresh copy of the component-stripped PAG with one orientation stamped in, so the shared
     * template is never mutated.
     *
     * @param pag the PAG to transform (assumed a valid, completed PAG).
     * @return a lazy iterable over the Zhang MAGs reachable from {@code pag}.
     */
    public static Iterable<Graph> zhangMagsFromPag(Graph pag) {
        UnorientedComponentAsUndirected result = getGetUnorientedComponentAsUndirected(pag);
        final Graph template = result.pafci();                  // stamp target; copied per MAG, never mutated
        final Iterator<Graph> dags = new ConsistentDagIterator(result.pcafci(), pag.getNodes(), false, true).iterator();

        return () -> new Iterator<>() {
            @Override
            public boolean hasNext() {
                return dags.hasNext();
            }

            @Override
            public Graph next() {
                Graph dag = dags.next();                        // one orientation of the component
                Graph mag = new EdgeListGraph(template);        // fresh; template stays the o-o PAG
                for (Edge e : dag.getEdges()) {
                    mag.removeEdges(e.getNode1(), e.getNode2());
                    mag.addEdge(e);
                }
                return mag;
            }
        };
    }

    /**
     * Returns a random Zhang MAG of a PAG. A uniformly shuffled seed order is passed through
     * {@code getValidOrder} to get a random valid causal order of the PAG's unoriented component;
     * that order orients the component into a consistent DAG; and, exactly as in
     * {@link #zhangMagFromPag}, those directed edges are stamped onto the component-stripped PAG.
     *
     * @param pag    the PAG to transform (assumed a valid, completed PAG).
     * @param random source of randomness (seed it for reproducibility).
     * @return a random Zhang MAG obtained from {@code pag}.
     */
    public static Graph randomZhangMagFromPag(Graph pag, Random random) {
        UnorientedComponentAsUndirected result = getGetUnorientedComponentAsUndirected(pag);
        Graph cpdag = result.pcafci();

        // A random valid causal order of the component.
        List<Node> order = new ArrayList<>(cpdag.getNodes());
        Collections.shuffle(order, random);
        order = cpdag.paths().getValidOrder(order, true);   // the getValidOrder you pasted

        // Orient the component by that order: parent = earlier in the order. The valid-sink clique
        // condition guarantees this introduces no unshielded collider, i.e. it's a consistent extension.
        Map<Node, Integer> pos = new HashMap<>();
        for (int i = 0; i < order.size(); i++) pos.put(order.get(i), i);

        for (Edge e : cpdag.getEdges()) {
            Node a = e.getNode1(), b = e.getNode2();
            Node parent = pos.get(a) < pos.get(b) ? a : b;
            Node child = (parent == a) ? b : a;
            result.pafci().removeEdges(a, b);
            result.pafci().addDirectedEdge(parent, child);
        }

        return result.pafci();
    }

    /**
     * Convenience overload with a fresh {@link Random}; use the seeded overload for reproducibility.
     *
     * @param pag The PAG to find a random Zhang MAG in.
     * @return this graph.
     */
    public static Graph randomZhangMagFromPag(Graph pag) {
        return randomZhangMagFromPag(pag, new Random());
    }

    private static @NotNull GraphTransforms.UnorientedComponentAsUndirected getGetUnorientedComponentAsUndirected(Graph pag) {
        Graph pafci = new EdgeListGraph(pag);

        for (Edge e : pafci.getEdges()) {
            Node x = e.getNode1();
            Node y = e.getNode2();
            Endpoint endx = e.getEndpoint1();
            Endpoint endy = e.getEndpoint2();

            // o-> : circle becomes tail, so x --> y
            if (endx == Endpoint.CIRCLE && endy == Endpoint.ARROW) {
                pafci.removeEdge(e);
                pafci.addDirectedEdge(x, y);
                // <-o : circle becomes tail, so y --> x
            } else if (endx == Endpoint.ARROW && endy == Endpoint.CIRCLE) {
                pafci.removeEdge(e);
                pafci.addDirectedEdge(y, x);
                // --o : circle becomes tail, so x --- y (undirected)
            } else if (endx == Endpoint.TAIL && endy == Endpoint.CIRCLE) {
                pafci.removeEdge(e);
                pafci.addUndirectedEdge(x, y);
                // o-- : circle becomes tail, so x --- y (undirected)
            } else if (endx == Endpoint.CIRCLE && endy == Endpoint.TAIL) {
                pafci.removeEdge(e);
                pafci.addUndirectedEdge(x, y);
            }
            // o-o, -->, <--, <-> : left as-is
        }

        // Collect all o-o edges (now undirected after circle replacement above,
        // but original o-o edges are still nondirected) into a CPDAG-like graph
        // and orient them via dagFromCpdag.
        Graph pcafci = new EdgeListGraph(pafci.getNodes());

        for (Edge e : pafci.getEdges()) {
            if (Edges.isNondirectedEdge(e)) {
                pcafci.addUndirectedEdge(e.getNode1(), e.getNode2());
            }
        }
        UnorientedComponentAsUndirected result = new UnorientedComponentAsUndirected(pafci, pcafci);
        return result;
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
     * Runs {@link #dagToPag} with a timeout. Returns {@code false} if the
     * check does not complete within {@code timeoutSeconds} seconds, treating a
     * timeout as a failed legality check (i.e. the surgery is reverted).
     *
     * @param graph                The input DAG to be converted.
     * @param excludeSelectionBias True to exclude selection bias, false otherwise.
     * @param timeoutSeconds       maximum seconds to wait
     * @return The resulting PAG obtained from the input DAG.
     * @throws RuntimeException if the check does not complete within {@code timeoutSeconds} seconds, treating
     *                          a timeout as a failed legality check (i.e. the surgery is reverted).
     */
    public static Graph dagToPag(Graph graph, boolean excludeSelectionBias, int timeoutSeconds) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Graph> future = executor.submit(
                () -> dagToPag(graph, new Knowledge(), excludeSelectionBias, 15));
        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            TetradLogger.getInstance().log("Timeout on PAG conversion from DAG.");
            throw new RuntimeException("Timeout waiting for pag to complete");
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted waiting for pag to complete");
        } catch (Exception e) {
            future.cancel(true);
            throw new RuntimeException("Exception waiting for pag to complete", e.getCause());
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * Converts a Directed Acyclic Graph (DAG) to a Partial Ancestral Graph (PAG) using the DagToPag algorithm.
     *
     * @param graph                The input DAG to be converted.
     * @param excludeSelectionBias True to exclude selection bias, false otherwise.
     * @return The resulting PAG obtained from the input DAG.
     */
    @NotNull
    public static Graph dagToPag(Graph graph, boolean excludeSelectionBias) {
        return dagToPag(graph, new Knowledge(), excludeSelectionBias, 15);
    }

    /**
     * Converts a Directed Acyclic Graph (DAG) to a Partial Ancestral Graph (PAG) using the provided DAG and knowledge.
     *
     * @param graph                The input Directed Acyclic Graph (DAG) to be converted.
     * @param knowledge            Background knowledge used to guide the conversion process.
     * @param excludeSelectionBias True to exclude selection bias, false otherwise.
     * @param recursiveDepth       Recursion depth to consider during PAG computation.
     * @return The resulting Partial Ancestral Graph (PAG) obtained from the input DAG and knowledge.
     * @throws IllegalStateException if a discriminating path cannot be found.
     */
    public static Graph dagToPag(Graph graph, Knowledge knowledge, boolean excludeSelectionBias, int recursiveDepth) {
        return PagCache.getInstance().getPag(graph, knowledge, excludeSelectionBias, recursiveDepth);
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
        Graph graph = calcAdjacencyGraph(dag);

        List<Node> allNodes = dag.getNodes();

        Set<Node> selection = new LinkedHashSet<>(allNodes.stream().filter(node -> node.getNodeType() == NodeType.SELECTION).toList());

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

    /**
     * Calculates the adjacency graph for the given Directed Acyclic Graph (DAG).
     *
     * @param dag The input MAG.
     * @return The adjacency graph represented by a Graph object.
     */
    public static Graph calcAdjacencyGraph(Graph dag) {
        List<Node> allNodes = dag.getNodes();

        List<Node> selection = allNodes.stream().filter(node -> node.getNodeType() == NodeType.SELECTION).toList();

        List<Node> measured = allNodes.stream().filter(node -> node.getNodeType() == NodeType.MEASURED).toList();

        Graph graph = new EdgeListGraph(measured);

        for (int i = 0; i < measured.size(); i++) {
            for (int j = i + 1; j < measured.size(); j++) {
                Node n1 = measured.get(i);
                Node n2 = measured.get(j);

                if (dag.paths().existsInducingPath(n1, n2, new LinkedHashSet<>(selection))) {
                    graph.addEdge(Edges.nondirectedEdge(n1, n2));
                }
            }
        }

//        IntStream.range(0, measured.size()).forEach(i -> {
//            Node n1 = measured.get(i);
//            IntStream.range(i + 1, measured.size()).forEach(j -> {
//                Node n2 = measured.get(j);
//                if (!graph.isAdjacentTo(n1, n2)) {
//                    if (dag.paths().existsInducingPath(n1, n2, new HashSet<>(selection))) {
//                        graph.addEdge(Edges.nondirectedEdge(n1, n2));
//                    }
//                }
//            });
//        });

        return graph;
    }

    /**
     * Reverts the provided graph to its unshielded colliders, with other endpoints oriented either as circles (PAG
     * case) or tails (CPDAG case). The operation orients a copy of the graph with no orientations, then iterates
     * through the unshielded colliders (X --&gt; Y &lt;-- Z with ~adj(X, Z)) in the original graph and makes these
     * orientations in the new graph.
     *
     * @param graph   the input graph on which the operation is performed.
     * @param circles if other endpoints should otherwise be circles (PAG case), false if tails (CPDAG case).
     * @return a new graph with the specified updates applied.
     */
    public static Graph revertToUnshieldedColliders(Graph graph, boolean circles) {
        Graph _graph = new EdgeListGraph(graph);

        if (circles) {
            _graph.reorientAllWith(Endpoint.CIRCLE);
        } else {
            _graph.reorientAllWith(Endpoint.TAIL);
        }

        List<Node> nodes = _graph.getNodes();

        for (Node z : nodes) {
            List<Node> adjNodes = _graph.getAdjacentNodes(z);

            for (int i = 0; i < adjNodes.size(); i++) {
                for (int j = i + 1; j < adjNodes.size(); j++) {
                    Node x = adjNodes.get(i);
                    Node y = adjNodes.get(j);

                    if (!graph.isAdjacentTo(x, y) && graph.isDefCollider(x, z, y)) {
                        _graph.setEndpoint(x, z, Endpoint.ARROW);
                        _graph.setEndpoint(y, z, Endpoint.ARROW);
                    }
                }
            }
        }
        return _graph;
    }

    /**
     * Converts a maximal ancestral graph (MAG) into a partial ancestral graph (PAG).
     *
     * @param mag                  the maximal ancestral graph (MAG) to be converted
     * @param excludeSelectionBias whether to exclude selection bias
     * @param recursiveDepth       the maximum current depth of recursion in the conversion process
     * @return the resulting partial ancestral graph (PAG)
     */
    public static Graph magToPag(Graph mag, boolean excludeSelectionBias, int recursiveDepth) {
        MagToPag magToPag = new MagToPag(mag);
        magToPag.setRecursiveDepth(recursiveDepth);
        return magToPag.convert(true, excludeSelectionBias);
    }

    private record UnorientedComponentAsUndirected(Graph pafci, Graph pcafci) {
    }
}

