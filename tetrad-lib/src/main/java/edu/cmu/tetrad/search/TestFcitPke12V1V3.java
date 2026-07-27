package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.Fcit;
import edu.cmu.tetrad.search.score.GraphScore;
import edu.cmu.tetrad.search.test.MsepTest;
import edu.cmu.tetrad.search.utils.PagLegalityCheck;
import edu.cmu.tetrad.util.SublistGenerator;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * PKE12 SKELETON_DIFF, model at...aaaacaaa.aaat.aaaaat.
 *
 * The surviving spurious edge is V1--V3 (terminal shows V1 <-> V3; absent in G*). The forced
 * separator is the singleton {V2}: V2 is the ONLY common neighbor of V1,V3, it is a
 * non-collider on the all-real 2-path V1 <-- V2 <-> V3 (tail toward V1, arrow toward V3), so
 * every separator must contain it; the other V1--V3 route V1 <-> V6 <-> V4 <-> V3 is blocked
 * at the colliders V6, V4 as long as they are not conditioned on, and {V2} does not condition
 * them. So {V2} separates and is forced.
 *
 * What makes this case DIFFERENT from the earlier V3--V6 / V2--V6 fixes: the marks that decay
 * are apex arrowheads that R4 sets, not R0 colliders.
 *   G* V2 --> V1   -> terminal V2 o-> V1   (lost tail at V2)
 *   G* V2 <-> V3   -> terminal V2 o-> V3   (lost arrowhead at V2)
 *   G* V5 <-> V2   -> terminal V2 --> V5   (lost arrowhead into V2 from V5)
 *   G* V5 --> V3   -> terminal V3 --> V5   (reversed)
 * V2 ends up a pure source (o-> everything), which is what happens when the arrowheads INTO
 * V2 (from V5 <-> V2, and the V2 <-> V3 apex) never land -- an R4 / discriminating-path
 * symptom, not an R0 collider-stamp symptom.
 *
 * The two tests split the failure:
 *   - testFcitFromOracle: does the whole search recover adj(G*)? (reproduces the harness)
 *   - testReorientPrimitiveRecoversGStar: remove V1--V3 from G*+spurious with Sep(V1,V3)={V2}
 *     recorded, reorient, and check G* + legal. Uses ONLY reorient+legality, not the search.
 *
 * READING THE FORK:
 *   reorient primitive FAILS non-maximal -> reorient/legality path, same family as the V3--V6
 *       fix (evidence-backed R0/R4 not stamping); check provenance R4 counts.
 *   reorient primitive PASSES -> the search never recorded {V2}, OR (the PKE11 precedent) the
 *       DDP with apex V2 is never PRESENTED to R4 in the full run because a stale pre-commit
 *       graph reaches finalOrientation. In that case run testFcitFromOracle with superVerbose
 *       and grep the R4 log for a DDP touching (V1,V3) or apex V2.
 *
 * // VERIFY seams (same as the case-2 test you already resolved): MsepTest(Graph),
 * GraphScore(Graph), noteInitialColliders(List,Graph), reorientFromScratch(boolean),
 * and the private fields pag / initialColliders / foundSepsets / seedGraph.
 */
public class TestFcitPke12V1V3 {

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

        // 1. V2 --> V1
        g.addDirectedEdge(n(m, "V2"), n(m, "V1"));
        // 2. V2 <-> V3
        g.addBidirectedEdge(n(m, "V2"), n(m, "V3"));
        // 3. V2 o-> V4   (circle at V2, arrow at V4)
        g.addPartiallyOrientedEdge(n(m, "V2"), n(m, "V4"));   // VERIFY: o-> helper name
        // 4. V4 <-> V3
        g.addBidirectedEdge(n(m, "V4"), n(m, "V3"));
        // 5. V4 <-> V5
        g.addBidirectedEdge(n(m, "V4"), n(m, "V5"));
        // 6. V5 <-> V2
        g.addBidirectedEdge(n(m, "V5"), n(m, "V2"));
        // 7. V5 --> V3
        g.addDirectedEdge(n(m, "V5"), n(m, "V3"));
        // 8. V6 <-> V1
        g.addBidirectedEdge(n(m, "V6"), n(m, "V1"));
        // 9. V6 <-> V4
        g.addBidirectedEdge(n(m, "V6"), n(m, "V4"));
        // 10. V6 --> V5
        g.addDirectedEdge(n(m, "V6"), n(m, "V5"));

        System.out.println("=== oracle ===\n" + g);

        return g;
    }

    /** The Zhang MAG of G* (oracle MAG from the log), the m-separation oracle. */
    private Graph oracleMag(Map<String, Node> m) {
        Graph g = new EdgeListGraph(new ArrayList<>(m.values()));
        g.addDirectedEdge(n(m, "V2"), n(m, "V1"));
        g.addBidirectedEdge(n(m, "V2"), n(m, "V3"));
        g.addDirectedEdge(n(m, "V2"), n(m, "V4"));
        g.addBidirectedEdge(n(m, "V4"), n(m, "V3"));
        g.addBidirectedEdge(n(m, "V4"), n(m, "V5"));
        g.addBidirectedEdge(n(m, "V5"), n(m, "V2"));
        g.addDirectedEdge(n(m, "V5"), n(m, "V3"));
        g.addBidirectedEdge(n(m, "V6"), n(m, "V1"));
        g.addBidirectedEdge(n(m, "V6"), n(m, "V4"));
        g.addDirectedEdge(n(m, "V6"), n(m, "V5"));
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
        System.out.println("PagLegalityCheck from: " + PagLegalityCheck.class
                .getProtectionDomain().getCodeSource().getLocation());
    }

    // ---------------------------------------------------------------- oracle sanity

