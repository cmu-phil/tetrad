package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.score.GraphScore;
import edu.cmu.tetrad.search.test.MsepTest;
import edu.cmu.tetrad.search.utils.PagLegalityCheck;
import edu.cmu.tetrad.util.SublistGenerator;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Case 3. The surviving spurious edge is V3--V6 (terminal shows V3 &lt;-&gt; V6; absent in G*).
 * Second symptom: G* has V6 o-&gt; V2, terminal has V2 o-o V6 -- a decayed mark on a real edge,
 * which is the usual consequence of the spurious edge shielding a triple.
 *
 * WHAT MAKES THIS ONE DIFFERENT from case 2 (V1--V3, no common neighbors): here V3 and V6
 * have THREE common neighbors -- V1 (V3&lt;-&gt;V1, V1--&gt;V6), V2 (V2&lt;-&gt;V3, V6 o-&gt; V2), and V5
 * (V5--&gt;V3, V6&lt;-&gt;V5). So the separator should be reachable by the common-neighbor sweep
 * alone, without the widened addition pool. If it is not found, the mechanism is NOT the
 * no-common-neighbor gap of case 2 and the candidate family is failing for some other reason
 * -- most likely that RB drags in a node the removal loop cannot strip, or that the
 * enumeration confirms a different valid separator first and the reorientation then reverts.
 *
 * Derivation of the expected separator {V1, V5} (verified by testSeparatorsForV3V6, which
 * brute-forces and prints ALL of them rather than trusting this comment):
 *   V3 &lt;-&gt; V1 --&gt; V6      V1 non-collider -- must condition on V1
 *   V3 &lt;-- V5 &lt;-&gt; V6      V5 non-collider -- must condition on V5
 *   V3 &lt;-&gt; V2 &lt;-- V6      V2 COLLIDER -- must NOT condition on V2
 *   V3 &lt;-&gt; V1 &lt;-- V4 --&gt; V5 &lt;-&gt; V6   conditioning V1 opens it at V1, but V4 is an
 *                                     unconditioned non-collider, so it stays blocked
 * V2 is an effect of V1 (V1--&gt;V2), not a descendant issue for the collider, so {V1,V5} leaves
 * the V2 collider closed. The trap is symmetric to earlier cases: a search that greedily
 * conditions on ALL common neighbors picks up V2 and opens the collider path.
 */
public class TestFcitCase3V3V6 {

    private static Node n(Map<String, Node> m, String s) {
        return m.get(s);
    }

    /** True PAG G*. */
    private Graph truePag(Map<String, Node> m) {
        List<Node> nodes = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            Node v = new GraphNode("V" + i);
            m.put(v.getName(), v);
            nodes.add(v);
        }
        Graph g = new EdgeListGraph(nodes);

        g.addDirectedEdge(n(m, "V1"), n(m, "V2"));            // 1. V1 --> V2
        g.addDirectedEdge(n(m, "V1"), n(m, "V6"));            // 2. V1 --> V6
        g.addBidirectedEdge(n(m, "V2"), n(m, "V3"));          // 3. V2 <-> V3
        g.addBidirectedEdge(n(m, "V3"), n(m, "V1"));          // 4. V3 <-> V1
        g.addPartiallyOrientedEdge(n(m, "V4"), n(m, "V1"));   // 5. V4 o-> V1
        g.addPartiallyOrientedEdge(n(m, "V4"), n(m, "V5"));   // 6. V4 o-> V5
        g.addBidirectedEdge(n(m, "V5"), n(m, "V2"));          // 7. V5 <-> V2
        g.addDirectedEdge(n(m, "V5"), n(m, "V3"));            // 8. V5 --> V3
        g.addPartiallyOrientedEdge(n(m, "V6"), n(m, "V2"));   // 9. V6 o-> V2
        g.addBidirectedEdge(n(m, "V6"), n(m, "V5"));          // 10. V6 <-> V5

        System.out.println("=== oracle ===\n" + g);

