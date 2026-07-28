///////////////////////////////////////////////////////////////////////////////
// StepLemmaWitnessPke13Test.java                                            //
//                                                                           //
// Independent Tetrad verification that the PKE13 violation model            //
// (positionalKey cc...cc..ta.aaat.aaacat, OBS=6) is NOT a counterexample    //
// to the generalized Meek conjecture / Step Lemma.                          //
//                                                                           //
// Claim verified: let M* be the oracle MAG (Zhang MAG of G*), and let       //
//   H' = M* + V2->V3 + V6->V3                                               //
// (the terminal skeleton of the stalled GraspFci run, with all shared       //
// marks as in M*).  Then H' is a MAG I-map of M*, and BOTH spurious edges  //
// are individually deletable from H' with the Step Lemma's two prongs:      //
//   (A) the deleted graph is a legal MAG;                                   //
//   (B) its independence model is contained in I(M*).                       //
// Deleting both lands exactly on M* (model equality).  Hence single-edge,   //
// Markov-maintaining moves reach the true class from this state, exactly    //
// as Conjecture 2.1 requires; the GraspFci stall is a property of its       //
// fixed-representative commit gate, not of MAG space.                       //
//                                                                           //
// I-map checks are brute force: all 15 pairs x all 2^4 conditioning sets    //
// over the remaining observed variables, via MsepTest on each graph.        //
//                                                                           //
// @author josephramsey (test scaffolding by Claude)                        //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search.harness;

import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphNode;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.test.MsepTest;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertTrue;

public final class StepLemmaWitnessPke13Test {

    // ────────────────────────────────────────────────────────────────────────
    // Graph builders
    // ────────────────────────────────────────────────────────────────────────

    private static List<Node> nodes() {
        List<Node> ns = new ArrayList<>();
        for (int i = 1; i <= 6; i++) ns.add(new GraphNode("V" + i));
        return ns;
    }

    private static Node n(List<Node> ns, String name) {
        for (Node x : ns) if (x.getName().equals(name)) return x;
        throw new IllegalArgumentException(name);
    }

    /** The oracle MAG M* (Zhang MAG of G*) from the PKE13 violation entry. */
    private static Graph mStar(List<Node> ns) {
        Graph g = new EdgeListGraph(ns);
        g.addDirectedEdge(n(ns, "V1"), n(ns, "V2"));
        g.addDirectedEdge(n(ns, "V1"), n(ns, "V6"));
        g.addDirectedEdge(n(ns, "V2"), n(ns, "V5"));
        g.addBidirectedEdge(n(ns, "V4"), n(ns, "V3"));
        g.addDirectedEdge(n(ns, "V5"), n(ns, "V3"));
        g.addBidirectedEdge(n(ns, "V5"), n(ns, "V4"));
        g.addDirectedEdge(n(ns, "V6"), n(ns, "V4"));
        g.addDirectedEdge(n(ns, "V6"), n(ns, "V5"));
        return g;
    }

    /** The hosting representative H' = M* + V2->V3 + V6->V3. */
    private static Graph hPrime(List<Node> ns) {
        Graph g = mStar(ns);
        g.addDirectedEdge(n(ns, "V2"), n(ns, "V3"));
        g.addDirectedEdge(n(ns, "V6"), n(ns, "V3"));
        return g;
    }

    // ────────────────────────────────────────────────────────────────────────
    // Model comparison: brute-force I-map / model-equality via MsepTest
    // ────────────────────────────────────────────────────────────────────────