//    /**
//     * Confirms the hand-derivation BEFORE any reorientation logic is exercised: {V2} is a
//     * separator, {} is not, and {V2,V6} / {V2,V4} are NOT (conditioning on a collider common
//     * to the V6/V4 route would open it -- but here V6,V4 are not common neighbors of V1,V3,
//     * so this really tests that adding them doesn't accidentally separate via some other path).
//     */
//    @Test
//    public void testSepsetIsForcedSingletonV2() {
//        Map<String, Node> m = new LinkedHashMap<>();
//        truePag(m);
//        Graph mag = oracleMag(m);
//        MsepTest oracle = new MsepTest(mag);
//
//        Node v1 = n(m, "V1"), v3 = n(m, "V3");
//
//        assertTrue("V1 _||_ V3 | {V2} should hold",
//                oracle.checkIndependence(v1, v3,
//                        new HashSet<>(Collections.singletonList(n(m, "V2")))).isIndependent());
//        assertFalse("V1 _||_ V3 | {} should NOT hold",
//                oracle.checkIndependence(v1, v3, new HashSet<>()).isIndependent());
//        assertFalse("V1 _||_ V3 | {V6} should NOT hold (V2 omitted)",
//                oracle.checkIndependence(v1, v3,
//                        new HashSet<>(Collections.singletonList(n(m, "V6")))).isIndependent());
//
//        // V2 is the ONLY common neighbor of V1,V3 in the MAG -- the forced-singleton geometry.
//        List<Node> common = mag.getAdjacentNodes(v1);
//        common.retainAll(mag.getAdjacentNodes(v3));
//        assertEquals("V1,V3 should have exactly one common neighbor (V2)",
//                Collections.singletonList(n(m, "V2")), common);
//    }

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
        fcit.setSuperVerbose(true);        // needed to see R4 DDP-presentation lines
//        fcit.setAuditBlockingSets(true);
        fcit.setKnowledge(new Knowledge());

        Graph out = fcit.search();

        System.out.println("=== terminal ===\n" + out);
        System.out.println("=== foundSepsets ===");
//        fcit.getFoundSepsets().forEach((k, v) -> System.out.println("  " + k + " -> " + v));

        assertEquals("skeleton mismatch: FCIT did not recover adj(G*)",
                skeleton(gStar), skeleton(out));
    }

    // ---------------------------------------------------------------- reorient primitive

//    @Test
//    public void testReorientPrimitiveRecoversGStar() throws Exception {
//        Map<String, Node> m = new LinkedHashMap<>();
//        Graph gStar = truePag(m);
//        Graph mag = oracleMag(m);
//
//        // interim = G* + spurious V1 <-> V3
//        Graph interim = new EdgeListGraph(gStar);
//        interim.addBidirectedEdge(n(m, "V1"), n(m, "V3"));
//
//        MsepTest oracle = new MsepTest(mag);
//        GraphScore gscore = new GraphScore(mag);
//        Fcit fcit = new Fcit(oracle, gscore);
//        fcit.setStartWith(Fcit.START_WITH.GRASP);
//        fcit.setVerbose(true);
//        fcit.setSuperVerbose(true);
//
//        Set<Node> justV2 = new HashSet<>(Collections.singletonList(n(m, "V2")));
//        assertTrue("oracle: V1 _||_ V3 | {V2} should hold",
//                oracle.checkIndependence(n(m, "V1"), n(m, "V3"), justV2).isIndependent());
//
//        // --- reflection drive of the private primitive ---
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
//        found.put(new HashSet<>(Arrays.asList(n(m, "V1"), n(m, "V3"))), justV2);
//
//        // seedGraph feeds the strategy's CPDAG-backed R0 branch; seed = interim (pre-deletion).
//        java.lang.reflect.Field seedF = Fcit.class.getDeclaredField("seedGraph");
//        seedF.setAccessible(true);
//        seedF.set(fcit, new EdgeListGraph(interim));
//
//        // remove spurious edge, reorientFromScratch(false)
//        interim.removeEdge(interim.getEdge(n(m, "V1"), n(m, "V3")));
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

    @Test
    public void testFindV1V3Separator() {
        Map<String, Node> m = new LinkedHashMap<>();
        truePag(m);
        Graph mag = oracleMag(m);
        MsepTest oracle = new MsepTest(mag);
        Node v1 = n(m, "V1"), v3 = n(m, "V3");
        List<Node> others = new ArrayList<>(m.values());
        others.removeAll(Arrays.asList(v1, v3));
        for (int k = 0; k <= others.size(); k++) {
            SublistGenerator gen = new SublistGenerator(others.size(), k);
            int[] c;
            while ((c = gen.next()) != null) {
                Set<Node> s = new HashSet<>(GraphUtils.asSet(c, others));
                if (oracle.checkIndependence(v1, v3, s).isIndependent()) {
                    System.out.println("SEPARATOR V1,V3 = " + s);
                }
            }
        }
    }
}