        return g;
    }

    /**
     * Zhang MAG of G*: the three circles become tails (V4 o-&gt; V1 =&gt; V4 --&gt; V1,
     * V4 o-&gt; V5 =&gt; V4 --&gt; V5, V6 o-&gt; V2 =&gt; V6 --&gt; V2); everything else is already definite.
     * testMagIsAncestral checks the result is acyclic and almost-cycle free.
     */
    private Graph oracleMag(Map<String, Node> m) {
        Graph g = new EdgeListGraph(new ArrayList<>(m.values()));
        g.addDirectedEdge(n(m, "V1"), n(m, "V2"));
        g.addDirectedEdge(n(m, "V1"), n(m, "V6"));
        g.addBidirectedEdge(n(m, "V2"), n(m, "V3"));
        g.addBidirectedEdge(n(m, "V3"), n(m, "V1"));
        g.addDirectedEdge(n(m, "V4"), n(m, "V1"));
        g.addDirectedEdge(n(m, "V4"), n(m, "V5"));
        g.addBidirectedEdge(n(m, "V5"), n(m, "V2"));
        g.addDirectedEdge(n(m, "V5"), n(m, "V3"));
        g.addDirectedEdge(n(m, "V6"), n(m, "V2"));
        g.addBidirectedEdge(n(m, "V6"), n(m, "V5"));
        return g;
    }

    private Set<Set<Node>> skeleton(Graph g) {
        Set<Set<Node>> s = new HashSet<>();
        for (Edge e : g.getEdges()) {
            s.add(new HashSet<>(Arrays.asList(e.getNode1(), e.getNode2())));
        }
        return s;
    }

    // ---------------------------------------------------------------- classpath canary

    @Test
    public void testClasspathCanary() {
        System.out.println("Fcit from: " + Fcit.class
                .getProtectionDomain().getCodeSource().getLocation());
    }

    // ------------------------------------------------- what the separator actually is

    /**
     * Brute-forces every separator of (V3,V6) and prints it. Run this FIRST. It replaces
     * hand-derivation, which has been wrong twice in this series; the printed list is the
     * ground truth the candidate family has to be able to reach.
     */
    @Test
    public void testSeparatorsForV3V6() {
        Map<String, Node> m = new LinkedHashMap<>();
        truePag(m);
        Graph mag = oracleMag(m);
        MsepTest oracle = new MsepTest(mag);

        Node v3 = n(m, "V3"), v6 = n(m, "V6");

        List<Node> others = new ArrayList<>(m.values());
        others.removeAll(Arrays.asList(v3, v6));

        List<Set<Node>> found = new ArrayList<>();
        for (int k = 0; k <= others.size(); k++) {
            SublistGenerator gen = new SublistGenerator(others.size(), k);
            int[] c;
            while ((c = gen.next()) != null) {
                Set<Node> s = new HashSet<>(GraphUtils.asSet(c, others));
                if (oracle.checkIndependence(v3, v6, s).isIndependent()) {
                    System.out.println("SEPARATOR V3,V6 = " + s);
                    found.add(s);
                }
            }
        }

        // Common neighbors -- all three of V1, V2, V5, which is what distinguishes this case.
        List<Node> common = mag.getAdjacentNodes(v3);
        common.retainAll(mag.getAdjacentNodes(v6));
        System.out.println("common neighbours of V3,V6 = " + common);

        assertFalse("V3,V6 must be separable -- if not, V3--V6 is a TRUE edge and the "
                + "violation is an over-dense G*, not a search failure", found.isEmpty());
        assertFalse("V3 _||_ V6 | {} should NOT hold",
                oracle.checkIndependence(v3, v6, new HashSet<>()).isIndependent());

        try {
            RecursiveBlocking.BlockingResult result = RecursiveBlocking.blockPathsRecursively(
                    mag, v3, v6, Set.of(), Set.of(), -1, -1, -1, 1, true,
                    Long.MAX_VALUE);

            System.out.println("RB result = " + result);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /** Sanity on the hand-built MAG: ancestral (acyclic, almost-cycle free). */
    @Test
    public void testMagIsAncestral() {
        Map<String, Node> m = new LinkedHashMap<>();
        truePag(m);
        Graph mag = oracleMag(m);
        for (Edge e : mag.getEdges()) {
            if (Edges.isBidirectedEdge(e)) {
                Node a = e.getNode1(), b = e.getNode2();
                assertFalse("almost-cycle: " + a + " <-> " + b + " with a directed path",
                        mag.paths().existsDirectedPath(a, b)
                                || mag.paths().existsDirectedPath(b, a));
            }
        }
    }

    // ---------------------------------------------------------------- whole search

    @Test
    public void testFcitFromOracle() throws InterruptedException {
        Map<String, Node> m = new LinkedHashMap<>();
        Graph gStar = truePag(m);
        Graph mag = oracleMag(m);

        MsepTest oracle = new MsepTest(mag);
        GraphScore gscore = new GraphScore(mag);

        Fcit fcit = new Fcit(oracle, gscore);
        fcit.setStartWith(Fcit.START_WITH.GRASP);
        fcit.setVerbose(true);
        fcit.setSuperVerbose(true);
        fcit.setKnowledge(new Knowledge());

        Graph out = fcit.search();

        System.out.println("=== terminal ===\n" + out);
        System.out.println("=== foundSepsets ===");
//        fcit.getFoundSepsets().forEach((k, v) -> System.out.println("  " + k + " -> " + v));

        assertEquals("skeleton mismatch: FCIT did not recover adj(G*)",
                skeleton(gStar), skeleton(out));
        assertEquals("terminal is not G*", gStar, out);
    }

    // ---------------------------------------------------------------- reorient primitive

//    /**
//     * The fork, same as the earlier cases. Remove V3--V6 from G*+spurious with the separator
//     * recorded, reorient, check G* + legal. Uses ONLY reorient+legality, not the search.
//     *
//     * PASSES  -&gt; the reorientation is fine; the search never recorded a separator for
//     *            (V3,V6), so the fault is in proposeAndConfirm's candidate family.
//     * FAILS   -&gt; the reorientation cannot rebuild G* from the true skeleton, same family as
//     *            the evidence-backed R0/R4 fix; check the provenance counts.
//     *
//     * The recorded separator is read from the brute-force search so this test cannot go stale
//     * against a wrong hand-derivation.
//     */
//    @Test
//    public void testReorientPrimitiveRecoversGStar() throws Exception {
//        Map<String, Node> m = new LinkedHashMap<>();
//        Graph gStar = truePag(m);
//        Graph mag = oracleMag(m);
//        MsepTest oracle = new MsepTest(mag);
//
//        Node v3 = n(m, "V3"), v6 = n(m, "V6");
//
//        // Smallest separator of (V3,V6), computed not assumed.
//        Set<Node> sep = null;
//        List<Node> others = new ArrayList<>(m.values());
//        others.removeAll(Arrays.asList(v3, v6));
//        outer:
//        for (int k = 0; k <= others.size(); k++) {
//            SublistGenerator gen = new SublistGenerator(others.size(), k);
//            int[] c;
//            while ((c = gen.next()) != null) {
//                Set<Node> s = new HashSet<>(GraphUtils.asSet(c, others));
//                if (oracle.checkIndependence(v3, v6, s).isIndependent()) {
//                    sep = s;
//                    break outer;
//                }
//            }
//        }
//        assertNotNull("no separator for (V3,V6) exists", sep);
//        System.out.println("using Sep(V3,V6) = " + sep);
//
//        // interim = G* + spurious V3 <-> V6
//        Graph interim = new EdgeListGraph(gStar);
//        interim.addBidirectedEdge(v3, v6);
//
//        GraphScore gscore = new GraphScore(mag);
//        Fcit fcit = new Fcit(oracle, gscore);
//        fcit.setStartWith(Fcit.START_WITH.GRASP);
//        fcit.setVerbose(true);
//        fcit.setSuperVerbose(true);
//
//        java.lang.reflect.Field pagF = Fcit.class.getDeclaredField("pag");
//        pagF.setAccessible(true);
//        pagF.set(fcit, interim);
//
//        java.lang.reflect.Method noteIC = Fcit.class.getDeclaredMethod(
//                "noteInitialColliders", List.class, Graph.class);
//        noteIC.setAccessible(true);
//        @SuppressWarnings("unchecked")
//        Set<Triple> ic = (Set<Triple>) noteIC.invoke(null, interim.getNodes(), interim);
//        java.lang.reflect.Field icF = Fcit.class.getDeclaredField("initialColliders");
//        icF.setAccessible(true);
//        icF.set(fcit, ic);
//
//        java.lang.reflect.Field fsF = Fcit.class.getDeclaredField("foundSepsets");
//        fsF.setAccessible(true);
//        @SuppressWarnings("unchecked")
//        Map<Set<Node>, Set<Node>> found = (Map<Set<Node>, Set<Node>>) fsF.get(fcit);
//        found.put(new HashSet<>(Arrays.asList(v3, v6)), sep);
//
//        java.lang.reflect.Field seedF = Fcit.class.getDeclaredField("seedGraph");
//        seedF.setAccessible(true);
//        seedF.set(fcit, new EdgeListGraph(interim));
//
//        interim.removeEdge(interim.getEdge(v3, v6));
//        java.lang.reflect.Method reorient = Fcit.class.getDeclaredMethod(
//                "reorientFromScratch", boolean.class);
//        reorient.setAccessible(true);
//        reorient.invoke(fcit, false);
//
//        Graph out = (Graph) pagF.get(fcit);
//        System.out.println("=== reoriented ===\n" + out);
//
//        PagLegalityCheck.LegalPagRet legal =
//                PagLegalityCheck.isLegalPag(out, new LinkedHashSet<>());
//        System.out.println("legal? " + legal.isLegalPag()
//                + (legal.isLegalPag() ? "" : "  reason=" + legal.getReason()));
//
//        assertTrue("reorientation at adj(G*) rejected -- contradicts prop:trueskel-markov: "
//                + legal.getReason(), legal.isLegalPag());
//        assertEquals("reorientation at adj(G*) did not reconstruct G*", gStar, out);
//    }
}