    /** Asserts I(sub) SUBSETEQ I(sup): every m-separation entailed by sub holds in sup. */
    private static void assertIMap(String label, Graph sub, Graph sup) throws Exception {
        // Fresh copies: some Tetrad constructions mutate node types internally.
        MsepTest tSub = new MsepTest(new EdgeListGraph(sub));
        MsepTest tSup = new MsepTest(new EdgeListGraph(sup));
        List<Node> vs = tSub.getVariables();
        List<Node> vsSup = tSup.getVariables();

        for (int i = 0; i < vs.size(); i++) {
            for (int j = i + 1; j < vs.size(); j++) {
                Node x = vs.get(i), y = vs.get(j);
                Node xs = byName(vsSup, x), ys = byName(vsSup, y);
                List<Node> others = new ArrayList<>(vs);
                others.remove(x);
                others.remove(y);
                int m = others.size();
                for (int mask = 0; mask < (1 << m); mask++) {
                    Set<Node> z = new HashSet<>();
                    Set<Node> zSup = new HashSet<>();
                    for (int q = 0; q < m; q++) {
                        if ((mask & (1 << q)) != 0) {
                            z.add(others.get(q));
                            zSup.add(byName(vsSup, others.get(q)));
                        }
                    }
                    boolean inSub = tSub.checkIndependence(x, y, z).isIndependent();
                    if (inSub) {
                        boolean inSup = tSup.checkIndependence(xs, ys, zSup).isIndependent();
                        assertTrue(label + ": " + x + " _||_ " + y + " | " + z
                                        + " holds in the candidate but NOT in M* -- prong (B) fails here",
                                inSup);
                    }
                }
            }
        }
    }

    /** Asserts I(a) == I(b) by checking containment both ways. */
    private static void assertSameModel(String label, Graph a, Graph b) throws Exception {
        assertIMap(label + " (forward)", a, b);
        assertIMap(label + " (reverse)", b, a);
    }

    private static Node byName(List<Node> vs, Node x) {
        for (Node v : vs) if (v.getName().equals(x.getName())) return v;
        throw new IllegalArgumentException(x.getName());
    }

    private static Graph minus(Graph g, List<Node> ns, String a, String b) {
        Graph out = new EdgeListGraph(g);
        out.removeEdge(out.getNode(a), out.getNode(b));
        return out;
    }

    // ────────────────────────────────────────────────────────────────────────
    // Tests
    // ────────────────────────────────────────────────────────────────────────

    /** Sanity: M* itself is a legal MAG. */
    @Test
    public void testMStarIsLegalMag() {
        List<Node> ns = nodes();
        assertTrue("M* should be a legal MAG", mStar(ns).paths().isLegalMag());
    }

    /** H' is a legal MAG and an I-map of M*: the Meek hypothesis holds at this state. */
    @Test
    public void testHPrimeIsLegalMagIMap() throws Exception {
        List<Node> ns = nodes();
        Graph hp = hPrime(ns);
        assertTrue("H' = M* + V2->V3 + V6->V3 should be a legal MAG",
                hp.paths().isLegalMag());
        assertIMap("H'", hp, mStar(ns));
    }

    /** Step Lemma witness, order 1: delete V2->V3 first, then V6->V3; both prongs at each step. */
    @Test
    public void testDeleteV2V3ThenV6V3() throws Exception {
        List<Node> ns = nodes();
        Graph mStar = mStar(ns);

        Graph h1 = minus(hPrime(ns), ns, "V2", "V3");
        assertTrue("H' - (V2->V3) should be a legal MAG [prong A]", h1.paths().isLegalMag());
        assertIMap("H' - (V2->V3) [prong B]", h1, mStar);

        Graph h2 = minus(h1, ns, "V6", "V3");
        assertTrue("H' - both should be a legal MAG [prong A]", h2.paths().isLegalMag());
        assertSameModel("H' - both vs M*", h2, mStar);
    }

    /** Step Lemma witness, order 2: delete V6->V3 first, then V2->V3; both prongs at each step. */
    @Test
    public void testDeleteV6V3ThenV2V3() throws Exception {
        List<Node> ns = nodes();
        Graph mStar = mStar(ns);

        Graph h1 = minus(hPrime(ns), ns, "V6", "V3");
        assertTrue("H' - (V6->V3) should be a legal MAG [prong A]", h1.paths().isLegalMag());
        assertIMap("H' - (V6->V3) [prong B]", h1, mStar);

        Graph h2 = minus(h1, ns, "V2", "V3");
        assertTrue("H' - both should be a legal MAG [prong A]", h2.paths().isLegalMag());
        assertSameModel("H' - both vs M*", h2, mStar);
    }
}
