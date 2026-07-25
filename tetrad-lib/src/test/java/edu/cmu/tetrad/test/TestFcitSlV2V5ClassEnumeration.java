package edu.cmu.tetrad.test;

import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.test.MsepTest;
import edu.cmu.tetrad.search.utils.MagToPag;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * INDEPENDENT class-enumeration audit of the PKE11 V2--V5 removal, decoupled from FcitSl's
 * own generator (no seedMags / LegEnumerator / forkFlips / stampLegColliders).
 * <p>
 * CORRECTED from the first cut: the interim class is now enumerated over the fixed 13-edge
 * SKELETON with every per-edge orientation (-->, <--, <->), NOT by realizing the interim PAG's
 * circle slots. The PAG-realization approach could not emit class members that differ from the
 * PAG on an OVER-COMMITTED mark (e.g. the interim PAG shows V3-->V6 directed while G* has
 * V6<->V3), so it silently dropped the trivial host and mis-reported "0 hosts". Enumerating the
 * raw skeleton and filtering by {@code pag(H).equals(interimPag)} trusts none of the PAG's marks
 * and is immune to that.
 * <p>
 * Because it exhausts the skeleton, this is a strict UPPER BOUND on within-class reach: if it
 * finds no in-class host, the boundary is proved; if it finds one, FcitSl's within-class failure
 * is a REACH GAP and the witness is a representative the generator never reached.
 * <p>
 * Definitions match FcitSl: class identity = {@code MagToPag(H).convert(false, true)} equality;
 * "hosts the removal" = H - (V2--V5) is a legal MAG that does not OVER-separate V2,V5 (for every
 * Z, H-f entailing V2 _||_ V5 | Z implies G* does too) -- prong A plus the complete prong B.
 */
public class TestFcitSlV2V5ClassEnumeration {

    private static final boolean EXCLUDE_SELECTION_BIAS = true;

    /** The 13-edge interim skeleton: G*'s 12 edges plus the spurious V2--V5. */
    private static final String[][] SKELETON = {
            {"V2", "V1"}, {"V2", "V4"}, {"V2", "V6"}, {"V3", "V1"}, {"V3", "V4"},
            {"V4", "V1"}, {"V4", "V5"}, {"V5", "V1"}, {"V5", "V3"}, {"V6", "V3"},
            {"V6", "V4"}, {"V6", "V5"}, {"V2", "V5"}
    };

    // ---------------------------------------------------------------- fixture

    private static List<Node> canonicalNodes() {
        return Arrays.asList(new GraphNode("V1"), new GraphNode("V2"), new GraphNode("V3"),
                new GraphNode("V4"), new GraphNode("V5"), new GraphNode("V6"));
    }

    /** Oracle MAG (Zhang MAG of G*) from the PKE11 violation log. Has NO V2--V5 edge. */
    private static Graph oracleMag() {
        Graph g = new EdgeListGraph(canonicalNodes());
        g.addDirectedEdge(g.getNode("V2"), g.getNode("V1"));
        g.addDirectedEdge(g.getNode("V2"), g.getNode("V4"));
        g.addDirectedEdge(g.getNode("V2"), g.getNode("V6"));
        g.addDirectedEdge(g.getNode("V3"), g.getNode("V1"));
        g.addDirectedEdge(g.getNode("V3"), g.getNode("V4"));
        g.addDirectedEdge(g.getNode("V4"), g.getNode("V1"));
        g.addBidirectedEdge(g.getNode("V4"), g.getNode("V5"));
        g.addBidirectedEdge(g.getNode("V5"), g.getNode("V1"));
        g.addBidirectedEdge(g.getNode("V5"), g.getNode("V3"));
        g.addBidirectedEdge(g.getNode("V6"), g.getNode("V3"));
        g.addDirectedEdge(g.getNode("V6"), g.getNode("V4"));
        g.addDirectedEdge(g.getNode("V6"), g.getNode("V5"));
        return g;
    }

    // ----------------------------------------------------------- small helpers

    /** PAG of a MAG; defensively copies so MagToPag can't mutate the caller's graph. */
    private static Graph pag(Graph mag) {
        return new MagToPag(new EdgeListGraph(mag)).convert(false, EXCLUDE_SELECTION_BIAS);
    }

    /**
     * Build a MAG over the canonical nodes from a per-edge orientation vector.
     * state: 0 = n1-->n2, 1 = n1<--n2, 2 = n1<->n2.
     */
    private static Graph realizeSkeleton(List<Node> nodes, int[] state) {
        Map<String, Node> by = new HashMap<>();
        for (Node n : nodes) by.put(n.getName(), n);
        Graph h = new EdgeListGraph(nodes);
        for (int i = 0; i < SKELETON.length; i++) {
            Node a = by.get(SKELETON[i][0]), b = by.get(SKELETON[i][1]);
            Endpoint ea, eb;
            switch (state[i]) {
                case 0 -> { ea = Endpoint.TAIL;  eb = Endpoint.ARROW; }
                case 1 -> { ea = Endpoint.ARROW; eb = Endpoint.TAIL;  }
                default -> { ea = Endpoint.ARROW; eb = Endpoint.ARROW; }
            }
            h.addEdge(new Edge(a, b, ea, eb));
        }
        return h;
    }

