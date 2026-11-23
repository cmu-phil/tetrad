package edu.cmu.tetrad.search;

import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Endpoint;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.graph.Paths;
import edu.cmu.tetrad.search.test.IndependenceResult;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.utils.PagLegalityCheck;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;

/**
 * Small-graph, oracle-based prototype of a single-edge DMAG I-map descent step.
 *
 * <p>The input is a DMAG, NOT a PAG. The search visits its Markov-equivalent DMAG
 * representatives using one-endpoint mark changes, and tries deleting exactly one
 * adjacency from each representative. For each candidate, it checks MAG legality
 * and ALL elementary m-separations against the supplied independence test. With
 * a correct, deterministic independence oracle, every returned graph is an I-map.
 *
 * <p>This is deliberately exponential and bounded; it is intended for FCIT/BFCI
 * oracle experiments, not for unrestricted sample-based searches. When using a
 * statistical test, the certificate is only relative to that test's decisions.
 * No PAG conversion, background knowledge, or session state is modified.
 */
public final class OracleDmagDeletionSearch {

    /** Describes whether the bounded search found a valid step. */
    public enum Status { FOUND, NO_DELETION, LIMIT_REACHED, INVALID_START }

    /** One verified deletion, with a representative before and a DMAG after. */
    public record Step(Graph representative, Graph after, Edge removed,
                       int checkedIndependences) {
    }

    /** A bounded search outcome. LIMIT_REACHED is not evidence of nonexistence. */
    public record Result(Status status, Optional<Step> step, int representativesVisited,
                         int deletionAttempts, int oracleQueries) {
    }

    private final int maxVertices;
    private final int maxRepresentatives;

    /** Default small-graph limits (eight vertices, 2,000 equivalent DMAGs). */
    public OracleDmagDeletionSearch() {
        this(8, 2_000);
    }

    public OracleDmagDeletionSearch(int maxVertices, int maxRepresentatives) {
        if (maxVertices < 2 || maxVertices > 10 || maxRepresentatives < 1) {
            throw new IllegalArgumentException("Invalid small-graph search limits");
        }
        this.maxVertices = maxVertices;
        this.maxRepresentatives = maxRepresentatives;
    }

    /**
     * Finds at most one deletion while preserving the full oracle I-map invariant.
     * An invalid oracle judgment fails closed (throws), rather than approving a step.
     * The input graph is not mutated.
     *
     * @throws InterruptedException if the test or caller is interrupted
     */
    public Result findDeletion(Graph input, IndependenceTest oracle) throws InterruptedException {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(oracle, "oracle");
        if (input.getNumNodes() > maxVertices) {
            throw new IllegalArgumentException("Too many vertices for exhaustive certification: "
                                               + input.getNumNodes());
        }
        if (!isDmag(input)) {
            throw new IllegalArgumentException("Input must be a legal DMAG (without selection nodes)");
        }

        List<Node> nodes = new ArrayList<>(input.getNodes());
        nodes.sort(Comparator.comparing(Node::getName));
        Map<String, Node> testNodes = new HashMap<>();
        for (Node node : oracle.getVariables()) {
            if (testNodes.put(node.getName(), node) != null) {
                throw new IllegalArgumentException("Duplicate oracle node name: " + node.getName());
            }
        }
        for (Node node : nodes) {
            if (!testNodes.containsKey(node.getName())) {
                throw new IllegalArgumentException("Oracle is missing variable " + node.getName());
            }
        }
        if (testNodes.size() != nodes.size()) {
            throw new IllegalArgumentException("Oracle and DMAG must have the same observed variables");
        }

        List<Fact> facts = enumerateFacts(nodes, testNodes);
        Graph start = new EdgeListGraph(input);
        BitSet startModel = model(start, facts);
        Map<Integer, Boolean> answers = new HashMap<>();
        int[] oracleCalls = {0};
        if (!certify(startModel, facts, oracle, answers, oracleCalls)) {
            return new Result(Status.INVALID_START, Optional.empty(), 0, 0, oracleCalls[0]);
        }

        // Marginal dependence components of H, used ONLY to order candidates.
        // A wrong inferred component cannot cause acceptance of a bad deletion.
        int[] components = marginalComponents(nodes, facts, oracle, answers, oracleCalls);

        Queue<Graph> queue = new ArrayDeque<>();
        Set<String> seen = new HashSet<>();
        queue.add(start);
        seen.add(markingKey(start, nodes));
        int explored = 0;
        int attempts = 0;

        while (!queue.isEmpty()) {
            checkInterrupted();
            if (explored == maxRepresentatives) {
                return new Result(Status.LIMIT_REACHED, Optional.empty(), explored, attempts, oracleCalls[0]);
            }
            Graph representative = queue.remove();
            explored++;

            for (Edge edge : orderedEdges(representative, nodes, components)) {
                checkInterrupted();
                attempts++;
                Graph reduced = new EdgeListGraph(representative);
                reduced.removeEdge(edge.getNode1(), edge.getNode2());
                if (!isDmag(reduced)) continue;

                BitSet reducedModel = model(reduced, facts);
                if (!contains(reducedModel, startModel)) continue;
                if (certify(reducedModel, facts, oracle, answers, oracleCalls)) {
                    return new Result(Status.FOUND,
                            Optional.of(new Step(new EdgeListGraph(representative), reduced, edge,
                                    reducedModel.cardinality())),
                            explored, attempts, oracleCalls[0]);
                }
            }

            // The Zhang-Spirtes transformational characterization connects a DMAG
            // equivalence class via legitimate one-mark changes. Instead of
            // implementing the legitimacy rules, verify each neighbor exactly.
            for (Graph neighbor : markNeighbors(representative)) {
                checkInterrupted();
                String key = markingKey(neighbor, nodes);
                if (seen.contains(key) || !isDmag(neighbor)) continue;
                if (!model(neighbor, facts).equals(startModel)) continue;
                seen.add(key);
                queue.add(neighbor);
            }
        }
        return new Result(Status.NO_DELETION, Optional.empty(), explored, attempts, oracleCalls[0]);
    }

