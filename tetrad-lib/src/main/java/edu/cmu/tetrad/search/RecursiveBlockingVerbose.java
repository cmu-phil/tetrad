package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;

import java.io.PrintStream;
import java.util.*;

/**
 * The {@code RecursiveBlocking} class implements a recursive procedure for
 * constructing candidate separating sets between two nodes under PAG semantics,
 * with optional verbose tracing for debugging and pedagogy.
 */
public class RecursiveBlockingVerbose {

    private RecursiveBlockingVerbose() {
    }

    // ------------------------- PUBLIC API (unchanged behavior) -------------------------

    /**
     * Blocks paths recursively between two nodes in a graph, based on specified constraints and conditions.
     *
     * @param graph the graph in which paths are to be blocked
     * @param x the starting node for the path to be blocked
     * @param y the target node for the path to be blocked
     * @param containing a set of nodes that must be present in the blocked paths
     * @param notFollowed a set of nodes that must not be followed during the path blocking traversal
     * @param maxPathLength the maximum allowable length for a path to be considered for blocking
     * @return a set of nodes that were part of the blocked paths
     * @throws InterruptedException if the execution is interrupted during the operation
     */
    public static Set<Node> blockPathsRecursively(Graph graph,
                                                  Node x,
                                                  Node y,
                                                  Set<Node> containing,
                                                  Set<Node> notFollowed,
                                                  int maxPathLength) throws InterruptedException {
        return blockPathsRecursivelyVisit(
                graph, x, y, containing, notFollowed,
                graph.paths().getDescendantsMap(), maxPathLength, null, Tracer.disabled()
        );
    }

    /**
     * Recursively blocks paths in a graph between two nodes under specified constraints.
     *
     * @param graph The graph containing the nodes and edges being analyzed.
     * @param x The starting node of the paths to be blocked.
     * @param y The target node of the paths to be blocked.
     * @param containing A set of nodes that must be included in the paths being evaluated.
     * @param notFollowed A set of nodes that should not be followed during the path exploration process.
     * @param maxPathLength The maximum allowed length of the paths to be evaluated. A value of -1 indicates no limit.
     * @param knowledge A contextual object containing additional constraints or knowledge about the graph structure.
     * @return A set of nodes that have been determined to block the paths based on the given constraints.
     * @throws InterruptedException If the thread executing the method is interrupted during execution.
     */
    public static Set<Node> blockPathsRecursively(Graph graph,
                                                  Node x,
                                                  Node y,
                                                  Set<Node> containing,
                                                  Set<Node> notFollowed,
                                                  int maxPathLength,
                                                  Knowledge knowledge) throws InterruptedException {
        return blockPathsRecursivelyVisit(
                graph, x, y, containing, notFollowed,
                graph.paths().getDescendantsMap(), maxPathLength, knowledge, Tracer.disabled()
        );
    }

    // ------------------------- NEW: VERBOSE OVERLOADS -------------------------

    /**
     * Blocks paths recursively between two nodes in a graph while tracing the process.
     *
     * @param graph The graph in which the paths need to be blocked.
     * @param x The starting node of the path.
     * @param y The ending node of the path.
     * @param containing A set of nodes that must be included in the path to be blocked.
     * @param notFollowed A set of nodes that must not be followed during the traversal.
     * @param maxPathLength The maximum allowable path length for blocking. Paths exceeding this length are ignored.
     * @param out The PrintStream instance to output detailed trace information during recursive processing.
     * @return A set of nodes representing the paths that were blocked during the process.
     * @throws InterruptedException If the operation is interrupted during execution.
     */
    public static Set<Node> blockPathsRecursivelyVerbose(Graph graph,
                                                         Node x,
                                                         Node y,
                                                         Set<Node> containing,
                                                         Set<Node> notFollowed,
                                                         int maxPathLength,
                                                         PrintStream out) throws InterruptedException {
        Tracer tracer = Tracer.enabled(out);
        tracer.header("RecursiveBlocking Trace");
        return blockPathsRecursivelyVisit(
                graph, x, y, containing, notFollowed,
                graph.paths().getDescendantsMap(), maxPathLength, null, tracer
        );
    }

