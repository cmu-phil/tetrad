package edu.cmu.tetrad.test;

import edu.cmu.tetrad.graph.*;

import java.util.*;

/**
 * Diagnostic for {@code Paths.existsInducingPath} (and its BFS/DFS backends).
 *
 * <p>Motivation: the existing guard test ({@code testZhangPagToMag}) only ever feeds
 * the maximality check LEGAL graphs, where the correct answer for every non-adjacent
 * pair is always {@code false}. It therefore exercises false POSITIVES but never a
 * true inducing path, so a false-NEGATIVE bug — returning {@code false} when an
 * inducing path exists — is invisible to it, yet that is exactly the branch FCIT's
 * legality-and-revert relies on. These cases feed the detector inputs it is actually
 * for: ancestral-but-NON-maximal graphs (a true edge deleted, an inducing path left
 * behind), with maximal controls alongside.
 *
 * <p>The cases bisect the failure:
 * <ul>
 *   <li>WALK_ONLY: true via the latent-interior exemption, no ancestor gate touched.
 *       If this passes but the ancestral cases fail, the walk is fine and the bug is
 *       in the collider->ancestor gate.</li>
 *   <li>ANC_ENDPOINT_2COLLIDER: ancestral, non-maximal, NO selection vars — the
 *       scenario for latent-variable benchmarks. Detection depends on
 *       {@code isAncestorOf(collider, endpoint)}.</li>
 *   <li>ANC_SELECTION: detection depends on {@code isAncestorOfAnyZ(collider, S)};
 *       the paired empty-selection control must be false. If the {S} case is false
 *       and the {} case is correct, the miss is specifically in the Z-ancestor
 *       predicate.</li>
 *   <li>*_MAXIMAL: controls that MUST be false. If one of these is true, the gate
 *       isn't gating (false positive) rather than missing.</li>
 * </ul>
 *
 * <p>Runnable as {@code main} (prints a verdict table and throws on any mismatch).
 * Drop {@code @Test} on {@link #runAll()} if you want it under JUnit; it takes no
 * arguments and throws {@link AssertionError} on failure.
 */
public final class InducingPathDetectionTest {

    private InducingPathDetectionTest() {
    }

    public static void main(String[] args) {
        runAll();
    }

    public static void runAll() {
        List<Case> cases = new ArrayList<>();
        cases.add(walkOnlyLatentInterior());
        cases.add(ancEndpointTwoCollider());
        cases.add(ancEndpointTwoColliderMaximalControl());
        cases.add(ancSelection());
        cases.add(ancSelectionEmptyControl());

        System.out.printf("%-34s | %-8s | %-8s | %-8s | %-8s | %s%n",
                "case", "expected", "BFS", "DFS", "dispatch", "status");
        System.out.println("-".repeat(96));

        int failures = 0;
        for (Case c : cases) {
            Paths p = c.graph.paths();
            boolean bfs = p.existsInducingPathBFS(c.x, c.y, c.selection);
            boolean dfs = p.existsInducingPathDFS(c.x, c.y, c.selection);
            boolean disp = p.existsInducingPath(c.x, c.y, c.selection);

            boolean ok = (bfs == c.expected) && (dfs == c.expected) && (disp == c.expected);
            boolean agree = (bfs == dfs);
            String status = ok ? "PASS" : (agree ? "FAIL" : "FAIL (BFS/DFS DISAGREE)");
            if (!ok) failures++;

            System.out.printf("%-34s | %-8s | %-8s | %-8s | %-8s | %s%n",
                    c.name, c.expected, bfs, dfs, disp, status);
        }

        System.out.println("-".repeat(96));
        if (failures == 0) {
            System.out.println("ALL PASS — detector correctly finds the planted inducing paths "
                    + "and rejects the maximal controls.");
        } else {
            System.out.println(failures + " case(s) failed. Read the table:");
            System.out.println("  * WALK_ONLY fails        -> bug is in the path walk itself.");
            System.out.println("  * only ANC_* cases fail  -> bug is in the collider->ancestor gate.");
            System.out.println("  * ANC_SELECTION fails,");
            System.out.println("    ANC_ENDPOINT passes    -> bug is specifically in isAncestorOfAnyZ.");
            System.out.println("  * a *_MAXIMAL control    -> false positive (gate not gating), not a miss.");
            System.out.println("    returns true");
            System.out.println("  * BFS/DFS DISAGREE       -> one backend is incomplete; compare the two.");
            throw new AssertionError(failures + " inducing-path detection case(s) failed; see table above.");
        }
    }

    // ---------------------------------------------------------------------
    // Cases
    // ---------------------------------------------------------------------

    /**
     * WALK_ONLY. A o-* C *-o B with C LATENT and a non-collider on the path.
     * A latent interior is exempt from the collider requirement, so this is an
     * inducing path with no collider and no ancestor obligation. True via the
     * walk alone — touches neither isAncestorOf nor isAncestorOfAnyZ.
     */
    private static Case walkOnlyLatentInterior() {
        Node a = m("A"), b = m("B"), c = new GraphNode("C");
        c.setNodeType(NodeType.LATENT);
        Graph g = new EdgeListGraph(Arrays.asList(a, b, c));
        g.addEdge(Edges.directedEdge(a, c)); // A -> C
        g.addEdge(Edges.directedEdge(c, b)); // C -> B   (C latent, non-collider here)
        return new Case("WALK_ONLY_latent_interior", g, a, b, empty(), true);
    }

