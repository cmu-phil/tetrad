package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.score.GraphScore;
import edu.cmu.tetrad.search.test.MsepTest;

import java.util.List;

/** Isolates GRaSP + oracle behavior on the 2-node graph, with and without tiers; BOSS for contrast. */
public final class ReproGraspOracle {
    private ReproGraspOracle() {
    }

    public static void main(String[] args) throws Exception {
        Node x1 = new GraphNode("X1");
        Node x2 = new GraphNode("X2");
        Graph trueDag = new EdgeListGraph(List.of(x1, x2));
        trueDag.addDirectedEdge(x1, x2);

        Knowledge tiers = new Knowledge();
        tiers.addToTier(0, "X1");
        tiers.addToTier(1, "X2");

        // GRaSP without knowledge
        Grasp g1 = new Grasp(new MsepTest(trueDag), new GraphScore(trueDag));
        g1.setNumStarts(1);
        g1.bestOrder(new GraphScore(trueDag).getVariables());
        System.out.println("GRaSP oracle, no knowledge:   " + g1.getGraph(false).getEdges());

        // GRaSP with tiers
        Grasp g2 = new Grasp(new MsepTest(trueDag), new GraphScore(trueDag));
        g2.setNumStarts(1);
        g2.setKnowledge(tiers);
        g2.bestOrder(new GraphScore(trueDag).getVariables());
        System.out.println("GRaSP oracle, tiers:          " + g2.getGraph(false).getEdges());

        // GRaSP with tiers, useScore=false (test-based, as one would for an oracle)
        Grasp g3 = new Grasp(new MsepTest(trueDag), new GraphScore(trueDag));
        g3.setNumStarts(1);
        g3.setUseScore(false);
        g3.setKnowledge(tiers);
        g3.bestOrder(new GraphScore(trueDag).getVariables());
        System.out.println("GRaSP oracle, tiers, useScore=false: " + g3.getGraph(false).getEdges());

        // BOSS with tiers for contrast
        Boss boss = new Boss(new GraphScore(trueDag));
        boss.setNumStarts(1);
        PermutationSearch ps = new PermutationSearch(boss);
        ps.setKnowledge(tiers);
        System.out.println("BOSS oracle, tiers:           " + ps.search().getEdges());
    }
}
