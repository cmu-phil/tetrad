package edu.cmu.tetrad.test;

import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.FcitSl;
import edu.cmu.tetrad.search.score.GraphScore;
import edu.cmu.tetrad.search.test.MsepTest;
import edu.cmu.tetrad.search.utils.MagToPag;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * Reconstruction of the PKE11 SKELETON_DIFF violation for FCIT-SL, isolated as a
 * playable unit test.
 * <p>
 * Provenance: PKE11 run, model {@code atatataa..ca.cacaaaaaaaacat},
 * config {@code mode=magspace escape=false zMax=5 forkFlips=2 depth=-1 recursiveDepth=-1}.
 * <p>
 * The oracle is m-separation in the Zhang MAG of G*. With class escape disabled
 * (Step-Lemma-pure mode), FCIT-SL fails to remove the spurious edge V2--V5 and the
 * terminal PAG has one extra adjacency. With escape enabled, the skeleton is recovered.
 * <p>
 * Diagnosis under test:
 * <ul>
 * <li>The separating set for (V2, V5) is FORCED and UNIQUE over subsets of the
 *     remaining variables: S = {V6}. The chain V2 --&gt; V6 --&gt; V5 is the only
 *     noncollider path, so {} fails; V1 and V4 are both shielded colliders between
 *     V2 and V5 (V2 --&gt; V1 &lt;-&gt; V5, V2 --&gt; V4 &lt;-&gt; V5) sitting on direct
 *     2-edge paths, so any set containing either fails. ({@link #testSepsetIsForcedAndUnique})</li>
 * <li>Hosting the deletion therefore requires stamping BOTH shielded colliders
 *     (V2,V1,V5) and (V2,V4,V5) simultaneously on the representative -- and shielded
 *     collider status is class-variant, which is exactly what the Stage-1/2/2b LEG
 *     search must resolve. In this configuration no reachable within-class
 *     representative hosts it ({@link #testEscapeFalseLeavesV2V5}), while pass 3
 *     does ({@link #testEscapeTrueRecoversSkeleton}).</li>
 * </ul>
 */
public class TestFcitSlPke11V2V5 {

    // ---------------------------------------------------------------- fixture

    /**
     * The oracle MAG (Zhang MAG of G*) from the violation log:
     * <pre>
     * 1.  V2 --> V1      7.  V4 <-> V5
     * 2.  V2 --> V4      8.  V5 <-> V1
     * 3.  V2 --> V6      9.  V5 <-> V3
     * 4.  V3 --> V1      10. V6 <-> V3
     * 5.  V3 --> V4      11. V6 --> V4
     * 6.  V4 --> V1      12. V6 --> V5
     * </pre>
     */
    private static Graph oracleMag() {
        Node v1 = new GraphNode("V1");
        Node v2 = new GraphNode("V2");
        Node v3 = new GraphNode("V3");
        Node v4 = new GraphNode("V4");
        Node v5 = new GraphNode("V5");
        Node v6 = new GraphNode("V6");

        Graph mag = new EdgeListGraph(Arrays.asList(v1, v2, v3, v4, v5, v6));

        mag.addDirectedEdge(v2, v1);
        mag.addDirectedEdge(v2, v4);
        mag.addDirectedEdge(v2, v6);
        mag.addDirectedEdge(v3, v1);
        mag.addDirectedEdge(v3, v4);
        mag.addDirectedEdge(v4, v1);
        mag.addBidirectedEdge(v4, v5);
        mag.addBidirectedEdge(v5, v1);
        mag.addBidirectedEdge(v5, v3);
        mag.addBidirectedEdge(v6, v3);
        mag.addDirectedEdge(v6, v4);
        mag.addDirectedEdge(v6, v5);

        return mag;
    }

    /**
     * Builds an FCIT-SL instance against the m-separation oracle for the given MAG,
     * with the PKE11 violation's configuration. Passing an MsepTest switches the
     * initial search to GRaSP internally (see the FcitSl constructor).
     */
    private static FcitSl oracleSearch(Graph mag, boolean allowClassEscape) {
        MsepTest test = new MsepTest(mag);
        GraphScore score = new GraphScore(mag);

        FcitSl fcit = new FcitSl(test, score);
        fcit.setAllowClassEscape(allowClassEscape);
        fcit.setBatteryZMax(5);       // zMax=5
        fcit.setMaxForkFlips(5);      // forkFlips=2
        fcit.setDepth(-1);            // depth=-1
        fcit.setRecursiveDepth(-1);   // recursiveDepth=-1
        fcit.setCompleteRuleSetUsed(true);
        fcit.setVerbose(true);        // handy while playing with it
        return fcit;
    }

    /**
     * Unordered adjacency set of a graph, as name pairs, for skeleton comparison.
     */
    private static Set<Set<String>> skeleton(Graph g) {
        Set<Set<String>> out = new HashSet<>();
        for (Edge e : g.getEdges()) {
            out.add(new HashSet<>(Arrays.asList(
                    e.getNode1().getName(), e.getNode2().getName())));
        }
        return out;
    }

    private static Set<String> pair(String a, String b) {
        return new HashSet<>(Arrays.asList(a, b));
    }

    // ------------------------------------------------------------------ tests

    /**
     * Documents the forcedness claim: over all subsets of {V1, V3, V4, V6},
     * exactly {V6} m-separates V2 and V5 in the oracle MAG. In particular {} fails
     * (the chain V2 --> V6 --> V5 is open) and every superset containing V1 or V4
     * fails (each reopens a direct shielded-collider path).
     */
    @Test
    public void testSepsetIsForcedAndUnique() throws Exception {
        Graph mag = oracleMag();
        MsepTest msep = new MsepTest(mag);

        Node v2 = mag.getNode("V2");
        Node v5 = mag.getNode("V5");
        List<Node> rest = Arrays.asList(
                mag.getNode("V1"), mag.getNode("V3"), mag.getNode("V4"), mag.getNode("V6"));

        List<Set<Node>> separators = new ArrayList<>();

        for (int mask = 0; mask < (1 << rest.size()); mask++) {
            Set<Node> s = new HashSet<>();
            for (int i = 0; i < rest.size(); i++) {
                if ((mask & (1 << i)) != 0) s.add(rest.get(i));
            }
            if (msep.checkIndependence(v2, v5, s).isIndependent()) {
                separators.add(s);
            }
        }

        assertEquals("Expected exactly one separator for (V2, V5): {V6}. Found: "
                + separators, 1, separators.size());
        assertEquals(Collections.singleton(mag.getNode("V6")), separators.get(0));
    }

    /**
     * The violation itself: in Step-Lemma-pure mode (escape=false), the terminal PAG
     * retains the spurious adjacency V2--V5, and that is the ONLY skeleton error.
     * <p>
     * NOTE: if a future fix makes this test fail because the skeleton comes out
     * CORRECT, that is the good outcome -- flip the assertions (or just delete this
     * test and keep {@link #testEscapeTrueRecoversSkeleton} for both settings).
     */
    @Test
    public void testEscapeFalseLeavesV2V5() throws Exception {
        Graph mag = oracleMag();
        Graph truePag = new MagToPag(mag).convert(false, true);

                FcitSl fcitSl = oracleSearch(mag, false);
        fcitSl.setFocusPair(mag.getNode("V2"), mag.getNode("V5"));
        Graph terminal = fcitSl.search();
        System.out.println("V2--V5 tally:      " + fcitSl.getFocusTally());
        System.out.println("V2--V5 battery Zs: " + fcitSl.getFocusBatteryZ());

        System.out.println("interim PAG (generator): \n" + fcitSl.getFocusInterimPag());
        System.out.println("basePag (class identity):\n" + fcitSl.getFocusBasePag());
        System.out.println("enumerated closure (" + fcitSl.getFocusEnumerated().size() + " MAGs):");
        fcitSl.getFocusEnumerated().forEach(m -> System.out.println("----\n" + m));

        System.out.println("=== seed/flip log (pre-filter) ===");
        fcitSl.getFocusSeedLog().forEach(s -> System.out.println("----\n" + s));

        Set<Set<String>> expected = skeleton(truePag);
        Set<Set<String>> actual = skeleton(terminal);

        Set<Set<String>> extra = new HashSet<>(actual);
        extra.removeAll(expected);
        Set<Set<String>> missing = new HashSet<>(expected);
        missing.removeAll(actual);

        assertEquals("Expected no missing adjacencies; missing = " + missing,
                Collections.emptySet(), missing);
        assertEquals("Expected exactly one extra adjacency, V2--V5; extra = " + extra,
                Collections.singleton(pair("V2", "V5")), extra);
    }

    /**
     * Item 4 of the homework, pinned down: with the class-escape pass enabled, the
     * same oracle and configuration recover the true skeleton exactly.
     */
    @Test
    public void testEscapeTrueRecoversSkeleton() throws Exception {
        Graph mag = oracleMag();
        Graph truePag = new MagToPag(mag).convert(false, true);

        Graph terminal = oracleSearch(mag, true).search();

        assertEquals("Terminal skeleton should match the true PAG's skeleton",
                skeleton(truePag), skeleton(terminal));
    }
}