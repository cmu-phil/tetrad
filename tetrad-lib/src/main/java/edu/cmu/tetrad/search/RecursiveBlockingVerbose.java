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
     * Verbose version. Pass a {@code PrintStream} (e.g., System.out) to see a step-by-step trace.
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
     * Verbose version with optional Knowledge object.
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
     * Evaluates whether all paths from a→b onward to y can be blocked by the current candidate set Z, possibly
     * augmented with b.
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
        BLOCKED,
        UNBLOCKABLE,
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

        private Tracer(PrintStream out, boolean enabled) {
            this.out = out;
            this.enabled = enabled && out != null;
        }

        public static Tracer enabled(PrintStream out) {
            return new Tracer(out, true);
        }

        public static Tracer disabled() {
            return new Tracer(null, false);
        }

        public void header(String title) {
            if (!enabled) return;
            out.println("==== " + title + " ====");
        }

        public void section(String name, String... kv) {
            if (!enabled) return;
            out.println("• " + name + (kv.length == 0 ? "" : " " + Arrays.toString(kv)));
        }

        public void push(String msg) {
            if (!enabled) return;
            printLine("▶ " + msg);
            indent++;
        }

        public void pop(String trailer) {
            if (!enabled) return;
            indent = Math.max(0, indent - 1);
            if (trailer != null && !trailer.isEmpty()) {
                printLine("◀ " + trailer);
            }
        }

        public void log(String msg) {
            if (!enabled) return;
            printLine(msg);
        }

        public void ok(String msg) {
            if (!enabled) return;
            printLine("✓ " + msg);
        }

        public void fail(String msg) {
            if (!enabled) return;
            printLine("✗ " + msg);
        }

        public void warn(String msg) {
            if (!enabled) return;
            printLine("! " + msg);
        }

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