    /**
     * Blocks paths recursively between two nodes in a graph with detailed verbose tracing output.
     * This method utilizes a tracer to provide debug information for each step of the recursive
     * path-blocking process.
     *
     * @param graph the graph in which paths are to be blocked
     * @param x the node from which to start blocking paths
     * @param y the node at which to stop blocking paths
     * @param containing a set of nodes that must be included in the paths to be blocked
     * @param notFollowed a set of nodes that must not be followed in the paths
     * @param maxPathLength the maximum allowable length of paths to be considered
     * @param knowledge additional domain-specific knowledge to guide the path-blocking process
     * @param out the print stream for verbose trace output
     * @return a set of nodes representing the results of the path-blocking process
     * @throws InterruptedException if the path-blocking process is interrupted
     */
    public static Set<Node> blockPathsRecursivelyVerbose(Graph graph,
                                                         Node x,
                                                         Node y,
                                                         Set<Node> containing,
                                                         Set<Node> notFollowed,
                                                         int maxPathLength,
                                                         Knowledge knowledge,
                                                         PrintStream out) throws InterruptedException {
        Tracer tracer = Tracer.enabled(out);
        tracer.header("RecursiveBlocking Trace");
        return blockPathsRecursivelyVisit(
                graph, x, y, containing, notFollowed,
                graph.paths().getDescendantsMap(), maxPathLength, knowledge, tracer
        );
    }

    // ------------------------- CORE IMPLEMENTATION -------------------------

    private static Set<Node> blockPathsRecursivelyVisit(
            Graph graph,
            Node x,
            Node y,
            Set<Node> containing,
            Set<Node> notFollowed,
            Map<Node, Set<Node>> descendantsMap,
            int maxPathLength,
            Knowledge knowledge,
            Tracer tracer) throws InterruptedException {

        if (x == y) {
            throw new NullPointerException("x and y are equal");
        }

        tracer.section("blockPathsRecursivelyVisit",
                "x=" + name(x),
                "y=" + name(y),
                "containing=" + names(containing),
                "notFollowed=" + names(notFollowed),
                "maxPathLength=" + maxPathLength);

        // Z accumulates nodes that block all *blockable* paths.
        Set<Node> z = new HashSet<>(containing);
        tracer.log("Init Z = " + names(z));

        // Maintain visited nodes in the current traversal (cycle guard).
        Set<Node> path = new LinkedHashSet<>();
        path.add(x);
        tracer.log("Start path = [" + name(x) + "]");

        for (Node b : graph.getAdjacentNodes(x)) {
            if (Thread.currentThread().isInterrupted()) {
                tracer.warn("Thread interrupted → returning INDETERMINATE (null).");
                return null; // indeterminate
            }

            // Ignore the direct edge x—y on the first hop; we only explore paths that leave x via node ≠ y.
            if (b == y) {
                tracer.log("Skip first-hop direct neighbor y=" + name(y) + " (ignored on first step).");
                continue;
            }

            tracer.push("Explore first hop via b=" + name(b));

            Blockable r = findPathToTarget(
                    graph, x, b, y, path, z, maxPathLength, notFollowed, descendantsMap, tracer
            );

            tracer.log("Result via b=" + name(b) + " → " + r);

            if (r == Blockable.UNBLOCKABLE) {
                tracer.pop("UNBLOCKABLE encountered → no graphical sepset; return null");
                return null;
            }
            if (r == Blockable.INDETERMINATE) {
                tracer.pop("INDETERMINATE encountered → cannot certify; return null");
                return null;
            }

            tracer.pop(null); // BLOCKED: keep checking other neighbors
        }

        tracer.success("All branches BLOCKED → candidate Z = " + names(z));
        return z;
    }

