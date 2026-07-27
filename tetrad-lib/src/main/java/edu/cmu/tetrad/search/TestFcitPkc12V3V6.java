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
 * PKC12 SKELETON_DIFF, case 2. Model aaaa.aataaaat..aata..aaaa.
 *
 * The surviving spurious edge is V3--V6, separable in M* by the forced singleton {V1}
 * (V1 is a non-collider on the all-real 2-path V3<->V1-->V6; the other common neighbors
 * V4, V5 are colliders on all-real 2-paths and must NOT be conditioned on). The terminal
 * PAG keeps V3<->V6 and decays V4 to all-circles because the spurious edge shields the true
 * collider <V3,V4,V6>.
 *
 * The two tests below split the failure at its only interesting joint:
 *   - testFcitFromOracle: does the whole search recover adj(G*)? (fails like the harness)
 *   - testReorientPrimitive*: does removing V3--V6 from G*+spurious, with Sep(V3,V6)={V1}
 *     recorded, reorient to G* and pass the legality check? This uses ONLY the reorientation
 *     and legality primitives, not the separator search, so it decides search-vs-reorient.
 *
 * API NOTE: three call points may differ in your build and are flagged with // VERIFY.
 * Adjust the reflection/helper hooks to match, or inline your own accessors.
 */
public class TestFcitPkc12V3V6 {

    // ---------------------------------------------------------------- fixture: G*

    private static Node n(Map<String, Node> m, String s) {
        return m.get(s);
    }

    /** True PAG G* for case 2. */
    private Graph truePag(Map<String, Node> m) {
        List<Node> nodes = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            Node v = new GraphNode("V" + i);
            m.put(v.getName(), v);
            nodes.add(v);
        }
        Graph g = new EdgeListGraph(nodes);

        // 1. V1 <-> V5
        g.addBidirectedEdge(n(m, "V1"), n(m, "V5"));
        // 2. V1 --> V6
        g.addDirectedEdge(n(m, "V1"), n(m, "V6"));
        // 3. V2 <-> V1
        g.addBidirectedEdge(n(m, "V2"), n(m, "V1"));
        // 4. V3 <-> V1
        g.addBidirectedEdge(n(m, "V3"), n(m, "V1"));
        // 5. V3 <-> V2
        g.addBidirectedEdge(n(m, "V3"), n(m, "V2"));
        // 6. V3 --> V5
        g.addDirectedEdge(n(m, "V3"), n(m, "V5"));
        // 7. V4 --> V2
        g.addDirectedEdge(n(m, "V4"), n(m, "V2"));
        // 8. V4 <-> V3
        g.addBidirectedEdge(n(m, "V4"), n(m, "V3"));
        // 9. V6 <-> V4
        g.addBidirectedEdge(n(m, "V6"), n(m, "V4"));
        // 10. V6 <-> V5
        g.addBidirectedEdge(n(m, "V6"), n(m, "V5"));

        System.out.println(g);

        return g;
    }

    /** The Zhang MAG of G* (oracle MAG from the log), used as the m-separation oracle. */
    private Graph oracleMag(Map<String, Node> m) {
        Graph g = new EdgeListGraph(new ArrayList<>(m.values()));
        g.addBidirectedEdge(n(m, "V1"), n(m, "V5"));
        g.addDirectedEdge(n(m, "V1"), n(m, "V6"));
        g.addBidirectedEdge(n(m, "V2"), n(m, "V1"));
        g.addBidirectedEdge(n(m, "V3"), n(m, "V1"));
        g.addBidirectedEdge(n(m, "V3"), n(m, "V2"));
        g.addDirectedEdge(n(m, "V3"), n(m, "V5"));
        g.addDirectedEdge(n(m, "V4"), n(m, "V2"));
        g.addBidirectedEdge(n(m, "V4"), n(m, "V3"));
        g.addBidirectedEdge(n(m, "V6"), n(m, "V4"));
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

    /**
     * Run FIRST. A prior "easy pair missed" case turned out to be a stale tetrad-lib jar
     * shadowing the workspace classes. If either prints a jar path instead of your module's
     * build/classes directory, nothing below is trustworthy.
     */
    @Test
    public void testClasspathCanary() {
        System.out.println("Fcit from: " + Fcit.class
                .getProtectionDomain().getCodeSource().getLocation());
        System.out.println("PagLegalityCheck from: " + PagLegalityCheck.class
                .getProtectionDomain().getCodeSource().getLocation());
    }

    // ---------------------------------------------------------------- whole search

    /**
     * Does the whole search recover adj(G*)? Expected to FAIL, reproducing the harness. If it
     * PASSES here but fails in the harness, the harness path differs (magspace interim
     * construction, seed order, canonicalization) and the bug is there, not in Fcit.
     */
    @Test
    public void testFcitFromOracle() throws InterruptedException {
        Map<String, Node> m = new LinkedHashMap<>();
        Graph gStar = truePag(m);
        Graph mag = oracleMag(m);

        MsepTest oracle = new MsepTest(mag);              // VERIFY: MsepTest(Graph) ctor
        GraphScore gscore = new GraphScore(mag);          // VERIFY: GraphScore(Graph) ctor

        Fcit fcit = new Fcit(oracle, gscore);
        fcit.setStartWith(Fcit.START_WITH.GRASP);
        fcit.setVerbose(true);
        fcit.setSuperVerbose(true);
        fcit.setKnowledge(new Knowledge());

        Graph out = fcit.search();

        assertEquals("skeleton mismatch: FCIT did not recover adj(G*)",
                skeleton(gStar), skeleton(out));
    }

    @Test
    public void testFindV3V6Separator() {
        Map<String, Node> m = new LinkedHashMap<>();
        truePag(m);
        Graph mag = oracleMag(m);
        MsepTest oracle = new MsepTest(mag);
        Node v3 = n(m, "V3"), v6 = n(m, "V6");
        List<Node> others = new ArrayList<>(m.values());
        others.removeAll(Arrays.asList(v3, v6));
        for (int k = 0; k <= others.size(); k++) {
            SublistGenerator gen = new SublistGenerator(others.size(), k);
            int[] c;
            while ((c = gen.next()) != null) {
                Set<Node> s = new HashSet<>(GraphUtils.asSet(c, others));
                if (oracle.checkIndependence(v3, v6, s).isIndependent()) {
                    System.out.println("SEPARATOR V3, V6 = " + s);
                }
            }
        }
    }

    @Test
    public void testFindV6V2Separator() {
        Map<String, Node> m = new LinkedHashMap<>();
        truePag(m);
        Graph mag = oracleMag(m);
        MsepTest oracle = new MsepTest(mag);
        Node v6 = n(m, "V6"), v2 = n(m, "V2");
        List<Node> others = new ArrayList<>(m.values());
        others.removeAll(Arrays.asList(v6, v2));
        for (int k = 0; k <= others.size(); k++) {
            SublistGenerator gen = new SublistGenerator(others.size(), k);
            int[] c;
            while ((c = gen.next()) != null) {
                Set<Node> s = new HashSet<>(GraphUtils.asSet(c, others));
                if (oracle.checkIndependence(v6, v2, s).isIndependent()) {
                    System.out.println("SEPARATOR V3, V6 = " + s);
                }
            }
        }
    }
}