    private static boolean mSep(MsepTest test, Graph g, String a, String b, Set<String> zNames) {
        Set<Node> z = new HashSet<>();
        for (String n : zNames) z.add(g.getNode(n));
        return test.checkIndependence(g.getNode(a), g.getNode(b), z).isIndependent();
    }

    private static List<Set<String>> conditioningSets() {
        String[] rest = {"V1", "V3", "V4", "V6"};
        List<Set<String>> out = new ArrayList<>();
        for (int m = 0; m < (1 << rest.length); m++) {
            Set<String> z = new TreeSet<>();
            for (int i = 0; i < rest.length; i++) if ((m & (1 << i)) != 0) z.add(rest[i]);
            out.add(z);
        }
        return out;
    }

    /** First Z on which hMinusF over-separates V2,V5 relative to G*, or null if it is a host. */
    private static Set<String> overSeparatingZ(Graph hMinusF, MsepTest gStarTest, Graph gStar) {
        MsepTest hTest = new MsepTest(hMinusF);
        for (Set<String> z : conditioningSets()) {
            if (mSep(hTest, hMinusF, "V2", "V5", z) && !mSep(gStarTest, gStar, "V2", "V5", z)) {
                return z;
            }
        }
        return null;
    }

    /**
     * True iff MAGs a and b encode the SAME m-separation model -- the DEFINITION of Markov
     * equivalence. Used as the class-membership filter INSTEAD of pag(H).equals(interimPag):
     * the seed/flip diagnostic proved MagToPag over-commits here (rendering a class-variant
     * V6&lt;-&gt;V3 as an invariant V3--&gt;V6), so PAG-equality both over-splits classes AND, for
     * this audit, spuriously matched the host to the interim class. This test cannot: it compares
     * the entailed independencies themselves. Compares by node NAME.
     */
    private static boolean sameMsepModel(Graph a, Graph b) {
        MsepTest ta = new MsepTest(a);
        MsepTest tb = new MsepTest(b);
        List<Node> nodes = a.getNodes();
        int n = nodes.size();
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                String u = nodes.get(i).getName(), v = nodes.get(j).getName();
                List<String> rest = new ArrayList<>();
                for (Node nd : nodes) {
                    if (!nd.getName().equals(u) && !nd.getName().equals(v)) rest.add(nd.getName());
                }
                for (int m = 0; m < (1 << rest.size()); m++) {
                    Set<String> z = new HashSet<>();
                    for (int k = 0; k < rest.size(); k++) if ((m & (1 << k)) != 0) z.add(rest.get(k));
                    if (mSep(ta, a, u, v, z) != mSep(tb, b, u, v, z)) return false;
                }
            }
        }
        return true;
    }

    /** True iff there is a directed path from -> ... -> to in g (tail-to-arrowhead chain). */
    private static boolean ancestorDirected(Graph g, Node from, Node to) {
        Deque<Node> q = new ArrayDeque<>();
        Set<Node> seen = new HashSet<>();
        q.add(from); seen.add(from);
        while (!q.isEmpty()) {
            Node cur = q.removeFirst();
            if (!cur.equals(from) && cur.equals(to)) return true;
            for (Node w : g.getAdjacentNodes(cur)) {
                if (seen.contains(w)) continue;
                if (g.getEndpoint(cur, w) == Endpoint.ARROW && g.getEndpoint(w, cur) == Endpoint.TAIL) {
                    seen.add(w); q.addLast(w);
                }
            }
        }
        return false;
    }

    // -------------------------------------------------------- legality report

    /**
     * Diagnostic (not a hard forced-orientation claim): reports {@code isLegalMag} for each way
     * of adding V2--V5 to G*, alongside an INDEPENDENT almost-directed-cycle test. A divergence
     * -- e.g. isLegalMag accepting V2<->V5 though V2 in An(V5) makes it an almost-directed cycle
     * -- is itself a finding about the legality gate the generator relies on, so it is printed
     * loudly rather than asserted away. Only V2-->V5 legal is asserted (the robust part).
     */
    @Test
    public void reportAddedEdgeLegality() {
        String[] labels = {"V2 --> V5", "V5 --> V2", "V2 <-> V5"};
        for (int ori = 0; ori < 3; ori++) {
            Graph g = oracleMag();
            Node a = g.getNode("V2"), b = g.getNode("V5");
            if (ori == 0) g.addDirectedEdge(a, b);
            else if (ori == 1) g.addDirectedEdge(b, a);
            else g.addBidirectedEdge(a, b);

            boolean legal = g.paths().isLegalMag();
            boolean cycle;   // independent illegality witness
            if (ori == 2) cycle = ancestorDirected(g, a, b) || ancestorDirected(g, b, a);
            else cycle = ori == 0 ? ancestorDirected(g, b, a) : ancestorDirected(g, a, b);

            System.out.printf("%-10s isLegalMag=%-5s independentCycleWitness=%-5s%s%n",
                    labels[ori], legal, cycle,
                    (legal && cycle) ? "   <-- WARNING: isLegalMag accepts an (almost-)directed cycle" : "");
        }

        Graph forced = oracleMag();
        forced.addDirectedEdge(forced.getNode("V2"), forced.getNode("V5"));
        assertTrue("V2 --> V5 must be a legal MAG", forced.paths().isLegalMag());
    }

    // -------------------------------------------------------------- the audit

    /**
     * Single-pass audit over the 13-edge skeleton (3^13 orientations). Computes, trustworthily:
     * the interim-class host count, the over-separating Z's of the non-hosts, and the set of
     * hosting classes -- then prints a verdict.
     */
    @Test
    public void auditV2V5Removal() {
        List<Node> nodes = canonicalNodes();
        Graph gStar = oracleMag();
        MsepTest gStarTest = new MsepTest(gStar);
        Graph truePag = pag(gStar);

        Graph interimMag = oracleMag();
        interimMag.addDirectedEdge(interimMag.getNode("V2"), interimMag.getNode("V5"));
        assertTrue(interimMag.paths().isLegalMag());
        Graph interimPag = pag(interimMag);

        int inClass = 0, legalAfterDelete = 0, hosts = 0;
        Set<String> distinctOverSepZ = new TreeSet<>();
        Set<String> hostingClasses = new LinkedHashSet<>();
        String interimHostWitness = null;

        long total = 1;
        for (int i = 0; i < SKELETON.length; i++) total *= 3;

        int[] state = new int[SKELETON.length];
        for (long k = 0; k < total; k++) {
            long t = k;
            for (int i = 0; i < SKELETON.length; i++) { state[i] = (int) (t % 3); t /= 3; }

            Graph h = realizeSkeleton(nodes, state);
            if (!h.paths().isLegalMag()) continue;

            Graph hMinusF = new EdgeListGraph(h);
            hMinusF.removeEdge(hMinusF.getNode("V2"), hMinusF.getNode("V5"));
            boolean deletesLegal = hMinusF.paths().isLegalMag();
            boolean hostable = deletesLegal && overSeparatingZ(hMinusF, gStarTest, gStar) == null;

            if (hostable) hostingClasses.add(pag(h).toString());

            if (sameMsepModel(h, interimMag)) {   // definitional membership, not MagToPag-equality
                inClass++;
                if (deletesLegal) {
                    legalAfterDelete++;
                    if (hostable) {
                        hosts++;
                        if (interimHostWitness == null) interimHostWitness = h.toString();
                    } else {
                        Set<String> bad = overSeparatingZ(hMinusF, gStarTest, gStar);
                        if (bad != null) distinctOverSepZ.add(bad.toString());
                    }
                }
            }

            if ((k & 0x3FFFF) == 0) System.out.println("  ... scanned " + k + " / " + total);
        }

        // interim-class hosting is now the m-sep in-class host count, NOT MagToPag PAG identity.
        boolean interimHosts = hosts > 0;

        System.out.println("=== interim PAG (class identity) ===");
        System.out.println(interimPag);
        System.out.println("=== within-class enumeration (13-skeleton, m-sep membership) ===");
        System.out.println("in-class legal MAGs (m-sep)    : " + inClass);
        System.out.println("  ...legal after deleting V2-V5: " + legalAfterDelete);
        System.out.println("  ...that HOST (no over-sep)   : " + hosts);
        System.out.println("distinct over-separating Z's   : " + distinctOverSepZ);
        System.out.println("hosting PAG-classes (MagToPag, may over-split): " + hostingClasses.size());
        System.out.println("interim class hosts (m-sep)?   : " + interimHosts);

        assertTrue("interim class enumeration empty -- fixture/skeleton mismatch", inClass > 0);
        assertFalse("expected at least one hosting class over the skeleton", hostingClasses.isEmpty());

        System.out.println();
        if (hosts > 0 || interimHosts) {
            System.out.println("VERDICT: within-class host EXISTS (" + hosts + " in-class host MAG(s)). "
                    + "FcitSl's within-class generator has a REACH GAP -- the escape=false failure is a "
                    + "search-reach bug, not a class boundary.");
            System.out.println("---- an in-class host the generator failed to reach ----");
            System.out.println(interimHostWitness);
        } else {
            System.out.println("VERDICT: NO within-class host. Class boundary PROVED for this "
                    + "configuration; escape is load-bearing.");
        }
    }
}