    /**
     * Finds and returns a path from node 'a' to the target node 'b' in a given graph considering various constraints.
     *
     * @param graph         The graph in which the pathfinding occurs.
     * @param a             The starting node of the path.
     * @param b             The target node of the path.
     * @param y             An intermediary node that may influence the pathfinding process.
     * @param path          A set of nodes representing the currently considered path.
     * @param z             A set of excluded nodes that cannot be part of the path.
     * @param maxPathLength The maximum allowable path length.
     * @param notFollowed   A set of nodes that should not be followed during pathfinding.
     * @param descendantsMap A map containing nodes and their respective descendants, used to guide the pathfinding logic.
     * @return A {@code Blockable} object representing the discovered path or null if no path is found.
     * @throws InterruptedException If the operation is interrupted while executing.
     */
    public static Blockable findPathToTarget(Graph graph,
                                             Node a,
                                             Node b,
                                             Node y,
                                             Set<Node> path,
                                             Set<Node> z,
                                             int maxPathLength,
                                             Set<Node> notFollowed,
                                             Map<Node, Set<Node>> descendantsMap) throws InterruptedException {
        // Back-compat: no tracing
        return findPathToTarget(graph, a, b, y, path, z, maxPathLength, notFollowed, descendantsMap, Tracer.disabled());
    }

