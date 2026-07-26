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

    // ------------------------------------------------- host characterization

    /**
     * Orientation of skeleton edge (a,b) in g: 0 = a--&gt;b, 1 = a&lt;--b, 2 = a&lt;-&gt;b,
     * -1 = absent/other.
     */
    private static int orientationOf(Graph g, String a, String b) {
        Node na = g.getNode(a), nb = g.getNode(b);
        Edge e = g.getEdge(na, nb);
        if (e == null) return -1;
        Endpoint atA = g.getEndpoint(nb, na);   // mark at a
        Endpoint atB = g.getEndpoint(na, nb);   // mark at b
        if (atA == Endpoint.TAIL && atB == Endpoint.ARROW) return 0;
        if (atA == Endpoint.ARROW && atB == Endpoint.TAIL) return 1;
        if (atA == Endpoint.ARROW && atB == Endpoint.ARROW) return 2;
        return -1;
    }

    private static int bidirectedCount(Graph g) {
        int n = 0;
        for (Edge e : g.getEdges()) if (Edges.isBidirectedEdge(e)) n++;
        return n;
    }

    private static String orientationLabel(int o, String a, String b) {
        return switch (o) {
            case 0 -> a + " --> " + b;
            case 1 -> a + " <-- " + b;
            case 2 -> a + " <-> " + b;
            default -> a + "  ?  " + b;
        };
    }

    /** Membership predicate, in ONE place. MagToPag PAG-equality is cheap and (post
     *  legality fix) agrees with {@link #sameMsepModel} here; swap the body to
     *  {@code sameMsepModel(h, interimMag)} if you ever need the definitional test. */
    private static boolean inInterimClass(Graph h, Graph interimPag, Graph interimMag) {
        return pag(h).equals(interimPag);
    }

    /**
     * Characterizes EVERY in-class host of the V2--V5 removal, to answer the question the
     * reach gap poses: what do the hosts have that the generator's candidates lack?
     * <p>
     * Prints, for the full host set:
     * <ul>
     * <li>each host MAG, with its bidirected-edge count;</li>
     * <li>the bidirected-count distribution -- compare against the generator's enumerated
     *     closure, which topped out at 1-2 bidirected edges;</li>
     * <li>a per-skeleton-edge orientation profile: which marks are INVARIANT across all
     *     hosts (necessary structure any generator must be able to produce) versus which
     *     vary (free);</li>
     * <li>the edit distance from the Zhang seed the generator starts its walk at -- i.e.
     *     how many single-edge reorientations away the NEAREST host is. If that minimum
     *     exceeds what the fork-flip move families can compose within the budget, the
     *     reach gap is quantified, not just asserted.</li>
     * </ul>
     * Diagnostic only: asserts just that hosts exist.
     */
    @Test
    public void characterizeInClassHosts() {
        List<Node> nodes = canonicalNodes();
        Graph gStar = oracleMag();
        MsepTest gStarTest = new MsepTest(gStar);

        Graph interimMag = oracleMag();
        interimMag.addDirectedEdge(interimMag.getNode("V2"), interimMag.getNode("V5"));
        Graph interimPag = pag(interimMag);

        // The seed the generator's walk starts from.
        Graph seed = GraphTransforms.zhangMagFromPag(interimPag);

        List<Graph> hosts = new ArrayList<>();

        long total = 1;
        for (int i = 0; i < SKELETON.length; i++) total *= 3;

        int[] state = new int[SKELETON.length];
        for (long k = 0; k < total; k++) {
            long t = k;
            for (int i = 0; i < SKELETON.length; i++) { state[i] = (int) (t % 3); t /= 3; }

            Graph h = realizeSkeleton(nodes, state);
            if (!h.paths().isLegalMag()) continue;
            if (!inInterimClass(h, interimPag, interimMag)) continue;

            Graph hMinusF = new EdgeListGraph(h);
            hMinusF.removeEdge(hMinusF.getNode("V2"), hMinusF.getNode("V5"));
            if (!hMinusF.paths().isLegalMag()) continue;
            if (overSeparatingZ(hMinusF, gStarTest, gStar) != null) continue;

            hosts.add(h);
        }

        assertFalse("no in-class hosts found -- fixture or membership mismatch", hosts.isEmpty());

        System.out.println("=== IN-CLASS HOSTS OF THE V2--V5 REMOVAL: " + hosts.size() + " ===");
        System.out.println();
        System.out.println("---- Zhang seed the generator walks from (bi=" + bidirectedCount(seed) + ") ----");
        System.out.println(seed);

        // 1) Every host, with bidirected count and distance from the seed.
        Map<Integer, Integer> biDist = new TreeMap<>();
        Map<Integer, Integer> hopDist = new TreeMap<>();
        int minHops = Integer.MAX_VALUE;
        Graph nearest = null;

        for (int hi = 0; hi < hosts.size(); hi++) {
            Graph h = hosts.get(hi);
            int bi = bidirectedCount(h);
            int hops = 0;
            for (String[] se : SKELETON) {
                if (orientationOf(h, se[0], se[1]) != orientationOf(seed, se[0], se[1])) hops++;
            }
            biDist.merge(bi, 1, Integer::sum);
            hopDist.merge(hops, 1, Integer::sum);
            if (hops < minHops) { minHops = hops; nearest = h; }

            System.out.println("---- host #" + (hi + 1) + "  bi=" + bi + "  edgesDifferingFromSeed=" + hops + " ----");
            System.out.println(h);
        }

        // 2) Distributions.
        System.out.println("=== bidirected-edge count distribution across hosts ===");
        biDist.forEach((bi, n) -> System.out.println("  bi=" + bi + " : " + n + " host(s)"));
        System.out.println("  (generator's enumerated closure topped out at 1-2 bidirected edges)");

        System.out.println("=== distance from Zhang seed (single-edge reorientations) ===");
        hopDist.forEach((hops, n) -> System.out.println("  " + hops + " edge(s) differ : " + n + " host(s)"));
        System.out.println("  NEAREST host is " + minHops + " reorientation(s) from the seed.");

        // 3) Per-edge orientation profile: invariant vs free across the host set.
        System.out.println("=== per-edge orientation profile across all hosts ===");
        List<String> invariant = new ArrayList<>();
        for (String[] se : SKELETON) {
            int[] counts = new int[3];
            for (Graph h : hosts) {
                int o = orientationOf(h, se[0], se[1]);
                if (o >= 0) counts[o]++;
            }
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("  %-10s", se[0] + "--" + se[1]));
            sb.append(" -->:").append(counts[0]);
            sb.append("  <--:").append(counts[1]);
            sb.append("  <->:").append(counts[2]);
            int nonZero = 0, whichOne = -1;
            for (int o = 0; o < 3; o++) if (counts[o] > 0) { nonZero++; whichOne = o; }
            if (nonZero == 1) {
                sb.append("   <== INVARIANT: ").append(orientationLabel(whichOne, se[0], se[1]));
                invariant.add(orientationLabel(whichOne, se[0], se[1])
                        + (orientationOf(seed, se[0], se[1]) == whichOne ? "" : "   [SEED DISAGREES]"));
            }
            System.out.println(sb);
        }

        System.out.println("=== structure EVERY host must have ===");
        invariant.forEach(s -> System.out.println("  " + s));
        System.out.println("Marks tagged [SEED DISAGREES] are exactly what the generator's walk must be");
        System.out.println("able to produce from its seed; any move family that cannot reach them cannot");
        System.out.println("reach any host.");

        System.out.println();
        System.out.println("---- nearest host to the seed (" + minHops + " reorientation(s)) ----");
        System.out.println(nearest);
    }

    // ---------------------------------------- single-mark-change class walk

    /** Canonical key over the fixed skeleton: orientation code per edge. */
    private static String magKey(Graph g) {
        StringBuilder sb = new StringBuilder();
        for (String[] se : SKELETON) sb.append(orientationOf(g, se[0], se[1])).append(',');
        return sb.toString();
    }

    private static Endpoint flip(Endpoint e) {
        if (e == Endpoint.TAIL) return Endpoint.ARROW;
        if (e == Endpoint.ARROW) return Endpoint.TAIL;
        return null;
    }

    /** Copy of g with the (a,b) edge's marks replaced. */
    private static Graph withMarks(Graph g, String a, String b, Endpoint atA, Endpoint atB) {
        Graph h = new EdgeListGraph(g);
        Node na = h.getNode(a), nb = h.getNode(b);
        h.removeEdge(na, nb);
        h.addEdge(new Edge(na, nb, atA, atB));
        return h;
    }

    /**
     * All graphs one SINGLE MARK CHANGE from g: for each edge, flip the mark at one endpoint
     * (tail &lt;-&gt; arrowhead). Note a full reversal i--&gt;j to i&lt;--j is TWO mark changes and
     * is reached through the bidirected intermediate, which is exactly the point -- the walk
     * has to pass through bidirected-richer graphs to reverse anything.
     */
    private static List<Graph> markChangeNeighbors(Graph g) {
        List<Graph> out = new ArrayList<>();
        for (String[] se : SKELETON) {
            Node a = g.getNode(se[0]), b = g.getNode(se[1]);
            if (g.getEdge(a, b) == null) continue;
            Endpoint atA = g.getEndpoint(b, a);
            Endpoint atB = g.getEndpoint(a, b);
            Endpoint fA = flip(atA), fB = flip(atB);
            if (fA != null) out.add(withMarks(g, se[0], se[1], fA, atB));
            if (fB != null) out.add(withMarks(g, se[0], se[1], atA, fB));
        }
        return out;
    }

    /**
     * Tests the fix direction: a BFS over SINGLE MARK CHANGES that stay legal and in-class,
     * started from the Zhang seed the generator walks from.
     * <p>
     * IMPORTANT DESIGN NOTE. Zhang &amp; Spirtes give graphical side conditions for when a single
     * mark change preserves Markov equivalence ("legitimate mark change"). This BFS deliberately
     * does NOT hard-code those conditions -- it proposes every single-mark neighbour and accepts
     * it iff it is a legal MAG AND in the interim class, i.e. it tests equivalence directly.
     * That yields the same neighbourhood the graphical conditions characterize, without me
     * having to get the side conditions right, and it means this test can serve as an oracle
     * for a faster hard-coded implementation later.
     * <p>
     * Reports whether the walk reaches the whole class and all 22 hosts, and at what BFS depth
     * the first host appears -- that depth is the budget any bounded implementation needs.
     */
    @Test
    public void markChangeWalkReachesHosts() {
        List<Node> nodes = canonicalNodes();
        Graph gStar = oracleMag();
        MsepTest gStarTest = new MsepTest(gStar);

        Graph interimMag = oracleMag();
        interimMag.addDirectedEdge(interimMag.getNode("V2"), interimMag.getNode("V5"));
        Graph interimPag = pag(interimMag);

        // --- 1. Brute-force ground truth: the class, and the hosts within it.
        Set<String> classKeys = new HashSet<>();
        Set<String> hostKeys = new HashSet<>();

        long total = 1;
        for (int i = 0; i < SKELETON.length; i++) total *= 3;
        int[] state = new int[SKELETON.length];
        for (long k = 0; k < total; k++) {
            long t = k;
            for (int i = 0; i < SKELETON.length; i++) { state[i] = (int) (t % 3); t /= 3; }

            Graph h = realizeSkeleton(nodes, state);
            if (!h.paths().isLegalMag()) continue;
            if (!inInterimClass(h, interimPag, interimMag)) continue;
            classKeys.add(magKey(h));

            Graph hMinusF = new EdgeListGraph(h);
            hMinusF.removeEdge(hMinusF.getNode("V2"), hMinusF.getNode("V5"));
            if (!hMinusF.paths().isLegalMag()) continue;
            if (overSeparatingZ(hMinusF, gStarTest, gStar) != null) continue;
            hostKeys.add(magKey(h));
        }

        System.out.println("=== ground truth (brute force) ===");
        System.out.println("class size : " + classKeys.size());
        System.out.println("hosts      : " + hostKeys.size());

        // --- 2. BFS from the Zhang seed over legal, in-class single mark changes.
        Graph seed = GraphTransforms.zhangMagFromPag(interimPag);
        String seedKey = magKey(seed);

        Map<String, Integer> depth = new LinkedHashMap<>();
        Deque<Graph> queue = new ArrayDeque<>();
        depth.put(seedKey, 0);
        queue.add(seed);

        int firstHostDepth = -1;
        Graph firstHost = null;
        Map<Integer, Integer> depthHist = new TreeMap<>();
        depthHist.merge(0, 1, Integer::sum);

        while (!queue.isEmpty()) {
            Graph cur = queue.removeFirst();
            int d = depth.get(magKey(cur));

            for (Graph nb : markChangeNeighbors(cur)) {
                String key = magKey(nb);
                if (depth.containsKey(key)) continue;
                if (!nb.paths().isLegalMag()) continue;
                if (!inInterimClass(nb, interimPag, interimMag)) continue;

                depth.put(key, d + 1);
                depthHist.merge(d + 1, 1, Integer::sum);
                queue.addLast(nb);

                if (hostKeys.contains(key) && firstHostDepth < 0) {
                    firstHostDepth = d + 1;
                    firstHost = nb;
                }
            }
        }

        Set<String> reached = depth.keySet();
        Set<String> hostsReached = new HashSet<>(hostKeys);
        hostsReached.retainAll(reached);
        Set<String> classMissed = new HashSet<>(classKeys);
        classMissed.removeAll(reached);

        System.out.println("=== single-mark-change BFS from the Zhang seed ===");
        System.out.println("seed is in class      : " + classKeys.contains(seedKey));
        System.out.println("class members reached : " + reached.size() + " / " + classKeys.size());
        System.out.println("class members MISSED  : " + classMissed.size());
        System.out.println("hosts reached         : " + hostsReached.size() + " / " + hostKeys.size());
        System.out.println("first host at BFS depth: " + firstHostDepth
                + "   (mark changes; this is the budget a bounded walk needs)");
        System.out.println("=== BFS depth histogram (class members per depth) ===");
        depthHist.forEach((d, n) -> System.out.println("  depth " + d + " : " + n));

        if (firstHost != null) {
            System.out.println("---- first host reached ----");
            System.out.println(firstHost);
        }

        System.out.println();
        if (hostsReached.size() == hostKeys.size() && classMissed.isEmpty()) {
            System.out.println("VERDICT: single mark changes CONNECT the class and reach every host. "
                    + "Replacing the fork-flip families with this neighbourhood (BFS + visited set, "
                    + "no depth cap below " + firstHostDepth + ") closes the reach gap.");
        } else if (!hostsReached.isEmpty()) {
            System.out.println("VERDICT: PARTIAL -- the walk reaches " + hostsReached.size()
                    + " host(s) but misses " + classMissed.size() + " class member(s). "
                    + "The neighbourhood is useful but not connected here; check the missed members.");
        } else {
            System.out.println("VERDICT: the walk reaches NO host. Either the seed is isolated under "
                    + "single mark changes or the membership filter is wrong -- investigate before "
                    + "building on this.");
        }

        assertTrue("BFS should at least reach the seed", reached.contains(seedKey));
    }
}
