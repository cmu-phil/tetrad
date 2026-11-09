package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.score.GraphScore;
import edu.cmu.tetrad.search.test.MsepTest;
import edu.cmu.tetrad.search.utils.PagLegalityCheck;
import edu.cmu.tetrad.util.SublistGenerator;

import java.util.*;

/** Reproduces the PKE14 SKELETON_DIFF violation ca....aaaa.acccacacacaccc and diagnoses it. */
public final class ReproPke14Violation {
    private ReproPke14Violation() {
    }

    /**
     * The main method serves as the entry point for the program. It demonstrates the process of
     * creating a graph using nodes and edges, performs checks for MAG legality, identifies separating sets,
     * and applies different analysis methods on the graph structure.
     *
     * @param args command-line arguments passed to the application
     * @throws Exception if any error occurs during the execution of the program
     */
    public static void main(String[] args) throws Exception {
        List<Node> nodes = new ArrayList<>();
        for (int i = 1; i <= 6; i++) nodes.add(new GraphNode("V" + i));
        Graph mag = new EdgeListGraph(nodes);
        Node v1 = mag.getNode("V1"), v2 = mag.getNode("V2"), v3 = mag.getNode("V3"),
                v4 = mag.getNode("V4"), v5 = mag.getNode("V5"), v6 = mag.getNode("V6");

        mag.addDirectedEdge(v1, v2);
        mag.addBidirectedEdge(v2, v3);
        mag.addBidirectedEdge(v2, v4);
        mag.addBidirectedEdge(v2, v6);
        mag.addBidirectedEdge(v3, v4);
        mag.addBidirectedEdge(v3, v5);
        mag.addBidirectedEdge(v3, v6);
        mag.addBidirectedEdge(v4, v5);
        mag.addDirectedEdge(v6, v4);
        mag.addDirectedEdge(v6, v5);

        System.out.println("oracle MAG legal: "
                + PagLegalityCheck.isLegalMag(mag, new HashSet<>()).isLegalMag());

        Graph truePag = new edu.cmu.tetrad.search.utils.MagToPag(new EdgeListGraph(mag))
                .convert(false, true);
        System.out.println("\ntrue PAG G*:\n" + truePag);

        // ---- Which sets m-separate V2 and V5 in the oracle MAG? ----
        MsepTest oracle = new MsepTest(new EdgeListGraph(mag));
        Node o2 = oracle.getVariables().stream().filter(n -> n.getName().equals("V2")).findFirst().orElseThrow();
        Node o5 = oracle.getVariables().stream().filter(n -> n.getName().equals("V5")).findFirst().orElseThrow();
        List<Node> others = new ArrayList<>();
        for (Node n : oracle.getVariables()) {
            if (!n.getName().equals("V2") && !n.getName().equals("V5")) others.add(n);
        }

        System.out.println("\n-- separators of V2, V5 in the oracle MAG --");
        List<Set<Node>> seps = new ArrayList<>();
        SublistGenerator gen = new SublistGenerator(others.size(), others.size());
        int[] ch;
        while ((ch = gen.next()) != null) {
            Set<Node> s = GraphUtils.asSet(ch, others);
            if (oracle.checkIndependence(o2, o5, s).isIndependent()) {
                seps.add(s);
                System.out.println("  " + s);
            }
        }
        if (seps.isEmpty()) System.out.println("  (none -- V2 and V5 are NOT separable!)");

        // ---- Run FcitZm verbose and watch the V2/V5 decisions. ----
        System.out.println("\n-- FcitZm (MAG route), verbose --");
        FcitZm zm = new FcitZm(new MsepTest(new EdgeListGraph(mag)),
                new GraphScore(new EdgeListGraph(mag)));
        zm.setKnowledge(new Knowledge());
        zm.setCompleteRuleSetUsed(true);
        zm.setExcludeSelectionBias(true);
        zm.setCommitRoute(FcitZm.COMMIT_ROUTE.MAG);
        zm.setVerbose(true);
        Graph terminal = zm.search();
        System.out.println("\nterminal:\n" + terminal);

        System.out.println("V2-V5 adjacent in terminal: "
                + terminal.isAdjacentTo(terminal.getNode("V2"), terminal.getNode("V5")));

        // ---- Would removing V2-V5 with each true separator pass the MAG gate? ----
        System.out.println("\n-- MAG-legality of the seed PAG minus V2-V5, per candidate separator --");
        for (Set<Node> s : seps) {
            Graph seed = new EdgeListGraph(truePag);
            Graph m2 = GraphTransforms.zhangMagFromPag(seed);
            m2.removeEdge(m2.getNode("V2"), m2.getNode("V5"));
            PagLegalityCheck.LegalMagRet ret = PagLegalityCheck.isLegalMag(m2, new HashSet<>());
            System.out.println("  sepset " + s + " -> legal MAG after removal: " + ret.isLegalMag()
                    + (ret.isLegalMag() ? "" : " (" + ret.getReason() + ")"));
            break;
        }
    }
}