    /**
     * ANC_ENDPOINT_2COLLIDER. Ancestral, almost-cycle-free, NON-maximal, with NO
     * selection variables — the latent-variable-benchmark scenario.
     *
     * Skeleton on the A--B route:  A <-> C <-> D <-> B
     *   C is a collider on (A,C,D) and an ancestor of the FAR endpoint B: C -> E -> B
     *   D is a collider on (C,D,B) and an ancestor of the FAR endpoint A: D -> F -> A
     * A collider adjacent to an endpoint cannot be that endpoint's ancestor without
     * an almost-cycle, so each collider is made an ancestor of the OTHER endpoint.
     * A and B are non-adjacent; the path A<->C<->D<->B is inducing => non-maximal.
     * Detection depends on isAncestorOf(collider, endpoint).
     */
    private static Case ancEndpointTwoCollider() {
        Node a = m("A"), b = m("B"), c = m("C"), d = m("D"), e = m("E"), f = m("F");
        Graph g = new EdgeListGraph(Arrays.asList(a, b, c, d, e, f));
        g.addEdge(Edges.bidirectedEdge(a, c)); // A <-> C
        g.addEdge(Edges.bidirectedEdge(c, d)); // C <-> D
        g.addEdge(Edges.bidirectedEdge(d, b)); // D <-> B
        g.addEdge(Edges.directedEdge(c, e));   // C -> E
        g.addEdge(Edges.directedEdge(e, b));   // E -> B   (C ancestor of B)
        g.addEdge(Edges.directedEdge(d, f));   // D -> F
        g.addEdge(Edges.directedEdge(f, a));   // F -> A   (D ancestor of A)
        return new Case("ANC_ENDPOINT_2collider", g, a, b, empty(), true);
    }

    /**
     * Maximal control for the case above: same bidirected spine, but the colliders
     * are ancestors of nothing. C, D are colliders but not ancestors of A, B, or any
     * selection var, so neither qualifies and there is NO inducing path. MUST be false.
     */
    private static Case ancEndpointTwoColliderMaximalControl() {
        Node a = m("A"), b = m("B"), c = m("C"), d = m("D");
        Graph g = new EdgeListGraph(Arrays.asList(a, b, c, d));
        g.addEdge(Edges.bidirectedEdge(a, c)); // A <-> C
        g.addEdge(Edges.bidirectedEdge(c, d)); // C <-> D
        g.addEdge(Edges.bidirectedEdge(d, b)); // D <-> B
        return new Case("ANC_ENDPOINT_2collider_MAXIMAL", g, a, b, empty(), false);
    }

    /**
     * ANC_SELECTION. Single-collider inducing path that is legal ONLY because the
     * collider is an ancestor of a selection variable.
     *   A <-> C <-> B,  C -> S
     * With S in the selection set, C qualifies (ancestor of S) and A<->C<->B is an
     * inducing path => true. Detection depends on isAncestorOfAnyZ(C, {S}).
     */
    private static Case ancSelection() {
        Node a = m("A"), b = m("B"), c = m("C"), s = m("S");
        Graph g = new EdgeListGraph(Arrays.asList(a, b, c, s));
        g.addEdge(Edges.bidirectedEdge(a, c)); // A <-> C
        g.addEdge(Edges.bidirectedEdge(c, b)); // C <-> B
        g.addEdge(Edges.directedEdge(c, s));   // C -> S  (S a selection var)
        return new Case("ANC_SELECTION_{S}", g, a, b, set(s), true);
    }

    /**
     * Paired control for ANC_SELECTION on the SAME graph with an empty selection set.
     * Now C is an ancestor of no endpoint and no selection var, so the path is not
     * inducing. MUST be false. The contrast with ANC_SELECTION_{S} pins a miss to
     * the Z-ancestor predicate specifically.
     */
    private static Case ancSelectionEmptyControl() {
        Node a = m("A"), b = m("B"), c = m("C"), s = m("S");
        Graph g = new EdgeListGraph(Arrays.asList(a, b, c, s));
        g.addEdge(Edges.bidirectedEdge(a, c));
        g.addEdge(Edges.bidirectedEdge(c, b));
        g.addEdge(Edges.directedEdge(c, s));
        return new Case("ANC_SELECTION_{}_control", g, a, b, empty(), false);
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private static Node m(String name) {
        Node n = new GraphNode(name);
        n.setNodeType(NodeType.MEASURED);
        return n;
    }

    private static Set<Node> empty() {
        return new HashSet<>();
    }

    private static Set<Node> set(Node... ns) {
        return new HashSet<>(Arrays.asList(ns));
    }

    private static final class Case {
        final String name;
        final Graph graph;
        final Node x;
        final Node y;
        final Set<Node> selection;
        final boolean expected;

        Case(String name, Graph graph, Node x, Node y, Set<Node> selection, boolean expected) {
            this.name = name;
            this.graph = graph;
            this.x = x;
            this.y = y;
            this.selection = selection;
            this.expected = expected;
        }
    }
}