    private static boolean isDmag(Graph graph) {
        for (Node node : graph.getNodes()) {
            if (node.getNodeType() != NodeType.MEASURED) return false;
        }
        for (Edge edge : graph.getEdges()) {
            if (!Edges.isDirectedEdge(edge) && !Edges.isBidirectedEdge(edge)) return false;
        }
        return PagLegalityCheck.isLegalMagQuiet(graph, Set.of());
    }

    private record Fact(Node x, Node y, Set<Node> z, Node testX, Node testY,
                        Set<Node> testZ) {
    }

    private static List<Fact> enumerateFacts(List<Node> nodes, Map<String, Node> testNodes) {
        List<Fact> facts = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                List<Node> others = new ArrayList<>(nodes);
                others.remove(nodes.get(j));
                others.remove(nodes.get(i));
                int subsets = 1 << others.size();
                for (int mask = 0; mask < subsets; mask++) {
                    Set<Node> z = new LinkedHashSet<>();
                    Set<Node> testZ = new LinkedHashSet<>();
                    for (int k = 0; k < others.size(); k++) {
                        if ((mask & (1 << k)) != 0) {
                            Node node = others.get(k);
                            z.add(node);
                            testZ.add(testNodes.get(node.getName()));
                        }
                    }
                    facts.add(new Fact(nodes.get(i), nodes.get(j), Set.copyOf(z),
                            testNodes.get(nodes.get(i).getName()),
                            testNodes.get(nodes.get(j).getName()), Set.copyOf(testZ)));
                }
            }
        }
        return facts;
    }

    private static BitSet model(Graph graph, List<Fact> facts) throws InterruptedException {
        BitSet bits = new BitSet(facts.size());
        Paths paths = graph.paths();
        for (int i = 0; i < facts.size(); i++) {
            checkInterrupted();
            Fact f = facts.get(i);
            if (paths.isMSeparatedFrom(f.x(), f.y(), f.z(), false)) bits.set(i);
        }
        return bits;
    }

    private static boolean certify(BitSet implied, List<Fact> facts,
                                   IndependenceTest oracle, Map<Integer, Boolean> cached,
                                   int[] calls) throws InterruptedException {
        for (int k = implied.nextSetBit(0); k >= 0; k = implied.nextSetBit(k + 1)) {
            if (!oracleAnswer(k, facts, oracle, cached, calls)) return false;
        }
        return true;
    }

    private static boolean oracleAnswer(int index, List<Fact> facts,
                                        IndependenceTest oracle, Map<Integer, Boolean> cached,
                                        int[] calls) throws InterruptedException {
        Boolean answer = cached.get(index);
        if (answer != null) return answer;
        checkInterrupted();
        Fact f = facts.get(index);
        IndependenceResult result = oracle.checkIndependence(f.testX(), f.testY(), f.testZ());
        calls[0]++;
        if (result == null || !result.isValid()) {
            throw new IllegalStateException("Oracle returned an invalid independence judgment for "
                                            + f.x() + ", " + f.y() + " | " + f.z());
        }
        cached.put(index, result.isIndependent());
        return result.isIndependent();
    }

    private static boolean contains(BitSet superset, BitSet subset) {
        BitSet missing = (BitSet) subset.clone();
        missing.andNot(superset);
        return missing.isEmpty();
    }

    private static int[] marginalComponents(List<Node> nodes, List<Fact> facts,
                                            IndependenceTest oracle, Map<Integer, Boolean> cached,
                                            int[] calls) throws InterruptedException {
        int[] parent = new int[nodes.size()];
        for (int i = 0; i < parent.length; i++) parent[i] = i;
        for (int k = 0; k < facts.size(); k++) {
            Fact fact = facts.get(k);
            if (!fact.z().isEmpty()) continue;
            int i = nodes.indexOf(fact.x());
            int j = nodes.indexOf(fact.y());
            if (!oracleAnswer(k, facts, oracle, cached, calls)) {
                parent[find(parent, i)] = find(parent, j);
            }
        }
        for (int i = 0; i < parent.length; i++) parent[i] = find(parent, i);
        return parent;
    }

    private static int find(int[] parent, int i) {
        while (parent[i] != i) i = parent[i];
        return i;
    }

    private static List<Edge> orderedEdges(Graph graph, List<Node> nodes, int[] components) {
        List<Edge> edges = new ArrayList<>(graph.getEdges());
        edges.sort(Comparator
                .comparingInt((Edge e) -> {
                    int i = nodes.indexOf(e.getNode1());
                    int j = nodes.indexOf(e.getNode2());
                    return components[i] == components[j] ? 1 : 0;
                })
                .thenComparingInt(e -> bridge(graph, e) ? 0 : 1)
                .thenComparingInt(e -> Edges.isDirectedEdge(e) ? 0 : 1)
                .thenComparingInt(e -> ancestorRank(graph, e.getNode1())
                        + ancestorRank(graph, e.getNode2()))
                .thenComparing(e -> pairKey(e.getNode1(), e.getNode2())));
        return edges;
    }

    private static int ancestorRank(Graph graph, Node node) {
        int result = 0;
        for (Node other : graph.getNodes()) {
            if (!node.equals(other) && graph.isAncestorOf(other, node)) result++;
        }
        return result;
    }

    private static boolean bridge(Graph graph, Edge edge) {
        Node source = edge.getNode1();
        Node target = edge.getNode2();
        Queue<Node> queue = new ArrayDeque<>();
        Set<Node> seen = new HashSet<>();
        queue.add(source);
        seen.add(source);
        while (!queue.isEmpty()) {
            Node current = queue.remove();
            for (Node next : graph.getAdjacentNodes(current)) {
                if ((current.equals(source) && next.equals(target)) ||
                    (current.equals(target) && next.equals(source))) continue;
                if (next.equals(target)) return false;
                if (seen.add(next)) queue.add(next);
            }
        }
        return true;
    }

    private static List<Graph> markNeighbors(Graph graph) {
        List<Graph> neighbors = new ArrayList<>();
        for (Edge edge : graph.getEdges()) {
            Node a = edge.getNode1();
            Node b = edge.getNode2();
            if (Edges.isDirectedEdge(edge)) {
                Graph next = new EdgeListGraph(graph);
                next.removeEdge(a, b);
                next.addBidirectedEdge(a, b);
                neighbors.add(next);
            } else if (Edges.isBidirectedEdge(edge)) {
                Graph ab = new EdgeListGraph(graph);
                ab.removeEdge(a, b);
                ab.addDirectedEdge(a, b);
                neighbors.add(ab);
                Graph ba = new EdgeListGraph(graph);
                ba.removeEdge(a, b);
                ba.addDirectedEdge(b, a);
                neighbors.add(ba);
            }
        }
        return neighbors;
    }

    private static String markingKey(Graph graph, List<Node> orderedNodes) {
        StringBuilder key = new StringBuilder(orderedNodes.size() * (orderedNodes.size() - 1) / 2);
        for (int i = 0; i < orderedNodes.size(); i++) {
            for (int j = i + 1; j < orderedNodes.size(); j++) {
                Node a = orderedNodes.get(i);
                Node b = orderedNodes.get(j);
                if (!graph.isAdjacentTo(a, b)) {
                    key.append('0');
                } else if (Edges.isBidirectedEdge(graph.getEdge(a, b))) {
                    key.append('3');
                } else {
                    key.append(graph.getEndpoint(a, b) == Endpoint.ARROW ? '1' : '2');
                }
            }
        }
        return key.toString();
    }

    private static String pairKey(Node a, Node b) {
        return a.getName().compareTo(b.getName()) < 0
                ? a.getName() + "/" + b.getName() : b.getName() + "/" + a.getName();
    }

    private static void checkInterrupted() throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
    }
}