    /**
     * Recursive method to find and evaluate the path between two nodes in a graph.
     * The method determines if the path from a source node to a target node is
     * blockable, unblockable, or indeterminate based on various conditions,
     * including the presence of certain nodes, maximum path length, and contextual
     * constraints provided by descendants and traced paths.
     *
     * @param graph The graph containing the nodes and edges for evaluation.
     * @param a The source node from which the path begins.
     * @param b The current node being evaluated along the path.
     * @param y The target node to which the path is being traced.
     * @param path A set of nodes constituting the current path. Used to prevent cycles during the evaluation.
     * @param z A set of conditioned nodes in the graph that must be considered during the path evaluation.
     * @param maxPathLength The maximum allowed length of the path. If -1, no limit is enforced.
     * @param notFollowed A set of nodes that should not be followed during path exploration.
     * @param descendantsMap A mapping from each node to the set of its descendants
     *                       (direct or indirect) in the graph. Used for reachability evaluation.
     * @param tracer A utility object for logging, tracing, and debugging the recursive operations.
     * @return A {@link Blockable} enum indicating whether the path is:
     *         - UNBLOCKABLE: The path to the target node is definitively possible without blocks.
     *         - BLOCKED: The path to the target node is definitively blocked.
     *         - INDETERMINATE: The path cannot be conclusively determined due to limitations such as interruption.
     * @throws InterruptedException If the thread executing the method is interrupted during execution.
     */
    public static Blockable findPathToTarget(Graph graph,
                                             Node a,
                                             Node b,
                                             Node y,
                                             Set<Node> path,
                                             Set<Node> z,
                                             int maxPathLength,
                                             Set<Node> notFollowed,
                                             Map<Node, Set<Node>> descendantsMap,
                                             Tracer tracer) throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            tracer.warn("Thread interrupted inside findPathToTarget.");
            return Blockable.INDETERMINATE;
        }

        tracer.push("findPathToTarget(a=" + name(a) + ", b=" + name(b) + ", y=" + name(y) + ") path=" + names(path) + " Z=" + names(z));

        // Immediate termination cases before adding b to path.
        if (b == y) {
            tracer.fail("Hit y directly at b → UNBLOCKABLE");
            tracer.pop(null);
            return Blockable.UNBLOCKABLE;
        }
        if (path.contains(b)) {
            tracer.fail("Cycle guard: b already in path → UNBLOCKABLE");
            tracer.pop(null);
            return Blockable.UNBLOCKABLE;
        }
        if (notFollowed.contains(b)) {
            tracer.warn("b in notFollowed → INDETERMINATE");
            tracer.pop(null);
            return Blockable.INDETERMINATE;
        }
        if (notFollowed.contains(y)) {
            tracer.log("y in notFollowed → treat branch as BLOCKED");
            tracer.pop(null);
            return Blockable.BLOCKED;
        }

        path.add(b);
        tracer.log("Push b onto path → " + names(path));

        try {
            if (maxPathLength != -1 && path.size() > maxPathLength) {
                tracer.warn("Exceeded maxPathLength(" + maxPathLength + ") → INDETERMINATE");
                return Blockable.INDETERMINATE;
            }

            // Case 1: if b is latent or already in Z, we cannot (or need not) condition on it.
            if (b.getNodeType() == NodeType.LATENT || z.contains(b)) {
                tracer.log("Case1: b is " + (b.getNodeType() == NodeType.LATENT ? "LATENT" : "already in Z") + " → continue w/o conditioning on b");

                List<Node> passNodes = getReachableNodesTrace(graph, a, b, z, descendantsMap, tracer);
                passNodes.removeAll(notFollowed);

                for (Node c : passNodes) {
                    if (Thread.currentThread().isInterrupted()) {
                        tracer.warn("Interrupted during Case1 loop.");
                        return Blockable.INDETERMINATE;
                    }
                    Blockable blockable = findPathToTarget(graph, b, c, y, path, z, maxPathLength, notFollowed, descendantsMap, tracer);
                    tracer.log("Case1 continuation b→c=" + name(c) + " → " + blockable);

                    if (blockable == Blockable.UNBLOCKABLE || blockable == Blockable.INDETERMINATE) {
                        tracer.fail("Case1 found " + blockable + " → UNBLOCKABLE");
                        return Blockable.UNBLOCKABLE;
                    }
                }

                tracer.ok("Case1: all continuations BLOCKED w/o adding b");
                return Blockable.BLOCKED;
            }

            // Case 2: Try first WITHOUT conditioning on b.
            {
                tracer.log("Case2: try WITHOUT conditioning on b");
                boolean blockable1 = true;

                List<Node> passNodes = getReachableNodesTrace(graph, a, b, z, descendantsMap, tracer);
                passNodes.removeAll(notFollowed);

                for (Node c : passNodes) {
                    if (Thread.currentThread().isInterrupted()) {
                        tracer.warn("Interrupted during Case2 loop.");
                        return Blockable.INDETERMINATE;
                    }

                    Blockable blockType = findPathToTarget(graph, b, c, y, path, z, maxPathLength, notFollowed, descendantsMap, tracer);
                    tracer.log("Case2 continuation b→c=" + name(c) + " → " + blockType);

                    if (blockType == Blockable.UNBLOCKABLE || blockType == Blockable.INDETERMINATE) {
                        blockable1 = false;
                        break;
                    }
                }

                if (blockable1) {
                    tracer.ok("Case2 succeeded WITHOUT b → BLOCKED");
                    return Blockable.BLOCKED;
                } else {
                    tracer.log("Case2 failed → try WITH conditioning on b");
                }
            }

            // Case 3: Try WITH conditioning on b.
            z.add(b);
            tracer.log("Case3: add b to Z → Z=" + names(z));
            {
                boolean blockable2 = true;

                List<Node> passNodes = getReachableNodesTrace(graph, a, b, z, descendantsMap, tracer);
                passNodes.removeAll(notFollowed);

                for (Node c : passNodes) {
                    if (Thread.currentThread().isInterrupted()) {
                        tracer.warn("Interrupted during Case3 loop. Rolling back b from Z.");
                        z.remove(b);
                        return Blockable.INDETERMINATE;
                    }

                    Blockable blockable = findPathToTarget(graph, b, c, y, path, z, maxPathLength, notFollowed, descendantsMap, tracer);
                    tracer.log("Case3 continuation b→c=" + name(c) + " → " + blockable);

                    if (blockable == Blockable.UNBLOCKABLE || blockable == Blockable.INDETERMINATE) {
                        blockable2 = false;
                        break;
                    }
                }

                if (blockable2) {
                    tracer.ok("Case3 succeeded WITH b ∈ Z → BLOCKED");
                    return Blockable.BLOCKED;
                } else {
                    // Roll back Z: adding b did not help, leave Z unchanged.
                    z.remove(b);
                    tracer.fail("Case3 failed WITH b ∈ Z → roll back; return UNBLOCKABLE");
                    return Blockable.UNBLOCKABLE;
                }
            }
        } finally {
            // ALWAYS clean up the path.
            path.remove(b);
            tracer.log("Pop b from path → " + names(path));
            tracer.pop(null);
        }
    }

    private static List<Node> getReachableNodes(Graph graph,
                                                Node a,
                                                Node b,
                                                Set<Node> z,
                                                Map<Node, Set<Node>> descendantsMap) {
        List<Node> passNodes = new ArrayList<>();
        for (Node c : graph.getAdjacentNodes(b)) {
            if (c == a) continue;
            if (reachable(graph, a, b, c, z, descendantsMap)) {
                passNodes.add(c);
            }
        }
        return passNodes;
    }

    private static List<Node> getReachableNodesTrace(Graph graph,
                                                     Node a,
                                                     Node b,
                                                     Set<Node> z,
                                                     Map<Node, Set<Node>> descendantsMap,
                                                     Tracer tracer) {
        List<Node> passNodes = new ArrayList<>();
        for (Node c : graph.getAdjacentNodes(b)) {
            if (c == a) continue;
            boolean r = reachable(graph, a, b, c, z, descendantsMap);
            tracer.log("reachable? triple (" + name(a) + " " + name(b) + " " + name(c) + ") → " + r
                       + "  [collider=" + graph.isDefCollider(a, b, c)
                       + ", underline=" + graph.isUnderlineTriple(a, b, c)
                       + ", b∈Z=" + z.contains(b)
                       + ", hasZDesc=" + hasZDesc(descendantsMap, b, z, graph) + "]");
            if (r) passNodes.add(c);
        }
        tracer.log("Reachable from b=" + name(b) + " (excluding a=" + name(a) + "): " + names(passNodes));
        return passNodes;
    }

    private static boolean reachable(Graph graph,
                                     Node a,
                                     Node b,
                                     Node c,
                                     Set<Node> z,
                                     Map<Node, Set<Node>> descendantsMap) {
        boolean collider = graph.isDefCollider(a, b, c);

        // Non-collider (or underlined collider) is traversable if we are NOT conditioning on b.
        if ((!collider || graph.isUnderlineTriple(a, b, c)) && !z.contains(b)) {
            return true;
        }

        // Collider is traversable iff collider or a DESCENDANT of it is in Z.
        if (descendantsMap == null) {
            return collider && graph.paths().isAncestorOfAnyZ(b, z);
        } else {
            Set<Node> desc = descendantsMap.getOrDefault(b, Collections.emptySet());
            for (Node d : desc) {
                if (z.contains(d)) {
                    return collider;
                }
            }
            return false;
        }
    }

    private static boolean hasZDesc(Map<Node, Set<Node>> descendantsMap, Node b, Set<Node> z, Graph graph) {
        if (descendantsMap == null) {
            return graph.paths().isAncestorOfAnyZ(b, z);
        } else {
            Set<Node> desc = descendantsMap.getOrDefault(b, Collections.emptySet());
            for (Node d : desc) if (z.contains(d)) return true;
            return false;
        }
    }

    /**
     * The Blockable enum represents the state of an entity in relation to its
     * ability to be blocked. It defines three possible states:
     */
    public enum Blockable {

        /**
         * Represents the state where an entity is explicitly unable to perform certain actions
         * or be accessed due to restrictions applied. This state indicates that the entity
         * is actively blocked from operation or interaction.
         */
        BLOCKED,

        /**
         * Represents the state where an entity cannot be blocked from performing actions
         * or being accessed under any circumstances. This state indicates the entity is
         * inherently immune to any blocking restrictions.
         */
        UNBLOCKABLE,

        /**
         * Represents a state where it is not explicitly determined whether an entity
         * is blocked or unblockable. This state indicates ambiguity or lack of
         * sufficient information to classify the entity as either blocked or unblockable.
         */
        INDETERMINATE
    }

    // ------------------------- TRACING UTILS -------------------------

    private static String name(Node n) {
        return n == null ? "null" : n.getName();
    }

    private static String names(Collection<Node> nodes) {
        if (nodes == null) return "∅";
        if (nodes.isEmpty()) return "∅";
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        boolean first = true;
        for (Node n : nodes) {
            if (!first) sb.append(", ");
            sb.append(name(n));
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * Lightweight indented tracer. No-ops if disabled.
     */
    public static final class Tracer {
        private final PrintStream out;
        private int indent = 0;
        private boolean enabled;

        /**
         * Constructs a Tracer instance with the specified output stream and enabled state.
         *
         * @param out     the {@code PrintStream} to which log messages will be written; if {@code null},
         *                the tracer will be disabled regardless of the {@code enabled} parameter
         * @param enabled a flag indicating whether the tracer should be enabled; if {@code false},
         *                logging operations will be suppressed
         */
        private Tracer(PrintStream out, boolean enabled) {
            this.out = out;
            this.enabled = enabled && out != null;
        }

        /**
         * Creates an enabled tracer instance. An enabled tracer will actively log messages
         * and perform logging-related operations.
         *
         * @param out the {@code PrintStream} to which log messages will be written
         * @return a new instance of an enabled Tracer configured to output to the specified {@code PrintStream}
         */
        public static Tracer enabled(PrintStream out) {
            return new Tracer(out, true);
        }

        /**
         * Creates a disabled tracer instance. A disabled tracer does not log any messages
         * or perform any logging-related operations.
         *
         * @return a new instance of a disabled Tracer
         */
        public static Tracer disabled() {
            return new Tracer(null, false);
        }

        /**
         * Logs a section header with optional key-value pairs.
         *
         * @param title the section title
         */
        public void header(String title) {
            if (!enabled) return;
            out.println("==== " + title + " ====");
        }

        /**
         * Logs a section header with optional key-value pairs.
         *
         * @param name the section name
         * @param kv   optional key-value pairs to include in the section header
         */
        public void section(String name, String... kv) {
            if (!enabled) return;
            out.println("• " + name + (kv.length == 0 ? "" : " " + Arrays.toString(kv)));
        }

        /**
         * Logs a message if the tracer is enabled.
         *
         * @param msg the message to log
         */
        public void push(String msg) {
            if (!enabled) return;
            printLine("▶ " + msg);
            indent++;
        }

        /**
         * Decreases the indentation level and optionally logs a closing trailer message
         * if the tracer is enabled.
         *
         * @param trailer the closing message to log, or null/empty to skip logging the trailer
         */
        public void pop(String trailer) {
            if (!enabled) return;
            indent = Math.max(0, indent - 1);
            if (trailer != null && !trailer.isEmpty()) {
                printLine("◀ " + trailer);
            }
        }

        /**
         * Logs a message if the tracer is enabled.
         *
         * @param msg the message to log
         */
        public void log(String msg) {
            if (!enabled) return;
            printLine(msg);
        }

        /**
         * Logs a success message prefixed with a checkmark symbol if the tracer is enabled.
         *
         * @param msg the success message to log
         */
        public void ok(String msg) {
            if (!enabled) return;
            printLine("✓ " + msg);
        }

        /**
         * Logs a failure message prefixed with a cross mark emoji if the tracer is enabled.
         *
         * @param msg the failure message to log
         */
        public void fail(String msg) {
            if (!enabled) return;
            printLine("✗ " + msg);
        }

        /**
         * Logs a warning message if the tracer is enabled.
         *
         * @param msg the warning message to log
         */
        public void warn(String msg) {
            if (!enabled) return;
            printLine("! " + msg);
        }

        /**
         * Logs a success message prefixed with a checkmark emoji if the tracer is enabled.
         *
         * @param msg the success message to log
         */
        public void success(String msg) {
            if (!enabled) return;
            printLine("✅ " + msg);
        }

        private void printLine(String msg) {
            if (!enabled) return;
            for (int i = 0; i < indent; i++) out.print("  ");
            out.println(msg);
        }
    }
}