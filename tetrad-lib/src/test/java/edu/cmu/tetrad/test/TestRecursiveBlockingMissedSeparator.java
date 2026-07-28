package edu.cmu.tetrad.test;

import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.RecursiveBlocking;
import edu.cmu.tetrad.search.test.MsepTest;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Isolates a case in which {@link RecursiveBlocking#blockPathsRecursively} reports that no
 * blocking set exists for a pair that IS m-separable, and reports it DEFINITIVELY --
 * {@code indeterminate() == false}, so the negative is a decision rather than an exhausted
 * budget.
 *
 * <p>Provenance: PKE8 at N=8, 2 latent (6 observed), exemplar DAG mask 263299724, latent set
 * {X1, X6}. FcitSl's terminal PAG retains the edge V1 *-* V6, which is absent from G*. The
 * miss is upstream of FcitSl's MAG-selection step: the pair is never identified as removable,
 * so no candidate MAG is ever built and no independence test is ever issued. Both the staged
 * and the closure-cover generators fail identically, which is what points at the separator
 * search rather than at either generator.
 *
 * <p>The graphs below are taken verbatim from that run:
 * <ul>
 *   <li>{@link #trueMag()} is the true MAG G* over the 6 observed variables.</li>
 *   <li>{@link #interimPag()} is the PAG FcitSl holds when it attempts to remove V1 *-* V6.
 *       It is the PAG of the GRaSP start and still carries the spurious edge.</li>
 * </ul>
 *
 * <p>{@link #separatorIsUniqueAndIsV5()} is a sanity check on the fixture: it verifies against
 * the m-separation oracle that {V5} separates V1 and V6 and that NOTHING ELSE does. The
 * uniqueness matters -- there is no alternative separator the search could reach instead, so a
 * null result cannot be excused as "found a different valid set".
 *
 * <p>{@link #recursiveBlockingFindsTheSeparator()} is the failing test.
 *
 * <p>{@link #notFollowedEscapeReachesTheTrueSeparator()} documents the scope of the
 * failure: FcitSl retries RB under subsets of a "not-followed" set to escape exactly this kind
 * of dead end, and RB returns null under all of them. It is written to FAIL while the defect
 * is present.
 */
public class TestRecursiveBlockingMissedSeparator {

    // The first three match FcitSl's own fields at the call site. The trailing int and boolean
    // are reproduced verbatim from that call site; their semantics are not relied on here, only
    // that this test invokes RB exactly as FcitSl does.
    private static final int RECURSIVE_DEPTH = -1;
    private static final int DEPTH = -1;
    private static final int RB_RADIUS = -1;
    private static final int TRAILING_INT_ARG = 1;
    private static final boolean TRAILING_BOOLEAN_ARG = true;

    private static List<Node> nodes() {
        List<Node> nodes = new ArrayList<>();
        for (int i = 1; i <= 6; i++) nodes.add(new GraphNode("V" + i));
        return nodes;
    }

    private static Node get(Graph g, String name) {
        Node n = g.getNode(name);
        assertNotNull("no such node: " + name, n);
        return n;
    }

    /** The true MAG G* over the observed variables. */
    private static Graph trueMag() {
        Graph g = new EdgeListGraph(nodes());
        g.addDirectedEdge(get(g, "V1"), get(g, "V2"));
        g.addDirectedEdge(get(g, "V1"), get(g, "V3"));
        g.addDirectedEdge(get(g, "V1"), get(g, "V4"));
        g.addDirectedEdge(get(g, "V2"), get(g, "V3"));
        g.addDirectedEdge(get(g, "V2"), get(g, "V4"));
        g.addDirectedEdge(get(g, "V3"), get(g, "V4"));
        g.addDirectedEdge(get(g, "V5"), get(g, "V1"));
        g.addDirectedEdge(get(g, "V5"), get(g, "V6"));
        g.addBidirectedEdge(get(g, "V6"), get(g, "V2"));
        g.addDirectedEdge(get(g, "V6"), get(g, "V3"));
        g.addDirectedEdge(get(g, "V6"), get(g, "V4"));
        return g;
    }

    /** The interim PAG FcitSl holds when it attempts to remove V1 *-* V6. */
    private static Graph interimPag() {
        Graph g = new EdgeListGraph(nodes());
        edge(g, "V2", "V1", Endpoint.CIRCLE, Endpoint.CIRCLE);
        edge(g, "V1", "V5", Endpoint.CIRCLE, Endpoint.CIRCLE);
        edge(g, "V4", "V3", Endpoint.CIRCLE, Endpoint.CIRCLE);
        edge(g, "V2", "V6", Endpoint.CIRCLE, Endpoint.ARROW);
        edge(g, "V5", "V6", Endpoint.CIRCLE, Endpoint.ARROW);
        edge(g, "V1", "V6", Endpoint.CIRCLE, Endpoint.ARROW);
        edge(g, "V6", "V3", Endpoint.TAIL, Endpoint.ARROW);
        edge(g, "V6", "V4", Endpoint.TAIL, Endpoint.ARROW);
        edge(g, "V2", "V3", Endpoint.TAIL, Endpoint.ARROW);
        edge(g, "V2", "V4", Endpoint.TAIL, Endpoint.ARROW);
        edge(g, "V1", "V3", Endpoint.TAIL, Endpoint.ARROW);
        edge(g, "V1", "V4", Endpoint.TAIL, Endpoint.ARROW);
        return g;
    }

    /** Adds {@code a *-* b} with the given endpoints AT a and AT b respectively. */
    private static void edge(Graph g, String a, String b, Endpoint atA, Endpoint atB) {
        g.addEdge(new Edge(get(g, a), get(g, b), atA, atB, false));
    }

    /**
     * Fixture sanity check: {V5} m-separates V1 and V6 in G*, and it is the ONLY set that
     * does. Passes today and must keep passing; if it ever fails, the fixture has drifted and
     * the other tests here mean nothing.
     */
    @Test
    public void separatorIsUniqueAndIsV5() throws InterruptedException {
        Graph mag = trueMag();
        MsepTest oracle = new MsepTest(mag);

        Node x = get(mag, "V1");
        Node y = get(mag, "V6");
        assertFalse("fixture: V1 and V6 must be nonadjacent in G*", mag.isAdjacentTo(x, y));

        List<Node> rest = new ArrayList<>(mag.getNodes());
        rest.remove(x);
        rest.remove(y);

        List<Set<Node>> separators = new ArrayList<>();
        for (int bits = 0; bits < (1 << rest.size()); bits++) {
            Set<Node> z = new LinkedHashSet<>();
            for (int i = 0; i < rest.size(); i++) {
                if ((bits & (1 << i)) != 0) z.add(rest.get(i));
            }
            if (oracle.checkIndependence(x, y, z).isIndependent()) separators.add(z);
        }

        assertEquals("expected exactly one separating set for V1, V6; got " + separators,
                1, separators.size());
        assertEquals("the unique separator should be {V5}",
                Collections.singleton(get(mag, "V5")), separators.get(0));
    }

    /**
     * THE DEFECT. RecursiveBlocking is asked for a blocking set for V1, V6 against the interim
     * PAG, exactly as FcitSl asks for it. A separator exists ({V5}, uniquely -- see
     * {@link #separatorIsUniqueAndIsV5()}), but RB returns {@code blockingSet() == null} with
     * {@code indeterminate() == false}: a definitive negative, not a budget exhaustion.
     *
     * <p>Downstream consequence in FcitSl: the pair is declared inseparable without a single
     * independence test being issued, so the spurious edge survives into the terminal PAG.
     */
    @Test
    public void recursiveBlockingFindsTheSeparator() throws InterruptedException {
        Graph pag = interimPag();
        Node x = get(pag, "V1");
        Node y = get(pag, "V6");

        RecursiveBlocking.BlockingResult result = RecursiveBlocking.blockPathsRecursively(
                pag, x, y, Collections.emptySet(), Collections.emptySet(),
                RECURSIVE_DEPTH, DEPTH, RB_RADIUS, TRAILING_INT_ARG, TRAILING_BOOLEAN_ARG);

        assertFalse("RB reported indeterminate; expected a decided verdict", result.indeterminate());
        assertNotNull("RB returned no blocking set for V1, V6, but {V5} separates them "
                        + "(and is the unique separator). Verdict was DECIDED, not indeterminate.",
                result.blockingSet());
    }

    /**
     * The escape mechanism, post-fix. Under forced-arrowhead (definite-status)
     * semantics, the phantom chain V1 *-&gt; V3 o-o V4 &lt;-* V6 is recognized as
     * closed, so RB succeeds under every not-followed subset, returning the
     * graphical complement of {V2, V5}. Two points on that lattice are pinned:
     *
     * <ul>
     *   <li>Empty notFollowed: {V2, V5}. Both circle chains (V1 o-o V2 o-&gt; V6,
     *       V1 o-o V5 o-&gt; V6) must be conditioned; V3, V4 stay out. This set
     *       fails the data test (V2 is a collider in G* via V2 &lt;-&gt; V6), which
     *       is what triggers the retry.</li>
     *   <li>notFollowed = {V2}: {V5} exactly -- the retry that reaches the
     *       unique true separator (see {@link #separatorIsUniqueAndIsV5()}) and
     *       lets FcitSl remove the spurious edge.</li>
     * </ul>
     *
     * <p>Both verdicts must be decided, not indeterminate.</p>
     */
    @Test
    public void notFollowedEscapeReachesTheTrueSeparator() throws InterruptedException {
        Graph pag = interimPag();
        Node x = get(pag, "V1");
        Node y = get(pag, "V6");

        RecursiveBlocking.BlockingResult unconstrained = RecursiveBlocking.blockPathsRecursively(
                pag, x, y, Collections.emptySet(), Collections.emptySet(),
                RECURSIVE_DEPTH, DEPTH, RB_RADIUS, TRAILING_INT_ARG, TRAILING_BOOLEAN_ARG);

        assertFalse("unconstrained verdict should be decided", unconstrained.indeterminate());
        assertEquals("unconstrained RB should condition exactly the two circle chains",
                new HashSet<>(Arrays.asList(get(pag, "V2"), get(pag, "V5"))),
                unconstrained.blockingSet());

        RecursiveBlocking.BlockingResult escaped = RecursiveBlocking.blockPathsRecursively(
                pag, x, y, Collections.emptySet(),
                Collections.singleton(get(pag, "V2")),
                RECURSIVE_DEPTH, DEPTH, RB_RADIUS, TRAILING_INT_ARG, TRAILING_BOOLEAN_ARG);

        assertFalse("escaped verdict should be decided", escaped.indeterminate());
        assertEquals("notFollowed = {V2} should yield the unique true separator {V5}",
                Collections.singleton(get(pag, "V5")), escaped.blockingSet());
    }
}